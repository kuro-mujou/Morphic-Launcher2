package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect

/**
 * How a glass surface bends what is under it and catches the light — the two things a refractive emboss does that a
 * bevel does not.
 *
 * **Here for [LayerBevel]'s reason, and it is the same subsystem seen from one step over.** The surface, its height
 * map and its slope are shared with the bevel through [LayerSurface] and are not re-decided here; what this object
 * owns is the pair of answers that are *only* a glass's — where a sloped surface reads its pixel *from*
 * ([sourceOf]), and the specular sheen it strikes ([sheened]). Both are silently wrong when they are wrong: a bend
 * with its sign flipped shrinks the artwork into the swell instead of magnifying it under it — a plausible lens
 * pointing the wrong way — and only the bake draws either, so the check is a test rather than an eye.
 *
 * The lighting itself is [LayerBevel.light] and [LayerBevel.relief], reused verbatim, so a glass and a bevel on one
 * icon are lit by one light from one normal.
 */
object LayerGlass {

    /**
     * The blur radius the glass's [softness][LayerEffect.Glass.softness] asks for, in pixels — **floored, never
     * null**, unlike a bevel's or a halo's.
     *
     * A bevel with no blur has no slope and so nothing to do, which is why [LayerBevel.radiusPxOrNull] may answer
     * null and the effect skip. A glass always has something to do — it is only reached when it bends or shines —
     * so it always needs a surface to read, and a raw-alpha height map (softness at zero) is a real, if hard, look
     * rather than an absence. The floor is what keeps `BlurMaskFilter`, which rejects a radius of zero, from ever
     * seeing one.
     */
    fun radiusPx(glass: LayerEffect.Glass, sizePx: Int): Float =
        (glass.softness * sizePx).coerceAtLeast(MinRadiusPx)

    /** How far a fully-sloped part of the surface bends the sampling, in pixels — a fraction of the box. */
    fun refractionPx(glass: LayerEffect.Glass, sizePx: Int): Float = glass.refraction * sizePx

    /**
     * Where the output pixel at ([x], [y]) reads *from*, given the surface slope there, written into [out] as
     * `[srcX, srcY]` in pixels and **not rounded** — the fractional part is the whole of what makes a lens look made
     * rather than stepped, and [LayerSample.bilinear] is what reads it.
     *
     * **The sampling moves *along* the up-slope, and that sign is the effect.** [slopeX] is `∂height/∂x`, so it
     * points downhill; adding it moves the read toward *lower* surface, i.e. away from the swell's peak — which
     * pulls the content under a convex swell inward and so **magnifies** it, the way a lens does. Flip the sign and
     * the swell minifies instead, which is a coherent picture of the wrong thing. The scale is already the
     * width-cancelled slope [LayerSurface.slope] produces, so a softer, wider glass bends about as much as a tight
     * one rather than fading.
     */
    fun sourceOf(x: Int, y: Int, slopeX: Float, slopeY: Float, refractPx: Float, out: FloatArray) {
        out[0] = x + slopeX * refractPx
        out[1] = y + slopeY * refractPx
    }

    /**
     * [sampledArgb] with the glass's sheen screened on where the surface faces the light — [relief] positive — and
     * left alone where it faces away or lies flat.
     *
     * **Keeps [sampledArgb]'s own alpha**, which is what stops the sheen floating off the glass: a bent pixel that
     * read from beyond the artwork comes back transparent, and screening light onto transparency leaves it
     * transparent. So the highlight lives only where there is bent artwork under it — the surface's own edge — and
     * needs no separate clip.
     *
     * **Screened, not painted**, the same choice [LayerBevel] makes for its highlight band: light adds to the color
     * already there rather than replacing it, so a sheen brightens the artwork instead of laying a flat band of
     * white over it.
     */
    fun sheened(sampledArgb: Int, relief: Float, glass: LayerEffect.Glass): Int {
        if (relief <= 0f) return sampledArgb
        val amount = (relief * glass.highlightStrength).coerceIn(0f, 1f)
        if (amount <= 0f) return sampledArgb

        val highlight = glass.highlightArgb
        fun channel(shift: Int): Int {
            val d = (sampledArgb shr shift) and 0xFF
            val s = (highlight shr shift) and 0xFF
            val screened = 255 - (255 - d) * (255 - s) / 255
            return (d + (screened - d) * amount).toInt().coerceIn(0, 255)
        }

        return (sampledArgb and 0xFF000000.toInt()) or
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** A one-pixel surface is the least that has a slope to read; below it `BlurMaskFilter` has nothing to blur. */
    private const val MinRadiusPx = 1f
}
