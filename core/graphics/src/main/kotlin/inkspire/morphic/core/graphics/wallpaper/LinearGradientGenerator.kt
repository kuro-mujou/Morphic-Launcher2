package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt

/**
 * A gradient climbing the frame through the palette's stops — the studio's simplest real design and W0's proof that
 * the whole pipeline (recipe → generator → bitmap) is wired, without any of the gart engine.
 *
 * **It ignores [seed], and that is honest rather than lazy.** A gradient through a fixed palette has no variation to
 * seed; a generator reads only the inputs its look depends on. The interface still hands it over because *other*
 * generators need it, and a total interface beats one that grows a parameter list per design.
 *
 * **[DesignParams.rotation] is the one knob it does read, and it had none at all until the quality pass.** A ramp that
 * can only run top to bottom is not the simplest version of this design, it is one arbitrary direction out of every
 * one it could take — and a diagonal gradient is the shape most wallpapers of this kind actually have. `0` is the
 * top-to-bottom ramp it has always drawn, so no stored recipe moves, and the knob sweeps a half turn from there. The
 * axis is [frameAxis], shared with the other designs built on an angle, so a 30 degree gradient and a 30 degree band
 * run at the same angle on the screen.
 *
 * **Half a turn reaches every axis and three of its directions are still out of reach, which is a knowing limit.** A
 * ramp is *directed*: light-at-the-top-right and light-at-the-bottom-left are the same axis and two different
 * pictures, so covering them all takes a full turn — and a full turn makes the knob's top the same picture as its
 * bottom, which `GeneratorKnobTest` reads as a knob that does nothing and fails on. Both readings are right; what is
 * missing is a way to say "this angle's period is the whole circle" that the guard understands. Until then the axis
 * moves and one direction of each pair is reachable, which is strictly more than the one direction it had.
 *
 * **The color math is [colorAt], pulled out and tested.** Interpolating packed ARGB across a run of stops is the part
 * that is silently wrong when it is wrong — a transposed channel tints the whole wallpaper, an off-by-one at a stop
 * boundary bands it — and it is `IntArray` arithmetic that needs no bitmap, so it lives where it can be checked
 * without an emulator, the same split the icon renderer keeps.
 */
object LinearGradientGenerator : Generator {

    // An angle and nothing else: a gradient through a fixed palette has nothing to count and nothing to disturb, and
    // saying so is what keeps the panel honest.
    override val style = DesignStyle(rotation = "Angle")

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val axis = frameAxis(degreesFor(params.rotation), width, height)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = colorAt(axis.at(x.toFloat(), y.toFloat()), palette)
            }
        }
        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Which way the ramp climbs at [rotation], in degrees clockwise from the horizontal — [frameAxis]' convention.
     *
     * `0` answers [StraightDown]: the top-to-bottom ramp this design has always drawn, and what keeps every stored
     * recipe on the picture it was saved as. A half turn from there — see the class note for what that leaves out and
     * why it is not a full one.
     */
    internal fun degreesFor(rotation: Float): Float = StraightDown + rotation.coerceIn(0f, 1f) * HalfTurn

    /**
     * The color [fraction] of the way down the gradient, `0` at the first stop and `1` at the last, interpolated in
     * sRGB between the two stops it falls between.
     *
     * **sRGB, not OKLab, for now.** A perceptual ramp (the `Oklab` work the tritone effect added) is the eventual
     * home for this — a two-stop sRGB blend dips through a muddy midpoint — but reaching it means sharing `Oklab`
     * across `core:icon` and here, which is a relocation this skeleton does not need. Left plain, and marked.
     */
    internal fun colorAt(fraction: Float, palette: Palette): Int {
        val f = fraction.coerceIn(0f, 1f)
        if (palette.size <= 1) return palette.colorAt(0)

        // Where along the run of stops the fraction lands, and the two stops bracketing it.
        val scaled = f * (palette.size - 1)
        val lower = scaled.toInt().coerceIn(0, palette.size - 1)
        val upper = (lower + 1).coerceAtMost(palette.size - 1)
        return lerpArgb(palette.colorAt(lower), palette.colorAt(upper), scaled - lower)
    }

    /**
     * [fraction] of the way through the palette as a **loop** — the last stop rejoined to the first — so a value
     * rolling past the end bands back to the start seamlessly instead of hitting a hard edge at the final stop. The
     * looped sibling of [colorAt], which stops at the ends; shared by [PlasmaGenerator] and [RingsGenerator], whose
     * fields both wrap.
     */
    internal fun colorLooping(fraction: Float, palette: Palette): Int {
        if (palette.size <= 1) return palette.colorAt(0)
        // Map 0..1 across size stops that wrap: the segment after the last stop returns to the first.
        val scaled = fraction.coerceIn(0f, 1f) * palette.size
        val lower = scaled.toInt() % palette.size
        val upper = (lower + 1) % palette.size
        return lerpArgb(palette.colorAt(lower), palette.colorAt(upper), scaled - scaled.toInt())
    }

    /**
     * [from] blended [t] of the way to [to], every ARGB channel — alpha included, since a palette stop may be
     * translucent. Shared with [colorLooping] and the generators that loop the ramp: interpolating packed ARGB is the
     * arithmetic that tints a whole wallpaper when a channel is transposed, so every ramp blends through this one
     * function rather than each risking it.
     */
    internal fun lerpArgb(from: Int, to: Int, t: Float): Int {
        val a = lerpChannel(from ushr 24 and 0xFF, to ushr 24 and 0xFF, t)
        val r = lerpChannel(from shr 16 and 0xFF, to shr 16 and 0xFF, t)
        val g = lerpChannel(from shr 8 and 0xFF, to shr 8 and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        val packed = (a shl 24) or (r shl 16) or (g shl 8) or b
        return packed
    }

    /** One channel byte, [from] moved [t] toward [to], clamped. */
    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, ChannelMax)

    private const val ChannelMax = 255

    /** The angle of the ramp this design has always drawn, and the sweep the knob adds to it. */
    private const val StraightDown = 90f
    private const val HalfTurn = 180f
}
