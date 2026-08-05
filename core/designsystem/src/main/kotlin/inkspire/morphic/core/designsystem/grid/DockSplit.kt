package inkspire.morphic.core.designsystem.grid

import inkspire.morphic.core.model.DockEdge

/**
 * How HOME's window divides between its two zones.
 *
 * @property main what the pager gets — the window minus the dock, on whichever axis the dock is stacked.
 * @property dock what the dock gets — its configured extent on that axis, and the whole of the other.
 */
data class DockSplit(val main: GridArea, val dock: GridArea)

/**
 * **Divide this window between the pager and the dock**, given the dock's extent and which edge it sits on.
 *
 * One function returning **both** halves, for the reason [DerivedCell] returns a height and its metrics together: the
 * two are complementary and using one without the other is the bug worth designing out. Three callers need this
 * agreement and cannot check each other — the home surface (which draws them), the Home settings section (which bounds
 * home's rows against what is left) and the Dock section (which bounds the dock's counts *and* re-checks home's). L1
 * had `homeGridArea(window, insets, landscape, dockVisible, dockThickness)` doing the same subtraction and its home
 * surface measuring the result a second way (`pagerBoundsInWindow`), so the size it fitted a grid to and the size it
 * drew into could disagree.
 *
 * The dock's extent is subtracted **whole**: a zone's own horizontal margin is that zone's, applied to whichever half
 * it belongs to afterwards, so it is deliberately not an argument here.
 *
 * Both halves are floored at 1dp rather than allowed to reach zero — a grid is always fitted against this, and
 * `maxCells` on a zero area would report a cell count for a zone with no room at all.
 *
 * @param extentDp the dock's height (on [DockEdge.BOTTOM]) or width (on [DockEdge.END]), in dp.
 */
fun GridArea.splitForDock(extentDp: Float, edge: DockEdge): DockSplit = when (edge) {
    DockEdge.BOTTOM -> DockSplit(
        main = copy(heightDp = (heightDp - extentDp).coerceAtLeast(1f)),
        dock = copy(heightDp = extentDp.coerceAtLeast(1f)),
    )
    DockEdge.END -> DockSplit(
        main = copy(widthDp = (widthDp - extentDp).coerceAtLeast(1f)),
        dock = copy(widthDp = extentDp.coerceAtLeast(1f)),
    )
}

/**
 * The fraction of the window the dock takes, along the axis it is stacked on — what a grid editor draws its companion
 * zone at.
 *
 * Here rather than at the two call sites because it must be the *same* axis the split used: reading a rail's share off
 * the height would draw a companion that changes size when nothing about the dock did.
 */
fun GridArea.dockFraction(extentDp: Float, edge: DockEdge): Float = when (edge) {
    DockEdge.BOTTOM -> extentDp / heightDp.coerceAtLeast(1f)
    DockEdge.END -> extentDp / widthDp.coerceAtLeast(1f)
}
