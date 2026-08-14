package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.model.icon.IconLayerSpec


/**
 * The stack, top layer first — **drawn in the order it is drawn on screen**, which is the reverse of the list's
 * index order. A layer editor that showed the bottom layer at the top would be asking the user to hold an inversion
 * in their head for no reason.
 *
 * That reversal reaches further than this file: `IconStudioViewModel.removeSelected` moves the selection **down** an
 * index to keep the highlight on the same row, which only makes sense while the rows are drawn this way round. Reverse
 * the render order and that becomes an off-by-one.
 *
 * **The list only.** Its actions are [LayerStackActions], which the panel host pins below the scroll rather than
 * emitting here — adding a layer must not push "add a layer" off the bottom.
 */
@Composable
internal fun LayerStackRows(
    state: IconStudioState,
    onSelectLayer: (Int) -> Unit,
    onToggleVisible: () -> Unit,
) {
    // Which layers were already on screen last time this composed, so a row that is genuinely **new** can slide in
    // while the ones that merely moved glide. Held as a plain var behind a `remember` and advanced in a `SideEffect`:
    // reading it during composition is what decides the entrance, and it must not change under that composition.
    var known by remember { mutableStateOf(emptySet<Long>()) }
    val current = state.editing.layers.indices.map(state::layerKey).toSet()
    SideEffect { known = current }

    // **The height change is animated by the panel, not here.** This list used to animate its own size, which was the
    // feedback add and remove had none of — but the panel changes height for other reasons too (switching section,
    // most of all), so the animation belongs to the one node every one of those reasons passes through. Two nested size
    // animations would be worse than one: the outer would chase a target still moving under it. See [StudioToolPanel].
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.editing.layers.indices.reversed().forEach { index ->
            val layerKey = state.layerKey(index)
            // **`key` is what makes the whole choreography work**, and what it needs is identity the model cannot
            // give — see `IconStudioState.layerKey`. With it, an insert *moves* the rows beneath rather than
            // rebuilding them in their new places, so `animatePlacement` has something to glide.
            key(layerKey) {
                LayerRow(
                    spec = state.editing.layers[index],
                    selected = index == state.selected,
                    entering = layerKey !in known,
                    onClick = { onSelectLayer(index) },
                    onToggleVisible = onToggleVisible,
                )
            }
        }
    }
}

/**
 * Reorder, add and delete — **pinned to the bottom of the panel, outside the scroll.**
 *
 * Two reasons, and the second is why it is not merely a divider's worth of separation. It used to be the last child of
 * the list's own `Column`, four glyphs one gap below the final layer, which read as a fifth row of the list rather than
 * as a toolbar over it — and a layer row *is* a control (tap to select, eye to hide), so buttons among them invite
 * being read as another layer, when in fact these four act on whichever layer is **selected** rather than on anything
 * at their own position. But the load-bearing reason is that the panel scrolls: inside it, **adding a layer pushed
 * "add a layer" further down**, and past the panel's cap it left the stack's own controls below the fold — worst
 * exactly when the stack is deep enough to need them.
 *
 * Which is why this is the host's to place ([StudioToolPanel]) rather than something this file emits: staying put is a
 * property of where a row sits in the *panel*, and a section body cannot see the panel.
 */
@Composable
internal fun LayerStackActions(
    state: IconStudioState,
    onMove: (up: Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    // One child of the panel's `Column`, not two, so the divider hugs the buttons it belongs to rather than being
    // spaced off them by the gap the host puts between its own groups.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = StudioContentColor.copy(alpha = 0.15f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Disabled rather than hidden, because *which* move is illegal is the information: a grayed arrow says
            // "the foreground cannot go below its background" before the move is attempted, where a vanished button
            // says nothing and a refused drag says nothing twice.
            StudioIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Move up",
                enabled = state.canMoveUp,
                onClick = { onMove(true) },
            )
            StudioIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Move down",
                enabled = state.canMoveDown,
                onClick = { onMove(false) },
            )
            StudioIconButton(Icons.Default.Add, "Add layer", onClick = onAdd)
            StudioIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Remove layer",
                enabled = state.canRemoveSelected,
                onClick = onRemove,
            )
        }
    }
}

/**
 * One row of the stack: what the layer is, whether it is showing, and whether it is the one being edited.
 *
 * **Three animations, one per thing that changes.** They are separate because they are separate facts, and each is
 * pinned to the theme's motion scheme rather than to a duration of its own:
 * - **Placement** — `animatePlacement`, the launcher's own modifier, already carrying free-grid push and MovingGap
 *   migration. A row whose index changes glides to its new position, which is the whole of both choreographies: an
 *   insert pushes the rows beneath it down, a removal lets them rise, and the rows *above* never move because their
 *   indices do not change. It seeds from the first real position, so a row that has just appeared does not fly in from
 *   the origin.
 * - **Entrance** — [entering] rows slide down out of the row above and fade in, which is the new layer emerging from
 *   the selected one it was inserted beneath. Only genuinely new rows get it: without that test the list would
 *   re-assemble itself every time the Layers panel was opened.
 * - **Selection** — the wash fades rather than switching, because add and remove both *move* the selection and a
 *   highlight that appears somewhere new in one frame is easy to miss. An effects spec, since only a color changes.
 *
 * The one thing not animated is a row **leaving**: it is gone from the stack the moment remove is pressed, so there is
 * nothing left to fade. Keeping it would mean holding a deleted layer in state until an animation said so, which is a
 * two-phase delete for a few frames of polish. What the eye gets instead is the rows below rising into the gap, which
 * is the motion that says a row was removed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerRow(
    spec: IconLayerSpec,
    selected: Boolean,
    entering: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
) {
    val wash by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "layerRowSelection",
    )

    // Runs once per row, on the composition that mounts it — `entering` is read at mount and never again, so a row
    // does not re-animate when the set of known keys catches up a frame later.
    val entrance = remember { Animatable(if (entering) 0f else 1f) }
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (!entering) return@LaunchedEffect
        // **Scrolled into view *before* it animates, not after.** A new layer lands directly beneath the selected one,
        // which is off the bottom of the panel whenever the selected row was the last one visible — and a row that
        // slides in while off screen has an entrance nobody sees. `BringIntoViewRequester` asks whatever scrollable
        // ancestor there is, so this needs nothing from the panel that owns the scroll.
        reveal.bringIntoView()
        entrance.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(reveal)
            .animatePlacement()
            // Slides out of the row above rather than from nowhere: the new layer is inserted directly beneath the
            // selected one, so that is where it comes from.
            .graphicsLayer {
                alpha = entrance.value
                translationY = -size.height * (1f - entrance.value)
            }
            .clip(RoundedCornerShape(10.dp))
            .background(wash)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = spec.role.label,
            color = StudioContentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = spec.source.label,
            color = StudioContentColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            // The eye acts on the *selected* layer, so tapping it on an unselected row would silently hide a
            // different one. Selecting first is what makes one command serve every row.
            StudioIconButton(
                icon = if (spec.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (spec.visible) "Hide layer" else "Show layer",
                enabled = selected,
                onClick = onToggleVisible,
            )
        }
    }
}
