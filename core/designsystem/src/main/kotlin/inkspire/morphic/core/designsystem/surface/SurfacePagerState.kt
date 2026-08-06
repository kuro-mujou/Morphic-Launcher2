package inkspire.morphic.core.designsystem.surface

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import inkspire.morphic.core.model.HomeEdge
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * State for [SurfacePager]: the HOME surface's pan position toward its side surfaces, plus the pan operations
 * (drag / settle / open / close).
 *
 * Position is two independent axes, each in **normalized page units** where `0` is HOME and `±1` is a fully
 * open side surface:
 * - [panX]: `-1` = [HomeEdge.LEFT] open, `+1` = [HomeEdge.RIGHT] open.
 * - [panY]: `-1` = [HomeEdge.TOP] open, `+1` = [HomeEdge.BOTTOM] open.
 *
 * Only one axis is ever off-zero at a time in practice — you open one surface, then return to HOME before
 * opening another — but keeping the axes separate (two [Animatable]s, not one 2D vector) lets each settle on
 * its own spring, which is exactly how the gesture drives them: it locks to one axis per drag.
 *
 * **Why hoisted state at all** (L1 kept the pan as loose `Animatable`s inside the composable): the same reason
 * the grid pager got [inkspire.morphic.core.designsystem.pager.LauncherPagerState] — the gesture layer, the
 * layout, and the caller (a back handler, a demo readout) all need to read and drive one position. A `@Stable`
 * holder gives them one owner instead of prop-drilling four animatables.
 *
 * The reachable bounds come from [edgeSwipes], set by [SurfacePager] from the side content it was given — so a
 * drag can never open an edge that has no surface behind it. The state derives its per-axis min/max from those
 * keys rather than the caller passing bounds into every operation.
 */
@Stable
class SurfacePagerState {

    /**
     * The one-finger swipe policy for each bound edge — which directions HOME may pan toward, and when one
     * finger alone may open and close each. Set by [SurfacePager] from its `sideContent`; its keys drive the
     * clamp bounds below, and the gesture reads each [EdgeSwipe] to decide whether a one-finger swipe counts.
     */
    var edgeSwipes: Map<HomeEdge, EdgeSwipe> by mutableStateOf(emptyMap())
        internal set

    /** Measured viewport width in px; set by [SurfacePager] on size change. Converts a pixel drag to page units. */
    var viewportWidth: Int by mutableIntStateOf(0)
        internal set

    /** Measured viewport height in px; set by [SurfacePager] on size change. */
    var viewportHeight: Int by mutableIntStateOf(0)
        internal set

    private val animX = Animatable(0f)
    private val animY = Animatable(0f)

    /** Horizontal pan in page units: `-1` LEFT open, `0` HOME, `+1` RIGHT open. */
    val panX: Float get() = animX.value

    /** Vertical pan in page units: `-1` TOP open, `0` HOME, `+1` BOTTOM open. */
    val panY: Float get() = animY.value

    private val minX: Float get() = if (HomeEdge.LEFT in edgeSwipes) -1f else 0f
    private val maxX: Float get() = if (HomeEdge.RIGHT in edgeSwipes) 1f else 0f
    private val minY: Float get() = if (HomeEdge.TOP in edgeSwipes) -1f else 0f
    private val maxY: Float get() = if (HomeEdge.BOTTOM in edgeSwipes) 1f else 0f

    /**
     * The side surface currently open (past the half-way point), or `null` while resting on HOME. Used by the
     * caller for a back handler and by the demo for a readout; the `0.5` cut just names whichever surface a
     * partially-dragged pan is closer to.
     */
    val openEdge: HomeEdge?
        get() = when {
            animX.value <= -0.5f -> HomeEdge.LEFT
            animX.value >= 0.5f -> HomeEdge.RIGHT
            animY.value <= -0.5f -> HomeEdge.TOP
            animY.value >= 0.5f -> HomeEdge.BOTTOM
            else -> null
        }

    /**
     * **How far from HOME the pan has travelled, `0f..1f`** — 0 resting on HOME, 1 with a side surface fully open.
     *
     * The scalar the two axes collapse to, because only one of them is ever non-zero: a pan is toward one edge, and
     * `max` picks whichever without the caller having to know which. What reads it is anything that should *appear*
     * as a surface arrives rather than travel with it — the full-screen frost above all (`SurfaceBackdropLayer`),
     * whose whole point is that its motion is not the pane's.
     *
     * Deliberately unsigned and edge-agnostic: "how far in is the other surface" is the same question from every
     * edge, and a consumer that needed to know *which* edge already has [openEdge].
     */
    val progress: Float
        get() = maxOf(kotlin.math.abs(animX.value), kotlin.math.abs(animY.value)).coerceIn(0f, 1f)

    /**
     * Drags the horizontal pan by a raw pixel delta (a finger move). A positive [px] (finger moving right)
     * pulls the LEFT surface in, so the pan *decreases* — matching a page that follows the finger. Clamped to
     * the reachable bounds, so it can't drag toward an edge with no surface.
     */
    suspend fun dragXBy(px: Float) {
        if (viewportWidth <= 0) return
        animX.snapTo((animX.value - px / viewportWidth).coerceIn(minX, maxX))
    }

    /** Vertical counterpart of [dragXBy]. */
    suspend fun dragYBy(px: Float) {
        if (viewportHeight <= 0) return
        animY.snapTo((animY.value - px / viewportHeight).coerceIn(minY, maxY))
    }

    /**
     * Releases a horizontal drag: springs to whichever page the [settleTarget] rule picks (fling past
     * threshold, or dragged past [SETTLE_FRACTION]), from where the drag started.
     *
     * @param start the integer page the drag began on (`0`, `-1`, or `+1`).
     * @param velocityPx release velocity in px/s, its sign flipped by the caller so it matches the pan
     *   direction (finger flinging right → pan decreasing).
     */
    suspend fun settleX(start: Float, velocityPx: Float) {
        animX.animateTo(settleTarget(start, animX.value, velocityPx, minX, maxX), settleSpring)
    }

    /** Vertical counterpart of [settleX]. */
    suspend fun settleY(start: Float, velocityPx: Float) {
        animY.animateTo(settleTarget(start, animY.value, velocityPx, minY, maxY), settleSpring)
    }

    /**
     * Settles both axes to their nearest page. Used on a release that never claimed the gesture (a tap, or a
     * gesture a child took) so the pan never rests mid-transition — mirrors the grid pager's re-settle after
     * it stops any in-flight animation on touch-down.
     */
    suspend fun settleToNearest() {
        val tx = animX.value.roundToInt().toFloat().coerceIn(minX, maxX)
        val ty = animY.value.roundToInt().toFloat().coerceIn(minY, maxY)
        if (animX.value != tx) animX.animateTo(tx, settleSpring)
        if (animY.value != ty) animY.animateTo(ty, settleSpring)
    }

    /** Opens a side surface programmatically (e.g. a button), springing to that edge. */
    suspend fun open(edge: HomeEdge) = when (edge) {
        HomeEdge.LEFT -> animX.animateTo(minX, settleSpring)
        HomeEdge.RIGHT -> animX.animateTo(maxX, settleSpring)
        HomeEdge.TOP -> animY.animateTo(minY, settleSpring)
        HomeEdge.BOTTOM -> animY.animateTo(maxY, settleSpring)
    }

    /** Returns to HOME, springing both axes back to `0`. */
    suspend fun close() {
        if (animX.value != 0f) animX.animateTo(0f, settleSpring)
        if (animY.value != 0f) animY.animateTo(0f, settleSpring)
    }

    /** Freezes any in-flight settle, so a fresh touch takes over the pan instead of fighting the spring. */
    suspend fun stop() {
        animX.stop()
        animY.stop()
    }

    private companion object {
        val settleSpring =
            spring<Float>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f)
    }
}

/**
 * Picks the page a released drag lands on, one page at most from [start]:
 * - a fling past [FLING_THRESHOLD_PX] in the drag's direction advances one page;
 * - otherwise a drag past [SETTLE_FRACTION] of a page commits, and a smaller drag snaps back.
 *
 * The result is clamped to `[start-1, start+1]` (a single-page move) and then to the axis bounds.
 */
private fun settleTarget(
    start: Float,
    current: Float,
    velocityPx: Float,
    min: Float,
    max: Float,
): Float {
    val dragged = current - start
    val flingDir = if (velocityPx > 0f) 1f else -1f
    val flungPastThreshold = abs(velocityPx) > FLING_THRESHOLD_PX && dragged * flingDir >= 0f
    val target = when {
        flungPastThreshold -> start + flingDir
        dragged > SETTLE_FRACTION -> start + 1f
        dragged < -SETTLE_FRACTION -> start - 1f
        else -> start
    }
    return target.coerceIn(start - 1f, start + 1f).coerceIn(min, max)
}

private const val SETTLE_FRACTION = 0.25f

/** Fling speed (px/s) above which a release advances a page regardless of how far it was dragged. */
private const val FLING_THRESHOLD_PX = 1000f

/** Remembers a [SurfacePagerState] for the lifetime of the composition. */
@Composable
fun rememberSurfacePagerState(): SurfacePagerState = remember { SurfacePagerState() }
