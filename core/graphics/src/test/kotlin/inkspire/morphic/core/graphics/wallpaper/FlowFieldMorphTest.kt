package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Mark
import inkspire.morphic.core.graphics.wallpaper.FlowFieldGenerator.Orb
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flow field's scrub. What fails silently here is the pairing: a mark paired with one across the frame still
 * bends smoothly into it, and a pairing that finds too few partners still scrubs — the frame just fades out and back
 * in, which reads as a dissolve with extra steps.
 */
class FlowFieldMorphTest {

    private val palette = Palette(listOf(0xFFF2E2C4.toInt(), 0xFFC9603E.toInt(), 0xFF2C6E6B.toInt(), 0xFF121E2B.toInt()))

    /** **A moment holds every mark of both ends exactly once** — the paired ones, and each end's unpaired ones fading. */
    @Test
    fun `a moment holds every mark of both ends once`() {
        val a = FlowFieldGenerator.plan(540, 1200, palette, DesignParams(), seed = 1L)
        val b = FlowFieldGenerator.plan(540, 1200, palette, DesignParams(), seed = 2L)
        val morph = FlowFieldMorph(a, b)
        val (paired, leaving, arriving) = morph.counts
        assertEquals(a.items.count { it is Mark }, paired + leaving)
        assertEquals(b.items.count { it is Mark }, paired + arriving)

        val moment = morph.at(0.4f)
        assertEquals(paired + leaving + arriving, moment.count { it is Mark })
        assertEquals(a.items.count { it is Orb }, moment.count { it is Orb && it.opacity > 0.5f })
        assertTrue("drawn in depth order", moment.zipWithNext().all { (p, q) -> p.depth <= q.depth })
    }

    /**
     * **Most marks find a partner**, since two fields packed at one spacing hold marks within a lane or two of each
     * other almost everywhere — so the scrub bends the picture rather than fading most of it out and in.
     */
    @Test
    fun `most marks find a partner within reach`() {
        for (variant in 0..1) {
            val params = DesignParams(variant = variant)
            val a = FlowFieldGenerator.plan(540, 1200, palette, params, seed = 3L)
            val b = FlowFieldGenerator.plan(540, 1200, palette, params, seed = 4L)
            val (paired, leaving, arriving) = FlowFieldMorph(a, b).counts
            val share = paired.toFloat() / minOf(paired + leaving, paired + arriving)
            assertTrue("look $variant paired only $share of its marks", share > 0.8f)
        }
    }
}
