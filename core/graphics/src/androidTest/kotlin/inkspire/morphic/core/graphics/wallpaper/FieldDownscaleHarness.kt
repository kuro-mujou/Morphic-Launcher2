package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
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
