package inkspire.morphic.launcher.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import kotlin.math.abs

/**
 * G1 harness for [LauncherGrid] — the static grid, no drag. Cycle through demo grids of **different dims**
 * (home 4×4, pager 4×3, dock 4×1, drawer 1×3) and toggle **empty**; each fills the surface, so rotating the
 * device re-measures the cells (responsive check). The demos exercise the layout matrix: 1×1s, **multi-cell
 * spans** (2×2, 1×2, 2×1), **gaps** (empty cells left empty — no packing), and **edge spans** flush to the
 * right/bottom.
 */
@Composable
fun GridPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        var index by remember { mutableStateOf(0) }
        var empty by remember { mutableStateOf(false) }
        val demo = Demos[index]

        Column(
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "${demo.label} (${demo.config.rows}×${demo.config.cols})  →",
                    color = colors.accent,
                    modifier = Modifier.clickable { index = (index + 1) % Demos.size },
                )
                Text(
                    text = "empty: ${if (empty) "on" else "off"}",
                    color = colors.contentMuted,
                    modifier = Modifier.clickable { empty = !empty },
                )
            }

            // The grid fills the remaining area; a grid-line overlay behind it shows the cell lattice.
            Box(Modifier.fillMaxSize()) {
                GridLines(demo.config, colors.divider, Modifier.fillMaxSize())
                LauncherGrid(config = demo.config, modifier = Modifier.fillMaxSize()) {
                    if (!empty) {
                        for (tile in demo.tiles) {
                            GridTile(
                                label = tile.label,
                                span = "${tile.placement.rowSpan}×${tile.placement.colSpan}",
                                modifier = Modifier.gridPlacement(tile.placement),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One demo tile: a colored, span-filling box labelled with its name and span. */
@Composable
private fun GridTile(label: String, span: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileColorFor(label)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(span, color = Color(0xBBFFFFFF))
        }
    }
}

/** Draws the cell lattice for [config] over the measured area, so gaps and cell sizes are visible. */
@Composable
private fun GridLines(config: GridConfig, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cellW = size.width / config.cols
        val cellH = size.height / config.rows
        val stroke = 1.dp.toPx()
        for (c in 0..config.cols) {
            val x = c * cellW
            drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), stroke)
        }
        for (r in 0..config.rows) {
            val y = r * cellH
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), stroke)
        }
    }
}

/** A tile with its target placement. */
private class DemoTile(val label: String, val placement: GridPlacement)

/** A named demo grid: its dims and the tiles placed on it. */
private class DemoGrid(val label: String, val config: GridConfig, val tiles: List<DemoTile>)

private fun tile(label: String, row: Int, col: Int, rowSpan: Int = 1, colSpan: Int = 1) =
    DemoTile(label, GridPlacement(page = 0, row = row, col = col, rowSpan = rowSpan, colSpan = colSpan))

private val Demos = listOf(
    // Home 4×4: 1×1s, a 2×2 flush to the right edge, a 1×2, a 2×1, a corner tile, and deliberate gaps.
    DemoGrid(
        "Home", GridConfig(rows = 4, cols = 4),
        listOf(
            tile("A", 0, 0),
            tile("B", 0, 1),
            tile("C", 0, 2, rowSpan = 2, colSpan = 2), // spans to the right edge
            tile("D", 1, 0, colSpan = 2),              // 1×2
            tile("E", 2, 0, rowSpan = 2),              // 2×1
            tile("F", 2, 1),
            tile("G", 3, 3),                           // bottom-right corner
        ),
    ),
    // Pager 4×3: a full-ish page with one wide tile and a trailing gap.
    DemoGrid(
        "Pager", GridConfig(rows = 3, cols = 4),
        listOf(
            tile("A", 0, 0), tile("B", 0, 1), tile("C", 0, 2), tile("D", 0, 3),
            tile("E", 1, 0, colSpan = 2), tile("F", 1, 2),     // gap at (1,3)
            tile("G", 2, 0), tile("H", 2, 3),                  // gaps in the middle of the last row
        ),
    ),
    // Dock 4×1: a strip with a gap.
    DemoGrid(
        "Dock", GridConfig(rows = 1, cols = 4),
        listOf(tile("A", 0, 0), tile("B", 0, 1), tile("C", 0, 3)), // gap at (0,2)
    ),
    // Drawer 1×3: a vertical column.
    DemoGrid(
        "Drawer", GridConfig(rows = 3, cols = 1),
        listOf(tile("A", 0, 0), tile("B", 1, 0), tile("C", 2, 0)),
    ),
)

private val TilePalette = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552), Color(0xFFC26D6D),
)

private fun tileColorFor(label: String): Color = TilePalette[abs(label.hashCode()) % TilePalette.size]
