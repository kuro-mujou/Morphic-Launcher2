package inkspire.morphic.feature.settings.iconstudio

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.key
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.icons.CustomIconStore
import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.icons.IconPackManager
import inkspire.morphic.data.settings.IconPreset
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
    private val customIcons: CustomIconStore,
    private val iconPacks: IconPackManager,
) : ViewModel() {

    private val _state = MutableStateFlow(IconStudioState())

    val state: StateFlow<IconStudioState> = _state.asStateFlow()

    init {
        // Above the route branch, because the canvas exists in all three cases — including the picker's, which floats
        // over it rather than replacing it.
        observeBackground()

        when (route) {
            is IconStudioRoute.Global -> openGlobal(route.preset)
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

    /**
     * Saves what is being edited as a preset called [name].
     *
     * **Independent of Save.** A preset is a recipe kept in a library, not a commitment to use it anywhere, so
     * naming one neither writes the global default nor detaches an app — a user can build a look, keep it, and
     * back out without applying it.
     */
    fun savePreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val set = _state.value.editing
        viewModelScope.launch { settingsRepository.saveIconPreset(trimmed, set) }
    }

    /** Loads [preset] into the editor. An ordinary edit: recorded in history, undoable, and not saved. */
    fun loadPreset(preset: IconPreset) =
        _state.update { it.withEditing(preset.layerSet).withSelectionInRange().recordHistory() }

    /** Removes a saved preset. Does not touch anything it was applied to — a preset is a copy, not a link. */
    fun deletePreset(name: String) {
        viewModelScope.launch { settingsRepository.deleteIconPreset(name) }
    }

    /** Chooses the app to edit — the picker's one output, and how [StudioSubject.Unchosen] is left. */
    fun selectApp(component: ComponentKey) = openApp(component)

    /**
     * Advances the preview backdrop, and **remembers it** — the studio reopens on whatever it was left on.
     *
     * **Optimistic, then written.** The state moves first so the canvas turns over under the finger rather than after a
     * round trip through DataStore; [observeBackground]'s collector then echoes the same value back, which is a no-op.
     * The alternative — write, and let the flow be the only thing that moves the state — would cost a frame on every tap
     * *and* mis-handle a fast double tap, since the second press would read a `background` the first write had not
     * landed yet and compute the same successor twice.
     */
    fun cycleBackground() {
        val next = _state.value.background.next()
        _state.update { it.copy(background = next) }
        viewModelScope.launch { settingsRepository.setIconStudioBackground(next) }
    }

    /**
     * Keeps the canvas on the stored backdrop.
     *
     * **The one thing on this screen that *is* projected from the store**, against [IconStudioState.editing] being read
     * once and owned. It can be because nothing edits it continuously: a cycle is a discrete tap, so there is no drag
     * for an emission to overwrite — the divergence that makes projecting the recipe wrong does not arise here.
     *
     * Collected rather than read once so an externally-changed value still arrives, and because the write path above
     * relies on the echo being harmless. There is one implausible window — a tap landing before the first emission,
     * which would briefly show the stored value instead — and it corrects itself, since that tap's own write emits next.
     */
    private fun observeBackground() {
        viewModelScope.launch {
            settingsRepository.iconStudioBackground.collect { stored ->
                _state.update { it.copy(background = stored) }
            }
        }
    }

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
     * Imports [uri] as the selected layer's artwork, or as a new layer when the selection cannot take one.
     *
     * **Nothing is written to disk here.** The image is decoded, kept in [IconStudioState.images] under a
     * reserved path, and drawn from memory — so backing out of the studio leaves no file behind, which is the
     * bug L1 recorded and accepted. [save] writes whatever the committed recipe still refers to.
     */
    fun pickImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = customIcons.decode(uri) ?: return@launch
            val path = customIcons.reservePath()
            unsaved[path] = bitmap

            _state.update { current ->
                val source = LayerSource.CustomImage(path)
                val withImage = current.selectedLayer
                    // A custom layer takes the image in place; a foreground or background gets it as its source
                    // too, which is how one app's icon is replaced outright rather than covered over.
                    ?.let { current.replaceSelectedSource(source) }
                    ?: current
                withImage.copy(images = current.images + (path to bitmap)).recordHistory()
            }
        }
    }

    /**
     * Points the selected layer at an installed icon pack.
     *
     * The **whole** of "apply a pack": in the global studio this sets the default's foreground source and every
     * app inherits it, and in one app's studio it sets that app's. There is no pack *mode* anywhere, which is why
     * decoration layers are untouched by construction — a pack only ever occupies the slot it is put in.
     */
    fun pickPack(packPackage: String) {
        _state.update { it.replaceSelectedSource(LayerSource.IconPack(packPackage)).recordHistory() }
        loadPackArtwork()
    }

    /**
     * Opens the drawable browser for [packPackage], or closes it when [packPackage] is null.
     *
     * **Refuses in the global studio**, because a named drawable there would be inherited by every app. The UI
     * does not offer the affordance either, so this is the guard behind the guard rather than the only one.
     */
    fun browsePack(packPackage: String?) {
        if (packPackage == null) {
            _state.update { it.copy(browsing = null) }
            return
        }
        if (_state.value.subject !is StudioSubject.App) return

        _state.update { it.copy(browsing = PackBrowse(packPackage)) }
        viewModelScope.launch {
            val names = iconPacks.drawableNames(packPackage)
            // Guard against the browser having been closed, or another pack opened, while the names loaded.
            _state.update {
                if (it.browsing?.packPackage == packPackage) it.copy(browsing = PackBrowse(packPackage, names)) else it
            }
        }
    }

    /** One drawable's thumbnail for a browser cell. Cached in the manager, so scrolling back is free. */
    suspend fun packPreview(packPackage: String, drawableName: String): Bitmap? =
        iconPacks.preview(packPackage, drawableName)

    /** Chooses a specific drawable from the pack being browsed, and closes the browser. */
    fun pickPackDrawable(drawableName: String) {
        val pack = _state.value.browsing?.packPackage ?: return
        _state.update {
            it.replaceSelectedSource(LayerSource.IconPack(pack, drawableName)).copy(browsing = null).recordHistory()
        }
        loadPackArtwork()
    }

    /** Keeps the preset library in step, since saving and deleting both happen from this screen. */
    private fun observePresets() {
        viewModelScope.launch {
            settingsRepository.iconPresets.collect { presets -> _state.update { it.copy(presets = presets) } }
        }
    }

    /** Loads the installed packs for the chooser. Once per screen; nothing about the list changes while it is up. */
    private fun loadPacks() {
        viewModelScope.launch {
            val installed = iconPacks.installedPacks()
            if (installed.isNotEmpty()) _state.update { it.copy(packs = installed) }
        }
    }

    /**
     * Resolves this app's artwork from every pack the recipe names, for the preview.
     *
     * Additive and keyed on what is missing, so switching between two packs does not re-parse the one already
     * loaded. A pack that does not cover this app resolves to nothing and is simply absent from the map — the
     * ordinary case rather than an error, since no pack themes everything.
     */
    private fun loadPackArtwork() {
        val component = _state.value.subject.previewComponent ?: return
        val missing = _state.value.editing.packLayers().filterNot { it.key in _state.value.packImages }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = missing.mapNotNull { source ->
                iconPacks.drawable(source.packPackage, component, source.drawableName)
                    ?.let { source.key to it.toBitmap() }
            }
            if (loaded.isNotEmpty()) _state.update { it.copy(packImages = it.packImages + loaded) }
        }
    }

    /** Every pack layer this recipe draws from — by pack *and* chosen drawable, since both decide the artwork. */
    private fun IconLayerSet.packLayers(): List<LayerSource.IconPack> =
        layers.mapNotNull { it.source as? LayerSource.IconPack }.distinct()

    /** Swaps the selected layer's source, leaving every other property of it alone. */
    private fun IconStudioState.replaceSelectedSource(source: LayerSource): IconStudioState {
        val layers = editing.layers.toMutableList()
        val spec = layers.getOrNull(selected) ?: return this
        layers[selected] = spec.copy(source = source)
        return withEditing(IconLayerSet(layers))
    }

    /**
     * Inserts a new custom layer directly above the selected one, and selects it.
     *
     * A **solid fill** rather than an image: a new layer needs *some* content, and a colour is the one that needs
     * nothing from outside the app. An image is a tap away once the layer exists ([pickImage]).
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
            // **Images first, recipe second.** A recipe that referred to a file which had not been written yet
            // would render as a missing layer for as long as the gap lasted — and if the write then failed, for
            // good. In the other order the worst case is a written file nothing refers to, which the sweep below
            // collects.
            current.editing.imagePaths().forEach { path ->
                unsaved.remove(path)?.let { bitmap -> customIcons.write(path, bitmap) }
            }

            when (val subject = current.subject) {
                is StudioSubject.Global -> settingsRepository.setIconLayerSet(current.editing)
                is StudioSubject.App -> overrideRepository.set(subject.component, current.editing)
                StudioSubject.Unchosen -> return@launch
            }
            _state.update { it.copy(dirty = false) }
            saved = current.editing
            collectOrphanedImages()
        }
    }

    /**
     * Deletes every stored image that no recipe refers to any more.
     *
     * Run after a save because that is when a reference can have been *dropped* — a layer removed, an image
     * replaced, or an edit undone past the pick that made it. Asking what is still referenced is one question with
     * one answer; the alternative is a delete at each of those sites, where missing one leaks silently and
     * invisibly. It reads the stores rather than this screen's state on purpose: the answer has to include every
     * *other* app's recipe, not just the one being edited.
     */
    private suspend fun collectOrphanedImages() {
        val referenced = buildSet {
            addAll(settingsRepository.iconLayerSet.first().imagePaths())
            overrideRepository.overrides.first().values.forEach { addAll(it.imagePaths()) }
        }
        customIcons.retainOnly(referenced)
    }

    /** Every custom-image path this recipe draws from. */
    private fun IconLayerSet.imagePaths(): Set<String> =
        layers.mapNotNull { (it.source as? LayerSource.CustomImage)?.path }.toSet()

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
    private fun openGlobal(preset: String? = null) {
        viewModelScope.launch {
            // A named preset opens *loaded with* it rather than with what is stored — and stays unsaved, so the
            // user sees what it will do to every icon before committing. See `IconStudioRoute.Global.preset`.
            val stored = preset
                ?.let { name -> settingsRepository.iconPresets.first().firstOrNull { it.name == name }?.layerSet }
                ?: settingsRepository.iconLayerSet.first()
            val sample = appRepository.observeApps().first().firstOrNull()
            // `saved` is what is *persisted*, which a preset-loaded session deliberately is not — so it opens
            // dirty, and Save is what applies the preset. Same shape as an inheriting app opening dirty.
            saved = settingsRepository.iconLayerSet.first()
            resetHistory(stored)
            _state.update {
                it.copy(
                    subject = StudioSubject.Global(sample?.componentKey),
                    editing = stored,
                    label = null,
                ).withHistoryFlags()
            }
            loadStoredImages(stored)
            loadPacks()
            observePresets()
            sample?.componentKey?.let(::loadArtwork)
            loadPackArtwork()
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
            loadStoredImages(stored)
            loadPacks()
            observePresets()
            loadArtwork(component)
            loadPackArtwork()
        }
    }

    /**
     * Loads the subject's parsed layers for the live render.
     *
     * Off the main thread explicitly, because [ParsedIconLoader.load] is deliberately blocking — see its KDoc for
     * why it does not hop for itself. Keyed to nothing: it runs once per subject, never per edit, since re-parsing
     * on every slider frame is the exact cost the live render path exists to avoid.
     */
    /**
     * Reads any custom-image layer whose artwork is on disk but not yet in memory — an app opened with a recipe
     * that already had images in it.
     *
     * Additive: it never removes anything from [IconStudioState.images], so an image the user picked but has not
     * saved survives, and undoing back to a layer keeps its artwork available rather than blanking it.
     */
    private fun loadStoredImages(set: IconLayerSet) {
        val missing = set.imagePaths() - _state.value.images.keys
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = missing.mapNotNull { path -> customIcons.read(path)?.let { path to it } }
            if (loaded.isNotEmpty()) _state.update { it.copy(images = it.images + loaded) }
        }
    }

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

    /**
     * Picked images that exist only in memory, by the path they will be written to.
     *
     * Not in the state, because nothing renders *from* this — the same bitmaps are in
     * [IconStudioState.images], which is what the preview reads. This is the narrower question of which of them
     * still owe a write, and keeping it out of the state means undo cannot rewind it: a path that has been
     * undone past is simply never written, and the sweep tidies it if it was.
     */
    private val unsaved = mutableMapOf<String, Bitmap>()

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
