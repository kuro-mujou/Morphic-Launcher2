package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey

/**
 * Folds an order **reported by the UI** back onto the collection's real membership.
 *
 * Lives beside the ops it guards rather than in one feature, because every store that lets the UI report a *whole*
 * order owes its writes this same reconciliation. Three do today: `ReorderFolder` in both command sets (HOME's
 * [LayoutChange] and the APPS pager's [AppsPagerChange]), and [AppsCategoryChange.Reorder].
 *
 * Why this exists: those ops set a collection's order to *exactly* the list they are given (the store clears and
 * re-inserts), while the UI can only report the items it actually rendered — and every surface resolves its contents
 * through the app cache, so a member with no `AppInfo` (uninstalled and not yet pruned, a paused work profile, a
 * cache still warming) never reaches the screen at all. Handing the reported list straight to the op would therefore
 * **drop** those members. Reconciling keeps the UI honest — it reports what it drew — and keeps the write total.
 *
 * Named for the *reported* order rather than for the folder it first guarded: a category expansion reports an order
 * the same way a folder overlay does, and for the same reason. *Where* the guard is applied does differ between the
 * two, deliberately — see [AppsCategoryChange.Reorder].
 *
 * @param known the collection's full membership, in its stored order (the source of truth for *what* is in it).
 * @param reported the order the UI dropped, over the subset it could render (the source of truth for *sequence*).
 * @return [reported] restricted to actual members, then the members the UI couldn't render, in their stored order.
 *   Unrenderable members land at the end because the drop said nothing about where they belong.
 */
fun reconcileReportedOrder(
    known: List<ComponentKey>,
    reported: List<ComponentKey>,
): List<ComponentKey> {
    val knownSet = known.toSet()
    val reportedSet = reported.toSet()
    return reported.filter { it in knownSet } + known.filterNot { it in reportedSet }
}
