package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Paints one `VitrallGenerator.Plan` through **both** rasterizers and measures how far apart they land — the
 * agreement docs/MORPH_ENGINE_PLAN.md's M2 exists to prove rather than assert.
 *
 * ## What is actually being compared, and why it is not Compose
 *
 * `VitrallGenerator.draw` takes an `android.graphics.Canvas`, and that is exactly what a Compose `DrawScope` hands
 * out (`drawIntoCanvas { it.nativeCanvas }`). So there is no separate Compose drawing path to write and none to test:
 * the scrub and the bake issue **the same draw calls from the same plan**. What differs is only which rasterizer is
 * on the other end of the canvas — Skia's CPU raster for a software `Bitmap`, and Skia's GPU backend for a
 * hardware-accelerated one.
 *
 * That GPU backend is reached here through [RenderNode] + [HardwareRenderer], which is *what Compose uses
 * underneath* on a hardware-accelerated window: a `DrawScope` records into a RenderNode's recording canvas and
 * HardwareRenderer rasterizes it. Going through it directly keeps Compose's test infrastructure out of a module that
 * has no Compose in it, and puts the measurement where the risk actually is.
 *
 * ## Why the bar is not "byte-identical"
 *
 * M1's bar was byte-identical because both sides were the same rasterizer. Here they are two, and two rasterizers
 * antialias differently by construction — a GPU edge is coverage-sampled by the hardware and a CPU edge by Skia's
 * own scan conversion. Expecting equality would be expecting the wrong thing. So the question is *where* and *how
 * much* they disagree:
 *
 * - **A few counts of difference spread along pane edges** is antialiasing, and is the expected result.
 * - **A large difference over whole pane interiors** would mean a `Paint` feature silently did nothing on the
 *   hardware canvas. That is the failure worth catching, and Vitrall is a good probe for it because its rim is a
 *   `BlurMaskFilter` inside a `clipPath` — a mask filter was for years unsupported under hardware acceleration, and
 *   an unsupported one does not throw, it simply draws unblurred or not at all.
 *
 * The PNGs are written for the same reason the sweeps are: a number says two pictures differ, and only the
 * difference map says whether the rim is missing or the edges are merely softer. Pull them with
 * `adb pull /sdcard/Pictures/genharness` — see [saveHarnessPng] for the clearing rule.
 */
@RunWith(AndroidJUnit4::class)
class VitrallLivePathTest {

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
    fun theHardwareCanvasPaintsTheSameWindowAsTheBake() {
        // HardwareRenderer and Bitmap.wrapHardwareBuffer are API 29+. The launcher's floor is 26, so the *scrub* has
        // to fall back to the software path below that — this test measures the agreement where there is one to
        // measure, and says so rather than pretending to cover every device.
        assumeTrue("offscreen hardware rendering needs API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val params = DesignParams(colorMode = WallpaperColorMode.BICHROMATIC)
        val plan = VitrallGenerator.plan(Width, Height, params, Seed)

        val baked = createBitmap(Width, Height).also {
            VitrallGenerator.draw(Canvas(it), plan, palette, Width, Height)
        }
        val live = renderOnHardware {
            VitrallGenerator.draw(it, plan, palette, Width, Height)
        }

        val difference = compare(baked, live)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        saveHarnessPng(resolver, "live_vitrall_baked.png", baked)
        saveHarnessPng(resolver, "live_vitrall_hardware.png", live)
        saveHarnessPng(resolver, "live_vitrall_difference.png", difference.map)
        Log.i(Tag, "mean=${difference.mean} max=${difference.max} over${Loud}=${difference.loudShare}")

        // Deliberately a statement about *area*, not about any single pixel: an antialiased edge legitimately differs
        // by a lot at the pixel, and a feature that silently did nothing differs a little over a great many. The
        // first is what the max catches and must be tolerated; the second is what this catches.
        assertTrue(
            "pane interiors must agree — ${difference.loudShare} of pixels differ by more than $Loud, " +
                "which is a Paint feature the hardware canvas ignored rather than antialiasing",
            difference.loudShare < MaxLoudShare,
        )
    }

    /**
     * Rasterizes [record] on the GPU and reads the result back as a software bitmap.
     *
     * The read-back is what makes this a test rather than a preview, and it is also why this is not how the scrub
     * would draw: a scrub paints into the window's own render node and never copies anything back.
     */
    private fun renderOnHardware(record: (Canvas) -> Unit): Bitmap {
        val reader = ImageReader.newInstance(
            Width,
            Height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("vitrall")
        try {
            renderer.setSurface(reader.surface)
            node.setPosition(0, 0, Width, Height)
            val canvas = node.beginRecording()
            try {
                record(canvas)
            } finally {
                node.endRecording()
            }
            renderer.setContentRoot(node)
            // Without waiting for present the image is not necessarily on the reader's queue yet, and the acquire
            // below returns null — which reads as "hardware rendering is broken here" rather than as a race.
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = requireNotNull(reader.acquireNextImage()) { "the GPU produced no frame" }
            return image.use {
                val buffer = requireNotNull(it.hardwareBuffer)
                buffer.use { hardware ->
                    val wrapped = requireNotNull(
                        Bitmap.wrapHardwareBuffer(hardware, ColorSpace.get(ColorSpace.Named.SRGB)),
                    )
                    // A hardware bitmap has no pixels to read; the copy is what brings them back to the CPU.
                    wrapped.copy(Bitmap.Config.ARGB_8888, false)!!
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }

    /**
     * How far apart two renders of one plan are, and a picture of where.
     *
     * @property mean the average per-channel difference over every pixel, `0..255`.
     * @property max the largest single-channel difference anywhere.
     * @property loudShare the fraction of pixels differing by more than [Loud] on any channel — the statistic the
     *   assertion is made on, because it separates "edges are softer" from "something was not drawn".
     * @property map the difference amplified [MapGain]× into grayscale, so a faint disagreement is visible at all.
     */
    private class Difference(val mean: Double, val max: Int, val loudShare: Double, val map: Bitmap)

    private fun compare(baked: Bitmap, live: Bitmap): Difference {
        val a = IntArray(Width * Height).also { baked.getPixels(it, 0, Width, 0, 0, Width, Height) }
        val b = IntArray(Width * Height).also { live.getPixels(it, 0, Width, 0, 0, Width, Height) }
        val map = IntArray(a.size)

        var total = 0L
        var max = 0
        var loud = 0
        for (i in a.indices) {
            val red = abs((a[i] shr 16 and 0xFF) - (b[i] shr 16 and 0xFF))
            val green = abs((a[i] shr 8 and 0xFF) - (b[i] shr 8 and 0xFF))
            val blue = abs((a[i] and 0xFF) - (b[i] and 0xFF))
            val worst = maxOf(red, green, blue)
            total += red + green + blue
            if (worst > max) max = worst
            if (worst > Loud) loud++
            val lit = (worst * MapGain).coerceAtMost(0xFF)
            map[i] = 0xFF shl 24 or (lit shl 16) or (lit shl 8) or lit
        }

        return Difference(
            mean = total.toDouble() / (a.size * 3),
            max = max,
            loudShare = loud.toDouble() / a.size,
            map = createBitmap(Width, Height).also { it.setPixels(map, 0, Width, 0, 0, Width, Height) },
        )
    }

    private companion object {
        const val Tag = "VitrallLive"
        const val Seed = 42L
        const val Width = 1080
        const val Height = 2400

        /**
         * The per-channel difference above which a pixel is counted as disagreeing rather than as merely resampled.
         *
         * Well above the couple of counts two rasterizers land apart on a shared flat fill, and well below what a
         * missing fill or an undrawn rim would show.
         */
        const val Loud = 24

        /** How far the difference map is amplified so a two-count disagreement is visible rather than black. */
        const val MapGain = 8

        /**
         * How much of the frame may disagree loudly before the two paths are not drawing the same window.
         *
         * **Measured at `4.2e-4` on an API 36 emulator** — a hairline along pane boundaries and nothing else. The bar
         * is set an order of magnitude above that for headroom, because two *GPUs* antialias differently too and this
         * has to pass on real hardware, not only where it was recorded.
         *
         * It still fails by a wide margin on the thing it exists to catch. Vitrall's rim is a blurred stroke washed
         * inward from every pane's edge; a hardware canvas that ignored the `BlurMaskFilter` would light up a large
         * share of the frame — two orders of magnitude past this — where softened edges cannot reach it.
         */
        const val MaxLoudShare = 0.005
    }
}
