package inkspire.morphic.feature.settings.iconstudio

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.icon.ContentAnchor
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.key
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.icons.CustomIconStore
import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.icons.IconPackManager
import inkspire.morphic.data.settings.IconPreset
import inkspire.morphic.data.settings.IconStudioWorkspace
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.wallpaper.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    private val wallpaperRepository: WallpaperRepository,
    private val overrideRepository: IconOverrideRepository,
    private val parsedIcons: ParsedIconLoader,
    private val appRepository: AppRepository,
    private val customIcons: CustomIconStore,
    private val iconPacks: IconPackManager,
) : ViewModel() {

    private val _state = MutableStateFlow(IconStudioState())

    /** Where a fill lands on a layer that has never held one — see [pickSolidFill]. */
    private val DefaultFillArgb = 0xFF000000.toInt()

    val state: StateFlow<IconStudioState> = _state.asStateFlow()

    init {
        // Above the route branch, because the canvas exists in all three cases — including the picker's, which floats
        // over it rather than replacing it. The workspace is the same canvas's arrangement, so it is seeded here too.
        observeBackground()
        observeWorkspace()

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
        val appearance = _state.value.appearance
        viewModelScope.launch { settingsRepository.saveIconPreset(trimmed, appearance) }
    }

    /**
     * Loads [preset] into the editor. An ordinary edit: recorded in history, undoable, and not saved.
     *
     * A whole new stack arrives, so its layers get **fresh keys** — nothing in it continues a layer that was there
     * before, and reusing a key would animate an unrelated row into its place. Selection goes to the foreground for the
     * same reason a fresh open does: the previous index means nothing in a stack the user did not build.
     */
    fun loadPreset(preset: IconPreset) = _state.update { current ->
        val keys = freshKeys(preset.appearance.layerSet.layers.size)
        // **The plate and the zoom come with it**, a preset being a whole look rather than its layers — see
        // `IconAppearance`. They are *not* in history, which records the layer set alone: nothing edits them yet,
        // so there is nothing to step back through. The screen that does will widen history with it.
        current.withEditing(preset.appearance.layerSet)
            .copy(
                layerKeys = keys,
                target = StudioTarget.Composite,
                plate = preset.appearance.plate,
                zoom = preset.appearance.zoom,
            )
            .recordHistory()
    }

    /** Removes a saved preset. Does not touch anything it was applied to — a preset is a copy, not a link. */
    fun deletePreset(name: String) {
        viewModelScope.launch { settingsRepository.deleteIconPreset(name) }
    }

    /**
     * Renames a saved preset, keeping its place in the library.
     *
     * **Refuses in the individual studio** for [savePreset]'s reason, one step further on: a library that cannot be
     * added to there should not be editable there either, or the same panel would offer two of the three verbs and
     * refuse the third. The guard behind the guard, as ever — the menu is absent there too.
     */
    fun renamePreset(from: String, to: String) {
        if (_state.value.subject !is StudioSubject.Global) return
        viewModelScope.launch { settingsRepository.renameIconPreset(from, to) }
    }

    /**
     * Moves to the step that shows what applying this session would do, and back again.
     *
     * **Neither direction commits or discards anything**, which is what makes the way back cheap: the recipe, the
     * plate, the zoom and the whole history are one session's state, and a step is which half of it is on screen.
     */
    fun toFinalize() = _state.update { it.copy(step = StudioStep.FINALIZE) }

    fun toEdit() = _state.update { it.copy(step = StudioStep.EDIT) }

    /**
     * The plate behind the icon: whether it draws, and the silhouette it is cut to.
     *
     * **Not recorded in history, and that is deliberate rather than pending.** History is the *recipe* — what the
     * layer tools build — and the finalize step's three controls are one tap or one drag each, with the result on
     * screen across every icon while it happens. Undo stepping back through a switch would also have to step back
     * through it *from the editor*, where the plate is not visible at all.
     *
     * **Turning it on seeds a rounded square**, because the model's own default is no shape at all — which is the
     * right default for a stored recipe (it is what every one of them was written against) and the wrong one for a
     * control someone just switched on: a hard-edged square plate reads as the setting being broken. The same split
     * `ContentAnchor` records, one screen over.
     */
    fun setPlateEnabled(enabled: Boolean) = _state.update { current ->
        val shape = current.plate.shape ?: IconShapes.RoundedSquare.takeIf { enabled }
        current.copy(plate = current.plate.copy(enabled = enabled, shape = shape)).withDirty()
    }

    fun setPlateShape(shape: IconShape?) = _state.update {
        it.copy(plate = it.plate.copy(shape = shape)).withDirty()
    }

    /** How large the artwork sits inside its own box — the icon's size *relative to its plate*. */
    fun setZoom(zoom: Float) = _state.update { it.copy(zoom = zoom).withDirty() }

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

    /**
     * Moves the workspace — the preview's pan and zoom, or where the layer rail sits — **without writing anything**.
     *
     * Called every frame of a pinch or a rail drag, which is exactly why it does not persist: a gesture is sixty of
     * these a second and a settings slice is one JSON document rewritten whole. [commitWorkspace] is the other half.
     *
     * **It touches neither `dirty` nor history, and that is the point rather than an omission.** The viewport is not
     * part of the recipe — it is where the paper is lying, not the drawing — so panning the icon must not light up
     * Save, and undo must not step back through a pinch on the way to the edit before it. That is the same line
     * `background` sits on, and the reason both are fields of their own rather than anything inside `editing`.
     */
    fun setWorkspace(workspace: IconStudioWorkspace) = _state.update { it.copy(workspace = workspace) }

    /**
     * Remembers wherever the workspace was left. One write per gesture, at the end of it.
     *
     * The same optimistic shape as [cycleBackground] — the state has already moved, so this only catches the store up
     * — and [observeWorkspace]'s echo is a no-op for the same reason.
     */
    fun commitWorkspace() {
        val workspace = _state.value.workspace
        viewModelScope.launch { settingsRepository.setIconStudioWorkspace(workspace) }
    }

    /**
     * Puts the preview back where it started — nothing panned, nothing zoomed — and remembers that.
     *
     * **Discrete, so it writes at once**, unlike [setWorkspace]: there is no gesture to punctuate, which is the same
     * line every source tile and every slider reset in the studio sits on.
     *
     * It leaves the **layer rail** where the user put it; see `IconStudioWorkspace.withPreviewReset` for why those are
     * two arrangements rather than one.
     */
    fun resetPreviewView() {
        _state.update { it.copy(workspace = it.workspace.withPreviewReset()) }
        commitWorkspace()
    }

    /**
     * Turns the layer rail from a column into a row, or back.
     *
     * **A toggle rather than a setter**, because the menu row that drives it names the *other* arrangement ("Lay out
     * as a row") — so the caller has nothing to say that the current value does not already answer. Same shape as the
     * quick menu's Hide/Show.
     *
     * Discrete, so it writes at once. Like every workspace command it leaves the recipe alone: rearranging the rail is
     * not an edit and must neither light up Save nor land in undo.
     */
    fun toggleRailAxis() {
        _state.update { it.copy(workspace = it.workspace.copy(railAxis = it.workspace.railAxis.flipped)) }
        commitWorkspace()
    }

    /**
     * Cuts the rail's list of layers down to one tile's worth of viewport, or opens it back up.
     *
     * It shrinks the *window*, never the list — see `IconStudioWorkspace.railCollapsed`.
     *
     * @see toggleRailAxis
     */
    fun toggleRailCollapsed() {
        _state.update { it.copy(workspace = it.workspace.copy(railCollapsed = !it.workspace.railCollapsed)) }
        commitWorkspace()
    }

    /**
     * Seeds the workspace from the store, and **once only**, which is where it differs from [observeBackground].
     *
     * A backdrop is changed by discrete taps, so collecting it forever is harmless: an echo lands on the value already
     * held. The workspace is *dragged*, so a late emission arriving mid-gesture would yank the icon back to where the
     * last commit put it — the divergence that makes projecting `editing` wrong, on a field that looked safe because
     * its neighbour is. So this reads the first value and then the screen owns it, which is the studio's usual detach.
     */
    private fun observeWorkspace() {
        viewModelScope.launch {
            val stored = settingsRepository.iconStudioWorkspace.first()
            _state.update { it.copy(workspace = stored) }
        }
    }

    /**
     * Points the controls at a layer or at the finished icon. Pure selection — nothing about the recipe changes, so
     * no history entry.
     *
     * One command for both, because the rail is one control: a tile is a tile whether it draws a layer or the whole
     * stack, and splitting this would make the caller decide which kind it just handled.
     */
    fun selectTarget(target: StudioTarget) = _state.update { it.copy(target = target) }

    /**
     * Applies [transform] to the selected layer **without recording history** — the live-edit path.
     *
     * Every frame of a slider drag comes through here. Recording each one would make undo useless (a hundred steps
     * back through one gesture), so history is punctuated by [commitEdit] instead, which the controls call when a
     * gesture ends.
     */
    fun updateSelected(transform: (IconLayerSpec) -> IconLayerSpec) = _state.update { current ->
        val index = current.selected ?: return@update current
        val layers = current.editing.layers.toMutableList()
        val spec = layers.getOrNull(index) ?: return@update current
        layers[index] = transform(spec)
        current.withEditing(current.editing.copy(layers = layers))
    }

    /**
     * Applies [transform] to whichever effect list the target owns — the selected layer's, or the whole icon's.
     *
     * **One command rather than two, and the target is what decides.** The panel behind it is the same panel either
     * way (see `EffectsControls`), so a caller that had to pick would be re-deciding something the rail already
     * settled — and the two would eventually disagree about which one a given tap meant.
     *
     * Live, like [updateSelected]: every frame of a slider drag arrives here and [commitEdit] punctuates it.
     */
    fun updateEffects(transform: (List<LayerEffect>) -> List<LayerEffect>) = _state.update { current ->
        when (val target = current.target) {
            StudioTarget.Composite ->
                current.withEditing(current.editing.copy(effects = transform(current.editing.effects)))

            is StudioTarget.Layer -> {
                val layers = current.editing.layers.toMutableList()
                val spec = layers.getOrNull(target.index) ?: return@update current
                layers[target.index] = spec.copy(effects = transform(spec.effects))
                current.withEditing(current.editing.copy(layers = layers))
            }
        }
    }

    /**
     * Points whichever thing the target names — the selected layer, or the finished icon — in a direction: turned in
     * the plane, and leaned out of it about each axis.
     *
     * **All three at once, and one command for both targets**, which is [updateEffects]' shape for its reason: they
     * are the same three sliders either way (see `OrientationSliders`), so a caller that had to choose which holder
     * it meant would be re-deciding what the rail already settled. Taking them together also means neither holder
     * is ever handed a partial update to merge.
     *
     * Live, like [updateSelected] — every frame of a drag arrives here and [commitEdit] punctuates it. That is why
     * the layer's position and zoom still go through [updateSelected] rather than gaining commands of their own:
     * they have one holder, so there is nothing to dispatch.
     */
    fun setOrientation(rotation: Float, tiltX: Float, tiltY: Float) = _state.update { current ->
        when (val target = current.target) {
            StudioTarget.Composite ->
                current.withEditing(current.editing.copy(rotation = rotation, tiltX = tiltX, tiltY = tiltY))

            is StudioTarget.Layer -> {
                val layers = current.editing.layers.toMutableList()
                val spec = layers.getOrNull(target.index) ?: return@update current
                layers[target.index] = spec.copy(rotation = rotation, tiltX = tiltX, tiltY = tiltY)
                current.withEditing(current.editing.copy(layers = layers))
            }
        }
    }

    /**
     * Puts [shape] on whichever silhouette the target owns — the selected layer's, or the whole icon's.
     *
     * **One command for both, dispatched on the target, exactly as [updateEffects] is**, and for the same reason: the
     * chooser behind it is the same grid either way, so a caller that had to pick which write it meant would be
     * re-deciding what the rail already settled.
     *
     * **Picking a shape turns the layer's anchor to [ContentAnchor.CONTENT], which is a real behavior change and not
     * a default.** Cutting against the box is what a *plate* wants — a fixed silhouette with the artwork sliding
     * under it — but that is not what someone reaching for this section is usually after: they want the icon they
     * can see trimmed to that outline, and against the box an app whose artwork sits small and off-center is cropped
     * by a shape that never touches it, which reads as the control being broken. So the useful anchor is the one a
     * pick lands on, and the switch beneath is how the plate reading is asked for. The model's default stays
     * [ContentAnchor.BOX], because that is what a spec carrying *no* shape means and what every stored recipe was
     * written against.
     *
     * Nothing for the composite to anchor — it has neither ink nor a transform — so that arm writes the shape alone.
     * See `IconLayerSet.shape`.
     *
     * Discrete, so it records history at once, the shape [toggleSelectedVisible] and [pickPack] take.
     */
    fun pickShape(shape: IconShape?) {
        _state.update { current ->
            when (val target = current.target) {
                StudioTarget.Composite -> current.withEditing(current.editing.copy(shape = shape))

                is StudioTarget.Layer -> {
                    val layers = current.editing.layers.toMutableList()
                    val spec = layers.getOrNull(target.index) ?: return@update current
                    layers[target.index] = spec.copy(
                        shape = shape,
                        // Left alone when the shape is cleared: there is nothing to anchor, so writing here would
                        // only be forgetting what to return to if a shape is picked again.
                        shapeAnchor = if (shape != null) ContentAnchor.CONTENT else spec.shapeAnchor,
                    )
                    current.withEditing(current.editing.copy(layers = layers))
                }
            }
        }
        commitEdit()
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
     * Turns size normalization on or off for the selected layer — see [IconLayerSpec.normalize].
     *
     * A command for [toggleSelectedMonochrome]'s reason, and it sits beside it in the Source panel for the same one:
     * both refine *the app's own artwork* rather than choosing whose artwork it is. Unguarded on the source, unlike
     * that one, because the field is inert everywhere it does not apply — the resolver consults it only on the
     * foreground's app-artwork arms — so a stray call changes a value nothing reads.
     */
    fun toggleSelectedNormalize() {
        updateSelected { spec -> spec.copy(normalize = !spec.normalize) }
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
     *
     * Either direction is remembered ([foregroundMonochrome]), so *off* survives a trip through another source
     * exactly as *on* does — the memory is the last form the layer had, not a latch that only ever turns on.
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
     * Points the selected layer back at the app's own artwork — the "System default" tile.
     *
     * **A command rather than a source the UI writes, because *which* app-default form to return to is the
     * ViewModel's to know.** On the foreground it restores [foregroundMonochrome]: leaving the app's own artwork for a
     * pack or an image and coming back lands on the form the layer was in, instead of silently dropping the refinement
     * the row beneath the tiles controls. A tile that quietly resets a control one row away is the worst kind of side
     * effect — the tile looks selected before the press and after it, so nothing on screen says what changed.
     *
     * On the background there is no monochrome to restore, so it is plain [LayerSource.AppDefault]; the refinement is
     * foreground-only, the platform shipping one silhouette and it being for that slot.
     *
     * Pressing it while the layer already shows that form writes an identical set, which `recordHistory` dedupes away
     * — so it is a no-op without needing a guard of its own.
     */
    fun pickAppDefault() {
        updateSelected { spec ->
            val monochrome = spec.role == LayerRole.FOREGROUND && foregroundMonochrome
            spec.copy(source = if (monochrome) LayerSource.AppDefaultMonochrome else LayerSource.AppDefault)
        }
        commitEdit()
    }

    /**
     * Fills the selected layer with a flat color — the "Solid color" row.
     *
     * **Returns to the color that layer was last filled with** ([layerFills]), for [pickAppDefault]'s reason applied to
     * a value rather than to a form: leaving a fill for a pack or an image and coming back should land where it was,
     * not on black. Black is only where a fill *arrives* on a layer that has never had one — the one value nobody
     * mistakes for a color that was already chosen, so the swatch row below reads as the next step.
     *
     * **A no-op when the layer already shows a fill**, so pressing the row twice cannot throw away the color under it.
     * Guarded rather than left to `recordHistory`'s dedupe, because this one would write a *different* set.
     *
     * Refuses where the layer may not take a fixed source, behind the same rule that omits the row — see [pickImage].
     */
    fun pickSolidFill() {
        val current = _state.value
        if (!current.canUseFixedSource) return
        val remembered = current.selected?.let(current.layerKeys::getOrNull)?.let(layerFills::get)
        updateSelected { spec ->
            if (spec.source is LayerSource.SolidFill) spec
            else spec.copy(source = LayerSource.SolidFill(remembered ?: DefaultFillArgb))
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
        val index = current.selected ?: return@update current
        val moved = if (up) current.editing.moveUp(index) else current.editing.moveDown(index)
        if (moved === current.editing) return@update current
        val destination = if (up) index + 1 else index - 1
        // The keys swap with the layers, so both rows keep their identity and glide past each other rather than
        // swapping contents in place.
        val keys = current.layerKeys.toMutableList().apply {
            if (index in indices && destination in indices) {
                val held = this[index]
                this[index] = this[destination]
                this[destination] = held
            }
        }
        current.withEditing(moved).copy(target = StudioTarget.Layer(destination), layerKeys = keys).recordHistory()
    }

    /**
     * Imports [uri] as the selected layer's artwork.
     *
     * **Nothing is written to disk here.** The image is decoded, kept in [IconStudioState.images] under a
     * reserved path, and drawn from memory — so backing out of the studio leaves no file behind, which is the
     * bug L1 recorded and accepted. [save] writes whatever the committed recipe still refers to.
     *
     * **Refuses where the layer may not take one** — the *global* default's foreground, since one picture there stands
     * in for every app's own artwork. `IconStudioState.canUseFixedSource` is the whole rule and the UI omits the row by
     * the same expression, so this is the guard behind the guard, as [browsePack]'s is. The global **background** is
     * not refused: the glyph above it still identifies the app, so a shared plate restyles every icon rather than
     * replacing it.
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

    /**
     * Which way the device is held, for the wallpaper the plate samples.
     *
     * The backdrop cannot derive it — a **rotating** wallpaper is two pictures, so "the wallpaper" is not one image
     * until you say which orientation — and this is `ShellViewModel`'s own reasoning, reported the same way: the
     * composable is where the window is.
     */
    private val orientation = MutableStateFlow(Orientation.PORTRAIT)

    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The wallpaper as a frosted **panel** samples it, and the color its wash is blended toward.
     *
     * **The studio needs its own, because it is not inside the shell** — `LocalBackdrop` is provided at that zone
     * boundary, and this is a destination beyond it. Without this the plate on the finalize step drew its *scrim*: a
     * flat gray square, on the one screen whose whole job is judging blurred wallpaper behind an icon. The picture is
     * the panel one rather than the full-screen film's, because a plate is a small surface and that is the strength
     * the user chose for those.
     *
     * A third reader of the same repository question is worth noting rather than hiding: if a fourth appears, this
     * belongs beside `ProvideIconRecipes` in `app`, which is already where a launcher-wide read is assembled.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBackdrop() {
        viewModelScope.launch {
            settingsRepository.backdropEffect.collect { effect ->
                _state.update { it.copy(backdropEffect = effect) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backdropEffect
                .map { it.blurStrength }
                .distinctUntilChanged()
                .flatMapLatest { wallpaperRepository.backdrop(it, orientation) }
                .collect { image -> _state.update { it.copy(backdropImage = image) } }
        }
        viewModelScope.launch {
            wallpaperRepository.accentColor.collect { accent ->
                _state.update { it.copy(backdropAccent = accent) }
            }
        }
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
        val index = selected ?: return this
        val layers = editing.layers.toMutableList()
        val spec = layers.getOrNull(index) ?: return this
        layers[index] = spec.copy(source = source)
        return withEditing(editing.copy(layers = layers))
    }

    /**
     * Inserts a new custom layer **directly beneath** the selected one, and selects it.
     *
     * Beneath rather than above, and the two senses of that agree, which is what makes it the right default: inserting
     * at the selected layer's own index puts the new layer **below it in the composite** *and* on the row directly
     * **under it in the list**, since `StudioLayerRail` draws the stack top-first. So one rule reads correctly whether
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
        // With the whole icon selected there is no layer to go beneath, so the new one goes on **top** of the stack
        // — which is beneath the composite in exactly the sense above, since the composite is what sits over
        // everything. One rule, read from wherever the selection happens to be.
        val insertAt = (current.selected ?: current.editing.layers.size)
            .coerceIn(0, current.editing.layers.size)
        val layers = current.editing.layers.toMutableList()
        layers.add(insertAt, IconLayerSpec(role = LayerRole.CUSTOM, source = LayerSource.Empty))
        // A key of its own for the new layer; every other layer keeps the one it had, which is what lets the rows
        // beneath it *slide* rather than being rebuilt in their new positions.
        val keys = current.layerKeys.toMutableList().apply { add(insertAt, nextLayerKey++) }
        current.withEditing(current.editing.copy(layers = layers))
            .copy(target = StudioTarget.Layer(insertAt), layerKeys = keys)
            .recordHistory()
    }

    /**
     * Deletes the selected layer. Custom layers only — the foreground and background are permanent, which the set
     * enforces in its own `init`, so removing one would throw rather than misbehave.
     *
     * **The selection stays on the same *row*, which is `selected - 1` and not `selected`.** That looks like an
     * off-by-one and is not, so it is worth stating plainly: `StudioLayerRail` draws the stack **top layer first**, the
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
        val index = current.selected ?: return@update current
        if (!current.canRemoveSelected) return@update current
        val layers = current.editing.layers.toMutableList()
        layers.removeAt(index)
        val keys = current.layerKeys.toMutableList().apply { removeAt(index) }
        current.withEditing(current.editing.copy(layers = layers))
            .copy(target = StudioTarget.Layer((index - 1).coerceAtLeast(0)), layerKeys = keys)
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
        withEditing(step.set).copy(layerKeys = step.keys).withHistoryFlags().withSelectionInRange()
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

            val appearance = current.appearance
            when (val subject = current.subject) {
                is StudioSubject.Global -> settingsRepository.setIconAppearance(appearance)
                is StudioSubject.App -> overrideRepository.set(subject.component, appearance)
                StudioSubject.Unchosen -> return@launch
            }
            _state.update { it.copy(dirty = false) }
            saved = appearance
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
            addAll(settingsRepository.iconAppearance.first().layerSet.imagePaths())
            overrideRepository.overrides.first().values.forEach { addAll(it.layerSet.imagePaths()) }
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
                // The plate goes with it: "stop being customized" is one verb over the whole appearance, and a
                // reset that left glass behind every icon would be a reset the user could see it had not done.
                is StudioSubject.Global ->
                    IconAppearance.Base.also { settingsRepository.setIconAppearance(it) }

                is StudioSubject.App -> {
                    overrideRepository.clear(subject.component)
                    settingsRepository.iconAppearance.first()
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
                ?.let { name -> settingsRepository.iconPresets.first().firstOrNull { it.name == name }?.appearance }
                ?: settingsRepository.iconAppearance.first()
            val apps = appRepository.observeApps().first()
            val sample = apps.firstOrNull()
            // **Who this actually changes**, which is not "every app installed": an app with a recipe of its own is
            // detached and a global edit passes it by, so listing it on the finalize step would be a lie about what
            // Apply is going to do. Read once, here, rather than observed — the step is a snapshot of a decision.
            val detached = overrideRepository.overrides.first().keys
            // `saved` is what is *persisted*, which a preset-loaded session deliberately is not — so it opens
            // dirty, and Save is what applies the preset. Same shape as an inheriting app opening dirty.
            saved = settingsRepository.iconAppearance.first()
            _state.update {
                it.seatedOn(stored).copy(
                    subject = StudioSubject.Global(sample?.componentKey),
                    label = null,
                    affected = apps.filterNot { app -> app.componentKey in detached },
                )
            }
            loadStoredImages(stored.layerSet)
            loadPacks()
            observePresets()
            observeBackdrop()
            sample?.componentKey?.let(::loadArtwork)
            loadPackArtwork()
        }
    }

    private fun openApp(component: ComponentKey) {
        viewModelScope.launch {
            // The override if there is one, otherwise a copy of the global default — which is the snapshot half of
            // snapshot-detach. Nothing is written here: an app that was inheriting still inherits until a commit.
            val stored = overrideRepository.overrides.first()[component]
                ?: settingsRepository.iconAppearance.first()
            val app = appRepository.observeApps().first().firstOrNull { it.componentKey == component }

            // `saved` is what is *persisted*, which for an app that is still inheriting is not the same as what is
            // shown: the studio opens on a copy of the global default, and that copy is already an unsaved change.
            // So a freshly opened inheriting app is `dirty`, correctly — saving it is what detaches it.
            saved = overrideRepository.overrides.first()[component] ?: IconAppearance.Base
            _state.update {
                it.seatedOn(stored).copy(
                    subject = StudioSubject.App(component),
                    label = app?.label,
                    // One app today; a list because this route is meant to gain a multi-app picker, and the finalize
                    // step is already written against "the apps about to change" rather than against a subject.
                    affected = listOfNotNull(app),
                    // Keyed by pack, but resolved *per app* — so anything cached here belongs to whichever app was
                    // open before. Only reachable since the studio learned to change app without being reopened; see
                    // [shuffleSample], which clears it for the same reason.
                    packImages = emptyMap(),
                )
            }
            loadStoredImages(stored.layerSet)
            loadPacks()
            observePresets()
            observeBackdrop()
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
    /**
     * What is persisted for the subject, which is what [IconStudioState.dirty] is measured against.
     *
     * **A whole [IconAppearance] now that the finalize step can edit the plate and the zoom.** It held the layer set
     * alone while those two only rode along — correct then, and wrong the moment a control could change one: a
     * plated icon would have read as clean and the commit would have looked unnecessary.
     */
    private var saved: IconAppearance = IconAppearance.Base

    /**
     * Picked images that exist only in memory, by the path they will be written to.
     *
     * Not in the state, because nothing renders *from* this — the same bitmaps are in
     * [IconStudioState.images], which is what the preview reads. This is the narrower question of which of them
     * still owe a write, and keeping it out of the state means undo cannot rewind it: a path that has been
     * undone past is simply never written, and the sweep tidies it if it was.
     */
    private val unsaved = mutableMapOf<String, Bitmap>()

    /**
     * This state, with [IconStudioState.dirty] answered against what is stored.
     *
     * One expression, because there are three ways to reach the question — an edit, a history step, and a whole-icon
     * control — and three comparisons would be three chances to compare the wrong halves.
     */
    private fun IconStudioState.withDirty(): IconStudioState = copy(dirty = appearance != saved)

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
        return withEditing(set).copy(layerKeys = keys, target = StudioTarget.Composite).withHistoryFlags()
    }

    /**
     * The same, for a whole [IconAppearance] — which is what every one of those three paths actually reads from a
     * store now.
     *
     * **The plate and the zoom are seated beside the recipe rather than merged into history**, because the studio
     * cannot yet edit them: they arrive with the appearance, ride along untouched, and go back out on save. Seating
     * them here rather than at each call site is what stops one of the three forgetting — an app opened, edited and
     * saved without its plate would come out of the studio having silently lost its glass.
     */
    private fun IconStudioState.seatedOn(appearance: IconAppearance): IconStudioState =
        seatedOn(appearance.layerSet).copy(plate = appearance.plate, zoom = appearance.zoom)

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

    /**
     * Applies a new recipe without recording it — the live path; see [updateSelected].
     *
     * **Every recipe change passes through here, which is what makes [noteMonochromeForm] a rule rather than a habit.**
     * [seatedOn] and [atHistoryStep] were routed through it for that — an open, a preset and an undo now remember what
     * a source pick remembers, without each of them having to say so. Both recompute `dirty` immediately afterwards,
     * so passing through costs them nothing.
     */
    private fun IconStudioState.withEditing(set: IconLayerSet): IconStudioState {
        noteMonochromeForm(set)
        noteFillColors()
        return copy(editing = set).withDirty()
    }

    /**
     * Whether the foreground was last showing the **monochrome** form of the app's own artwork.
     *
     * **Editor memory, not part of the recipe** — which is why it is a field here rather than in the state, beside
     * [saved] and [unsaved] for their reason: nothing renders from it. What it buys is that leaving the app's own
     * artwork for a pack or an image and coming back does not turn monochrome off behind the user's back. Monochrome is
     * a *refinement of* that source rather than a peer of it, so returning to the source returns to the form it was in;
     * without the memory the round trip is a hidden reset. See [pickAppDefault], its one reader.
     *
     * **One flag rather than one per layer**, because the refinement is foreground-only and a set has exactly one
     * foreground — `IconLayerSet`'s own `init` requires it.
     */
    private var foregroundMonochrome = false

    /**
     * Keeps [foregroundMonochrome] on the last app-default form the foreground actually had.
     *
     * **Recorded on arrival rather than captured on departure**, which is what makes a single hook enough: every path
     * that can set a source — a tile, the toggle, a preset, an open, a reset, an undo — hands the resulting set to
     * [withEditing], so none of them has to remember to save anything first. Any other source leaves the flag alone,
     * which is the whole point: that is precisely the trip the memory exists to survive.
     */
    private fun noteMonochromeForm(set: IconLayerSet) {
        when (set.foreground.source) {
            LayerSource.AppDefaultMonochrome -> foregroundMonochrome = true
            LayerSource.AppDefault -> foregroundMonochrome = false
            else -> Unit
        }
    }

    /**
     * The color each layer was last filled with, by its layer key — [foregroundMonochrome]'s counterpart for a value
     * that is per layer rather than per role, and read by [pickSolidFill].
     *
     * **Keyed rather than indexed, because an index is exactly what an insert moves.** `IconStudioState.layerKeys` is
     * the identity that already survives every reorder and insert (see its KDoc for why the model cannot carry one),
     * so this rides on it.
     *
     * **Nothing is ever evicted, and it cannot go stale**: keys come from a monotonic counter that never reuses a
     * value, so an entry left behind by a deleted layer — or by a whole stack a preset replaced — is unreadable rather
     * than wrong. What it costs is one `Int` per layer that has ever held a fill, for the life of the screen.
     */
    private val layerFills = mutableMapOf<Long, Int>()

    /**
     * Records every layer's current fill color, from the state **being replaced**.
     *
     * **This reads the receiver, not the incoming set, and that is the whole reason it is correct here.** A key belongs
     * to a layer by *position*, and the two lists only agree on the state that already holds both — during a structural
     * edit `withEditing` is handed new layers while the receiver still carries the old keys, so pairing the *new* set
     * with them would file a color under its neighbor's identity.
     *
     * Capturing on departure loses nothing, because leaving a fill is itself an edit: the tile, the pack and the image
     * all pass through here, so the last color a layer showed is recorded on the way out.
     */
    private fun IconStudioState.noteFillColors() {
        editing.layers.forEachIndexed { index, spec ->
            val fill = spec.source as? LayerSource.SolidFill ?: return@forEachIndexed
            layerKeys.getOrNull(index)?.let { key -> layerFills[key] = fill.argb }
        }
    }

    private fun IconStudioState.withHistoryFlags(): IconStudioState =
        copy(canUndo = historyIndex > 0, canRedo = historyIndex < history.lastIndex).withDirty()

    /** Keeps the selection valid when a step through history changes how many layers there are. */
    private fun IconStudioState.withSelectionInRange(): IconStudioState =
        when (val target = target) {
            // The composite survives any stack, so there is nothing to clamp.
            StudioTarget.Composite -> this
            is StudioTarget.Layer ->
                copy(target = StudioTarget.Layer(target.index.coerceIn(0, editing.layers.lastIndex.coerceAtLeast(0))))
        }

}
