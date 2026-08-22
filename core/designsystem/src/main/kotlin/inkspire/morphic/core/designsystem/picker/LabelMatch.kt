package inkspire.morphic.core.designsystem.picker

import java.text.Collator

/**
 * A collator that ignores case **and accents** — `PRIMARY` strength compares base letters only.
 *
 * Shared because every list a user searches must agree: `lowercase()` compares raw UTF-16, so an accented
 * label matches as if it were a different alphabet, and a picker that used one rule while the surface beside
 * it used another would find "Éditeur" for "e" on one screen and not the other.
 */
fun labelCollator(): Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/**
 * Whether [this] label contains [query], ignoring case and accents.
 *
 * Done by hand because `Collator` compares whole strings and there is no substring form: each window of the label
 * the same length as the query is compared, which is O(label × query) and fine for a label.
 */
fun String.matchesLabel(query: String, collator: Collator): Boolean {
    if (query.length > length) return false
    for (start in 0..length - query.length) {
        if (collator.compare(substring(start, start + query.length), query) == 0) return true
    }
    return false
}
