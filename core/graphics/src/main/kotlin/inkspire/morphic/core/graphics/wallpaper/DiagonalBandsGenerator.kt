package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette

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
 * variable-width banding is [Bands], shared with the columns. Deterministic in the seed.
 *
 * **The bands are drawn as shapes, not classified a pixel at a time**, so the bake and a scrub issue the same canvas
 * calls ([plan] and [draw]) and a shuffle can be scrubbed — see docs/MORPH_ENGINE_PLAN.md, whose field survey is why:
 * a per-pixel slab has hard unantialiased edges that a downscale stairs, and costs tens of milliseconds a frame.
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
     * The band axis for one frame and one angle: where a pixel falls *across* the bands, `0..1` corner to corner.
     *
     * [Angle.degrees] is the angle the band boundaries **run** at, so the axis that measures across them is its
     * normal — a quarter turn on. The projection itself is [frameAxis], shared with the strips of
     * [LouversGenerator]; what belongs to this design is only which of the two perpendiculars its angle names.
     */
    internal fun axisOf(angle: Angle, width: Int, height: Int): FrameAxis =
        frameAxis(angle.degrees + QuarterTurn, width, height)

    /**
     * A slab of bands planned but not painted — at no particular size and in no particular palette.
     *
     * **It takes no size**, because every number here is a share of the band axis, and the axis is laid across
     * whatever frame [draw] is given. A band's color is not here either: the `i`-th band takes the `i`-th tone above
     * the ground, so a shuffle never recolors one.
     *
     * @property coverage how much of the axis the slab spans, `0..1` — [coverage] of the knob.
     * @property boundaries the edges between bands, as shares of the slab, sorted — [Bands.boundaries].
     */
    internal class Plan(val angle: Angle, val coverage: Float, val boundaries: FloatArray)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(params, seed), palette, width, height)
        return bitmap
    }

    /** The slab [params] and [seed] describe. The seed moves only the band widths, and only under *Variation*. */
    internal fun plan(params: DesignParams, seed: Long): Plan = Plan(
        angle = Angle.entries[params.variant.coerceIn(0, Angle.entries.lastIndex)],
        coverage = coverage(params.scale),
        boundaries = Bands.boundaries(bandCount(params.density), params.irregularity, seed),
    )

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     *
     * **Each band is drawn from its own start to the slab's far end, and the next one covers the rest.** Two
     * antialiased fills that merely *meet* each leave a partly covered pixel along the edge, and the ground shows
     * through the pair as a hairline; drawn this way every shared edge is antialiased once, over a solid band.
     *
     * **Half a pixel in, because the axis is measured at pixel centers**: [FrameAxis] reads pixel `x` at `x`, and a
     * canvas puts that pixel's center at `x + 0.5`.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        canvas.drawColor(palette.colorAt(0))
        val tones = RampTones.aboveGround(palette)
        // An all-ground palette has nothing to lay across it, which is the honest picture rather than an error.
        if (tones.isEmpty()) return

        val axis = axisOf(plan.angle, width, height)
        val start = (1f - plan.coverage) / 2f
        val end = start + plan.coverage
        val reach = (width + height).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        canvas.save()
        canvas.translate(PixelCenter, PixelCenter)
        for (band in 0..plan.boundaries.size) {
            val from = if (band == 0) 0f else plan.boundaries[band - 1]
            paint.color = tones[band % tones.size]
            canvas.drawPath(axis.slabPath(start + plan.coverage * from, end, reach), paint)
        }
        canvas.restore()
    }

    /**
     * A scrub between two slabs: the band edges slide, and nothing else moves.
     *
     * **The simplest scrub in the catalog, and a shuffle is exactly this subtle.** The seed only sets the band widths,
     * and only while *Variation* is up; at `0` two seeds are the same picture and the scrub is a still one.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(params, from), plan(params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two slabs prepared to interpolate, edge for edge — or **null where they hold different numbers of bands**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.boundaries.size == to.boundaries.size) Morph(from, to) else null

    /** Two slabs and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /**
         * The slab [t] of the way across; the ends are the plans themselves.
         *
         * **The edges cannot cross.** Both ends are sorted, and a point between two sorted lists taken edge for edge is
         * sorted too — so no band ever turns inside out mid-scrub, and there is nothing to guard.
         */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                // A choice, and one a shuffle never changes.
                angle = if (t < 0.5f) from.angle else to.angle,
                coverage = from.coverage + (to.coverage - from.coverage) * t,
                boundaries = FloatArray(from.boundaries.size) {
                    from.boundaries[it] + (to.boundaries[it] - from.boundaries[it]) * t
                },
            )
        }
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

    /** From a band's own direction to the axis across it, in degrees. */
    private const val QuarterTurn = 90f

    /** Where a pixel's center sits within it, in canvas units — see [draw]. */
    private const val PixelCenter = 0.5f
}
