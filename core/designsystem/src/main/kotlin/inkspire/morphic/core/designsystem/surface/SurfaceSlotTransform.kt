package inkspire.morphic.core.designsystem.surface

import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.SurfaceTransition
import kotlin.math.abs

/**
 * Where one surface slot sits, and how it is drawn, for a given pan — **the whole difference between the six
 * transitions**. Every value is a slot's placement resolved from the pan; [SurfacePager] applies the offset in its
 * layout phase and the rest through a `graphicsLayer`, so a pan re-draws without recomposing.
 *
 * The two offsets are **fractions of the viewport** (`1f` == one full viewport), matching the pan's own page units;
 * the pager scales them by the measured size. The rest are `graphicsLayer` inputs in their own units — [scale] a
 * multiplier, [rotationX]/[rotationY] degrees, [cameraDistance] the layer's perspective
 * depth, and [pivotX]/[pivotY] the transform origin as a `0..1` fraction of the slot.
 *
 * Ported from L1's `CrossPager.SlotTransform`; the defaults are the identity so that [SurfaceTransition.SLIDE], which
 * moves a slot with offset alone, names none of the graphics fields.
 */
internal data class SlotTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val cameraDistance: Float = 8f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
)

/**
 * The placement for one slot at the current pan: [edge] is the side surface it belongs to, or `null` for HOME (the
 * center). [panX]/[panY] are [SurfacePagerState]'s two axes.
 *
 * A single dispatch over [SurfaceTransition], as L1's `surfaceSlotTransform` was — so the pager stays ignorant of
 * which transition it is drawing and there is exactly one place a new one is added.
 */
internal fun surfaceSlotTransform(
    transition: SurfaceTransition,
    edge: HomeEdge?,
    panX: Float,
    panY: Float,
): SlotTransform = when (transition) {
    SurfaceTransition.SLIDE -> slideTransform(edge, panX, panY)
    SurfaceTransition.PARALLAX -> parallaxTransform(edge, panX, panY)
    SurfaceTransition.ZOOM -> zoomTransform(edge, panX, panY)
    SurfaceTransition.DEPTH -> depthTransform(edge, panX, panY)
    SurfaceTransition.FADE -> fadeTransform(edge, panX, panY)
    SurfaceTransition.RISE -> riseTransform(edge, panX, panY)
}

/**
 * Whether a transition needs a `graphicsLayer` at all, or moves with a plain offset. [SurfaceTransition.SLIDE] is the
 * one that does not — so the pager skips the layer for it and pays nothing for the five it is not showing.
 */
internal fun SurfaceTransition.usesGraphicsLayer(): Boolean = this != SurfaceTransition.SLIDE

// Tuning constants, ported verbatim from L1's CrossPager. Not dp — unitless factors, degrees and an elevation the
// graphicsLayer reads directly — so they keep their names rather than moving to the call site (the dp rule does not
// reach them, and each is used in exactly one transform below).
private const val PARALLAX_FACTOR = 0.5f
private const val ZOOM_MIN_SCALE = 0.85f
private const val DEPTH_MIN_SCALE = 0.78f
private const val DEPTH_TILT_DEG = 26f
private const val DEPTH_CAMERA = 10f
private const val DEPTH_DRIFT = 0.22f
private const val FADE_SIDE_GROW = 0.04f
private const val RISE_HOME_DRIFT = 0.3f
private const val RISE_HOME_SHRINK = 0.10f
private const val RISE_SIDE_START_OFFSET = 0.25f

/**
 * A side surface's resting offset: parked one full viewport off its [edge], then slid by the pan so it tracks in as
 * HOME slides out. The offset half of [SurfaceTransition.SLIDE], and the part every other transition keeps for its
 * side slots — only the center and the graphics fields change between transitions.
 */
private fun sideSlideOffset(edge: HomeEdge, panX: Float, panY: Float): SlotTransform = when (edge) {
    HomeEdge.LEFT -> SlotTransform(-(1f + panX), -panY)
    HomeEdge.RIGHT -> SlotTransform(1f - panX, -panY)
    HomeEdge.TOP -> SlotTransform(-panX, -(1f + panY))
    HomeEdge.BOTTOM -> SlotTransform(-panX, 1f - panY)
}

/** How far in a specific side surface is (`0f..1f`), used where a slot animates on its own arrival rather than the pan. */
private fun sideOpenAmount(edge: HomeEdge, panX: Float, panY: Float): Float = when (edge) {
    HomeEdge.LEFT -> maxOf(0f, -panX)
    HomeEdge.RIGHT -> maxOf(0f, panX)
    HomeEdge.TOP -> maxOf(0f, -panY)
    HomeEdge.BOTTOM -> maxOf(0f, panY)
}

/** How far from HOME the pan has traveled, `0f..1f` — the two axes collapsed, since only one is ever off zero. */
private fun panProgress(panX: Float, panY: Float): Float = maxOf(abs(panX), abs(panY))

private fun slideTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform =
    if (edge == null) SlotTransform(-panX, -panY) else sideSlideOffset(edge, panX, panY)

private fun parallaxTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform =
    if (edge == null) {
        SlotTransform(-panX * PARALLAX_FACTOR, -panY * PARALLAX_FACTOR, alpha = 1f - panProgress(panX, panY))
    } else {
        sideSlideOffset(edge, panX, panY)
    }

private fun zoomTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform {
    if (edge != null) return sideSlideOffset(edge, panX, panY)
    val p = panProgress(panX, panY)
    return SlotTransform(0f, 0f, scale = 1f - (1f - ZOOM_MIN_SCALE) * p, alpha = 1f - p)
}

private fun depthTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform {
    if (edge != null) return sideSlideOffset(edge, panX, panY)
    val p = panProgress(panX, panY)
    return SlotTransform(
        offsetX = -panX * DEPTH_DRIFT,
        offsetY = -panY * DEPTH_DRIFT,
        scale = 1f - (1f - DEPTH_MIN_SCALE) * p,
        alpha = 1f - p * p,
        rotationY = DEPTH_TILT_DEG * panX,
        rotationX = -DEPTH_TILT_DEG * panY,
        cameraDistance = DEPTH_CAMERA,
    )
}

private fun fadeTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform {
    val p = panProgress(panX, panY)
    if (edge == null) return SlotTransform(0f, 0f, alpha = 1f - p)
    val sideScale = 1f - FADE_SIDE_GROW * (1f - p)
    return sideSlideOffset(edge, panX, panY).copy(scale = sideScale, alpha = p * p)
}

private fun riseTransform(edge: HomeEdge?, panX: Float, panY: Float): SlotTransform {
    if (edge == null) {
        val p = panProgress(panX, panY)
        return SlotTransform(
            offsetX = -panX * RISE_HOME_DRIFT,
            offsetY = -panY * RISE_HOME_DRIFT,
            scale = 1f - RISE_HOME_SHRINK * p,
            alpha = 1f - p,
        )
    }
    // Before this surface has opened at all (another edge is the one moving) it just parks off its own edge, so a
    // second bound surface does not drift while a different one rises.
    val q = sideOpenAmount(edge, panX, panY)
    if (q == 0f) return sideSlideOffset(edge, panX, panY)

    val offset = (1f - q) * RISE_SIDE_START_OFFSET
    return SlotTransform(
        offsetX = when (edge) {
            HomeEdge.LEFT -> -offset
            HomeEdge.RIGHT -> offset
            else -> 0f
        },
        offsetY = when (edge) {
            HomeEdge.TOP -> -offset
            HomeEdge.BOTTOM -> offset
            else -> 0f
        },
        alpha = q,
    )
}
