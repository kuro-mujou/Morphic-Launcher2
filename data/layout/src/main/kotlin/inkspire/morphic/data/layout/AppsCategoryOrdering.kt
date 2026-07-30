package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey

/**
 * Which apps are in which category, and in what order — the whole arrangement, keyed by category id.
 *
 * A map rather than a list because **the key order is not the category order**: how categories are ordered is
 * `category.sortOrder`'s business, held in its own table, so a shape that implied an order here would invite a
 * caller to trust one that isn't there.
 */
internal typealias CategoryItems = Map<String, List<ComponentKey>>

/**
 * The APPS category arrangement as pure list maths — the counterpart of `AppsPagerPaging.kt` for the other APPS
 * store, and deliberately much smaller.
 *
 * **A category is one dense list with no capacity.** That single difference removes everything the pager needed:
 * no page boundaries, so nothing cascades; no per-page compaction, because removing an item from a list *is* the
 * compaction; and no re-fit on a capacity change, because there is no capacity to change. What is left is
 * insertion, removal, and the rule about which of the two a stored app is subject to.
 */

/**
 * Moves [app] to [toSlot] of [toCategory] — the only edit the surface makes.
 *
 * Reordering within a category and re-filing into another are the same operation: pages *are* categories, so
 * carrying an app to the next page and dropping it between two icons is one move with a different destination id.
 * Removal happens first, so a move within a category is measured against the list minus the dragged app, which is
 * what the MovingGap preview already shows.
 *
 * [toCategory] may be one that holds nothing yet (a page the user emptied) — its bucket is simply created.
 */
internal fun moveCategoryItem(
    items: CategoryItems,
    app: ComponentKey,
    toCategory: String,
    toSlot: Int,
): CategoryItems {
    val without = items.mapValues { (_, apps) -> apps.filterNot { it == app } }
    val target = without[toCategory].orEmpty()
    val landed = target.toMutableList().apply { add(toSlot.coerceIn(0, size), app) }
    return without + (toCategory to landed)
}

/**
 * Reconciles the arrangement with what is installed: appends apps that are new, drops ones that are gone, and
 * leaves everything already filed exactly where it is.
 *
 * **A stored app keeps its category even when [assignments] disagrees**, and that is the most important line in
 * this file. Classification runs on every launch, so re-applying it here would silently undo every drag the user
 * ever made — the arrangement would revert to the classifier's opinion each time the app started. An assignment is
 * therefore only ever a *first* answer, consulted for apps that have no answer yet. That also makes the user's
 * override free: it is simply the row already being there.
 *
 * With nothing stored this is the **first-run seed** — every app is new, so the same path lays out the lot. One
 * path rather than a separate seed step, for the reason the pager's `syncPagerPages` gives: a second path is where
 * the two drift apart.
 *
 * @param assignments every installed app and the category it *would* be filed under. Iteration order decides the
 *   order new apps are appended in, so the caller passes a map built in display order (A–Z) rather than a set.
 */
internal fun syncCategoryItems(items: CategoryItems, assignments: Map<ComponentKey, String>): CategoryItems {
    val installed = assignments.keys
    val pruned = items.mapValues { (_, apps) -> apps.filter { it in installed } }
    val filed = pruned.values.flatMapTo(mutableSetOf()) { it }
    var out = pruned
    assignments.forEach { (app, categoryId) ->
        if (app !in filed) out = out + (categoryId to (out[categoryId].orEmpty() + app))
    }
    return out
}

/**
 * Drops every bucket whose id is not in [known] — apps filed under a category that no longer exists.
 *
 * **The bound on "a filed app keeps its category".** It keeps it *if that category exists*: an id can stop existing
 * because the built-in set was rebalanced, or later because the user deleted a category they made. Leaving the rows
 * behind would strand those apps — the read is driven by the definitions table, so they would render nowhere while
 * still occupying a row, and `syncCategoryItems` would consider them filed and never re-file them. Unfiling them
 * instead hands them back to the classifier, which is the only thing that can place them again.
 */
internal fun dropUnknownCategories(items: CategoryItems, known: Set<String>): CategoryItems =
    items.filterKeys { it in known }

/** The category [app] is filed under, or null when it isn't filed at all. */
internal fun categoryOf(items: CategoryItems, app: ComponentKey): String? =
    items.entries.firstOrNull { (_, apps) -> app in apps }?.key
