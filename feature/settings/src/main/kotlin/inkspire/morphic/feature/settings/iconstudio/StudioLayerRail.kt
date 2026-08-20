package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.icon.compose.IconPreview
import inkspire.morphic.data.settings.IconStudioWorkspace
import inkspire.morphic.data.settings.LayerRailAxis
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The stack, always on screen: one tile per layer down the end edge of the canvas, with a `+` at the end.
 *
 * **This replaces the Layers *section*, and the tool bar lost an entry to it.** `StudioToolPanel` recorded the
 * problem it solves: *"the header names the selected layer, not just the tool, and that is the one thing the bar
 * cost us — while the stack was permanently on screen, 'which layer am I editing?' was answered by looking at it;
 * now the stack is behind its own entry."* A rail puts that back, and once it also reorders, hides and deletes,
 * the section's only remaining job was *add* — which is not a section, it is a button, and it belongs where the
 * layers are.
 *
 * **Tap selects; long-press selects *and* opens the quick menu.** Selecting first is what lets one set of commands
 * serve every tile, which is the rule the old eye button already followed — an action on an unselected row would
 * silently act on a different layer. So the menu reads `state.canMoveUp` and friends, which are answers about the
 * selected layer, and needs no per-index variant of any of them.
 *
 * **Drawn top layer first**, matching the list it replaces and the order the layers are drawn on screen. That
 * reversal reaches further than this file: `IconStudioViewModel.removeSelected` moves the selection **down** an
 * index to keep the highlight on the same tile, which only makes sense while they are drawn this way round.
 *
 * **On the end edge, not "the right"** — RTL-aware, for `SideZoneEdge`'s own reason. It **rests** at the top of the
 * workspace, directly below the back and history buttons, and is capped so it scrolls rather than growing into the
 * tool panel below. The **stack** is what scrolls: `+` is pinned below it, because a control that adds a layer must
 * not be pushed out of reach by the layer it just added.
 *
 * **And resting is all it is now: the rail can be dragged anywhere on the canvas by the handle at its head.** Which
 * is what the handle is for and why it sits *above* the composite tile rather than beside the `+` — it belongs to the
 * whole rail, and the head is the one position that is not next to some particular layer. The offset is persisted
 * (see [IconStudioWorkspace]), so a user who moves the rail out of their own way finds it there next time.
 *
 * A **handle rather than a drag on the tiles themselves**, for the reason the reorder buttons already give: the tiles
 * are targets — tap selects, long-press opens the quick menu — and a third gesture on the same 48dp square would have
 * to be told apart from those two by timing. A grab bar has nothing else to mean.
 *
 * **The handle carries the stack's own menu as well**, which is the one place two gestures do share a target — and
 * they can, because they are the same two the tiles already carry: drag and long-press, told apart by whether the
 * finger moves. See [RailDragHandle] for the race, and for what happens when a user long-presses and *then* drags.
 *
 * **It runs as a column or a row, and its list of layers collapses to one tile's worth of viewport**, both from that
 * menu and both persisted. The two arms below are the only thing not shared between them: a `Column` and a `Row` are
 * two layouts, and `weight` is scope-specific, so the container is written twice and everything inside it once.
 *
 * **Changing either one re-clamps where the rail sits, which is why the offset is *resolved* and not simply read.** A
 * stored offset describes a rail of the size it had when it was dragged — so a column dragged to the start edge, made
 * a row, is suddenly a wide thing at a position chosen for a narrow one, and it leaves the canvas taking its handle
 * with it. That is the one failure a movable control must not have, since the handle is what would fix it. See
 * [railOffset], which also says why the correction is not written back.
 *
 * **Four bands, and only one of them scrolls**: the handle, the composite tile, the layers, then `+`. The three that
 * do not are pinned because none of them is a layer — a control that adds one must not be pushed out of reach by the
 * layer it just added, and the composite is what the whole stack draws into. Collapsing shortens the one that does.
 *
 * @param workspace the rail's whole arrangement — where it was dragged, which way it runs, whether it is collapsed.
 * @param canvasWidth the studio canvas, in pixels, which the rail is clamped inside. Zero before it is measured, which
 *   [railDragged] treats as "not yet" rather than as a canvas of nothing.
 * @param menu which of the rail's menus is showing, or null. **Hoisted**, so a tap on the canvas can put it away —
 *   which is the one thing the rail cannot know about — and so the two menus cannot both be up.
 * @param onBoundsChange the rail's bounds in canvas space, reported as they change. The menus are drawn *outside* the
 *   rail and so cannot be children of it (Compose does not hit-test past a parent's bounds), which means the thing
 *   that positions them needs this.
 */
@Composable
internal fun StudioLayerRail(
    state: IconStudioState,
    hazeState: HazeState,
    workspace: IconStudioWorkspace,
    canvasWidth: Float,
    canvasHeight: Float,
    menu: RailMenu?,
    customImage: (path: String) -> android.graphics.drawable.Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> android.graphics.drawable.Drawable?,
    onSelect: (StudioTarget) -> Unit,
    onAdd: () -> Unit,
    onMenuChange: (RailMenu?) -> Unit,
    onWorkspaceChange: (IconStudioWorkspace) -> Unit,
    onWorkspaceCommit: () -> Unit,
    onBoundsChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which layers were already on screen last time this composed, so a tile that is genuinely **new** can arrive
    // while the ones that merely moved glide. Held as a plain var behind a `remember` and advanced in a
    // `SideEffect`: reading it during composition is what decides the entrance, and it must not change under that
    // composition. Ported from the layer list this rail replaced, which had the same problem.
    var known by remember { mutableStateOf(emptySet<Long>()) }
    val current = state.editing.layers.indices.map(state::layerKey).toSet()
    SideEffect { known = current }

    // **What the rail measures about itself, so where it sits can be clamped without anyone declaring its geometry.**
    // The resting top-left is the placed position *less* the offset currently applied — exact, and not circular,
    // because the offset is a value we already hold. That is what lets the caller change the padding, the cap or the
    // edge the rail rests on with nothing here to keep in step.
    //
    // Read by the *draw* as well as by the drag now, which is what makes an axis flip safe: the size these hold is
    // one frame behind the flip, so the first composition after it clamps against the old shape and the next against
    // the new. One frame, and the alternative — measuring in composition — is not available.
    var railSize by remember { mutableStateOf(Size.Zero) }
    var restingTopLeft by remember { mutableStateOf(Offset.Zero) }

    // **Resolved rather than read, because the rail's size changes for reasons that are not drags** — the axis flip
    // most of all, but also collapsing, a layer added, or a rotation. A stored offset describes a rail of the size it
    // had when it was dragged, so every one of those can leave it pointing off the edge; [railOffset] is what keeps
    // the drawn rail on the canvas whatever happened to it, and it deliberately does not write the correction back.
    val offset = workspace.railOffset(
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        railWidth = railSize.width,
        railHeight = railSize.height,
        restingLeft = restingTopLeft.x,
        restingTop = restingTopLeft.y,
    )
    val offsetX = offset.x
    val offsetY = offset.y
    val vertical = workspace.railAxis == LayerRailAxis.VERTICAL

    // **Collapsing shrinks the viewport; it does not shorten the list.** The first cut drew only the selected layer,
    // which made collapse a different rail rather than a smaller one — you could not reach any other layer without
    // expanding first, so the state it left you in was one you had to leave to do anything. Capping the scroll band
    // to a single tile says the same thing about screen space and costs nothing: every layer is still there, still in
    // order, still one flick away, and the selected one is still scrolled into view by [LayerTile]'s own effect.
    val bandExtent = if (workspace.railCollapsed) TileSide else RailScrollExtent

    val handle = @Composable {
        // **The grab bar, at the head of the rail and before everything in it.** It moves and configures the whole
        // rail, so it belongs where it is next to no particular layer — and being first it is also what a user
        // reaches for when the rail is in the way of what they are looking at.
        RailDragHandle(
            vertical = vertical,
            onDrag = { dragX, dragY ->
                onWorkspaceChange(
                    workspace.railDragged(
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        railWidth = railSize.width,
                        railHeight = railSize.height,
                        restingLeft = restingTopLeft.x,
                        restingTop = restingTopLeft.y,
                        dragX = dragX,
                        dragY = dragY,
                    ),
                )
            },
            onDragEnd = onWorkspaceCommit,
            onMenuChange = onMenuChange,
        )
    }

    // **The whole icon, at the head of the stack and *pinned* there** — before the top layer, because that is where
    // it sits: it is what everything beneath composites into. Separated by a rule so it does not read as one more
    // slot, and given no long-press menu, since none of those verbs mean anything for it.
    //
    // Outside the scroll band, which is what collapsing made necessary: a rail cut down to one tile has to be one
    // tile *of the list*, and a composite scrolling away with the layers would leave a collapsed rail showing
    // whichever of the two the finger last stopped on. Pinning it costs the expanded rail a tile's worth of scroll
    // and buys the thing being edited always being on screen.
    val composite = @Composable {
        CompositeTile(
            state = state,
            customImage = customImage,
            packImage = packImage,
            onClick = {
                onSelect(StudioTarget.Composite)
                onMenuChange(null)
            },
        )
        RailDivider(vertical)
    }

    val tiles = @Composable {
        state.editing.layers.indices.reversed().forEach { index ->
            // `key` gives the tiles identity the model cannot — see `IconStudioState.layerKey` — so an insert
            // *moves* the tiles beneath rather than rebuilding them, and `animatePlacement` has something to glide.
            key(state.layerKey(index)) {
                LayerTile(
                    state = state,
                    index = index,
                    entering = state.layerKey(index) !in known,
                    vertical = vertical,
                    customImage = customImage,
                    packImage = packImage,
                    onClick = {
                        onSelect(StudioTarget.Layer(index))
                        onMenuChange(null)
                    },
                    onLongClick = {
                        onSelect(StudioTarget.Layer(index))
                        onMenuChange(RailMenu.LAYER)
                    },
                )
            }
        }
    }

    val addButton = @Composable {
        // **Separated, because a tile is a layer and this is not.** Flush among them it reads as another slot in the
        // stack — the same misreading the old layer list's buttons had, and the same fix.
        RailDivider(vertical)
        StudioIconButton(
            icon = Icons.Default.Add,
            contentDescription = "Add layer",
            onClick = {
                onAdd()
                onMenuChange(null)
            },
            modifier = Modifier.size(TileSide),
        )
    }

    // **The scroll band's cap is stated rather than weighted, which is what lets one number serve both arms.** It was
    // `weight(1f, fill = false)` inside a capped column — correct, and unavailable here, because `weight` belongs to
    // `ColumnScope` or `RowScope` and the two arms need the same answer. Capping the band directly says the same
    // thing: the pinned handle and `+` are measured on their own and the band takes [RailScrollExtent], which is
    // [RailMaxHeight] less exactly what those two occupy. The rail is still its content's size when it holds little.
    val surface = modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .onGloballyPositioned { coordinates ->
            railSize = coordinates.size.toSize()
            val placed = coordinates.positionInParent()
            restingTopLeft = Offset(placed.x - offsetX, placed.y - offsetY)
            onBoundsChange(Rect(placed, coordinates.size.toSize()))
        }
        .studioSurface(hazeState, shape = RoundedCornerShape(RailCorner))
        .padding(RailPadding)

    // **The band's extent is the only thing on the rail that animates**, and every reason it changes arrives here: a
    // layer added or removed, and collapsing. One animation rather than two, for the reason `StudioToolPanel` states —
    // nested size animations mean the outer chases a target that is itself still moving, which reads as lag. The
    // surface follows for free because it wraps this.
    //
    // No clip of its own: a scroller already clips to its bounds, and the band no longer touches the rail's rounded
    // corners now that the composite tile is pinned above it.
    val band = Modifier.animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())

    if (vertical) {
        Column(
            modifier = surface,
            verticalArrangement = Arrangement.spacedBy(RailGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            handle()
            composite()
            Column(
                modifier = band
                    .heightIn(max = bandExtent)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RailGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { tiles() }
            addButton()
        }
    } else {
        Row(
            modifier = surface,
            horizontalArrangement = Arrangement.spacedBy(RailGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            handle()
            composite()
            Row(
                modifier = band
                    .widthIn(max = bandExtent)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RailGap),
                verticalAlignment = Alignment.CenterVertically,
            ) { tiles() }
            addButton()
        }
    }
}

/** The rule between the composite and the layers, and between the layers and `+`, laid across the rail's own run. */
@Composable
private fun RailDivider(vertical: Boolean) {
    if (vertical) {
        HorizontalDivider(color = RailDividerColor, modifier = Modifier.width(TileSide))
    } else {
        VerticalDivider(color = RailDividerColor, modifier = Modifier.height(TileSide))
    }
}

/**
 * The bar that moves the whole rail — and, on a long press, opens the stack's own menu.
 *
 * **A grab bar and not a drag on the tiles**, because the tiles already carry two gestures — tap selects, long-press
 * opens the layer menu — and a third on the same square would have to be distinguished from those by timing alone.
 *
 * **It looks like a sheet's handle on purpose.** That shape is already the platform's word for "this whole surface
 * moves", so it needs no label and no discovery — which matters for a control that would otherwise be a plain gap at
 * the head of a column of pictures. It turns with the rail, because a bar across the run of a row would read as a
 * divider rather than as something to grab.
 *
 * The bar itself is deliberately much smaller than the slot it sits in: [HandleWidth] × [HandleThickness] is what is
 * *drawn*, and the slot around it is the press target, so the mark can be discreet without the control being fiddly.
 * Same split as `StudioIconButton`'s glyph inside its 40dp face.
 *
 * ### The gesture, which is three states rather than two callbacks
 *
 * A press here can become either of two things, and — this is the part that is not standard — it can become the
 * *second* after having already become the first. So this is a hand-written `awaitEachGesture` rather than
 * `detectDragGestures` plus a long-press detector, which could not express the hand-off:
 *
 * 1. **The race.** From the down, whichever comes first: the finger crosses touch slop (a drag) or the long-press
 *    timeout elapses (the menu). `withTimeoutOrNull` around `awaitTouchSlopOrCancellation` decides it, and the result
 *    is wrapped so that "timed out" and "the finger lifted before slop" stay distinguishable — both are null on their
 *    own, and they mean opposite things.
 * 2. **Drag after menu.** If the menu opened and the finger has *not* lifted, this waits for slop again with no
 *    timeout. A drag from there hides the menu, moves the rail, and **puts the menu back when the finger lifts** —
 *    which is what makes "long-press, then reposition, then keep choosing" one gesture instead of three. The menu is
 *    re-opened rather than merely re-shown, so it is placed against where the rail *now* is.
 * 3. **Neither.** The finger lifted: a long press leaves its menu up, and a plain tap does nothing at all. A tap is
 *    deliberately not a third meaning — the handle would then have every gesture there is, and the menu it would open
 *    is the one a long press already opens.
 *
 * A drag edits live and commits when the finger lifts — the studio's rule for anything draggable, and here it is what
 * keeps one reposition one write to the settings store rather than one per frame.
 */
@Composable
private fun RailDragHandle(
    vertical: Boolean,
    onDrag: (dragX: Float, dragY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMenuChange: (RailMenu?) -> Unit,
) {
    // **Read through `rememberUpdatedState`, and this is load-bearing rather than tidy.** `pointerInput(Unit)` runs
    // its block *once*, so a lambda referenced directly inside it is the one built by the **first** composition —
    // which closed over the workspace as it was then, and over a rail that had not been measured. That is not a stale
    // *position*, it is a stale *whole value*: every frame of a drag recomputed from `IconStudioWorkspace.Default`, so
    // the rail never moved (its offset was always one step from zero, against an unmeasured size the clamp refused)
    // **and the preview snapped back to its resting pan and zoom**, because those two fields came along in the copy.
    // One capture, both symptoms.
    //
    // Keying the `pointerInput` on the workspace instead would fix the staleness and break the gesture: the block
    // restarts on its own first frame, so the drag would be cancelled the instant it moved anything and would never
    // reach the commit. Which is the same reasoning `StudioStepperButton` spells out for `enabled`, and what
    // `StudioCanvas` already does for the pinch.
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentMenuChange by rememberUpdatedState(onMenuChange)

    Box(
        modifier = Modifier
            .size(
                width = if (vertical) TileSide else HandleSlotThickness,
                height = if (vertical) HandleSlotThickness else TileSide,
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var overSlop = Offset.Zero
                    val slop: (PointerInputChange, Offset) -> Unit = { change, over ->
                        change.consume()
                        overSlop = over
                    }

                    // Stage 1 — the race. **Wrapped rather than returned bare**, because a bare null is ambiguous
                    // here: `withTimeoutOrNull` returns null on the timeout *and* `awaitTouchSlopOrCancellation`
                    // returns null when the finger lifts before slop, and those are the two opposite outcomes.
                    var raced = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        SlopOutcome(awaitTouchSlopOrCancellation(down.id, slop))
                    }

                    var openedMenu = false
                    if (raced == null) {
                        // The finger stayed put: the stack's menu, opened under a finger that is still down.
                        openedMenu = true
                        currentMenuChange(RailMenu.STACK)
                        // Stage 2 — and no timeout this time. A drag from here is a reposition *of the thing the
                        // menu is about*, so it hands the menu off rather than being a second gesture.
                        raced = SlopOutcome(awaitTouchSlopOrCancellation(down.id, slop))
                    }

                    // Stage 3 — the finger lifted without ever dragging. A long press keeps its menu; a plain tap
                    // did nothing and leaves nothing.
                    val start = raced.change ?: return@awaitEachGesture

                    // The menu is put away for the length of the drag, so the panel is not carried across the canvas
                    // by a rail sliding out from under it.
                    if (openedMenu) currentMenuChange(null)

                    // The slop overshoot is the drag's first delta — dropping it makes the rail lag the finger by
                    // the slop distance for the whole gesture, which reads as the handle being loose.
                    if (overSlop != Offset.Zero) currentDrag(overSlop.x, overSlop.y)

                    drag(start.id) { change ->
                        // **Read the delta, *then* consume — the order is the whole of it.**
                        // `positionChange()` answers `Offset.Zero` for a change that is already consumed, so
                        // consuming first makes every frame after the slop report no movement at all. The symptom is
                        // not an error: the rail jumps by the slop overshoot and then sits still under a finger that
                        // is plainly still dragging, which reads as the gesture having died. `detectDragGestures`,
                        // computes the delta before it hands the change over — so the same two lines in the other
                        // order are correct only by accident of who calls `consume`.
                        val delta = change.positionChange()
                        change.consume()
                        currentDrag(delta.x, delta.y)
                    }
                    currentDragEnd()

                    // **Re-opened, not un-hidden**, so it is placed against where the rail is now rather than where
                    // it was when the press landed. A cancelled drag comes back here too: the rail has moved either
                    // way, so the finger has been let go of and the menu is what the user was in the middle of.
                    if (openedMenu) currentMenuChange(RailMenu.STACK)
                }
            }
            .semantics { contentDescription = "Move the layer rail, or hold for its options" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (vertical) HandleWidth else HandleThickness,
                    height = if (vertical) HandleThickness else HandleWidth,
                )
                .clip(RoundedCornerShape(HandleThickness / 2))
                .background(StudioContentColor.copy(alpha = HandleAlpha)),
        )
    }
}

/**
 * The result of racing touch slop against the long-press timeout — a wrapper whose only job is to keep two nulls
 * apart.
 *
 * `withTimeoutOrNull` answers null for "the timeout won", and `awaitTouchSlopOrCancellation` answers null for "the
 * finger lifted before it moved". Those are opposite outcomes — one opens a menu, one does nothing at all — so the
 * inner one is boxed and the outer one left bare.
 */
private class SlopOutcome(val change: PointerInputChange?)

/**
 * The whole icon, drawn as itself — the tile that selects the composite.
 *
 * **It draws the real stack with nothing hidden**, where a layer tile hides every layer but one. So it is a small
 * copy of the canvas, and that is correct rather than redundant: it is the thumbnail of the thing being edited, and
 * a thumbnail that did not match what the tools act on would be the one thing this rail must not do.
 *
 * **No long-press menu.** Every row of that menu — move up, move down, hide, delete — is about a layer's place in a
 * stack, and the composite has none: it is not in the stack, it is what the stack makes. A menu of four disabled
 * rows says less than no menu at all, which is the one place this file's own "disable, never omit" rule does not
 * apply, because these are not moves that could ever become legal.
 *
 * It shares [LayerTile]'s selection treatment exactly — the same gap, the same ring, the same checkerboard — because
 * it is the same question being answered: which tile is lit.
 */
@Composable
private fun CompositeTile(
    state: IconStudioState,
    customImage: (path: String) -> android.graphics.drawable.Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
) {
    val selection by animateFloatAsState(
        targetValue = if (state.editingComposite) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "compositeTileSelection",
    )
    // Clamped for [LayerTile]'s reason: the spatial spec is a spring, and `Modifier.padding` throws on a negative.
    val inset = TileInset * selection.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(TileSide)
            // Above the clip, for [TileShape]'s reason — the ring and the clip are the same rounded rect, so inside
            // it the ring loses its corners.
            .border(SelectionBorder, StudioContentColor.copy(alpha = selection.coerceIn(0f, 1f)), TileShape)
            .clip(TileShape)
            .clickable(onClick = onClick)
            .padding(inset)
            .clip(RoundedCornerShape(TileCorner - inset))
            .drawBehind { drawCheckerboard(CheckerTileSquare.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        state.parsed?.let { parsed ->
            IconPreview(
                icon = parsed,
                layerSet = state.editing,
                modifier = Modifier.fillMaxSize(),
                customImage = customImage,
                packImage = packImage,
            )
        }
    }
}

/**
 * One layer, drawn as itself.
 *
 * **The thumbnail is the real render path with every other layer hidden**, rather than a second way to draw a
 * layer. `IconLayerSet`'s own `init` forbids a set without a foreground and a background, so a one-layer set is
 * unrepresentable — but visibility is per layer, so hiding the rest says the same thing and says it through
 * `IconLayerStack`. The tile therefore shows the layer's transform, shape, effects and source exactly as the icon
 * will, at no cost in code that could drift.
 *
 * **A checkerboard behind it, because most layers are mostly transparent.** A dark glyph on nothing is an empty
 * tile on dark glass, and a white one is invisible on a light plate — the canvas already solves this for the icon
 * and the same two grays solve it here.
 *
 * **Selecting shrinks the preview and spawns a ring in the space it gives up.** The tile is one size whatever its
 * state, so the rail never reflows and no neighbour shifts — what changes is how much of the tile the artwork gets.
 *
 * The ring needs that gap, and the gap is the whole point: the first cut drew the ring *over* the preview at the
 * same bounds, which fails on exactly the artwork a layer editor is full of — an all-white layer swallows a white
 * ring, so the selected tile looked identical to the rest. Inset, the ring has ground of its own that no layer can
 * paint on.
 *
 * Two other arrangements were tried and dropped: an outline on *every* tile made the rail a column of boxes and was
 * redundant with the checkerboard, which already gives an unselected tile a square to read as a slot; and a
 * *constant* inset left every resting tile with a margin of nothing around a preview that could have filled it.
 *
 * A hidden layer is dimmed rather than removed: it is still yours to select, and the eye is how it comes back.
 *
 * **Three animations, one per thing that changes**, each pinned to the theme's motion scheme rather than to a
 * duration of its own:
 * - **Placement** — `animatePlacement`, the launcher's own modifier, already carrying free-grid push and MovingGap
 *   migration. A tile whose index changes glides, which is the whole of both choreographies: an insert pushes the
 *   tiles beneath it along, a removal lets them close up, and the tiles *above* never move because their indices do
 *   not change.
 * - **Entrance** — [entering] tiles slide out of the tile *before* them and fade in, which is the new layer emerging
 *   from the selected one it was inserted beneath. Only genuinely new tiles get it: without that test the rail
 *   would re-assemble itself every time the studio recomposed for an unrelated reason. **It follows the rail's
 *   axis** — down the column, across the row — because "out of the tile before it" is a different direction in each,
 *   and a tile dropping vertically into a horizontal rail comes from nowhere in particular.
 * - **Selection** — the gap and the ring, above.
 *
 * The one thing not animated is a tile **leaving**: it is gone from the stack the moment delete is pressed, so
 * there is nothing left to fade. Keeping it would mean holding a deleted layer in state until an animation said so,
 * which is a two-phase delete for a few frames of polish. What the eye gets instead is the tiles below closing up,
 * which is the motion that says a tile was removed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerTile(
    state: IconStudioState,
    index: Int,
    entering: Boolean,
    vertical: Boolean,
    customImage: (path: String) -> android.graphics.drawable.Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val spec = state.editing.layers[index]
    val selected = state.selected == index
    // **One driver for both halves of the selection, so they cannot come apart.** The gap opening and the ring
    // fading in are the same event; animated separately — a `Dp` on the spatial spec, a color on the effects one —
    // they would run at different rates, and the frames where the ring is already dark over a gap that has barely
    // opened are exactly the ones where it clips the artwork.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "layerTileSelection",
    )

    // **Clamped, because the spatial spec is a *spring* and a spring overshoots by design.** Deselecting settles on
    // zero from below, so an unclamped `TileInset * selection` goes negative for a few frames and `Modifier.padding`
    // throws `Padding must be non-negative` — a crash on a plain tap, and only on the tile being left rather than
    // the one being chosen. Expressive motion is kept everywhere it is free; this is the one place where the tail of
    // it is an illegal value rather than a bounce.
    val progress = selection.coerceIn(0f, 1f)
    val inset = TileInset * progress

    // Runs once per tile, on the composition that mounts it — `entering` is read at mount and never again, so a
    // tile does not re-animate when the set of known keys catches up a frame later.
    val entrance = remember { Animatable(if (entering) 0f else 1f) }
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (!entering) return@LaunchedEffect
        // **Scrolled into view *before* it animates, not after.** A new layer lands directly beneath the selected
        // one, which is past the bottom of the rail whenever the selected tile was the last one visible — and a
        // tile that arrives off screen has an entrance nobody sees. `BringIntoViewRequester` asks whatever
        // scrollable ancestor there is, so this needs nothing from the rail that owns the scroll.
        reveal.bringIntoView()
        entrance.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
    }

    // **The selected tile is kept in view wherever it goes**, which matters most for the one control that moves it
    // without the finger following: reorder is pressed in the quick menu, so on a stack long enough to scroll the
    // layer being moved would walk out of the viewport while the presses went on landing. Keyed on [index] as well
    // as [selected] because the position is what changes — the selection stays put across a move (`moveSelected`
    // carries it to the destination), so keying on selection alone would never fire.
    //
    // Skipped while [entering], where the entrance effect above already scrolls, and deliberately *before* it
    // animates rather than at the same time.
    LaunchedEffect(selected, index) {
        if (selected && !entering) reveal.bringIntoView()
    }

    // Every other layer hidden, so the stack draws this one alone. Keyed on the whole set, so an edit anywhere
    // re-derives it exactly as the canvas does.
    //
    // **And the whole-icon effects come off with them**, which they did not before the composite had any. A tile's
    // job is *which layer is this?*, and a grain or a glow belonging to the icon rather than to the layer obscures
    // exactly that at 48dp — it would also drag every tile onto the baked path the moment one was added, for
    // something none of them are showing. The composite tile is where those are seen.
    //
    // **The whole-icon *shape* comes off for the first of those two reasons**, and it is the sharper case: a stack
    // mask trims every tile identically, so a custom layer sitting near a corner is cropped to nothing and its tile
    // goes blank — a layer the user cannot see is one they cannot find, and the tile is the only way to reach it.
    // The layer's *own* shape stays, because that genuinely is what this layer looks like.
    //
    // **The whole-icon *angles* likewise**: a turn or a lean applies to every tile at once, so at 44dp it costs the
    // artwork the room it needs to be recognized — and clips its corners — while saying nothing about which layer
    // this is. A layer's own rotation and tilt stay, for the reason its own shape does.
    val soloed = remember(state.editing, index) {
        state.editing.copy(
            layers = state.editing.layers.mapIndexed { i, layer -> layer.copy(visible = i == index) },
            rotation = 0f,
            tiltX = 0f,
            tiltY = 0f,
            shape = null,
            effects = emptyList(),
        )
    }

    Box(
        modifier = Modifier
            .bringIntoViewRequester(reveal)
            .animatePlacement()
            // Slides out of the tile *before* it rather than appearing from nowhere: a new layer is inserted directly
            // beneath the selected one, so that is where it comes from — down the column, or across the row. Along
            // the rail's own run either way, which is the axis the tiles are laid out on; the other one would have a
            // tile arriving from outside the rail entirely.
            .graphicsLayer {
                alpha = entrance.value
                val travel = 1f - entrance.value
                if (vertical) {
                    translationY = -size.height * travel
                } else {
                    translationX = -size.width * travel
                }
            }
            .size(TileSide)
            // **The ring goes above the clip, not under it** — see [TileShape]. Both are the same rounded rect, and
            // a rounded clip is a hardware outline clip with no antialiasing, so from inside it the ring's own
            // antialiased outer arc loses whole pixels: straight sides full width, corners thin and stepped. It
            // still draws over everything below it in the chain, which is what the gap beneath is for.
            .border(SelectionBorder, StudioContentColor.copy(alpha = progress), TileShape)
            .clip(TileShape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            // **Selecting shrinks the preview to make room for the ring; it does not grow the tile.** Every tile is
            // [TileSide] whatever its state, so the rail never reflows and no neighbour moves when the selection
            // does — the ring is drawn in space the preview gives up, not in space the tile takes.
            //
            // **And the gap is what makes selection survive a white layer.** The first cut drew the ring *over* the
            // preview at the same bounds, so an all-white icon swallowed a white ring and the selected tile looked
            // exactly like every other one. Inset, the ring has ground of its own that no layer can paint on.
            .padding(inset)
            // Follows the gap, so the preview's corner stays concentric with the tile's rather than snapping
            // between two radii as the inset opens.
            .clip(RoundedCornerShape(TileCorner - inset))
            .drawBehind { drawCheckerboard(CheckerTileSquare.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        state.parsed?.let { parsed ->
            IconPreview(
                icon = parsed,
                layerSet = soloed,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (spec.visible) Modifier else Modifier.alpha(HiddenLayerAlpha)),
                customImage = customImage,
                packImage = packImage,
            )
        }

        if (!spec.visible) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = "Hidden",
                tint = StudioContentColor,
                modifier = Modifier.size(HiddenBadge),
            )
        }
    }
}

/** The transparency ground a tile is read against — the canvas's own two grays, at tile scale. */
private fun DrawScope.drawCheckerboard(square: Float) {
    if (square <= 0f) return
    drawRect(CheckerLight)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else square
        while (x < size.width) {
            drawRect(
                color = CheckerDark,
                topLeft = Offset(x, y),
                size = Size(
                    minOf(square, size.width - x),
                    minOf(square, size.height - y),
                ),
            )
            x += square * 2
        }
        y += square
        row++
    }
}

/**
 * The tile, and the gap the selection ring needs.
 *
 * [TileInset] is the gap a *selected* tile opens — at rest the preview fills the tile — and it is deliberately
 * wider than [SelectionBorder], because a border draws *inward* from the bounds. Drop the inset below the border
 * width and selecting a layer would start cropping its own preview.
 */
private val TileSide = 48.dp
private val TileInset = 4.dp
private val TileCorner = 12.dp

/**
 * The tile's own rounded rect, stated once because a tile asks for it **twice** — as the clip that rounds its
 * checkerboard and as the shape of the selection ring drawn over it — and the two must be the same rect. It is also
 * the reminder of which way round they go in the chain: the ring **above** the clip, since a rounded clip is a
 * hardware outline clip and is not antialiased, so a clip boundary running along the ring's outer arc strips it and
 * the corners come back thin and stepped while the straight sides stay full width. Same fix as the effect section's
 * swatches.
 */
private val TileShape = RoundedCornerShape(TileCorner)

/**
 * The ring on the selected tile — and **only** on it.
 *
 * An outline on every tile was tried and dropped: it made the rail a column of boxes, and it was redundant with the
 * checkerboard, which already gives an unselected tile a defined square to read as a slot. What the gap buys is kept
 * either way, because the inset does not depend on the ring being drawn.
 */
private val SelectionBorder = 2.dp
private val RailCorner = 18.dp
private val RailPadding = 6.dp
private val RailGap = 6.dp
private val HiddenBadge = 18.dp
private val CheckerTileSquare = 6.dp

/** The rules inside the rail. Its own value so the two calls cannot drift, being the same line drawn twice. */
private val RailDividerColor = StudioContentColor.copy(alpha = 0.15f)

/**
 * M3's own divider thickness, restated so [RailScrollExtent] can subtract it.
 *
 * `DividerDefaults.Thickness` is what the dividers actually draw at; naming it here is the one place this file could
 * drift from Material, and it is one dp of a 320dp cap — stated rather than dropped, because the whole point of that
 * derivation is that the pieces add up to the cap exactly.
 */
private val DividerThickness = 1.dp

/**
 * The grab bar: what is **drawn**, and the slot that is **pressed**.
 *
 * The two are deliberately different sizes — the mark is a discreet 20×4, the slot a full tile across and 20dp
 * through — which is `StudioIconButton`'s own split between its 20dp glyph and its 40dp face. A handle large enough
 * to grab reliably would be a heavy bar at the head of a rail whose whole subject is small pictures.
 *
 * Both turn with the rail: across its run, a grab bar reads as a divider rather than as something to take hold of.
 */
private val HandleWidth = 20.dp
private val HandleThickness = 4.dp
private val HandleSlotThickness = 20.dp

/** Present without competing with the tiles: a handle is chrome, and the layers are the content. */
private const val HandleAlpha = 0.45f

/** Enough that a hidden layer reads as switched off, not enough that it stops being identifiable. */
private const val HiddenLayerAlpha = 0.25f

/**
 * Capped so a rail at rest stays clear of the tool panel below it, which grows to 320dp. Past this it scrolls, which
 * is the right answer for a stack deep enough to reach it.
 *
 * **A cap on the resting arrangement, not a guarantee**, now that the rail can be dragged: a user who moves it down
 * the canvas can put it over the panel, and that is their call rather than something to prevent. What the cap still
 * buys is that the studio never *opens* with the two overlapping.
 */
private val RailMaxHeight = 320.dp

/**
 * How far the **expanded** scroll band of layer tiles may run — [RailMaxHeight] less exactly what everything pinned
 * around it takes: the padding at both ends, the handle, the composite tile, the two rules, `+`, and the five gaps
 * between those six children.
 *
 * **Derived rather than a second number**, which is what keeps the cap meaning what it says: the rail is capped at
 * [RailMaxHeight] whatever it holds, and stating the band's own extent separately would be one edit away from a rail
 * that quietly grew past it. It is the arithmetic `weight(1f, fill = false)` performs at measure time, given up
 * because `weight` belongs to `ColumnScope` or `RowScope`, and a rail that can be either needs one answer both
 * arms can use.
 *
 * One value for both axes, deliberately: the cap is "how much of the screen may the stack take", and that is the same
 * question lying down as standing up.
 *
 * The **collapsed** extent is not here — it is one [TileSide], stated at the call site, because it is not a leftover
 * but the whole point: a collapsed rail is a list one item tall.
 */
private val RailScrollExtent = RailMaxHeight -
    RailPadding * 2 -
    HandleSlotThickness -
    TileSide -
    DividerThickness * 2 -
    RailGap * 5 -
    TileSide
