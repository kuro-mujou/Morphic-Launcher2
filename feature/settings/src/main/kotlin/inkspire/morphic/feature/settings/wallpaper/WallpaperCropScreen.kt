package inkspire.morphic.feature.settings.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.data.wallpaper.NormalizedCropRect
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * **Framing a picked image**: pan and pinch it under a screen-shaped viewport, then save the region that is showing.
 *
 * The port of L1's `WallpaperCropScreen`, and it keeps that screen's arithmetic exactly — the cover scale, the
 * centroid-anchored zoom, the clamp that stops the image being dragged off its own edges, and the four fractions read
 * back out of the transform at save time. Those are not incidental: together they are what makes the image impossible
 * to frame *badly* (there is never a gap, and the crop is never outside the picture), which is worth more than any
 * chrome a crop screen could grow.
 *
 * **A destination rather than a pane**, unlike every settings *section*. It is full-screen, it is transient, and back
 * from it means "I did not want that image" rather than "close this detail" — all three are what a back-stack entry
 * is for. L1 kept it a separate screen too, and the settings section is what pushes it.
 *
 * **The viewport is the shape of the output, and [target] is its size.** What the user frames against has the target
 * slot's aspect, and the rectangle they end up with is read back in fractions of the source — so the frame and the
 * result share one coordinate space whatever size either is. It is also why this screen draws under the system bars: a
 * wallpaper does, so framing against a smaller box would be framing against the wrong thing.
 *
 * **The landscape half of the rotating pair is framed letterboxed**, in a landscape-shaped frame inside whatever
 * orientation the phone is in. L1 pinned the *activity* to landscape instead, which is more of the screen to frame with
 * and a device left facing a way the user did not ask for; and it is not needed for the thing that matters, since the
 * stored image takes its resolution from the target screen rather than from the frame it was drawn in.
 *
 * @param uri the picked image, as a string because a `NavKey` is `@Serializable` and `Uri` is not.
 * @param target which slot this fills — the single image, or one half of the rotating pair. It decides the frame's shape
 *   and the stored size, and which of the ViewModel's two write commands the Save button calls.
 * @param onDone leaves the screen — the same action for Save and Cancel, since both mean "I am finished here". The
 *   host wires it to the back stack; this screen does not know it is on one.
 */
@Composable
fun WallpaperCropScreen(
    uri: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    target: CropTarget = CropTarget.SINGLE,
) {
    val viewModel = koinViewModel<WallpaperViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parsed = remember(uri) { uri.toUri() }

    // **The size the image is stored at, which is the target slot's screen rather than this one.** For the single image
    // and the portrait half that is the window upright; for the landscape half it is the same window with its axes
    // swapped, so a landscape wallpaper is stored at landscape resolution even though it was framed on a phone held
    // upright. Independent of how the device is currently held, because the pair is per orientation, not per posture.
    val window = LocalWindowInfo.current.containerSize
    val storedSize = remember(window, target) {
        val short = minOf(window.width, window.height)
        val long = maxOf(window.width, window.height)
        when (target) {
            CropTarget.ROTATING_LANDSCAPE -> IntSize(long, short)
            CropTarget.ROTATING_PORTRAIT -> IntSize(short, long)
            CropTarget.SINGLE -> window
        }
    }
    val frameRatio = if (storedSize.height > 0) storedSize.width.toFloat() / storedSize.height else 1f

    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(parsed) { image = viewModel.preview(parsed)?.asImageBitmap() }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Start filling the viewport, centered — the state the user would otherwise have to reach by hand, and the only one
    // in which nothing is missing. Re-run when either input changes, since the image arrives after the first layout.
    val current = image
    LaunchedEffect(current, viewport) {
        if (current != null && viewport.width > 0 && viewport.height > 0) {
            val cover = coverScale(current, viewport)
            scale = cover
            offset = Offset(
                (viewport.width - current.width * cover) / 2f,
                (viewport.height - current.height * cover) / 2f,
            )
        }
    }

    // Its own theme boundary, as the settings screen is: this is pushed from settings and is our own surface, so it
    // follows the system's dark mode rather than the launcher's wallpaper-brightness signal.
    LauncherTheme {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            if (current != null) {
                Box(
                    modifier = Modifier
                        // The target's shape, centered — which is `fillMaxSize` for a slot shaped like this screen, and
                        // a letterboxed band for the one that is not. `aspectRatio` picks the larger dimension it can
                        // honor, so the frame is always as big as the screen allows.
                        .align(Alignment.Center)
                        .fillMaxSize(FRAME_FRACTION)
                        .aspectRatio(frameRatio)
                        .clipToBounds()
                        .onSizeChanged { viewport = it }
                        .pointerInput(current) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val cover = coverScale(current, viewport)
                                // Floored at the cover scale, so the image can never be pinched small enough to leave
                                // a gap: the viewport is always fully covered, whatever the gesture asks for.
                                val next = (scale * zoom).coerceIn(cover, cover * MAX_ZOOM)
                                // Anchor the zoom on the centroid — the point under the fingers stays under them —
                                // then apply the pan.
                                var moved = centroid - (centroid - offset) * (next / scale) + pan
                                val shownW = current.width * next
                                val shownH = current.height * next
                                // And clamp, so an edge of the image can never be dragged inside the viewport. `minOf`
                                // with 0 handles the axis where the image is not larger than the viewport at all.
                                moved = Offset(
                                    moved.x.coerceIn(minOf(viewport.width - shownW, 0f), 0f),
                                    moved.y.coerceIn(minOf(viewport.height - shownH, 0f), 0f),
                                )
                                scale = next
                                offset = moved
                            }
                        }
                        // Drawn rather than composed as an `Image`: the transform is this screen's own state, applied
                        // to the destination rectangle directly, so there is no layout to fight with.
                        .drawBehind {
                            drawImage(
                                image = current,
                                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                                dstSize = IntSize(
                                    (current.width * scale).roundToInt(),
                                    (current.height * scale).roundToInt(),
                                ),
                            )
                        },
                )
            }

            IconButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(ChromeGap),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }

            MorphicButton(
                onClick = {
                    val source = current ?: return@MorphicButton
                    if (viewport.width == 0 || viewport.height == 0) return@MorphicButton
                    // The viewport's corners, read back into the source's own fractions: the transform is
                    // invertible, so where the visible window sits *on the image* is arithmetic rather than
                    // bookkeeping this screen has to maintain as the fingers move. In *frame* coordinates, which is
                    // why the stored size can differ from it without the rectangle needing to know.
                    val crop = NormalizedCropRect(
                        left = ((0 - offset.x) / (source.width * scale)).coerceIn(0f, 1f),
                        top = ((0 - offset.y) / (source.height * scale)).coerceIn(0f, 1f),
                        right = ((viewport.width - offset.x) / (source.width * scale)).coerceIn(0f, 1f),
                        bottom = ((viewport.height - offset.y) / (source.height * scale)).coerceIn(0f, 1f),
                    )
                    val orientation = target.orientation
                    if (orientation == null) {
                        viewModel.chooseImage(parsed, crop, storedSize.width, storedSize.height, onDone)
                    } else {
                        viewModel.chooseRotatingImage(parsed, crop, storedSize.width, storedSize.height, orientation, onDone)
                    }
                },
                enabled = current != null && !state.busy,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = SaveGap),
            ) {
                Text(if (state.busy) "Saving…" else "Save")
            }
        }
    }
}

/**
 * How much of the screen the frame may take before its own edge is flush with the display's.
 *
 * 1 for a frame shaped like the screen (it fills it, and `aspectRatio` leaves nothing over); short of 1 only so a
 * letterboxed one has a visible margin, which is what makes it read as a frame rather than as a cropped screen.
 */
private const val FRAME_FRACTION = 1f

/**
 * The scale at which [image] just covers [viewport] — the larger of the two ratios, so neither axis is left short.
 *
 * Both the starting scale and the floor a pinch may not go below, which is the same number for the same reason: below
 * it the viewport would show something that is not the image.
 */
private fun coverScale(image: ImageBitmap, viewport: IntSize): Float =
    maxOf(
        viewport.width.toFloat() / image.width,
        viewport.height.toFloat() / image.height,
    )

/** How far in a pinch may go. L1's, and it is generous by design — a wallpaper crop is not a magnifier. */
private const val MAX_ZOOM = 8f

private val ChromeGap = 8.dp
private val SaveGap = 24.dp
