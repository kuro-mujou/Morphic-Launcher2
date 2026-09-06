package inkspire.morphic.core.model

/**
 * Where each bucket's run sits in an **already ordered** list — first index to last, buckets with nothing omitted.
 *
 * Two things read one map, which is why it is a map of ranges rather than a list of buckets and a separate lookup:
 * the index strip draws the keys (only the buckets that lead somewhere, which is what stops a finger landing on a
 * letter that does nothing), and a scrub scrolls to `first` and dims everything outside the range.
 *
 * **Keyed by the bucket's *ordinal*, not by its label**, and that is not a detail: an alphabetic index labels both
 * its ends the same way — one `…` for what sorts before the alphabet and another for what sorts after it — so labels
 * are not unique and a map keyed on them would fold the two ends of the list into one range spanning all of it. An
 * ordinal is also already in reading order, which is what lets the strip be drawn by sorting the keys.
 *
 * **First-to-last rather than a count**, because contiguity is an assumption this does not need to make. It holds
 * when the buckets come from the same collation the list was sorted with, and where it does not the scroll target is
 * still right and only the dimming is generous.
 *
 * @param bucketOf which bucket an element belongs to — the caller's, since only it knows both the element's label
 *   and the index the labels came from.
 */
fun <T> List<T>.indexRanges(bucketOf: (T) -> Int): Map<Int, IntRange> {
    val first = HashMap<Int, Int>()
    val last = HashMap<Int, Int>()
    forEachIndexed { position, item ->
        val bucket = bucketOf(item)
        first.putIfAbsent(bucket, position)
        last[bucket] = position
    }
    return first.keys.sorted().associateWith { bucket -> first.getValue(bucket)..last.getValue(bucket) }
}
