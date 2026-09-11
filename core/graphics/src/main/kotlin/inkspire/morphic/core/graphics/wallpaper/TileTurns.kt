package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where a tile's top-left corner stands at [turn] quarter turns, in fractions of the tile — one of [TileCorners] at a
 * whole turn, and between two the corner turned about the tile's center.
 *
 * **Shared by every tile design whose scrub turns a tile** — Bauhaus's quarter discs and Truchet's arcs — because a turn
 * is only right if it arrives exactly where the bake draws, and the failure is silent: turned the wrong way, a tile
 * still sweeps smoothly, then jumps at the end. Reading the table at whole turns is what keeps each bake exactly the
 * render it was.
 *
 * A turn clockwise on screen, where `y` runs down, is the plain rotation, which is what carries the corner round the
 * other three in [TileCorners]' order. In a tile that is not square the fractions still land on its corners, and the
 * path between them stretches with the tile.
 */
internal fun tileCornerAt(turn: Float): FloatArray {
    val whole = turn.roundToInt()
    if (turn == whole.toFloat()) return TileCorners[whole.mod(TileCorners.size)]
    val angle = turn * QuarterTurn
    return floatArrayOf(Half - Half * cos(angle) + Half * sin(angle), Half - Half * sin(angle) - Half * cos(angle))
}

/** A tile's four corners, in fractions of it — clockwise from the top-left, one per whole turn of [tileCornerAt]. */
private val TileCorners = arrayOf(
    floatArrayOf(0f, 0f),
    floatArrayOf(1f, 0f),
    floatArrayOf(1f, 1f),
    floatArrayOf(0f, 1f),
)

/** A quarter turn, in radians. */
private const val QuarterTurn = (PI / 2).toFloat()

/** Half a tile — where its center sits from a corner. */
private const val Half = 0.5f
