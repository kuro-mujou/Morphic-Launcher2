package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The old-school demoscene *plasma* — overlapping sine waves summed into a rippling field, read through the palette.
 *
 * **Summed sinusoids, the classic recipe, ported from gart's `arts/plasma`.** Each pixel's value is a sum of a few
 * sine terms — one along x, one along y, one along the diagonal, one radial from the center — and that scalar picks a
 * color off the palette ramp. Where the waves reinforce the value is high, where they cancel it is low, and the
 * interference is the marbled plasma. gart animates it by drifting phases per frame; a wallpaper is one still frame, so
 * the phases are drawn *once* from [seed] — which is what makes it deterministic and a shuffle a new still.
 *
 * **The value wraps through the palette rather than clamping.** Plasma's charm is the banding where the field rolls
 * past the last stop and back to the first, so [sample] returns a value taken **mod 1**, and the ramp is sampled as a
 * loop (last stop back to the first) so there is no seam at the wrap. [DesignParams.density] sets the wave frequency —
 * broad swells or a busy ripple.
 *
 * **[DesignParams.irregularity] warps the plane the waves are read on, and until the quality pass this design had no
 * organic axis at all — it was frequency and nothing else, the fewest ways to change a picture in the catalog.** Four
 * sines at one frequency interfere in a way that is perfectly regular however the phases fall, so the field always
 * repeats itself across the frame and reads as a *pattern* rather than as a fluid. The warp is [DomainWarp], the same
 * push [MetaballsGenerator] distorts its contours with, applied to the sample point before [sample] ever sees it —
 * which is why that function is untouched and still tests the summed sinusoids alone.
 *
 * **The push is measured in *wavelengths*, not in frame widths, and that is the part that would be silently wrong.**
 * The frequency knob spans a four-fold range, so a fixed frame distance is a barely-visible nudge at the broad end and
 * complete noise at the busy end — the same knob doing two different things depending on a knob beside it. A share of
 * `2π / frequency` is the same amount of *swell* wherever the frequency sits. `0` is the rigid interference this
 * design has always drawn.
 *
 * [sample] is pure and tested: the summed-sine field is arithmetic that is silently wrong (a flat or banded-wrong
 * wallpaper) with no bitmap needed to see it.
 *
 * **It plans and then paints**, so a shuffle can be scrubbed: [plan] draws the phases and the warp, and [draw] runs
 * [FieldRaster]'s loop — into the frame for the bake, into a small buffer for a scrub. See docs/MORPH_ENGINE_PLAN.md.
 */
object PlasmaGenerator : Generator {

    // A frequency rather than a count: the plasma draws no discrete things, so its amount is the one in the catalog
    // with nothing to number, and the panel shows it as a plain scale.
    override val style = DesignStyle(
        amount = AmountKnob.Fraction("Frequency"),
        irregularity = "Turbulence",
    )

    /** The phase offsets that make one plasma still distinct from another — drawn once from the seed. */
    internal data class Phases(val x: Float, val y: Float, val diagonal: Float, val radial: Float)

    /**
     * A plasma planned but not painted — at no particular size and in no particular palette.
     *
     * **Everything the seed decides is [phases] and [warp]**; the frequency and the warp's reach are knobs. So a
     * shuffle turns four phases and trades one warp field for another, and a moment of a scrub is the phases part-way
     * round and the two warps blended.
     *
     * @property frequency the waves' angular frequency, per frame *width* — [frequency] of the knob.
     * @property warp the push the plane is read through — [DomainWarp] at [warpReach].
     * @property nextWarp the warp a scrub is on its way to, on a moment of a [Morph]; null on a plasma that was planned.
     *   **The two are blended rather than one swapped for the other**, since a warp is a point pushed by a field, and a
     *   point pushed part-way between two fields' pushes moves continuously as the blend does.
     * @property warpMix how far from [warp] toward [nextWarp] the plane is pushed, `0..1`.
     */
    internal class Plan(
        val phases: Phases,
        val frequency: Float,
        val warp: DomainWarp,
        val nextWarp: DomainWarp? = null,
        val warpMix: Float = 0f,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val plan = plan(params, seed)
        // The waves have to be the same size across the frame as down it — see [sample].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width
        FieldRaster.paint(bitmap, width, height) { nx, ny -> colorAt(plan, nx, ny, heightOverWidth, palette) }
        return bitmap
    }

    /** The plasma [params] and [seed] describe. */
    internal fun plan(params: DesignParams, seed: Long): Plan {
        val frequency = frequency(params.density)
        return Plan(
            phases = phases(seed),
            frequency = frequency,
            warp = DomainWarp(seed xor WarpSeed, warpReach(params.irregularity, frequency), WarpFrequency),
        )
    }

    /**
     * Paints a scrub frame of [plan] into [canvas] at `[width]` × `[height]`, in [palette], evaluated on a buffer whose
     * short side is [shortSide] and blown up by the canvas — [FieldRaster]'s loop, the one the bake runs at full size.
     */
    internal fun draw(
        canvas: Canvas,
        plan: Plan,
        palette: Palette,
        width: Int,
        height: Int,
        shortSide: Int = min(width, height),
    ) {
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width
        FieldRaster.draw(canvas, width, height, shortSide) { nx, ny -> colorAt(plan, nx, ny, heightOverWidth, palette) }
    }

    /**
     * [plan]'s color at ([nx], [ny]) of the frame, both `0..1` — the plane warped, then the waves summed and read
     * round the palette.
     *
     * Warped in the same width-share metric the waves are read in, so the swirls are round on the screen.
     */
    private fun colorAt(plan: Plan, nx: Float, ny: Float, heightOverWidth: Float, palette: Palette): Int {
        val sy = ny * heightOverWidth
        var wx = plan.warp.x(nx, sy)
        var wy = plan.warp.y(nx, sy)
        val next = plan.nextWarp
        if (next != null && plan.warpMix > 0f) {
            wx += (next.x(nx, sy) - wx) * plan.warpMix
            wy += (next.y(nx, sy) - wy) * plan.warpMix
        }
        return LinearGradientGenerator.colorLooping(sample(wx, wy, plan.frequency, plan.phases), palette)
    }

    /**
     * A field scrub: the phases turn and the warp blends, and the field is re-evaluated on a small buffer, blown up by
     * the canvas — see [scrubShortSide] for how small.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph {
        val morph = Morph(plan(params, from), plan(params, to))
        val shortSide = scrubShortSide(frequency(params.density))
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h, shortSide) }
    }

    /** Two plasmas and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /**
         * The plasma [t] of the way across; the ends are the plans themselves.
         *
         * **A phase turns the short way**, since it is an angle — taken the long way, a wave would roll through most
         * of a cycle on its way to a partner a few degrees off, and the plasma would churn rather than drift.
         */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                phases = Phases(
                    x = lerpAngle(from.phases.x, to.phases.x, t),
                    y = lerpAngle(from.phases.y, to.phases.y, t),
                    diagonal = lerpAngle(from.phases.diagonal, to.phases.diagonal, t),
                    radial = lerpAngle(from.phases.radial, to.phases.radial, t),
                ),
                frequency = from.frequency + (to.frequency - from.frequency) * t,
                warp = from.warp,
                nextWarp = to.warp,
                warpMix = t,
            )
        }
    }

    /** The wave frequency [density] asks for — [MinFrequency] broad swells up to [MaxFrequency] a busy ripple. */
    internal fun frequency(density: Float): Float =
        MinFrequency + density.coerceIn(0f, 1f) * (MaxFrequency - MinFrequency)

    /**
     * How far the warp may push a sample point at this [irregularity], for waves at this [frequency] — a share of one
     * **wavelength**, which is `2π / frequency`.
     *
     * **A distance in wavelengths rather than in frame widths, because the frequency knob spans a four-fold range.**
     * The same push in frame units is a fifth of a swell at [MinFrequency] and most of one at [MaxFrequency], so the
     * turbulence knob would mean something different at each end of the knob beside it — a design whose two sliders
     * interact without saying so. Measured this way, half a wavelength is half a swell wherever the frequency sits.
     * `0` is the rigid interference the design drew before the knob existed.
     */
    internal fun warpReach(irregularity: Float, frequency: Float): Float =
        irregularity.coerceIn(0f, 1f) * MaxWarpWavelengths * (2f * PI.toFloat() / frequency)

    /** Four phase offsets in `0..2π` for [seed], so each seed is a different still of the same plasma. */
    internal fun phases(seed: Long): Phases {
        val random = Random(seed)
        val twoPi = (2.0 * PI).toFloat()
        return Phases(
            random.nextFloat() * twoPi,
            random.nextFloat() * twoPi,
            random.nextFloat() * twoPi,
            random.nextFloat() * twoPi,
        )
    }

    /**
     * The plasma value at ([nx], [ny]), **wrapped to `0..1`** — the sum of four sine terms scaled to a loop. The radial
     * term is measured from the center, which is what gives plasma its concentric heart rather than a purely woven
     * look.
     *
     * **Both coordinates are shares of the frame's *width*, so [ny] runs past `1` on a taller frame.** Passing the
     * plain `y` share instead spreads the same number of wave cycles over a phone's whole height, which is more than
     * twice the pixels the same count gets across it — every swell drawn two and a bit times taller than it is wide,
     * and the radial term an ellipse. It reads as a smeared plasma rather than as a wrong one, which is why it stood.
     */
    internal fun sample(nx: Float, ny: Float, frequency: Float, phases: Phases): Float {
        val radial = hypot(nx - 0.5f, ny - 0.5f)
        val sum = sin(nx * frequency + phases.x) +
            sin(ny * frequency + phases.y) +
            sin((nx + ny) * frequency * 0.5f + phases.diagonal) +
            sin(radial * frequency + phases.radial)
        // Four terms span [-4, 4]; fold to [0, 1) and let the color wrap, so the banding is the plasma rather than a clip.
        val unit = (sum / 8f) + 0.5f
        return unit - floor(unit)
    }

    // Softened toward broad swells: the default density now opens on calm marbling rather than a busy ripple (W7).
    private const val MinFrequency = 6f
    private const val MaxFrequency = 26f

    /**
     * The short side a scrub frame of this design is evaluated at, for waves at [frequency] — **measured, not guessed**
     * (2026-09-11, `FieldDownscaleHarness.measurePlasma`), and rising with the frequency where the mesh gradient's is
     * one number.
     *
     * The busier the waves, the steeper the colors run round the looped palette, and a steep run is what a coarse
     * buffer shows. Held to the same bar across the knob — no more than about `0.6%` of pixels past four levels and
     * none past twenty, at either end of the turbulence — the broadest waves need a short side of `120` and the busiest
     * `240`, with the default's `180` between. A fixed `120` would leave the busiest six percent of the frame visibly
     * soft; a fixed `240` would spend four times the pixels on the broad swells that need none of them.
     */
    internal fun scrubShortSide(frequency: Float): Int {
        val busy = ((frequency - MinFrequency) / (MaxFrequency - MinFrequency)).coerceIn(0f, 1f)
        return (ScrubAtBroadest + busy * (ScrubAtBusiest - ScrubAtBroadest)).roundToInt()
    }

    /** [scrubShortSide] at the broadest waves and at the busiest. */
    private const val ScrubAtBroadest = 120
    private const val ScrubAtBusiest = 240

    /** How far the warp pushes at full turbulence, in wavelengths — see [warpReach]. */
    private const val MaxWarpWavelengths = 0.5f

    /**
     * How many warp cells span the frame's width — a handful, so the turbulence is a swirl through the plasma rather
     * than grain on top of it. **Fixed rather than taken from the design's own frequency**, which is what
     * [MetaballsGenerator] does: tying the two would shrink the swirls exactly as the swells shrink, and a knob that
     * scales everything it could distort ends up distorting nothing.
     */
    private const val WarpFrequency = 3f

    /** Keeps the warp fields off the stream the phases are drawn from, so turbulence does not re-roll the still. */
    private const val WarpSeed = 0x6A09E667L
}
