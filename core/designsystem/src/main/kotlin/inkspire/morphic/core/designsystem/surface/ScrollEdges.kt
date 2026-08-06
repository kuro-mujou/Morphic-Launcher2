package inkspire.morphic.core.designsystem.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import inkspire.morphic.core.model.HomeEdge

/**
 * **Where a surface's scrollable content is resting** — which of its own four edges it has currently reached.
 *
 * The live half of the pair whose static half is [ScrollAxes]: the axes say a hand-off is *possible* on this axis,
 * these say it may happen *now*. Together they are the whole of [OneFingerSwipe.AT_EDGE] — the nested-scroll
 * hand-off, and the only thing that made `AT_EDGE` behave identically to [OneFingerSwipe.ALWAYS] until this existed.
 *
 * **Every field defaults to true, and that default is load-bearing twice.** Content that does not scroll on an axis
 * genuinely *is* at both of that axis's edges, so a vertical list fills in its horizontal pair by saying nothing. And
 * a surface that reports nothing at all — a layout not yet wired, a preview, the dev harness — reads as at every
 * edge, which is precisely how the launcher behaved before this landed. The permissive value is the safe one: it can
 * make a swipe cross a fraction early, never trap the user on a surface they cannot swipe off.
 *
 * Named by **physical** edge rather than by scroll direction because that is what it is compared against — a
 * [HomeEdge], which is physical (the LEFT surface really is parked on the left). The one place that costs anything is
 * a horizontal pager under RTL, where the first page is on the right; `LauncherPager` lays out in raw pixels with no
 * layout-direction handling today, so the two agree, and the day it gains one this mapping is where it shows.
 *
 * Ported from L1's `SurfaceScrollEdges`, with the same four booleans and two differences. L1 had HOME answer the
 * same question through a *second*, differently-shaped type (`HomeGestureRelease`'s `swipeRightOpensLeft`/…), which
 * conflated the live edge fact with the policy and could not express a scrolled home list at all; here one type is
 * reported by HOME and by every side surface, and the policy is [ScrollAxes]' job. And L1's reporters blanked every
 * field to false while an item drag was in flight, to stop the pan claiming mid-drag — L2 answers that one layer up,
 * with `SurfaceGestureLock`, which gates the whole gesture rather than lying about where the content is.
 */
@Immutable
data class ScrollEdges(
    val atLeft: Boolean = true,
    val atRight: Boolean = true,
    val atTop: Boolean = true,
    val atBottom: Boolean = true,
) {
    /** Whether the content has reached the edge facing [edge]. */
    operator fun get(edge: HomeEdge): Boolean = when (edge) {
        HomeEdge.LEFT -> atLeft
        HomeEdge.RIGHT -> atRight
        HomeEdge.TOP -> atTop
        HomeEdge.BOTTOM -> atBottom
    }
}

/**
 * The channel one surface slot answers [ScrollEdges] through: content [ReportScrollEdges]s into it, and
 * [surfacePagerGesture] asks it at the moment it must decide whether a one-finger swipe is the pan's or the
 * content's.
 *
 * **It holds a lambda, not a value, and that is the point.** The question is asked exactly once per gesture, from
 * inside a pointer callback — so reading the scroll states *there* costs nothing and is never stale. Publishing a
 * value instead would mean the reporter reads `canScrollForward` during composition, and for a `ScrollState` that is
 * a raw read of `value`: the surface would recompose once per scroll frame purely to keep this in step. That is the
 * trap `CategoryPage`'s geometry comment already warns about on the same surface, and L1 fell into it — its
 * `PlainGridLayout` and `IosCategoryGridLayout` read `gridState.canScrollBackward` straight into composition.
 *
 * A plain `var` rather than snapshot state for the same reason: nothing composes against it, so a write should
 * invalidate nothing.
 */
@Stable
class ScrollEdgeSlot {

    /** The content's own answer, or null while nothing in this slot reports — see [ScrollEdges]' permissive default. */
    internal var provider: (() -> ScrollEdges)? = null

    /** Where the content in this slot is resting right now. */
    internal fun edges(): ScrollEdges = provider?.invoke() ?: ScrollEdges()
}

/**
 * The slot the content of the current surface reports into, or null outside a [SurfacePager] (previews, the settings
 * zone, a layout composed on its own).
 *
 * `staticCompositionLocalOf` because the pager provides one per slot and never changes it — what moves is the lambda
 * inside, which is not snapshot state at all.
 */
val LocalScrollEdgeSlot = staticCompositionLocalOf<ScrollEdgeSlot?> { null }

/**
 * **Reports where this surface's scrollers are resting**, so a one-finger swipe toward a surface boundary can hand
 * off to the pan once the content has nowhere left to go.
 *
 * One call per surface, not one per scroller: [edges] is a single answer covering both axes, so a layout that owns a
 * pager *and* a per-page scroll builds one value from both. A second call in the same slot would overwrite the first
 * rather than combine with it, which is why the parameter is the whole record and not one edge.
 *
 * [edges] is a **lambda** and is invoked from a pointer callback, never from composition — see [ScrollEdgeSlot]. So
 * it may read scroll state freely, and must not be a value computed above the call.
 *
 * No-op where there is no [LocalScrollEdgeSlot], which is every context but the launcher shell.
 */
@Composable
fun ReportScrollEdges(edges: () -> ScrollEdges) {
    val slot = LocalScrollEdgeSlot.current ?: return
    val current = rememberUpdatedState(edges)
    // Keyed on the slot alone: `current` is re-read on every invocation, so a recomposition needs no re-registration.
    // The guard on dispose is the one this codebase already applies to the folder's shared drop zone — a layout
    // swapped for another (an `AppsLayout` change) composes the newcomer before disposing the outgoing one in some
    // orderings, and an unguarded clear would tear out the report that had just replaced it.
    DisposableEffect(slot) {
        val published = { current.value() }
        slot.provider = published
        onDispose { if (slot.provider === published) slot.provider = null }
    }
}
