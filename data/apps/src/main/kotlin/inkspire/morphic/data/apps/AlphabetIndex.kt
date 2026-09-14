package inkspire.morphic.data.apps

import android.icu.text.AlphabeticIndex
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.indexRanges
import java.text.Collator
import java.util.Locale

/**
 * One bucket of the A–Z index: what a strip draws for it, and where its apps sit in the ordered list.
 *
 * @property label the bucket's own label — a letter in the user's alphabet, `#` for what sorts before it, or `…` for
 *   what sorts after. Not unique: both ends of the alphabet can be `…` in ICU's own labelling, which is why nothing
 *   here is keyed on it.
 * @property range first-to-last position in the list the index was built over. Positions *in that list* — paired with
 *   a differently ordered one they point at the wrong apps, which is silent rather than visible.
 */
data class LetterBucket(val label: String, val range: IntRange)

/**
 * The collection in [LabelOrder] **and** the letter runs in it, which are one fact: the ranges are positions in that
 * list, and paired with a differently ordered one they name the wrong apps — silently, since the wrong letter simply
 * shows the wrong apps.
 */
data class IndexedApps(val apps: List<AppInfo>, val letters: List<LetterBucket>)

/**
 * **The order every A–Z view of the collection is sorted by**, and the reason it is one value rather than a
 * `sortedBy { it.label }` at each site.
 *
 * `String.compareTo` is codepoint order: it puts every accented letter after `Z` (so a Vietnamese or French app list
 * breaks into two alphabets) and gets Turkish dotless-i wrong. The collator sorts by the *current locale's* rules,
 * which is what a user scanning an alphabetical list expects. Default (tertiary) strength on purpose — a
 * primary-strength collator treats `a` and `ă` as equal, which is right for *searching* and wrong for *ordering*.
 *
 * The component tie-break makes the order total: two apps can share a label (a work-profile clone of a personal app
 * is the common one), and without it their relative order would depend on the cache's emission order and could
 * visibly swap between refreshes — including changing where a newly installed app lands on a pager.
 *
 * **It has to be the same comparator [letterBuckets] indexes against**, which is the whole reason both live here.
 */
val LabelOrder: Comparator<AppInfo> = run {
    val collator = Collator.getInstance()
    Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
        .thenBy { it.componentKey.flatten() }
}

/**
 * The A–Z buckets of this list, which must already be in [LabelOrder] — only the letters that lead somewhere, in
 * reading order.
 *
 * **Shared rather than written per surface, because a disagreement here is invisible.** The ranges are positions in
 * the caller's own list, so a bucketing built from one collation over a list sorted by another produces a strip that
 * scrolls to the wrong place and dims the wrong rows, with nothing on screen to say so. Two surfaces index the same
 * collection — the APPS list and grid scroll by these, HOME's list filters by them — and they have to agree.
 *
 * Bucketing is the platform's `AlphabeticIndex`, and hand-rolling it was tried and is wrong, which is worth saying
 * because the hand-rolled version looks fine. The obvious approach — compare each label's first character against
 * `A`–`Z` with a primary-strength collator — folds accents correctly (`Â`→A, `Ứ`→U, `Ê`→E) and then fails twice:
 *
 * - **Stroked letters do not fold at all.** `java.text.Collator` matches `Đ`, `Ø` and `Ł` to no letter, and sorts
 *   `Đ` *after* `Z`. A Vietnamese phone sorts its `Đ` apps right after the `D` apps, so the bucket holding them
 *   would claim a range spanning almost the whole list.
 * - **The index and the sort must agree, and only one collation can make them.** Bucketing with the root collator
 *   while sorting with the user's own is what produces that disagreement; bucketing with the user's own instead
 *   breaks `A`–`Z`, since a Vietnamese collator makes `Ă` and `Â` letters in their own right.
 *
 * `AlphabeticIndex` is the resolution rather than a compromise: it derives the buckets *from* the locale's collation,
 * so agreement is structural. On a Vietnamese device that means `Đ` is its own letter beside `D` — which is where
 * that user's list puts it, and what a Vietnamese reader looks for. AOSP's own launcher indexes its drawer this way.
 *
 * The index is rebuilt per call rather than held, because the locale can change under a running launcher and one
 * built for the old locale files letters the list no longer sorts that way. It is a few hundred microseconds beside
 * the sort that produced the list.
 */
fun List<AppInfo>.letterBuckets(): List<LetterBucket> {
    val index = LetterIndex(Locale.getDefault())
    return indexRanges { index.bucketOf(it.label) }
        .map { (bucket, range) -> LetterBucket(index.labels[bucket], range) }
}

/**
 * The locale's own alphabet, and which of its letters a label files under.
 *
 * **Every script the user has, not only the locale's.** `addLabels` with English gives `A`–`Z` alongside a Cyrillic
 * or Greek locale's own letters; anything else still lands in the overflow bucket, which is the honest answer for one
 * Chinese app on a Vietnamese phone.
 *
 * API 24, so it is simply present at this project's minimum — there is no fallback path to keep in step, which is the
 * other half of why this beats a table of exceptions.
 */
private class LetterIndex(locale: Locale) {

    private val index = AlphabeticIndex<Unit>(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()

    /**
     * Every bucket's label, in reading order — a strip's own order, and the list's.
     *
     * `#` replaces ICU's underflow label, which is `…` like the overflow one. Two identical ellipses at opposite ends
     * of a strip say nothing about which is which, where `#` is what a reader already reads as "numbers and symbols".
     */
    val labels: List<String> = (0 until index.bucketCount).map { position ->
        val bucket = index.getBucket(position)
        if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.UNDERFLOW) "#" else bucket.label
    }

    /** Which bucket [label] files under, as a position in [labels]. */
    fun bucketOf(label: String): Int = index.getBucketIndex(label)
}
