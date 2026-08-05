package inkspire.morphic.feature.settings.wallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.data.wallpaper.WallpaperTarget
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp
private val PreviewCorner = 16.dp

/**
 * **Wallpaper**: the image the launcher owns, and where to put it.
 *
 * The vertical that makes S5a visible — a preview of the stored image, "Choose image", and Apply / Re-apply onto the
 * home screen, the lock screen, or both. Ported from the *single-image* half of L1's `WallpaperTab`.
 *
 * **The preview is screen-shaped, and that is the honest shape rather than decoration.** The stored file is already
 * cropped and scaled to this screen (`WallpaperRepository.setImage`), so a preview at any other aspect ratio would show
 * a crop the device will never display. It is the same argument `GridEditor`'s preview makes for taking the window's
 * ratio instead of a square.
 *
 * **One button and a menu, where L1 drew a split button.** Its `SplitButtonLayout` looks like two controls but both
 * halves ran `expanded = true` — the leading half opened the same menu the trailing chevron did — so the split was
 * decoration over a single action. One `MorphicButton` opening the same three-item menu is what it actually did, and it
 * keeps the section on the design system's own button rather than on an M3 control that would need its own restyle.
 *
 * **Choosing an image opens [WallpaperCropScreen]**, which is where the writing happens — so this section reads the
 * store and issues one command (apply), and the picked-but-unsaved image never touches it. L1's picker pushed its crop
 * screen the same way.
 *
 * **Two sources, and they are not peers.** "Choose image" picks one and frames it; "Capture screen" takes a picture
 * *of* the wallpaper for the effects to sample (see [WallpaperCaptureScreen]). A captured image is previewed like any
 * other but cannot be applied, so the Apply button is replaced by the reason rather than left dead — the rule itself
 * lives in the repository, where it cannot be worked around.
 *
 * **The third source is the rotating pair**, in its own group below: two slots, one per orientation, drawn by the
 * launcher's own live wallpaper. It is applied by the *system's* chooser rather than by this screen, because a live
 * wallpaper can only be set with the user confirming — so its button opens that chooser and the section reports back
 * whether ours ended up active. L1 put the two modes in a pager of two pages; they are two groups here, because a pager
 * hides one of them behind a swipe and there are only two.
 *
 * **What is deliberately absent**: L1's three browse rows — "My wallpapers", "Backdrops (By Unsplash)" and a list of
 * *installed* live wallpapers. The first two are empty-state hints for a source that does not exist; the third is a
 * browser for other apps' wallpapers, which is the system's own chooser rendered a second time. An empty shelf is not a
 * feature, and neither is a duplicate of a screen the platform already provides.
 */
@Composable
internal fun WallpaperDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WallpaperViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    // The **whole** window, insets included: a wallpaper sits under the system bars, so the preview is the shape of
    // what will actually be covered. Every other section measures the *usable* area instead, and the difference is
    // exactly that — those size things the user reaches, this sizes something they only look at.
    val windowSize = LocalWindowInfo.current.containerSize
    val screenRatio = if (windowSize.height > 0) {
        windowSize.width.toFloat() / windowSize.height.toFloat()
    } else {
        DEFAULT_SCREEN_RATIO
    }

    // A pick opens the **crop screen** rather than writing: the user frames the image there, and that screen saves.
    // L1's picker did the same, and it is why nothing here passes a size — the viewport the user frames against is
    // what gets stored.
    val navigator = LocalNavigator.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.goTo(WallpaperCropRoute(uri.toString()))
    }
    // Photo Picker rather than a document-open intent: it needs no storage permission and it is what L1 moved to.
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }

    // A pick for one half of the rotating pair goes to the same crop screen, told which slot it is filling — which is
    // what decides the shape it frames against and the size it stores at.
    var rotatingTarget by remember { mutableStateOf(CropTarget.ROTATING_PORTRAIT) }
    val rotatingPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.goTo(WallpaperCropRoute(uri.toString(), rotatingTarget))
    }

    // Whether *our* live wallpaper is the active one is a system read, and the only way it changes is the user
    // confirming in the system's chooser — which happens while this screen is stopped. So it is re-asked on resume,
    // which is where L1 ran `reconcileLiveWallpaper`; the difference is that this refreshes a read rather than
    // repairing a stored copy.
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRotatingActive()
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Wallpaper", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "The launcher keeps its own copy, cropped to this screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        Box(
            modifier = Modifier
                .padding(top = RowGap * 2)
                .fillMaxWidth(PREVIEW_WIDTH_FRACTION)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(PreviewCorner))
                .background(colors.surface)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            val preview = state.preview
            if (preview != null) {
                Image(
                    bitmap = remember(preview) { preview.asImageBitmap() },
                    contentDescription = "Current wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Two silences to tell apart: nothing chosen, and something chosen whose file we could not read.
                Text(
                    text = if (state.image == null) "No wallpaper chosen" else "Image unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.contentMuted,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = RowGap * 2),
            horizontalArrangement = Arrangement.spacedBy(RowGap * 1.5f),
        ) {
            MorphicButton(
                onClick = { picker.launch(imageRequest) },
                style = MorphicButtonStyle.Tonal,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Choose image")
            }
            MorphicButton(
                onClick = { navigator.goTo(WallpaperCaptureRoute) },
                style = MorphicButtonStyle.Tonal,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Capture screen")
            }
        }

        // Apply is the *system* action and sits apart from the two that only change what the launcher holds. A capture
        // has nothing to apply, so it says so where the button would be — a disabled control invites a second tap and
        // explains nothing.
        if (state.image != null && !state.applicable) {
            Text(
                text = "A capture is a picture of the wallpaper, so it is not applied — it is there for the effects " +
                    "to sample.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                modifier = Modifier.padding(top = RowGap * 2),
            )
        } else {
            ApplyButton(
                // "Re-apply" once this launcher is the one that set it — L1's wording, off the same stored id.
                label = if (state.applied) "Re-apply" else "Apply",
                // Nothing to apply until something is chosen, and nothing to press while a write is in flight.
                enabled = state.applicable && !state.busy,
                onSelect = viewModel::apply,
                modifier = Modifier.fillMaxWidth().padding(top = RowGap * 2),
            )
        }

        SettingsSectionHeader("Rotating wallpaper")
        Text(
            text = "A picture for each orientation, drawn by the launcher's own live wallpaper.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = RowGap * 2),
            horizontalArrangement = Arrangement.spacedBy(RowGap * 1.5f),
        ) {
            RotatingSlot(
                label = "Portrait",
                preview = state.rotatingPortrait,
                // The screen's own ratio for the portrait slot and its inverse for the landscape one, so the two tiles
                // are the shapes they stand for rather than two identical squares.
                ratio = minOf(screenRatio, 1f / screenRatio),
                enabled = !state.busy,
                onClick = {
                    rotatingTarget = CropTarget.ROTATING_PORTRAIT
                    rotatingPicker.launch(imageRequest)
                },
                modifier = Modifier.weight(1f),
            )
            RotatingSlot(
                label = "Landscape",
                preview = state.rotatingLandscape,
                ratio = maxOf(screenRatio, 1f / screenRatio),
                enabled = !state.busy,
                onClick = {
                    rotatingTarget = CropTarget.ROTATING_LANDSCAPE
                    rotatingPicker.launch(imageRequest)
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Applying a live wallpaper cannot be done silently — the platform requires the user to confirm in its own
        // preview — so this opens that chooser rather than pretending to be the action. L1's `applyLiveWallpaper` had
        // the same two-step fallback, and for the same reason: the direct intent is not supported everywhere.
        MorphicButton(
            onClick = { openLiveWallpaperChooser(context, viewModel.rotatingServiceComponent()) },
            enabled = state.hasRotating && !state.busy,
            modifier = Modifier.fillMaxWidth().padding(top = RowGap * 2),
        ) {
            Text(if (state.rotatingActive) "Re-open in system chooser" else "Apply rotating wallpaper")
        }

        Text(
            text = when {
                !state.hasRotating -> "Add at least one orientation to apply it."
                state.rotatingActive -> "Active. Changing either picture updates it without re-applying."
                else -> "Not active yet — the system asks you to confirm a live wallpaper."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = RowGap),
        )

        if (state.busy) {
            Text(
                text = "Working…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                modifier = Modifier.padding(top = RowGap),
            )
        }
    }
}

/**
 * One orientation of the rotating pair: its picture if it has one, its name if it does not, and a tap to replace it.
 *
 * Shaped like the orientation it stands for, which is the whole of what tells the two apart at a glance — L1 labelled
 * two equal squares instead. Tapping a filled slot re-picks rather than offering a menu: there are two things one could
 * do to a slot, and "clear" is not worth a menu when choosing another image is the common one and clearing it leaves the
 * pair half-configured anyway.
 */
@Composable
private fun RotatingSlot(
    label: String,
    preview: Bitmap?,
    ratio: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(PreviewCorner))
                .background(colors.surface)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = remember(preview) { preview.asImageBitmap() },
                    contentDescription = "$label wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("Add", style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = RowGap / 2),
        )
    }
}

/**
 * Opens the system's live-wallpaper preview for [component], falling back to its generic chooser.
 *
 * **A live wallpaper cannot be set silently** — the platform hands the user a preview with its own confirm button — so
 * this is the whole of "apply" for the rotating pair, and the section learns the outcome by asking on resume rather than
 * from a result. The fallback is L1's: the direct intent is optional, and a device without it still has the chooser.
 */
private fun openLiveWallpaperChooser(context: Context, component: ComponentName): Boolean {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    if (runCatching { context.startActivity(direct) }.isSuccess) return true
    val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/**
 * Apply, and *where* — the button and the three-item menu it opens.
 *
 * The menu is the whole control rather than a secondary affordance: applying always asks where, so there is no plain
 * "apply" to run without it. That is why the button opens the menu instead of acting, which is also what both halves
 * of L1's split button did.
 */
@Composable
private fun ApplyButton(
    label: String,
    enabled: Boolean,
    onSelect: (WallpaperTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        MorphicButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val select = { target: WallpaperTarget ->
                expanded = false
                onSelect(target)
            }
            DropdownMenuItem(text = { Text("Home screen") }, onClick = { select(WallpaperTarget.HOME) })
            DropdownMenuItem(text = { Text("Lock screen") }, onClick = { select(WallpaperTarget.LOCK) })
            DropdownMenuItem(text = { Text("Both") }, onClick = { select(WallpaperTarget.BOTH) })
        }
    }
}

/** How much of the pane the preview spans. A picture of the screen, not the screen — so it leaves margin. */
private const val PREVIEW_WIDTH_FRACTION = 0.55f

/** Stands in for a window that has not reported a size yet; only ever used for one frame. */
private const val DEFAULT_SCREEN_RATIO = 0.5f
