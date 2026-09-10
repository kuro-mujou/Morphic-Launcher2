package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.system.measureNanoTime

/**
 * Times every generator across a ladder of render sizes, and fits each design's **fixed cost** and **per-pixel cost**
 * from the curve — the numbers docs/MORPH_ENGINE_PLAN.md needs before its first slice, and which nothing in this repo
 * has ever measured.
 *
 * **Not an assertion, an instrument.** Like [GeneratorRenderHarness] it cannot pass or fail; it exists to produce a
 * table. It is a separate class from that one because it is a different measurement with a different output, and
 * because it must be run **alone** — see the thermal note below.
 *
 * ## What the two fitted numbers are, and why they are worth more than the raw times
 *
 * A generator's cost is `fixed + perPixel × pixels`: some work is planning the picture (scattering sites, walking
 * trails, subdividing a lattice) and does not care how big the frame is, and the rest is filling pixels and scales
 * with the frame exactly. Timing at **six sizes** and least-squaring the line through them separates the two —
 * which is the `plan` cost versus `draw` cost the morph plan is sized by, obtainable **before** anything is split.
 *
 * Read the fit as a diagnosis:
 * - **`fixed` near zero, `r2` near 1** — pixel-bound. A field design: the picture is a per-pixel function of a few
 *   parameters, so a scrub frame at a fraction of the size costs that fraction. The morph plan's "field designs
 *   downscale for free" bucket, confirmed by measurement rather than by reading the source.
 * - **`fixed` large** — plan-bound. The design does real work before it draws anything, so shrinking the frame buys
 *   little and **pre-planning matters**: this is the design that will hitch at touch-down if the next seed is not
 *   already in hand.
 * - **`r2` poor** — the composition is *changing* with the size rather than merely resolving finer, so the cost is not
 *   linear in pixels. That is the same failure `DraftShortSidePx`' 360px floor exists to prevent (`ContourGenerator`
 *   samples a lattice 360 cells across the short side and floors the cell at one pixel, so below that it draws a
 *   coarser field rather than a softer one). **A design with a poor fit is a design whose downscale is not free**, and
 *   this is the cheapest way to find the rest of them.
 *
 * ## Method, and every clause of it is a way to get a wrong number
 *
 * - **Run it on the real phone, never the emulator.** An emulator renders on the host CPU with no thermal ceiling and
 *   a different Skia build; its numbers are not slow or fast versions of the device's, they are unrelated to them.
 *   [logDevice] prints what it ran on so a stale table cannot be mistaken for a fresh one.
 * - **Run it alone.** [GeneratorRenderHarness]'s sweeps take minutes and heat the phone, and a throttled SoC reports a
 *   generator as slower than it is. Filter to this class:
 *
 *   ```
 *   gradle :core:graphics:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=inkspire.morphic.core.graphics.wallpaper.GeneratorTimingHarness
 *   adb logcat -d -s GenTiming:I > timings.csv
 *   ```
 *
 * - **[WarmUps] discarded before each timed set.** The first call into a generator pays class initialization and runs
 *   interpreted before the JIT has compiled anything, and at full size that is routinely several times the settled
 *   cost. Timing it would report every design as slow and the *cheap* ones as slowest, since they have the least real
 *   work to hide it behind.
 * - **Median *and* min, never mean.** A GC pause or a scheduler preemption lands in one sample and drags a mean with
 *   it. The min is what the machine can do; the median is what it does. They disagreeing by much is itself the signal
 *   that the run was noisy.
 * - **Nothing is written to disk during timing.** [GeneratorRenderHarness] saves a PNG per render through the
 *   MediaStore, which is far heavier than most of the renders here — so results go to **logcat** instead. That also
 *   sidesteps the stale-file trap in that class's KDoc: a re-run cannot overwrite a MediaStore file it does not own, so
 *   a pulled `timings.csv` would silently be the *previous* run's. Logcat is append-only and timestamped and cannot
 *   go stale.
 * - **The allocation is inside the measurement**, because it is inside `render` and the studio pays it every time.
 *
 * Rendered at the default knobs, `seed = 42` and bichromatic — the same arrangement [GeneratorRenderHarness] renders,
 * so a timing and a PNG describe the same picture.
 */
@RunWith(AndroidJUnit4::class)
class GeneratorTimingHarness {

    private val palette = PaletteColorMode.resolve(
        Palette(
            listOf(
                0xFFF2E2C4.toInt(),
                0xFFE6A15C.toInt(),
                0xFFC9603E.toInt(),
                0xFF2C6E6B.toInt(),
                0xFF1F3A4D.toInt(),
                0xFF121E2B.toInt(),
            ),
        ),
        WallpaperColorMode.BICHROMATIC,
    )

    @Test
    fun reportRenderTimings() {
        logDevice()
        Log.i(Tag, "design,shortPx,longPx,megapixels,minMs,medianMs")

        val fits = WallpaperDesign.entries.map { design ->
            val samples = Sizes.map { (width, height) -> measure(design, width, height) }
            samples.forEach { Log.i(Tag, it.csv(design)) }
            design to fit(samples)
        }

        Log.i(Tag, "")
        Log.i(Tag, "design,fixedMs,perMegapixelMs,fullFrameMs,planShareAtFull,r2")
        fits.forEach { (design, fit) -> Log.i(Tag, fit.csv(design)) }
    }

    /** One design at one size: [Reps] timed renders after [WarmUps] discarded ones. */
    private fun measure(design: WallpaperDesign, width: Int, height: Int): Sample {
        val generator = Generators.forDesign(design)
        val params = DesignParams(colorMode = WallpaperColorMode.BICHROMATIC)

        repeat(WarmUps) { generator.render(width, height, palette, params, Seed).recycle() }

        val nanos = LongArray(Reps) {
            var bitmap: Bitmap? = null
            val elapsed = measureNanoTime { bitmap = generator.render(width, height, palette, params, Seed) }
            // Outside the measurement, but before the next allocation — a full-frame bitmap is ~10MB and holding six
            // of them would trade a timing run for an OOM.
            bitmap?.recycle()
            elapsed
        }
        nanos.sort()

        return Sample(
            width = width,
            height = height,
            minMs = nanos.first() / NanosPerMs,
            medianMs = nanos[nanos.size / 2] / NanosPerMs,
        )
    }

    /**
     * Least-squares `cost = fixed + perMegapixel × megapixels` through one design's [samples], on the **min** of each
     * (the fixed cost is what the line is being asked for, and a preemption in one sample tilts it).
     *
     * `r2` is reported rather than assumed: a design whose composition changes with the size is not linear in pixels
     * at all, and the fit silently returning a plausible-looking pair of numbers for it is exactly the failure worth
     * catching. See the class KDoc.
     *
     * **A slightly negative `fixedMs` is a result, not a bug** — a purely pixel-bound design's true intercept is zero,
     * and measurement noise puts the fitted line a hair either side of it. Read anything within a millisecond of zero
     * as zero. It is deliberately not clamped: a clamp would turn "this design has no fixed cost" and "this fit is
     * mildly noisy" into the same number.
     */
    private fun fit(samples: List<Sample>): Fit {
        val n = samples.size
        val meanPixels = samples.sumOf { it.megapixels } / n
        val meanMs = samples.sumOf { it.minMs } / n

        val covariance = samples.sumOf { (it.megapixels - meanPixels) * (it.minMs - meanMs) }
        val variance = samples.sumOf { (it.megapixels - meanPixels) * (it.megapixels - meanPixels) }
        val perMegapixel = if (variance == 0.0) 0.0 else covariance / variance
        val fixed = meanMs - perMegapixel * meanPixels

        val residual = samples.sumOf { sample ->
            val predicted = fixed + perMegapixel * sample.megapixels
            (sample.minMs - predicted) * (sample.minMs - predicted)
        }
        val total = samples.sumOf { (it.minMs - meanMs) * (it.minMs - meanMs) }

        return Fit(
            fixedMs = fixed,
            perMegapixelMs = perMegapixel,
            fullFrameMs = samples.maxBy { it.megapixels }.minMs,
            r2 = if (total == 0.0) 1.0 else 1.0 - residual / total,
        )
    }

    private fun logDevice() {
        Log.i(Tag, "# ${Build.MANUFACTURER} ${Build.MODEL}, ${Build.HARDWARE}, API ${Build.VERSION.SDK_INT}")
        // Named rather than judged: nothing here can reliably tell an emulator from a phone, and a wrong verdict is
        // worse than the string. `goldfish`/`ranchu` hardware and a `generic` model are the tell.
        Log.i(Tag, "# reps=$Reps warmups=$WarmUps seed=$Seed — emulator numbers are not comparable to a device's")
    }

    /** One design at one size. Times are milliseconds. */
    private data class Sample(val width: Int, val height: Int, val minMs: Double, val medianMs: Double) {
        val megapixels: Double get() = width.toDouble() * height / 1_000_000

        fun csv(design: WallpaperDesign): String =
            "${design.name},$width,$height,${megapixels.r(3)},${minMs.r(2)},${medianMs.r(2)}"
    }

    /**
     * A design's cost curve, split.
     *
     * @property planShareAtFull the fraction of a full-frame render that does **not** scale with the frame — how much
     *   of the cost a scrub cannot escape by drawing smaller, and therefore how much pre-planning is worth.
     */
    private data class Fit(
        val fixedMs: Double,
        val perMegapixelMs: Double,
        val fullFrameMs: Double,
        val r2: Double,
    ) {
        private val planShareAtFull: Double
            get() = if (fullFrameMs <= 0.0) 0.0 else (fixedMs / fullFrameMs).coerceIn(0.0, 1.0)

        fun csv(design: WallpaperDesign): String = "${design.name},${fixedMs.r(2)},${perMegapixelMs.r(2)}," +
            "${fullFrameMs.r(2)},${planShareAtFull.r(3)},${r2.r(4)}"
    }

    private companion object {
        const val Tag = "GenTiming"
        const val Seed = 42L
        const val NanosPerMs = 1_000_000.0

        /**
         * Discarded renders before each timed set, per design per size.
         *
         * One is enough to get past class initialization and the interpreter; the JIT keeps optimizing after it, which
         * is what the min over [Reps] is for.
         */
        const val WarmUps = 1

        /** Timed renders per design per size. The run's whole duration is roughly linear in this. */
        const val Reps = 5

        /**
         * The ladder, at the studio's own aspect (9:20) so every rung is the *same composition* at fewer pixels rather
         * than a differently-shaped one — which is the assumption the fit rests on.
         *
         * It spans full-screen down to a 64th of the pixels, and the rungs are chosen to bracket the sizes that
         * actually matter: **1080** is what the bake pays, **360** is today's `DraftShortSidePx`, and the three below
         * it are where a scrub frame would have to live for a field design to morph inside a frame budget.
         */
        val Sizes = listOf(
            1080 to 2400,
            540 to 1200,
            360 to 800,
            270 to 600,
            180 to 400,
            135 to 300,
        )

        /** Fixed-decimal formatting, so the log is a CSV rather than a column of `1.2345678E-4`. */
        fun Double.r(places: Int): String = String.format(Locale.US, "%.${places}f", this)
    }
}
