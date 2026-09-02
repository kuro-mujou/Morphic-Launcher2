package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bands of flat palette color separated by one wave, drawn again and again down the frame — *Wave Dividers*.
 *
 * **Every divider is the same wave at the same phase, and the bands between them are exactly equal.** That is the
 * design, and driving the reference is what settled it: at every setting of every knob its bands measure the same
 * height as each other and its dividers sit parallel. What this replaced jittered the band widths by
 * [DesignParams.irregularity] and gave each divider a sum of two sines at *random* frequencies and phases, so no
 * setting of ours drew the rank of identical waves theirs draws at all of them.
 *
 * **The wavelength is measured against the frame's *height*, not against the axis the wave runs along** — theirs,
 * measured: at *Waves* `9` the period is `267px` on a 2400px frame, which is `height / 9` to within a pixel. It
 * matters because of [DesignParams.variant]: an axis-relative wavelength would redraw the same setting at a different
 * scale the moment the stack is turned, since that axis is the frame's width at one angle and its diagonal at another.
 * [FrameAxis.lengthPx] is what converts, and it was added for this.
 *
 * **[DesignParams.irregularity] is *Wave depth* — the amplitude — because `0` there is the design's real rigid end.**
 * The reference calls the same knob *Wideness*, and winding it down gives dead-straight dividers: flat bands, no wave
 * left. That is exactly what the field's contract asks `0` to mean, which is why the amplitude sits here rather than
 * on [DesignParams.scale]. It is **squared**, so the uniform `0.5` default lands on the reference's own restrained
 * amplitude (a twentieth of the frame, measured) rather than halfway to a stack that folds through itself.
 *
 * **[DesignParams.scale] is *Wavelength*, running from a tight ripple to one broad sweep across the frame** — the
 * reference's *Waves* `20..1`, read the other way so the field keeps its own direction (`0` tight, `1` sprawling).
 *
 * **[DesignParams.variant] is their *Rotation*, sampled** — the call [DiagonalBandsGenerator] and [LouversGenerator]
 * both make, and for their reason: a continuous rotation wants an orientation field on [DesignParams] that no design
 * yet has. Theirs is continuous over `-179..180°` and opens on `2°`, so index `0` here is **flat**, which is also what
 * Diagonal Bands' KDoc already promised this design would be at rest.
 *
 * Two of the reference's six knobs are deliberately not ported. Its *Offset* is a **phase** — one full period across
 * its `-50..50` travel, which is why `-49` and `50` draw the same picture — and a phase is what [seed] is for; the
 * studio's shuffle should choose it rather than a slider. Its *Irregularity* roughens the shared waveform into a
 * jagged silhouette, which is a real look, but its own default is `0` and the field that would carry it is spent on
 * the amplitude whose zero is the actual rigid end; a second noise field for one design would be a model in a vacuum.
 *
 * The palette **cycles through every stop**, including the first: unlike [DiagonalBandsGenerator] this design reserves
 * no ground, which the reference confirms — at *Count* `20` all four of its stops paint bands and none is held back.
 */
object WaveDividersGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Bands* slider's own range. */
    private val Amount = AmountKnob.Count("Bands", 2..20)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Wavelength",
        irregularity = "Wave depth",
        variant = VariantKnob("Direction", Direction.entries.map { it.label }),
    )

    /**
     * Which way the stack of bands runs — the reference's *Rotation*, at the stops a segmented control can offer.
     *
     * Ascending, and **flat is first** because it has to be both: the panel reads a segmented control as one axis, so
     * the angles want sorting, and the model's contract is that `variant = 0` is the design's default look — which
     * theirs is, opening at `2°`.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property degrees the angle the dividers run at, measured from the horizontal.
     */
    internal enum class Direction(val label: String, val degrees: Float) {
        /** Level dividers marching down the frame — theirs at rest, and so the one at index `0`. */
        FLAT("0°", degrees = 0f),

        /** The shallow slope the reference's other banded designs open on. */
        SHALLOW("20°", degrees = 20f),

        /** The true diagonal. */
        DIAGONAL("45°", degrees = 45f),

        /** Upright dividers marching across the frame. */
        UPRIGHT("90°", degrees = 90f),

        /** The diagonal mirrored. */
        REVERSE("135°", degrees = 135f),
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val direction = Direction.entries[params.variant.coerceIn(0, Direction.entries.lastIndex)]
        val count = bandCount(params.density)
        val depth = waveDepth(params.irregularity)
        // The phase is the reference's Offset, taken from the seed instead: it is variety, not a choice worth a knob.
        val phase = Random(seed).nextFloat() * TwoPi

        val across = frameAxis(direction.degrees + QuarterTurn, width, height)
        val along = frameAxis(direction.degrees, width, height)
        // Cycles over the axis the wave runs along, from a wavelength set against the frame's height. See the KDoc.
        val turns = along.lengthPx * waveCycles(params.scale) / height

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val fy = y.toFloat()
            val row = y * width
            for (x in 0 until width) {
                val fx = x.toFloat()
                val offset = depth * sin(along.at(fx, fy) * turns * TwoPi + phase)
                // The stack keeps cycling past both ends of the frame, so a band displaced off it wraps rather than
                // clamping into a flat strip along the edge.
                val band = floor((across.at(fx, fy) - offset) * count).toInt()
                pixels[row + x] = palette.colorAt(band.mod(palette.size))
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [density] asks for — a pair of broad sweeps up to a finely rippled stack. Theirs exactly. */
    internal fun bandCount(density: Float): Int = Amount.at(density)

    /**
     * How many wave cycles fit in one frame *height* at [scale] — the reference's *Waves*, read the other way so the
     * field keeps its own direction: `0` is a tight ripple and `1` one broad sweep.
     */
    internal fun waveCycles(scale: Float): Float =
        MaxCycles - scale.coerceIn(0f, 1f) * (MaxCycles - MinCycles)

    /**
     * How far a divider swings off straight at [irregularity], as a share of the axis across the bands.
     *
     * **Squared**, so the `0.5` every design still opens at lands on the reference's own restrained amplitude — a
     * twentieth of the frame, measured off its default — rather than halfway to a stack that folds through itself.
     * Third design in two slices to buy a per-design default with an exponent, which is the argument for having real
     * ones made once more.
     */
    internal fun waveDepth(irregularity: Float): Float {
        val amount = irregularity.coerceIn(0f, 1f)
        return MaxDepth * amount * amount
    }

    /** The cycles per frame height at the ends of the wavelength knob — theirs, whose *Waves* runs `1..20`. */
    private const val MinCycles = 1f
    private const val MaxCycles = 20f

    /** The furthest a divider may swing off straight, as a share of the axis across the bands. */
    private const val MaxDepth = 0.19f

    /** From the dividers' own direction to the axis that measures across them. */
    private const val QuarterTurn = 90f

    private val TwoPi = (2.0 * PI).toFloat()
}
