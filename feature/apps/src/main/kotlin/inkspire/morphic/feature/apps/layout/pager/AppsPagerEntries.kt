package inkspire.morphic.feature.apps.layout.pager

import inkspire.morphic.core.designsystem.ordered.movingGapDisplayOrder
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.feature.apps.AppsItem
import inkspire.morphic.feature.apps.gridItem

/**
 * The APPS pager's **pure** list and lookup maths — no Compose, no state, no drag toolkit.
 *
 * Split out of [AppsPager] because none of it is UI: given a page's stored entries and where the gap is, these say
 * what to draw and what a drag is carrying. Keeping them here rather than file-private in the composable is what
 * makes them reachable from a unit test, which is the same reason the equivalent arithmetic for the *store* side
 * lives in `data:layout`'s `AppsPagerPaging` (and is tested there).
 */

/**
 * What one page draws while a drag is in flight: its stored entries, with the dragged item lifted to the gap.
 *
 * The shared MovingGap render ([movingGapDisplayOrder]) plus the one rule that is this surface's own — the page
 * **capacity**. Entries are told apart by [gridItem] rather than by value, since [AppsItem] wraps an `AppInfo` and
 * the question is only *which item is this*.
 *
 * **The dragged item stays composed on its source page even after the finger has carried it to another**, which is
 * why this can return it twice across two pages. That is not a glitch to tidy up: the cell on the source page owns
 * the gesture's pointer stream, and disposing it mid-drag kills the drag (the drag toolkit's standing rule). Both
 * copies are drawn invisible by `LauncherDragCell`, so the user sees only the floating proxy — the far copy exists
 * purely to occupy the gap so the other icons flow around it.
 *
 * Truncated to [perPage] because the destination page may not have room: the surplus is what the repository will
 * cascade onto the next page, so previewing it here would promise a layout the commit won't produce. That truncation
 * is the whole reason this stays a named function instead of an inlined call — a page is the one ordered surface with
 * a hard capacity, and both call sites (the planner and the page itself) must apply it identically.
 */
internal fun pageDisplayOrder(
    stored: List<AppsItem>,
    dragged: AppsItem?,
    gap: Int,
    perPage: Int,
): List<AppsItem> = movingGapDisplayOrder(stored, dragged, gap) { it.gridItem }.take(perPage)

/** The folder entry with [folderId], wherever it sits. */
internal fun folderAt(pages: List<List<AppsItem>>, folderId: Long): AppsItem.Folder? =
    pages.firstNotNullOfOrNull { page ->
        page.filterIsInstance<AppsItem.Folder>().firstOrNull { it.folder.id == folderId }
    }

/**
 * Whether [dragged] can be folded into [target].
 *
 * Apps combine with apps (making a folder) and drop into folders; a **folder cannot go into a folder**, because
 * folders don't nest — the model says so in [inkspire.morphic.core.model.Folder], whose contents are apps.
 */
internal fun canMergeInto(dragged: GridItem, target: AppsItem): Boolean =
    dragged is GridItem.App && (target is AppsItem.App || target is AppsItem.Folder)

/**
 * The entry a drag is carrying: an item sitting on a page, **or an app inside a folder**.
 *
 * The second half is what an extract needs. An app dragged out of a folder is on no page at all — it is still a
 * member, since nothing is written until the drop — so a page-only lookup would find nothing, and both the
 * floating proxy and the reorder preview would have no icon to draw.
 */
internal fun entryFor(pages: List<List<AppsItem>>, dragged: GridItem): AppsItem? {
    pages.forEach { page -> page.firstOrNull { it.gridItem == dragged }?.let { return it } }
    val component = (dragged as? GridItem.App)?.component ?: return null
    return appInPages(pages, component)?.let(AppsItem::App)
}

/**
 * The app for [component] wherever it sits — **loose on a page, or inside a folder**.
 *
 * Both halves are needed for the reason [entryFor]'s second half is: a drag detaches an app from nothing until it
 * lands, so an app on its way out of a folder is still a member and appears on no page. Anything resolving "the app
 * under the finger" from page contents alone would find nothing and draw nothing — the floating proxy and a folder's
 * `incoming` cell both go through here.
 *
 * The pager's twin of the category card's `appInCategories`: one lookup per surface, over the shape that surface is
 * actually handed. A single `AppsState`-level helper was tried and was dead on arrival — a layout receives a *slice*
 * (`pages`, `categories`), never the whole state, so it could not be called from either place that needs it.
 */
internal fun appInPages(pages: List<List<AppsItem>>, component: ComponentKey): AppInfo? =
    pages.firstNotNullOfOrNull { page ->
        page.firstNotNullOfOrNull { entry ->
            when (entry) {
                is AppsItem.App -> entry.info.takeIf { it.componentKey == component }
                is AppsItem.Folder -> entry.apps.firstOrNull { it.componentKey == component }
            }
        }
    }
