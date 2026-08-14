package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.floor

/**
 * The noise field a grain effect pushes its pixels through.
 *
 * [LayerRipple]'s counterpart, and here for the same reason: only the bake draws this, so nothing is competing with
 * the arithmetic — it is separated because a noise field that is subtly wrong looks exactly like a noise field that
 * is right, and because pulled out of `IconRenderer` it can be checked without an emulator.
 *
 * **Smooth value noise, not per-pixel random**, which is the whole difference between grain and static. A fresh
 * number per pixel scatters the artwork into confetti; interpolating between lattice points moves neighbouring
 * pixels *together*, so the picture tears into pieces that are still recognisable as pieces of it.
 *
 * **Deterministic, and defined in fractions of the box.** The same recipe has to grain identically every time it is
 * baked — otherwise the icon would shimmer as the studio re-bakes, and a draft would not predict the full-size
 * result — and identically at 96px and 288px, which is why the field is sampled in normalised coordinates rather
 * than in pixels. That is also why there is no seed: a hash of position *is* the randomness, and a seed would be a
 * second control offering nothing the grain size does not.
 */
object LayerGrain {

    /**
     * The field's value at ([x], [y]) in lattice units, in −1..1.
     *
     * [salt] picks *which* field: a displacement needs two independent ones to push in two dimensions, and sampling
     * the same field twice would push every pixel along the diagonal.
     */
    fun noise(x: Float, y: Float, salt: Int): Float {
        val cellX = floor(x).toInt()
        val cellY = floor(y).toInt()
        // Smoothstep rather than the raw fraction: a linear blend leaves a visible crease along every lattice line,
        // because the *slope* jumps there even though the value does not.
        val fadeX = smooth(x - cellX)
        val fadeY = smooth(y - cellY)

        val top = lerp(at(cellX, cellY, salt), at(cellX + 1, cellY, salt), fadeX)
        val bottom = lerp(at(cellX, cellY + 1, salt), at(cellX + 1, cellY + 1, salt), fadeX)
        return lerp(top, bottom, fadeY)
    }

    /** How far the field pushes at its extreme, in pixels. */
    fun amplitudePx(grain: LayerEffect.Grain, sizePx: Int): Float = grain.amplitude * sizePx

    /**
     * How far apart the lattice points sit, in pixels — the size of the pieces the artwork tears into.
     *
     * Floored above zero because it divides, and [LayerEffect.Grain.isIdentity] already refuses a size of zero; this
     * is the guard for a stored recipe that never went through it.
     */
    fun cellPx(grain: LayerEffect.Grain, sizePx: Int): Float =
        (grain.grainSize * sizePx).coerceAtLeast(MinCellPx)

    /** One lattice point's value, in −1..1 — the randomness itself, and the only place it comes from. */
    private fun at(x: Int, y: Int, salt: Int): Float {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0xFFFF) / HalfRange - 1f
    }

    /** `3t² − 2t³`, whose slope is zero at both ends — which is what removes the crease at a lattice line. */
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    /** Below a pixel the lattice is finer than the grid it is sampled on, so the field degrades into static. */
    private const val MinCellPx = 1f

    /** Half of `0xFFFF`, which maps the hash's sixteen bits onto −1..1. */
    private const val HalfRange = 32767.5f
}
