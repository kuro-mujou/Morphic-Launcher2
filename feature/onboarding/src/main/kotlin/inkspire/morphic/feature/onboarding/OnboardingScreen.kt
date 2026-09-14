package inkspire.morphic.feature.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.PunchThroughLayer
import inkspire.morphic.core.designsystem.backdrop.punchThroughHole
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.surface.SurfacePagerState
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/** How long the preview rests on HOME, and then on the app list, between crossings. */
private const val RestOnHomeMs = 1_400L
private const val RestOnSideMs = 1_800L

/**
 * The first-run screen: the launcher as it will be, and one button to keep it.
 *
 * **The picture is the real launcher**, scaled down — [preview] is the shell itself, drawn at the window's full size
 * and shrunk, so the screen shows the user's own apps at their real arrangement and cannot disagree with the home screen
 * it leads to. It plays the crossing to the app list by itself, which teaches the one gesture a fresh install needs.
 *
 * **Every way forward keeps a look.** The look is applied as the screen is shown, and there is no skip beside the
 * button: skipping would reach a home with no route to the app list, the one outcome the gate exists to rule out.
 *
 * Themed from the system, as settings is, because it paints its own background around the picture.
 *
 * @param onShown applies the look being previewed; called once, as the screen is first composed.
 * @param onUse finishes setup with that look.
 */
@Composable
internal fun OnboardingScreen(
    preview: @Composable (SurfacePagerState) -> Unit,
    onShown: () -> Unit,
    onUse: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }
    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        PunchThroughLayer(background = colors.background) {
            Column(
                modifier = Modifier
                    .uiInsetsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LauncherMiniature(
                    preview = preview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Text(
                    text = "Classic layout",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.content,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Home screen pages with a dock. Swipe up from home for all your apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                )
                MorphicButton(onClick = onUse) {
                    Text("Use this layout")
                }
            }
        }
    }
}

/**
 * The launcher at the window's full size, scaled to fit [modifier]'s box, over the real wallpaper, taking no touch.
 *
 * **Laid out full-size and then scaled, never laid out small.** Every surface measures the *window* to size its grids,
 * so a copy given a small box would still arrange itself for the whole screen and overflow it; given the whole window
 * and shrunk as a picture, it is exactly what the screen will show.
 *
 * **A hole in the screen, so the wallpaper is really behind it.** HOME paints no background, and a gray panel under its
 * icons would not be the home screen. The wallpaper behind the hole is the window's own at full size rather than scaled
 * with the picture — true to the colors the icons will sit on, if not to the crop.
 */
@Composable
private fun LauncherMiniature(
    preview: @Composable (SurfacePagerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberSurfacePagerState()
    LaunchedEffect(pagerState) {
        while (true) {
            delay(RestOnHomeMs.milliseconds)
            pagerState.open(HomeEdge.BOTTOM)
            delay(RestOnSideMs.milliseconds)
            pagerState.close()
        }
    }

    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
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
                preview(pagerState)
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
