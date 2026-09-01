package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip count, the drift and the ramp's rung placement — this design's own arithmetic, all of which fails
 * *silently*. The projection its strips ride on is tested in [FrameAxisTest] and the ramp's colors in
 * [LinearGradientGeneratorTest]; the shading is judged in the render harness.
 *
 * **The rung placement is the one worth the most.** A cluster that ran off the end of the axis, or positions that
 * stopped ascending, still draws a smooth plausible wallpaper — with a palette stop quietly missing from it, which is
 * not something a render tells you unless you already know which stops you expected to see.
 */
class LouversGeneratorTest {

    @Test
    fun `density maps to the strip count range`() {
        assertEquals(1, LouversGenerator.stripCount(0f))
        assertEquals(30, LouversGenerator.stripCount(1f))
        assertEquals(1, LouversGenerator.stripCount(-1f)) // clamped
        assertEquals(30, LouversGenerator.stripCount(2f)) // clamped
    }

    @Test
    fun `no drift leaves every strip identical`() {
        // The rigid end, and it is the design's whole claim: at drift 0 this is a plain gradient with seams drawn on
        // it. A strip that centered its ramp anywhere else would break that without looking broken.
        val centers = (0 until 8).map { LouversGenerator.centerOf(it, count = 8, drift = 0f) }
        assertTrue("every strip must share one center", centers.all { it == centers.first() })
        assertEquals(0.5f, centers.first(), 1e-6f)
    }

    @Test
    fun `drift slides the ramp evenly, centered on the middle strip`() {
        val centers = (0 until 9).map { LouversGenerator.centerOf(it, count = 9, drift = 1f) }
        assertEquals("the middle strip must stay put", 0.5f, centers[4], 1e-6f)
        assertTrue("the run must ascend", centers.zipWithNext().all { (a, b) -> b > a })
        // Symmetric about the middle, so winding the knob up opens the design out rather than walking it off an end.
        assertEquals(0.5f - centers.first(), centers.last() - 0.5f, 1e-6f)
        assertTrue("and it must not slide the ramp clean off the axis", centers.first() > 0f && centers.last() < 1f)
    }

    @Test
    fun `a single strip centers its ramp whatever the drift`() {
        // With no run of strips there is nothing to interpolate across; dividing by the gap would be a NaN wallpaper.
        assertEquals(0.5f, LouversGenerator.centerOf(0, count = 1, drift = 1f), 1e-6f)
    }

    @Test
    fun `the ramp pins its ends and clusters the rest, ascending, whatever the spread`() {
        for (spread in listOf(0f, 0.25f, 0.5f, 1f)) {
            for (center in listOf(0.1f, 0.5f, 0.9f)) {
                val places = LouversGenerator.rungPlaces(rungs = 5, center = center, spread = spread)
                assertEquals("spread $spread: the first stop is the head of the axis", 0f, places.first(), 1e-6f)
                assertEquals("spread $spread: the last stop is its foot", 1f, places.last(), 1e-6f)
                assertTrue(
                    "spread $spread at $center: the rungs must never descend",
                    places.toList().zipWithNext().all { (a, b) -> b >= a },
                )
            }
        }
    }

    @Test
    fun `no spread collapses the inner stops onto one edge`() {
        // The knob's rigid end: the middle of the palette becomes a single hard boundary, with a long wash either
        // side of it. That washed flank is what a clamped ramp would flatten into a dead block of one color.
        val places = LouversGenerator.rungPlaces(rungs = 5, center = 0.5f, spread = 0f)
        assertEquals(0.5f, places[1], 1e-6f)
        assertEquals(0.5f, places[2], 1e-6f)
        assertEquals(0.5f, places[3], 1e-6f)
    }

    @Test
    fun `full spread reaches the whole axis`() {
        val places = LouversGenerator.rungPlaces(rungs = 5, center = 0.5f, spread = 1f)
        assertEquals("the innermost rungs must open out to the ends", 0f, places[1], 1e-6f)
        assertEquals(1f, places[3], 1e-6f)
    }

    @Test
    fun `a two-stop palette still has an inside for the spread to move`() {
        // The failure that killed Dot Grid and Flowing Blobs at their own defaults: bichromatic is the shipped color
        // mode, so a design whose look comes from a multi-stop ramp has to read one where the palette has none.
        val narrow = LouversGenerator.rungPlaces(rungs = 4, center = 0.5f, spread = 0.2f)
        val wide = LouversGenerator.rungPlaces(rungs = 4, center = 0.5f, spread = 0.8f)
        assertTrue("the spread must still separate them", wide[2] - wide[1] > narrow[2] - narrow[1])
    }

    @Test
    fun `the shadow is off at zero, restrained at the default, and never black`() {
        assertEquals(0f, LouversGenerator.shadowDepth(0f), 1e-6f)
        val default = LouversGenerator.shadowDepth(0.5f)
        assertTrue("the default must be a hint of depth, not a rule", default > 0f && default < 0.1f)
        assertTrue("and the maximum a shading, not an ink line", LouversGenerator.shadowDepth(1f) < 0.3f)
    }

    @Test
    fun `the default direction is the upright one the reference opens on`() {
        // The model's contract is that variant 0 is the design's default look, and theirs opens at rotation 0.
        assertEquals(LouversGenerator.Direction.VERTICAL, LouversGenerator.Direction.entries.first())
        assertEquals(0f, LouversGenerator.Direction.VERTICAL.acrossDegrees, 1e-6f)
    }
}
