package inkspire.morphic.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import inkspire.morphic.core.designsystem.backdrop.punchThroughHole
import inkspire.morphic.core.designsystem.surface.SurfacePagerState
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/** How long the preview rests on HOME, and then on the apps, between crossings. */
private const val RestOnHomeMs = 1_400L
private const val RestOnSideMs = 1_800L

/**
 * The launcher at the window's full size, scaled to fit [modifier]'s box, over the real wallpaper, taking no touch, and
 * crossing to [edge] and back by itself.
 *
 * **Laid out full-size and then scaled, never laid out small.** Every surface measures the *window* to size its grids,
 * so a copy given a small box would still arrange itself for the whole screen and overflow it; given the whole window
 * and shrunk as a picture, it is exactly what the screen will show.
 *
 * **A hole in the screen, so the wallpaper is really behind it.** HOME paints no background, and a gray panel under its
 * icons would not be the home screen. The wallpaper behind the hole is the window's own at full size rather than scaled
 * with the picture — true to the colors the icons will sit on, if not to the crop.
 *
 * **Its ViewModels live in a store of their own, cleared when the picture leaves.** The shell and its surfaces resolve
 * their holders through `koinViewModel`, and outside navigation that is the Activity's store — where they would outlive
 * the first-run screen for as long as the launcher runs, beside the real launcher's own.
 *
 * **One described image to accessibility services.** Inside is a whole launcher's worth of icons that do nothing, and
 * a screen reader walking them would be reading out a home screen the user cannot touch.
 *
 * @param edge the edge the applied look opens its apps from; the crossing restarts, from HOME, whenever it changes.
 * @param lookName the look being previewed, for its description.
 */
@Composable
internal fun LauncherMiniature(
    preview: @Composable (SurfacePagerState) -> Unit,
    edge: HomeEdge?,
    lookName: String?,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberSurfacePagerState()
    LaunchedEffect(pagerState, edge) {
        pagerState.close()
        if (edge == null) return@LaunchedEffect
        while (true) {
            delay(RestOnHomeMs.milliseconds)
            pagerState.open(edge)
            delay(RestOnSideMs.milliseconds)
            pagerState.close()
        }
    }

    val previewStore = remember { PreviewViewModelStore() }
    DisposableEffect(previewStore) { onDispose { previewStore.viewModelStore.clear() } }

    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = lookName?.let { "Preview of the $it look" } ?: "Preview of the launcher"
        },
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth / windowWidth, maxHeight / windowHeight)
        Box(
            modifier = Modifier
                .size(windowWidth * scale, windowHeight * scale)
                .clip(RoundedCornerShape(24.dp))
                .punchThroughHole(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(windowWidth, windowHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides previewStore) {
                    preview(pagerState)
                }
            }
            // The picture takes no touch: a press inside it would reach a real launcher's icons and gestures.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

/** A store that belongs to one composition, so the preview's ViewModels can be cleared with it. */
private class PreviewViewModelStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
