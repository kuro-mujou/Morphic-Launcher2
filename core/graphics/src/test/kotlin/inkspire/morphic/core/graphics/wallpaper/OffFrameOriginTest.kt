package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The placement and the two extents the radial designs count against — all three silently wrong rather than visibly
 * so. An origin that slips inside the frame draws the bullseye this type exists to remove; an extent that is off
 * makes a slider read a number nothing on screen matches.
 */
class OffFrameOriginTest {

    /** A tall phone: 1080×2400, the frame every aspect-sensitive case is measured on. */
    private val phone = 2400f / 1080f

    /**
     * The whole point of the type: whatever the seed and whatever the distance, the origin is off the frame. Swept
     * across seeds because the bearing is the only thing seeded, so one seed proves nothing about the rest of the
     * circle.
     */
    @Test
    fun `the origin is outside the frame at every seed and every distance`() {
        for (aspect in listOf(1f, phone, 0.5f)) {
            for (distance in listOf(0f, 0.25f, 0.5f, 1f)) {
                for (seed in 1L..200L) {
                    val origin = offFrameOrigin(Random(seed), distance, aspect)
                    val insideX = origin.x in 0f..1f
                    val insideY = origin.y in 0f..1f
                    assertTrue(
                        "origin landed inside the frame at aspect $aspect, distance $distance, seed $seed",
                        !(insideX && insideY),
                    )
                }
            }
        }
    }

    /** Climbing the knob moves the origin further out, which is what makes it a *Distance*. */
    @Test
    fun `distance pushes the origin further from the frame`() {
        var previous = -1f
        for (distance in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val origin = offFrameOrigin(Random(7), distance, phone)
            val out = hypot(origin.x - 0.5f, (origin.y - 0.5f) * phone)
            assertTrue("distance $distance did not move the origin out", out > previous)
            previous = out
        }
    }

    /** One draw whatever the distance, so moving the knob cannot reshuffle whatever the caller seeds next. */
    @Test
    fun `the placement consumes the same stream at every distance`() {
        val after = listOf(0f, 0.5f, 1f).map { distance ->
            val random = Random(3)
            offFrameOrigin(random, distance, phone)
            random.nextFloat()
        }

        assertEquals(after[0], after[1], 0f)
        assertEquals(after[0], after[2], 0f)
    }

    /**
     * The distance span runs from the frame's nearest **point** to its far corner. Placed square-on above the middle
     * of the top edge, that near point is on the edge rather than at a corner — the case a corner-only reading gets
     * wrong, and it overstates the span by enough to lose a ring or two.
     */
    @Test
    fun `the distance span measures from the nearest point, not the nearest corner`() {
        // A square frame, origin one unit directly above the middle of the top edge.
        val origin = OffFrameOrigin(x = 0.5f, y = -1f, heightOverWidth = 1f)

        val nearest = 1f // straight down to the middle of the top edge
        val farthest = hypot(0.5f, 2f) // to either bottom corner
        assertEquals(farthest - nearest, origin.distanceSpan, 1e-4f)
    }

    /** A frame is nearer to filling the view the closer the origin is, and shrinks to a sliver as it moves away. */
    @Test
    fun `the sector narrows as the origin moves away`() {
        val near = offFrameOrigin(Random(11), 0f, phone).sectorTurns
        val far = offFrameOrigin(Random(11), 1f, phone).sectorTurns

        assertTrue("the near sector should be wide, was $near", near > 0.2f)
        assertTrue("the far sector should be narrow, was $far", far < near)
        assertTrue("a frame seen from outside can never fill the turn, was $far", far > 0f && near < 1f)
    }

    /**
     * The sector is taken from the four corners, which is only exact because a rectangle is convex. Pinned against a
     * case with an answer by hand: from one unit directly above the middle of a unit square's top edge, the corners
     * sit at ±26.57° off straight down, so the frame subtends 53.13° — just under a seventh of the turn.
     */
    @Test
    fun `the sector is the angle the frame actually subtends`() {
        val origin = OffFrameOrigin(x = 0.5f, y = -1f, heightOverWidth = 1f)

        assertEquals(53.13f / 360f, origin.sectorTurns, 1e-3f)
    }
}
