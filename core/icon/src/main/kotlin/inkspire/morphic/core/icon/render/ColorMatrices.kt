package inkspire.morphic.core.icon.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * The 4×5 colour-matrix arithmetic, and nothing about what it is used *for*.
 *
 * Split out of [LayerFilter] when the filter library arrived, because the two want the same builders for different
 * reasons: `LayerFilter` composes a fixed sequence the four recolouring sliders mean, `IconFilters` composes a
 * table of authored looks. One copy of the multiply is the whole point — a second would be a second chance to get
 * the fifth column wrong, and that column is silent when it is.
 *
 * **The fifth column is a translation on a 0..255 scale**, which is Android's convention and Compose's too, since
 * `ColorFilter.colorMatrix` hands the array straight to `android.graphics.ColorMatrixColorFilter`. A 0..1 value
 * there comes out very nearly black rather than the colour asked for. [offset], [contrast] and [invert] are the
 * builders that use it; everything else leaves it at zero.
 *
 * Pure Kotlin, so every matrix here is unit-testable without an emulator.
 */
internal object ColorMatrices {

    /** Rec. 709 luminance weights — the ones Android's own `ColorMatrix.setSaturation` uses. */
    const val LumR = 0.213f
    const val LumG = 0.715f
    const val LumB = 0.072f

    fun identity() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * This matrix applied first, then [next] — so a chain reads in the order the effects happen.
     *
     * The fifth column is a translation rather than a coefficient, so it accumulates [next]'s own offset instead of
     * multiplying through. Getting that backwards is silent: colours come out subtly shifted rather than wrong.
     */
    fun FloatArray.then(next: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0..3) {
            for (column in 0..4) {
                var sum = 0f
                for (k in 0..3) sum += next[row * 5 + k] * this[k * 5 + column]
                if (column == 4) sum += next[row * 5 + 4]
                out[row * 5 + column] = sum
            }
        }
        return out
    }

    /** Per-channel multipliers — brightness (all three equal) and a multiply tint (the tint's channels) are both this. */
    fun scale(r: Float, g: Float, b: Float) = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Adds a constant to each channel, in 0..255. Negative darkens; this is how a look lifts or crushes blacks. */
    fun offset(r: Float, g: Float, b: Float) = floatArrayOf(
        1f, 0f, 0f, 0f, r,
        0f, 1f, 0f, 0f, g,
        0f, 0f, 1f, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Pivots each channel about mid-grey: above 1 steepens, below 1 flattens.
     *
     * The offset is what keeps the pivot at the middle rather than at black — without it a "contrast" control is a
     * brightness control that also steepens, which is the usual way this one is written wrong.
     */
    fun contrast(amount: Float): FloatArray {
        val shift = 128f * (1f - amount)
        return floatArrayOf(
            amount, 0f, 0f, 0f, shift,
            0f, amount, 0f, 0f, shift,
            0f, 0f, amount, 0f, shift,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Flips each channel about its middle, leaving alpha alone. */
    fun invert() = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Luminance-preserving saturation: 0 is grayscale, 1 unchanged, above 1 oversaturated. */
    fun saturation(saturation: Float): FloatArray {
        val inv = 1f - saturation
        val r = LumR * inv
        val g = LumG * inv
        val b = LumB * inv
        return floatArrayOf(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Rotation about the luminance axis — the standard hue matrix, kept in its published form. */
    fun hue(degrees: Float): FloatArray {
        val radians = degrees * Math.PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        return floatArrayOf(
            LumR + c * (1 - LumR) + s * (-LumR), LumG + c * (-LumG) + s * (-LumG),
            LumB + c * (-LumB) + s * (1 - LumB), 0f, 0f,
            LumR + c * (-LumR) + s * 0.143f, LumG + c * (1 - LumG) + s * 0.140f,
            LumB + c * (-LumB) + s * (-0.283f), 0f, 0f,
            LumR + c * (-LumR) + s * (-(1 - LumR)), LumG + c * (-LumG) + s * LumG,
            LumB + c * (1 - LumB) + s * LumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /**
     * Replaces the colour with a constant and keeps the alpha — a flat silhouette of whatever it is applied to.
     * `SRC_IN` tinting expressed as a matrix, so it stays one shared `FloatArray` for both renderers rather than a
     * second kind of filter each would build its own way.
     *
     * **Channels are 0..255 here**, being the fifth column. Zeroing the colour coefficients is also what makes this
     * *spend* whatever composed before it: hue, saturation and brightness all act on inputs this then ignores.
     */
    fun solid(r: Float, g: Float, b: Float) = floatArrayOf(
        0f, 0f, 0f, 0f, r,
        0f, 0f, 0f, 0f, g,
        0f, 0f, 0f, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Maps the whole tonal range onto a **two-colour ramp**: what was black comes out [darkR]/[darkG]/[darkB], what
     * was white comes out [lightR]/[lightG]/[lightB], and everything between is that line at its own luminance.
     *
     * **The third thing [scale] cannot express, after [mix] and [solid], and it is the one a whole family of looks is
     * made of** — every duotone, and the green-on-green of a handheld screen. It is not a tint: a tint attenuates the
     * colours already there, so a red icon and a blue one stay different, where this discards the hue entirely and
     * keeps only how *light* each pixel was. Which is exactly why the two ends are worth choosing.
     *
     * `out = dark + luma × (light − dark)`, per channel — so the coefficients are the luminance weights times the
     * span, and the fifth column is the dark end. **Channels are 0..255**, the fifth column's scale, and being where
     * the dark end lands that is the half which is silent when it is wrong: a 0..1 dark end is visually black, which
     * looks like a duotone that simply has dark shadows.
     */
    fun duotone(
        darkR: Float, darkG: Float, darkB: Float,
        lightR: Float, lightG: Float, lightB: Float,
    ): FloatArray {
        fun row(dark: Float, light: Float): FloatArray {
            // The span is divided by 255 and the weights are not, because a coefficient multiplies a channel on
            // 0..255 and must land on 0..255: `luma × (light − dark) / 255`. Scaling it back up — which reads as
            // symmetry with the fifth column — is the arithmetic slip the duotone test exists to catch, and it
            // produces a picture that is merely blown out rather than one that is obviously broken.
            val span = (light - dark) / 255f
            return floatArrayOf(LumR * span, LumG * span, LumB * span, 0f, dark)
        }
        return row(darkR, lightR) + row(darkG, lightG) + row(darkB, lightB) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    /**
     * Each output channel as a weighted mix of all three inputs — the shape a sepia or a channel-swapped look needs,
     * and the one thing [scale] cannot express, since it can only ever attenuate a channel by itself.
     */
    fun mix(
        rr: Float, rg: Float, rb: Float,
        gr: Float, gg: Float, gb: Float,
        br: Float, bg: Float, bb: Float,
    ) = floatArrayOf(
        rr, rg, rb, 0f, 0f,
        gr, gg, gb, 0f, 0f,
        br, bg, bb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
