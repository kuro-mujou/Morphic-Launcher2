package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.core.graphics.createBitmap
import kotlin.math.abs

/**
 * The instrument behind every live-path test: paint on the GPU, read it back, and measure how far it lands from the
 * software bake of the same plan.
 *
 * **Why RenderNode and not Compose.** A scrub paints through `drawIntoCanvas { it.nativeCanvas }`, which is a
 * RenderNode's recording canvas rasterized by [HardwareRenderer] — so going through them directly measures exactly the
 * rasterizer a scrub runs on, without Compose's test infrastructure in a module that has no Compose in it.
 *
 * **API 29+**, for [HardwareRenderer] and `Bitmap.wrapHardwareBuffer`; callers assume it rather than pretend to cover
 * the launcher's floor of 26.
 */
internal object LivePath {

    /**
     * The per-channel difference above which a pixel is counted as disagreeing rather than as merely antialiased
     * differently. Well above the couple of counts two rasterizers land apart on a shared flat fill, and well below
     * what a missing fill, an ignored blur or an unsupported blend would show.
     */
    const val Loud = 24

    /** How far the difference map is amplified so a two-count disagreement is visible rather than black. */
    private const val MapGain = 8

    /**
     * Rasterizes [record] on the GPU at `[width]` × `[height]` and reads the result back as a software bitmap.
     *
     * The read-back is what makes this a test rather than a preview, and it is also why this is not how a scrub
     * draws: a scrub paints into the window's own render node and never copies anything back.
     */
    fun renderOnHardware(width: Int, height: Int, record: (Canvas) -> Unit): Bitmap {
        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("livepath")
        try {
            renderer.setSurface(reader.surface)
            node.setPosition(0, 0, width, height)
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
     * @property loudShare the fraction of pixels differing by more than [Loud] on any channel — the statistic to
     *   assert on, because it separates "edges are softer" from "something was not drawn".
     * @property map the difference amplified into grayscale, so a faint disagreement is visible at all.
     */
    class Difference(val mean: Double, val max: Int, val loudShare: Double, val map: Bitmap)

    /** Compares two renders of the same size. */
    fun compare(baked: Bitmap, live: Bitmap): Difference {
        val width = baked.width
        val height = baked.height
        val a = IntArray(width * height).also { baked.getPixels(it, 0, width, 0, 0, width, height) }
        val b = IntArray(width * height).also { live.getPixels(it, 0, width, 0, 0, width, height) }
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
            map = createBitmap(width, height).also { it.setPixels(map, 0, width, 0, 0, width, height) },
        )
    }
}
