package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.TintMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a layer's [LayerEffect.Color] into the 4×5 color matrix that applies it.
 *
 * **One derivation for two renderers**, [LayerTransform]'s reason exactly: the baked path and the live editor use
 * different graphics APIs, and an icon that is tinted one way while being edited and another on the home screen is
 * a bug the editor cannot show you. This is the arithmetic, once.
 *
 * It happens to be free to share, because both APIs take the *same shape*: `android.graphics.ColorMatrix` and
 * `androidx.compose.ui.graphics.ColorMatrix` are each a row-major `FloatArray(20)`. So neither side converts
 * anything — they wrap the identical array.
 *
 * Pure Kotlin, so the matrices are unit-testable without an emulator.
 */
object LayerFilter {

    /** Rec. 709 luminance weights — the ones Android's own `ColorMatrix.setSaturation` uses. */
    private const val LumR = 0.213f
    private const val LumG = 0.715f
    private const val LumB = 0.072f

    /**
     * The color matrix for [color], or `null` when it would change nothing.
     *
     * Applied in a fixed order — **hue, then saturation, then brightness, then tint** — which is the order that
     * makes each control mean what its name says. Hue first because rotating a color wheel is meaningless once
     * saturation has flattened it to grey; tint last because it is the one that decides the final cast, and
     * anything after it would undo that.
     */
    fun colorMatrixOf(color: LayerEffect.Color?): FloatArray? {
        if (color == null || color.isIdentity) return null

        var matrix = identity()
        if (color.hueDegrees != 0f) matrix = matrix.then(hueMatrix(color.hueDegrees))
        if (color.saturation != 1f) matrix = matrix.then(saturationMatrix(color.saturation))
        if (color.brightness != 1f) matrix = matrix.then(scaleMatrix(color.brightness, color.brightness, color.brightness))
        color.tintArgb?.let { tint ->
            val r = tint shr 16 and 0xFF
            val g = tint shr 8 and 0xFF
            val b = tint and 0xFF
            matrix = matrix.then(
                when (color.tintMode) {
                    TintMode.MULTIPLY -> scaleMatrix(r = r / 255f, g = g / 255f, b = b / 255f)
                    TintMode.SOLID -> solidMatrix(r = r.toFloat(), g = g.toFloat(), b = b.toFloat())
                },
            )
        }
        return matrix
    }

    private fun identity() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * This matrix applied first, then [next] — the composition order the names in [colorMatrixOf] read in.
     *
     * The fifth column is a translation rather than a coefficient, so it accumulates [next]'s own offset instead of
     * multiplying through. Getting that backwards is silent: colors come out subtly shifted rather than wrong.
     */
    private fun FloatArray.then(next: FloatArray): FloatArray {
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

    /**
     * Replaces the color with a constant and keeps the alpha — a flat silhouette of whatever it is applied to. This
     * is `SRC_IN` tinting expressed as a matrix, so it stays one shared `FloatArray` for both renderers rather than
     * a second kind of filter each would have to build its own way.
     *
     * **The channels are 0..255 here, not 0..1, and getting that wrong is silent.** The fifth column is a
     * *translation*, and both graphics APIs read this array on Android's 0..255 convention
     * (`android.graphics.ColorMatrixColorFilter`, which Compose's `ColorFilter.colorMatrix` hands straight through).
     * Every other matrix in this file leaves that column at zero, which is why the question has not arisen before —
     * a 0..1 value here would come out as very nearly black instead of the color asked for.
     *
     * Zeroing the color coefficients is also what makes this *spend* whatever composed before it: hue, saturation and
     * brightness all act on inputs this then ignores. See [TintMode.SOLID].
     */
    private fun solidMatrix(r: Float, g: Float, b: Float) = floatArrayOf(
        0f, 0f, 0f, 0f, r,
        0f, 0f, 0f, 0f, g,
        0f, 0f, 0f, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Per-channel multipliers — brightness (all three equal) and tint (the tint's own channels) are both this. */
    private fun scaleMatrix(r: Float, g: Float, b: Float) = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Luminance-preserving saturation: 0 is greyscale, 1 unchanged, above 1 oversaturated. */
    private fun saturationMatrix(saturation: Float): FloatArray {
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
    private fun hueMatrix(degrees: Float): FloatArray {
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
}
