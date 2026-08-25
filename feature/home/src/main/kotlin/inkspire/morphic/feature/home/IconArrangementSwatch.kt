package inkspire.morphic.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalDensity
import inkspire.morphic.core.model.IconArrangement

/**
 * An [IconArrangement] drawn as the shape it makes — the picture a user picks one by.
 *
 * **The dots are placed by the arrangement itself**, through the same `iconContainerSlots` the real container lays
 * its icons out with, so a swatch cannot claim a shape the container does not make. That is the whole reason this
 * is a `Canvas` over real slots rather than seven hand-drawn glyphs: a drawing agrees on the day it is made and
 * drifts the first time a formula is retuned, and a preview that has drifted is worse than none because it is
 * believed. Slice A retuned three of these formulas; nothing here needed touching.
 *
 * What it deliberately does *not* reuse is `IconContainerCell`: that needs resolved apps to draw, and which apps
 * are in the container has nothing to do with which shape the user is choosing. Dots say "shape" without
 * pretending to preview contents the container does not have yet.
 *
 * @param count how many dots to arrange. **Eight by default, and the fan is what sets the floor**: its icons sit
 *   on arcs holding 1, then 3, then 4, so anything under eight draws a single arc and part of another — which
 *   reads as a scatter rather than as a fan. Eight is the first count that shows three complete arcs. The other
 *   shapes are legible well below that, so the fan decides.
 */
@Composable
internal fun IconArrangementSwatch(
    arrangement: IconArrangement,
    color: Color,
    modifier: Modifier = Modifier,
    count: Int = 8,
) {
    val density = LocalDensity.current
    Canvas(modifier) {
        val slots = iconContainerSlots(arrangement, count, size.width, size.height, density)
        val path = Path()
        slots.forEach { slot ->
            path.addRoundRect(
                RoundRect(
                    rect = androidx.compose.ui.geometry.Rect(
                        offset = Offset(slot.x, slot.y),
                        size = Size(slot.width, slot.height),
                    ),
                    // A quarter of the side, which is roughly a launcher icon's own squircle at this scale — round
                    // enough to read as an icon rather than a tile, square enough that the grid still reads as one.
                    cornerRadius = CornerRadius(minOf(slot.width, slot.height) * 0.25f),
                ),
            )
        }
        drawOutline(Outline.Generic(path), color = color)
    }
}
