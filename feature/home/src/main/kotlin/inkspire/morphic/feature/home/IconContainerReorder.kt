package inkspire.morphic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.designsystem.container.ArrangementSlot
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.PlacementPlan

/**
 * What an icon container should draw while one of its own icons is being dragged around inside it.
 *
 * @property shown the membership in the order to render *right now* — the stored order, or the previewed exchange
 *   while the finger is over a slot.
 * @property lifted the icon currently being carried, which the caller draws invisible. It keeps its place in
 *   [shown] rather than being removed: the floating proxy under the finger is standing in for it, and taking it
 *   out of the list would re-flow the arrangement around a gap that is about to be filled.
 */
internal data class IconContainerReorder(
    val shown: List<ContainerIcon>,
    val lifted: IconItem?,
)

/**
 * Registers the container's own drop zone and reports the reorder it is previewing.
 *
 * **A zone of its own rather than home's merge ring.** Merging onto a container is gated on the finger being
 * inside a small circle at its center (`inMergeRingOf`), which is the right target for "put this *in* there" and
 * useless for "put this at *that* slot" — most of the slots lie outside that circle. So the container registers
 * its whole rectangle above the grid, exactly as an open collection does, and answers for its own hover and drop
 * (`DropZone`'s rule: behavior travels with the destination).
 *
 * **It accepts only what it already holds**, which confines this to reordering and leaves an app arriving from
 * elsewhere to the merge ring it uses today. Without that narrowing the container would swallow every drop that
 * crossed it, and an app could no longer be dragged *past* one to push it aside.
 *
 * **A drop exchanges two icons; it does not insert between them.** The stored order is a list, but a container
 * renders it into a shape — a ring, a honeycomb spiral — where "before this one" is not a place a finger can
 * point at. What the user is aiming at is a *position*, so its occupant and the carried icon trade places and
 * nothing else moves. A folder reorders by MovingGap insert instead, and should: its grid has a reading order.
 *
 * @param containerId null for a container being drawn as something other than itself — the floating drag proxy,
 *   the widget picker's preview. Those register nothing, which is what stops a proxy's zone shadowing the real
 *   container it is a picture of.
 * @param bounds the cell's rectangle in root coordinates, or null before it has been measured.
 */
@Composable
internal fun rememberIconContainerReorder(
    containerId: Long?,
    icons: List<ContainerIcon>,
    slots: List<ArrangementSlot>,
    bounds: Rect?,
    onReorder: (List<IconItem>) -> Unit,
): IconContainerReorder {
    val coordinator = LocalDragCoordinator.current
    val members = icons.map { it.asIconItem() }
    val lifted = coordinator?.session?.item?.asIconItem()?.takeIf { it in members }

    // Which slot the finger is over. Written by the planner, which makes that a *command* rather than a query —
    // the open collection's reorder gap is the same thing for the same reason, and it is safe because the
    // coordinator resolves a plan exactly once per finger move.
    var hovered by remember { mutableStateOf(-1) }
    LaunchedEffect(lifted) { if (lifted == null) hovered = -1 }

    if (coordinator != null && containerId != null && bounds != null) {
        RegisterDropZone(
            coordinator = coordinator,
            zone = DropZone(
                id = ZoneId("icon_container_$containerId"),
                bounds = bounds,
                // Above the grid's own zone, so the finger inside a container is the container's. The open
                // collection sits at the same height and never overlaps one: it is a full-screen overlay, and
                // home's cells are not composed as drop targets behind it.
                z = 1,
                accepts = { item -> item.asIconItem() in members },
                planner = { _, finger ->
                    hovered = slots.nearestIndexTo(finger - bounds.topLeft) ?: -1
                    // The footprint goes unread on a REORDER intent — `DropFootprint` returns early on it — and
                    // should: the preview here is two icons trading places, so there is no target cell for a drop
                    // shadow to name.
                    PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)
                },
                onDrop = { outcome ->
                    val reordered = members.exchanging(members.indexOf(outcome.item.asIconItem()), hovered)
                    if (reordered != members) onReorder(reordered)
                    hovered = -1
                },
            ),
        )
    }

    // The same exchange the drop will commit, which is the point of it being one function: the preview cannot
    // promise a rearrangement the drop then performs differently.
    return IconContainerReorder(icons.exchanging(lifted?.let { members.indexOf(it) } ?: -1, hovered), lifted)
}

/**
 * This list with the items at [a] and [b] exchanged — unchanged if either index is out of range or they are the
 * same one, which is what makes "no slot hovered" (`-1`) and "hovering the one being carried" both mean *nothing
 * moves* without either caller testing for them.
 */
private fun <T> List<T>.exchanging(a: Int, b: Int): List<T> =
    if (a !in indices || b !in indices || a == b) this
    else toMutableList().also { it[a] = this[b]; it[b] = this[a] }
