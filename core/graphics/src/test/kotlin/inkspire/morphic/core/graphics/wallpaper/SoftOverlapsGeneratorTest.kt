package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapBlend
import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The forms' counts, the ring their outlines are built from, and the two choosers.
 *
 * **The ring's rigid end is what this file is really for.** The reference draws an *exact ellipse* at its
 * *Irregularity* `0`, and that is the measurement the whole shape construction was read off — a ring that wandered by
 * even a percent there would still draw a perfectly plausible blob, and no render of ours could show that it is not
 * the design.
 */
class SoftOverlapsGeneratorTest {

    @Test
    fun `density maps to the form count range`() {
        // The reference's own range, and its `1` — one form alone on the ground — is a setting, not a degenerate case.
        assertEquals(1, SoftOverlapsGenerator.blobCount(0f))
        assertEquals(10, SoftOverlapsGenerator.blobCount(1f))
        assertEquals(1, SoftOverlapsGenerator.blobCount(-1f)) // clamped
        assertEquals(10, SoftOverlapsGenerator.blobCount(2f)) // clamped
    }

    @Test
    fun `variant picks a look and finish picks a blend, both clamped`() {
        // Index 0 is the design's default, and theirs opens on Fill over Screen.
        assertEquals(OverlapLook.FILL, SoftOverlapsGenerator.lookOf(0))
        assertEquals(OverlapLook.GLOW, SoftOverlapsGenerator.lookOf(1))
        assertEquals(OverlapLook.GLOW, SoftOverlapsGenerator.lookOf(9)) // clamped at the end
        assertEquals(OverlapLook.FILL, SoftOverlapsGenerator.lookOf(-2)) // and at the start

        assertEquals(OverlapBlend.SCREEN, SoftOverlapsGenerator.blendOf(0))
        assertEquals(OverlapBlend.OVERLAY, SoftOverlapsGenerator.blendOf(9)) // clamped at the end
        assertEquals(OverlapBlend.SCREEN, SoftOverlapsGenerator.blendOf(-2)) // and at the start
    }

    @Test
    fun `only Normal paints over — every other blend carries a mode`() {
        // A blend that silently resolved to null would draw the design's default as its plainest look, which is the
        // one failure this enum can have and a render cannot show.
        assertNull("Normal is the absence of a blend, not a mode", OverlapBlend.NORMAL.mode)
        for (blend in OverlapBlend.entries - OverlapBlend.NORMAL) {
            assertNotNull("$blend must carry a porter-duff mode", blend.mode)
        }
    }

    @Test
    fun `a ring with no deformation is an exact ellipse`() {
        val factors = SoftOverlapsGenerator.radii(points = 8, deform = 0f, random = Random(7))
        assertEquals(8, factors.size)
        // The reference's rigid end: at its Irregularity 0 every form is a plain ellipse, so every factor is exactly 1.
        assertTrue("a factor left the ellipse", factors.all { abs(it - 1f) < 1e-6f })
    }

    @Test
    fun `a deformed ring stays inside its bounds and does not collapse`() {
        for (deform in floatArrayOf(0.1f, 0.5f, 1f)) {
            val factors = SoftOverlapsGenerator.radii(points = 12, deform = deform, random = Random(11))
            assertEquals(12, factors.size)
            assertTrue(
                "deform $deform: a factor left 1 ± deform",
                factors.all { it >= 1f - deform - 1e-6f && it <= 1f + deform + 1e-6f },
            )
        }
    }

    @Test
    fun `the deformation knob changes how far the ring wanders, never which way`() {
        // Two values are drawn per point whatever the amplitude, so the seeded stream does not shift as the knob
        // moves — a form keeps its character and only its exaggeration changes.
        val gentle = SoftOverlapsGenerator.radii(points = 8, deform = 0.2f, random = Random(3))
        val strong = SoftOverlapsGenerator.radii(points = 8, deform = 0.8f, random = Random(3))
        for (k in gentle.indices) {
            val gentleOffset = gentle[k] - 1f
            val strongOffset = strong[k] - 1f
            assertEquals("point $k turned the other way", 4f, strongOffset / gentleOffset, 1e-3f)
        }
    }
}
