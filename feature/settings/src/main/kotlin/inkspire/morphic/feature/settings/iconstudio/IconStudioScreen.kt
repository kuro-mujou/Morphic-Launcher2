package inkspire.morphic.feature.settings.iconstudio

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
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
                    .uiInsetsPadding()
                    .padding(ChromeMargin),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ChromeMargin),
            ) {
                // Provisional placement: above the bar and at the trailing end, which is out of the way of both the
                // icon it edits and the bar's own contents. Where it finally sits is a decision for the pass that
                // knows what else is up here.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                    BackgroundCycleButton(
                        background = state.background,
                        onClick = viewModel::cycleBackground,
                    )
                }

                // Above the bar it belongs to, and below the cycle button, which belongs to neither. Absent rather
                // than empty when nothing is chosen: the picker covers the screen then, and a panel editing a recipe
                // with no subject would be editing nothing.
                tool?.takeIf { state.subject !is StudioSubject.Unchosen }?.let { open ->
                    StudioToolPanel(
                        tool = open,
                        state = state,
                        actions = actions,
                        hazeState = hazeState,
                    )
                }

                StudioToolBar(
                    hazeState = hazeState,
                    selected = tool,
                    onSelect = { tool = it },
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
