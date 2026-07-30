package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey

/**
 * The write-command vocabulary for the APPS **category** arrangement, shared by both category layouts.
 *
 * A sealed interface for the shape rather than the ceremony: [AppsOrderRepository] applies a *batch* against one view
 * of the arrangement, which is what lets an edit that spans stores commit together; a bare method per edit would have
 * to give that up later.
 *
 * Category *management* — create, rename, delete, reorder the categories themselves — is deliberately not here. It
 * has no caller: the seeded groups are what exists, and the rewrite plan puts editors in `feature:settings`. Adding
 * ops for it now would be model in a vacuum, and their shape should be decided by the screen that uses them.
 */
sealed interface AppsCategoryChange {

    /**
     * Put [app] at [toSlot] of [toCategory] — the op that changes **membership**.
     *
     * **Reordering and re-filing are the same op**, which is the point: on the category *pager* a page *is* a
     * category, so carrying an app to the next page and dropping it between two icons differs from an in-page reorder
     * only in the destination id. There is no capacity to overflow, so nothing cascades — the list simply grows.
     */
    data class Move(val app: ComponentKey, val toCategory: String, val toSlot: Int) : AppsCategoryChange

    /**
     * Re-sequence [category]'s apps to [apps] — the op that changes **order only**.
     *
     * Why a second op when [Move] can already reorder: the category *card*'s expansion reorders by MovingGap, which
     * reports a whole list rather than one item and a slot (the same shape a folder overlay reports, since it *is*
     * the same overlay). A caller could diff that list down to a single [Move], but the diff would be guesswork about
     * which app the user actually dragged; reporting the sequence the UI ended up showing is exact.
     *
     * **Membership is untouchable here, by construction.** [apps] is reconciled against real membership inside the
     * store ([reconcileReportedOrder]), so an app missing from the list stays a member and an app that was never one
     * cannot sneak in — the op can only ever permute. That is a deliberate difference from `ReorderFolder`, whose
     * callers apply the same guard *themselves*: a folder's membership travels to the UI intact (it is the folder
     * definition), so the caller has something true to reconcile against, while a category's does not — the UI only
     * ever sees the apps the cache could resolve. With no honest `known` list available up there, the guard has to
     * live where the full membership does.
     */
    data class Reorder(val category: String, val apps: List<ComponentKey>) : AppsCategoryChange
}
