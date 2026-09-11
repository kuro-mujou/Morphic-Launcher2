package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The run a tween marches along, and how one turns into another. The failure a turn exists to prevent is a run that
 * collapses mid-scrub — every copy stacked on one centre — which still animates smoothly and so reads as a style.
 */
class TweenRunTest {

    /**
     * **Turned between two opposite headings, a run swings about the centre and never shortens toward nothing** —
     * where interpolating its endpoints would pass both through the frame's middle at the halfway point.
     */
    @Test
    fun `a run turned to the opposite heading keeps its length and its centre`() {
        val from = RunDraw(bearing = 0.3f, share = 0.2f)
        val to = RunDraw(bearing = 0.3f + PI.toFloat(), share = 0.9f)
        for (step in 0..20) {
            val run = from.turnedTo(to, step / 20f).at(1080, 2400)
            val length = hypot(run.lastX - run.firstX, run.lastY - run.firstY)
            assertTrue("the run shrank to $length at ${step / 20f}", length >= 0.55f * 1080f - 1f)
            assertEquals(540f, (run.firstX + run.lastX) / 2f, 1e-3f)
            assertEquals(1200f, (run.firstY + run.lastY) / 2f, 1e-3f)
        }
    }

    @Test
    fun `a turn starts on one run and ends on the other`() {
        val from = runDraw(Random(1))
        val to = runDraw(Random(2))
        for ((t, run) in listOf(0f to from, 1f to to)) {
            val turned = from.turnedTo(to, t).at(1080, 2400)
            val expected = run.at(1080, 2400)
            assertEquals(expected.firstX, turned.firstX, 0.01f)
            assertEquals(expected.firstY, turned.firstY, 0.01f)
            assertEquals(expected.lastX, turned.lastX, 0.01f)
            assertEquals(expected.lastY, turned.lastY, 0.01f)
        }
    }
}
