package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.DitherKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of dithering: the palette step, where a channel rounds, and the two kernels' shapes.
 *
 * [LayerPixelateTest]'s reason: only the bake draws a dither, so nothing competes with this — and every failure is
 * silent. A step size off by one is a slightly bolder dither; a diffusion weight in the wrong cell is a plausible
 * different kernel; an uncentered ordered threshold brightens the whole icon as it dithers. None throw.
 */
class LayerDitherTest {

    @Test
    fun `the palette step lands its ends exactly on black and white`() {
        // 255 / (levels - 1): two levels step by the whole range, so a channel is only ever off or full.
        assertEquals(255, LayerDither.stepSize(2))
        assertEquals(127, LayerDither.stepSize(3))
        assertEquals(36, LayerDither.stepSize(8))
    }

    @Test
    fun `quantizing rounds to the nearest step and clamps to a channel`() {
        val step = LayerDither.stepSize(2) // 255: nearest of 0 or 255

        assertEquals(0, LayerDither.quantize(127, step))
        assertEquals(255, LayerDither.quantize(128, step))
        // Error diffusion can push a channel past its range; the palette value still has to be a real channel.
        assertEquals(255, LayerDither.quantize(400, step))
        assertEquals(0, LayerDither.quantize(-40, step))
    }

    @Test
    fun `a cell is a fraction of the box, floored at a pixel`() {
        assertEquals(3, LayerDither.cellPx(0.03f, sizePx = 96))
        assertEquals(1, LayerDither.cellPx(0.0001f, sizePx = 96))
    }

    @Test
    fun `Floyd-Steinberg spreads all of the error and Atkinson drops a quarter`() {
        val floyd = LayerDither.diffusionOf(DitherKernel.FLOYD_STEINBERG)!!
        val atkinson = LayerDither.diffusionOf(DitherKernel.ATKINSON)!!

        assertEquals(1f, floyd.sumOf { it.weight.toDouble() }.toFloat(), 0.0001f)
        // Six eighths, deliberately — the missing quarter is what gives Atkinson its high-contrast look.
        assertEquals(0.75f, atkinson.sumOf { it.weight.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `an ordered kernel has no error to diffuse`() {
        assertNull(LayerDither.diffusionOf(DitherKernel.ORDERED))
    }

    @Test
    fun `the ordered threshold is centered on zero, so it dithers without brightening`() {
        val step = 255

        // The Bayer cell that maps to the middle of a step biases it by nothing.
        assertEquals(0, LayerDither.orderedThreshold(cellX = 1, cellY = 0, stepSize = step))
        // Its darkest and brightest cells push a channel down and up by about half a step, in balance.
        assertTrue(LayerDither.orderedThreshold(cellX = 0, cellY = 0, stepSize = step) < 0)
        assertTrue(LayerDither.orderedThreshold(cellX = 0, cellY = 3, stepSize = step) > 0)
    }

    @Test
    fun `the ordered matrix tiles every four cells`() {
        assertEquals(
            LayerDither.orderedThreshold(cellX = 1, cellY = 0, stepSize = 255),
            LayerDither.orderedThreshold(cellX = 5, cellY = 8, stepSize = 255),
        )
    }
}
