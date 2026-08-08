package inkspire.morphic.core.designsystem.pager

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * State for [LauncherPager]: a single continuous [pagePosition] (in page units) that every page's offset is
 * derived from, plus the paging operations (drag / fling / snap / settle).
 *
 * The pager can be **infinite** (wrap-around) — but never via the Int.MAX-pages trick, which lags and, worse,
 * makes page indices shift under an active drag. Instead only the real [pageCount] pages ever exist; wrapping
 * is pure modular arithmetic on each page's *visual* offset (in [LauncherPager]), and [normalizeWrapPosition]
 * snaps [pagePosition] back into one lap after settling so the float never grows without bound.
 *
 * Crucially, **[isBounded] is forced true while [dragMode] is on**: during an item drag the pager stops
 * wrapping, so page targeting stays stable for the drag-and-drop layer.
 *
 * @param pageCountProvider current number of pages (coerced to >= 1).
 * @param dragModeProvider true while an item drag is in progress — forces bounded paging.
 * @param infiniteScrollProvider the user's toggle: may the pager wrap when *not* dragging.
 */
@Stable
class LauncherPagerState(
    private val pageCountProvider: () -> Int,
    private val dragModeProvider: () -> Boolean = { false },
    private val infiniteScrollProvider: () -> Boolean = { true },
) {
    val pageCount: Int get() = pageCountProvider().coerceAtLeast(1)
    val dragMode: Boolean get() = dragModeProvider()
    val infiniteScroll: Boolean get() = infiniteScrollProvider()

    /** Bounded (no wrap) whenever an item drag is active or the user disabled infinite scroll. */
    val isBounded: Boolean get() = dragMode || !infiniteScroll

    /** Measured page width in px; set by [LauncherPager] on measure. */
    var pageSize: Int by mutableIntStateOf(0)
        internal set

    /** Measured viewport height in px; set by [LauncherPager] on measure. */
    var containerHeight: Int by mutableIntStateOf(0)
        internal set

    private val positionAnimatable = Animatable(0f)

    /**
     * The scroll position (in page units) the layout places from. Within range it is the raw position; **past a
     * bound it is rubber-banded** — the content follows a little, with increasing resistance, then springs back
     * on release. A translation bounce, deliberately not the M3 stretch/glow.
     */
    val pagePosition: Float
        get() {
            val raw = positionAnimatable.value
            if (!isBounded) return raw
            val max = (pageCount - 1).toFloat()
            return when {
                raw < 0f -> -rubberBand(-raw)
                raw > max -> max + rubberBand(raw - max)
                else -> raw
            }
        }

    val isPagerAnimating: Boolean get() = positionAnimatable.isRunning

    /** The settled page under the current position; wraps into `[0, pageCount)` when unbounded. */
    val currentPage: Int
        get() = if (isBounded) {
            positionAnimatable.value.roundToInt().coerceIn(0, pageCount - 1)
        } else {
            positionAnimatable.value.roundToInt().mod(pageCount)
        }

    /**
     * True when the pager has nothing further to the **left** — it is resting on its first page and bounded.
     *
     * Read by the surface-swipe hand-off (`ScrollEdges`), which needs to know whether a one-finger swipe crossing
     * this pager has anywhere left to go. **Never true while wrapping**, which is the whole reason it is phrased
     * against [isBounded] rather than against the position alone: an infinite pager has no first page to rest on, and
     * saying otherwise would let a swipe leave the surface from the middle of a lap.
     *
     * The epsilon is L1's own — the position is a float mid-settle, so an exact comparison would answer "no" for the
     * last few frames of a spring that has visibly arrived. L1 restated this expression in three layout files; here
     * it lives on the state, which is also the only thing that knows whether it is bounded.
     */
    val atFirstPage: Boolean get() = isBounded && pagePosition <= PAGE_EDGE_EPSILON

    /** The trailing counterpart of [atFirstPage]: bounded, and resting on the last page. */
    val atLastPage: Boolean get() = isBounded && pagePosition >= (pageCount - 1) - PAGE_EDGE_EPSILON

    /** Signed offset from the nearest page in `[-0.5, 0.5)` — for page indicators / transforms. */
    val currentPageOffsetFraction: Float
        get() = positionAnimatable.value.let { it - it.roundToInt() }

    /**
     * Scrolls by a raw pixel delta (a finger drag). When bounded, the raw position may go a little past the
     * ends — capped by [RAW_OVERSCROLL_LIMIT] so the snap-back stays short — and [pagePosition] rubber-bands
     * the *visual*. Unbounded (infinite) scroll is free; wrapping handles the ends.
     */
    suspend fun dragHorizontalBy(deltaPx: Float) {
        if (pageSize <= 0 || pageCount <= 1) return
        val next = positionAnimatable.value - deltaPx / pageSize
        val target = if (isBounded) {
            next.coerceIn(-RAW_OVERSCROLL_LIMIT, (pageCount - 1) + RAW_OVERSCROLL_LIMIT)
        } else {
            next
        }
        positionAnimatable.snapTo(target)
    }

    suspend fun snapToPage(page: Int) = positionAnimatable.snapTo(clampIfBounded(page.toFloat()))

    suspend fun animateToPage(page: Int) =
        positionAnimatable.animateTo(clampIfBounded(page.toFloat()), pagerSpring)

    /** Animate to the nearest page (used when releasing without a fling), then re-normalise the wrap. */
    suspend fun settleToNearestPage() {
        val target = clampIfBounded(positionAnimatable.value.roundToInt().toFloat())
        if (positionAnimatable.value != target) positionAnimatable.animateTo(target, pagerSpring)
        normalizeWrapPosition()
    }

    /** Release with velocity: advance one page past the fling threshold, else settle to the nearest. */
    suspend fun flingHorizontal(velocityPx: Float) {
        if (pageSize <= 0 || pageCount <= 1) return
        val current = positionAnimatable.value
        val target = when {
            velocityPx < -PAGE_FLING_THRESHOLD -> floor(current).toInt() + 1
            velocityPx > PAGE_FLING_THRESHOLD -> ceil(current).toInt() - 1
            else -> current.roundToInt()
        }
        val capped = velocityPx.coerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY)
        positionAnimatable.animateTo(
            targetValue = clampIfBounded(target.toFloat()),
            animationSpec = pagerSpring,
            initialVelocity = -capped / pageSize,
        )
        normalizeWrapPosition()
    }

    suspend fun stopAllAnimations() = positionAnimatable.stop()

    /**
     * Brings the position back inside the page range — **what a pager owes itself when its page count shrinks**.
     *
     * Every other way of leaving the range is transient: a drag past the end is rubber-banded and springs back on
     * release, and a fling is clamped by [clampIfBounded]. Losing a page is different, because nothing about the
     * *gesture* has changed — the position was legitimately settled on page 1 and page 1 stopped existing.
     * [pagePosition] then rubber-bands it forever, which is a pager frozen part-way between two pages with no
     * gesture in flight to release it. Removing the last item from the last page is exactly that, and it took no
     * touch at all to reach.
     *
     * Animated rather than snapped, because the page that is left has to be seen arriving: the user removed
     * something and the launcher slides back to what remains.
     *
     * A no-op when the count grows, which is the common case — the trailing empty page that appears mid-drag.
     */
    suspend fun settleWithinPageCount() {
        if (!isBounded) return
        val max = (pageCount - 1).toFloat()
        if (positionAnimatable.value <= max) return
        positionAnimatable.animateTo(max, pagerSpring)
    }

    private fun clampIfBounded(value: Float): Float =
        if (isBounded) value.coerceIn(0f, (pageCount - 1).toFloat()) else value

    /**
     * The rubber-band curve: maps a raw overshoot (pages past a bound) to a damped one that asymptotes to
     * [MAX_OVERSCROLL_PAGES] — responsive at first, then increasingly resistant.
     */
    private fun rubberBand(overshoot: Float): Float {
        val x = overshoot.coerceAtLeast(0f)
        return MAX_OVERSCROLL_PAGES * (1f - 1f / (x / MAX_OVERSCROLL_PAGES * RUBBER_BAND_STIFFNESS + 1f))
    }

    /** Snap the settled position back into one lap `[0, pageCount)` so the float can't grow unbounded. */
    private suspend fun normalizeWrapPosition() {
        if (isBounded) return
        val span = pageCount.toFloat()
        if (span <= 0f) return
        val normalized = positionAnimatable.value.mod(span)
        if (positionAnimatable.value != normalized) positionAnimatable.snapTo(normalized)
    }

    private companion object {
        const val PAGE_FLING_THRESHOLD = 400f
        const val MAX_FLING_VELOCITY = 4000f

        /** Visual overscroll ceiling, in page widths (the rubber-band asymptote). */
        const val MAX_OVERSCROLL_PAGES = 0.16f

        /** How far the *raw* position may go past a bound, so the snap-back animation stays short. */
        const val RAW_OVERSCROLL_LIMIT = 0.6f

        /** Higher = the rubber-band tightens up sooner. */
        const val RUBBER_BAND_STIFFNESS = 3f

        /** How close to a page bound still counts as resting on it, in page units — see [atFirstPage]. */
        const val PAGE_EDGE_EPSILON = 0.001f

        val pagerSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f)
    }
}

/** Remembers a [LauncherPagerState]. The providers are read live, so pass lambdas over your state. */
@Composable
fun rememberLauncherPagerState(
    pageCount: () -> Int,
    dragMode: () -> Boolean = { false },
    infiniteScroll: () -> Boolean = { true },
): LauncherPagerState = remember { LauncherPagerState(pageCount, dragMode, infiniteScroll) }
