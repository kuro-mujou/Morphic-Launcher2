package inkspire.morphic.feature.settings.iconstudio

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.graphics.RectangleShape
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.picker.AppPicker
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.icon.compose.IconLayerStack
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
 * **Everything except the canvas is chrome floating over it**, so the work is never boxed into a pane. What is here
 * is the whole editor for the current model: the layer stack, and per-layer transform, shape and source. Its
 * presentation is deliberately unfinished — the actions are labelled pills standing where an icon rail with
 * tooltips will go, and the panel is fixed to the bottom rather than adapting to landscape. Both are presentation
 * passes over working controls, which is the right way round: L1's own conclusion was that everything its studio
 * *did* was right and only how it looked was wrong.
 *
 * Not here yet: a **shadow** effect (deferred — it is the one effect that could not be matched across both render
 * paths below API 31), and a real colour picker behind the solid-fill, tint and gradient swatch rows.
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

    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickImage(uri)
    }

    // Custom-image layers draw from what the ViewModel has already decoded — never from a file read here. The
    // live path's `customImage` is a parameter for exactly this: a disk read inside a composition that reruns on
    // every slider frame would be the wrong place for I/O, and a freshly picked image has no file yet anyway.
    val resources = LocalResources.current
    val customImage: (String) -> Drawable? = remember(state.images, resources) {
        { path -> state.images[path]?.toDrawable(resources) }
    }

    BackHandler(onBack = onBack)

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
                    )
                }
            }

            // Chrome floats over the canvas, inset from the system bars. The studio paints its own backdrop edge to
            // edge — the insets are content padding, never layout padding, as everywhere else in this launcher.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .uiInsetsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .studioSurface(hazeState, shape = CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = StudioContentColor,
                    )
                }
                state.label?.let {
                    Text(it, color = StudioContentColor, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // Undo / redo / background / reset / save. Stands in for the extras rail: named rather than iconified,
            // since the rail that will carry glyphs and tooltips is not built and an unlabelled button is worse than a
            // wordy one.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .uiInsetsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudioAction("undo", hazeState, state.canUndo, viewModel::undo)
                StudioAction("redo", hazeState, state.canRedo, viewModel::redo)
                StudioAction(state.background.name.lowercase().replace('_', ' '), hazeState, true) {
                    viewModel.cycleBackground()
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // "Reset" is the same verb in both modes pointed at different things — stop being customised. An
                    // app drops its override and inherits again; the global default returns to plain app icons.
                    StudioAction("reset", hazeState, enabled = true, onClick = viewModel::reset)
                    // Enabled only when there is something to write, which is also what makes the unsaved state
                    // visible — backing out discards, and a permanently-lit Save would give no hint of that.
                    StudioAction("save", hazeState, enabled = state.dirty, onClick = viewModel::save)
                }

                if (state.subject !is StudioSubject.Unchosen) {
                    StudioPanel(
                        state = state,
                        hazeState = hazeState,
                        onSelectLayer = viewModel::selectLayer,
                        onUpdate = viewModel::updateSelected,
                        onCommit = viewModel::commitEdit,
                        onToggleVisible = viewModel::toggleSelectedVisible,
                        onMove = viewModel::moveSelected,
                        onAdd = viewModel::addLayer,
                        onRemove = viewModel::removeSelected,
                        onPickImage = { imagePicker.launch(imageRequest) },
                        modifier = Modifier.uiInsetsPadding(),
                    )
                }
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

/** A labelled pill on the shared studio material — the stand-in for the extras rail's icon buttons. */
@Composable
private fun StudioAction(
    label: String,
    hazeState: dev.chrisbanes.haze.HazeState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = StudioContentColor.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier
            .studioSurface(hazeState, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
