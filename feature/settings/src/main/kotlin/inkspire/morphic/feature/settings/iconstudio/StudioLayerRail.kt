package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.icon.compose.IconPreview

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
 * **On the end edge, not "the right"** — RTL-aware, for `SideZoneEdge`'s own reason. Vertically centred and capped,
 * so it stays clear of the history and save buttons above and the tool panel below, and scrolls rather than growing
 * into either. The **stack** is what scrolls: `+` is pinned below it, because a control that adds a layer must not
 * be pushed out of reach by the layer it just added.
 */
@Composable
internal fun StudioLayerRail(
    state: IconStudioState,
    hazeState: HazeState,
    customImage: (path: String) -> android.graphics.drawable.Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> android.graphics.drawable.Drawable?,
    onSelect: (StudioTarget) -> Unit,
    onMove: (up: Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // **Whether the quick menu is open, and nothing more.** It held the tile's index first, which read as though
    // the menu belonged to a *tile* — it never did: every row acts on the **selected** layer, and long-pressing
    // selects before opening. Keeping an index became actively wrong once reordering stopped closing the menu,
    // since a move changes the index while the menu goes on pointing at the same layer.
    var menuOpen by remember { mutableStateOf(false) }

    // Which layers were already on screen last time this composed, so a tile that is genuinely **new** can arrive
    // while the ones that merely moved glide. Held as a plain var behind a `remember` and advanced in a
    // `SideEffect`: reading it during composition is what decides the entrance, and it must not change under that
    // composition. Ported from the layer list this rail replaced, which had the same problem.
    var known by remember { mutableStateOf(emptySet<Long>()) }
    val current = state.editing.layers.indices.map(state::layerKey).toSet()
    SideEffect { known = current }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The menu opens *toward the canvas*, so it never hangs off the screen edge the rail is pinned to.
        if (menuOpen) {
            // **Reorder and hide leave it open; delete and close do not.** The first three are things you do
            // *repeatedly* and judge by looking — moving a layer two places is two presses, and hiding one is a
            // question about the icon you have to see answered — so closing after each would make the menu a thing
            // to keep reopening. It also makes the disabled rows work for you: move up greys out at the moment the
            // layer reaches the top, which is the answer to "how far can this go?" given while you are asking.
            //
            // Delete closes because the layer it acted on is gone, and leaving the menu up would silently re-point
            // it at whatever the selection fell to.
            LayerQuickMenu(
                state = state,
                hazeState = hazeState,
                onMove = onMove,
                onToggleVisible = onToggleVisible,
                onRemove = {
                    onRemove()
                    menuOpen = false
                },
                onDismiss = { menuOpen = false },
            )
        }

        // **Two bands: the stack scrolls, `+` does not.** `weight(1f, fill = false)` is what makes that free — the
        // pinned button is measured first and the scroll takes what is left of the cap, so pinning takes space
        // *from* the list rather than adding it to the rail. `fill = false` is the half that is easy to miss: with
        // it filling, three layers would stretch the scroll to the full cap and the rail would be its tallest
        // whatever it held. Same three-band shape `StudioToolPanel` uses, and the same reason.
        Column(
            modifier = Modifier
                .studioSurface(hazeState, shape = RoundedCornerShape(RailCorner))
                .heightIn(max = RailMaxHeight)
                .padding(RailPadding),
            verticalArrangement = Arrangement.spacedBy(RailGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    // **Clipped to the rail's inner contour, because a scroller clips square** — a tile passing the
                    // top fills the very corner the rail's radius cuts away, and the checkerboard is opaque right
                    // to its edge. The radius is the rail's less its padding, which keeps the two concentric.
                    //
                    // Outside the size animation, so the clip is at the **animated** height rather than the target:
                    // a modifier earlier in the chain wraps the ones after it, so this sees what the animation
                    // reports.
                    .clip(RoundedCornerShape(topStart = RailCorner - RailPadding, topEnd = RailCorner - RailPadding))
                    // **This is the height that animates, and it is the only one that does.** It was on the rail's
                    // outer `Column` first, which animated the *glass* while the tiles inside sat at their final
                    // layout — the frame grew smoothly around a list that had already jumped, which is the worst of
                    // both. What actually changes when a layer is added is the height of this scroll viewport, so
                    // this is where it belongs, and the outer column follows for free because it wraps this.
                    //
                    // One animation rather than two, for the reason `StudioToolPanel` states: nested size
                    // animations mean the outer chases a target that is itself still moving, which reads as lag.
                    .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RailGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                    // **The whole icon, at the head of the stack** — above the top layer, because that is where it
                // sits: it is what everything beneath composites into. Separated by a rule so it does not read as
                // one more slot, and given no long-press menu, since none of those verbs mean anything for it.
                CompositeTile(
                    state = state,
                    customImage = customImage,
                    packImage = packImage,
                    onClick = {
                        onSelect(StudioTarget.Composite)
                        menuOpen = false
                    },
                )
                HorizontalDivider(
                    color = StudioContentColor.copy(alpha = 0.15f),
                    modifier = Modifier.width(TileSide),
                )

                state.editing.layers.indices.reversed().forEach { index ->
                    // `key` gives the tiles identity the model cannot — see `IconStudioState.layerKey` — so an
                    // insert *moves* the tiles beneath rather than rebuilding them, and `animatePlacement` has
                    // something to glide.
                    key(state.layerKey(index)) {
                        LayerTile(
                            state = state,
                            index = index,
                            entering = state.layerKey(index) !in known,
                            customImage = customImage,
                            packImage = packImage,
                            onClick = {
                                onSelect(StudioTarget.Layer(index))
                                menuOpen = false
                            },
                            onLongClick = {
                                onSelect(StudioTarget.Layer(index))
                                menuOpen = true
                            },
                        )
                    }
                }
            }

            // **Separated, because a tile is a layer and this is not.** Flush among them it reads as another slot
            // in the stack — the same misreading the old layer list's buttons had, and the same fix.
            HorizontalDivider(
                color = StudioContentColor.copy(alpha = 0.15f),
                modifier = Modifier.width(TileSide),
            )

            StudioIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add layer",
                onClick = {
                    onAdd()
                    menuOpen = false
                },
                modifier = Modifier.size(TileSide),
            )
        }
    }
}

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
            .clip(RoundedCornerShape(TileCorner))
            .clickable(onClick = onClick)
            .border(SelectionBorder, StudioContentColor.copy(alpha = selection.coerceIn(0f, 1f)), RoundedCornerShape(TileCorner))
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
 * and the same two greys solve it here.
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
 * **Three animations, one per thing that changes** — the shape the layer list this replaced already had, and each
 * pinned to the theme's motion scheme rather than to a duration of its own:
 * - **Placement** — `animatePlacement`, the launcher's own modifier, already carrying free-grid push and MovingGap
 *   migration. A tile whose index changes glides, which is the whole of both choreographies: an insert pushes the
 *   tiles beneath it along, a removal lets them close up, and the tiles *above* never move because their indices do
 *   not change.
 * - **Entrance** — [entering] tiles slide down out of the tile above and fade in, which is the new layer emerging
 *   from the selected one it was inserted beneath. Only genuinely new tiles get it: without that test the rail
 *   would re-assemble itself every time the studio recomposed for an unrelated reason.
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
    customImage: (path: String) -> android.graphics.drawable.Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val spec = state.editing.layers[index]
    val selected = state.selected == index
    // **One driver for both halves of the selection, so they cannot come apart.** The gap opening and the ring
    // fading in are the same event; animated separately — a `Dp` on the spatial spec, a colour on the effects one —
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
    val soloed = remember(state.editing, index) {
        state.editing.copy(
            layers = state.editing.layers.mapIndexed { i, layer -> layer.copy(visible = i == index) },
            effects = emptyList(),
        )
    }

    Box(
        modifier = Modifier
            .bringIntoViewRequester(reveal)
            .animatePlacement()
            // Slides down out of the tile above rather than appearing from nowhere: a new layer is inserted
            // directly beneath the selected one, so that is where it comes from.
            .graphicsLayer {
                alpha = entrance.value
                translationY = -size.height * (1f - entrance.value)
            }
            .size(TileSide)
            .clip(RoundedCornerShape(TileCorner))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .border(SelectionBorder, StudioContentColor.copy(alpha = progress), RoundedCornerShape(TileCorner))
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

/**
 * The quick menu: what can be done to the selected layer, and what cannot.
 *
 * **Every row that would do nothing is disabled rather than absent**, which is the reason the reorder controls were
 * buttons and never a drag: *a disabled row says which move is illegal before it is attempted*, where a refused
 * gesture does nothing and cannot explain itself. The answers come from the model (`editing.moveUp(i) !== editing`),
 * so they cannot drift from the rule the set enforces.
 */
@Composable
private fun LayerQuickMenu(
    state: IconStudioState,
    hazeState: HazeState,
    onMove: (up: Boolean) -> Unit,
    onToggleVisible: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spec = state.selectedLayer ?: return

    Column(
        modifier = Modifier
            .width(MenuWidth)
            .studioSurface(hazeState, shape = RoundedCornerShape(RailCorner))
            .padding(vertical = 6.dp),
    ) {
        MenuRow(Icons.Default.KeyboardArrowUp, "Move up", state.canMoveUp) { onMove(true) }
        MenuRow(Icons.Default.KeyboardArrowDown, "Move down", state.canMoveDown) { onMove(false) }
        MenuRow(
            icon = if (spec.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            label = if (spec.visible) "Hide" else "Show",
            enabled = true,
            onClick = onToggleVisible,
        )
        MenuRow(Icons.Default.Delete, "Delete", state.canRemoveSelected, onClick = onRemove)
        MenuRow(Icons.Default.Close, "Close", enabled = true, onClick = onDismiss)
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

/** The transparency ground a tile is read against — the canvas's own two greys, at tile scale. */
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
private val MenuWidth = 148.dp
private val HiddenBadge = 18.dp
private val CheckerTileSquare = 6.dp

/** Enough that a hidden layer reads as switched off, not enough that it stops being identifiable. */
private const val HiddenLayerAlpha = 0.25f

/**
 * Capped so the rail stays clear of the chrome above and below it — the save row at the top end, the tool panel at
 * the bottom, which grows to 320dp. Past this it scrolls, which is the right answer for a stack deep enough to
 * reach it.
 */
private val RailMaxHeight = 320.dp
