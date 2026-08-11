package inkspire.morphic.feature.settings.iconstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen state holder for the icon studio.
 *
 * **Plain MVVM: one `StateFlow` and typed methods, no `Action`/`Effect` hierarchy.** L1's plan for this screen
 * specified MVI, and CLAUDE.md forbids it precisely because that ceremony is what turned its home screen into a
 * 500-line `when(event)`. The unidirectional flow MVI is wanted for is already here without the machinery.
 *
 * ## The set is read once and then owned
 *
 * [IconStudioState.editing] is seeded from storage and never re-seeded. A live editor's set diverges from the store
 * the moment a slider moves, so projecting the repository flow into the screen would mean either writing every frame
 * of a drag or having the next emission overwrite what the user is doing. Reading once is also exactly the
 * **snapshot-detach** the persistence layer is built on: opening an app in the studio copies the current global
 * default and the app goes its own way.
 *
 * ## Which recipe seeds it
 *
 * - [IconStudioRoute.Global] — the stored global default.
 * - [IconStudioRoute.App] with an override — that app's own recipe.
 * - [IconStudioRoute.App] with none — the global default, *copied*. Nothing is written until the edit is committed,
 *   so opening an app's studio and backing out leaves it still inheriting.
 */
class IconStudioViewModel(
    route: IconStudioRoute,
    private val settingsRepository: SettingsRepository,
    private val overrideRepository: IconOverrideRepository,
    private val parsedIcons: ParsedIconLoader,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IconStudioState())

    val state: StateFlow<IconStudioState> = _state.asStateFlow()

    init {
        when (route) {
            is IconStudioRoute.Global -> openGlobal()
            is IconStudioRoute.App -> route.component?.let(ComponentKey::parse)
                ?.let { openApp(it) }
                ?: loadPickable()
        }
    }

    /**
     * Loads the list the picker offers, for the one route that arrives without an app.
     *
     * Only in that case: every other way in already knows its subject, and holding the whole app list for a screen
     * that will never show it is a list nobody reads.
     */
    private fun loadPickable() {
        viewModelScope.launch {
            val apps = appRepository.observeApps().first()
            _state.update { it.copy(subject = StudioSubject.Unchosen, pickable = apps) }
        }
    }

    /** Chooses the app to edit — the picker's one output, and how [StudioSubject.Unchosen] is left. */
    fun selectApp(component: ComponentKey) = openApp(component)

    /** Advances the preview backdrop. Pure screen state: nothing about it is persisted or shared. */
    fun cycleBackground() = _state.update { it.copy(background = it.background.next()) }

    /** Points the controls at a layer. Pure selection — nothing about the recipe changes, so no history entry. */
    fun selectLayer(index: Int) = _state.update { it.copy(selected = index) }

    /**
     * Applies [transform] to the selected layer **without recording history** — the live-edit path.
     *
     * Every frame of a slider drag comes through here. Recording each one would make undo useless (a hundred steps
     * back through one gesture), so history is punctuated by [commitEdit] instead, which the controls call when a
     * gesture ends.
     */
    fun updateSelected(transform: (IconLayerSpec) -> IconLayerSpec) = _state.update { current ->
        val layers = current.editing.layers.toMutableList()
        val spec = layers.getOrNull(current.selected) ?: return@update current
        layers[current.selected] = transform(spec)
        current.withEditing(IconLayerSet(layers))
    }

    /**
     * Marks the end of a continuous edit, so undo steps over the whole gesture rather than through it.
     *
     * Called from a slider's `onValueChangeFinished`. Discrete edits — a shape, a source, a reorder — do not need
     * it: they record themselves, because there is no intermediate state for them to flood history with.
     */
    fun commitEdit() = _state.update { it.recordHistory() }

    /** Shows or hides the selected layer without deleting it. Discrete, so it records history at once. */
    fun toggleSelectedVisible() {
        updateSelected { it.copy(visible = !it.visible) }
        commitEdit()
    }

    /**
     * Moves the selected layer one step up or down the stack, and **follows it with the selection**.
     *
     * A no-op when the move would break the foreground-above-background invariant — the set refuses it and returns
     * itself, which is also what disables the button, so this can never be reached in that state from the UI. It is
     * still guarded here, because a command that silently mis-selects when called out of turn is worse than one that
     * does nothing.
     */
    fun moveSelected(up: Boolean) = _state.update { current ->
        val moved = if (up) current.editing.moveUp(current.selected) else current.editing.moveDown(current.selected)
        if (moved === current.editing) return@update current
        val destination = if (up) current.selected + 1 else current.selected - 1
        current.withEditing(moved).copy(selected = destination).recordHistory()
    }

    /**
     * Inserts a new custom layer directly above the selected one, and selects it.
     *
     * A **solid fill** rather than an image, because an image needs a picker and a file, which is its own slice.
     * That is not a placeholder: a flat colour layer is the useful half on its own — it is how a legacy icon with
     * no background of its own gets one.
     */
    fun addLayer() = _state.update { current ->
        val insertAt = (current.selected + 1).coerceIn(0, current.editing.layers.size)
        val layers = current.editing.layers.toMutableList()
        layers.add(insertAt, IconLayerSpec(role = LayerRole.CUSTOM, source = LayerSource.SolidFill(NewLayerArgb)))
        current.withEditing(IconLayerSet(layers)).copy(selected = insertAt).recordHistory()
    }

    /**
     * Deletes the selected layer. Custom layers only — the foreground and background are permanent, which the set
     * enforces in its own `init`, so removing one would throw rather than misbehave.
     */
    fun removeSelected() = _state.update { current ->
        if (!current.canRemoveSelected) return@update current
        val layers = current.editing.layers.toMutableList()
        layers.removeAt(current.selected)
        current.withEditing(IconLayerSet(layers))
            .copy(selected = current.selected.coerceAtMost(layers.lastIndex))
            .recordHistory()
    }

    /** Steps back one recorded edit. */
    fun undo() = _state.update { current ->
        if (!current.canUndo) return@update current
        historyIndex--
        current.copy(editing = history[historyIndex]).withHistoryFlags().withSelectionInRange()
    }

    /** Steps forward again after an [undo]. */
    fun redo() = _state.update { current ->
        if (!current.canRedo) return@update current
        historyIndex++
        current.copy(editing = history[historyIndex]).withHistoryFlags().withSelectionInRange()
    }

    /**
     * Persists the edit: the global default, or this app's own recipe.
     *
     * **An explicit save in both modes, which is a deliberate departure from L1** — its global studio committed
     * live, on every control change. Two reasons not to. A slice is one JSON blob, so a live-committing slider
     * would rewrite the whole document per frame; and a global edit restyles every icon on the device, which is
     * not something to do continuously while a user is still deciding. The *preview* stays live either way, which
     * is what "live edit is non-negotiable" was ever about.
     */
    fun save() {
        val current = _state.value
        viewModelScope.launch {
            when (val subject = current.subject) {
                is StudioSubject.Global -> settingsRepository.setIconLayerSet(current.editing)
                is StudioSubject.App -> overrideRepository.set(subject.component, current.editing)
                StudioSubject.Unchosen -> return@launch
            }
            _state.update { it.copy(dirty = false) }
            saved = current.editing
        }
    }

    /**
     * Puts the subject back to inheriting: an app drops its own recipe and follows the global default again; the
     * global default returns to the plain two-layer set.
     *
     * The two are the same verb — "stop being customised" — pointed at different things, which is why the global
     * case is `IconLayerSet.Base` rather than something remembered: there is nothing above the global default for
     * it to fall back *to*.
     */
    fun reset() {
        val current = _state.value
        viewModelScope.launch {
            val restored = when (val subject = current.subject) {
                is StudioSubject.Global -> IconLayerSet.Base.also { settingsRepository.setIconLayerSet(it) }
                is StudioSubject.App -> {
                    overrideRepository.clear(subject.component)
                    settingsRepository.iconLayerSet.first()
                }
                StudioSubject.Unchosen -> return@launch
            }
            saved = restored
            resetHistory(restored)
            _state.update { it.copy(editing = restored, dirty = false).withHistoryFlags().withSelectionInRange() }
        }
    }

    /**
     * Editing the global default, previewed on a real app.
     *
     * The sample is simply the first installed app for now. It has to be *some* real app — a recipe drawn over
     * nothing shows nothing — and which one is a question for the extras rail's shuffle, not for this.
     */
    private fun openGlobal() {
        viewModelScope.launch {
            val stored = settingsRepository.iconLayerSet.first()
            val sample = appRepository.observeApps().first().firstOrNull()
            saved = stored
            resetHistory(stored)
            _state.update {
                it.copy(
                    subject = StudioSubject.Global(sample?.componentKey),
                    editing = stored,
                    label = null,
                ).withHistoryFlags()
            }
            sample?.componentKey?.let(::loadArtwork)
        }
    }

    private fun openApp(component: ComponentKey) {
        viewModelScope.launch {
            // The override if there is one, otherwise a copy of the global default — which is the snapshot half of
            // snapshot-detach. Nothing is written here: an app that was inheriting still inherits until a commit.
            val stored = overrideRepository.overrides.first()[component]
                ?: settingsRepository.iconLayerSet.first()
            val label = appRepository.observeApps().first()
                .firstOrNull { it.componentKey == component }?.label

            // `saved` is what is *persisted*, which for an app that is still inheriting is not the same as what is
            // shown: the studio opens on a copy of the global default, and that copy is already an unsaved change.
            // So a freshly opened inheriting app is `dirty`, correctly — saving it is what detaches it.
            saved = overrideRepository.overrides.first()[component] ?: IconLayerSet.Base
            resetHistory(stored)
            _state.update {
                it.copy(subject = StudioSubject.App(component), editing = stored, label = label).withHistoryFlags()
            }
            loadArtwork(component)
        }
    }

    /**
     * Loads the subject's parsed layers for the live render.
     *
     * Off the main thread explicitly, because [ParsedIconLoader.load] is deliberately blocking — see its KDoc for
     * why it does not hop for itself. Keyed to nothing: it runs once per subject, never per edit, since re-parsing
     * on every slider frame is the exact cost the live render path exists to avoid.
     */
    private fun loadArtwork(component: ComponentKey) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) { parsedIcons.load(component) }
            _state.update { if (it.subject.previewComponent == component) it.copy(parsed = parsed) else it }
        }
    }

    // ---- Undo history -------------------------------------------------------------------------------------------
    //
    // **Undo is a list of whole recipes, and it is nearly free because `IconLayerSet` is immutable.** There is no
    // command pattern here and no inverse operation per edit: a step back is an index. L1 left undo an open
    // feasibility question in its studio plan, and the reason it was a question there is that its equivalent state
    // was a bag of mutable flat fields with no single value to snapshot.
    //
    // The cost is one reference per recorded edit, which is why history is punctuated by `commitEdit` rather than
    // recorded per frame — see `updateSelected`.

    /** Recorded states, oldest first. Always non-empty: index 0 is what the studio opened with. */
    private var history: List<IconLayerSet> = listOf(IconLayerSet.Base)
    private var historyIndex = 0

    /** What is currently persisted, so `dirty` is a comparison rather than a flag that can drift out of step. */
    private var saved: IconLayerSet = IconLayerSet.Base

    /** Starts history afresh at [set] — on open, and after a reset. */
    private fun resetHistory(set: IconLayerSet) {
        history = listOf(set)
        historyIndex = 0
    }

    /**
     * Records the current recipe as an undo step, unless it is identical to the last one.
     *
     * **Redo is discarded on a new edit**, which is the standard and the only coherent option: once the user
     * branches, the states that used to be ahead describe a future that no longer follows from the present.
     */
    private fun IconStudioState.recordHistory(): IconStudioState {
        if (editing == history[historyIndex]) return withHistoryFlags()
        history = history.take(historyIndex + 1) + editing
        historyIndex = history.lastIndex
        return withHistoryFlags()
    }

    /** Applies a new recipe without recording it — the live path; see [updateSelected]. */
    private fun IconStudioState.withEditing(set: IconLayerSet): IconStudioState =
        copy(editing = set, dirty = set != saved)

    private fun IconStudioState.withHistoryFlags(): IconStudioState =
        copy(canUndo = historyIndex > 0, canRedo = historyIndex < history.lastIndex, dirty = editing != saved)

    /** Keeps the selection valid when a step through history changes how many layers there are. */
    private fun IconStudioState.withSelectionInRange(): IconStudioState =
        copy(selected = selected.coerceIn(0, editing.layers.lastIndex.coerceAtLeast(0)))

    private companion object {
        /** A mid grey for a freshly added fill: visible against both a light and a dark canvas, and obviously a
         *  placeholder the user is meant to change. */
        const val NewLayerArgb: Int = 0xFF808080.toInt()
    }
}
