package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a bevel's surface is lit: where the light is, how steep the slope reads, and how much of the light each pixel
 * catches.
 *
 * **Here for [LayerShadow]'s reason rather than the shared-derivation one.** Only the bake draws a bevel, so nothing
 * is competing with this arithmetic — it is separated because every line of `IconRenderer` needs an emulator, and
 * because all three answers below are *silently* wrong when they are wrong: a light vector with a sign flipped lights
 * the far side of the relief, which is a perfectly plausible picture of a bevel lit from somewhere else.
 *
 * The whole effect is one idea. The layer's alpha, blurred, is read as a **height map**; where that height changes,
 * the surface is sloping; a slope facing the light catches more of it than a flat area does, and one facing away
 * catches less. [relief] is that comparison, and the two bands the renderer paints are its two signs.
 */
object LayerBevel {

    /** A unit vector pointing **at** the light, in screen coordinates with `z` out of the screen toward the viewer. */
    data class Light(val x: Float, val y: Float, val z: Float)

    /**
     * Where the light is, from the effect's angle and altitude.
     *
     * **The angle names where the light *travels*, which is the convention every other effect here runs on** —
     * clockwise from straight down, so 45° is a light moving down and to the right and therefore *coming from* the
     * top-left. That is where `LayerEffect.Shadow`'s default throw already implies the light is, and matching it is
     * what stops one icon's bevel and its drop shadow disagreeing about the time of day.
     *
     * So the horizontal part is **negated**: the vector wanted here points back at the source.
     *
     * **Altitude is measured up from the surface**, so 0 is a light skimming along it and 90 is one directly
     * overhead. At 90 the horizontal part vanishes and the light stops favouring any side — but a tilted surface
     * still catches less of an overhead light than a flat one, so what is left is *every* slope shading equally: a
     * uniform darkened rim rather than nothing. That is a real look, and it is why the studio's altitude control
     * runs the whole way up.
     */
    fun light(angleDegrees: Float, altitudeDegrees: Float): Light {
        val angle = angleDegrees * PI.toFloat() / 180f
        val altitude = altitudeDegrees.coerceIn(0f, 90f) * PI.toFloat() / 180f
        val flat = cos(altitude)
        return Light(x = -sin(angle) * flat, y = -cos(angle) * flat, z = sin(altitude))
    }

    /**
     * What a height difference of one unit across one pixel counts as a slope, given a bevel blurred over [radiusPx].
     *
     * **This is what makes the bevel's strength independent of its width, and without it the size control would be
     * an intensity control as well.** A blurred edge spreads the whole of its rise over about twice the blur radius,
     * so its gradient is proportional to `1 / radius` — a narrow bevel would come out violently lit and a wide one
     * would fade to nothing, which is the opposite of what someone widening a bevel is asking for. Scaling by the
     * radius cancels it exactly, and what is left is a constant that decides how steep a *fully* developed bevel
     * reads. That constant is a look rather than a control: a depth slider beside the two strengths would be a
     * second way to reach the same picture, since scaling the slope and scaling the bands both just make it stronger.
     */
    fun slopeScale(radiusPx: Float): Float = radiusPx * Steepness

    /**
     * How much more light this pixel's slope catches than a flat one — **−1..1**, and zero where the surface is flat.
     *
     * **The subtraction is the whole of why a bevel appears only at the edges.** A plain Lambert term lights every
     * surface facing the viewer, so the flat interior of an icon would come out uniformly brightened and the effect
     * would read as a brightness control with an odd rim. Measuring each pixel *against the flat case* leaves the
     * interior at exactly zero, so the renderer paints nothing there.
     *
     * @param slopeX how fast the height rises to the right, already through [slopeScale].
     * @param slopeY the same, downward.
     */
    fun relief(slopeX: Float, slopeY: Float, light: Light): Float {
        // The surface normal of a height field: rising to the right tilts the face toward the left, hence the signs.
        val nx = -slopeX
        val ny = -slopeY
        val length = sqrt(nx * nx + ny * ny + 1f)
        val lit = (nx * light.x + ny * light.y + light.z) / length
        return (lit - light.z).coerceIn(-1f, 1f)
    }

    /** [argb] with its alpha replaced by [amount] of full — how each band's colour reaches the canvas. */
    fun banded(argb: Int, amount: Float): Int {
        val alpha = (amount.coerceIn(0f, 1f) * 255f).toInt()
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    /** The blur radius a bevel's size asks for, as a fraction of the box — the same reading every halo takes. */
    fun radiusPxOrNull(bevel: LayerEffect.Bevel, sizePx: Int): Float? =
        LayerShadow.radiusPxOrNull(bevel.size, sizePx)

    /**
     * How steep a fully developed bevel reads, once [slopeScale] has cancelled the width out.
     *
     * Tuned so that the strongest part of a default bevel lands near the top of [relief]'s range without sitting on
     * it: pinned there, every bevel wider than a hairline would clip to a flat band of pure highlight and the
     * altitude control would stop doing anything over most of its travel.
     */
    private const val Steepness = 2.5f
}
