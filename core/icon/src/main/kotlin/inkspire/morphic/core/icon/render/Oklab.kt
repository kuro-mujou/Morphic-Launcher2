package inkspire.morphic.core.icon.render

import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Mixing two colors the way the eye reads the blend, not the way the wire stores it — the perceptual interpolation
 * a [LayerTritone] needs and sRGB cannot give.
 *
 * **The whole reason this exists is the muddy midpoint.** Halfway between two colors in sRGB is their raw channel
 * average, which lands darker and grayer than either — a violet and a gold meet at a dead mauve. OKLab is built so
 * that a straight line between two colors passes through the shades a person would actually call "halfway", keeping
 * the lightness even and the chroma up. So a tritone's mid band reads as a real color rather than as the dip sRGB
 * leaves.
 *
 * **Ported from gart's `ColorOKLAB` as the *direct* sRGB↔OKLab transform, not its XYZ detour.** gart routes through
 * XYZ with a Bradford adaptation, which is correct and heavier than a launcher icon needs; the matrices here are
 * Björn Ottosson's own sRGB-linear→LMS→OKLab coefficients, which do the same job in one hop each way. That is the
 * "understand it, then take the shorter honest path" the rewrite asks for rather than a verbatim port.
 *
 * **OKLab, not OKLCh.** Both are perceptual; the difference is only how the *mid* colors are reached. OKLab draws a
 * straight line through `a`/`b`, OKLCh swings an arc through hue — which is more vivid between complements but can
 * take the long way round and pass through a hue neither endpoint implies. For a tritone whose colors the user
 * picked, predictable beats vivid, so the interpolation is Cartesian.
 *
 * Pure and allocation-light enough to build a 256-entry ramp per bake; the transforms never run per pixel — see
 * [LayerTritone].
 */
object Oklab {

    /**
     * The color [t] of the way from [argb0] to [argb1], interpolated in OKLab and returned as an opaque sRGB int.
     *
     * Alpha is dropped — the only caller is a luminance ramp of opaque stops, and the pixel's own alpha is applied
     * elsewhere. [t] outside `0..1` is clamped, so a ramp segment cannot read past its own ends.
     */
    fun mix(argb0: Int, argb1: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val a = toOklab(argb0)
        val b = toOklab(argb1)
        return fromOklab(
            a[0] + (b[0] - a[0]) * f,
            a[1] + (b[1] - a[1]) * f,
            a[2] + (b[2] - a[2]) * f,
        )
    }

    /** `[L, a, b]` for an sRGB [argb] — its alpha ignored. */
    private fun toOklab(argb: Int): FloatArray {
        val r = linearize((argb shr 16 and 0xFF) / ChannelMax)
        val g = linearize((argb shr 8 and 0xFF) / ChannelMax)
        val b = linearize((argb and 0xFF) / ChannelMax)

        val lms = mul(LinearRgbToLms, r, g, b)
        return mul(LmsToOklab, cbrt(lms[0]), cbrt(lms[1]), cbrt(lms[2]))
    }

    /** The opaque sRGB int for an OKLab color, each channel clamped back into `0..255`. */
    private fun fromOklab(bigL: Float, aa: Float, bb: Float): Int {
        val lms = mul(OklabToLms, bigL, aa, bb)
        val rgb = mul(LmsToLinearRgb, cube(lms[0]), cube(lms[1]), cube(lms[2]))

        val packed = (0xFF shl 24) or
            (channel(delinearize(rgb[0])) shl 16) or
            (channel(delinearize(rgb[1])) shl 8) or
            channel(delinearize(rgb[2]))
        return packed
    }

    /** `m · (x, y, z)`, a 3×3 by a 3-vector — the one shape both transforms are made of. */
    private fun mul(m: Array<FloatArray>, x: Float, y: Float, z: Float): FloatArray =
        floatArrayOf(
            m[0][0] * x + m[0][1] * y + m[0][2] * z,
            m[1][0] * x + m[1][1] * y + m[1][2] * z,
            m[2][0] * x + m[2][1] * y + m[2][2] * z,
        )

    private fun cube(x: Float): Float = x * x * x

    /** sRGB gamma → linear light, on a `0..1` channel. */
    private fun linearize(c: Float): Float =
        if (c <= GammaThreshold) c / LinearSlope else ((c + GammaOffset) / GammaSlope).pow(Gamma)

    /** Linear light → sRGB gamma, on a `0..1` channel. */
    private fun delinearize(c: Float): Float =
        if (c <= LinearThreshold) LinearSlope * c else GammaSlope * c.pow(1f / Gamma) - GammaOffset

    /** A `0..1` channel as a `0..255` byte, clamped. */
    private fun channel(c: Float): Int = (c * ChannelMax).toInt().coerceIn(0, ChannelMax.toInt())

    private const val ChannelMax = 255f

    // sRGB transfer-function constants (IEC 61966-2-1).
    private const val GammaThreshold = 0.04045f
    private const val LinearThreshold = 0.0031308f
    private const val LinearSlope = 12.92f
    private const val GammaSlope = 1.055f
    private const val GammaOffset = 0.055f
    private const val Gamma = 2.4f

    // Ottosson's direct sRGB-linear ↔ OKLab coefficients (the four 3×3 matrices of the round trip).
    private val LinearRgbToLms = arrayOf(
        floatArrayOf(0.4122214708f, 0.5363325363f, 0.0514459929f),
        floatArrayOf(0.2119034982f, 0.6806995451f, 0.1073969566f),
        floatArrayOf(0.0883024619f, 0.2817188376f, 0.6299787005f),
    )
    private val LmsToOklab = arrayOf(
        floatArrayOf(0.2104542553f, 0.7936177850f, -0.0040720468f),
        floatArrayOf(1.9779984951f, -2.4285922050f, 0.4505937099f),
        floatArrayOf(0.0259040371f, 0.7827717662f, -0.8086757660f),
    )
    private val OklabToLms = arrayOf(
        floatArrayOf(1f, 0.3963377774f, 0.2158037573f),
        floatArrayOf(1f, -0.1055613458f, -0.0638541728f),
        floatArrayOf(1f, -0.0894841775f, -1.2914855480f),
    )
    private val LmsToLinearRgb = arrayOf(
        floatArrayOf(4.0767416621f, -3.3077115913f, 0.2309699292f),
        floatArrayOf(-1.2684380046f, 2.6097574011f, -0.3413193965f),
        floatArrayOf(-0.0041960863f, -0.7034186147f, 1.7076147010f),
    )
}
