package inkspire.morphic.core.designsystem.surface

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **Whether the surface a composable sits in is the one the user is looking at.** Provided per slot by
 * [SurfacePager]: true for HOME while the pan rests there, true for a side surface once it is more than half open,
 * false for everything else.
 *
 * This exists because *composed* and *on screen* stopped being the same thing the moment one [DragCoordinator]
 * spanned every surface. [SurfacePager] keeps all of its slots composed at all times — which is what satisfies the
 * drag toolkit's "keep a source surface composed while a drag from it is in flight" rule for free, and is the whole
 * reason a drag can leave the APPS drawer and carry on over HOME. The cost is that an off-screen surface's
 * `onGloballyPositioned` no longer reliably re-fires as the pan moves it, so the bounds it published while it *was*
 * on screen sit there in the drop registry claiming the finger from off-stage. `RegisterDropZone` reads this and
 * unregisters instead.
 *
 * Anything else that must belong to exactly one surface at a time reads it too — the floating drag proxy above all,
 * since two surfaces both drawing one would put two icons under a single finger.
 *
 * **Defaults to true**, for [ScrollEdges]' reason: a composable that is not inside a [SurfacePager] at all (settings,
 * a preview, a dev harness) is exactly as on-screen as its host, and the permissive value is what every such caller
 * behaved as before this existed.
 *
 * `compositionLocalOf`, not `staticCompositionLocalOf`, unlike its two neighbours here — this one actually changes,
 * twice per pan, and the static variant recomposes the **whole** subtree below the provider on any change rather than
 * just its readers. A handful of readers is what this has; a screenful of cells is what that would cost.
 */
val LocalSurfacePresented = compositionLocalOf { true }

/**
 * **Hands a drag lifted on a side surface back to HOME**: the shell closes this surface, and the *same* uninterrupted
 * gesture carries on over home's grids.
 *
 * The `EjectToHome` of docs/DRAG_AND_DROP_DESIGN.md §11, and it is deliberately tiny — one method, no app, no finger
 * position, no drag state. That is the measure of how much the shared coordinator bought: the drag is already in
 * flight on a coordinator HOME's zones are registered with, and the lifted cell already owns the pointer stream and
 * keeps it (its surface stays composed). So the only thing the surface cannot do for itself is *get out of the way*,
 * and that is the whole of this interface.
 *
 * Compare L1's `HomeDragBridge`, which had to pass the app, the finger position in window space and a grab offset,
 * because its side surfaces owned their own recognisers and its `CrossPager` stopped delivering events to either
 * subtree as it collapsed — so the finger had to be re-tracked from scratch at the common ancestor.
 *
 * Two callers, both in `feature:apps`, and the difference between them is *when* they call it: the derived layouts
 * (the A–Z list and grid) eject the moment the drag is lifted, since they have no arrangement of their own to
 * rearrange; the layouts that do store one eject only when the finger reaches the top action zone.
 */
fun interface EjectToHome {
    operator fun invoke()
}

/** Provides [EjectToHome] below the shell; null where there is no surface to close (previews, the dev harness). */
val LocalEjectToHome = staticCompositionLocalOf<EjectToHome?> { null }
