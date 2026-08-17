package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerBlend
import kotlin.math.roundToInt

/**
 * How a layer's pixels join the ones beneath it — the separable blend modes, done as arithmetic.
 *
 * **This exists because `PorterDuff.Mode` is not the set of blend modes it appears to be.** The bake composited a
 * layer by handing its [LayerBlend] to a `PorterDuffXfermode`, and one of those five is not the blend of the same
 * name: `PorterDuff.Mode.MULTIPLY` is documented as `[Sa × Da, Sc × Dc]`, so the result **alpha is the product**.
 * A foreground layer set to multiply therefore multiplied the alpha of everything beneath it by zero wherever the
 * foreground was transparent, and on a device every app's background plate vanished from the home screen.
 *
 * **The live path was correct throughout**, Compose's `BlendMode` being a true separable blend — so the studio
 * showed the icon intact and only the baked icon was wrong. That is the two-renderer hazard in its worst form: the
 * one kind of divergence the editor structurally cannot show you.
 *
 * **The obvious repair is `Paint.setBlendMode`, and it is API 29 against a `minSdk` of 26.** Rather than fork the
 * compositing by API level — two implementations of the thing that just proved it goes wrong when there are two —
 * the bake does the blend itself, at every API, from the same formulas the platform implements. What that costs is a
 * pass over the icon *only when a layer asks for a blend at all*: a normal layer, which is every layer of every
 * unedited icon, still goes onto the canvas in one call.
 *
 * The formulas are the W3C compositing model, which is what both `android.graphics.BlendMode` and Compose's
 * `BlendMode` implement — so agreeing with it is what makes the two paths agree with each other.
 *
 * Pure Kotlin, so the arithmetic is unit-testable without an emulator.
 */
object LayerComposite {

    /**
     * [src] laid onto [dst] through [mode] at [opacity] — both **non-premultiplied** ARGB, as `Bitmap.getPixels`
     * hands them over and `setPixels` takes them back.
     *
     * The general form, so that every mode falls out of one expression:
     * `Co = αs·(1−αb)·Cs + αs·αb·B(Cb, Cs) + (1−αs)·αb·Cb`, over an output alpha of `αs + αb·(1−αs)`.
     *
     * Two properties of that are the whole point, and both are what the Porter-Duff route got wrong:
     * - **A transparent source leaves the destination exactly as it was.** Every term carrying `Cs` is scaled by
     *   `αs`, and the output alpha reduces to `αb`.
     * - **A transparent destination leaves the source standing**, rather than the layer disappearing for having
     *   nothing to blend against — which is what a blend mode on the bottom layer of a stack needs.
     */
    fun blend(dst: Int, src: Int, mode: LayerBlend, opacity: Float): Int {
        val alphaS = ((src ushr 24) and 0xFF) / 255f * opacity.coerceIn(0f, 1f)
        // Nothing to lay on. Returned early rather than computed, since it is the common case inside any layer's
        // own transparent surround and the arithmetic below would only rediscover it.
        if (alphaS <= 0f) return dst

        val alphaB = ((dst ushr 24) and 0xFF) / 255f
        val alphaO = alphaS + alphaB * (1f - alphaS)
        if (alphaO <= 0f) return 0

        fun channel(shift: Int): Int {
            val cs = ((src shr shift) and 0xFF) / 255f
            val cb = ((dst shr shift) and 0xFF) / 255f
            val blended = mixed(cb, cs, mode)
            val co = alphaS * (1f - alphaB) * cs +
                alphaS * alphaB * blended +
                (1f - alphaS) * alphaB * cb
            // Un-premultiplied on the way out, which is the form the pixel arrays are in.
            return ((co / alphaO) * 255f).roundToInt().coerceIn(0, 255)
        }

        return ((alphaO * 255f).roundToInt().coerceIn(0, 255) shl 24) or
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * `B(Cb, Cs)` — what one channel of the backdrop and the source make of each other, before either alpha is
     * considered. The separable blend functions, in the W3C's own terms.
     */
    private fun mixed(backdrop: Float, source: Float, mode: LayerBlend): Float = when (mode) {
        // Which reduces the expression above to plain source-over, so `NORMAL` needs no special case anywhere.
        LayerBlend.NORMAL -> source
        LayerBlend.MULTIPLY -> backdrop * source
        LayerBlend.SCREEN -> backdrop + source - backdrop * source
        // Hard-light with the operands swapped, which is what overlay is defined as.
        LayerBlend.OVERLAY ->
            if (backdrop <= 0.5f) 2f * backdrop * source
            else 1f - 2f * (1f - backdrop) * (1f - source)

        LayerBlend.DARKEN -> minOf(backdrop, source)
        LayerBlend.LIGHTEN -> maxOf(backdrop, source)
    }
}
