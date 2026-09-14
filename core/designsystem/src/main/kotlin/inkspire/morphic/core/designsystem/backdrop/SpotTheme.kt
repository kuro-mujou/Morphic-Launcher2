package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toRect
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicTheme
import kotlin.math.ceil
import kotlin.math.floor

/**
 * **A picture that text sits straight on**, read spot by spot: its luminance map, and the wash painted over it.
 *
 * Two of them exist, and which one is behind a piece of text is a fact about where it is composed: the **wallpaper**
 * on HOME (no wash), and the **film** on APPS, in a collection and in the menu over HOME (the film's blurred picture
 * and the film's wash). A frosted panel or a flat scrim is neither — there is one tone under the text and the enclosing
 * theme already answers for it — so it provides none.
 *
 * @param wash the color painted over the picture, alpha included; `Color.Transparent` for none.
 */
class InkSurface(val brightness: BackdropBrightness, wash: Color = Color.Transparent) {
    private val washLuminance = wash.luminance()
    private val washAlpha = wash.alpha

    /** The luminance the eye sees at a cell — the picture composited under the wash, by [washedLuminance]'s formula. */
    fun luminanceAt(column: Int, row: Int): Float = washedLuminance(brightness.map[column, row], washLuminance, washAlpha)
}

/**
 * The [InkSurface] behind content composed here, or null where the background is one flat tone (a frosted panel, a
 * scrim, settings) and the enclosing theme is the answer.
 */
val LocalInkSurface = staticCompositionLocalOf<InkSurface?> { null }

/**
 * Themes [content] against the patch of [LocalInkSurface] it actually occupies — the one place text on a picture takes
 * its color from.
 *
 * **The spot is read from where the content is on screen, as it moves.** `onLayoutRectChanged` rather than
 * `onGloballyPositioned`, because the second does not reliably re-fire when a scroller moves a node — and the lists
 * scroll, while a pager swipe carries every label across the picture. The ink is snapshot state read in composition,
 * so content recomposes only when its ink *flips*; the backing's alpha is read in the draw phase, so a swipe that fades
 * it costs a redraw and nothing more.
 *
 * **The backing is a pill behind the content, outset past it**, in the ink's own palette's background — the soft lift
 * a spot straddling light and dark needs. Drawn here rather than by callers so the thing [inkOver] sizes and the thing
 * painted are one.
 *
 * **With no surface it does nothing and themes nothing**, which is every flat background and every settings preview.
 */
@Composable
fun SpotTheme(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val surface = LocalInkSurface.current
    if (surface == null) {
        Box(modifier = modifier, propagateMinConstraints = true) { content() }
        return
    }
    val spot = remember { InkSpot() }
    // Until the first layout there is no spot to read, and the enclosing theme is the surface's own verdict — one frame.
    val light = spot.light ?: (LocalMorphicColors.current == MorphicColors.Dark)
    MorphicTheme(darkTheme = light) {
        val backing = LocalMorphicColors.current.background
        Box(
            modifier = modifier
                .onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) { bounds ->
                    spot.read(surface, bounds.boundsInScreen.toRect())
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

/** One [SpotTheme]'s last reading — split so the ink and the backing invalidate different phases. */
@Stable
private class InkSpot {
    var light by mutableStateOf<Boolean?>(null)
        private set
    var backingAlpha by mutableFloatStateOf(0f)
        private set
    private val reader = InkReader()

    fun read(surface: InkSurface, screen: Rect) {
        val ink = reader.read(surface, screen)
        light = ink.light
        backingAlpha = ink.backingAlpha
    }
}

/**
 * Reads the [Ink] of a screen rectangle off an [InkSurface].
 *
 * **One buffer, reused**, so a label being swiped across the picture allocates nothing per frame. A rectangle partly off
 * the picture — a page mid-swipe — is read from the cells along the edge it hangs over, which is the picture it is about
 * to be over.
 */
internal class InkReader {
    private var scratch = FloatArray(64)

    fun read(surface: InkSurface, screen: Rect): Ink {
        val map = surface.brightness.map
        val cells = surface.brightness.screenToMap(screen)
        val firstColumn = floor(cells.left).toInt().coerceIn(0, map.columns - 1)
        val lastColumn = (ceil(cells.right).toInt() - 1).coerceIn(firstColumn, map.columns - 1)
        val firstRow = floor(cells.top).toInt().coerceIn(0, map.rows - 1)
        val lastRow = (ceil(cells.bottom).toInt() - 1).coerceIn(firstRow, map.rows - 1)
        val count = (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1)
        if (scratch.size < count) scratch = FloatArray(count)
        var i = 0
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) scratch[i++] = surface.luminanceAt(column, row)
        }
        return inkOver(scratch, count)
    }
}
