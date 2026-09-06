package inkspire.morphic.feature.apps.layout.alphabet

import android.icu.text.AlphabeticIndex
import java.util.Locale

/**
 * One bucket of the A–Z index: what the strip draws for it, and where its apps sit in the sorted list.
 *
 * @property label the bucket's own label — a letter in the user's alphabet, `#` for what sorts before it, or `…` for
 *   what sorts after. Not unique: both ends of the alphabet can be `…` in ICU's own labelling, which is why nothing
 *   here is keyed on it.
 * @property range first-to-last position in the list the index was built over. The strip scrolls to `first` and dims
 *   everything outside.
 */
data class LetterBucket(val label: String, val range: IntRange)

/**
 * The alphabet a list of labels should be indexed by, from **the platform's own** `AlphabeticIndex`.
 *
 * **Hand-rolling this was tried and is wrong, which is worth saying because the hand-rolled version looks fine.** The
 * obvious approach — compare each label's first character against `A`–`Z` with a primary-strength collator — folds
 * accents correctly (`Â`→A, `Ứ`→U, `Ê`→E) and then fails on two cases that are not rare:
 *
 * - **Stroked letters do not fold at all.** `java.text.Collator` matches `Đ`, `Ø` and `Ł` to no letter, and sorts
 *   `Đ` *after* `Z`. A Vietnamese phone sorts its `Đ` apps right after the `D` apps, so the bucket holding them
 *   would claim a range spanning almost the whole list — which scrolls to the wrong place and dims nothing.
 * - **The index and the sort must agree, and only one collation can make them.** Bucketing with the root collator
 *   while sorting with the user's own is what produces that disagreement; bucketing with the user's own instead
 *   breaks `A`–`Z`, since a Vietnamese collator makes `Ă` and `Â` letters in their own right.
 *
 * `AlphabeticIndex` is the resolution rather than a compromise: it derives the buckets *from* the locale's collation,
 * so agreement is structural. On a Vietnamese device that means `Đ` is its own letter beside `D` — which is where
 * that user's list puts it, and what a Vietnamese reader looks for. AOSP's own launcher indexes its drawer this way.
 *
 * **Every script the user has, not only the locale's.** [addLabels] with the display locales gives Cyrillic or Greek
 * their own letters where the phone is set up for them; anything else still lands in the overflow bucket, which is
 * the honest answer for one Chinese app on a Vietnamese phone.
 *
 * API 24, so it is simply present at this project's minimum — there is no fallback path to keep in step, which is the
 * other half of why this beats a table of exceptions.
 */
internal class LetterIndex(locale: Locale) {

    private val index = AlphabeticIndex<Unit>(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()

    /**
     * Every bucket's label, in reading order — the strip's own order, and the list's.
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
