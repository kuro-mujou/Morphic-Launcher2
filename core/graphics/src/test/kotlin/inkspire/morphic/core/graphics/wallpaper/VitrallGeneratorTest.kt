package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subdivision — the part a bitmap cannot check.
 *
 * The panes must still sum to the frame, or some of the glass has quietly gone missing; they must land near the count
 * the slider promises; and *Curves* has to be a knob about curves, since nothing else in the render says so. Cutting
 * one pane in two is [GlassCutTest]'s.
 */
class VitrallGeneratorTest {

    @Test
    fun `the panes still tile the frame, whatever it was cut into`() {
        // Swept over curves as well as count, because a bowed cut is where tiling is *hard*: a bitten pane is no
        // longer convex, and a later straight cut of one is the case a half-plane clip has no right to survive.
        for (count in listOf(12, 60, 160)) {
            for (curves in listOf(0f, 0.5f, 1f)) {
                val panes = VitrallGenerator.panes(count, curves, seed = 4L, aspect = 0.45f).panes
                val total = panes.sumOf { GlassCut.area(it).toDouble() }
                assertEquals("$count panes at curves=$curves must add up to the window", 0.45, total, 1e-3)
            }
        }
    }

    @Test
    fun `at no curves every cut is straight, and at full curves the window is tracery`() {
        // The knob is *Curves*, so the evidence is vertex counts: a straight subdivision of a rectangle cannot give
        // a pane more corners than the cuts through it, where a sampled arc gives it dozens.
        val straight = VitrallGenerator.panes(60, curves = 0f, seed = 4L, aspect = 0.45f).panes
        val bowed = VitrallGenerator.panes(60, curves = 1f, seed = 4L, aspect = 0.45f).panes
        assertTrue("no cut may curve at 0", straight.maxOf { it.size } <= 20)
        assertTrue("at 1 the panes must carry sampled arcs", bowed.maxOf { it.size } > 40)
    }

    @Test
    fun `the pane count lands near the number the slider shows`() {
        // The subdivision recurses on *area* and then glazes some panes into strips, so the count is a target rather
        // than a promise. It still has to be the right size, or the slider is lying about what it does.
        for (count in listOf(12, 60, 160)) {
            val panes = VitrallGenerator.panes(count, curves = 0.5f, seed = 4L).panes.size
            assertTrue("$count asked for, $panes cut", panes in (count * 2 / 3)..(count * 3 / 2))
        }
    }

    @Test
    fun `pane sizes spread, so the window is not a honeycomb`() {
        // The log-uniform stopping area per branch is what does this; splitting the biggest every time would not.
        val areas = VitrallGenerator.panes(80, curves = 0.5f, seed = 4L).panes.map { GlassCut.area(it) }
        assertTrue("the largest pane must dwarf the smallest", areas.max() > areas.min() * 8f)
    }

    @Test
    fun `the first cuts are kept as bones`() {
        val window = VitrallGenerator.panes(80, curves = 0.5f, seed = 4L)
        assertTrue("a window with no structural bars reads as flat crazing", window.bones.size >= 3)
        // Two points for a straight cut, a sampled chain for a bowed one — either way an even count of coordinates.
        assertTrue("a bone is a polyline", window.bones.all { it.size >= 4 && it.size % 2 == 0 })
    }

    @Test
    fun `every pane has three corners`() {
        assertTrue(VitrallGenerator.panes(160, curves = 1f, seed = 9L).panes.all { it.size >= 6 })
    }

    @Test
    fun `the same seed cuts the same window, so a recipe reproduces`() {
        val first = VitrallGenerator.panes(30, curves = 0.5f, seed = 12L).panes
        val again = VitrallGenerator.panes(30, curves = 0.5f, seed = 12L).panes
        assertEquals(first.size, again.size)
        assertTrue(first.indices.all { first[it].contentEquals(again[it]) })
    }

    @Test
    fun `a different seed cuts a different window`() {
        val a = VitrallGenerator.panes(30, curves = 0.5f, seed = 1L).panes
        val b = VitrallGenerator.panes(30, curves = 0.5f, seed = 2L).panes
        assertTrue(a.size != b.size || a.indices.any { !a[it].contentEquals(b[it]) })
    }

    /**
     * **The property the plan/draw split exists for**, and the one nothing else would catch: a plan is valid at every
     * frame size of its shape, so a scrub can redraw at whatever resolution it can afford without re-cutting the
     * window. Break it — by reading a pixel size anywhere in `plan` — and the failure is not an exception but a
     * window that reshuffles itself the moment the preview changes size.
     */
    @Test
    fun `a plan is the same window at every size of one shape`() {
        val full = VitrallGenerator.plan(1080, 2400, params, seed = 7L)
        val draft = VitrallGenerator.plan(135, 300, params, seed = 7L)

        assertEquals(full.aspect, draft.aspect, 1e-6f)
        assertEquals(full.panes.size, draft.panes.size)
        assertTrue(
            full.panes.indices.all {
                full.panes[it].outline.contentEquals(draft.panes[it].outline) &&
                    full.panes[it].tone == draft.panes[it].tone &&
                    full.panes[it].angle == draft.panes[it].angle &&
                    full.panes[it].lift == draft.panes[it].lift
            },
        )
    }

    /**
     * A pane carries a **position on the ramp**, never a color, so the same plan can be drawn in any palette and two
     * windows' tones can be interpolated without their palettes agreeing. Off the ramp the draw clamps silently and
     * the window grows flat runs of one end stop.
     */
    @Test
    fun `every pane's tone is a ramp position`() {
        val plan = VitrallGenerator.plan(1080, 2400, params, seed = 3L)
        assertTrue(plan.panes.all { it.tone in 0f..1f })
    }

    /** The knobs reach the plan unresolved, so they can be interpolated — and out of range they clamp, not wrap. */
    @Test
    fun `glass and leading arrive as the knobs give them`() {
        val plan = VitrallGenerator.plan(1080, 2400, params.copy(depth = 0.3f, scale = 0.8f), seed = 5L)
        assertEquals(0.3f, plan.glass, 1e-6f)
        assertEquals(0.8f, plan.leading, 1e-6f)

        val past = VitrallGenerator.plan(1080, 2400, params.copy(depth = 2f, scale = -1f), seed = 5L)
        assertEquals(1f, past.glass, 1e-6f)
        assertEquals(0f, past.leading, 1e-6f)
    }

    /**
     * A fingerprint of the whole glazing stream, pinned.
     *
     * **This guards the one thing in `plan` that fails silently: the order the four per-pane draws are taken in.**
     * Tone, flash, angle and lift come off one `Random` in a fixed sequence, and re-ordering them — or hoisting one
     * out of the loop — re-glazes every pane after the change while leaving the geometry, the pane count, the tone
     * range and every other property here untouched. The window simply becomes a *different* window at the same seed,
     * which is a broken promise to anyone who saved that seed.
     *
     * A fingerprint rather than a table of floats because the number itself carries no meaning: what is being asserted
     * is only that the stream has not moved. If this fails and the change was deliberate, re-record it — but re-render
     * the design first and look at it, because every stored recipe now draws something else.
     */
    @Test
    fun `the glazing stream is drawn in a fixed order`() {
        val plan = VitrallGenerator.plan(1080, 2400, params, seed = 11L)
        var fingerprint = 17
        for (pane in plan.panes) {
            fingerprint = fingerprint * 31 + pane.tone.toRawBits()
            fingerprint = fingerprint * 31 + if (pane.flashed) 1 else 0
            fingerprint = fingerprint * 31 + pane.angle.toRawBits()
            fingerprint = fingerprint * 31 + pane.lift.toRawBits()
        }
        assertEquals(GlazingFingerprint, fingerprint)
    }

    /**
     * **Both ends of a scrub are the real plans, not re-cuts of them.** Re-cutting reproduces them, but the plans are
     * already in hand, and taking them removes any question of whether a scrub lands on the window a bake would draw.
     */
    @Test
    fun `a morph begins and ends on the plans themselves`() {
        val from = VitrallGenerator.plan(1080, 2400, params, seed = 1L)
        val to = VitrallGenerator.plan(1080, 2400, params, seed = 2L)
        val morph = VitrallGenerator.morph(from, to)

        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        assertSame(from, morph.at(-0.5f))
        assertSame(to, morph.at(2f))
    }

    /**
     * **The frame is still exactly divided at every moment of a scrub, and this is the assertion the whole design
     * exists to make true.**
     *
     * It is what the morph this replaced could not do. Pairing panes off and interpolating each against its partner
     * is reasonable until you notice that two panes are neighbours *because one cut made both*: partners chosen pane
     * by pane pull a shared edge in two directions, so the panes part company and the lead opens between them.
     * Driven on the device it went from 18% of the frame to 40% at the middle of a scrub, and the bones — meaningful
     * only as pane boundaries — came off the panes and swept over open ground.
     *
     * Interpolating the *cuts* makes this an invariant rather than a hope: whatever the cuts are at a moment,
     * applying them in order divides the frame, because that is what cutting is. So the check is the plainest one
     * there is, and it holds at every `t` rather than at the two ends where the old one was looked at.
     */
    @Test
    fun `the frame stays whole at every moment of a scrub`() {
        val from = VitrallGenerator.plan(1080, 2400, params, seed = 1L)
        val to = VitrallGenerator.plan(1080, 2400, params.copy(density = 0.9f, irregularity = 0.8f), seed = 2L)
        val morph = VitrallGenerator.morph(from, to)

        for (step in 0..20) {
            val t = step / 20f
            val covered = morph.at(t).panes.sumOf { GlassCut.area(it.outline).toDouble() }
            assertEquals("the window has come apart at t=$t", from.aspect.toDouble(), covered, 1e-3)
        }
    }

    /** A moment of a scrub still has to be a window somebody can draw, not merely one that adds up. */
    @Test
    fun `every moment is drawable`() {
        val from = VitrallGenerator.plan(1080, 2400, params, seed = 3L)
        val to = VitrallGenerator.plan(1080, 2400, params, seed = 4L)
        val middle = VitrallGenerator.morph(from, to).at(0.5f)

        assertTrue("a pane needs three corners", middle.panes.all { it.outline.size >= 6 })
        assertTrue(
            "an interpolated coordinate must be finite",
            middle.panes.all { pane -> pane.outline.all { it.isFinite() } },
        )
        assertTrue("a bone is a polyline", middle.bones.all { it.size >= 4 && it.size % 2 == 0 })
    }

    /**
     * A hair into the scrub the window is still the one it started from, and a hair before the end it is already the
     * one it is going to.
     *
     * **Counted in panes that are actually there**, which is the point: a window whose counterpart cuts where it does
     * not gains those cuts flattened out past the frame's edge, so at its own end they carve off nothing and the
     * panes they would make have no area yet. Cutting at `0.001` rather than at `0` is what makes this a statement
     * about the re-cut window rather than about the plan handed back unchanged.
     */
    @Test
    fun `each end of a scrub re-cuts the window it belongs to`() {
        val from = VitrallGenerator.plan(1080, 2400, params, seed = 5L)
        val to = VitrallGenerator.plan(1080, 2400, params.copy(density = 0.8f), seed = 6L)
        val morph = VitrallGenerator.morph(from, to)
        val sliver = from.aspect * 1e-5f

        val opening = morph.at(0.001f).panes.count { GlassCut.area(it.outline) > sliver }
        val closing = morph.at(0.999f).panes.count { GlassCut.area(it.outline) > sliver }
        assertEquals(from.panes.size, opening)
        assertEquals(to.panes.size, closing)
    }

    /** A scrub asks for the same `t` again whenever a finger holds still, and two answers would read as a flicker. */
    @Test
    fun `one moment of a morph is always the same moment`() {
        val from = VitrallGenerator.plan(1080, 2400, params, seed = 6L)
        val to = VitrallGenerator.plan(1080, 2400, params, seed = 7L)
        val morph = VitrallGenerator.morph(from, to)

        val once = morph.at(0.37f)
        val again = morph.at(0.37f)
        assertEquals(once.panes.size, again.panes.size)
        assertTrue(once.panes.indices.all { once.panes[it].outline.contentEquals(again.panes[it].outline) })
    }

    /** The knobs interpolate too, so a morph carries the glass and the leading across rather than snapping them. */
    @Test
    fun `the knobs cross with the geometry`() {
        val from = VitrallGenerator.plan(1080, 2400, params.copy(depth = 0f, scale = 0f), seed = 8L)
        val to = VitrallGenerator.plan(1080, 2400, params.copy(depth = 1f, scale = 1f), seed = 9L)
        val middle = VitrallGenerator.morph(from, to).at(0.5f)

        assertEquals(0.5f, middle.glass, 1e-5f)
        assertEquals(0.5f, middle.leading, 1e-5f)
    }


    private val params = DesignParams()

    private companion object {
        /** Recorded 2026-09-10 against the render this split was proved byte-identical to. */
        const val GlazingFingerprint = 611271883
    }
}
