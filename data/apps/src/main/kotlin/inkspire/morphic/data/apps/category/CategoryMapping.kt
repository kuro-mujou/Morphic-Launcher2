package inkspire.morphic.data.apps.category

/**
 * Resolves a package name to a **fine** category id ([inkspire.morphic.core.model.AppCategory.name]), or null when
 * the package isn't in the curated set.
 *
 * A `fun interface` because it is the one part of classification that is *data*, not logic: which category
 * `com.spotify.music` belongs to is a fact someone curated, and it should be swappable without touching
 * [AppCategorizer]. That is also what keeps the categorizer unit-testable — a test supplies a map inline instead of
 * standing up an Android asset (see [AssetCategoryMapping], the real one).
 */
fun interface CategoryMapping {

    fun categoryId(packageName: String): String?

    companion object {
        /** Knows nothing; every app falls through to the platform category and then the heuristics. */
        val Empty = CategoryMapping { null }
    }
}
