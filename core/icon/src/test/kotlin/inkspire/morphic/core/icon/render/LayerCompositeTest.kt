package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a layer's blend mode does to the layers beneath it.
 *
 * **These exist because the bake got this wrong on a real device.** Compositing through `PorterDuff.Mode` looked
 * like the whole job, and `PorterDuff.Mode.MULTIPLY` is `[Sa × Da, Sc × Dc]` — the *alpha* is the product too — so a
 * foreground set to multiply erased every app's background plate from the home screen wherever the foreground was
 * transparent. The live path was correct throughout, so the studio showed the icon intact and only the baked one was
 * wrong; the first two tests below are the two halves of that bug, and neither can pass by accident.
 */
class LayerCompositeTest {

    private fun blend(dst: Int, src: Int, mode: LayerBlend, opacity: Float = 1f) =
        LayerComposite.blend(dst, src, mode, opacity)

    private fun channels(argb: Int) = listOf(
        argb ushr 24 and 0xFF,
        argb shr 16 and 0xFF,
        argb shr 8 and 0xFF,
        argb and 0xFF,
    )

    @Test
    fun `a transparent source leaves the backdrop exactly as it was`() {
        // The bug, in the direction that erased the icon: a foreground is transparent across most of its box, and
        // multiplying there must be a no-op rather than a hole.
        val plate = 0xFFFFFFFF.toInt()

        for (mode in LayerBlend.entries) {
            assertEquals("$mode erased the backdrop", plate, blend(plate, src = 0x00000000, mode = mode))
        }
    }

    @Test
    fun `a blended layer over nothing still shows, rather than vanishing for want of a backdrop`() {
        // The other half: the bottom of a stack has an empty destination, and a blend mode there must not mean the
        // layer disappears. `PorterDuff.Mode.MULTIPLY` would have given an alpha of zero.
        val red = 0xFFCC3333.toInt()

        assertEquals(red, blend(dst = 0x00000000, src = red, mode = LayerBlend.MULTIPLY))
        assertEquals(red, blend(dst = 0x00000000, src = red, mode = LayerBlend.SCREEN))
    }

    @Test
    fun `normal is plain source-over, so it needs no special case anywhere`() {
        val opaque = 0xFF204080.toInt()

        assertEquals(opaque, blend(dst = 0xFFFFFFFF.toInt(), src = opaque, mode = LayerBlend.NORMAL))
    }

    @Test
    fun `multiply is the product of two opaque colours`() {
        // 0x80 × 0x40 / 255 ≈ 0x20, per channel.
        val result = blend(dst = 0xFF808080.toInt(), src = 0xFF404040.toInt(), mode = LayerBlend.MULTIPLY)

        assertEquals(0xFF, channels(result)[0])
        for (channel in channels(result).drop(1)) {
            assertEquals(0x20.toFloat(), channel.toFloat(), 2f)
        }
    }

    @Test
    fun `multiplying by white changes nothing, which is what makes it a multiply`() {
        val colour = 0xFF3366CC.toInt()

        assertEquals(colour, blend(dst = colour, src = 0xFFFFFFFF.toInt(), mode = LayerBlend.MULTIPLY))
    }

    @Test
    fun `screening with white produces white`() {
        val colour = 0xFF3366CC.toInt()

        assertEquals(0xFFFFFFFF.toInt(), blend(dst = colour, src = 0xFFFFFFFF.toInt(), mode = LayerBlend.SCREEN))
    }

    @Test
    fun `darken and lighten pick a side per channel`() {
        val backdrop = 0xFF20C020.toInt()
        val source = 0xFFC020C0.toInt()

        assertEquals(0xFF202020.toInt(), blend(backdrop, source, LayerBlend.DARKEN))
        assertEquals(0xFFC0C0C0.toInt(), blend(backdrop, source, LayerBlend.LIGHTEN))
    }

    @Test
    fun `opacity scales how much of the blend arrives`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()

        val half = blend(dst = white, src = black, mode = LayerBlend.MULTIPLY, opacity = 0.5f)

        // Half of the way from white to the fully multiplied black.
        for (channel in channels(half).drop(1)) {
            assertEquals(128f, channel.toFloat(), 2f)
        }
        assertEquals(0xFF, channels(half)[0])
    }

    @Test
    fun `no opacity is the layer not joining at all`() {
        val plate = 0xFFFFFFFF.toInt()

        assertEquals(plate, blend(dst = plate, src = 0xFF000000.toInt(), mode = LayerBlend.MULTIPLY, opacity = 0f))
    }

    @Test
    fun `a partially transparent source blends partially and keeps the backdrop's own coverage`() {
        // The antialiased edge of a glyph, which is where an alpha mistake shows first.
        val result = blend(dst = 0xFFFFFFFF.toInt(), src = 0x80000000.toInt(), mode = LayerBlend.MULTIPLY)

        assertEquals("the plate stays fully covered", 0xFF, channels(result)[0])
        for (channel in channels(result).drop(1)) {
            assertTrue("expected a half-darkened white, got $channel", channel in 120..136)
        }
    }
}
