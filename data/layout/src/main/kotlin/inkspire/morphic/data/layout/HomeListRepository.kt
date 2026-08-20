package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/**
 * **HOME's vertical-list arrangement** — the ordered store behind `HomeLayout.LIST_WITH_WIDGET_AREA`'s main area.
 *
 * The third layout repository, beside [LayoutRepository] (HOME's coordinate placements) and [AppsOrderRepository]
 * (the APPS surface's order stores), and it is here rather than folded into either for the reason those two are
 * separate from each other: the *shape* differs. A coordinate store answers "which cell, in which zone, at which
 * orientation"; this one answers "which position in one list", with no orientation and no zone at all. Two of the
 * three questions have no answer here, and a repository that had to accept them would be asking every caller to
 * supply a value it then ignores.
 *
 * **A store of its own, not a view of the grid.** Deriving the list from the pager's placements — flattened by
 * (page, row, col) — means reordering it
 * wrote `MoveApp(page = 0, row = index, col = 0)` for every app — so a single drag in the list collapsed the grid
 * arrangement into one column, permanently, and switching back to the pager layout found it destroyed. Two stores
 * make the two layouts independent, which is what "switch layout and switch back" has to mean.
 *
 * The good half of that idea survives as [seedIfEmpty]: the *first* time the list is shown it is filled from the
 * grid in reading order, so choosing this layout hands the user their apps rather than a blank screen.
 *
 * **Apps only, and one list across orientations.** No folders, a one-lane list having no merge ring to make one
 * with — and no per-orientation copy, because rotating a list changes how many rows are on
 * screen and nothing about their order (the persistence table in `CLAUDE.md` states both).
 */
interface HomeListRepository {

    /** The list, top to bottom. Empty until something seeds it — see [seedIfEmpty]. */
    val order: Flow<List<ComponentKey>>

    /**
     * Replaces the order with [reported], the arrangement the surface dropped.
     *
     * **Reconciled against what is stored before it is written** ([reconcileReportedOrder]), not taken verbatim: the
     * surface can only report the rows it could *render*, and every row is resolved through the app cache, so a
     * member whose `AppInfo` is missing (uninstalled and not yet pruned, a paused work profile, a cache still
     * warming) never reaches the screen. Writing the reported list as-is would silently delete it.
     *
     * The guard is **here** rather than at the call site, which is `AppsCategoryChange.Reorder`'s placement and not
     * `ReorderFolder`'s, and for that op's reason: the caller has nothing true to reconcile against. A folder's
     * membership reaches the UI intact — it *is* the folder definition — while this list's stored order is only ever
     * seen by this repository.
     *
     * Membership is therefore fixed by this call: it can reorder the list and it cannot add to or remove from it.
     */
    suspend fun setOrder(reported: List<ComponentKey>)

    /**
     * Fills the list with [apps] **only if it is empty** — the first-run default for this layout.
     *
     * [apps] is the caller's reading-order flattening of HOME's grid, and the reason this exists: a user who
     * switches to the list layout should find the apps they had, in the order they had them,
     * rather than an empty screen with no way to fill it — an add-apps picker is not built.
     *
     * Idempotent, and idempotent on *emptiness* rather than on a "seeded" flag: a user who empties their list
     * deliberately would be re-seeded by a flag that had been set and cleared, and there is nowhere honest to store
     * "they meant it". The same rule `HomeViewModel.seedIfEmpty` uses for the grid.
     */
    suspend fun seedIfEmpty(apps: List<ComponentKey>)

    /**
     * Removes [component] from the list, if it is in it.
     *
     * The one membership op, and it exists because a drag has to be able to take an app *out* — there is nowhere
     * else on this layout's main area for it to go. Adding is [seedIfEmpty] plus, later, a picker; this is the half
     * a gesture can reach today.
     */
    suspend fun remove(component: ComponentKey)
}
