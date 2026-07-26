package inkspire.morphic.core.designsystem.surface

import androidx.compose.runtime.Composable
import inkspire.morphic.core.model.HomeEdge

/**
 * When a **one-finger** swipe alone may cross a surface boundary. (A two-finger swipe always may — it bypasses
 * the content underneath, which is the whole point of using two fingers.)
 *
 * The value is decided by the content sitting under the swipe *on that axis* — HOME's content for opening, the
 * side surface's own content for closing:
 * - [ALWAYS] — nothing scrolls on this axis, so a one-finger swipe pans immediately (a vertical list under a
 *   horizontal swipe; a horizontal pager under a vertical swipe).
 * - [AT_EDGE] — a **bounded** scroller owns this axis: the one-finger swipe first scrolls that content, and
 *   only once it's scrolled to its far edge does the leftover hand off to the surface pan (nested scroll). Two
 *   fingers skip straight to the pan. The exact edge (start/end/top/bottom) isn't stored here — it's just
 *   "the scroller can't move further in the swipe's direction," read from the scroll state when we wire it.
 * - [NEVER] — an **infinite** scroller owns this axis (a wrap-around pager): there's no edge to hand off from,
 *   so one finger can never cross and only a two-finger swipe pans.
 *
 * Set by the shell from the layout on each side — the pager/list/grid don't exist yet, so today's demos choose
 * the value directly.
 */
enum class OneFingerSwipe {
    /** One finger crosses immediately — nothing under it scrolls on this axis. */
    ALWAYS,

    /** One finger crosses only after the (bounded) content under it scrolls to its edge; two fingers any time. */
    AT_EDGE,

    /** One finger never crosses — the (infinite) content owns this axis with no edge to hand off from. */
    NEVER,
}

/**
 * The one-finger policy for one edge, split by direction of travel — the two collide with *different* content.
 * Published to [SurfacePagerState] so the gesture can gate opening and closing independently.
 *
 * @param open crossing HOME → surface; decided by HOME's content on the edge's axis.
 * @param close crossing surface → HOME; decided by that surface's own content on the edge's axis.
 */
data class EdgeSwipe(val open: OneFingerSwipe, val close: OneFingerSwipe)

/**
 * A side surface bound to one [HomeEdge]: the [content] to show, and the one-finger policy to pan to and from
 * it. Co-locating them matches the model's "each edge is bound independently" rule — a binding owns both what's
 * behind the edge and how the user crosses it in each direction.
 *
 * @param openSwipe how HOME reaches this surface — driven by HOME's content on the edge's axis.
 * @param closeSwipe how this surface returns to HOME — driven by this surface's own content on the edge's axis.
 *   Defaults to [OneFingerSwipe.ALWAYS] for a surface that doesn't scroll along the close axis.
 * @param content the surface itself.
 */
class SurfaceBinding(
    val openSwipe: OneFingerSwipe,
    val closeSwipe: OneFingerSwipe = OneFingerSwipe.ALWAYS,
    val content: @Composable () -> Unit,
)
