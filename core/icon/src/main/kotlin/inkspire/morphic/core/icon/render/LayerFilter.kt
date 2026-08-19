package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.render.ColorMatrices.then
import inkspire.morphic.core.icon.render.ColorMatrices.towards
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.TintMode

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
 * **What is left here is the *policy*, not the arithmetic.** The builders moved to [ColorMatrices] when the filter
 * library arrived and wanted the same ones; this file keeps the single thing that is about the four sliders — the
 * order they compose in.
 *
 * Pure Kotlin, so the matrices are unit-testable without an emulator.
 */
object LayerFilter {

    /**
     * The color matrix for [color], or `null` when it would change nothing.
     *
     * Applied in a fixed order — **hue, then saturation, then brightness, then tint** — which is the order that
     * makes each control mean what its name says. Hue first because rotating a color wheel is meaningless once
     * saturation has flattened it to gray; tint last because it is the one that decides the final cast, and
     * anything after it would undo that.
     */
    fun colorMatrixOf(color: LayerEffect.Color?): FloatArray? {
        if (color == null || color.isIdentity) return null

        var matrix = ColorMatrices.identity()
        if (color.hueDegrees != 0f) matrix = matrix.then(ColorMatrices.hue(color.hueDegrees))
        if (color.saturation != 1f) matrix = matrix.then(ColorMatrices.saturation(color.saturation))
        if (color.brightness != 1f) {
            matrix = matrix.then(ColorMatrices.scale(color.brightness, color.brightness, color.brightness))
        }
        color.tintArgb?.let { tint ->
            matrix = matrix.then(
                when (color.tintMode) {
                    TintMode.MULTIPLY -> ColorMatrices.scale(
                        r = (tint shr 16 and 0xFF) / 255f,
                        g = (tint shr 8 and 0xFF) / 255f,
                        b = (tint and 0xFF) / 255f,
                    )

                    TintMode.SOLID -> solidMatrixOf(tint)
                },
            )
        }
        return matrix
    }

    /**
     * The matrix that turns whatever it is applied to into a flat silhouette of [argb], keeping only its alpha.
     *
     * Second consumer of [ColorMatrices.solid] and the reason it is worth a name here: an extrusion is the layer's
     * silhouette in one color, which is the same operation a [TintMode.SOLID] tint performs — so both go through
     * one place rather than each pulling the channels out of an int its own way. **The channels are 0..255**, being
     * the matrix's fifth column, and that is exactly the thing that would be silently wrong if it were written twice.
     */
    fun solidMatrixOf(argb: Int): FloatArray = ColorMatrices.solid(
        r = (argb shr 16 and 0xFF).toFloat(),
        g = (argb shr 8 and 0xFF).toFloat(),
        b = (argb and 0xFF).toFloat(),
    )

    /**
     * The matrix mapping a whole tonal range onto the ramp from [darkArgb] to [lightArgb].
     *
     * **Here rather than in `IconFilters` because there are two consumers now**, which is what that file's own KDoc
     * reserved: it authors a table of two-color looks, and [LayerEffect.Duotone] lets a user pick the same two, so
     * both were about to unpack the channels out of an int their own way. That is [solidMatrixOf]'s argument exactly,
     * and it protects the same thing — the channels are the matrix's **fifth column**, on 0..255, where a 0..1 value
     * is visually black rather than obviously broken.
     *
     * The alpha byte of each color is ignored: a ramp has no opacity of its own, and the layer's own alpha survives
     * untouched.
     */
    fun duotoneMatrixOf(darkArgb: Int, lightArgb: Int): FloatArray = ColorMatrices.duotone(
        darkR = (darkArgb shr 16 and 0xFF).toFloat(),
        darkG = (darkArgb shr 8 and 0xFF).toFloat(),
        darkB = (darkArgb and 0xFF).toFloat(),
        lightR = (lightArgb shr 16 and 0xFF).toFloat(),
        lightG = (lightArgb shr 8 and 0xFF).toFloat(),
        lightB = (lightArgb and 0xFF).toFloat(),
    )

    /**
     * The matrix for [duotone] — its full ramp, taken [LayerEffect.Duotone.strength] of the way from leaving the
     * layer alone.
     *
     * **The partial grade is an interpolation of the *matrix*, not of two drawn copies** — see
     * [ColorMatrices.towards] for why those are the same picture. It is what keeps a strength slider from costing
     * this effect its live path.
     */
    fun duotoneMatrixOf(duotone: LayerEffect.Duotone): FloatArray =
        ColorMatrices.identity()
            .towards(duotoneMatrixOf(duotone.darkArgb, duotone.lightArgb), duotone.strength)
}
