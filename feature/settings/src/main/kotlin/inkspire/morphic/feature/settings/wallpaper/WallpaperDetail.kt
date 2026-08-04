package inkspire.morphic.feature.settings.wallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
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
 * **What is deliberately absent**, each waiting on its own slice rather than missing: the **crop screen** (S5c — until
 * it lands, picking centre-crops, which is what the repository says it does), **capture** (S5e) and the **rotating
 * live wallpaper** (S5f). L1's tab also carried three browse rows — "My wallpapers", "Backdrops (By Unsplash)" and
 * installed live wallpapers — of which the first two are empty-state hints for a source that does not exist. An empty
 * shelf is not a feature; they arrive when something fills them.
 */
@Composable
internal fun WallpaperDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WallpaperViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    // The **whole** window, insets included: a wallpaper sits under the system bars, so the image has to cover them.
    // Every other section measures the *usable* area instead, and the difference is exactly that — those size things
    // the user reaches, this sizes something they only look at.
    val windowSize = LocalWindowInfo.current.containerSize
    val screenRatio = if (windowSize.height > 0) {
        windowSize.width.toFloat() / windowSize.height.toFloat()
    } else {
        DEFAULT_SCREEN_RATIO
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.chooseImage(uri, windowSize.width, windowSize.height)
    }
    // Photo Picker rather than a document-open intent: it needs no storage permission and it is what L1 moved to.
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }

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
            ApplyButton(
                // "Re-apply" once this launcher is the one that set it — L1's wording, off the same stored id.
                label = if (state.applied) "Re-apply" else "Apply",
                // Nothing to apply until something is chosen, and nothing to press while a write is in flight.
                enabled = state.image != null && !state.busy,
                onSelect = viewModel::apply,
                modifier = Modifier.weight(1f),
            )
        }

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
