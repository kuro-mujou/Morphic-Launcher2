package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.data.settings.IconStudioWorkspace
import kotlin.math.min

/**
 * Where the icon's square bound sits on the canvas, in **pixels of the canvas's own coordinate space**.
 *
 * One value rather than three, because two very different things read it and they must not each derive their own:
 * the layout places a `Box` at exactly this rectangle, and `drawBackdrop` fills the same rectangle with the
 * checkerboard. Those used to be two independent derivations from the same constants — which agreed because neither
 * had any input but the canvas's size. The moment the user could pan and zoom, "derive it twice and hope" stopped
 * being safe, so it became "derive it once and hand it to both".
 */
internal data class StudioIconBound(val left: Float, val top: Float, val side: Float)

/**
 * How much of the canvas's shorter side the icon's bound takes at rest. Large enough to work in, short of edge to edge.
 */
private const val IconBoundFraction = 0.62f

/**
 * How far toward the start the resting bound sits, as a fraction of the canvas's width.
 *
 * **The layer rail rests down the end edge**, so the canvas is not symmetrical left to right. Centered, a phone's bound
 * reaches within a few dp of the rail and a narrower one goes under it — the icon obscured by the very control used to
 * pick which layer is being edited.
 *
 * A fraction rather than the rail's width in dp, because a dp shift moves the bound further on a phone than on a
 * tablet relative to what is around it.
 *
 * **It is a *resting* place now, not a fixed one**, which is the change the viewport made to it: the user can pan the
 * icon anywhere, and can drag the rail off the edge this was avoiding. So the shift is the arrangement the studio
 * opens in rather than a promise it keeps.
 */
private const val IconBoundShift = 0.08f

/**
 * How far the bound's center may be pushed, as a fraction of the canvas past its edges.
 *
 * Zero, which is to say the **center stays on the canvas** — so at the very worst a quarter of the icon is visible in
 * a corner and there is always something to drag back. That is the whole of the clamp's job: a viewport a user can
 * lose the icon out of is one they have to be given a reset button for, and no reset button is a better answer than a
 * good one.
 */
private const val CenterKeep = 0f

/**
 * How far the preview may be zoomed.
 *
 * The floor is short of the resting size rather than tiny — below about a half the icon is smaller than the layer
 * tiles that show the same thing, so there is nothing down there to see. The ceiling is where a phone's bound fills
 * the screen several times over, which is what inspecting one layer's edge actually wants.
 */
internal val StudioZoomRange = 0.5f..4f

/**
 * The icon's bound, resolved from the canvas's size, the chrome above it and the user's [workspace].
 *
 * **The resting bound is top-aligned under the chrome**, not centered — which is the arrangement change this function
 * carries. It used to be centered and then lifted by a constant fraction to keep clear of the tool panel, and that
 * constant was a compromise the KDoc admitted to: too small and an open panel covered the icon, too large and the icon
 * sat oddly high with nothing above it. Anchoring it to the top instead removes the compromise rather than tuning it —
 * the icon is as far from the panel as the screen allows, and the space it used to leave empty above is space it now
 * occupies.
 *
 * **Zoom scales the bound about its own center**, so [IconStudioWorkspace.panX] and [panY] keep meaning "how far the
 * icon has been dragged" at any zoom. The centroid-anchored half of a pinch is [pinched]'s job, which is what keeps
 * this one a plain resolution with nothing remembered in it.
 *
 * @param topInset how much of the canvas's top edge the chrome occupies — the system inset plus the row of pill
 *   buttons. The resting bound starts immediately below it, which is what "all the way to the top" means here.
 */
internal fun studioIconBound(
    canvasWidth: Float,
    canvasHeight: Float,
    topInset: Float,
    workspace: IconStudioWorkspace,
): StudioIconBound {
    val side = restingSide(canvasWidth, canvasHeight) * workspace.zoom
    val center = restingCenter(canvasWidth, canvasHeight, topInset)
    val centerX = (center.first + workspace.panX * canvasWidth).coerceIn(
        -CenterKeep * canvasWidth,
        canvasWidth + CenterKeep * canvasWidth,
    )
    val centerY = (center.second + workspace.panY * canvasHeight).coerceIn(
        -CenterKeep * canvasHeight,
        canvasHeight + CenterKeep * canvasHeight,
    )
    return StudioIconBound(left = centerX - side / 2f, top = centerY - side / 2f, side = side)
}

/**
 * This workspace after one frame of a pinch-and-drag on the canvas.
 *
 * **The centroid stays under the fingers, which is the whole of why this is a function and not two `+=`s at the call
 * site.** Zooming about the bound's center instead is the version everybody writes first, and it is subtly wrong in a
 * way that is hard to name while using it: the thing you pinched slides away from your fingers as it grows, so
 * enlarging a corner of the icon means pinching and then chasing it with a drag. Keeping the centroid fixed is one
 * line of algebra — a point at distance `d` from the center must stay at distance `d`, so the center moves to
 * `c + (center - c) × ratio` — and it is the difference between a viewport that feels direct and one that feels
 * indirect.
 *
 * **The clamp is applied to the resulting pan rather than to the gesture**, so a drag that would push the icon off the
 * canvas simply stops rather than accumulating an offset that has to be dragged back through. Without that, holding a
 * drag against the edge for a second banks a second's worth of travel and the icon does not move on the way back until
 * that debt is paid.
 *
 * **Rotation is not a parameter**, which is deliberate rather than an omission: `detectTransformGestures` reports one
 * and the studio ignores it. A layer's rotation is a *property of the recipe* with its own slider and its own undo
 * entry; turning the viewport would mean the preview no longer showed the icon as the launcher draws it, which is the
 * one thing this canvas must always do.
 *
 * @param centroidX the point between the fingers, in canvas pixels. For a one-finger drag this is the finger, and the
 *   zoom ratio is 1, so it has no effect.
 * @param zoomBy the ratio this frame multiplies the zoom by — `1f` for a pure drag.
 */
internal fun IconStudioWorkspace.pinched(
    canvasWidth: Float,
    canvasHeight: Float,
    topInset: Float,
    centroidX: Float,
    centroidY: Float,
    dragX: Float,
    dragY: Float,
    zoomBy: Float,
): IconStudioWorkspace {
    if (canvasWidth <= 0f || canvasHeight <= 0f) return this

    val newZoom = (zoom * zoomBy).coerceIn(StudioZoomRange)
    // The *effective* ratio, which is what the centroid maths must use: at the ends of the range the gesture keeps
    // reporting a ratio the zoom no longer takes, and applying that one would go on sliding the icon while the size
    // held still — a pinch that pans, which reads as the canvas slipping.
    val ratio = if (zoom == 0f) 1f else newZoom / zoom

    val resting = restingCenter(canvasWidth, canvasHeight, topInset)
    val centerX = resting.first + panX * canvasWidth
    val centerY = resting.second + panY * canvasHeight

    val zoomedX = centroidX + (centerX - centroidX) * ratio + dragX
    val zoomedY = centroidY + (centerY - centroidY) * ratio + dragY

    return copy(
        panX = ((zoomedX - resting.first) / canvasWidth).coerceIn(panBound(resting.first, canvasWidth)),
        panY = ((zoomedY - resting.second) / canvasHeight).coerceIn(panBound(resting.second, canvasHeight)),
        zoom = newZoom,
    )
}

/**
 * This workspace after one frame of dragging the layer rail by its handle.
 *
 * **Clamped so the rail stays wholly on the canvas**, which is a stricter bound than the icon's — and the difference
 * is what each thing *is*. The icon is the work: half of it off the edge is a view of it, and the clamp only has to
 * guarantee there is something left to drag back. The rail is a *control*, and a control half off the screen is one
 * whose buttons cannot all be pressed. So this keeps the whole of it in, and the pan keeps only the center.
 *
 * **Both the resting place and the size are measured rather than declared**, which is what lets the caller change the
 * rail's padding, its cap, or the side it rests on without this function learning about any of it: the resting
 * top-left is the placed position less the offset currently applied, which is exact and not circular. See the call
 * site in `StudioLayerRail`.
 *
 * A degenerate measurement — a frame before the rail has been laid out, or a canvas of nothing — returns the workspace
 * untouched rather than clamping against zero, which would slam the rail into the corner on the first frame of a drag.
 */
internal fun IconStudioWorkspace.railDragged(
    canvasWidth: Float,
    canvasHeight: Float,
    railWidth: Float,
    railHeight: Float,
    restingLeft: Float,
    restingTop: Float,
    dragX: Float,
    dragY: Float,
): IconStudioWorkspace {
    if (canvasWidth <= 0f || canvasHeight <= 0f || railWidth <= 0f || railHeight <= 0f) return this

    val left = (restingLeft + railX * canvasWidth + dragX)
        .coerceIn(0f, (canvasWidth - railWidth).coerceAtLeast(0f))
    val top = (restingTop + railY * canvasHeight + dragY)
        .coerceIn(0f, (canvasHeight - railHeight).coerceAtLeast(0f))

    return copy(
        railX = (left - restingLeft) / canvasWidth,
        railY = (top - restingTop) / canvasHeight,
    )
}

/**
 * The range a pan fraction may take on one axis, so the bound's center lands within the canvas.
 *
 * Expressed as bounds on the *pan* rather than on the center because the pan is what is stored, and clamping the
 * stored value is what stops a drag banking travel it will not spend. [CenterKeep] widens both ends by the same amount
 * if the center is ever allowed off the canvas.
 */
private fun panBound(resting: Float, extent: Float): ClosedFloatingPointRange<Float> {
    val low = (-CenterKeep * extent - resting) / extent
    val high = (extent + CenterKeep * extent - resting) / extent
    return low..high
}

/** The bound's side at zoom 1 — a square on the canvas's shorter dimension, so it fits either way up. */
private fun restingSide(canvasWidth: Float, canvasHeight: Float): Float =
    min(canvasWidth, canvasHeight) * IconBoundFraction

/**
 * Where the bound's center sits with nothing panned: hard against the chrome at the top, and shifted off the rail's
 * edge horizontally.
 *
 * Derived from the **resting** side rather than the zoomed one, which is what keeps the anchor still as the zoom
 * changes — a center that moved with the size would make [pinched]'s algebra chase itself.
 */
private fun restingCenter(canvasWidth: Float, canvasHeight: Float, topInset: Float): Pair<Float, Float> {
    val side = restingSide(canvasWidth, canvasHeight)
    return (canvasWidth / 2f - canvasWidth * IconBoundShift) to (topInset + side / 2f)
}
