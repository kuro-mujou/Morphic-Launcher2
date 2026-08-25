package inkspire.morphic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import inkspire.morphic.core.designsystem.container.ArrangementSlot
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.PlacementPlan

/**
 * What an icon container should draw right now, given whatever drag is in flight over it.
 *
 * @property slots where the icons go — **as many as the container is about to hold**, so an icon arriving from
 *   outside is previewed by the arrangement opening up for it rather than by the icons staying put and jumping on
 *   drop.
 * @property shown one entry per slot, in slot order. A `null` is the **gap** the incoming icon will fill: it has
 *   no `ContainerIcon` here because it is not a member yet, and the floating proxy under the finger is already
 *   drawing it.
 * @property lifted the icon being carried out of *this* container, which the caller draws invisible. It keeps its
 *   place rather than being removed: the proxy stands in for it, and taking it out would re-flow the arrangement
 *   around a gap that is about to be filled again.
 */
internal data class IconContainerPreview(
    val slots: List<ArrangementSlot>,
    val shown: List<ContainerIcon?>,
    val lifted: IconItem?,
)

/**
 * Registers the container's own drop zone and reports what it should draw while a drag is over it.
 *
 * **A zone of its own rather than home's merge ring.** Merging onto a container is gated on the finger being
 * inside a small circle at its center (`inMergeRingOf`), which is the right target for "put this *in* there" and
 * useless for "put this at *that* slot" — most of the slots lie outside that circle. So the container registers
 * its whole rectangle above the grid, exactly as an open collection does, and answers for its own hover and drop
 * (`DropZone`'s rule: behavior travels with the destination).
 *
 * **Two gestures, told apart by whether the container already holds what is being carried.**
 * - A **member** is being rearranged, so the drop *exchanges* it with the slot's occupant and nothing else moves.
 *   The stored order is a list, but a container renders it into a shape — a ring, a honeycomb spiral — where
 *   "before this one" is not a place a finger can point at, so what the user is aiming at is a *position*.
 * - A **newcomer** has no position to trade, so it is *inserted* at the slot and what was there shifts along.
 *
 * A folder reorders by MovingGap insert throughout and should: its grid has a reading order.
 *
 * **Accepting anything an icon container can hold is what costs the push-past.** While the finger is inside these
 * bounds the home grid is not the drop target, so an app can no longer be dragged *over* a container to shove it
 * aside — it goes in instead. That is the trade an open collection already makes, and the container is still moved
 * by dragging the container.
 *
 * @param containerId null for a container being drawn as something other than itself — the floating drag proxy,
 *   the widget picker's preview. Those register nothing, which is what stops a proxy's zone shadowing the real
 *   container it is a picture of.
 * @param bounds the cell's rectangle in root coordinates, or null before it has been measured.
 */
@Composable
internal fun rememberIconContainerPreview(
    containerId: Long?,
    icons: List<ContainerIcon>,
    arrangement: IconArrangement,
    size: Size,
    bounds: Rect?,
    onReorder: (List<IconItem>) -> Unit,
    onInsert: (IconItem, Int) -> Unit,
): IconContainerPreview {
    val coordinator = LocalDragCoordinator.current
    val density = LocalDensity.current
    val members = icons.map { it.asIconItem() }
    val carried = coordinator?.session?.item?.asIconItem()
    val lifted = carried?.takeIf { it in members }
    // Something this container does not hold, hovering over it — so it is about to gain a slot. Resolved before
    // the slots are laid out, because it is what decides how many there are.
    val incoming = carried?.takeIf { it !in members && coordinator.session?.activeZone == zoneIdOf(containerId) }

    val slotCount = icons.size + if (incoming != null) 1 else 0
    val slots = remember(arrangement, slotCount, size, density) {
        iconContainerSlots(arrangement, slotCount, size.width, size.height, density)
    }

    // Which slot the finger is over. Written by the planner, which makes that a *command* rather than a query —
    // the open collection's reorder gap is the same thing for the same reason, and it is safe because the
    // coordinator resolves a plan exactly once per finger move.
    var hovered by remember { mutableStateOf(-1) }
    LaunchedEffect(carried) { if (carried == null) hovered = -1 }

    if (coordinator != null && containerId != null && bounds != null) {
        RegisterDropZone(
            coordinator = coordinator,
            zone = DropZone(
                id = zoneIdOf(containerId)!!,
                bounds = bounds,
                // Above the grid's own zone, so the finger inside a container is the container's. The open
                // collection sits at the same height and never overlaps one: it is a full-screen overlay, and
                // home's cells are not composed as drop targets behind it.
                z = 1,
                accepts = { it.asIconItem() != null },
                planner = { _, finger ->
                    hovered = slots.nearestIndexTo(finger - bounds.topLeft) ?: -1
                    // The footprint goes unread on a REORDER intent — `DropFootprint` returns early on it — and
                    // should: the preview here is the container rearranging itself, so there is no target cell for
                    // a drop shadow to name.
                    PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REORDER)
                },
                onDrop = { outcome ->
                    commitDrop(outcome.item.asIconItem(), members, hovered, onReorder, onInsert)
                    hovered = -1
                },
            ),
        )
    }

    // The same rearrangement the drop will commit, which is the point of computing it here: the preview cannot
    // promise something the drop then does differently.
    return IconContainerPreview(slots, previewOrder(icons, members, lifted, incoming != null, hovered), lifted)
}

/** The zone id a container publishes, or null for one that is not itself (a proxy, a picker preview). */
private fun zoneIdOf(containerId: Long?): ZoneId? = containerId?.let { ZoneId("icon_container_$it") }

/**
 * This list with the items at [a] and [b] exchanged — unchanged if either index is out of range or they are the
 * same one, which is what makes "no slot hovered" (`-1`) and "hovering the one being carried" both mean *nothing
 * moves* without either caller testing for them.
 */
private fun <T> List<T>.exchanging(a: Int, b: Int): List<T> =
    if (a !in indices || b !in indices || a == b) this
    else toMutableList().also { it[a] = this[b]; it[b] = this[a] }

/**
 * Writes the drop: an exchange for something the container already holds, an insert for anything else.
 *
 * A no-op for a release that resolved to no slot, and for an exchange that would leave the order as it was —
 * neither is a change the user made, and writing one would still cost a round trip through the store.
 */
private fun commitDrop(
    dropped: IconItem?,
    members: List<IconItem>,
    hovered: Int,
    onReorder: (List<IconItem>) -> Unit,
    onInsert: (IconItem, Int) -> Unit,
) {
    if (dropped == null || hovered < 0) return
    val from = members.indexOf(dropped)
    if (from < 0) {
        onInsert(dropped, hovered)
    } else {
        members.exchanging(from, hovered).takeIf { it != members }?.let(onReorder)
    }
}

/**
 * What to draw in each slot: the stored order, the previewed exchange, or the order with a hole opened for an
 * icon arriving from outside.
 *
 * The same rearrangement [commitDrop] will write, which is the point of computing it here — the preview cannot
 * promise something the drop then does differently.
 */
private fun previewOrder(
    icons: List<ContainerIcon>,
    members: List<IconItem>,
    lifted: IconItem?,
    incoming: Boolean,
    hovered: Int,
): List<ContainerIcon?> = when {
    incoming && hovered >= 0 ->
        icons.toMutableList<ContainerIcon?>().also { it.add(hovered.coerceIn(0, it.size), null) }

    else -> icons.exchanging(lifted?.let { members.indexOf(it) } ?: -1, hovered)
}
