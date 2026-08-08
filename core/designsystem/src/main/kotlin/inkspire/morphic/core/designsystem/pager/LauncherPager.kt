package inkspire.morphic.core.designsystem.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * @param keepAllPagesPlaced when true, off-screen pages are still *placed* (far off, clipped) instead of
 *   culled. Set this during an item drag: a page that scrolls away must stay placed so a tile being dragged
 *   out of it keeps its pointer stream (an unplaced node stops receiving events).
 * @param pageTransform optional per-page `graphicsLayer` block (parallax/scale/fade).
 * @param pageContent renders the content of a given page index.
 */
@Composable
fun LauncherPager(
    state: LauncherPagerState,
    modifier: Modifier = Modifier,
    keepAllPagesPlaced: Boolean = false,
    pageTransform: (GraphicsLayerScope.(PageTransformScope) -> Unit)? = null,
    pageContent: @Composable (page: Int) -> Unit,
) {
    val pageCount = state.pageCount

    // **A shrinking page count has to move the pager, and nothing else will.** The position is a float the layout
    // places from; when the last page goes away it is left past the end, where `pagePosition` rubber-bands it —
    // so the pager sits frozen part-way between two pages with no gesture in flight to spring it back. Removing
    // the last item from the last page reaches that state without the screen being touched at all.
    //
    // Here rather than in each surface because it is a property of *a pager whose count can change*, which is
    // every one of them: home's pages come and go with its items, and the APPS pager's with what is installed.
    LaunchedEffect(pageCount) { state.settleWithinPageCount() }

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
                if (!keepAllPagesPlaced && abs(pageOffset) > 1.5f) return@forEachIndexed // cull off-screen pages

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
