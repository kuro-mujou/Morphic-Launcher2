package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the contours sit, how wide they are drawn and which one is picked out — the arithmetic behind a map that
 * would still look like a plausible map when it is wrong, so none of it can be checked by eye.
 */
class ContourGeneratorTest {

    @Test
    fun `levels sit inside the coverage window, never on its edges`() {
        val heights = ContourGenerator.levelHeights(levels = 4, coverage = 1f)
        assertEquals(4, heights.size)
        assertTrue("first level is above the floor", heights.first() > 0f)
        assertTrue("last level is below the ceiling", heights.last() < 1f)
    }

    @Test
    fun `levels stay ascending and evenly spaced`() {
        val heights = ContourGenerator.levelHeights(levels = 5, coverage = 0.5f)
        val gaps = heights.toList().zipWithNext { low, high -> high - low }
        gaps.forEach { assertEquals(gaps.first(), it, 1e-5f) }
        assertTrue("ascending", gaps.all { it > 0f })
    }

    @Test
    fun `coverage narrows the window around the middle of the relief`() {
        val wide = ContourGenerator.levelHeights(levels = 4, coverage = 1f)
        val narrow = ContourGenerator.levelHeights(levels = 4, coverage = 0f)
        val wideSpan = wide.last() - wide.first()
        val narrowSpan = narrow.last() - narrow.first()
        assertTrue("narrow coverage packs the levels", narrowSpan < wideSpan * 0.25f)
        // Centered, so the packed levels sit around the middle rather than drifting to one end.
        assertEquals(0.5f, (narrow.first() + narrow.last()) / 2f, 1e-5f)
        assertEquals(0.5f, (wide.first() + wide.last()) / 2f, 1e-5f)
    }

    @Test
    fun `a band is the count of levels at or below the height`() {
        val heights = floatArrayOf(0.25f, 0.5f, 0.75f)
        assertEquals(0, ContourGenerator.bandAt(0.1f, heights))
        assertEquals(1, ContourGenerator.bandAt(0.3f, heights))
        assertEquals(2, ContourGenerator.bandAt(0.6f, heights))
        // Above every level is a band of its own — the peaks — not the top level's band.
        assertEquals(3, ContourGenerator.bandAt(0.9f, heights))
        assertEquals(1, ContourGenerator.bandAt(0.25f, heights)) // exactly on a level counts as above it
    }

    @Test
    fun `the highlight runs from none to the top level`() {
        assertEquals(-1, ContourGenerator.highlightedLevel(depth = 0f, levels = 4))
        assertEquals(3, ContourGenerator.highlightedLevel(depth = 1f, levels = 4))
        // Every answer in between is a level that exists.
        for (step in 0..10) {
            val level = ContourGenerator.highlightedLevel(step / 10f, levels = 4)
            assertTrue("level $level is drawable", level in -1..3)
        }
    }

    @Test
    fun `thickness spans the reference's own measured widths`() {
        // Measured off the reference on a 1080-wide frame: Thickness 2 draws about 1.5px and 100 about 17px.
        assertEquals(1.5f, ContourGenerator.strokeWidthPx(0f, 1080f), 0.1f)
        assertEquals(16.9f, ContourGenerator.strokeWidthPx(1f, 1080f), 0.2f)
        // And it is a share of the frame, so the same recipe reads the same on a tablet.
        assertEquals(
            ContourGenerator.strokeWidthPx(0.5f, 1080f) * 2f,
            ContourGenerator.strokeWidthPx(0.5f, 2160f),
            1e-3f,
        )
    }

    @Test
    fun `the panel's universal half lands near the reference's own defaults`() {
        // Every knob of every design still opens at 0.5, so each response is shaped to put that near their default
        // rather than in the middle of a range they never use. These are the numbers that claim.
        assertEquals(14f, thicknessUnits(0.5f), 1f) // theirs opens at 12 of 2..100
        assertTrue("zoom opens broad", ContourGenerator.frequencyFor(0.5f) < 3f)
        assertTrue("zoom's rigid ends bracket it", ContourGenerator.frequencyFor(1f) < ContourGenerator.frequencyFor(0f))
    }

    /** The reference's own Thickness units, recovered from the pixel width — see `strokeWidthPx`. */
    private fun thicknessUnits(thickness: Float): Float =
        (ContourGenerator.strokeWidthPx(thickness, 1080f) - 1.2f) / 0.157f
}
