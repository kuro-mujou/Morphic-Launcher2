package inkspire.morphic.launcher.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Spike: **ordered list → the same [LauncherGrid]'s flow strategy** (static start of grid plan G3). Proves the
 * reuse claim — the identical grid that renders home's stored coordinates renders an ordered list too, via
 * [flowItems], across pages of a [LauncherPager].
 *
 * Play with it: **+/− items** overflow onto new pages; **+/− cols** reflows the whole list (fewer cols → taller
 * fill → more pages). Swipe to page. Pagination is just *which slice* of the list each page gets; `flowItems`
 * lays that slice out row-major — no coordinate maths in the caller.
 */
@Composable
fun ReflowPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        var count by remember { mutableIntStateOf(11) }
        var cols by remember { mutableIntStateOf(4) }
        val rows = 4

        val config = GridConfig(rows = rows, cols = cols)
        val perPage = rows * cols

        // pageCount reads the live state each call, so adding items / changing cols repaginates the pager.
        val pagerState = rememberLauncherPagerState(
            pageCount = { maxOf(1, ceil(count.toFloat() / (rows * cols)).toInt()) },
            infiniteScroll = { false },
        )

        Column(
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper("items", count, colors.content, colors.accent, { count = (count - 1).coerceAtLeast(0) }) {
                    count += 1
                }
                Stepper("cols", cols, colors.content, colors.accent, { cols = (cols - 1).coerceAtLeast(2) }) {
                    cols = (cols + 1).coerceAtMost(6)
                }
                Text(
                    "page ${pagerState.currentPage + 1}/${maxOf(1, ceil(count.toFloat() / perPage).toInt())}",
                    color = colors.contentMuted,
                )
            }

            LauncherPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .launcherPagerSwipe(pagerState),
            ) { page ->
                Box(Modifier.fillMaxSize().padding(4.dp)) {
                    GridLines(config, colors.divider, Modifier.fillMaxSize())
                    // Pagination = which slice of the list this page shows; flowItems positions the slice.
                    val start = page * perPage
                    val pageItems = (start until minOf(count, start + perPage)).toList()
                    LauncherGrid(config = config, modifier = Modifier.fillMaxSize()) {
                        flowItems(pageItems) { i, cellModifier -> Cell(label = "${i + 1}", modifier = cellModifier) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(maxOf(1, ceil(count.toFloat() / perPage).toInt())) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.accent else colors.contentDisabled),
                    )
                }
            }
        }
    }
}

/** A single reflowed cell: a coloured tile labelled with its 1-based order index. */
@Composable
private fun Cell(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileColorFor(label)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** A `− value +` stepper. */
@Composable
private fun Stepper(label: String, value: Int, textColor: Color, buttonColor: Color, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("−", color = buttonColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onDec).padding(horizontal = 6.dp))
        Text("$label $value", color = textColor)
        Text("+", color = buttonColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onInc).padding(horizontal = 6.dp))
    }
}

/** Draws the cell lattice for [config] over the measured area, so empty cells on the last page are visible. */
@Composable
private fun GridLines(config: GridConfig, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cellW = size.width / config.cols
        val cellH = size.height / config.rows
        val stroke = 1.dp.toPx()
        for (c in 0..config.cols) drawLine(color, Offset(c * cellW, 0f), Offset(c * cellW, size.height), stroke)
        for (r in 0..config.rows) drawLine(color, Offset(0f, r * cellH), Offset(size.width, r * cellH), stroke)
    }
}

private val TilePalette = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552), Color(0xFFC26D6D),
)

private fun tileColorFor(label: String): Color = TilePalette[abs(label.hashCode()) % TilePalette.size]
