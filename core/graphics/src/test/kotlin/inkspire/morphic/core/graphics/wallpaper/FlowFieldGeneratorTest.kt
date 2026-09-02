package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four knob mappings, and the three of them whose ends or default land on a number measured off something else —
 * the reference's own two pitches, its ten orbs, and the width the separation implies on its own.
 *
 * The trail growth itself is not tested here: its rule is a *spatial* one over hundreds of interacting trails, and
 * what it produces is a picture rather than a number. The render harness is where it is judged — which is where the
 * first build's saturating density showed up, and nothing in this file could have caught it.
 */
class FlowFieldGeneratorTest {

    @Test
    fun `density sets the separation, and its middle is the pitch their untouched slider draws`() {
        // Measured off theirs on a 1080-wide frame: a 125px pitch wound all the way down, 35px near the top, and 55px
        // where they ship it. The middle is the one that matters — it is the picture the studio opens on.
        assertEquals(55f / 1080f, FlowFieldGenerator.spacing(0.5f), 1e-3f)
        assertEquals(125f / 1080f, FlowFieldGenerator.spacing(0f), 3e-3f)
        assertTrue(
            "the tight end must leave headroom past their 35px",
            FlowFieldGenerator.spacing(1f) < 35f / 1080f,
        )
        assertEquals(0.115f, FlowFieldGenerator.spacing(0f), 1e-6f)
        assertEquals(0.023f, FlowFieldGenerator.spacing(1f), 1e-6f)
        assertEquals(0.023f, FlowFieldGenerator.spacing(2f), 1e-6f) // clamped
    }

    @Test
    fun `the spacing tightens over the whole knob, so no stretch of it is dead`() {
        var previous = FlowFieldGenerator.spacing(0f)
        var density = 0.05f
        while (density <= 1f) {
            val spacing = FlowFieldGenerator.spacing(density)
            assertTrue("the spacing stopped tightening at $density", spacing < previous)
            previous = spacing
            density += 0.05f
        }
    }

    @Test
    fun `thickness is a multiplier whose default is exactly one`() {
        // The point of the whole scale: at the default the mark is whatever width its lane implies, and the knob
        // reads as a departure from that in either direction. A default that merely landed near 1 would make
        // *Density* and *Thickness* disagree about the shipped look by a few percent, invisibly.
        assertEquals(1f, FlowFieldGenerator.thicknessScale(0.5f), 1e-6f)
        assertEquals(0.4f, FlowFieldGenerator.thicknessScale(0f), 1e-6f)
        assertEquals(2.5f, FlowFieldGenerator.thicknessScale(1f), 1e-6f)
        assertEquals(2.5f, FlowFieldGenerator.thicknessScale(2f), 1e-6f) // clamped
    }

    @Test
    fun `thickness moves the ink over the whole knob`() {
        var previous = FlowFieldGenerator.thicknessScale(0f)
        var scale = 0.05f
        while (scale <= 1f) {
            val thickness = FlowFieldGenerator.thicknessScale(scale)
            assertTrue("the mark stopped widening at $scale", thickness > previous)
            previous = thickness
            scale += 0.05f
        }
    }

    @Test
    fun `irregularity zero is a perfectly smooth field`() {
        // `0` means rigid on this knob everywhere in the studio, which is the one thing a geometric scale could not
        // express — hence the linear one here, unlike its two neighbours.
        assertEquals(0f, FlowFieldGenerator.detailSpan(0f), 1e-6f)
        assertEquals(0.55f, FlowFieldGenerator.detailSpan(0.5f), 1e-6f)
        assertEquals(1.1f, FlowFieldGenerator.detailSpan(1f), 1e-6f)
        assertEquals(0f, FlowFieldGenerator.detailSpan(-1f), 1e-6f) // clamped
    }

    @Test
    fun `depth zero is a sky with no orbs, and the top is the reference's ten`() {
        assertEquals(0, FlowFieldGenerator.orbCount(0f))
        assertEquals(5, FlowFieldGenerator.orbCount(0.5f))
        assertEquals(10, FlowFieldGenerator.orbCount(1f))
        assertEquals(10, FlowFieldGenerator.orbCount(2f)) // clamped
    }

    @Test
    fun `orb size zero draws no orb at all, and the default is the shipped radius`() {
        // The knob has to reach the same picture Orbs `0` reaches, which is the one claim a radius scale can get
        // wrong without looking wrong — a floor of some small disc reads as a design choice rather than a bug.
        assertEquals(0f, FlowFieldGenerator.orbScale(0f), 1e-6f)
        assertEquals(1f, FlowFieldGenerator.orbScale(0.5f), 1e-6f)
        assertEquals(2f, FlowFieldGenerator.orbScale(1f), 1e-6f)
        assertEquals(0f, FlowFieldGenerator.orbScale(-1f), 1e-6f) // clamped
    }

    @Test
    fun `dots runs from every line stroked to every line beaded, defaulting to gart's one in six`() {
        assertEquals(0f, FlowFieldGenerator.beadedShare(0f), 1e-6f)
        assertEquals(1f / 6f, FlowFieldGenerator.beadedShare(0.5f), 0.01f)
        assertEquals(1f, FlowFieldGenerator.beadedShare(1f), 1e-6f)
        assertEquals(1f, FlowFieldGenerator.beadedShare(2f), 1e-6f) // clamped
    }

    @Test
    fun `Dots is offered for Pearls and withheld from Eclectic`() {
        // "Absent, not disabled": Eclectic has no beads, so the knob must not appear beside its working ones.
        assertEquals("Dots", FlowFieldGenerator.styleFor(1).roundness)
        assertEquals(null, FlowFieldGenerator.styleFor(0).roundness)
        assertEquals("Dots", FlowFieldGenerator.styleFor(7).roundness) // clamped to the last look, Pearls
        assertEquals("Orb size", FlowFieldGenerator.styleFor(0).depthScale)
    }

    @Test
    fun `only Pearls sweeps a whole turn, and its span is not zero`() {
        // The span is read while the enum loads, which is before this object's own `val`s are assigned — a computed
        // one would be zero here, and a zero span draws straight parallel lines rather than failing.
        assertEquals(6.2831855f, FlowFieldGenerator.Look.PEARLS.span, 1e-5f)
        assertEquals(3f, FlowFieldGenerator.Look.ECLECTIC.span, 1e-6f)
    }
}
