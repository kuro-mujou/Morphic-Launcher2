package inkspire.morphic.core.model

/**
 * The **display** buckets for apps — one per page of the APPS category layouts, so this list *is* what a user
 * swipes through. Every [AppCategory] folds into exactly one.
 *
 * **Rebalanced from six groups to twelve.** The original set had `INTERNET` and `UTILITIES` absorbing nine fine
 * categories each, so on a real device two pages held most of the apps and "Internet" was where you went to look for
 * your bank. The point of a category surface is that a page answers a question, and a page holding a third of
 * everything answers none.
 *
 * **Why groups still exist at all**, rather than paging the 24 [AppCategory] values directly: classification wants
 * to reason finely (a curated table says `com.spotify.music` is `AUDIO`) while display wants few, coherent
 * destinations. Keeping the two levels means a new fine category can be added without adding a page — it folds into
 * one of these.
 *
 * Uneven sizes are deliberate: [UTILITIES] and [SYSTEM] are *meant* to be the wide ones, because they are where the
 * unclassifiable goes and a user expects that. The rest are meant to be narrow enough that reaching the page means
 * finding the app.
 *
 * @property displayName The human-readable name of the group.
 */
enum class CategoryGroup(val displayName: String) {
    COMMUNICATION("Communication"),
    SOCIAL("Social"),
    MEDIA("Media"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity"),
    SHOPPING("Shopping"),
    FINANCE("Finance"),
    TRAVEL("Travel"),
    HEALTH("Health"),
    READING("News & Reading"),
    UTILITIES("Utilities"),
    SYSTEM("System"),
}

/** Stable string id for persistence — the enum constant [name]. */
fun CategoryGroup.categoryId(): String = name

/** Converts to a display [Category] (id = enum name, name = [displayName], order = [ordinal]). */
fun CategoryGroup.toCategory(): Category = Category(id = name, name = displayName, order = ordinal)

/**
 * True for a built-in group id — a category the user may rename but not create or delete.
 *
 * Also the test for whether a *stored* category id is still recognised: an id that is neither one of these nor
 * user-created no longer exists, so nothing may stay filed under it (see `AppsOrderRepository.syncCategories`).
 * User-created ids will need their own marker when the management UI arrives; L1 solved that with an id prefix
 * (`u1`, `u2`, …), which keeps the two id spaces apart without needing a column.
 */
fun isBuiltInCategoryId(id: String): Boolean = CategoryGroup.entries.any { it.name == id }
