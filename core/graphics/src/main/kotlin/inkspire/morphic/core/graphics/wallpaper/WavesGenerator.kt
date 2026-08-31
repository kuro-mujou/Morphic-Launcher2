package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The frame built from overlapping wave bands rising up it, each a flat palette color — the layered *Undula / Strata*
 * ridgelines (gart's `arts/layers`, `arts/hills`).
 *
 * **A stack of filled ridges, drawn back to front.** Each layer is a smooth wave — a baseline height plus a sum of a
 * few seeded sines — and everything below its crest is filled with the layer's color. The layers march up the frame
 * and are painted farthest-first, so each nearer band laps over the one behind it, which is what turns a set of curves
 * into overlapping *dunes* rather than a line drawing. The colors climb the palette front-to-back, so the ramp reads
 * as depth.
 *
 * **The wave is sines, not noise — a deliberate difference from [FlowFieldGenerator] and [ContourGenerator].** Those
 * want the organic wander of Perlin; a dune wants a *rolling*, near-periodic crest, which a sum of two or three sines
 * gives directly and controllably. [DesignParams.density] sets how many bands stack up, and
 * [DesignParams.irregularity] how tall the crests swell — from near-flat strata at `0` to steep, jagged dunes at `1`
 * (Smart Launcher's *Distortion*). Deterministic in [seed]: every layer's amplitudes and phases are drawn from it.
 *
 * [crestY] is pure and tested — the wave height is the arithmetic that decides whether a band even appears on screen
 * (a crest off the top or bottom of the frame is an invisible or full band), and it needs no canvas to check.
 */
object WavesGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Layers* slider's own range. */
    private val Amount = AmountKnob.Count("Layers", 3..9)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Swell",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val layers = layerCount(params.density)
        val random = Random(seed)
        // Each layer's wave: three (amplitude, frequency, phase) sine terms, and the baseline it rises from.
        val waves = List(layers) { Wave.random(random, amplitudeScale(params.irregularity)) }

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // the sky behind the farthest ridge — the first (lightest) stop
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Farthest (highest, palest) first so each nearer band overlaps it; the baseline steps down the frame per layer.
        for (layer in 0 until layers) {
            val baseline = (layer + 1).toFloat() / (layers + 1)
            paint.color = LinearGradientGenerator.colorAt((layer + 1).toFloat() / layers, palette)
            canvas.drawPath(bandPath(waves[layer], baseline, width, height), paint)
        }
        return bitmap
    }

    /** How many bands [density] asks for — bold ridges up to a finely stratified frame. */
    internal fun layerCount(density: Float): Int = Amount.at(density)

    /**
     * How much to scale a crest's amplitude for a given [irregularity] — `0` flattens the dunes toward strata, `1`
     * steepens them. Scaled so the default `0.5` returns `1.0`, leaving the shipped swell untouched.
     */
    internal fun amplitudeScale(irregularity: Float): Float =
        MinAmplitudeScale + irregularity.coerceIn(0f, 1f) * (MaxAmplitudeScale - MinAmplitudeScale)

    /**
     * The crest height of [wave] at horizontal position [nx] (`0..1`), returned as a fraction of the frame where `0`
     * is the top — [baseline] displaced up and down by the wave's summed sines. The amplitude is a fraction of the
     * frame, so a wave reads the same at any resolution.
     */
    internal fun crestY(wave: Wave, nx: Float, baseline: Float): Float {
        var offset = 0f
        for (term in wave.terms) {
            offset += term.amplitude * sin(nx * term.frequency + term.phase)
        }
        return baseline + offset
    }

    /** The filled region below [wave]'s crest, as a canvas path over a `[width] × [height]` frame. */
    private fun bandPath(wave: Wave, baseline: Float, width: Int, height: Int): Path {
        val path = Path()
        val steps = width.coerceAtLeast(2)
        path.moveTo(0f, crestY(wave, 0f, baseline) * height)
        for (i in 1..steps) {
            val nx = i.toFloat() / steps
            path.lineTo(nx * width, crestY(wave, nx, baseline) * height)
        }
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()
        return path
    }

    /** One sine term of a wave: how tall, how many cycles across the frame, and where it starts. */
    internal data class Term(val amplitude: Float, val frequency: Float, val phase: Float)

    /** A layer's crest, summed from [Terms] sine terms. */
    internal data class Wave(val terms: List<Term>) {
        companion object {
            fun random(random: Random, amplitudeScale: Float = 1f): Wave {
                val twoPi = (2.0 * PI).toFloat()
                return Wave(
                    List(Terms) {
                        Term(
                            // Amplitudes shrink per term so the first sine is the swell and the rest are ripples on it.
                            amplitude = (MaxAmplitude / (it + 1)) * (0.5f + random.nextFloat() * 0.5f) * amplitudeScale,
                            frequency = MinCycles + random.nextFloat() * (MaxCycles - MinCycles),
                            phase = random.nextFloat() * twoPi,
                        )
                    },
                )
            }
        }
    }

    /** How many sine terms sum into one crest — one swell plus two ripples. */
    private const val Terms = 3

    /** The tallest a term's crest may rise or fall, as a fraction of the frame — the first term's swell. */
    private const val MaxAmplitude = 0.06f

    /** The crest amplitude scale at the irregularity extremes — `0.5` lands between them at `1.0`, the shipped swell. */
    private const val MinAmplitudeScale = 0.4f
    private const val MaxAmplitudeScale = 1.6f

    /** How many cycles a term spans across the frame — a wide roll up to a few dunes. */
    private const val MinCycles = 1.2f
    private const val MaxCycles = 4f
}
