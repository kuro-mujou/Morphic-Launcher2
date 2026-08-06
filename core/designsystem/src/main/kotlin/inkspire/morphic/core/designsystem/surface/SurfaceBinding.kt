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
 *   "the scroller can't move further in the swipe's direction," which is what [ScrollEdges] answers, live, at
 *   the moment the gesture asks.
 * - [NEVER] — an **infinite** scroller owns this axis (a wrap-around pager): there's no edge to hand off from,
 *   so one finger can never cross and only a two-finger swipe pans.
 *
 * Set by the shell from the layout on each side — see [ScrollAxes], which is the rule that produces these values.
 */
enum class OneFingerSwipe {
    /** One finger crosses immediately — nothing under it scrolls on this axis. */
    ALWAYS,

    /** One finger crosses only after the (bounded) content under it scrolls to its edge; two fingers any time. */
    AT_EDGE,

    /** One finger never crosses — the (infinite) content owns this axis with no edge to hand off from. */
    NEVER,
    ;

    /**
     * Whether a **one-finger** swipe may cross right now, given whether the content under it has reached the edge
     * it is being pulled toward ([atEdge], from [ScrollEdges]).
     *
     * The whole of the policy, in one place, so opening and closing cannot answer it differently — they differ only
     * in *whose* content is asked and which of its edges.
     */
    fun allows(atEdge: Boolean): Boolean = when (this) {
        ALWAYS -> true
        AT_EDGE -> atEdge
        NEVER -> false
    }
}

/**
 * How content behaves on **one axis**, which is the only thing a [OneFingerSwipe] is derived from.
 *
 * The three cases are exhaustive because they are the three answers to "is there an edge to hand off from?":
 * nothing scrolls (every point is an edge), a bounded scroller (two edges), an infinite one (none).
 */
enum class AxisScroll {
    /** Nothing scrolls on this axis. */
    NONE,

    /** A bounded scroller owns this axis — it has a start and an end to hand off from. */
    BOUNDED,

    /** A wrap-around scroller owns this axis, so it never reaches an edge. */
    INFINITE,
    ;

    companion object {
        /**
         * A pager's axis, given whether its pages wrap: [INFINITE] when they do, [BOUNDED] when they stop at the ends.
         *
         * The whole of what a wrap toggle means to the surface swipe, in one expression rather than the same `if` in
         * each of the three features that own a pager. Here beside the enum because it *is* the enum's rule; which
         * pagers wrap is a settings question and stays there.
         */
        fun ofPager(wraps: Boolean): AxisScroll = if (wraps) INFINITE else BOUNDED
    }
}

/**
 * **What a surface's content does on each axis** — the static fact a surface publishes about itself, from which its
 * one-finger policy on every edge follows.
 *
 * Deliberately paired with, and named against, [ScrollEdges]: *axes* is what kind of scroller owns each direction and
 * never changes while a layout is on screen, *edges* is where that scroller is resting right now. One decides whether
 * a hand-off is even possible, the other whether it may happen this instant.
 *
 * This is the surface-pager playground's private `Scroll` enum and its `toSwipe()` promoted to a real type. The
 * harness worked the rule out against simulated layouts; the real layouts now answer with the same rule rather than
 * a second copy of it — which is exactly what L1 did not have, and why its HOME side (`HomeGestureRelease`) and its
 * side-surface side (`SurfaceScrollEdges`) were two differently-shaped records of the same fact.
 *
 * @param horizontal what owns left/right — a pager, usually.
 * @param vertical what owns up/down — a list or a scrolling grid.
 */
data class ScrollAxes(
    val horizontal: AxisScroll = AxisScroll.NONE,
    val vertical: AxisScroll = AxisScroll.NONE,
) {
    /**
     * The one-finger policy this content hands the swipe that crosses [edge].
     *
     * A crossing travels along the edge's own axis, so the answer is simply what owns that axis. Used twice per
     * edge and from opposite ends: HOME's axes give the **open** policy, the side surface's give the **close** one.
     */
    fun oneFingerSwipe(edge: HomeEdge): OneFingerSwipe =
        when (if (edge.isHorizontal) horizontal else vertical) {
            AxisScroll.NONE -> OneFingerSwipe.ALWAYS
            AxisScroll.BOUNDED -> OneFingerSwipe.AT_EDGE
            AxisScroll.INFINITE -> OneFingerSwipe.NEVER
        }
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
