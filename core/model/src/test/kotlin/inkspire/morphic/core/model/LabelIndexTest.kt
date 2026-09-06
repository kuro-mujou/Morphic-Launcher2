package inkspire.morphic.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The A–Z index's **grouping**, which is the half of it that can be checked without a device.
 *
 * Which bucket a label belongs to is the platform's `AlphabeticIndex` and is not testable here; what *is* testable,
 * and is where a launcher's index actually goes wrong, is what happens to those buckets once assigned — whether a run
 * that appears twice reports one range or two, whether a bucket with nothing in it appears at all, and whether the
 * strip ends up in the same order as the list beside it.
 */
class LabelIndexTest {

    @Test
    fun `each bucket spans its run, and empty buckets are absent`() {
        // Bucket ordinals as an index hands them out: 0 for what sorts before the alphabet, then a letter each.
        val buckets = listOf(0, 0, 1, 3, 3, 3)
        val ranges = buckets.indexRanges { it }

        assertEquals(0..1, ranges[0])
        assertEquals(2..2, ranges[1])
        assertEquals(3..5, ranges[3])
        // Bucket 2 holds nothing, so the strip does not offer it — a finger cannot land on a letter leading nowhere.
        assertEquals(null, ranges[2])
    }

    @Test
    fun `the strip reads in the same order the list scrolls`() {
        // Deliberately encountered out of order: the first app in the list belongs to a later bucket than the second.
        val ranges = listOf(4, 1, 4, 9).indexRanges { it }
        assertEquals(listOf(1, 4, 9), ranges.keys.toList())
    }

    @Test
    fun `a scattered bucket keeps a range that still leads to its first app`() {
        // What happens when a bucket is *not* contiguous — the case an index built from a different collation than
        // the sort produces. The range is generous rather than wrong: the scroll target is still that bucket's first
        // app, and only the dimming reaches further than it should.
        val ranges = listOf(1, 2, 1).indexRanges { it }
        assertEquals(0..2, ranges[1])
        assertEquals(0, ranges.getValue(1).first)
    }

    @Test
    fun `an empty list has no buckets rather than empty ones`() {
        assertEquals(emptyMap<Int, IntRange>(), emptyList<Int>().indexRanges { it })
    }
}
