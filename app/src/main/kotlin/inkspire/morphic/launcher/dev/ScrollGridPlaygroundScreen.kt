package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.flowItems
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import kotlin.math.abs
import kotlin.math.ceil

/**
 * G5 harness for [LauncherGrid]'s **SCROLL_GRID** mode. A vertically-scrolling grid whose cell *width* is still
 * measured (viewport ÷ cols, responsive) but whose cell *height* is a fixed dp — so the grid's total height
 * grows with its item count and overflows into a `verticalScroll`. This is the shape of a per-category page in
 * APPS × pager × category, where each page is a scrollable vertical grid rather than a `LazyVerticalGrid`.
 *
 * **+/− items** grows the grid past the viewport (scroll to see the rest); **+/− cols** reflows and re-measures
 * the cell width. Items are uniform 1×1, densely filled — the app-grid case.
 */
@Composable
fun ScrollGridPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        var count by remember { mutableIntStateOf(23) }
        var cols by remember { mutableIntStateOf(4) }
        val cellHeight = 96.dp

        // rows is unused by SCROLL_GRID (height comes from cellHeight × content), but GridConfig needs a valid
        // value — set it to what the content actually reaches for honesty.
        val rows = maxOf(1, ceil(count / cols.toFloat()).toInt())
        val config = GridConfig(rows = rows, cols = cols)

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
                Text("$rows rows", color = colors.contentMuted)
            }

            // The viewport: a bounded box. The grid inside grows taller than it and scrolls.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
            ) {
                LauncherGrid(
                    config = config,
                    cellHeight = cellHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Flow strategy: hand the grid the list, it lays them out row-major — no i/cols maths here.
                    flowItems((1..count).toList()) { n, cellModifier -> Cell("$n", cellModifier) }
                }
            }
        }
    }
}

/** A single grid cell: a coloured tile labelled with its 1-based index. */
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

private val TilePalette = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552), Color(0xFFC26D6D),
)

private fun tileColorFor(label: String): Color = TilePalette[abs(label.hashCode()) % TilePalette.size]
