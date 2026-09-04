package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Concentric rings of palette color rippling out from an off-center point — the *Echoes / Rings* op-art (gart's
 * `arts/sun` echoes, `arts/spiral`).
 *
 * **Distance banded through the looped palette — the radial sibling of [PlasmaGenerator].** Every pixel is colored by
 * its distance from a center, snapped into rings and read off the palette as a **loop** (last stop rejoined to the
 * first), so the bands ripple outward without a seam where the ramp turns over. There is no geometry and no overdraw —
 * just distance — which is what makes it a cheap full-screen pixel pass.
 *
 * **The center is seeded and set off-center**, because rings centered in the frame read as a target while rings from a
 * corner or an edge read as a rising sun or a sonar echo — far the stronger wallpaper. [DesignParams.density] sets the
 * ring frequency — a few broad haloes or a tight ripple. Deterministic in [seed].
 *
 * [ringFraction] is pure and tested: mapping a distance to a looped position is the arithmetic that decides whether the
 * rings even close, and it needs no bitmap.
 */
object RingsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Rings* slider's own range. */
    private val Amount = AmountKnob.Count("Rings", 4..18)

    override val style = DesignStyle(amount = Amount)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        // The center, off-center within an inset so the rings always sweep across most of the frame.
        val cx = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val cy = CenterInset + random.nextFloat() * (1f - 2f * CenterInset)
        val rings = ringCount(params.density)
        // A ring has to be round on the screen rather than in the unit square — see [ringFraction].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width
        val perUnit = ringsPerUnit(rings, heightOverWidth)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                pixels[y * width + x] = LinearGradientGenerator.colorLooping(
                    ringFraction(nx, ny, cx, cy, heightOverWidth, perUnit),
                    palette,
                )
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many rings [density] asks for across the frame — broad haloes up to a tight ripple. */
    internal fun ringCount(density: Float): Int = Amount.at(density)

    /**
     * Where the pixel at ([nx], [ny]) falls in the ring cycle, `0..1` — its distance from ([cx], [cy]) scaled by [rings]
     * and taken **mod 1**, so each unit of distance is one full pass through the palette and the next ring starts over.
     *
     * **The distance is measured on the screen, not in the unit square, and that is the whole of the design's name.**
     * Both coordinates arrive as shares of their own side, so a `hypot` of the two is a distance in a space stretched
     * by the frame's proportions — on a 1080×2400 phone it drew *ellipses* two and a bit times taller than they are
     * wide, at every setting, for as long as the design has existed. [heightOverWidth] scales the vertical share back
     * into the horizontal one, which makes the metric isotropic, and [perUnit] carries the ring pitch — see
     * [ringsPerUnit]. Nothing reports it, because a field of concentric ellipses is still a plausible ripple.
     */
    internal fun ringFraction(
        nx: Float,
        ny: Float,
        cx: Float,
        cy: Float,
        heightOverWidth: Float,
        perUnit: Float,
    ): Float {
        val distance = hypot(nx - cx, (ny - cy) * heightOverWidth) * perUnit
        return distance - distance.toInt()
    }

    /**
     * How many ring cycles one unit of the measured distance carries, for [rings] rings on a frame of this
     * [heightOverWidth] — the scale [ringFraction] multiplies by.
     *
     * **One derived number rather than the count and the frame's diagonal side by side**, which the first cut passed
     * as two parameters: a diagonal that disagreed with the aspect it was supposed to come from would draw a ring
     * pitch quietly off, and there is nothing in a ripple to notice that against. Dividing by the diagonal is what
     * keeps [rings] meaning "rings out to the far corner" whatever shape the frame is; hoisting it out of the pixel
     * loop is why it is here rather than inline.
     */
    internal fun ringsPerUnit(rings: Int, heightOverWidth: Float): Float = rings / hypot(1f, heightOverWidth)

    // Softened toward broad haloes: the default density now opens on a few calm rings rather than a tight ripple (W7).

    /** How far off the edge the center is kept, so the rings sweep across the frame rather than sitting in a corner. */
    private const val CenterInset = 0.15f
}
