package inkspire.morphic.core.designsystem.grid

import inkspire.morphic.core.model.SideZoneEdge

/**
 * How HOME's window divides between its two zones.
 *
 * @property main what the main area gets — the window minus the side zone, on whichever axis the two are stacked.
 * @property side what the side zone gets — its configured extent on that axis, and the whole of the other.
 */
data class SideZoneSplit(val main: GridArea, val side: GridArea)

/**
 * **Divide this window between HOME's main area and its side zone**, given the zone's extent and which edge it is on.
 *
 * One function returning **both** halves, for the reason [DerivedCell] returns a height and its metrics together: the
 * two are complementary and using one without the other is the bug worth designing out. Three callers need this
 * agreement and cannot check each other — the home surface (which draws them), the Home settings section (which bounds
 * the main area's rows against what is left) and the side-zone section (which bounds its own counts *and* re-checks the
 * main area's). L1 had `homeGridArea(window, insets, landscape, dockVisible, dockThickness)` doing the same subtraction
 * and its home surface measuring the result a second way (`pagerBoundsInWindow`), so the size it fitted a grid to and
 * the size it drew into could disagree.
 *
 * **The edge decides only *which dimension* is subtracted, never *how much*.** A strip at the top and a strip at the
 * bottom take the same height from the same window; what differs is only where each half is *drawn*, which is the
 * caller's `Column`/`Row` ordering rather than an arithmetic difference. That is why this reads
 * [SideZoneEdge.isStrip] and not the edge itself — two cases, not four.
 *
 * The extent is subtracted **whole**: a zone's own horizontal margin is that zone's, applied to whichever half it
 * belongs to afterwards, so it is deliberately not an argument here.
 *
 * Both halves are floored at 1dp rather than allowed to reach zero — a grid is always fitted against this, and
 * `maxCells` on a zero area would report a cell count for a zone with no room at all.
 *
 * @param extentDp the side zone's height (on a strip) or width (on a rail), in dp.
 */
fun GridArea.splitForSideZone(extentDp: Float, edge: SideZoneEdge): SideZoneSplit = if (edge.isStrip) {
    SideZoneSplit(
        main = copy(heightDp = (heightDp - extentDp).coerceAtLeast(1f)),
        side = copy(heightDp = extentDp.coerceAtLeast(1f)),
    )
} else {
    SideZoneSplit(
        main = copy(widthDp = (widthDp - extentDp).coerceAtLeast(1f)),
        side = copy(widthDp = extentDp.coerceAtLeast(1f)),
    )
}

/**
 * The fraction of the window the side zone takes, along the axis it is stacked on — what a grid editor draws its
 * companion zone at.
 *
 * Here rather than at the two call sites because it must be the *same* axis the split used: reading a rail's share off
 * the height would draw a companion that changes size when nothing about the zone did.
 */
fun GridArea.sideZoneFraction(extentDp: Float, edge: SideZoneEdge): Float =
    if (edge.isStrip) extentDp / heightDp.coerceAtLeast(1f) else extentDp / widthDp.coerceAtLeast(1f)
