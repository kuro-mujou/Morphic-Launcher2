package inkspire.morphic.feature.settings.iconstudio

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.graphics.RectangleShape
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.picker.AppPicker
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.icon.compose.IconLayerStack
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.key
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The icon studio: a full-bleed live preview with floating surfaces over it.
 *
 * **A creative workspace, not a settings form** — which is the whole reason it is a destination of its own rather
 * than another pane. L1 built its editor out of settings-list vocabulary (stacked commit sliders, emoji category
 * chips, "Up / Down / Remove" text buttons) and its own docs conclude that everything the studio *did* was right and
 * only the presentation was wrong. So this starts from the presentation L1 arrived at rather than the one it began
 * with.
 *
 * **The canvas is the Haze source and everything else floats on it.** One `hazeState` for the screen, and every
 * floating surface uses the shared [studioSurface] material, so a new panel cannot arrive looking different from the
 * rest. See that modifier for why this is Haze and not the launcher's own `wallpaperBackdrop`.
 *
 * **The layout is a drawing app's, and it was reached by taking the first cut's chrome out rather than reshaping it.**
 * That cut had every control the studio has today, and it worked — but it presented them as one permanent bottom sheet
 * with a segmented tab strip inside it, which is settings-list vocabulary again and the one thing this screen exists
 * not to be. What replaced it: the work full-bleed, leave at one top corner, the session's immediate actions at the
 * other, and a **tool rail** along the bottom whose entries each open one panel over it ([StudioTool],
 * [StudioToolPanel]).
 *
 * **Nothing on this screen is both a panel and an action.** The rail holds only what needs a surface to work on;
 * undo, redo, save and the background cycle act the instant they are pressed, so they live in the corners where a
 * panel opening cannot move them out from under a finger. That one line is what decides where anything new goes.
 *
 * Not here yet: a **shadow** effect (deferred — it is the one effect that could not be matched across both render
 * paths below API 31).
 */
@Composable
fun IconStudioScreen(
    route: IconStudioRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: IconStudioViewModel = koinViewModel { parametersOf(route) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    // Which section's panel is showing, or null for none. Screen state and nothing more — the recipe does not care
    // which tool is open, and neither does undo, so it stays out of the ViewModel.
    var tool by remember { mutableStateOf<StudioTool?>(null) }

    // The full color picker is hosted here rather than where it is asked for, and takes the tool panel's slot when it
    // is up. See [StudioColorPickerHost] for why a control cannot render inside the section that opens it.
    val colorPicker = remember { StudioColorPickerHost() }

    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickImage(uri)
    }

    // One value rather than fifteen parameters down two signatures — see [StudioActions]. Remembered so the panel is
    // handed the same instance every frame; keyed on what it actually closes over, which is the ViewModel, the picker
    // launcher, and which of the two studios this is.
    //
    // **Two commands turn on that one fact, in opposite directions**, which is why it is one value and not two flags:
    // browsing a pack for a *named* drawable is individual-only (a name on the global default would be handed to every
    // app), and naming a preset is global-only (a look tuned against one app tends to contain that app — see
    // `PresetsControls`). Both are absent rather than disabled, per the settings sections' rule.
    val editingOneApp = state.subject is StudioSubject.App
    val actions = remember(viewModel, imagePicker, editingOneApp) {
        StudioActions(
            selectLayer = viewModel::selectLayer,
            update = viewModel::updateSelected,
            commit = viewModel::commitEdit,
            toggleVisible = viewModel::toggleSelectedVisible,
            move = viewModel::moveSelected,
            addLayer = viewModel::addLayer,
            removeLayer = viewModel::removeSelected,
            pickImage = { imagePicker.launch(imageRequest) },
            pickAppDefault = viewModel::pickAppDefault,
            pickSolidFill = viewModel::pickSolidFill,
            toggleMonochrome = viewModel::toggleSelectedMonochrome,
            toggleNormalize = viewModel::toggleSelectedNormalize,
            pickPack = viewModel::pickPack,
            browsePack = if (editingOneApp) ({ pack: String -> viewModel.browsePack(pack) }) else null,
            savePreset = viewModel::savePreset.takeUnless { editingOneApp },
            loadPreset = viewModel::loadPreset,
            deletePreset = viewModel::deletePreset,
            reset = viewModel::reset,
        )
    }

    // Custom-image layers draw from what the ViewModel has already decoded — never from a file read here. The
    // live path's `customImage` is a parameter for exactly this: a disk read inside a composition that reruns on
    // every slider frame would be the wrong place for I/O, and a freshly picked image has no file yet anyway.
    val resources = LocalResources.current
    val customImage: (String) -> Drawable? = remember(state.images, resources) {
        { path -> state.images[path]?.toDrawable(resources) }
    }
    // Keyed by pack *and* chosen drawable, so two layers naming the same pack cannot show each other's icon.
    val packImage: (String, String?) -> Drawable? = remember(state.packImages, resources) {
        { pack, name -> state.packImages[LayerSource.IconPack(pack, name).key]?.toDrawable(resources) }
    }

    BackHandler(onBack = onBack)

    // **The bars' icons follow the canvas, because the canvas is what is behind them.** The studio's own chrome is
    // fixed dark glass with white content (see `studioSurface`), and it can be, because it paints its own wash to
    // read over either extreme. The system bars cannot: they are drawn *by the system*, directly over whatever the
    // user has set the backdrop to, with nothing between. So this is the one thing on the screen that must track the
    // background rather than being pinned — otherwise the clock and the back gesture hint vanish on two of the five.
    //
    // Both bars take the same verdict from the surround alone (see `darkSystemBarIcons`) — the icon's bound is a
    // square in the middle of the canvas and reaches neither bar.
    //
    // **Restored on dispose**, because the appearance is the *window's* and the window outlives this destination: the
    // launcher shell picks it from wallpaper brightness, and a studio that left the bars as it found the canvas would
    // hand back light icons over a bright wallpaper. Keyed on the background so a cycle re-applies; the captured
    // `previous` is therefore the value from *before* the studio, not from the previous background, since each pass
    // restores before the next one reads.
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view, state.background) {
        val bars = activity?.window?.let { WindowInsetsControllerCompat(it, view) }
        val previousStatus = bars?.isAppearanceLightStatusBars
        val previousNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = state.background.darkSystemBarIcons
        bars?.isAppearanceLightNavigationBars = state.background.darkSystemBarIcons
        onDispose {
            if (bars != null && previousStatus != null && previousNavigation != null) {
                bars.isAppearanceLightStatusBars = previousStatus
                bars.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    // **The studio is its own theme zone, and a fixed dark one.** Every other zone follows something — the launcher
    // follows wallpaper brightness, settings follows the system — but the studio's chrome floats over a canvas the
    // *user* switches between black and white at will, so there is nothing to follow that would stay legible. Fixed
    // dark glass with white content is the only setting that reads over both. Same call as `StudioContentColor`,
    // applied to the components rather than to the text.
    LauncherTheme(darkTheme = true) {
        Box(modifier.fillMaxSize()) {
            // `hazeSource` on the canvas rather than on this root: a floating surface must blur *the work*, and a
            // source that included the surfaces themselves would have them sampling each other.
            StudioCanvas(
                background = state.background,
                modifier = Modifier.hazeSource(hazeState),
            ) {
                state.parsed?.let { parsed ->
                    IconLayerStack(
                        icon = parsed,
                        layerSet = state.editing,
                        modifier = Modifier.fillMaxSize(),
                        customImage = customImage,
                        packImage = packImage,
                    )
                }
            }

            // **The stack, always on screen**, which is what the tool bar cost when the layer list went behind an
            // entry of its own. On the *end* edge and vertically centred, so it sits between the save row above and
            // the tool panel below without either having to know about it. See [StudioLayerRail].
            StudioLayerRail(
                state = state,
                hazeState = hazeState,
                customImage = customImage,
                packImage = packImage,
                onSelectLayer = viewModel::selectLayer,
                onMove = viewModel::moveSelected,
                onAdd = viewModel::addLayer,
                onRemove = viewModel::removeSelected,
                onToggleVisible = viewModel::toggleSelectedVisible,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .uiInsetsPadding()
                    .padding(ChromeMargin),
            )

            // Chrome floats over the canvas, inset from the system bars. The studio paints its own backdrop edge to
            // edge — the insets are content padding, never layout padding, as everywhere else in this launcher.
            //
            // Leave, at the corner the whole app leaves from. Its own pill rather than a slot in a rail: back is not
            // an editing action, and grouping it with any of them would put "discard this session" one finger-width
            // from a tool.
            StudioPillButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                hazeState = hazeState,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .uiInsetsPadding()
                    .padding(ChromeMargin),
            )

            // The session's actions, opposite the back button — history, then commit. **Corners rather than bar
            // entries, because none of them opens anything**: they act the moment they are pressed, so they must not
            // move when a panel changes. That is the line `StudioTool` is drawn along.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .uiInsetsPadding()
                    .padding(ChromeMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioHistoryButtons(
                    hazeState = hazeState,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                )

                // Lit only when there is something to write — which is also how the unsaved state is visible at all:
                // backing out discards, and a permanently-bright Save would give no hint of that. A tick rather than a
                // floppy disk: this commits and stays, it does not export a file.
                StudioPillButton(
                    icon = Icons.Default.Check,
                    contentDescription = "Save",
                    hazeState = hazeState,
                    enabled = state.dirty,
                    onClick = viewModel::save,
                )
            }

            // The bottom of the workspace: the tool bar, with anything floating above it in the same stack. One
            // `uiInsetsPadding` for the pair, so the gap between them is not inset twice.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The bottom chrome is the only thing on this screen a keyboard can cover, and the color picker's
                    // hex field is the only thing that raises one — so the whole stack rides above it rather than the
                    // panel alone, which would have left the rail underneath the keys. Zero when no keyboard is up, so
                    // it costs the other panels nothing.
                    .imePadding()
                    .uiInsetsPadding()
                    .padding(ChromeMargin)
                    .fillMaxWidth(),
                // **Start, not end, and the layer rail is why.** These two used to sit at the trailing end, which
                // was out of the way of everything that existed at the time. The rail now runs down that edge, and
                // the panel is what brings them together: opening one pushes this row up into the rail's vertical
                // span, so a trailing row would meet the tiles rather than clear them. The leading end is the only
                // side with nothing else on it — the icon bound has already shifted the other way for the same
                // reason (`IconBoundShift`).
                //
                // Only this row moves. Everything else in this column fills the width, so the alignment does not
                // reach the panel or the bar.
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackgroundCycleButton(
                        background = state.background,
                        onClick = viewModel::cycleBackground,
                    )
                    // **One slot, and the subject decides what is in it** — which is the sum type earning its keep
                    // rather than two buttons each checking whether they apply. Both answer the same question, "which
                    // app am I looking at?", and they differ because the answer means different things: a global
                    // recipe is *previewed* on an app, so any app will do and a shuffle is the fastest way through
                    // several; an individual recipe *belongs* to one, so it is chosen.
                    when (state.subject) {
                        is StudioSubject.Global -> StudioPillButton(
                            icon = Icons.Default.Casino,
                            contentDescription = "Preview on another app",
                            hazeState = hazeState,
                            onClick = viewModel::shuffleSample,
                        )

                        is StudioSubject.App -> StudioPillButton(
                            icon = Icons.Default.Apps,
                            contentDescription = "Edit another app",
                            hazeState = hazeState,
                            onClick = viewModel::chooseAnotherApp,
                        )

                        // The picker is already up, so there is nothing to change to.
                        StudioSubject.Unchosen -> Unit
                    }
                }

                // Above the bar it belongs to, and below the cycle button, which belongs to neither. Absent rather
                // than empty when nothing is chosen: the picker covers the screen then, and a panel editing a recipe
                // with no subject would be editing nothing.
                //
                // **One panel at a time, which is why the color picker is an arm of this and not a layer over it** —
                // see [StudioColorPickerPanel]. The section that opened it is among the controls it would otherwise
                // cover, and two sheets of glass stack taller than the canvas they float on.
                //
                // **It grows out from behind the rail and retreats the same way**, which is the one motion that matches
                // where it comes from: the rail is what opened it, so a panel appearing on the spot would read as
                // unrelated to the button that was pressed.
                //
                // **Fade, size and a short slide — all three.** Fade-and-size alone was tried, on the argument that the
                // growth already carries the panel up so a translation repeats it, and it was reversed at the author's
                // call: the slide is what gives the panel somewhere to come *from*. Growth alone reads as a box being
                // unmasked in place; the offset makes it move. It is deliberately a sixth of the height, small enough
                // to be a lead-in to the growth rather than a journey of its own.
                //
                // **`contentKey` is what keeps switching *tools* from being a transition at all.** The target carries
                // the tool so the content can draw it, but the key is only which *kind* of panel this is — so Source →
                // Effects updates the panel in place and its own `animateContentSize` handles the height, exactly as
                // [StudioToolPanel] says it should. Keying on the tool itself would cross-fade two panels and take
                // that away.
                //
                // **The slot's *size* animates as well, because it is what everything above it stands on.** The pair of
                // buttons is next in this bottom-anchored column, so the slot's height is their position: with the size
                // snapping, they jumped to the open height before the panel had drawn and stayed up there until after it
                // had gone. `SizeTransform` makes the panel grow from behind the rail and the buttons ride it.
                //
                // It does not collide with [StudioToolPanel]'s own `animateContentSize`, which is the hazard that KDoc
                // names, because the two are never running at once: a tool switch shares a `contentKey`, so no
                // transition starts and the container simply follows the height the panel is animating; and a panel
                // arriving is freshly composed, so it has no previous height of its own to animate from.
                val picking = colorPicker.request
                val open = tool?.takeIf { state.subject !is StudioSubject.Unchosen }
                // Read out here because `transitionSpec` is not a composable lambda — the motion scheme is, so it
                // cannot be reached from inside it.
                val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val resize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
                AnimatedContent(
                    targetState = picking to open,
                    contentKey = { (request, panel) ->
                        when {
                            request != null -> PanelSlot.COLOR
                            panel != null -> PanelSlot.TOOLS
                            else -> PanelSlot.NONE
                        }
                    },
                    transitionSpec = {
                        val enter = fadeIn(fade) + slideInVertically(slide) { it / 6 }
                        val exit = fadeOut(fade) + slideOutVertically(slide) { it / 6 }
                        enter togetherWith exit using SizeTransform { _, _ -> resize }
                    },
                    // Bottom-anchored, so a slot that is not yet its full height keeps its lower edge against the rail
                    // and opens *upward*. Top-aligned — the default — would pin the panel's head where it will end up
                    // and grow it downward over the rail, which is the opposite of coming out from behind it.
                    contentAlignment = Alignment.BottomCenter,
                    label = "studio panel",
                    // The outgoing panel is composed with the state it was opened on, which is what lets a closing
                    // picker keep drawing its request after `colorPicker.request` is already null.
                ) { (request, panel) ->
                    when {
                        request != null -> StudioColorPickerPanel(
                            modifier = Modifier.padding(vertical = 6.dp),
                            request = request,
                            hazeState = hazeState,
                            onDone = colorPicker::close,
                        )

                        panel != null ->
                            // Provided at the one consumer rather than at the screen root: the sections are the only
                            // things that ask for a color, and the host is what they ask.
                            CompositionLocalProvider(LocalStudioColorPicker provides colorPicker) {
                                StudioToolPanel(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    tool = panel,
                                    state = state,
                                    actions = actions,
                                    hazeState = hazeState,
                                )
                            }

                        else -> Unit
                    }
                }
                // Outside the transition, so it is bound to the picker being *open* rather than to whichever panel is
                // still on screen — a handler inside would linger for the length of the exit. Declared after the
                // screen's own handler, so back closes the picker before it leaves the studio, which is the same
                // layering the pack browser below relies on.
                if (picking != null) BackHandler { colorPicker.close() }

                StudioToolBar(
                    hazeState = hazeState,
                    // Centred explicitly, because the bar wraps its contents now and this column aligns to the
                    // start for the row of session buttons above. `ColumnScope.align` is the per-child override,
                    // so the two say what they mean rather than one of them settling for the other's answer.
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .align(Alignment.CenterHorizontally),
                    selected = tool,
                    // Choosing a tool closes the picker, so the rail always opens what it says it opens. Without it a
                    // press would swap the bar's highlight and leave the color panel sitting there — the one way this
                    // screen could show a selection that is not what is on screen.
                    onSelect = {
                        colorPicker.close()
                        tool = it
                    },
                )
            }

            // Reachable again now that the Source section is back. Full-screen over everything, including the bar:
            // it is a step *within* choosing a source, so the tools beneath must not be pressable while it is up.
            state.browsing?.let { browse ->
                PackDrawablePicker(
                    browse = browse,
                    loadPreview = viewModel::packPreview,
                    onPick = viewModel::pickPackDrawable,
                    modifier = Modifier
                        .fillMaxSize()
                        .studioSurface(hazeState, shape = RectangleShape)
                        .uiInsetsPadding()
                        .padding(top = 64.dp),
                )
                BackHandler { viewModel.browsePack(null) }
            }

            if (state.subject is StudioSubject.Unchosen) {
                // **Over the canvas, not instead of it**, so choosing an app does not feel like arriving at a second
                // screen — the picker lifts away and the icon it chose is already there behind it.
                AppPicker(
                    apps = state.pickable,
                    onPick = viewModel::selectApp,
                    modifier = Modifier
                        .fillMaxSize()
                        .studioSurface(hazeState, shape = RectangleShape)
                        .uiInsetsPadding()
                        .padding(top = 64.dp),
                )
            }
        }
    }
}

/**
 * What kind of thing is in the panel slot above the tool rail — the *transition's* vocabulary, not the screen's.
 *
 * It exists so a panel swap is animated per kind rather than per value: every tool is one `TOOLS`, so moving between
 * sections updates the panel in place and only opening, closing, or swapping in the color picker is a transition. See
 * the `AnimatedContent` above.
 */
private enum class PanelSlot { NONE, TOOLS, COLOR }
