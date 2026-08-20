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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.picker.AppPicker
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.icon.compose.IconPreview
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.key
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The icon studio: a full-bleed live preview with floating surfaces over it.
 *
 * **A creative workspace, not a settings form** — which is the whole reason it is a destination of its own rather
 * than another pane. An editor built out of settings-list vocabulary — stacked commit sliders, emoji category
 * chips, "Up / Down / Remove" text buttons — can do everything this one does and still be the wrong presentation
 * for it.
 *
 * **The canvas is the Haze source and everything else floats on it** — and the layer rail is both, which is why
 * there are two states rather than one. The rail samples the canvas alone; everything above the rail samples the
 * canvas *and* the rail. Every floating surface uses the shared [studioSurface] material either way, so a new panel
 * cannot arrive looking different from the rest. See that modifier for why this is Haze and not the launcher's own
 * `wallpaperBackdrop`.
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
    // **Two sources, because the rail is both a surface and something to see through.** Haze samples what is
    // *behind* a node, so one shared state would have the rail sampling itself — and the panel, which overlaps the
    // rail's lower half when it opens, sampling a rail that had nothing behind it.
    //
    // [canvasHaze] is the work alone. The rail's glass and its quick menu read this, so they blur the icon and
    // never each other.
    //
    // [screenHaze] is the work **and** the rail. Everything that floats over both reads this — the panel most of
    // all, which is what makes the rail appear blurred through it rather than vanishing behind an opaque edge.
    //
    // A node can register with both: `hazeSource` is one modifier node per call, so the canvas simply carries two.
    val canvasHaze = rememberHazeState()
    val screenHaze = rememberHazeState()
    // Which section's panel is showing, or null for none. Screen state and nothing more — the recipe does not care
    // which tool is open, and neither does undo, so it stays out of the ViewModel.
    var tool by remember { mutableStateOf<StudioTool?>(null) }
    // **The rail's menu, hoisted out of the rail**, which is what lets a tap on the canvas put it away. The rail held
    // it while the rail was the only thing that could close it; the canvas cannot reach into it, and a dismissal that
    // worked for the tool panel but not for the menu would be the sort of half-rule nobody can learn.
    //
    // A nullable [RailMenu] rather than a boolean each, so "the layer menu and the stack menu are both up" cannot be
    // said — the same reason the launcher has one menu host for its item and surface menus.
    var railMenu by remember { mutableStateOf<RailMenu?>(null) }
    // **Where the rail is**, reported by the rail as it is dragged and re-laid-out. The menu is placed against this
    // rather than being a child of the rail, because a child drawn outside its parent is visible but not touchable.
    var railBounds by remember { mutableStateOf(Rect.Zero) }

    // The full color picker is hosted here rather than where it is asked for, and takes the tool panel's slot when it
    // is up. See [StudioColorPickerHost] for why a control cannot render inside the section that opens it.
    val colorPicker = remember { StudioColorPickerHost() }

    // **A section that stops applying to the selection closes, rather than staying open with nothing in it.**
    // Selecting the composite tile shortens the bar to the three entries that mean something for it (see
    // [StudioTool.appliesTo]) — but the *panel* went on showing whichever per-layer section had been open, and
    // Source, Transform and Shape all resolve through `state.selectedLayer`, which is null there. So the panel drew
    // its header and nothing else, and the entry that would put it away was no longer on the bar to press: a sheet
    // of glass with one word on it and no way out.
    //
    // **Driven off `state.target` rather than off the rail's tap**, because it is an invariant about what may be open
    // and not a consequence of one gesture — every route that can move the selection is covered by construction,
    // which is the same reason the drop zone registration and the item gestures are wired where they cannot be
    // forgotten. The color picker goes with it for the reason it closes on a bar press: it is raised *by* a control
    // inside the section now leaving, so left up it would outlive the thing it edits.
    LaunchedEffect(state.target) {
        if (tool?.appliesTo(state.target) == false) {
            tool = null
            colorPicker.close()
        }
    }

    // Holds each kind of panel's saveable state while the other kind is showing — see the `SaveableStateProvider`
    // below. At the screen rather than inside the slot, so it outlives every swap the slot makes.
    val panelState = rememberSaveableStateHolder()

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
            selectTarget = viewModel::selectTarget,
            updateEffects = viewModel::updateEffects,
            pickShape = viewModel::pickShape,
            setOrientation = viewModel::setOrientation,
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
            renamePreset = viewModel::renamePreset.takeUnless { editingOneApp },
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
        // **The last page of the session replaces the editor rather than floating over it.** It is the same
        // destination and the same ViewModel — see [StudioStep] — so back is a step back and nothing is committed or
        // discarded on the way in or out. It paints no background of its own, which is how the wallpaper shows: the
        // studio's canvas is simply not drawn on this step.
        if (state.step == StudioStep.FINALIZE) {
            StudioFinalizeScreen(
                state = state,
                hazeState = screenHaze,
                onPlateEnabled = viewModel::setPlateEnabled,
                onPlateShape = viewModel::setPlateShape,
                onZoom = viewModel::setZoom,
                // Null in the individual session, where the library is read-only — `PresetsControls`' rule, and the
                // same nullable-means-absent shape.
                onSavePreset = viewModel::savePreset.takeUnless { editingOneApp },
                onApply = viewModel::save,
                onBack = viewModel::toEdit,
                modifier = modifier,
            )
            return@LauncherTheme
        }

        // **The root measures**, which the viewport made necessary: both the canvas's pan and the rail's drag are
        // clamped against the canvas, and both store their positions as fractions of it, so somebody has to know how
        // big it is. The root is the honest place — it is the canvas, every floating surface being drawn over it.
        BoxWithConstraints(modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val canvasWidth = with(density) { maxWidth.toPx() }
            val canvasHeight = with(density) { maxHeight.toPx() }

            // **How far down the workspace starts** — the system inset plus the row of pill buttons and the margins
            // around it. Computed here rather than inside the canvas and the rail separately, because it is the one
            // number that makes them line up: the icon rests immediately below the chrome and the rail rests beside
            // it, and two derivations of "below the chrome" would be one edit away from disagreeing.
            val insets = uiInsets.asPaddingValues()
            val topChrome = insets.calculateTopPadding() + ChromeMargin + StudioTopChromeHeight + WorkspaceGap

            // The area a floating panel may occupy: the canvas less `uiInsets`. Whole pixels, since that is what the
            // placement arithmetic works in.
            val layoutDirection = LocalLayoutDirection.current
            val usableFrame = with(density) {
                IntRect(
                    left = insets.calculateLeftPadding(layoutDirection).roundToPx(),
                    top = insets.calculateTopPadding().roundToPx(),
                    right = canvasWidth.toInt() - insets.calculateRightPadding(layoutDirection).roundToPx(),
                    bottom = canvasHeight.toInt() - insets.calculateBottomPadding().roundToPx(),
                )
            }

            // Everything a tap on the canvas puts away. One lambda so the three cannot come apart — a dismissal that
            // closed the panel but left the quick menu up would read as the tap having half worked.
            val dismissChrome = {
                tool = null
                railMenu = null
                colorPicker.close()
            }

            // `hazeSource` on the canvas rather than on this root: a floating surface must blur *the work*, and a
            // source that included the surfaces themselves would have them sampling each other.
            StudioCanvas(
                background = state.background,
                workspace = state.workspace,
                topInset = topChrome,
                onWorkspaceChange = viewModel::setWorkspace,
                onWorkspaceCommit = viewModel::commitWorkspace,
                onTap = dismissChrome,
                // The source for both, and the only node in either that is the *work* rather than chrome. Explicit
                // z-indices so "behind" is stated rather than inferred from draw order.
                modifier = Modifier
                    .hazeSource(canvasHaze, zIndex = CanvasDepth)
                    .hazeSource(screenHaze, zIndex = CanvasDepth),
            ) {
                state.parsed?.let { parsed ->
                    // **The canvas, and every other preview here, goes through `IconPreview` rather than the live
                    // stack directly.** It picks the bake for a recipe the live path cannot draw, which is what the
                    // six remaining effects need — and picking at one entry point is what stops a call site from
                    // forgetting to ask and silently showing a lie.
                    IconPreview(
                        icon = parsed,
                        layerSet = state.editing,
                        modifier = Modifier.fillMaxSize(),
                        customImage = customImage,
                        packImage = packImage,
                    )
                }
            }

            // **The stack, always on screen**, which is what the tool bar cost when the layer list went behind an
            // entry of its own. It **rests** at the top of the end edge, level with the icon and directly below the
            // save row, and is draggable from there by the handle at its head. See [StudioLayerRail].
            //
            // Top-aligned rather than vertically centered, which is the other half of the icon moving up: the two are
            // the workspace, and a rail floating in the middle of the screen beside an icon at the top read as two
            // unrelated things rather than as a picture and its layers.
            StudioLayerRail(
                state = state,
                // The canvas alone: the rail's own glass cannot sample the rail.
                hazeState = canvasHaze,
                workspace = state.workspace,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                customImage = customImage,
                packImage = packImage,
                onSelect = viewModel::selectTarget,
                onAdd = viewModel::addLayer,
                onMenuChange = { railMenu = it },
                onWorkspaceChange = viewModel::setWorkspace,
                onWorkspaceCommit = viewModel::commitWorkspace,
                onBoundsChange = { railBounds = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // ...but it *is* a source for everything above it, which is what puts blurred tiles behind the
                    // panel. Above the canvas, so a consumer of [screenHaze] gets the two in the order they are
                    // drawn.
                    .hazeSource(screenHaze, zIndex = RailDepth)
                    // The same expression the canvas is given, so the rail's head and the icon's top edge rest on one
                    // line. Applied as padding rather than as an offset so the rail's *cap* is measured against what
                    // is left below it.
                    .padding(top = topChrome, end = ChromeMargin)
                    .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .padding(bottom = ChromeMargin),
            )

            // **The rail's menus, drawn beside the rail rather than inside it.** Which side they take is computed from
            // where the rail currently is and which way it runs — see [railMenuPlacement]. A sibling because a child
            // drawn outside its parent's bounds is visible but **not touchable**: Compose does not hit-test past a
            // parent, so a menu placed to the left of a rail would have been a panel whose rows did nothing.
            //
            // After the rail in this stack, so it draws over it, and given the whole canvas to place itself in.
            railMenu?.let { menu ->
                StudioRailMenu(
                    menu = menu,
                    state = state,
                    workspace = state.workspace,
                    anchor = railBounds,
                    // The usable area, not the raw canvas: a menu clamped against the window would be allowed to sit
                    // under a notch or the gesture bar. Same correction `menuPlacementFor` documents for the launcher.
                    frame = usableFrame,
                    hazeState = screenHaze,
                    onMove = viewModel::moveSelected,
                    onToggleVisible = viewModel::toggleSelectedVisible,
                    onRemove = viewModel::removeSelected,
                    onToggleAxis = viewModel::toggleRailAxis,
                    onToggleCollapsed = viewModel::toggleRailCollapsed,
                    onDismiss = { railMenu = null },
                    modifier = Modifier.fillMaxSize(),
                )
                // Declared after the screen's own handler, so back closes the menu before it leaves the studio — the
                // same layering the color picker and the pack browser rely on. Set directly rather than through the
                // menu's animated dismissal, matching what a tap on the canvas does.
                BackHandler { railMenu = null }
            }

            // Chrome floats over the canvas, inset from the system bars. The studio paints its own backdrop edge to
            // edge — the insets are content padding, never layout padding, as everywhere else in this launcher.
            //
            // Leave, at the corner the whole app leaves from. Its own pill rather than a slot in a rail: back is not
            // an editing action, and grouping it with any of them would put "discard this session" one finger-width
            // from a tool.
            StudioPillButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                hazeState = screenHaze,
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
                    hazeState = screenHaze,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                )

                // **Forward to the last page, where the commit lives now.** This was the tick, and the tick was the
                // whole of Save; both moved to the finalize step, which is the only place the three whole-icon
                // settings — the plate, its shape, the zoom — can be judged, since they are read against the
                // *wallpaper* and this canvas deliberately is not it.
                //
                // **Always enabled, unlike the tick.** A session with nothing changed still has somewhere to go: the
                // step is also where a look is saved as a preset and where the plate is switched on. The
                // "is there anything to write?" signal the tick carried by being lit now sits on that step's own
                // Apply button, which is the thing it was ever really about.
                StudioPillButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next step",
                    hazeState = screenHaze,
                    onClick = viewModel::toFinalize,
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
                // **Start, not end, and the layer rail is why.** The trailing end is the obvious place, and
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
                    // **The view's two controls on one pill, the subject's on another** — see [StudioViewButtons].
                    // The grouping is the whole of what says these are different questions: the first pill is *how
                    // the work is shown*, the second is *which app it is shown on*.
                    StudioViewButtons(
                        background = state.background,
                        canResetView = !state.workspace.previewAtRest,
                        hazeState = screenHaze,
                        onCycleBackground = viewModel::cycleBackground,
                        onResetView = viewModel::resetPreviewView,
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
                            hazeState = screenHaze,
                            onClick = viewModel::shuffleSample,
                        )

                        is StudioSubject.App -> StudioPillButton(
                            icon = Icons.Default.Apps,
                            contentDescription = "Edit another app",
                            hazeState = screenHaze,
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
                    // **Each kind of panel keeps its own state while the other is showing.** `AnimatedContent`
                    // *disposes* the content it swaps away, so the tool panel's `rememberSaveable` state — which
                    // section of Effects is open, which page of its grid — went with it the moment a color field
                    // raised the picker, and pressing Done came back to the top of the grid rather than to the
                    // control that had asked for a color.
                    //
                    // A `SaveableStateHolder` keyed on the *kind* is what `SurfacePager` already does for its
                    // slots, and for the same reason: the state is saved on dispose and restored when the same key
                    // returns. Keyed on [PanelSlot] rather than on the tool, so it matches the `contentKey` above
                    // and the two cannot disagree about what counts as the same panel.
                    panelState.SaveableStateProvider(
                        key = when {
                            request != null -> PanelSlot.COLOR
                            panel != null -> PanelSlot.TOOLS
                            else -> PanelSlot.NONE
                        },
                    ) {
                        when {
                            request != null -> StudioColorPickerPanel(
                                modifier = Modifier.padding(vertical = 6.dp),
                                request = request,
                                hazeState = screenHaze,
                                onDone = colorPicker::close,
                            )

                            panel != null ->
                                // Provided at the one consumer rather than at the screen root: the sections are the
                                // only things that ask for a color, and the host is what they ask.
                                CompositionLocalProvider(LocalStudioColorPicker provides colorPicker) {
                                    StudioToolPanel(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        tool = panel,
                                        state = state,
                                        actions = actions,
                                        hazeState = screenHaze,
                                        customImage = customImage,
                                        packImage = packImage,
                                    )
                                }

                            else -> Unit
                        }
                    }
                }
                // Outside the transition, so it is bound to the picker being *open* rather than to whichever panel is
                // still on screen — a handler inside would linger for the length of the exit. Declared after the
                // screen's own handler, so back closes the picker before it leaves the studio, which is the same
                // layering the pack browser below relies on.
                if (picking != null) BackHandler { colorPicker.close() }

                StudioToolBar(
                    hazeState = screenHaze,
                    // Derived from the selection, so the composite offers only what applies to it — see
                    // [StudioTool.appliesTo].
                    tools = StudioTool.entries.filter { it.appliesTo(state.target) },
                    // Centered explicitly, because the bar wraps its contents now and this column aligns to the
                    // start for the row of session buttons above. `ColumnScope.align` is the per-child override,
                    // so the two say what they mean rather than one of them settling for the other's answer.
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .align(Alignment.CenterHorizontally),
                    selected = tool,
                    // Choosing a tool closes the picker **and the rail's menu**, so the bar always opens what it says
                    // it opens. Without the first a press would swap the bar's highlight and leave the color panel
                    // sitting there — the one way this screen could show a selection that is not what is on screen —
                    // and without the second a stack menu would hang over the panel that had just replaced what it
                    // was pointing at.
                    onSelect = {
                        colorPicker.close()
                        railMenu = null
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
                        .studioSurface(screenHaze, shape = RectangleShape)
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
                        .studioSurface(screenHaze, shape = RectangleShape)
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

/** Source depths for `screenHaze`: the work, then the rail that floats on it. */
private const val CanvasDepth = 0f
private const val RailDepth = 1f
