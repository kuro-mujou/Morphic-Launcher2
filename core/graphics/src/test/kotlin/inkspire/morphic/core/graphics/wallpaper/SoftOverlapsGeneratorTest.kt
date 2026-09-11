package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapBlend
import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapLook
import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
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

    /**
     * **Multiply is laid as an opaque color, and it must multiply exactly as the blend would.** The modulate mode it
     * rides on multiplies alpha too, which is how every Multiply bake used to come out translucent; the opaque color
     * is only a fix if modulating by it equals `ground × lerp(1, tone, alpha)`, per channel.
     */
    @Test
    fun `a modulated form multiplies its ground as the multiply blend would`() {
        val tone = 0xFFC9603E.toInt()
        val ground = 0xFF1F3A4D.toInt()
        val alpha = 0.85f
        val ink = SoftOverlapsGenerator.modulated(tone, alpha)
        assertEquals("the ink must be opaque, or it punches the ground", 0xFF, ink ushr 24)

        for (shift in intArrayOf(16, 8, 0)) {
            val s = (tone shr shift and 0xFF) / 255f
            val d = (ground shr shift and 0xFF) / 255f
            val blend = d * (1f - alpha + alpha * s)
            val modulate = d * (ink shr shift and 0xFF) / 255f
            assertEquals("channel at $shift", blend * 255f, modulate * 255f, 1f)
        }
        assertEquals(tone, SoftOverlapsGenerator.modulated(tone, 1f))
        assertEquals(0xFFFFFFFF.toInt(), SoftOverlapsGenerator.modulated(tone, 0f))
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
        // One value is drawn per point whatever the amplitude, so the seeded stream does not shift as the knob
        // moves — a form keeps its character and only its exaggeration changes.
        val gentle = SoftOverlapsGenerator.radii(points = 8, deform = 0.2f, random = Random(3))
        val strong = SoftOverlapsGenerator.radii(points = 8, deform = 0.8f, random = Random(3))
        for (k in gentle.indices) {
            val gentleOffset = gentle[k] - 1f
            val strongOffset = strong[k] - 1f
            assertEquals("point $k turned the other way", 4f, strongOffset / gentleOffset, 1e-3f)
        }
    }

    /**
     * **A form's partner is the form at its own index**, which is what the scrub pairs by and what a misaligned list
     * would get wrong without failing — every form would sail across the frame to a stranger's place. Two seeds at a
     * modest scatter put each form nearer its partner than any other form, which is only true if they share a cell.
     */
    @Test
    fun `a shuffle pairs every form with the one in its own lattice cell`() {
        val params = DesignParams(density = 1f, irregularity = 0.3f)
        val from = SoftOverlapsGenerator.plan(params, seed = 1L)
        val to = SoftOverlapsGenerator.plan(params, seed = 2L)
        assertNotNull(SoftOverlapsGenerator.morph(from, to))
        assertEquals(10, from.forms.size)

        from.forms.forEachIndexed { i, a ->
            val nearest = to.forms.indices.minBy { hypot(to.forms[it].x - a.x, to.forms[it].y - a.y) }
            assertEquals("form $i is nearer another form than its partner", i, nearest)
        }
    }

    @Test
    fun `a moment of the scrub is its two plans, form for form and ring point for ring point`() {
        val params = DesignParams(roundness = 0f)
        val from = SoftOverlapsGenerator.plan(params, seed = 5L)
        val to = SoftOverlapsGenerator.plan(params, seed = 6L)
        val morph = requireNotNull(SoftOverlapsGenerator.morph(from, to))

        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        val middle = morph.at(0.5f)
        middle.forms.forEachIndexed { i, form ->
            val a = from.forms[i]
            val b = to.forms[i]
            assertEquals((a.x + b.x) / 2f, form.x, 1e-6f)
            assertEquals((a.aspect + b.aspect) / 2f, form.aspect, 1e-6f)
            for (k in form.factors.indices) assertEquals((a.factors[k] + b.factors[k]) / 2f, form.factors[k], 1e-6f)
        }
    }

    @Test
    fun `two plans with different forms are refused rather than paired`() {
        val few = SoftOverlapsGenerator.plan(DesignParams(density = 0f), seed = 1L)
        val many = SoftOverlapsGenerator.plan(DesignParams(density = 1f), seed = 1L)
        assertNull(SoftOverlapsGenerator.morph(few, many))
    }
}
