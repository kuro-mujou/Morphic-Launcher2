package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** A named ± one row/column at an edge of the dock — the shape a grid editor's button produces. */
data class DockEdit(val edge: GridEditorEdge, val add: Boolean)

/**
 * **What a dock resize does to the items in it** — the one place that rule lives.
 *
 * The dock is a single-page strip, so unlike home it has no next page to push a displaced item onto: whatever the new
 * grid cannot hold is evicted and lands on HOME's main area, which can always append a page. That is a *policy* about
 * two zones rather than arithmetic about one, which is why it sits here in `data:layout` beside the ops it emits
 * instead of inside `GridReflow`.
 *
 * **It exists because two callers need exactly this and must not disagree.** The home surface re-fits the dock
 * whenever its grid changes (a height setting, a bigger icon), and the dock's settings editor applies a named edge
 * edit — different triggers, identical consequences for the items. Keeping them as two eight-line copies is how L1
 * ended up with `resolveDockDrop`, a drifted near-copy of its home resolver.
 *
 * @param dock the dock zone's placements, before.
 * @param main HOME's main-zone placements, which evicted items are placed *around*; they never move.
 * @param dockConfig the dock grid after the change.
 * @param mainConfig home's grid, needed only to place evictions.
 * @param edit the edge that changed, or **null** to simply re-fit the dock into [dockConfig] — the difference between
 *   "the user pressed − on the left" (shift everything left, the leftmost item leaves) and "the strip got shorter"
 *   (keep what still fits, re-home the rest).
 * @return the moves to persist, empty when nothing needs to change.
 */
fun settleDock(
    dock: Map<GridItem, GridPlacement>,
    main: Map<GridItem, GridPlacement>,
    dockConfig: GridConfig,
    mainConfig: GridConfig,
    edit: DockEdit? = null,
): List<LayoutChange> {
    val settled = if (edit == null) {
        GridReflow.reflow(dock, dockConfig, GridReflow.Overflow.EVICT)
    } else {
        GridReflow.edit(dock, edit.edge, edit.add, dockConfig, GridReflow.Overflow.EVICT)
    }
    if (!settled.changed) return emptyList()

    // Only what actually moved: both entry points return the whole zone, and re-stamping a row with the value it
    // already holds would be a write per item per resize.
    val moves = settled.placements
        .filterNot { (item, at) -> dock[item] == at }
        .map { (item, at) -> LayoutChange.Move(item, at, HomeZone.DOCK) }

    val evicted = settled.evicted.associateWith { dock.getValue(it) }
    if (evicted.isEmpty()) return moves

    val admitted = GridReflow.admit(evicted, main, mainConfig)
    return moves + admitted.placements.map { (item, at) -> LayoutChange.Move(item, at, HomeZone.MAIN) }
}
