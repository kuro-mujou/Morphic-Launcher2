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
 * How many dots it takes is [swatchCount] — a property of the shape rather than of the caller, since each one
 * becomes itself at a different number.
 */
@Composable
internal fun IconArrangementSwatch(
    arrangement: IconArrangement,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Canvas(modifier) {
        val slots = iconContainerSlots(arrangement, arrangement.swatchCount, size.width, size.height, density)
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

/**
 * How many dots it takes for an arrangement to look like itself.
 *
 * **Not one number for all of them**, because they do not become recognizable at the same count. A grid is a grid
 * at six and only gets busier; a beehive needs its centre plus one complete ring, which is seven exactly, and an
 * eighth dot starts a second ring that reads as a lump. A ring is a ring almost immediately — eight is chosen for
 * evenness rather than for legibility.
 *
 * **The fan is the demanding one and the reason this is per-shape at all.** Its icons sit on arcs holding 1, then
 * 3, then 4, then 6, so under eight it draws one arc and part of another — a scatter rather than a shape.
 * Fourteen is where four arcs come out complete, and that is what makes the nesting read.
 *
 * Exhaustive, so a new [IconArrangement] must say what shows it before it can be offered.
 */
private val IconArrangement.swatchCount: Int
    get() = when (this) {
        IconArrangement.GRID -> 6
        IconArrangement.CIRCLE -> 8
        IconArrangement.BEEHIVE -> 7
        IconArrangement.FAN_TOP_LEFT,
        IconArrangement.FAN_TOP_RIGHT,
        IconArrangement.FAN_BOTTOM_LEFT,
        IconArrangement.FAN_BOTTOM_RIGHT,
        -> 14
    }
