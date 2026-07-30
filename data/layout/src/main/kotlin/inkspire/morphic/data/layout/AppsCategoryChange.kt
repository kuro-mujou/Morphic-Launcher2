package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey

/**
 * The write-command vocabulary for the APPS **category** arrangement.
 *
 * One value today, and a sealed interface anyway — for the shape rather than the ceremony. [AppsOrderRepository]
 * applies a *batch* against one view of the arrangement, which is what lets a future edit that spans stores (a
 * re-file plus a category rename, say) commit together; a bare method per edit would have to give that up later.
 *
 * Category *management* — create, rename, delete, reorder — is deliberately not here. It has no caller: the seeded
 * groups are what exists, and the rewrite plan puts editors in `feature:settings`. Adding ops for it now would be
 * model in a vacuum, and their shape should be decided by the screen that uses them.
 */
sealed interface AppsCategoryChange {

    /**
     * Put [app] at [toSlot] of [toCategory].
     *
     * **Reordering and re-filing are the same op**, which is the point: on this surface a page *is* a category, so
     * carrying an app to the next page and dropping it between two icons differs from an in-page reorder only in
     * the destination id. There is no capacity to overflow, so nothing cascades — the list simply grows.
     */
    data class Move(val app: ComponentKey, val toCategory: String, val toSlot: Int) : AppsCategoryChange
}
