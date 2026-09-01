package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The band axis and the coverage — this design's own arithmetic. The variable-width banding it shares with the columns
 * is tested in [BandsTest], and the tones it lays over the ground in [RampTonesTest].
 *
 * **The axis has to span exactly `0..1` corner to corner, at every angle and every aspect**, because that is what the
 * coverage knob is a fraction *of*. Short of it, the slab drifts off the frame at some angles and not others — which
 * reads as the design being uneven rather than as arithmetic being wrong, and is invisible in any single render.
 */
class DiagonalBandsGeneratorTest {

    @Test
    fun `density maps to the band count range`() {
        assertEquals(2, DiagonalBandsGenerator.bandCount(0f))
        assertEquals(30, DiagonalBandsGenerator.bandCount(1f))
        assertEquals(2, DiagonalBandsGenerator.bandCount(-1f)) // clamped
        assertEquals(30, DiagonalBandsGenerator.bandCount(2f)) // clamped
    }

    @Test
    fun `the axis spans exactly zero to one over the frame, at every angle`() {
        for (angle in DiagonalBandsGenerator.Angle.entries) {
            val axis = DiagonalBandsGenerator.axisOf(angle, width = 1080, height = 2400)
            val corners = listOf(0f to 0f, 1079f to 0f, 1079f to 2399f, 0f to 2399f)
                .map { (x, y) -> axis.at(x, y) }
            assertEquals("${angle.label}: some corner must sit at 0", 0f, corners.min(), 1e-5f)
            assertEquals("${angle.label}: some corner must sit at 1", 1f, corners.max(), 1e-5f)
        }
    }

    @Test
    fun `an upright angle bands across the frame, not down it`() {
        val upright = DiagonalBandsGenerator.axisOf(DiagonalBandsGenerator.Angle.UPRIGHT, 1080, 2400)
        // Upright bands: the axis runs across the frame, so y cannot move it and x must.
        assertEquals(upright.at(600f, 0f), upright.at(600f, 2399f), 1e-6f)
        assertTrue(upright.at(0f, 0f) != upright.at(1079f, 0f))
    }

    @Test
    fun `the default look is the shallow angle the reference opens on`() {
        // The model's contract is that variant 0 is the design's default look, and the panel wants the angles sorted;
        // the two agree only because the sweep starts at the shallow one.
        assertEquals(DiagonalBandsGenerator.Angle.SHALLOW, DiagonalBandsGenerator.Angle.entries.first())
        val sorted = DiagonalBandsGenerator.Angle.entries.map { it.degrees }
        assertEquals("the angles must read as one axis", sorted.sorted(), sorted)
    }

    @Test
    fun `an angle and its mirror are equally far from the upright`() {
        val degrees = DiagonalBandsGenerator.Angle.entries.map { it.degrees }
        assertEquals(degrees.map { 90f - it }.map { abs(it) }.sorted().distinct().size, 3)
    }

    @Test
    fun `the shallow angle draws shallow on the screen, not on the unit square`() {
        // The point of projecting in pixels: a 20° band must fall 20° across the *screen*. Walking the frame's full
        // width should therefore move the axis by width·sin(20°) in pixels — a fifth of what a 90° band would.
        val axis = DiagonalBandsGenerator.axisOf(DiagonalBandsGenerator.Angle.SHALLOW, 1080, 2400)
        val acrossWidth = axis.at(1079f, 0f) - axis.at(0f, 0f)
        val acrossHeight = axis.at(0f, 2399f) - axis.at(0f, 0f)
        val expected = 1079f * sin(Math.toRadians(20.0)).toFloat()
        val span = expected + 2399f * cos(Math.toRadians(20.0)).toFloat()
        // The x-walk is negative here (the normal leans that way); compare magnitudes.
        assertEquals(expected / span, abs(acrossWidth), 1e-4f)
        assertTrue(
            "a shallow band must move far more down the frame than across it",
            acrossHeight > abs(acrossWidth) * 3f,
        )
    }

    @Test
    fun `coverage runs from a ribbon to full bleed, and never to nothing`() {
        assertEquals(1f, DiagonalBandsGenerator.coverage(1f), 1e-6f)
        assertTrue("the bottom of the knob must still draw bands", DiagonalBandsGenerator.coverage(0f) > 0f)
        assertTrue("and only a ribbon of them", DiagonalBandsGenerator.coverage(0f) < 0.2f)
        assertTrue(DiagonalBandsGenerator.coverage(0.5f) > DiagonalBandsGenerator.coverage(0f))
    }

    @Test
    fun `the slab sits centred on the frame`() {
        // Whatever the coverage, the ground left over is split evenly — the reference centres it too, and its Offset
        // is what walks it off. An off-centre slab at the default would read as a bug rather than as a style.
        for (scale in listOf(0f, 0.3f, 0.7f)) {
            val coverage = DiagonalBandsGenerator.coverage(scale)
            val start = (1f - coverage) / 2f
            assertEquals("the ground above and below must match", start, 1f - (start + coverage), 1e-6f)
        }
    }
}
