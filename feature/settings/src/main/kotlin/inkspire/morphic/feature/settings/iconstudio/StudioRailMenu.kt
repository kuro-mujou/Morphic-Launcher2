package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.menu.MenuPlacement
import inkspire.morphic.core.designsystem.menu.menuOffsetFor
import inkspire.morphic.core.designsystem.menu.transformOrigin
import inkspire.morphic.data.settings.IconStudioWorkspace
import inkspire.morphic.data.settings.LayerRailAxis

/**
 * Which of the rail's two menus is showing.
 *
 * **A sum type over the pair rather than a boolean each**, which is what makes "both open at once" unrepresentable —
 * the same reason the launcher has one `LauncherMenuHost` for its item and surface menus rather than two hosts that
 * could each be up. They also answer questions at different scopes (one layer, or the stack), so a screen showing both
 * would be asking the user which of two overlapping panels the next tap belongs to.
 */
enum class RailMenu {

    /** What can be done to the **selected layer**: reorder, hide, delete. Opened by long-pressing its tile. */
    LAYER,

    /** What can be done to the **stack itself**: which way it runs, how much of it shows. Opened from the handle. */
    STACK,
}

/**
 * Where the rail's menu opens, given where the rail currently is.
 *
 * **Not [inkspire.morphic.core.designsystem.menu.menuPlacementFor], and the difference is the whole point of this
 * function.** That one picks its axis from the *screen's* shape — stack the menu above or below on a tall screen,
 * beside on a wide one — which is right for an icon in a grid, a thing roughly as wide as it is tall. The rail is not
 * that: it is a long thin bar whose own direction the user chooses, so the axis that matters is **the rail's**, not
 * the screen's. Applying the shared rule would put a menu directly over the tiles of a vertical rail on every phone.
 *
 * So the menu goes **across** the rail — beside a column, above or below a row — and then flips toward whichever half
 * of [frame] the rail is *not* in, which is the shared rule's second half kept exactly. Everything after this decision
 * is shared: [menuOffsetFor] does the placement and the clamping, and [transformOrigin] the reveal.
 *
 * @param anchor the rail's own bounds, in the same space as [frame].
 */
internal fun railMenuPlacement(anchor: IntRect, frame: IntRect, axis: LayerRailAxis): MenuPlacement =
    when (axis) {
        LayerRailAxis.VERTICAL -> {
            val centerX = (anchor.left + anchor.right) / 2
            if (centerX < frame.left + frame.width / 2) MenuPlacement.RIGHT else MenuPlacement.LEFT
        }

        LayerRailAxis.HORIZONTAL -> {
            val centerY = (anchor.top + anchor.bottom) / 2
            if (centerY < frame.top + frame.height / 2) MenuPlacement.BELOW else MenuPlacement.ABOVE
        }
    }

/**
 * The rail's menu, placed against wherever the rail is now.
 *
 * **It is a sibling of the rail rather than a child of it, and that is a hit-testing fact rather than a preference.**
 * The menu used to be one more item in the rail's own `Row`, pinned to the leading side — which was fine while the
 * rail was pinned to the end edge and could be nowhere else. Now that it can be dragged anywhere and turned on its
 * side, the menu has to be placed *outside* the rail's bounds on whichever side has room; a child drawn outside its
 * parent is visible but **not touchable**, because Compose does not hit-test past a parent's bounds. So it moved out
 * here, into a full-screen node, which is exactly the shape `ContextMenu` takes for the same reason.
 *
 * The measure-and-place is `ContextMenu`'s too, deliberately copied rather than approximated: a `Layout` places the
 * panel in the same pass that measures it, so there is no first frame at the wrong offset and no size held in state.
 *
 * **The exit is owned here.** [onDismiss] fires when the transition has settled rather than when it starts, so the
 * caller's state outlives the animation — without it the panel would be dropped from the tree on the first frame of
 * the fade and would vanish rather than close.
 *
 * @param anchor the rail's bounds, which is what the placement is computed against — the rail measures itself and
 *   reports them, so this cannot drift from where the rail actually is.
 * @param frame the area the menu may occupy: the canvas less `uiInsets`.
 */
@Composable
internal fun StudioRailMenu(
    menu: RailMenu,
    state: IconStudioState,
    workspace: IconStudioWorkspace,
    anchor: Rect,
    frame: IntRect,
    hazeState: HazeState,
    onMove: (up: Boolean) -> Unit,
    onToggleVisible: () -> Unit,
    onRemove: () -> Unit,
    onToggleAxis: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val bounds = remember(anchor) {
        IntRect(anchor.left.toInt(), anchor.top.toInt(), anchor.right.toInt(), anchor.bottom.toInt())
    }
    // Resolved once and read by both the reveal (in composition) and the placement (in measurement), so a menu
    // cannot scale out of one edge while being placed against another — `ContextMenu`'s own note.
    val placement = remember(bounds, frame, workspace.railAxis) {
        railMenuPlacement(bounds, frame, workspace.railAxis)
    }

    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState) onDismiss()
    }
    val dismiss: () -> Unit = { visible.targetState = false }

    Layout(
        modifier = modifier,
        content = {
            val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedVisibility(
                visibleState = visible,
                // Out of the edge nearest the rail, so the panel reads as having come from the thing it acts on —
                // which is the whole reason the placement is resolved before the reveal rather than after it.
                enter = fadeIn(fade) + scaleIn(spatial, 0.85f, placement.transformOrigin()),
                exit = fadeOut(fade) + scaleOut(spatial, 0.9f, placement.transformOrigin()),
            ) {
                MenuPanel(hazeState) {
                    when (menu) {
                        RailMenu.LAYER -> LayerMenuRows(
                            state = state,
                            axis = workspace.railAxis,
                            onMove = onMove,
                            onToggleVisible = onToggleVisible,
                            onRemove = { onRemove(); dismiss() },
                        )

                        RailMenu.STACK -> StackMenuRows(
                            workspace = workspace,
                            onToggleAxis = onToggleAxis,
                            onToggleCollapsed = onToggleCollapsed,
                        )
                    }
                    MenuRow(Icons.Default.Close, "Close", enabled = true, onClick = dismiss)
                }
            }
        },
    ) { measurables, constraints ->
        // Empty for the frame between the exit finishing and [onDismiss] taking this out of the tree.
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val placeable = measurable.measure(
            Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight),
        )
        val offset = menuOffsetFor(
            anchor = bounds,
            menuSize = IntSize(placeable.width, placeable.height),
            frame = frame,
            placement = placement,
            gapPx = gapPx,
        )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offset) }
    }
}

/**
 * What can be done to the **selected layer**.
 *
 * **Every row that would do nothing is disabled rather than absent**, which is the reason the reorder controls were
 * buttons and never a drag: a disabled row says which move is illegal *before* it is attempted, where a refused
 * gesture does nothing and cannot explain itself. The answers come from the model (`editing.moveUp(i) !== editing`),
 * so they cannot drift from the rule the set enforces.
 *
 * **Reorder and hide leave the menu open; delete closes it.** The first three are things done *repeatedly* and judged
 * by looking — moving a layer two places is two presses, and hiding one is a question about the icon you have to see
 * answered — so closing after each would make the menu a thing to keep reopening. It also makes the disabled rows work
 * for you: the first row grays out at the moment the layer reaches the top, which answers "how far can this go?" while
 * you are asking it. Delete closes because the layer it acted on is gone, and a menu left up would silently re-point at
 * whatever the selection fell to.
 *
 * **The two reorder rows are named and drawn for the axis the rail is currently on.** The *operation* is one thing —
 * `moveUp` is a step toward the top of the stack whichever way the rail runs — but "Move up" beside an upward arrow is
 * a description of the **column**, and against a row it names a direction nothing moves in. The rail draws the top
 * layer first, so up-the-stack is up in a column and toward the start in a row; [axis] is what picks which of those to
 * say. Deliberately not a third vocabulary for both cases ("bring forward" / "send back"): those are true of either
 * layout and are what a *drawing app* says, but this rail is showing the layers in a line, and the direction they will
 * actually travel is the more useful thing to promise.
 *
 * The arrows are the **auto-mirrored** ones, so in RTL — where a `Row` places its first child on the right —
 * the picture flips with the layout and goes on pointing at the tile the press will move this one past. The *words*
 * stay LTR-shaped, which is the one thing here that does not mirror; it is also true of every other string in the
 * studio, so it is the existing debt rather than a new one, and it is settled the day these are localized.
 */
@Composable
private fun LayerMenuRows(
    state: IconStudioState,
    axis: LayerRailAxis,
    onMove: (up: Boolean) -> Unit,
    onToggleVisible: () -> Unit,
    onRemove: () -> Unit,
) {
    val spec = state.selectedLayer ?: return
    val vertical = axis == LayerRailAxis.VERTICAL

    MenuRow(
        icon = if (vertical) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        label = if (vertical) "Move up" else "Move left",
        enabled = state.canMoveUp,
    ) { onMove(true) }
    MenuRow(
        icon = if (vertical) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        label = if (vertical) "Move down" else "Move right",
        enabled = state.canMoveDown,
    ) { onMove(false) }
    MenuRow(
        icon = if (spec.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        label = if (spec.visible) "Hide" else "Show",
        enabled = true,
        onClick = onToggleVisible,
    )
    MenuRow(Icons.Default.Delete, "Delete", state.canRemoveSelected, onClick = onRemove)
}

/**
 * What can be done to the **stack itself** — which way it runs, and how much of it shows.
 *
 * **Two rows, both toggles that name the state they move to** rather than the state they are in, which is the quick
 * menu's own Hide/Show shape: a row is read as a thing to press, so it should say what pressing it does.
 *
 * **Neither closes the menu**, for the layer menu's reason exactly: both are judged by looking at the result, and
 * flipping a rail to a row to see whether that helps is a decision made *while* the menu is up. Collapsing while
 * collapsed is the same row saying Expand a frame later — which is the answer to "what did that do?" given in place.
 *
 * **Collapse shrinks the list's viewport to one tile; it does not hide any layer.** Which is why the row is a plain
 * toggle with nothing to warn about: every layer is still there and still one flick away, so pressing it can lose the
 * user nothing.
 *
 * There is deliberately no *Reset the rail's position* row. The rail is clamped to the canvas, so it can always be
 * dragged back by hand, and a row that duplicates a gesture the user has just performed earns nothing.
 */
@Composable
private fun StackMenuRows(
    workspace: IconStudioWorkspace,
    onToggleAxis: () -> Unit,
    onToggleCollapsed: () -> Unit,
) {
    val vertical = workspace.railAxis == LayerRailAxis.VERTICAL
    MenuRow(
        icon = if (vertical) Icons.Default.ViewColumn else Icons.Default.ViewStream,
        label = if (vertical) "Lay out as a row" else "Lay out as a column",
        enabled = true,
        onClick = onToggleAxis,
    )
    MenuRow(
        icon = if (workspace.railCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
        label = if (workspace.railCollapsed) "Expand" else "Collapse",
        enabled = true,
        onClick = onToggleCollapsed,
    )
}

/** The sheet both menus are drawn on, so neither can arrive with its own idea of the material. */
@Composable
private fun MenuPanel(hazeState: HazeState, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(176.dp)
            .studioSurface(hazeState, shape = RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = StudioContentColor.copy(alpha = if (enabled) 1f else 0.35f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}
