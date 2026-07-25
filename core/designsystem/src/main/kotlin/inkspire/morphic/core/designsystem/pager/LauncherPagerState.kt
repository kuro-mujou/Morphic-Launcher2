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

    /** Continuous scroll position in page units (e.g. 1.5 = halfway between pages 1 and 2). */
    val pagePosition: Float get() = positionAnimatable.value

    val isPagerAnimating: Boolean get() = positionAnimatable.isRunning

    /** The settled page under the current position; wraps into `[0, pageCount)` when unbounded. */
    val currentPage: Int
        get() = if (isBounded) {
            positionAnimatable.value.roundToInt().coerceIn(0, pageCount - 1)
        } else {
            positionAnimatable.value.roundToInt().mod(pageCount)
        }

    /** Signed offset from the nearest page in `[-0.5, 0.5)` — for page indicators / transforms. */
    val currentPageOffsetFraction: Float
        get() = positionAnimatable.value.let { it - it.roundToInt() }

    /** Scrolls by a raw pixel delta (a finger drag); bounded positions clamp to the page range. */
    suspend fun dragHorizontalBy(deltaPx: Float) {
        if (pageSize <= 0 || pageCount <= 1) return
        positionAnimatable.snapTo(clampIfBounded(positionAnimatable.value - deltaPx / pageSize))
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

    private fun clampIfBounded(value: Float): Float =
        if (isBounded) value.coerceIn(0f, (pageCount - 1).toFloat()) else value

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
