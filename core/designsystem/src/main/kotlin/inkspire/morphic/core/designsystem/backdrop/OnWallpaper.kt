package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toRect
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicTheme
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Declares that [content] is drawn **straight onto the wallpaper**, and themes it against the spot it occupies — the
 * fourth background beside [OnFilm], [OnPanel] and settings' solid color, and the only one that varies across a screen.
 *
 * **The spot is read from where the content is on screen, as it moves.** `onLayoutRectChanged` rather than
 * `onGloballyPositioned`, because the second does not reliably re-fire when a scroller moves a node — and the HOME list
 * scrolls, while a pager swipe carries every label across the picture. The ink is snapshot state read in composition,
 * so a label recomposes only when its ink *flips*; the backing's alpha is read in the draw phase, so a swipe that fades
 * it costs a redraw and nothing more.
 *
 * **The backing is a pill behind the content, outset past it**, in the ink's own palette's background — the soft lift a
 * label straddling a boundary needs. It is drawn by this node rather than by each caller so that the thing sized by the
 * rule and the thing painted are one.
 *
 * **Does nothing, and themes nothing, where there is no spot to read**: no measured wallpaper (another app's picture, or
 * no backdrop at all — the enclosing theme is then the system's whole-screen verdict), or already on a frost, where
 * what is behind the content is the film or a panel and [OnFilm]/[OnPanel] have answered. A settings preview therefore
 * renders exactly as before.
 */
@Composable
fun OnWallpaper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val brightness = LocalBackdrop.current?.brightness?.takeUnless { LocalOverFrost.current }
    if (brightness == null) {
        Box(modifier = modifier, propagateMinConstraints = true) { content() }
        return
    }
    val spot = remember { WallpaperSpot() }
    // Until the first layout there is no spot to read, and the enclosing theme is HOME's own verdict — one frame.
    val light = spot.light ?: (LocalMorphicColors.current == MorphicColors.Dark)
    MorphicTheme(darkTheme = light) {
        val backing = LocalMorphicColors.current.background
        Box(
            modifier = modifier
                .onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) { bounds ->
                    spot.read(brightness, bounds.boundsInScreen.toRect())
                }
                .drawBehind { drawBacking(backing, spot.backingAlpha) },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** A pill around this node, outset so the text does not touch its edge. Nothing at all when [alpha] is zero. */
private fun DrawScope.drawBacking(color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val dx = 6.dp.toPx()
    val dy = 2.dp.toPx()
    val height = size.height + dy * 2
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(-dx, -dy),
        size = Size(size.width + dx * 2, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

/** One [OnWallpaper]'s last reading — split so the ink and the backing invalidate different phases. */
@Stable
private class WallpaperSpot {
    var light by mutableStateOf<Boolean?>(null)
        private set
    var backingAlpha by mutableFloatStateOf(0f)
        private set
    private val reader = InkReader()

    fun read(brightness: BackdropBrightness, screen: Rect) {
        val ink = reader.read(brightness, screen)
        light = ink.light
        backingAlpha = ink.backingAlpha
    }
}

/**
 * Reads the [Ink] of a screen rectangle off a [BackdropBrightness].
 *
 * **One buffer, reused**, so a label being swiped across the picture allocates nothing per frame. A rectangle partly off
 * the picture — a page mid-swipe — is read from the cells along the edge it hangs over, which is the picture it is about
 * to be over.
 */
internal class InkReader {
    private var scratch = FloatArray(64)

    fun read(brightness: BackdropBrightness, screen: Rect): Ink {
        val map = brightness.map
        val cells = brightness.screenToMap(screen)
        val firstColumn = floor(cells.left).toInt().coerceIn(0, map.columns - 1)
        val lastColumn = (ceil(cells.right).toInt() - 1).coerceIn(firstColumn, map.columns - 1)
        val firstRow = floor(cells.top).toInt().coerceIn(0, map.rows - 1)
        val lastRow = (ceil(cells.bottom).toInt() - 1).coerceIn(firstRow, map.rows - 1)
        val count = (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1)
        if (scratch.size < count) scratch = FloatArray(count)
        var i = 0
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) scratch[i++] = map[column, row]
        }
        return inkOver(scratch, count)
    }
}
