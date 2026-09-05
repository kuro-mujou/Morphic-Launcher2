package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.atan2
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
 * **The center is [OffFrameOrigin], and it is never on screen — which is the design, not a detail of it.** This used
 * to place it inside the frame behind an inset, and every render was therefore a **bullseye**: a singularity sitting
 * among the icons with rings closing around it at full palette contrast. That is op-art, and it is the reason this
 * design was not usable as a wallpaper. From outside the frame the same arithmetic draws *arcs sweeping across the
 * screen* with no point of convergence anywhere in view — which is the rising sun the old KDoc claimed and the inset
 * made impossible. [DesignParams.scale] is how far out, from arcs curving hard across the frame to lazy ones.
 *
 * **Both of the design's counts are read from that origin rather than from the frame**, because from outside it the
 * frame covers only a slice of the distance and a slice of the turn — see [OffFrameOrigin]. The ring pitch divides
 * [OffFrameOrigin.distanceSpan], so *Rings* `10` still draws ten visible bands however far out the origin is placed;
 * the wobble's harmonics are read over [OffFrameOrigin.sectorTurns], so its lobes still bend the arcs rather than
 * shifting by a twentieth of a cycle across a narrow fan and reading as nothing at all.
 *
 * [DesignParams.density] sets the ring frequency — a few broad haloes or a tight ripple. Deterministic in [seed].
 *
 * [ringFraction] is pure and tested: mapping a distance to a looped position is the arithmetic that decides whether the
 * rings even close, and it needs no bitmap.
 */
object RingsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Rings* slider's own range. */
    private val Amount = AmountKnob.Count("Rings", 4..18)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Distance",
        irregularity = "Wobble",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        // A ring has to be round on the screen rather than in the unit square — see [ringFraction].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width
        val origin = offFrameOrigin(random, params.scale, heightOverWidth)
        val rings = ringCount(params.density)
        // Drawn after the origin, so tuning the wobble cannot move where the arcs sweep from.
        val harmonics = SeededHarmonics(WobbleWeights, WobbleHarmonics, random)
        val amplitude = params.irregularity.coerceIn(0f, 1f) * MaxWobble
        val perUnit = ringsPerUnit(rings, origin.distanceSpan)
        // The harmonics run over the visible fan rather than over the turn — see the class note.
        val lobeScale = 1f / origin.sectorTurns

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                // The bearing is taken in the same screen metric the distance is, or the lobes would be lopsided
                // exactly where the rings used to be.
                val bearing = atan2((ny - origin.y) * heightOverWidth, nx - origin.x)
                val wobble = 1f + amplitude * harmonics.at(bearing * lobeScale)
                pixels[y * width + x] = LinearGradientGenerator.colorLooping(
                    ringFraction(nx, ny, origin.x, origin.y, heightOverWidth, perUnit, wobble),
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
     *
     * **[wobble] scales the measured distance, which is what keeps the rings from crossing.** A factor on the radius
     * is a monotonic stretch along each bearing, so however far the lobes swing, ring `n` stays inside ring `n + 1` —
     * where displacing each ring's radius by its own amount would let two of them meet and the ripple would tear. `1`
     * is a perfect circle.
     */
    internal fun ringFraction(
        nx: Float,
        ny: Float,
        cx: Float,
        cy: Float,
        heightOverWidth: Float,
        perUnit: Float,
        wobble: Float = 1f,
    ): Float {
        val distance = hypot(nx - cx, (ny - cy) * heightOverWidth) * perUnit * wobble
        return distance - distance.toInt()
    }

    /**
     * How many ring cycles one unit of the measured distance carries, for [rings] rings over a frame that spans
     * [distanceSpan] of distance seen from the origin — the scale [ringFraction] multiplies by.
     *
     * **The span is the origin's, not the frame's diagonal, and that is what keeps the count honest.** With the origin
     * on screen the diagonal was the right extent, because the near end of the ripple was the center itself; with it
     * outside, the frame occupies a *slice* of the distance that narrows as the origin moves away — so dividing by the
     * diagonal would draw ten rings' worth of pitch and show four of them, and the *Rings* slider would read a number
     * nothing on screen matches. There is nothing in a ripple to notice that against, which is why it is derived here
     * rather than assumed. Hoisting it out of the pixel loop is why it is a function at all.
     */
    internal fun ringsPerUnit(rings: Int, distanceSpan: Float): Float = rings / distanceSpan

    // Softened toward broad haloes: the default density now opens on a few calm rings rather than a tight ripple (W7).

    /**
     * How far the radius may swing at [DesignParams.irregularity] `1`, as a share of itself.
     *
     * Well under `1`, so the factor can never reach zero and turn a ring inside out; at this ceiling the rings read as
     * hand-drawn echoes rather than as a target, which is the whole point of giving a radial design an organic axis.
     */
    private const val MaxWobble = 0.22f

    /** Which harmonics lobe the rings and how much each contributes — low ones, so a ring bends rather than crinkles. */
    private val WobbleHarmonics = floatArrayOf(2f, 3f, 5f)
    private val WobbleWeights = floatArrayOf(0.55f, 0.28f, 0.17f)
}
