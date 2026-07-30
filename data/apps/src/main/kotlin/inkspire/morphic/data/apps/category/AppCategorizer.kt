package inkspire.morphic.data.apps.category

import android.content.pm.ApplicationInfo
import inkspire.morphic.core.model.AppCategory
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CategoryGroup
import inkspire.morphic.core.model.categoryId

/**
 * Decides which category an app belongs to — the classification the APPS category layouts are seeded from.
 *
 * **One app in, one category id out.** That is the whole surface, and it is narrower than L1's on purpose: L1's
 * `categorize` took the app list, the user's overrides *and* the category definitions, and returned a sorted
 * `Map<Category, List<AppInfo>>` — classification, grouping, ordering and display resolution in one method. Here
 * ordering belongs to the category store (`category_item.sortOrder`) and overrides *are* that store, so the only
 * thing left for this class is the first-run guess. It has no state and touches no repository, which is what makes
 * it testable without Android.
 *
 * **The id is a [CategoryGroup] name, not an [AppCategory] one.** The fine taxonomy is how classification
 * *reasons* — a curated map says `com.spotify.music` is `AUDIO` — but a page per fine category would leave most of
 * 24 pages empty on a real device, so the answer is folded to the 7 broad groups. `isBuiltInCategoryId` in
 * `core:model` already assumes exactly that set.
 *
 * **Priority: curated map → platform category → keyword heuristic → uncategorized.** Each step is more of a guess
 * than the one before, so the order is confidence order. The platform value sits *below* the curated map because
 * it is coarse and often absent, and above the heuristics because a developer declaring `CATEGORY_AUDIO` is a fact
 * where a package name containing "radio" is a hunch.
 *
 * @param mapping the curated package → fine-category data; [CategoryMapping.Empty] in tests and wherever the asset
 *   isn't wanted. Injected rather than read from a `Context` here, so classification stays pure.
 */
class AppCategorizer(private val mapping: CategoryMapping = CategoryMapping.Empty) {

    /**
     * The category id [app] should be filed under — a [CategoryGroup] name, stable enough to persist.
     *
     * Never returns "Other": an app nothing could classify lands in [CategoryGroup.UTILITIES] if the user
     * installed it and [CategoryGroup.SYSTEM] if it shipped with the device. That split is worth the special case
     * — the unclassifiable pile is mostly system components, and mixing them in with the user's own leftovers
     * makes the one page they'd actually scan the least useful one.
     */
    fun categoryIdOf(app: AppInfo): String = groupOf(app).categoryId()

    private fun groupOf(app: AppInfo): CategoryGroup {
        val fine = fineCategoryOf(app)
        return if (fine == AppCategory.OTHER && app.isSystem) CategoryGroup.SYSTEM else fine.group
    }

    /** The fine classification, before folding to a group — the step the priority chain actually decides. */
    internal fun fineCategoryOf(app: AppInfo): AppCategory {
        curated(app.componentKey.packageName)?.let { return it }
        fromPlatform(app.category)?.let { return it }
        CategoryHeuristics.guess(app)?.let { return it }
        return AppCategory.OTHER
    }

    /** The curated id, resolved against the taxonomy — an unknown id in the data is ignored, not trusted. */
    private fun curated(packageName: String): AppCategory? =
        mapping.categoryId(packageName)?.let { id -> AppCategory.entries.firstOrNull { it.name == id } }

    /**
     * Maps a platform `ApplicationInfo.category` to the fine taxonomy; null for undefined or unmapped.
     *
     * Only the nine values the platform defines are here. `ApplicationInfo.CATEGORY_UNDEFINED` (-1) is the common
     * case by a wide margin — most apps never set it — which is why the heuristics below it aren't optional.
     */
    private fun fromPlatform(category: Int): AppCategory? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.IMAGE
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.ACCESSIBILITY
        else -> null
    }
}
