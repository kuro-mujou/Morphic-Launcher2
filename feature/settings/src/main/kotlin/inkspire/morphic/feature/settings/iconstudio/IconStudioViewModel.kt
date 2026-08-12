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
     *
     * **Refuses in the individual studio**, where a recipe is tuned against one app and so tends to name that app's own
     * artwork — a custom image of it, or a pack drawable chosen for it — which a preset would then carry into every
     * other icon it was applied to. The UI does not offer the affordance there either, so this is the guard behind the
     * guard rather than the only one, exactly as [browsePack]'s is.
     */
    fun savePreset(name: String) {
        if (_state.value.subject !is StudioSubject.Global) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val set = _state.value.editing
        viewModelScope.launch { settingsRepository.saveIconPreset(trimmed, set) }
    }

    /**
     * Loads [preset] into the editor. An ordinary edit: recorded in history, undoable, and not saved.
     *
     * A whole new stack arrives, so its layers get **fresh keys** — nothing in it continues a layer that was there
     * before, and reusing a key would animate an unrelated row into its place. Selection goes to the foreground for the
     * same reason a fresh open does: the previous index means nothing in a stack the user did not build.
     */
    fun loadPreset(preset: IconPreset) = _state.update { current ->
        val keys = freshKeys(preset.layerSet.layers.size)
        current.withEditing(preset.layerSet)
            .copy(layerKeys = keys, selected = preset.layerSet.foregroundIndex)
            .recordHistory()
    }

    /** Removes a saved preset. Does not touch anything it was applied to — a preset is a copy, not a link. */
    fun deletePreset(name: String) {
        viewModelScope.launch { settingsRepository.deleteIconPreset(name) }
    }

    /** Chooses the app to edit — the picker's one output, and how [StudioSubject.Unchosen] is left. */
    fun selectApp(component: ComponentKey) = openApp(component)

    /**
     * Draws a **different app** for the global studio to preview on — the answer to the question
     * [StudioSubject.Global.sample]'s KDoc has been deferring.
     *
     * A global recipe is edited against one app's artwork, and which app decides what the edit *looks* like: a legacy
     * icon with a flat plate and an adaptive icon with a transparent foreground respond to the same layer differently,
     * so a recipe tuned against one can be wrong for the other. This is how the user checks, and it is a *shuffle*
     * rather than a picker because the point is to see a spread, not to find a particular app.
     *
     * **The current sample is excluded**, so every press visibly changes something — a die that lands on the same face
     * reads as a broken button rather than as chance.
     *
     * **Refuses in the individual studio.** There the app is the subject, not a stand-in for one, so re-rolling it
     * would be editing a different app's recipe by accident. The UI shows a different button there.
     */
    fun shuffleSample() {
        val subject = _state.value.subject as? StudioSubject.Global ?: return
        viewModelScope.launch {
            val next = appRepository.observeApps().first()
                .filterNot { it.componentKey == subject.sample }
                .randomOrNull()
                ?.componentKey
                ?: return@launch

            // Pack artwork is **this app as drawn by that pack**, so it does not survive a change of app — see
            // `packImages`. Cleared rather than merged, because the keys are the pack's and would otherwise hit and
            // hand back the previous app's icon.
            _state.update { it.copy(subject = StudioSubject.Global(next), packImages = emptyMap()) }
            loadArtwork(next)
            loadPackArtwork()
        }
    }

    /**
     * Goes back to the picker so a different app can be edited.
     *
     * **The individual studio's counterpart to the shuffle**, and deliberately not a shuffle: an app's own recipe is
     * about *that* app, so landing on a random one would be editing something nobody asked for. `Unchosen` is already
     * the state that shows the picker, so this is a return to it rather than a second way in.
     *
     * **Unsaved edits are discarded**, which is the same bargain backing out of the studio makes and the reason Save
     * lights up: leaving an app — by any route — leaves what was not committed. The alternative, a confirm, would put a
     * dialog in front of the one gesture a user makes while browsing.
     */
    fun chooseAnotherApp() {
        if (_state.value.subject !is StudioSubject.App) return
        viewModelScope.launch {
            val apps = appRepository.observeApps().first()
            _state.update { it.copy(subject = StudioSubject.Unchosen, pickable = apps, label = null) }
        }
    }

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
     * Switches the selected layer between the app's own artwork and its monochrome form, and back.
     *
     * **A command rather than a source the UI writes**, so it records history at once — the same shape
     * [toggleSelectedVisible] and [pickPack] take, and what makes `commitEdit`'s "discrete edits record themselves"
     * true for this one. Off returns to [LayerSource.AppDefault], because monochrome is a *refinement of* the app's
     * own artwork rather than a peer source: there is nowhere else for turning it off to land.
     *
     * Guarded on the source as well as toggled by it, so calling this on a layer showing a pack or an image cannot
     * quietly discard what is there — the UI only offers it on the app-default foreground, and this is the guard
     * behind that one.
     */
    fun toggleSelectedMonochrome() {
        updateSelected { spec ->
            when (spec.source) {
                LayerSource.AppDefault -> spec.copy(source = LayerSource.AppDefaultMonochrome)
                LayerSource.AppDefaultMonochrome -> spec.copy(source = LayerSource.AppDefault)
                else -> spec
            }
        }
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
        // The keys swap with the layers, so both rows keep their identity and glide past each other rather than
        // swapping contents in place.
        val keys = current.layerKeys.toMutableList().apply {
            if (current.selected in indices && destination in indices) {
                val held = this[current.selected]
                this[current.selected] = this[destination]
                this[destination] = held
            }
        }
        current.withEditing(moved).copy(selected = destination, layerKeys = keys).recordHistory()
    }

    /**
     * Imports [uri] as the selected layer's artwork.
     *
     * **Nothing is written to disk here.** The image is decoded, kept in [IconStudioState.images] under a
     * reserved path, and drawn from memory — so backing out of the studio leaves no file behind, which is the
     * bug L1 recorded and accepted. [save] writes whatever the committed recipe still refers to.
     *
     * **Refuses where the layer may not take one** — the foreground or background of the *global* default, since one
     * picture there stands in for every app's own artwork. `IconStudioState.canUseFixedSource` is the whole rule and
     * the UI omits the row by the same expression, so this is the guard behind the guard, as [browsePack]'s is.
     *
     * Checked **before the decode**, which is the point of doing it here rather than inside the update: a refused pick
     * should not read the file, reserve a path, or leave a bitmap in [unsaved] that nothing will ever write.
     */
    fun pickImage(uri: Uri) {
        if (!_state.value.canUseFixedSource) return
        viewModelScope.launch {
            val bitmap = customIcons.decode(uri) ?: return@launch
            val path = customIcons.reservePath()
            unsaved[path] = bitmap

            _state.update { current ->
                // Re-checked inside the update, because the selection can move between the picker opening and the
                // image coming back — and the rule is about *which layer* is selected, not only which studio this is.
                // The bitmap is still kept: it is already decoded, and `images` is what the preview reads.
                val source = LayerSource.CustomImage(path)
                val withImage = if (current.canUseFixedSource) current.replaceSelectedSource(source) else current
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
     * Inserts a new custom layer **directly beneath** the selected one, and selects it.
     *
     * Beneath rather than above, and the two senses of that agree, which is what makes it the right default: inserting
     * at the selected layer's own index puts the new layer **below it in the composite** *and* on the row directly
     * **under it in the list**, since `LayerStackRows` draws the stack top-first. So one rule reads correctly whether
     * the user is thinking about draw order or about what they are looking at.
     *
     * It also matches what a new layer is *for*. A fresh layer is an opaque fill, so above the selection it hides
     * whatever was just being worked on; below it, it appears as a backing behind it — which is the thing people
     * actually add a layer to do (a colored disc behind a legacy icon is the worked example in `ShapeControls`).
     *
     * **Empty, not a color.** A new layer used to arrive as a mid-gray fill, which meant adding one dropped an opaque
     * plate into the stack and changed the icon before the user had chosen anything. `LayerSource.Empty` draws nothing,
     * so the insert is visible in the stack and invisible on the canvas — and what goes in it is the next choice, made
     * in the Source section rather than assumed here.
     *
     * Selecting the new layer is what makes the insertion visible — the highlight moves down one row onto it, which is
     * the same "the selection follows the row" rule [removeSelected] keeps.
     */
    fun addLayer() = _state.update { current ->
        val insertAt = current.selected.coerceIn(0, current.editing.layers.size)
        val layers = current.editing.layers.toMutableList()
        layers.add(insertAt, IconLayerSpec(role = LayerRole.CUSTOM, source = LayerSource.Empty))
        // A key of its own for the new layer; every other layer keeps the one it had, which is what lets the rows
        // beneath it *slide* rather than being rebuilt in their new positions.
        val keys = current.layerKeys.toMutableList().apply { add(insertAt, nextLayerKey++) }
        current.withEditing(IconLayerSet(layers)).copy(selected = insertAt, layerKeys = keys).recordHistory()
    }

    /**
     * Deletes the selected layer. Custom layers only — the foreground and background are permanent, which the set
     * enforces in its own `init`, so removing one would throw rather than misbehave.
     *
     * **The selection stays on the same *row*, which is `selected - 1` and not `selected`.** That looks like an
     * off-by-one and is not, so it is worth stating plainly: `LayerStackRows` draws the stack **top layer first**, the
     * reverse of index order, because that is the order the layers are drawn on screen. So the layer that slides into
     * the deleted one's place *on screen* is the one **below** it in the model, and keeping the index instead would
     * move the highlight up a row onto the layer that was above — which reads as the selection jumping to a layer the
     * user did not pick.
     *
     * Clamped at 0 for the one case with no row beneath it: a custom layer below the background, where the selection
     * lands on the new bottom row.
     *
     * One consequence, and it is the better half of the trade: **repeated deletes no longer chain.** Landing a row
     * down usually means landing on the foreground or background, which cannot be deleted, so the button grays out
     * rather than staying armed over a layer the user never selected.
     */
    fun removeSelected() = _state.update { current ->
        if (!current.canRemoveSelected) return@update current
        val layers = current.editing.layers.toMutableList()
        layers.removeAt(current.selected)
        val keys = current.layerKeys.toMutableList().apply { removeAt(current.selected) }
        current.withEditing(IconLayerSet(layers))
            .copy(selected = (current.selected - 1).coerceAtLeast(0), layerKeys = keys)
            .recordHistory()
    }

    /** Steps back one recorded edit. */
    fun undo() = _state.update { current ->
        if (!current.canUndo) return@update current
        historyIndex--
        current.atHistoryStep()
    }

    /** Steps forward again after an [undo]. */
    fun redo() = _state.update { current ->
        if (!current.canRedo) return@update current
        historyIndex++
        current.atHistoryStep()
    }

    /** The recipe **and** the keys recorded at [historyIndex] — see [Step] for why undo needs both. */
    private fun IconStudioState.atHistoryStep(): IconStudioState = history[historyIndex].let { step ->
        copy(editing = step.set, layerKeys = step.keys).withHistoryFlags().withSelectionInRange()
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
     * The two are the same verb — "stop being customized" — pointed at different things, which is why the global
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
            _state.update { it.seatedOn(restored).copy(dirty = false) }
        }
    }

    /**
     * Editing the global default, previewed on a real app.
     *
     * The sample opens on the first installed app, which is arbitrary and only has to be *some* real app — a recipe
     * drawn over nothing shows nothing. Which one it stays on is [shuffleSample]'s.
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
            _state.update {
                it.seatedOn(stored).copy(
                    subject = StudioSubject.Global(sample?.componentKey),
                    label = null,
                )
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
            _state.update {
                it.seatedOn(stored).copy(
                    subject = StudioSubject.App(component),
                    label = label,
                    // Keyed by pack, but resolved *per app* — so anything cached here belongs to whichever app was
                    // open before. Only reachable since the studio learned to change app without being reopened; see
                    // [shuffleSample], which clears it for the same reason.
                    packImages = emptyMap(),
                )
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

    /**
     * One recorded step: the recipe, and the layer keys that went with it.
     *
     * **The keys are in history because undo has to be animatable too.** Restoring a set without them would mean
     * regenerating identities, so every row would look new to Compose and a step back would re-assemble the whole list
     * instead of sliding it back the way it came. They are deliberately *not* part of the comparison — [recordHistory]
     * asks whether the recipe changed, and a key is not part of the recipe.
     */
    private class Step(val set: IconLayerSet, val keys: List<Long>)

    /** Recorded states, oldest first. Always non-empty: index 0 is what the studio opened with. */
    private var history: List<Step> = listOf(Step(IconLayerSet.Base, emptyList()))
    private var historyIndex = 0

    /**
     * The next unused layer key. Monotonic and never reused, which is the whole requirement — a key that came back
     * would make Compose treat a new layer as an old one and animate it from wherever that one was.
     */
    private var nextLayerKey = 0L

    /** Fresh keys for a whole stack — every point where a set arrives from outside rather than being edited. */
    private fun freshKeys(count: Int): List<Long> = List(count) { nextLayerKey++ }

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
    private fun resetHistory(set: IconLayerSet, keys: List<Long>) {
        history = listOf(Step(set, keys))
        historyIndex = 0
    }

    /**
     * Seats a set that arrived from outside the editor — an open, a reset, a preset — with fresh keys, the foreground
     * selected, and history restarted on it.
     *
     * **The foreground is the default selection, not index 0.** Every set has both permanent layers, and the
     * background is the one at index 0 — so opening on it meant the studio always started pointed at the layer *behind*
     * the artwork, which is not what anyone came to edit. The foreground is the app's own icon, and it is what "edit
     * this icon" means before the user says otherwise.
     */
    private fun IconStudioState.seatedOn(set: IconLayerSet): IconStudioState {
        val keys = freshKeys(set.layers.size)
        resetHistory(set, keys)
        return copy(editing = set, layerKeys = keys, selected = set.foregroundIndex).withHistoryFlags()
    }

    /**
     * Records the current recipe as an undo step, unless it is identical to the last one.
     *
     * **Redo is discarded on a new edit**, which is the standard and the only coherent option: once the user
     * branches, the states that used to be ahead describe a future that no longer follows from the present.
     */
    private fun IconStudioState.recordHistory(): IconStudioState {
        if (editing == history[historyIndex].set) return withHistoryFlags()
        history = history.take(historyIndex + 1) + Step(editing, layerKeys)
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

}
