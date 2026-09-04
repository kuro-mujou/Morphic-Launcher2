package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gradient's color math — the part that is silently wrong when it is wrong, tested without a bitmap.
 *
 * `BitmapBlur`'s reason: `core:graphics` exists precisely so this kind of arithmetic can be checked off an emulator.
 * A transposed channel tints the whole wallpaper; an off-by-one at a stop boundary bands it. Neither throws.
 */
class LinearGradientGeneratorTest {

    private val violet = 0xFF241B4E.toInt()
    private val sand = 0xFFFFD9A0.toInt()

    @Test
    fun `the ends of the gradient are the first and last stops`() {
        val palette = Palette(listOf(violet, 0xFFB65A78.toInt(), sand))

        assertEquals(violet, LinearGradientGenerator.colorAt(0f, palette))
        assertEquals(sand, LinearGradientGenerator.colorAt(1f, palette))
    }

    @Test
    fun `a stop sits exactly at its own fraction`() {
        // Three stops means the middle one is dead centre — the boundary an off-by-one would miss.
        val mid = 0xFFB65A78.toInt()
        assertEquals(mid, LinearGradientGenerator.colorAt(0.5f, Palette(listOf(violet, mid, sand))))
    }

    @Test
    fun `halfway between two stops is their channel average`() {
        val mid = LinearGradientGenerator.colorAt(0.5f, Palette(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))

        // Black to white: each color channel averages to ~128. Both stops are opaque, so alpha stays 255 rather than
        // averaging — the translucent case is its own test.
        for (shift in intArrayOf(16, 8, 0)) {
            assertEquals(128, (mid shr shift) and 0xFF)
        }
        assertEquals(0xFF, mid ushr 24)
    }

    @Test
    fun `a one-stop palette is that color everywhere`() {
        val palette = Palette(listOf(violet))

        assertEquals(violet, LinearGradientGenerator.colorAt(0f, palette))
        assertEquals(violet, LinearGradientGenerator.colorAt(0.5f, palette))
        assertEquals(violet, LinearGradientGenerator.colorAt(1f, palette))
    }

    @Test
    fun `a fraction outside the frame clamps to an end rather than reading past it`() {
        val palette = Palette(listOf(violet, sand))

        assertEquals(violet, LinearGradientGenerator.colorAt(-1f, palette))
        assertEquals(sand, LinearGradientGenerator.colorAt(2f, palette))
    }

    @Test
    fun `a translucent stop keeps its alpha through the blend`() {
        // A stop may be translucent (Soft Overlaps), so alpha interpolates like any other channel rather than being
        // forced opaque.
        val clear = 0x00FFFFFF
        val opaque = 0xFFFFFFFF.toInt()
        val mid = LinearGradientGenerator.colorAt(0.5f, Palette(listOf(clear, opaque)))

        assertTrue("alpha should be mid, was ${mid ushr 24}", (mid ushr 24) in 126..129)
    }

    /**
     * The design drew a top-to-bottom ramp and nothing else until the quality pass, so `0` has to keep drawing exactly
     * that — a stored recipe carries a rotation of `0` whether or not it ever saw the knob.
     */
    @Test
    fun `an untouched angle is the straight-down ramp this design has always drawn`() {
        assertEquals(90f, LinearGradientGenerator.degreesFor(0f), 1e-6f)
    }

    /**
     * Half a turn, which reaches every *axis* — and knowingly not every direction, since a ramp is directed and its
     * period is the whole circle. See the generator's class note; a full turn makes the knob's ends the same picture,
     * which the knob guard fails on.
     */
    @Test
    fun `the knob sweeps a half turn, reaching every axis`() {
        assertEquals(270f, LinearGradientGenerator.degreesFor(1f), 1e-6f)
        assertEquals(180f, LinearGradientGenerator.degreesFor(0.5f), 1e-6f)
        assertEquals(90f, LinearGradientGenerator.degreesFor(-1f), 1e-6f) // clamped
        assertEquals(270f, LinearGradientGenerator.degreesFor(2f), 1e-6f) // clamped
    }
}
