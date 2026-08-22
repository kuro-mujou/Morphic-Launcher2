package inkspire.morphic.core.designsystem.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One draggable coordinate cell — the per-item wiring every free-placement surface repeats, shared by the
 * single-zone [CoordinateDragGrid] and the paged [CoordinateDragPager] so there is one copy of it.
 *
 * Given the cell's placement [modifier] (from the grid layout), it applies the drag-state visuals:
 * - occupants glide to their (previewed) cells via [animatePlacement]; the **lifted** item skips the animation
 *   and is drawn invisible (`alpha = 0`) — the floating proxy in the surface's overlay stands in for it.
 * - so does an item whose placement the finger is driving *in place* ([tracksFinger]) — a live resize. It is the
 *   lifted item's reason without the proxy: a spring settling toward where the finger already is reads as lag,
 *   not as motion, and here the resize frame is drawn over the cell so the two would visibly disagree.
 *
 * **The gestures are handed to [content], not applied to the cell.** A cell is a *layout* footprint and is
 * usually much larger than the item drawn in it — a home cell is a whole 2×2 visual slot around an icon and a
 * one-line label. If the cell claimed the gestures, every pixel of that slack would lift/launch the item, and on
 * a full page there would be nowhere left to press for the surface's own long-press (the wallpaper / home
 * options menu). So [content] receives `itemGestures` and decides what is actually touchable: an icon cell puts
 * it on the icon+label group (see [inkspire.morphic.core.designsystem.cell.IconLabelCell]), while content that
 * genuinely fills its cell — a widget, a harness tile — just `.then()`s it onto its own root. Whatever is left
 * uncovered stays free: [launcherItemGestures] never consumes a down, so those events reach the surface beneath.
 *
 * Handing down a modifier (rather than reading it from a composition local) keeps the choice visible in the
 * signature: content that forgets to apply it is silently un-draggable, and a parameter says so where a local
 * would not.
 *
 * A tap fires [onOpen], a long-press [onShowMenu], and a press-and-swipe in [edgeActions] fires [onEdgeAction] —
 * all only within whatever bounds [content] gave the gestures.
 *
 * @param tracksFinger this item's placement is being driven directly by the finger right now (a live resize), so
 *   it must land on each new cell at once rather than glide there.
 * @param onRelease the finger came up on this cell, ending the drag. It is **not** where the drop is committed —
 *   that belongs to the zone the drag landed in ([inkspire.morphic.core.designsystem.drag.DropZone.onDrop]), which
 *   may be on a different surface entirely from this cell. What the surface does here is its own bookkeeping about
 *   a drag *leaving* it, and calling `coordinator.drop()` so the landing is dispatched at all.
 */
@Composable
fun LauncherDragCell(
    coordinator: DragCoordinator,
    item: GridItem,
    gestureConfig: ItemGestureConfig,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    edgeActions: Set<SwipeDirection> = emptySet(),
    tracksFinger: Boolean = false,
    onOpen: () -> Unit = {},
    onShowMenu: (anchorInRoot: Rect) -> Unit = {},
    onEdgeAction: (SwipeDirection) -> Unit = {},
    content: @Composable (itemGestures: Modifier) -> Unit,
) {
    val isDragged = coordinator.session?.item == item
    val pull = rememberSwipePull(gestureConfig)
    Box(
        modifier
            .then(if (isDragged || tracksFinger) Modifier else Modifier.animatePlacement())
            // **A draw-time translation, not a layout offset, and `animatePlacement` above is the reason.** That
            // modifier reads `positionInParent()` in `onPlaced` and renders the difference from where it is
            // animating to — so a pull expressed as layout is seen as the cell having *moved*, compensated with an
            // equal and opposite offset on the same frame, and sprung back to nothing. The icon did not visibly
            // budge. A `graphicsLayer` translation never touches placement, so nothing upstream can see it, and it
            // costs a draw rather than a re-layout.
            .graphicsLayer {
                alpha = if (isDragged) 0f else 1f
                translationX = pull.x()
                translationY = pull.y()
            },
    ) {
        content(
            Modifier.launcherItemGestures(
                config = gestureConfig,
                edgeActions = edgeActions,
                onOpen = onOpen,
                onEdgeAction = onEdgeAction,
                onSwipePull = pull::onPull,
                onShowMenu = onShowMenu,
                onBeginDrag = { root -> coordinator.start(item, root) },
                onDragTo = { root -> coordinator.moveTo(root) },
                onDrop = { onRelease() },
                onCancelDrag = { coordinator.cancel() },
            ),
        )
    }
}

/**
 * **How far a claimed swipe pulls the item, and how it comes back.**
 *
 * A swipe that has been claimed by the item does not fire until release, so without this the user presses, drags,
 * and sees nothing at all until an action happens — indistinguishable from a hidden trigger. The item following the
 * finger is what makes it a *pull*.
 *
 * **Resisted and capped rather than tracking one-to-one.** An icon that followed the finger across the screen would
 * read as a drag, which is a different gesture on this launcher and one the user has not made. Moving a little and
 * then refusing to move further says "held, and it will fire when you let go" — and the cap is what keeps the icon
 * inside its own cell, where a neighbour is a few dp away.
 *
 * **The travel is measured past the slop that recognized the swipe**, so the pull begins at zero at the moment of
 * recognition instead of jumping to whatever distance the finger had already covered. The surface pan owes the same
 * debt and pays it the same way (`pastSlop`).
 *
 * **Projected onto the committed direction's axis**, never the raw offset: the direction was locked at recognition,
 * so letting a curving finger drag the icon sideways would show a gesture other than the one that will fire.
 */
@Composable
private fun rememberSwipePull(config: ItemGestureConfig): SwipePull {
    val cap = with(LocalDensity.current) { PullCap.toPx() }
    val scope = rememberCoroutineScope()
    return remember(config, cap, scope) { SwipePull(config.touchSlopPx, cap, scope) }
}

/** The live pull for one cell. Written from the pointer thread, read at layout. */
@Stable
private class SwipePull(
    private val slopPx: Float,
    private val capPx: Float,
    private val scope: CoroutineScope,
) {

    private var travel by mutableFloatStateOf(0f)

    // Snapshot state, unlike an ordinary holder field: it is read in the draw lambda alongside [travel], and a plain
    // `var` there would leave the first frame of a pull drawn against a stale direction.
    private var direction by mutableStateOf<SwipeDirection?>(null)
    private var settle: Job? = null

    /** How far the cell is drawn from its placed position, in pixels. Read at draw. */
    fun x(): Float = when (direction) {
        SwipeDirection.LEFT -> -travel
        SwipeDirection.RIGHT -> travel
        else -> 0f
    }

    /** The vertical half of [x]. */
    fun y(): Float = when (direction) {
        SwipeDirection.UP -> -travel
        SwipeDirection.DOWN -> travel
        else -> 0f
    }

    /** A null [offsetFromDown] ends the pull; anything else moves it. */
    fun onPull(swipe: SwipeDirection, offsetFromDown: Offset?) {
        if (offsetFromDown == null) {
            settleBack()
            return
        }
        settle?.cancel()
        direction = swipe
        val along = when (swipe) {
            SwipeDirection.LEFT -> -offsetFromDown.x
            SwipeDirection.RIGHT -> offsetFromDown.x
            SwipeDirection.UP -> -offsetFromDown.y
            SwipeDirection.DOWN -> offsetFromDown.y
        }
        // Only movement *toward* the committed direction pulls: dragging back past the start would otherwise push
        // the icon out the opposite side of a swipe it is still committed to.
        travel = ((along - slopPx).coerceAtLeast(0f) * PullResistance).coerceAtMost(capPx)
    }

    /**
     * Springs back to rest.
     *
     * One coroutine per gesture end rather than one per frame — the animation writes [travel] directly, and nothing
     * composes against it, so each frame costs a layout and no more.
     */
    private fun settleBack() {
        settle?.cancel()
        settle = scope.launch {
            val from = travel
            animate(initialValue = from, targetValue = 0f, animationSpec = spring()) { value, _ ->
                travel = value
            }
            direction = null
        }
    }
}

/**
 * How much of the finger's travel the item takes, and how far it may go.
 *
 * Both are feel rather than derivation, and they are named because they are the whole of it. Half the travel is
 * what makes the icon read as *following* rather than twitching — at a third, a swipe of ordinary length moved it
 * only a few pixels and the pull was invisible. 16dp is as far as it may go, which keeps an icon clear of its
 * neighbours on the tightest grid this launcher offers.
 */
private const val PullResistance = 0.5f
private val PullCap = 16.dp
