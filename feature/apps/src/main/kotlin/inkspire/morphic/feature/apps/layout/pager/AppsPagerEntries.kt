package inkspire.morphic.feature.apps.layout.pager

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
 * **The dragged item stays composed on its source page even after the finger has carried it to another**, which is
 * why this can return it twice across two pages. That is not a glitch to tidy up: the cell on the source page owns
 * the gesture's pointer stream, and disposing it mid-drag kills the drag (the drag toolkit's standing rule). Both
 * copies are drawn invisible by `LauncherDragCell`, so the user sees only the floating proxy — the far copy exists
 * purely to occupy the gap so the other icons flow around it.
 *
 * Entries are compared by [gridItem] rather than by value: [AppsItem] wraps an `AppInfo`, so structural equality
 * would also compare the label and the icon, and the question here is only *which item is this*.
 *
 * Truncated to [perPage] because the destination page may not have room: the surplus is what the repository will
 * cascade onto the next page, so previewing it here would promise a layout the commit won't produce.
 */
internal fun displayOrder(
    stored: List<AppsItem>,
    dragged: AppsItem?,
    gap: Int,
    perPage: Int,
): List<AppsItem> {
    if (dragged == null) return stored
    val others = stored.filterNot { it.gridItem == dragged.gridItem }
    val at = gap.coerceIn(0, others.size)
    return (others.take(at) + dragged + others.drop(at)).take(perPage)
}

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
    pages.forEach { page ->
        page.filterIsInstance<AppsItem.Folder>().forEach { folder ->
            folder.apps.firstOrNull { it.componentKey == component }?.let { return AppsItem.App(it) }
        }
    }
    return null
}
