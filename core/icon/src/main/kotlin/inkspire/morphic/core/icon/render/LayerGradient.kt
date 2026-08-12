package inkspire.morphic.core.icon.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a gradient's angle into the two endpoints that draw it, in pixels of a square box.
 *
 * Shared for [LayerTransform]'s and [LayerFilter]'s reason — two renderers, and an angle read in opposite
 * directions is a bug that looks like a design choice. It is small enough to be tempting to inline on each side,
 * which is exactly how the two would come to disagree about which way 90° runs.
 *
 * **The convention is clockwise from straight down**, so 0° is the top-to-bottom gradient a user expects by
 * default and 90° runs left-to-right. Both endpoints sit on the box's edge through its center, so the gradient
 * spans the whole layer at every angle rather than being foreshortened as it turns.
 */
object LayerGradient {

    /** `[x0, y0, x1, y1]` for [angleDegrees] across a [sizePx]-square box. */
    fun endpoints(angleDegrees: Float, sizePx: Int): FloatArray {
        val radians = angleDegrees * Math.PI.toFloat() / 180f
        val half = sizePx / 2f
        // Straight down at 0°: the direction vector is (sin, cos), which puts 90° along +x.
        val dx = sin(radians) * half
        val dy = cos(radians) * half
        return floatArrayOf(half - dx, half - dy, half + dx, half + dy)
    }
}
