package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

/**
 * How small a field design may be evaluated at before a scrub stops being the same picture — the measurement
 * `DraftShortSidePx` has only ever had a guess for.
 *
 * **The number is per design, and 360 is Contour's rather than everyone's.** That floor exists because
 * `ContourGenerator` lays its terrain on a lattice 360 cells across the short side, so below it the contours *move*
 * instead of softening. Nothing says a mesh gradient needs anything like it, and a scrub's cost is linear in the
 * pixels evaluated — so the difference between 360 and whatever this design actually needs is the difference between
 * a scrub that can afford full frames and one that cannot.
 *
 * **Reported as a difference against the full-resolution render, which is the only honest reference.** A downscaled
 * evaluation blown back up is *supposed* to differ a little: it is reconstructing a smooth field from fewer samples.
 * What matters is whether it differs *smoothly* — the mean staying under a level or two of 255 — or whether some
 * feature has moved, which shows up as a large maximum in a small part of the frame.
 *
 * Read with:
 * ```
 * gradle :core:graphics:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=inkspire.morphic.core.graphics.wallpaper.FieldDownscaleHarness
 * adb logcat -d -s FieldDownscale
 * ```
 */
@RunWith(AndroidJUnit4::class)
class FieldDownscaleHarness {

    /**
     * Every corner of the knob space this design has, at every candidate resolution.
     *
     * **The floor has to be the worst case, not the default one.** The colour lattice is the thing with a frequency
     * — at density 8 it is nine nodes across the short side — so a resolution that flatters a two-patch gradient can
     * still be losing a patch of an eight-patch one. Warp matters for the opposite reason: it is what puts a steep
     * gradient anywhere in the frame at all, so its top end is where a coarse buffer would alias if it were going to.
     */
    @Test
    fun measureMeshGradient() {
        Log.i(Tag, "design  density warp variant | short side | mean/255  max/255  % over 4")
        for (density in listOf(0f, 1f)) {
            for (warp in listOf(0f, 1f)) {
                for (variant in 0..2) {
                    val params = DesignParams(
                        density = density,
                        irregularity = warp,
                        scale = 0.3f,
                        variant = variant,
                        colorMode = WallpaperColorMode.COLORFUL,
                    )
                    report(params)
                }
            }
        }
    }

    /**
     * The scrub path at full resolution is the bake — the field bucket's version of the assertion `VitrallLivePathTest`
     * makes for the primitive one.
     *
     * There, the two paths issue the same draw calls and differ only in the rasterizer. Here they share the pixel loop
     * and differ in whether its output is written straight into the bitmap or blitted through a canvas, so at 1:1 they
     * must be **exactly** equal — a blit that resampled, dithered or premultiplied would show up here as a handful of
     * levels and nowhere else, and "the scrub is very slightly the wrong colour" is not something anyone would catch
     * by looking.
     */
    @Test
    fun theBlitIsExactAtFullSize() {
        val params = DesignParams(density = 0.6f, irregularity = 0.7f, scale = 0.3f, variant = 1)
        val palette = PaletteColorMode.resolve(Palette(Stops), params.colorMode)

        val baked = MeshGradientGenerator.render(Width, Height, palette, params, Seed)
        val direct = IntArray(Width * Height)
        baked.getPixels(direct, 0, Width, 0, 0, Width, Height)

        val blitted = pixels(MeshGradientGenerator.plan(params, palette, Seed), min(Width, Height))
        val differing = direct.indices.count { direct[it] != blitted[it] }
        assertEquals("a full-size blit must be the bake, pixel for pixel", 0, differing)
    }

    /**
     * Every design still filed as a field, measured **before** it is split — whether its picture survives being
     * evaluated small, which the timing fit that filed it cannot say.
     *
     * **Through `render`, because none of these has a plan yet.** A field plan takes no size, so evaluating one into a
     * buffer of a given short side *is* rendering the design at that size; rendering small and blowing it up with
     * the scrub's own bilinear filter is therefore the scrub these designs would get. Where a design builds its
     * composition out of pixels rather than shares of the frame, this reports that too — as a failure, which is
     * right: that design's scrub would show it.
     *
     * **The knobs come from each design's own [DesignStyle]**, one at a time from the defaults and then all at their
     * low and high ends together, plus every option of every chooser. So a design is swept over what it actually
     * reads, and one that grows a knob is swept at it without an edit here.
     *
     * **A noise floor first.** Two full renders of the same recipe are compared, because a generator that is not
     * deterministic differs from itself — Planet has been caught doing that — and without the floor its noise would
     * read as a downscale failure.
     *
     * Read with `adb logcat -d -s FieldSurvey`; the worst recipe per design is saved at full size and at 240 and 120
     * as `down_<design>_<size>.png` for looking at.
     */
    @Test
    fun surveyRemainingFieldDesigns() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        Log.i(Survey, "design | recipe | short side | mean/255  max/255  % over $Loud")
        for (design in RemainingFields) {
            val generator = Generators.forDesign(design)
            val recipes = recipesFor(generator.style)
            val floor = difference(
                renderPixels(generator, recipes.first().second, Width),
                renderPixels(generator, recipes.first().second, Width),
            )
            Log.i(Survey, "${design.name} | noise floor | full | %s".format(floor))

            val worst = mutableMapOf<Int, Pair<String, Gap>>()
            for ((name, params) in recipes) {
                val full = renderPixels(generator, params, Width)
                for (shortSide in SurveyCandidates) {
                    val gap = difference(full, renderPixels(generator, params, shortSide))
                    Log.i(Survey, "${design.name} | $name | $shortSide | $gap")
                    if (gap.overShare >= (worst[shortSide]?.second?.overShare ?: -1.0)) worst[shortSide] = name to gap
                }
            }
            for (shortSide in SurveyCandidates) {
                val (name, gap) = worst.getValue(shortSide)
                Log.i(Survey, "WORST ${design.name} | $shortSide | $name | $gap")
            }

            val (worstName, _) = worst.getValue(120)
            val params = recipes.first { it.first == worstName }.second
            for (shortSide in listOf(Width, 240, 120)) {
                val bitmap = createBitmap(Width, Height)
                bitmap.setPixels(renderPixels(generator, params, shortSide), 0, Width, 0, 0, Width, Height)
                val label = if (shortSide == Width) "full" else shortSide.toString()
                saveHarnessPng(resolver, "down_${design.name}_$label.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /** The recipes to sweep for a design declaring [style]: the default, each knob at each end, both corners, each choice. */
    private fun recipesFor(style: DesignStyle): List<Pair<String, DesignParams>> {
        val base = DesignParams(colorMode = WallpaperColorMode.COLORFUL)
        val sliders = listOfNotNull(
            style.amount?.let { "density" to { p: DesignParams, v: Float -> p.copy(density = v) } },
            style.scale?.let { "scale" to { p: DesignParams, v: Float -> p.copy(scale = v) } },
            style.taper?.let { "taper" to { p: DesignParams, v: Float -> p.copy(taper = v) } },
            style.irregularity?.let { "irregularity" to { p: DesignParams, v: Float -> p.copy(irregularity = v) } },
            style.depth?.let { "depth" to { p: DesignParams, v: Float -> p.copy(depth = v) } },
            style.depthScale?.let { "depthScale" to { p: DesignParams, v: Float -> p.copy(depthScale = v) } },
            style.roundness?.let { "roundness" to { p: DesignParams, v: Float -> p.copy(roundness = v) } },
            style.rotation?.let { "rotation" to { p: DesignParams, v: Float -> p.copy(rotation = v) } },
        )
        val out = mutableListOf("default" to base)
        for ((name, set) in sliders) for (v in listOf(0f, 1f)) out += "$name=${v.toInt()}" to set(base, v)
        if (sliders.size > 1) {
            for (v in listOf(0f, 1f)) out += "all=${v.toInt()}" to sliders.fold(base) { p, (_, set) -> set(p, v) }
        }
        style.variant?.options?.indices?.drop(1)?.forEach { out += "variant=$it" to base.copy(variant = it) }
        style.finish?.options?.indices?.drop(1)?.forEach { out += "finish=$it" to base.copy(finish = it) }
        style.colorLayout?.options?.indices?.drop(1)?.forEach { out += "layout=$it" to base.copy(colorLayout = it) }
        return out
    }

    /** [generator]'s picture of [params] rendered with a short side of [shortSide] and blown up to the full frame. */
    private fun renderPixels(generator: Generator, params: DesignParams, shortSide: Int): IntArray {
        val palette = PaletteColorMode.resolve(Palette(Stops), params.colorMode)
        val w = shortSide
        val h = (Height.toLong() * shortSide / Width).toInt()
        val small = generator.render(w, h, palette, params, Seed)
        val out = IntArray(Width * Height)
        if (w == Width) {
            small.getPixels(out, 0, Width, 0, 0, Width, Height)
        } else {
            val up = createBitmap(Width, Height)
            Canvas(up).drawBitmap(small, null, RectF(0f, 0f, Width.toFloat(), Height.toFloat()), Blow)
            up.getPixels(out, 0, Width, 0, 0, Width, Height)
            up.recycle()
        }
        small.recycle()
        return out
    }

    /** How two full frames differ, in the terms the survey reports. */
    private class Gap(val mean: Double, val max: Int, val overShare: Double) {
        override fun toString() = "%6.2f %4d %8.3f%%".format(mean, max, overShare * 100)
    }

    private fun difference(a: IntArray, b: IntArray): Gap {
        var total = 0L
        var worst = 0
        var loud = 0
        for (i in a.indices) {
            val d = channelGap(a[i], b[i])
            total += d
            worst = max(worst, d)
            if (d > Loud) loud++
        }
        return Gap(total.toDouble() / a.size, worst, loud.toDouble() / a.size)
    }

    private fun report(params: DesignParams) {
        val palette = PaletteColorMode.resolve(Palette(Stops), params.colorMode)
        val mesh = MeshGradientGenerator.plan(params, palette, Seed)
        val full = pixels(mesh, min(Width, Height))

        for (shortSide in Candidates) {
            val scaled = pixels(mesh, shortSide)
            var total = 0L
            var worst = 0
            var loud = 0
            for (i in full.indices) {
                val d = channelGap(full[i], scaled[i])
                total += d
                worst = max(worst, d)
                if (d > Loud) loud++
            }
            val mean = total.toDouble() / full.size
            val over = loud.toDouble() / full.size
            Log.i(
                Tag,
                "mesh %5.1f %4.1f %d | %4d | %6.2f %6d %8.3f%%".format(
                    params.density, params.irregularity, params.variant, shortSide, mean, worst, over * 100,
                ),
            )
        }
    }

    /** The picture as it lands on screen: evaluated at [shortSide] and blown up to the full frame. */
    private fun pixels(mesh: MeshGradientGenerator.Mesh, shortSide: Int): IntArray {
        val bitmap = createBitmap(Width, Height)
        MeshGradientGenerator.draw(Canvas(bitmap), mesh, Width, Height, shortSide)
        val out = IntArray(Width * Height)
        bitmap.getPixels(out, 0, Width, 0, 0, Width, Height)
        bitmap.recycle()
        return out
    }

    /** The largest single-channel difference between two packed pixels — a colour that shifted hue, not just level. */
    private fun channelGap(a: Int, b: Int): Int {
        val r = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF))
        val g = abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF))
        return max(max(r, g), abs((a and 0xFF) - (b and 0xFF)))
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b

    private companion object {
        const val Tag = "FieldDownscale"
        const val Width = 1080
        const val Height = 2400
        const val Seed = 20260910L

        /** Where a difference stops being reconstruction and starts being visible on a flat wash. */
        const val Loud = 4

        val Candidates = listOf(360, 240, 180, 120, 90, 60, 45, 30, 20)

        const val Survey = "FieldSurvey"

        /** Coarser than [Candidates]: the survey is finding which designs have a floor at all, not pinning one. */
        val SurveyCandidates = listOf(360, 240, 180, 120, 60)

        /** The designs still filed as fields and not yet split — Mesh Gradient has [measureMeshGradient]. */
        val RemainingFields = listOf(
            WallpaperDesign.DIAGONAL_BANDS,
            WallpaperDesign.GRADIENT_COLUMNS,
            WallpaperDesign.LINEAR_GRADIENT,
            WallpaperDesign.LOUVERS,
            WallpaperDesign.MARBLE,
            WallpaperDesign.METABALLS,
            WallpaperDesign.PLASMA,
            WallpaperDesign.RIBBED_GLASS,
            WallpaperDesign.WAVE_DIVIDERS,
            WallpaperDesign.WAVES,
            WallpaperDesign.PLANET,
        )

        /** The scrub's own upscale — bilinear, as `MeshGradientGenerator` blows its buffer up. */
        val Blow = Paint(Paint.FILTER_BITMAP_FLAG)

        val Stops = listOf(
            0xFFF2E2C4.toInt(),
            0xFFE6A15C.toInt(),
            0xFFC9603E.toInt(),
            0xFF2C6E6B.toInt(),
            0xFF1F3A4D.toInt(),
            0xFF121E2B.toInt(),
        )
    }
}
