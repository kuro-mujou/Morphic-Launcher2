package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.data.settings.IconStudioWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas viewport's arithmetic.
 *
 * **Tested because every one of these is wrong *silently*.** A viewport that anchors a pinch to the bound's center
 * instead of to the fingers still zooms; a clamp that banks travel against an edge still moves; a checkerboard drawn
 * at the old bound still draws. Each of them produces a plausible picture and a canvas that feels subtly wrong to
 * use, which is exactly the class of defect this codebase pulls into a pure function so it can be pinned — the split
 * `ContentMetrics`, `LegacyBackground` and `ShapeMask.inkFit` are already made along.
 */
class StudioViewportTest {

    private val canvas = 1000f
    private val topInset = 100f

    /** The bound at rest, which every other case is read against. */
    private fun resting() = studioIconBound(canvas, canvas, topInset, IconStudioWorkspace.Default)

    @Test
    fun `rests against the chrome at the top`() {
        // "All the way to the top" means exactly this: the bound's upper edge is the inset and nothing more.
        assertEquals(topInset, resting().top, Tolerance)
    }

    @Test
    fun `rests shifted off the rail's edge`() {
        // Left of center, because the layer rail rests down the end edge — see `IconBoundShift`.
        val center = resting().let { it.left + it.side / 2f }
        assertTrue("expected the bound left of center, was $center", center < canvas / 2f)
    }

    @Test
    fun `a drag moves the bound by the distance dragged`() {
        val dragged = IconStudioWorkspace.Default.pinched(
            canvasWidth = canvas,
            canvasHeight = canvas,
            topInset = topInset,
            centroidX = 500f,
            centroidY = 500f,
            dragX = 60f,
            dragY = 40f,
            zoomBy = 1f,
        )
        val bound = studioIconBound(canvas, canvas, topInset, dragged)

        assertEquals(resting().left + 60f, bound.left, Tolerance)
        assertEquals(resting().top + 40f, bound.top, Tolerance)
    }

    @Test
    fun `a pinch keeps the point between the fingers under them`() {
        // **The one that would be wrong and look right.** Zooming about the bound's own center also enlarges the
        // icon; what it does not do is leave the thing you pinched where your fingers are, so a user enlarging one
        // corner has to chase it across the canvas afterwards.
        val centroidX = 300f
        val centroidY = 250f
        val before = resting()

        val zoomed = IconStudioWorkspace.Default.pinched(
            canvasWidth = canvas,
            canvasHeight = canvas,
            topInset = topInset,
            centroidX = centroidX,
            centroidY = centroidY,
            dragX = 0f,
            dragY = 0f,
            zoomBy = 2f,
        )
        val after = studioIconBound(canvas, canvas, topInset, zoomed)

        // Where the centroid sat within the bound, as a fraction of it, is what must not move.
        assertEquals(
            (centroidX - before.left) / before.side,
            (centroidX - after.left) / after.side,
            Tolerance,
        )
        assertEquals(
            (centroidY - before.top) / before.side,
            (centroidY - after.top) / after.side,
            Tolerance,
        )
    }

    @Test
    fun `zoom is clamped and the centroid maths uses the clamped ratio`() {
        val atCeiling = IconStudioWorkspace.Default.copy(zoom = StudioZoomRange.endInclusive)
        val pushed = atCeiling.pinched(
            canvasWidth = canvas,
            canvasHeight = canvas,
            topInset = topInset,
            centroidX = 200f,
            centroidY = 200f,
            dragX = 0f,
            dragY = 0f,
            zoomBy = 2f,
        )

        assertEquals(StudioZoomRange.endInclusive, pushed.zoom, Tolerance)
        // **And nothing slid.** Applying the reported ratio rather than the effective one would go on moving the
        // center while the size held still — a pinch that pans, which reads as the canvas slipping out from under
        // the fingers at exactly the moment it stops growing.
        assertEquals(atCeiling.panX, pushed.panX, Tolerance)
        assertEquals(atCeiling.panY, pushed.panY, Tolerance)
    }

    @Test
    fun `a drag past the edge stops rather than banking travel`() {
        // Held against the edge for a while, then dragged back by one step. Without a clamp on the *stored* pan, the
        // way back would be dead until the banked distance had been paid off.
        var workspace = IconStudioWorkspace.Default
        repeat(20) {
            workspace = workspace.pinched(
                canvasWidth = canvas,
                canvasHeight = canvas,
                topInset = topInset,
                centroidX = 500f,
                centroidY = 500f,
                dragX = 200f,
                dragY = 0f,
                zoomBy = 1f,
            )
        }
        val pinned = studioIconBound(canvas, canvas, topInset, workspace)

        workspace = workspace.pinched(
            canvasWidth = canvas,
            canvasHeight = canvas,
            topInset = topInset,
            centroidX = 500f,
            centroidY = 500f,
            dragX = -50f,
            dragY = 0f,
            zoomBy = 1f,
        )
        val returned = studioIconBound(canvas, canvas, topInset, workspace)

        assertEquals(pinned.left - 50f, returned.left, Tolerance)
    }

    @Test
    fun `the bound's center never leaves the canvas`() {
        val shoved = IconStudioWorkspace.Default.pinched(
            canvasWidth = canvas,
            canvasHeight = canvas,
            topInset = topInset,
            centroidX = 500f,
            centroidY = 500f,
            dragX = 9_000f,
            dragY = 9_000f,
            zoomBy = 1f,
        )
        val bound = studioIconBound(canvas, canvas, topInset, shoved)
        val centerX = bound.left + bound.side / 2f
        val centerY = bound.top + bound.side / 2f

        // Which is the whole guarantee that lets the studio ship with no "reset view" button: at worst a quarter of
        // the icon is in a corner, and there is always something under the finger to drag back.
        assertTrue("center x escaped: $centerX", centerX in 0f..canvas)
        assertTrue("center y escaped: $centerY", centerY in 0f..canvas)
    }

    @Test
    fun `a zoomed icon can be travelled all the way across`() {
        // **The cage this replaced.** "Keep the center on the canvas" is the right bound for an icon smaller than
        // the canvas and silently becomes a trap once it is larger: reaching the right-hand edge of a bound two and
        // a half canvases wide needs a center well off the left, which the old rule refused — so a user could zoom
        // in and then not travel across what they had zoomed into.
        var workspace = IconStudioWorkspace.Default.copy(zoom = StudioZoomRange.endInclusive)
        repeat(40) {
            workspace = workspace.pinched(
                canvasWidth = canvas,
                canvasHeight = canvas,
                topInset = topInset,
                centroidX = 500f,
                centroidY = 500f,
                dragX = -300f,
                dragY = 0f,
                zoomBy = 1f,
            )
        }
        val pushed = studioIconBound(canvas, canvas, topInset, workspace)

        // Dragged as far left as it goes, the icon's *right* edge has arrived at the canvas's right edge — the whole
        // of that side is reachable — and has not gone past it, so no blank canvas shows beside it.
        assertEquals(canvas, pushed.left + pushed.side, Tolerance)
    }

    @Test
    fun `a zoomed icon never leaves blank canvas beside it`() {
        // The other end of the same rule: whatever the pan, an icon larger than the canvas covers it completely.
        var workspace = IconStudioWorkspace.Default.copy(zoom = StudioZoomRange.endInclusive)
        repeat(40) {
            workspace = workspace.pinched(
                canvasWidth = canvas,
                canvasHeight = canvas,
                topInset = topInset,
                centroidX = 500f,
                centroidY = 500f,
                dragX = 400f,
                dragY = 400f,
                zoomBy = 1f,
            )
        }
        val bound = studioIconBound(canvas, canvas, topInset, workspace)

        assertTrue("a gap opened on the left: ${bound.left}", bound.left <= 0f)
        assertTrue("a gap opened at the top: ${bound.top}", bound.top <= 0f)
    }

    @Test
    fun `an unmeasured canvas leaves the workspace alone`() {
        // The first frame, before layout has answered. Clamping against zero here would slam the icon into the corner
        // and the user would watch it jump on the way in.
        val untouched = IconStudioWorkspace.Default.copy(panX = 0.2f).pinched(
            canvasWidth = 0f,
            canvasHeight = 0f,
            topInset = 0f,
            centroidX = 0f,
            centroidY = 0f,
            dragX = 30f,
            dragY = 30f,
            zoomBy = 1.5f,
        )

        assertEquals(0.2f, untouched.panX, Tolerance)
        assertEquals(1f, untouched.zoom, Tolerance)
    }

    @Test
    fun `the rail is kept wholly on the canvas`() {
        // Stricter than the icon's clamp, and deliberately: the rail is a *control*, and one half off the screen is
        // one whose buttons cannot all be pressed.
        val railWidth = 60f
        val railHeight = 300f
        val restingLeft = canvas - railWidth - 12f
        val restingTop = topInset

        val shoved = IconStudioWorkspace.Default.railDragged(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = railWidth,
            railHeight = railHeight,
            restingLeft = restingLeft,
            restingTop = restingTop,
            dragX = -9_000f,
            dragY = 9_000f,
        )

        val left = restingLeft + shoved.railX * canvas
        val top = restingTop + shoved.railY * canvas
        assertEquals(0f, left, Tolerance)
        assertEquals(canvas - railHeight, top, Tolerance)
    }

    @Test
    fun `resetting the preview leaves the rail where it was put`() {
        // **The one a tidy-up would break.** `withPreviewReset` looks like it wants to be `Default`, and it must not
        // be: panning the icon and moving the rail out of the way are two different annoyances fixed at two different
        // moments, so the button that undoes one has no business undoing the other.
        val arranged = IconStudioWorkspace(panX = 0.3f, panY = -0.2f, zoom = 2.5f, railX = -0.4f, railY = 0.1f)
        val reset = arranged.withPreviewReset()

        assertEquals(IconStudioWorkspace.Default.panX, reset.panX, Tolerance)
        assertEquals(IconStudioWorkspace.Default.panY, reset.panY, Tolerance)
        assertEquals(IconStudioWorkspace.Default.zoom, reset.zoom, Tolerance)
        assertEquals(arranged.railX, reset.railX, Tolerance)
        assertEquals(arranged.railY, reset.railY, Tolerance)
    }

    @Test
    fun `the preview reads as at rest only when neither panned nor zoomed`() {
        // Which is what the reset button is disabled by, so it doubles as "have I moved this?".
        assertTrue(IconStudioWorkspace.Default.previewAtRest)
        // A rail dragged clear of the icon is not the preview having moved.
        assertTrue(IconStudioWorkspace.Default.copy(railX = -0.4f).previewAtRest)
        assertTrue(!IconStudioWorkspace.Default.copy(panX = 0.01f).previewAtRest)
        assertTrue(!IconStudioWorkspace.Default.copy(zoom = 1.01f).previewAtRest)
    }

    @Test
    fun `a rail that changed shape is pulled back onto the canvas`() {
        // **The axis flip, which is what this clamp was moved onto the read for.** A column dragged to the start edge
        // is a narrow thing at a position chosen for a narrow thing; made a row it is 300 wide at the same place, and
        // clamping only on the way in left it — and its handle, at the head — off the canvas with no way back.
        val dragged = IconStudioWorkspace.Default.railDragged(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = 60f,
            railHeight = 300f,
            restingLeft = canvas - 72f,
            restingTop = topInset,
            dragX = -9_000f,
            dragY = 0f,
        )

        // Same stored offset, now read against a rail lying down and resting somewhere else.
        val restingLeft = canvas - 300f
        val offset = dragged.railOffset(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = 300f,
            railHeight = 60f,
            restingLeft = restingLeft,
            restingTop = topInset,
        )
        val left = restingLeft + offset.x

        assertTrue("the head left the canvas: $left", left >= 0f)
        assertTrue("the tail left the canvas: ${left + 300f}", left + 300f <= canvas)
    }

    @Test
    fun `the correction is not written back`() {
        // Clamped on read, never on the way to storage — the rule the grid counts are read under. So flipping the
        // rail back restores where the user dragged it rather than where the flip happened to leave it.
        val arranged = IconStudioWorkspace.Default.copy(railX = -0.9f)
        arranged.railOffset(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = 300f,
            railHeight = 60f,
            restingLeft = canvas - 300f,
            restingTop = topInset,
        )

        assertEquals(-0.9f, arranged.railX, Tolerance)
    }

    @Test
    fun `a drag from a clamped rail moves at once`() {
        // The banking defect, one control over from the pan's. Starting the drag from the *stored* offset rather than
        // from where the rail is drawn would leave it dead under the finger until the clamp's distance was paid off.
        val railWidth = 300f
        val railHeight = 60f
        val restingLeft = canvas - railWidth
        val offScreen = IconStudioWorkspace.Default.copy(railX = -0.9f)

        val drawn = offScreen.railOffset(canvas, canvas, railWidth, railHeight, restingLeft, topInset)
        val nudged = offScreen.railDragged(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = railWidth,
            railHeight = railHeight,
            restingLeft = restingLeft,
            restingTop = topInset,
            dragX = 40f,
            dragY = 0f,
        )
        val moved = nudged.railOffset(canvas, canvas, railWidth, railHeight, restingLeft, topInset)

        assertEquals(drawn.x + 40f, moved.x, Tolerance)
    }

    @Test
    fun `a rail longer than the canvas keeps its head on screen`() {
        // A row on a narrow phone: the clamped range collapses to a point, and the point to pick is the one the
        // handle is at — the head — because that is what fixes the state.
        val offset = IconStudioWorkspace.Default.copy(railX = -0.5f).railOffset(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = canvas + 200f,
            railHeight = 60f,
            restingLeft = -100f,
            restingTop = topInset,
        )

        assertEquals(0f, -100f + offset.x, Tolerance)
    }

    @Test
    fun `an unmeasured rail is drawn where it was stored`() {
        // The frame before layout has answered, matching the drag's own degenerate case: clamping against zero here
        // would put the rail in the corner for a frame on the way in.
        val offset = IconStudioWorkspace.Default.copy(railX = -0.4f, railY = 0.2f).railOffset(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = 0f,
            railHeight = 0f,
            restingLeft = 0f,
            restingTop = 0f,
        )

        assertEquals(-0.4f * canvas, offset.x, Tolerance)
        assertEquals(0.2f * canvas, offset.y, Tolerance)
    }

    @Test
    fun `an unmeasured rail is not dragged`() {
        val untouched = IconStudioWorkspace.Default.railDragged(
            canvasWidth = canvas,
            canvasHeight = canvas,
            railWidth = 0f,
            railHeight = 0f,
            restingLeft = 0f,
            restingTop = 0f,
            dragX = 40f,
            dragY = 40f,
        )

        assertEquals(IconStudioWorkspace.Default, untouched)
    }
}

/** Generous against float error, tight against every distance these tests actually assert. */
private const val Tolerance = 0.001f
