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
 * and what to write when a drag lands on it ([onDrop]) — the design doc's §10 rule, *behavior always travels with
 * the destination zone*, made structural. Both lambdas used to live on the surface instead, dispatched by a
 * `when (zone.id)`, which was workable only while a drag could never leave the surface it started on. It can now: an
 * app lifted in the APPS drawer is released by a cell in `feature:apps`, and the thing that has to commit it is
 * **home's** grid. Nothing in the releasing surface knows how to do that, and nothing should.
 *
 * @property id stable identity used to key the registry and report the active target.
 * @property bounds the zone's rectangle in root/window coordinates, reported by the surface as it measures or
 *   moves; hit-testing against the measured bounds is what stops hit geometry drifting from what's drawn (the
 *   alternative being a hardcoded tap radius).
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
 * registration driven by layout can never be revoked or restored. `AppCollectionOverlay` had already learned this and kept
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
    // **This composition's identity as a registrar**, and the token the coordinator records against the id — which
    // is what lets the teardown below say "give up the registration if it is still mine" without naming a value.
    // `remember`ed, so it is stable for as long as this call site lives and is a different object from every other
    // registrar's, including another node publishing the same id.
    //
    // It also tracks whether anything is currently published, so a teardown that owes nothing does nothing. A plain
    // holder rather than snapshot state: nothing reads it in composition, and making it observable would only invite
    // something to.
    val registered = remember { RegisteredZone() }
    // **Both halves run from the same state, in the same effect**, and that symmetry is the correctness argument
    // rather than tidiness. Publishing is re-done on every composition instead of being keyed on the value: a
    // [DropZone] carries three lambdas, so it is a fresh instance each time and keying an effect on it would tear the
    // zone down and put it back on every frame of a drag. The lambdas genuinely have to be refreshed — they close over
    // the surface's live state — so the put is real work, not a redundant write. `SideEffect` is the sanctioned place
    // to push composition state into a snapshot map, and nothing composes on the registry.
    //
    // Withdrawal used to be an *edge* instead: a `DisposableEffect` keyed on `active == null`, firing on the
    // transition rather than on the condition. A zone published from a state and withdrawn from a transition can
    // outlive its own disabling — any path reaching "not enabled" without flipping that key leaves the zone in the
    // registry answering for a node nobody can see. That is invisible in a way ordinary bugs are not: `ZoneId`
    // `"folder"` is **shared** by every folder overlay, so a stale one is not a duplicate but *the* folder zone, and
    // at `z = 1` it outranks the surface beneath it. The symptom is a rectangle in the middle of the screen — exactly
    // where an open folder's card sits — that silently swallows every drop while the rest of the surface behaves.
    //
    // What makes the withdrawal *land* is that it names the **id it published** and identifies itself by
    // [registered], rather than handing back the zone value it last built. Two nodes sharing an id can still hand it
    // over inside one composition — the successor becomes the owner and the predecessor's withdrawal correctly does
    // nothing — but a registrar giving up an id it still owns can never be refused. Passing the value instead made
    // that refusable, and it happened: see [DragCoordinator.unregisterZone].
    SideEffect {
        if (active != null) {
            coordinator.registerZone(active, registered)
            registered.publishedId = active.id
        } else {
            registered.publishedId?.let { coordinator.unregisterZone(it, registered) }
            registered.publishedId = null
        }
    }
    // Disposal is the one case the effect above cannot cover: a composable that leaves the tree gets no further
    // composition, so it has to hand its zone back on the way out. Keyed on identity alone — a bounds change is
    // already carried above, and re-keying on it would tear the zone down and put it back on every frame of a drag.
    DisposableEffect(coordinator, zone?.id) {
        onDispose {
            registered.publishedId?.let { coordinator.unregisterZone(it, registered) }
            registered.publishedId = null
        }
    }
}

/**
 * One [RegisterDropZone] call's identity as a registrar, and the id it currently has published (null when none).
 *
 * The object itself is the ownership token the coordinator records — see [DragCoordinator.unregisterZone]. Only the
 * *id* is kept beside it, never the [DropZone]: a fresh zone instance is republished every composition, so a stored
 * value would say nothing about whether this registrar still holds the id.
 */
private class RegisteredZone {
    var publishedId: ZoneId? = null
}
