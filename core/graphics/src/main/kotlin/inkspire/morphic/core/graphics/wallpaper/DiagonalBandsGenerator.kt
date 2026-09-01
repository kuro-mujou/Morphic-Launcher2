package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A slab of parallel bands lying across a calm ground — *Diagonal Bands*, the most restrained design in the catalog.
 *
 * **The bands do not fill the frame, and that is the whole design.** This read as a full-bleed stripe pattern for a
 * long time — every pixel a saturated band, the palette cycling edge to edge — which is the loud thing the teardown's
 * first aesthetic principle names. The reference puts a *slab* of bands across a large expanse of ground, and its
 * *Coverage* knob is how much of the frame that slab takes: wound down it is a slender ribbon of color on bare ground,
 * and only at the top of its travel is it the full-bleed pattern this used to be permanently stuck at. Measured on
 * theirs at Coverage 50: the slab's extent across the band axis is `0.49` of the frame's own extent along it.
 *
 * **The ground is stop 0 and the bands cycle the tones above it** ([RampTones]) — [ConfettiGenerator]'s finding and
 * [DotGridGenerator]'s arrangement, confirmed here by scanning theirs: five bands over a ground that is a *sixth*
 * color, none of the bands ever taking it. This used to cycle the whole palette including stop 0, so there was no
 * ground to be had even if the coverage had allowed one.
 *
 * **[DesignParams.variant] is their *Rotation*, sampled.** Theirs is continuous over `-180..180°`, opening on a
 * shallow `20°`; ours offers six angles across the half-turn a band direction actually spans, and opens on the same
 * shallow one. A continuous rotation wants an *orientation* field on [DesignParams] — the family is in the teardown's
 * inventory with nothing to live in — and that is deliberately **not** added here: it would be shaped by one design,
 * where their *Rotation / Direction / Delta rotation* covers six, three of which spend `variant` on a direction
 * today. It is worth a slice that moves them all at once.
 *
 * **Their *Spacing* is fixed here at their own default**, which is `0` — bands that touch. Wound up it opens a gap of
 * ground within each band's pitch (a hairline at `7`, and at `100` no bands at all), and it is a real look; it has
 * nowhere to sit while [DesignParams.scale] carries the coverage, which is the more valuable of the two by a distance.
 * **Their *Offset* is not ported** either: a four-arrow nudge that walks the slab off center, wanting a two-axis
 * control neither the model nor the panel has. It is the second design to want one, after Dot Grid.
 *
 * [DesignParams.irregularity] is their *Variation* — perfectly even bands at `0`, a hand-torn set at `1`. The
 * variable-width banding is [Bands], shared with the columns. Deterministic in [seed].
 */
object DiagonalBandsGenerator : Generator {

    /** What [DesignParams.density] resolves to — the band count, and the slider's own range. Theirs exactly. */
    private val Amount = AmountKnob.Count("Bands", 2..30)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Coverage",
        irregularity = "Variation",
        variant = VariantKnob("Angle", Angle.entries.map { it.label }),
    )

    /**
     * Which way the bands run — their *Rotation*, at the stops a segmented control can offer.
     *
     * A band direction spans only a **half** turn (a set of parallel lines at `θ` and at `θ + 180°` is the same set),
     * so these sweep the whole space rather than half of it, symmetrically about the upright: the two past it are the
     * mirrors of the two before, which is what a reversed diagonal is.
     *
     * **In ascending order, and the shallow one is first because it has to be both.** The panel reads a segmented
     * control as one axis, which wants them sorted; the model's contract is that `variant = 0` is the design's default
     * look, and theirs opens shallow. The two agree only if the sweep *starts* there — which is why a flat `0°` is not
     * on the list. Little is lost: `20°` is nearly flat, and the flat-band look is [WaveDividersGenerator]'s at rest.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property degrees the angle the band boundaries run at, measured from the horizontal.
     */
    internal enum class Angle(val label: String, val degrees: Float) {
        /** The shallow slope theirs opens on, and so the one at index `0`. */
        SHALLOW("20°", degrees = 20f),

        /** The true diagonal, which is what this design used to be locked to. */
        DIAGONAL("45°", degrees = 45f),

        /** Upright bands marching across the frame. */
        UPRIGHT("90°", degrees = 90f),

        /** The diagonal mirrored. */
        REVERSE("135°", degrees = 135f),

        /** The shallow slope mirrored. */
        REVERSE_SHALLOW("160°", degrees = 160f),
    }

    /**
     * The band axis for one frame and one angle: where a pixel falls across the bands, `0..1` corner to corner.
     *
     * **One derivation, built once and read per pixel, rather than the render and the test each computing it.** That
     * spanning-exactly-`0..1` property is what the coverage knob rests on, and it is the kind of thing a test can
     * confirm about its own copy of the arithmetic while the render quietly uses another.
     *
     * Projected in **pixels**, not in the unit square: a `20°` band has to draw at `20°` on the screen, and in the
     * unit square it would draw at whatever the frame's aspect turns `20°` into — near-flat on a phone.
     */
    internal class Axis(private val nx: Float, private val ny: Float, private val lowest: Float, private val span: Float) {

        /**
         * Where ([x], [y]) falls across the bands, `0` at the first corner the axis meets and `1` at the last.
         *
         * A frame with no extent along this axis — a one-pixel strip — answers [Centre], so a degenerate size draws
         * the middle band rather than dividing by nothing.
         */
        fun at(x: Float, y: Float): Float =
            if (span <= 0f) Centre else ((nx * x + ny * y - lowest) / span).coerceIn(0f, 1f)
    }

    /** The [Axis] for [angle] over a `[width] × [height]` frame. */
    internal fun axisOf(angle: Angle, width: Int, height: Int): Axis {
        val radians = Math.toRadians(angle.degrees.toDouble())
        val nx = -sin(radians).toFloat()
        val ny = cos(radians).toFloat()
        val right = (width - 1).toFloat()
        val bottom = (height - 1).toFloat()
        // The axis runs corner to corner: its lowest reading is whichever corner the normal points away from.
        return Axis(nx, ny, min(0f, nx * right) + min(0f, ny * bottom), abs(nx) * right + abs(ny) * bottom)
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val angle = Angle.entries[params.variant.coerceIn(0, Angle.entries.lastIndex)]
        val count = bandCount(params.density)
        val boundaries = Bands.boundaries(count, params.irregularity, seed)
        val coverage = coverage(params.scale)
        val tones = RampTones.aboveGround(palette)
        val ground = palette.colorAt(0)

        val bitmap = createBitmap(width, height)
        if (tones.isEmpty()) {
            // An all-ground palette has nothing to lay across it, which is the honest picture rather than an error.
            bitmap.eraseColor(ground)
            return bitmap
        }

        val axis = axisOf(angle, width, height)
        val start = (1f - coverage) / 2f
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            val fy = y.toFloat()
            for (x in 0 until width) {
                val within = (axis.at(x.toFloat(), fy) - start) / coverage
                pixels[row + x] = if (within < 0f || within > 1f) {
                    ground
                } else {
                    tones[Bands.bandAt(within, boundaries) % tones.size]
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [density] asks for — a couple of bold stripes up to a fine set. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * How much of the frame the slab of bands covers, across the band axis, at [scale].
     *
     * **Floored at [MinCoverage] rather than reaching `0`**, which is theirs too — its own slider bottoms out at a
     * tenth. A knob whose lower end renders an empty frame is a knob with a broken half, and the same rule already
     * caps Dot Grid's margin short of vanishing.
     */
    internal fun coverage(scale: Float): Float = MinCoverage + scale.coerceIn(0f, 1f) * (1f - MinCoverage)

    /** The least of the frame the slab may cover, so the knob's bottom end is a ribbon rather than an empty frame. */
    private const val MinCoverage = 0.1f

    /** The middle of the band axis — what a frame with no extent along it reads as. */
    private const val Centre = 0.5f
}
