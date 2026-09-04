package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.RoundedTilesGenerator.TileBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The bar count, the lanes they sit in, and the blend chooser.
 *
 * **The lanes are what this file is really for.** They decide where a fan opens *from*, and getting them wrong is
 * invisible at the default — a set laid out from one edge looks identical to a centred one while the bars are
 * parallel, and only swings off the frame once the fan is opened, which is a bug that shows in one setting and hides
 * in the one anybody looks at first.
 */
class RoundedTilesGeneratorTest {

    @Test
    fun `density maps to the bar count range`() {
        // The reference's own range, and its `1` — one bar across the whole frame — is a setting, not a degenerate case.
        assertEquals(1, RoundedTilesGenerator.barCount(0f))
        assertEquals(10, RoundedTilesGenerator.barCount(1f))
        assertEquals(1, RoundedTilesGenerator.barCount(-1f)) // clamped
        assertEquals(10, RoundedTilesGenerator.barCount(2f)) // clamped
    }

    @Test
    fun `finish picks a blend, clamped to the two`() {
        // Index 0 is the design's default, and theirs opens on Plus.
        assertEquals(TileBlend.PLUS, RoundedTilesGenerator.blendOf(0))
        assertEquals(TileBlend.NORMAL, RoundedTilesGenerator.blendOf(1))
        assertEquals(TileBlend.NORMAL, RoundedTilesGenerator.blendOf(7)) // clamped at the end
        assertEquals(TileBlend.PLUS, RoundedTilesGenerator.blendOf(-3)) // and at the start
    }

    @Test
    fun `only Normal paints over — Plus carries a mode`() {
        assertNull("Normal is the absence of a blend, not a mode", TileBlend.NORMAL.mode)
        assertNotNull("Plus must carry a porter-duff mode", TileBlend.PLUS.mode)
    }

    @Test
    fun `a single bar sits exactly on the middle`() {
        // Their Count 1 draws one bar across the centre of the frame, not a sliver at its edge.
        val lanes = RoundedTilesGenerator.lanes(1)
        assertEquals(1, lanes.size)
        assertEquals(0f, lanes[0], 1e-6f)
    }

    @Test
    fun `lanes are evenly spaced and centred on the middle whatever the count`() {
        for (count in 1..10) {
            val lanes = RoundedTilesGenerator.lanes(count)
            assertEquals("count $count", count, lanes.size)
            // Centred: the set's own middle is the frame's, which is what the fan turns about. A set counted from one
            // edge looks the same while the bars are parallel and swings off the frame the moment the fan opens.
            val middle = (lanes.first() + lanes.last()) / 2f
            assertTrue("count $count: lanes are not centred on zero ($middle)", abs(middle) < 1e-6f)

            val steps = (1 until count).map { lanes[it] - lanes[it - 1] }
            for (step in steps) {
                assertEquals("count $count: lanes are unevenly spaced", 1f / count, step, 1e-6f)
            }
        }
    }

    @Test
    fun `a lane set never spans more than the reach it is measured against`() {
        // Lanes are a share of the reach, so a set wider than 1 would put bars beyond the frame's own diagonal at
        // every count — visible as a design that thins out at its edges for no reason the knobs can explain.
        for (count in 1..10) {
            val lanes = RoundedTilesGenerator.lanes(count)
            assertTrue("count $count: a lane left the reach", lanes.all { abs(it) <= 0.5f + 1e-6f })
        }
    }
}
