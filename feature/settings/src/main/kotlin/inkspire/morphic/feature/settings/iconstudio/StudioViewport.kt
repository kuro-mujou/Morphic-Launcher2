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
 * tiles that show the same thing, so there is nothing down there to see.
 *
 * **The ceiling is where the icon is about twice the canvas's shorter side**, which is a real inspection zoom — a
 * layer's edge fills the screen — and is as far as it is worth going. It was **4**, where the bound is two and a
 * half canvases across, and two things went wrong there at once. The lesser: crossing a picture that wide is a lot
 * of panning for a 48dp icon. The greater: an icon on the baked path is rendered as a whole square and never as
 * the visible crop, so past a point the preview is a small bitmap stretched a long way — at 4 it was a fivefold
 * upscale, and what a user sees at maximum zoom is *less* detail than at half of it, which is the opposite of what
 * a zoom is for.
 *
 * So the two ends were moved toward each other rather than either being pushed: this, and `MaxPreviewPx` one module
 * over. Both are one-line changes and neither is a threshold — if the ceiling should be higher, the cap is what has
 * to pay for it.
 */
internal val StudioZoomRange = 0.5f..3f

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
    // **Clamped through the same [panBound] the gesture writes through**, rather than through a second expression
    // saying the same thing. They were two, agreeing because both said "keep the centre on the canvas"; the moment
    // that rule gained a second regime, two copies of it would have been two chances to disagree about where a
    // zoomed icon may sit — and the disagreement would show as an icon that snaps somewhere the drag cannot reach.
    val panX = workspace.panX.coerceIn(panBound(center.first, canvasWidth, side))
    val panY = workspace.panY.coerceIn(panBound(center.second, canvasHeight, side))
    return StudioIconBound(
        left = center.first + panX * canvasWidth - side / 2f,
        top = center.second + panY * canvasHeight - side / 2f,
        side = side,
    )
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

    // **The bound at the zoom this frame lands on, not the one it started at** — the clamp's regime depends on
    // whether the icon covers the canvas, so a pinch that grows past it has to be bounded by what it grew into.
    val side = restingSide(canvasWidth, canvasHeight) * newZoom

    return copy(
        panX = ((zoomedX - resting.first) / canvasWidth).coerceIn(panBound(resting.first, canvasWidth, side)),
        panY = ((zoomedY - resting.second) / canvasHeight).coerceIn(panBound(resting.second, canvasHeight, side)),
        zoom = newZoom,
    )
}

/** How far the layer rail is drawn from where it rests, in canvas pixels. See [railOffset]. */
internal data class RailOffset(val x: Float, val y: Float)

/**
 * Where the layer rail is actually drawn: its stored offset, **clamped to what fits on the canvas**.
 *
 * **The clamp is applied when the offset is read, not only when it is written, and that is the whole point of this
 * function existing beside [railDragged].** A stored offset is a promise about a rail of a particular size, and the
 * rail changes size for reasons that are not drags — it is flipped from a column to a row, collapsed or expanded,
 * given another layer, or handed a canvas of another shape by a rotation. Each of those can leave a perfectly good
 * offset describing a position that is now off the edge, and clamping only on the way *in* meant the rail sailed off
 * the screen taking its handle with it: nothing left to drag, and no menu to flip it back with. Which is the one
 * failure a movable control must not have, since every way of fixing it is the control itself.
 *
 * **And it is never written back**, which is this codebase's standing rule for a stored value invalidated by
 * something that was not about it — the same one the grid counts and the APPS list's row height are read under. So a
 * rail flipped to a row is pinned onto the canvas, and flipping it back to a column puts it exactly where the user
 * had dragged it rather than where the flip happened to leave it.
 *
 * **Both the resting place and the size are measured rather than declared**, which is what lets the caller change the
 * rail's padding, its cap, or the side it rests on without this function learning about any of it: the resting
 * top-left is the placed position less the offset currently applied, which is exact and not circular. See the call
 * site in `StudioLayerRail`.
 *
 * A degenerate measurement — the frame before the rail has been laid out, or a canvas of nothing — returns the stored
 * offset unclamped, for [railDragged]'s reason: clamping against zero would slam the rail into the corner for one
 * frame on the way in.
 *
 * A rail *longer than the canvas* — a row on a narrow phone — resolves to the head against the start edge, since the
 * clamped range collapses to a point. That is the best available answer rather than a fallback: the handle and the
 * menu it opens are at the head, so what stays reachable is what fixes it.
 */
internal fun IconStudioWorkspace.railOffset(
    canvasWidth: Float,
    canvasHeight: Float,
    railWidth: Float,
    railHeight: Float,
    restingLeft: Float,
    restingTop: Float,
): RailOffset {
    if (canvasWidth <= 0f || canvasHeight <= 0f || railWidth <= 0f || railHeight <= 0f) {
        return RailOffset(railX * canvasWidth, railY * canvasHeight)
    }
    return RailOffset(
        x = clampedRailOffset(restingLeft, railX * canvasWidth, canvasWidth, railWidth),
        y = clampedRailOffset(restingTop, railY * canvasHeight, canvasHeight, railHeight),
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
 * **The drag starts from where the rail is *drawn*, not from what is stored**, which is [railOffset]'s clamp applied
 * before this one rather than instead of it. The two differ exactly when the stored offset is out of range, and
 * starting from the stored value there would bank travel: a rail pinned at the edge by a flip would not move under the
 * finger until the drag had paid off the distance it was clamped by. Same defect `pinched` states for the pan, one
 * control over.
 *
 * A degenerate measurement returns the workspace untouched rather than clamping against zero.
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

    val drawn = railOffset(canvasWidth, canvasHeight, railWidth, railHeight, restingLeft, restingTop)

    return copy(
        railX = clampedRailOffset(restingLeft, drawn.x + dragX, canvasWidth, railWidth) / canvasWidth,
        railY = clampedRailOffset(restingTop, drawn.y + dragY, canvasHeight, railHeight) / canvasHeight,
    )
}

/**
 * [offset] reduced until the rail it moves lies wholly within the canvas, on one axis.
 *
 * Stated as an offset in and an offset out — rather than as a position — because the offset is what is stored and
 * what is applied, so every caller here would otherwise convert to a position and back around this one line.
 */
private fun clampedRailOffset(resting: Float, offset: Float, canvasExtent: Float, railExtent: Float): Float =
    (resting + offset).coerceIn(0f, (canvasExtent - railExtent).coerceAtLeast(0f)) - resting

/**
 * The range a pan fraction may take on one axis — **two regimes, decided by whether the icon is larger than the
 * canvas**.
 *
 * **Smaller than the canvas: keep the centre on it.** The clamp's whole job there is that the icon cannot be lost —
 * at worst a quarter of it sits in a corner and there is always something under the finger to drag back, which is
 * what lets this screen ship with no "reset view" button.
 *
 * **Larger than the canvas: keep the canvas inside the *icon*.** That is the opposite bound and it has to be,
 * because "the centre stays on the canvas" silently becomes a cage the moment the icon outgrows it: at a 4× zoom
 * the bound is two and a half canvases wide, so reaching its right-hand edge needs a centre well off the left of
 * the canvas — which the old rule refused. What a user got was an icon they had zoomed into and could not travel
 * across, stopping a fraction of the way over. This is the ordinary photo-viewer rule and it does the ordinary
 * thing: every part of a zoomed icon is reachable, and no part of the canvas ever shows blank beside it.
 *
 * The two ranges meet where the icon exactly covers the canvas, and the crossing is where the icon settles into
 * covering it — the same small snap every viewer makes when content grows past fit.
 *
 * Expressed as bounds on the *pan* rather than on the centre because the pan is what is stored, and clamping the
 * stored value is what stops a drag banking travel it will not spend. [CenterKeep] widens the small-icon regime at
 * both ends if the centre is ever allowed off the canvas.
 *
 * @param resting where the bound's centre sits on this axis with nothing panned.
 * @param extent the canvas on this axis.
 * @param side the bound's side **at the current zoom**, which is what decides the regime.
 */
private fun panBound(resting: Float, extent: Float, side: Float): ClosedFloatingPointRange<Float> {
    val (low, high) = if (side >= extent) {
        // The canvas stays within the icon: its far edge no closer than the canvas's, its near edge no further.
        (extent - side / 2f) to (side / 2f)
    } else {
        (-CenterKeep * extent) to (extent + CenterKeep * extent)
    }
    return ((low - resting) / extent)..((high - resting) / extent)
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
