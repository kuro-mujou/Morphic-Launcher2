package inkspire.morphic.core.designsystem.drag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.model.GridItem

/**
 * A registered drop target living in the shared **root/window** coordinate space. Every drag-participating
 * surface contributes one; the coordinator hit-tests the finger against all of them at once. That single
 * shared space is what makes cross-surface drops and dragging out of a folder "just work" with no per-surface
 * handoff (docs/DRAG_AND_DROP_DESIGN.md §4).
 *
 * **A zone answers for itself, end to end.** It says what it accepts, what a hover over it would do ([planner]),
 * and what to write when a drag lands on it ([onDrop]) — the design doc's §10 rule, *behaviour always travels with
 * the destination zone*, made structural. Both lambdas used to live on the surface instead, dispatched by a
 * `when (zone.id)`, which was workable only while a drag could never leave the surface it started on. It can now: an
 * app lifted in the APPS drawer is released by a cell in `feature:apps`, and the thing that has to commit it is
 * **home's** grid. Nothing in the releasing surface knows how to do that, and nothing should.
 *
 * @property id stable identity used to key the registry and report the active target.
 * @property bounds the zone's rectangle in root/window coordinates, reported by the surface as it measures or
 *   moves; hit-testing against the measured bounds is what stops hit geometry drifting from what's drawn (the
 *   L1 smell of a hardcoded tap radius).
 * @property z stacking order: when zones overlap (an open folder above home), the highest [z] wins the finger.
 * @property planner what a drop here would do, asked on every finger move while this zone is the active one.
 * @property accepts whether this zone will take the given dragged item — an A–Z drawer refuses a home item, a
 *   widget zone refuses an app. Zones that reject the item are skipped, so the finger falls through to
 *   whatever sits beneath them.
 * @property onDrop commits a landing in this zone. Invoked by [DragCoordinator.drop] **before** it returns, so the
 *   surface that owns the zone writes the change while the surface that released the finger is still none the wiser.
 *   Not called for a release over no zone or over an invalid plan — those are no landings at all.
 */
data class DropZone(
    val id: ZoneId,
    val bounds: Rect,
    val z: Int,
    val planner: DropPlanner,
    val accepts: (GridItem) -> Boolean = { true },
    val onDrop: (DropOutcome) -> Unit = {},
)

/**
 * Keeps [zone] registered with [coordinator] for as long as it is non-null and [enabled], and tears it down
 * otherwise. The one way a surface should publish a drop target.
 *
 * **Why a composable rather than a call inside `onGloballyPositioned`** (which is what every surface did before):
 * registration has two inputs, and only one of them is a measurement. The other is *whether this surface is on
 * screen at all* — and a surface panned off the side of the launcher is laid out exactly once, on its way out, so a
 * registration driven by layout can never be revoked or restored. `FolderOverlay` had already learned this and kept
 * its bounds in state for the same reason; this is that pattern given a name so the other seven zones share it.
 *
 * That gate is what keeps a shared coordinator honest. HOME and every side surface stay composed at all times (the
 * §5 "keep a source surface composed" rule falls out of `SurfacePager` for free), so without it the APPS pager's
 * viewport zone would go on claiming the finger from behind HOME the moment a drag was ejected onto it.
 *
 * @param enabled defaults to [LocalSurfacePresented], so the gate cannot be forgotten: a surface that is not the one
 *   the user is looking at registers nothing. Pass an explicit value only to add a *further* condition (an open
 *   folder's pointer holder passes `presenting`).
 */
@Composable
fun RegisterDropZone(
    coordinator: DragCoordinator,
    zone: DropZone?,
    enabled: Boolean = LocalSurfacePresented.current,
) {
    val active = zone?.takeIf { enabled }
    // What is in the registry right now, so the teardown below can remove *that* rather than whichever instance the
    // effect happened to be created with. A plain holder, not snapshot state: nothing reads it in composition, and
    // making it observable would only invite something to.
    val registered = remember { RegisteredZone() }
    // Re-published on every composition rather than keyed on the value: a [DropZone] carries three lambdas, so it is
    // a fresh instance each time and keying a `DisposableEffect` on it would unregister and re-register the zone on
    // every frame of a drag. The lambdas do have to be refreshed — they close over the surface's live state — so the
    // put is real work, not a redundant write. `SideEffect` is the sanctioned place to push composition state into a
    // snapshot map, and nothing composes on the registry.
    SideEffect {
        if (active != null) {
            coordinator.registerZone(active)
            registered.value = active
        }
    }
    // Teardown is the part that genuinely has a lifetime: keyed on identity and presence only, so it fires when the
    // zone goes away or the surface leaves the screen, and not on a bounds change (which the SideEffect above
    // already carried). It removes the zone **by instance**, so a node handing a shared id over to another node in
    // the same composition cannot delete the successor's registration — see [DragCoordinator.unregisterZone].
    DisposableEffect(coordinator, zone?.id, active == null) {
        onDispose {
            registered.value?.let(coordinator::unregisterZone)
            registered.value = null
        }
    }
}

/** The zone instance [RegisterDropZone] last put in the registry — see the note at its only use. */
private class RegisteredZone {
    var value: DropZone? = null
}
