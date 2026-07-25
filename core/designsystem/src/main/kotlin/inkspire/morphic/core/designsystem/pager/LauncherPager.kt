package inkspire.morphic.core.designsystem.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A horizontal pager built as a custom [Layout], with **optional infinite wrap** driven by [LauncherPagerState].
 *
 * Why not M3's `HorizontalPager`: making it "infinite" means seeding it with `Int.MAX_VALUE` pages, which lags
 * and shifts page indices under an active drag. Here only the real [LauncherPagerState.pageCount] pages are
 * composed; each is placed at `pageOffset * pageWidth`, where `pageOffset` wraps via modular arithmetic when
 * unbounded. Pages more than ~1.5 widths off-screen are skipped, so only a few are ever placed.
 *
 * Each page is measured to the full viewport, so its content (typically a grid) fills the page. [pageTransform]
 * may apply a per-page `graphicsLayer` effect from the page's [PageTransformScope.pageOffset].
 *
 * @param state paging position + wrap/bounded policy.
 * @param pageTransform optional per-page `graphicsLayer` block (parallax/scale/fade).
 * @param pageContent renders the content of a given page index.
 */
@Composable
fun LauncherPager(
    state: LauncherPagerState,
    modifier: Modifier = Modifier,
    pageTransform: (GraphicsLayerScope.(PageTransformScope) -> Unit)? = null,
    pageContent: @Composable (page: Int) -> Unit,
) {
    val pageCount = state.pageCount
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            for (page in 0 until pageCount) {
                key(page) { Box(Modifier.clipToBounds()) { pageContent(page) } }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = constraints.maxWidth
        val pageHeight = constraints.maxHeight
        if (state.pageSize != pageWidth) state.pageSize = pageWidth
        if (state.containerHeight != pageHeight) state.containerHeight = pageHeight

        val raw = state.pagePosition
        val wrap = !state.isBounded
        val pageConstraints = Constraints.fixed(pageWidth, pageHeight)
        val placeables = measurables.map { it.measure(pageConstraints) }

        val halfCount = pageCount / 2f
        val divisor = pageCount.toFloat()

        layout(pageWidth, pageHeight) {
            placeables.forEachIndexed { pageIndex, placeable ->
                // When wrapping, a page can appear on whichever side is nearer the viewport; the mod keeps the
                // offset in [-halfCount, halfCount).
                val pageOffset = if (wrap) {
                    (pageIndex - raw + halfCount).mod(divisor) - halfCount
                } else {
                    pageIndex - raw
                }
                if (abs(pageOffset) > 1.5f) return@forEachIndexed // cull off-screen pages

                val x = (pageOffset * pageWidth).roundToInt()
                if (pageTransform != null) {
                    val scope = PageTransformScope(pageOffset)
                    placeable.placeRelativeWithLayer(x = x, y = 0) { pageTransform(scope) }
                } else {
                    placeable.placeRelative(x = x, y = 0)
                }
            }
        }
    }
}
