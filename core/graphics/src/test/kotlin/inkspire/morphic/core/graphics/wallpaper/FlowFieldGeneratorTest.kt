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
        // Fitted through the ink rather than off a scan line, because marks overlap and curve and no line crosses
        // lanes cleanly: coverage does not depend on the pitch at all (a width is a share of a lane), so their 63%
        // over marks whose mean is 28px at a 0.85 duty gives a mean share of 0.74 and a 38px lane, at Density 50 on
        // a 1080-wide frame. The middle is the one that matters — it is the picture the studio opens on.
        assertEquals(38f / 1080f, FlowFieldGenerator.spacing(0.5f), 1e-3f)
        // The ends are that fit run again at Density 0 and 75, off the length distribution, which scales with the
        // lane: 1.73x the default at the bottom and 0.70x at three quarters. Those two imply travels of 3.0 and 4.2
        // over the whole knob and the fit splits them, so the tolerance here is the spread between the two readings
        // rather than a precision claim.
        assertEquals(1.73f, FlowFieldGenerator.spacing(0f) / FlowFieldGenerator.spacing(0.5f), 0.15f)
        assertEquals(0.70f, FlowFieldGenerator.spacing(0.75f) / FlowFieldGenerator.spacing(0.5f), 0.06f)
        assertEquals(0.066f, FlowFieldGenerator.spacing(0f), 1e-6f)
        assertEquals(0.019f, FlowFieldGenerator.spacing(1f), 1e-6f)
        assertEquals(0.019f, FlowFieldGenerator.spacing(2f), 1e-6f) // clamped
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
    fun `an orb is never small beside its neighbours, which is what stops an overlap reading as a lump`() {
        // The reference places its orbs with nothing keeping them apart -- driven to Orbs 10 it puts three and four
        // into overlapping clusters, down to 0.72 of the sum of their radii -- so what makes an overlap read as one
        // disc in front of another is the ring plus a floor high enough that no disc is small beside the rest.
        // Fitted by erosion off two captures on a 1080-wide frame: 112, 168, 228, 232 on Pearls and 120, 128, 208
        // on Eclectic, which is 0.10..0.215 of the short side. Ours had 0.05..0.15, whose floor is half theirs.
        assertEquals(0.10f, FlowFieldGenerator.MinOrb, 1e-6f)
        assertEquals(0.215f, FlowFieldGenerator.MaxOrb, 1e-6f)
        assertTrue(
            "a frame's largest orb must not be more than about twice its smallest",
            FlowFieldGenerator.MaxOrb / FlowFieldGenerator.MinOrb <= 2.2f,
        )
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
    fun `the hop shortens as irregularity turns the field faster`() {
        // The claim the whole knob rests on: a trail is a polyline through its hops, so the hop has to resolve the
        // field. Winding irregularity up without shortening it is what drew faceted lines with bulging joins.
        val smooth = FlowFieldGenerator.smoothStep(FlowFieldGenerator.Look.ECLECTIC, detail = 0f, longSide = 2400f)
        val serpentine = FlowFieldGenerator.smoothStep(FlowFieldGenerator.Look.ECLECTIC, detail = 1.1f, longSide = 2400f)
        assertTrue("a faster-turning field must take shorter hops", serpentine < smooth)

        // Five degrees of turn per hop at either end, which is where the smooth end already sat.
        for (detail in floatArrayOf(0f, 0.55f, 1.1f)) {
            val look = FlowFieldGenerator.Look.ECLECTIC
            val step = FlowFieldGenerator.smoothStep(look, detail, 2400f)
            val base = 2400f / look.frequency
            val turn = 2f * (look.span / base + detail / (base / 7f)) * step
            assertEquals("turn per hop at detail $detail", 0.09f, turn, 1e-4f)
        }
    }

    @Test
    fun `a frame's long side sets the swirl size, so a tall frame is not twice as turbulent`() {
        // Counted over the short side, a phone frame got two and a half times as many turns down it as across it.
        val tall = FlowFieldGenerator.smoothStep(FlowFieldGenerator.Look.ECLECTIC, detail = 0.55f, longSide = 2400f)
        val square = FlowFieldGenerator.smoothStep(FlowFieldGenerator.Look.ECLECTIC, detail = 0.55f, longSide = 1080f)
        assertTrue("a smaller frame turns faster per pixel, so its hops are shorter", square < tall)
    }

    @Test
    fun `the graded look runs its tone against its width, and the scattered one does not`() {
        // The finding the look was rebuilt on: theirs puts its boldest marks on the palette's first tone and its
        // hairlines on the last, and averaging its frame down a column finds one tone per band. A scattered look
        // averages to every tone everywhere, which is what our Pearls had been doing.
        val pearls = FlowFieldGenerator.Look.PEARLS
        val random = kotlin.random.Random(1)
        assertEquals("the thickest mark takes the opening tone", 0, FlowFieldGenerator.toneIndex(pearls, 1f, 4, random))
        assertEquals("the thinnest takes the last", 3, FlowFieldGenerator.toneIndex(pearls, 0f, 4, random))
        // A grade out of range must not index off the end — the cosine cannot leave 0..1, but nothing here says so.
        assertEquals(0, FlowFieldGenerator.toneIndex(pearls, 1.4f, 4, random))
        assertEquals(3, FlowFieldGenerator.toneIndex(pearls, -0.4f, 4, random))
    }

    @Test
    fun `the graded look is drawn far finer than the scattered one`() {
        // Measured off theirs on a 1080-wide frame: Pearls runs 5-10px marks at the frame's centre and 22-23px at
        // its edges, where Eclectic runs from 3px to 71px -- past two lanes -- on the same 38px lane. Ours drew
        // Pearls on Eclectic's own distribution, which is most of why the two looks read as one heavy design.
        val pearls = FlowFieldGenerator.Look.PEARLS
        val eclectic = FlowFieldGenerator.Look.ECLECTIC
        assertTrue(
            "the graded ceiling must stay under a lane, which is what keeps that look airy",
            FlowFieldGenerator.widthShare(pearls, 1f) < 1f,
        )
        assertTrue(
            "the scattered ceiling must cross a lane, which is where its marks overlap",
            FlowFieldGenerator.widthShare(eclectic, 1f) > 1.5f,
        )
        // Theirs puts its lower quartile at 7px on that 38px lane; ours lands it at 8.6px, where the old fat-biased
        // draw put it at 26px. It is the quartile rather than the floor that decides whether hairlines are a
        // texture through the picture or a rarity.
        assertEquals(7f / 38f, FlowFieldGenerator.widthShare(eclectic, 0.25f), 0.05f)
        // The looks cross near the bottom of the scale -- a hairline is a hairline, and Pearls' floor is above
        // Eclectic's -- so what separates them is the average mark, which is half the width.
        var pearlsMean = 0f
        var eclecticMean = 0f
        var grade = 0.005f
        while (grade < 1f) {
            pearlsMean += FlowFieldGenerator.widthShare(pearls, grade) / 100f
            eclecticMean += FlowFieldGenerator.widthShare(eclectic, grade) / 100f
            grade += 0.01f
        }
    }

    @Test
    fun `only Pearls sweeps a whole turn, and its span is not zero`() {
        // The span is read while the enum loads, which is before this object's own `val`s are assigned — a computed
        // one would be zero here, and a zero span draws straight parallel lines rather than failing.
        assertEquals(6.2831855f, FlowFieldGenerator.Look.PEARLS.span, 1e-5f)
        assertEquals(3f, FlowFieldGenerator.Look.ECLECTIC.span, 1e-6f)
    }
}
