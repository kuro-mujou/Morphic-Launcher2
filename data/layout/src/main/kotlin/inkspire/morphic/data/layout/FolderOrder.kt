package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey

/**
 * Folds an order **reported by a folder overlay** back onto the folder's real membership.
 *
 * Lives beside the op it guards rather than in one feature: `ReorderFolder` exists in both this module's
 * command sets (HOME's [LayoutChange] and the APPS pager's [AppsPagerChange]) and replaces membership
 * wholesale in both, so every surface that hosts a folder owes its writes this same reconciliation.
 *
 * Why this exists: `LayoutChange.ReorderFolder` sets membership to *exactly* the list it is given (the store
 * clears the folder and re-inserts), while the overlay can only report the members it actually rendered — and
 * [HomeState] resolves a folder's contents through the app cache, so a member with no `AppInfo` (uninstalled and
 * not yet pruned, a paused work profile, a cache still warming) never reaches the UI at all. Handing the reported
 * list straight to `ReorderFolder` would therefore **delete** those members. Reconciling here keeps the overlay
 * honest — it reports what it drew — and keeps the write total.
 *
 * @param known the folder's full membership, in its stored order (the source of truth for *what* is in it).
 * @param reported the order the UI dropped, over the subset it could render (the source of truth for *sequence*).
 * @return [reported] restricted to actual members, then the members the UI couldn't render, in their stored order.
 *   Unrenderable members land at the end because the drop said nothing about where they belong.
 */
fun reconcileFolderOrder(
    known: List<ComponentKey>,
    reported: List<ComponentKey>,
): List<ComponentKey> {
    val knownSet = known.toSet()
    val reportedSet = reported.toSet()
    return reported.filter { it in knownSet } + known.filterNot { it in reportedSet }
}
