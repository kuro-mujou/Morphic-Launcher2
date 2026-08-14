package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.designsystem.component.slider.Morphic2DPad
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.data.icons.InstalledIconPack
import kotlin.math.ceil
import kotlin.math.floor

/*
 * The bodies of the studio's sections — one per `StudioTool`, emitted into whatever `StudioToolPanel` lays out.
 *
 * A section emits controls and nothing else: no surface, no title, no scroll. Those belong to the host, which is the
 * only thing that knows a section is one of several — so a new section cannot arrive with its own idea of what a panel
 * looks like, and the host can rearrange them (a side rail in landscape) without touching one.
 *
 * There is no "this layer / whole icon" scope toggle, and that is a simplification the model earned rather than a
 * decision taken here. L1's editor mixed per-layer tools (transform, color, shadow) with whole-icon ones (icon shape,
 * background, theming, size, skin, pack) in one flat row, and its UI plan left the split as an open question. In L2
 * every one of those whole-icon tools has already gone somewhere else: the tile shape became a *per-layer* shape (there
 * is no stack-level mask), the background is the background layer's source, theming is `AppDefaultMonochrome` on the
 * foreground, sizing is `data:settings` and a different screen entirely, the skin is deferred, and an icon pack **is**
 * a per-layer source. So every section but Presets and More acts on one layer, and the question does not arise.
 */

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

/**
 * How the layer reads: opacity and blend, recoloring, and the gradient overlay.
 *
 * **One section rather than the two tabs this used to be**, because the model already groups them — `LayerEffect.Color`
 * and `LayerEffect.Gradient` are variants of one sealed list, and the deferred shadow will be a third. Splitting them
 * across bar entries would mean the rail grew every time that list did. See [StudioTool.EFFECTS].
 *
 * The gradient keeps a heading of its own inside the section: it is four controls that only make sense read together,
 * where the rest are independent.
 *
 * **No monochrome toggle here, deliberately.** Draining a layer of color is what Saturation does, and a toggle beside
 * it would be a lossy alias for it — switching one off has to invent a value to return to, discarding whatever the
 * user had. The word belongs to the *source* that swaps in the app's themed artwork, which is a different mechanism
 * with a different result; see [SourceControls].
 */
@Composable
internal fun EffectsControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    ColorControls(spec, onUpdate, onCommit)
    Text(
        text = "Gradient",
        color = StudioContentColor,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    GradientControls(spec, onUpdate, onCommit)
}

/**
 * Position, zoom and rotation.
 *
 * **Position is a 2D pad rather than two sliders**, because the value is a point: an icon is nudged diagonally as
 * often as along an axis, and two sliders make that two gestures and a mental transpose. `Morphic2DPad` was built
 * for exactly this and had no consumer until now.
 *
 * **Every control also has buttons, because a drag cannot be exact and these values have exact answers people want.**
 * Centered, 1.00×, 0°, 90° — a finger on a 140dp pad or a 250dp slider lands on 0.037 and 87°, and no amount of care
 * fixes that: the control's resolution is its length in pixels. The pad and the sliders stay the way you *find* a
 * value; the buttons are how you land on one.
 *
 * **The buttons snap to a grid rather than adding to the current value**, which is the detail that makes them worth
 * having. From 1.037 a plain `+0.05` gives 1.087 and every later press keeps the same debris; snapping gives 1.05, and
 * one press the other way gives exactly 1.00. So the round numbers are always at most one press away, and stepping
 * from a dragged value cleans it up instead of preserving it. See [snappedStep].
 *
 * Steps are chosen so the values people ask for by name are on the grid: 5° puts 45, 90 and 180 on it, and 0.05 puts
 * 1.00 and 1.50 on it.
 *
 * A **disabled** button is one whose target is where the value already is or outside the range — the same "ask, do not
 * guess" rule the layer reorder buttons use, and what makes the pad's center button say whether the layer is centered
 * without a second readout.
 *
 * Every control edits live and calls [onCommit] when the gesture *ends* — so the preview follows the finger, while
 * undo steps over the whole drag rather than through a hundred frames of it. A button press is discrete, so it commits
 * at once and is one undo step.
 */
@Composable
internal fun TransformControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Discrete edits record themselves, exactly as the source tiles do — there is no gesture here to punctuate.
    fun edit(transform: (IconLayerSpec) -> IconLayerSpec) {
        onUpdate(transform)
        onCommit()
    }

    LabeledControl("Position") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Morphic2DPad(
                x = spec.offsetX,
                y = spec.offsetY,
                onValueChange = { x, y -> onUpdate { it.copy(offsetX = x, offsetY = y) } },
                xRange = OffsetRange,
                yRange = OffsetRange,
                onValueChangeFinished = onCommit,
                modifier = Modifier.size(PadSide),
            )
            NudgePad(
                spec = spec,
                onStep = onUpdate,
                onStepsFinished = onCommit,
                onEdit = ::edit,
            )
        }
    }

    LabeledControl("Zoom  ${"%.2f".format(spec.zoom)}") {
        SteppedSlider(
            value = spec.zoom,
            valueRange = ZoomRange,
            step = ZoomStep,
            what = "zoom",
            onValueChange = { value -> onUpdate { it.copy(zoom = value) } },
            onValueChangeFinished = onCommit,
            onStepTo = { value -> onUpdate { it.copy(zoom = value) } },
        )
    }

    LabeledControl("Rotation  ${"%.0f".format(spec.rotation)}°") {
        SteppedSlider(
            value = spec.rotation,
            valueRange = RotationRange,
            step = RotationStep,
            what = "rotation",
            onValueChange = { value -> onUpdate { it.copy(rotation = value) } },
            onValueChangeFinished = onCommit,
            onStepTo = { value -> onUpdate { it.copy(rotation = value) } },
        )
    }
}

/**
 * The four directions and the way back to the middle, beside the pad rather than under it.
 *
 * **The center of a direction pad is where "back to the middle" belongs** — it is the one arrangement where the
 * control's shape states what it does, and it costs no row of its own. Disabled while the layer is already centered,
 * so the cluster doubles as the answer to "is it?", which the pad's knob only approximates.
 *
 * Arrows are disabled at the edge of the range for the same reason: a press that would do nothing says so first.
 */
@Composable
private fun NudgePad(
    spec: IconLayerSpec,
    onStep: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onStepsFinished: () -> Unit,
    onEdit: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
) {
    @Composable
    fun Arrow(icon: ImageVector, description: String, x: Int, y: Int) {
        StudioStepperButton(
            icon = icon,
            contentDescription = description,
            enabled = spec.nudged(x, y) != spec,
            onStep = { onStep { it.nudged(x, y) } },
            onStepsFinished = onStepsFinished,
            modifier = Modifier.size(NudgeSlot),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Arrow(Icons.Default.KeyboardArrowUp, "Nudge up", 0, -1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nudge left", -1, 0)
            StudioIconButton(
                icon = Icons.Default.CenterFocusStrong,
                contentDescription = "Center",
                enabled = spec.offsetX != 0f || spec.offsetY != 0f,
                onClick = { onEdit { it.copy(offsetX = 0f, offsetY = 0f) } },
                modifier = Modifier.size(NudgeSlot),
            )
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nudge right", 1, 0)
        }
        Arrow(Icons.Default.KeyboardArrowDown, "Nudge down", 0, 1)
    }
}

/**
 * A slider between a pair of buttons that step it onto the nearest grid value.
 *
 * @param what names the value for the buttons' content descriptions — the only thing here that is not the same for
 *   zoom and rotation, since both targets are computed from [step].
 * @param onStepTo the live edit a press makes, and it must **not** commit: a held button repeats, and
 *   [onValueChangeFinished] is what closes both a drag and a hold into one undo step. See `StudioStepperButton`.
 */
@Composable
private fun SteppedSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    what: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onStepTo: (Float) -> Unit,
) {
    val down = snappedStep(value, step, up = false).coerceIn(valueRange)
    val up = snappedStep(value, step, up = true).coerceIn(valueRange)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioStepperButton(
            icon = Icons.Default.Remove,
            contentDescription = "Decrease $what",
            enabled = down != value,
            onStep = { onStepTo(down) },
            onStepsFinished = onValueChangeFinished,
        )
        MorphicSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
        StudioStepperButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase $what",
            enabled = up != value,
            onStep = { onStepTo(up) },
            onStepsFinished = onValueChangeFinished,
        )
    }
}

/**
 * This layer moved one nudge along each axis [x] and [y] name (`-1`, `0`, `+1`), clamped to the pad's own range so a
 * button can never leave the layer somewhere the pad cannot show.
 *
 * **Snapped like the sliders, and here it is load-bearing rather than tidy.** Adding `0.01` repeatedly accumulates
 * float error, so a layer nudged twenty steps out and twenty back would land near zero rather than on it — leaving the
 * Center button lit, and lit *forever*, over an offset too small to see. Stepping onto the grid means the way back is
 * exactly the way out.
 */
private fun IconLayerSpec.nudged(x: Int, y: Int): IconLayerSpec = copy(
    offsetX = offsetX.nudged(x),
    offsetY = offsetY.nudged(y),
)

private fun Float.nudged(direction: Int): Float =
    if (direction == 0) this else snappedStep(this, NudgeStep, up = direction > 0).coerceIn(OffsetRange)

/**
 * The next multiple of [step] beyond [value], in the direction [up] names.
 *
 * **A grid position, not an addition**, which is what lets one press clean up a dragged value: 1.037 steps down to
 * 1.00 rather than to 0.987, and every value on the way is a number somebody could have meant. A value already on the
 * grid moves a full step, so repeated presses walk it evenly.
 *
 * The epsilon is what stops a value that *is* on the grid — arrived at by an earlier press — being read as a hair
 * below it and stepping only to itself, which would present as a button that works every other press.
 */
private fun snappedStep(value: Float, step: Float, up: Boolean): Float {
    val steps = value / step
    val target = if (up) floor(steps + SnapEpsilon) + 1f else ceil(steps - SnapEpsilon) - 1f
    return target * step
}

/** Where the pad's own edges are; the nudge buttons clamp to the same range so the two agree. */
private val OffsetRange = -0.5f..0.5f
private val ZoomRange = 0.2f..2f
private val RotationRange = 0f..360f

/** A hundredth of the frame — fine enough that a press is a correction rather than a move. */
private const val NudgeStep = 0.01f

/** Coarse enough to be worth pressing, fine enough that 1.00 and 1.50 are both on the grid. */
private const val ZoomStep = 0.05f

/** Five degrees, so 45, 90 and 180 are all reachable by stepping rather than only by luck. */
private const val RotationStep = 5f

/** Small against any step here, large against the float error of adding them up. */
private const val SnapEpsilon = 1e-4f

/** The pad, and one cell of the cluster beside it — equal to `StudioIconButton`'s own side. */
private val PadSide = 140.dp
private val NudgeSlot = 40.dp

/**
 * How the layer joins the stack (opacity, blend) and how it is recolored (tint, saturation, brightness, hue).
 *
 * **The recoloring controls write one `LayerEffect.Color`, never four**, via `IconLayerSpec.withColor` — which is
 * why an all-default effect is *removed* from the list rather than stored as a row of 1s. Four separate effects
 * would mean their order in the list silently changed the result.
 */
@Composable
private fun ColorControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    val color = spec.color ?: LayerEffect.Color()

    LabeledControl("Opacity  ${"%.2f".format(spec.opacity)}") {
        MorphicSlider(
            value = spec.opacity,
            onValueChange = { value -> onUpdate { it.copy(opacity = value) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Blend") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LayerBlend.entries.toList().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { blend ->
                        ChoiceChip(
                            label = blend.name.lowercase(),
                            selected = spec.blend == blend,
                            modifier = Modifier.fillMaxWidth(1f / row.size),
                        ) { onUpdate { it.copy(blend = blend) } }
                    }
                }
            }
        }
    }
    LabeledControl("Saturation  ${"%.2f".format(color.saturation)}") {
        MorphicSlider(
            value = color.saturation,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(saturation = value)) } },
            valueRange = 0f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Brightness  ${"%.2f".format(color.brightness)}") {
        MorphicSlider(
            value = color.brightness,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(brightness = value)) } },
            valueRange = 0.2f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Hue  ${"%.0f".format(color.hueDegrees)}°") {
        MorphicSlider(
            value = color.hueDegrees,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(hueDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Tint") {
        // Clearable because a tint is the one recoloring that cannot be undone by returning a slider to its
        // middle — without a way off, picking one would be a one-way door.
        ClearableColorField(
            argb = color.tintArgb,
            onChange = { argb -> onUpdate { it.withColor(color.copy(tintArgb = argb)) } },
        )
    }

    // **Only once a tint exists**, which is the difference between a mode and a dead control: with no tint set there
    // is nothing for either option to do, and the pair would be two buttons that change nothing.
    //
    // *Shaded* keeps the layer's own light and dark and pushes it toward the color; *Solid* keeps only the shape and
    // fills it flat. Solid is what makes app-shipped themed icons agree with each other — they arrive black, white or
    // colored depending on who built them, and only their alpha is meant to be meaningful — and it is the one mode a
    // multiply cannot reach, since black multiplied by anything is still black. See `TintMode`.
    if (color.tintArgb != null) {
        LabeledControl("Tint style") {
            MorphicSegmentedButtons(
                options = listOf("Shaded", "Solid"),
                selectedIndex = if (color.tintMode == TintMode.SOLID) 1 else 0,
                onSelect = { index ->
                    onUpdate { it.withColor(color.copy(tintMode = if (index == 1) TintMode.SOLID else TintMode.MULTIPLY)) }
                    onCommit()
                },
            )
        }
    }
}

/**
 * The gradient overlay's two stops, its direction and how strongly it is laid on.
 *
 * **Strength doubles as the on/off switch**: at zero the effect is identity and `withGradient` drops it from the
 * list entirely, so there is no separate toggle to disagree with the slider. That is the same shape the color
 * controls have — an effect at its defaults is simply not stored.
 */
@Composable
private fun GradientControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at zero strength when absent, so the sliders show a coherent gradient before it is turned on rather
    // than jumping to arbitrary values the moment strength leaves zero.
    val gradient = spec.gradient ?: LayerEffect.Gradient(strength = 0f)

    LabeledControl("Strength  ${"%.2f".format(gradient.strength)}") {
        MorphicSlider(
            value = gradient.strength,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(strength = value)) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Angle  ${"%.0f".format(gradient.angleDegrees)}°") {
        MorphicSlider(
            value = gradient.angleDegrees,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(angleDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("From") {
        ColorField(argb = gradient.startArgb) { argb ->
            onUpdate { it.withGradient(gradient.copy(startArgb = argb)) }
        }
    }
    LabeledControl("To") {
        ColorField(argb = gradient.endArgb) { argb ->
            onUpdate { it.withGradient(gradient.copy(endArgb = argb)) }
        }
    }
}

/**
 * Choosing a color: quick swatches, and a full picker one tap away.
 *
 * **One component for all four colors in this editor** — a solid fill, a tint, and a gradient's two stops. They
 * were three near-identical swatch rows before the picker existed, which is exactly the shape that drifts: L1 has
 * a whole file of near-copies for the same reason.
 *
 * The swatches stay rather than being replaced by the picker. They are how a color is chosen *quickly* and the
 * picker is how one is chosen *exactly*, and an editor that made every black require a drag across a saturation
 * panel would be slower for the common case in exchange for precision nobody wanted there.
 *
 * @param clearable whether "no color" is a choice. False for a fill or a gradient stop, which must be *some*
 *   color; true for a tint, which is an effect a user has to be able to get back off.
 */
@Composable
private fun ColorField(argb: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) =
    ColorFieldBody(argb, modifier, clearable = false) { picked -> picked?.let(onChange) }

/**
 * [ColorField] where *no color* is one of the choices.
 *
 * A separate function rather than a `clearable` flag on one, because the flag would not change the **type**: a
 * caller that must have a color would still be handed a nullable one and have to decide what to do with a null
 * that cannot happen. Two signatures make each call site say which it is, and the shared body is the same either
 * way.
 */
@Composable
private fun ClearableColorField(argb: Int?, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) =
    ColorFieldBody(argb, modifier, clearable = true, onChange = onChange)

@Composable
private fun ColorFieldBody(
    argb: Int?,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    onChange: (Int?) -> Unit,
) {
    val picker = LocalStudioColorPicker.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (clearable) Swatch(argb = null, selected = argb == null) { onChange(null) }
            FillSwatches.take(if (clearable) 6 else 7).forEach { swatch ->
                Swatch(argb = swatch, selected = argb == swatch) { onChange(swatch) }
            }
            // The way to a color that is not on the row. Shows the current one, so it doubles as the readout.
            //
            // **It opens the picker elsewhere rather than unfolding it here**, which is the whole of
            // `StudioColorPickerHost`: a saturation panel inside this scrolling section filled it and swallowed
            // every drag over it, so the section could not be scrolled past the control the user had just opened.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(argb?.let { Color(it) } ?: Color.Transparent)
                    .border(width = 1.dp, color = Color.White.copy(0.3f), shape = CircleShape)
                    .clickable {
                        // Black when there is nothing yet: the picker has to start somewhere, and it is the one
                        // value a user reading the panel will not mistake for a color that was already chosen.
                        picker.open(argb ?: 0xFF000000.toInt(), onChange)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = StudioContentColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** One color dot. A null [argb] is the "no tint" dot, drawn hollow. */
@Composable
private fun Swatch(argb: Int?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (argb == null) Color.Transparent else Color(argb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) StudioContentColor else Color.White.copy(0.3f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * The layer's silhouette.
 *
 * **Offered on every layer, including custom ones**, which differs from L1: it shaped only the foreground and left
 * custom images to their own alpha. The renderer here masks whatever it is given, so the restriction would be one
 * the UI invented — and a shaped custom layer is an obviously useful thing (a color fill trimmed to a circle is
 * how you put a colored disc behind a legacy icon).
 *
 * **A shape is shown, not named.** This was a grid of text chips, which asks the user to read "rounded_square" and
 * picture it — for the one control on this screen whose entire subject is what something looks like. Every other
 * chooser here already draws its subject (the source tiles are a pack's own artwork, the swatches are the colors), and
 * this is that rule reaching the last section that broke it. The ids are gone from the UI entirely; they stay what
 * `IconShapes` maps and what is written to disk.
 *
 * **Paged, with a grid on each page, because this list is about to get long.** Seven built-ins fit one page today, so
 * the pager shows no dots and never scrolls — the arrangement is here for the shape set that is coming, and building
 * it now means the layout does not have to be reconsidered when it arrives. Adding shapes past a page's capacity adds
 * a page rather than growing the panel, which is what keeps the section a fixed height inside a panel that is already
 * capped and scrolling: **paging horizontally is how this avoids a vertical scroller inside a vertical scroller**,
 * which is the arrangement that makes a drag ambiguous.
 */
@Composable
internal fun ShapeControls(spec: IconLayerSpec, onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit) {
    // **`null` is the first cell rather than a row above the grid.** "No shape" is a choice among the same set — the
    // one every layer starts on — so it belongs in the set, and a full-width row above a grid is exactly the
    // settings-list vocabulary this screen exists not to be.
    val pages = remember { (listOf<IconShape?>(null) + IconShapes.All).chunked(ShapesPerPage) }
    val pagerState = rememberPagerState { pages.size }

    LabeledControl("Shape") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // **The page height is derived from the width, because it is a consequence of it and not a value
            // anyone owns.** Tiles are square and the columns are fixed, so the width settles the cell and the
            // cell settles two rows plus the gap between them — a formula exists, which is this codebase's own
            // test for derive-versus-store (`derivedCell`, `CellFit`). It was a flat 168dp, and that is wrong on
            // an ordinary phone before it is wrong on a tablet: a 393dp screen leaves ≈361dp inside the panel,
            // so a cell is ≈84dp and two rows plus the gap want ≈176. The eight dp it was short of is not a
            // clipped corner — a `LazyVerticalGrid` in a box too small to hold it **scrolls**, so the fixed
            // height quietly created the vertical-scroller-inside-a-vertical-scroller that paging exists to
            // avoid. `pageSpacing` is not subtracted: with `PageSize.Fill` it is inserted *between* pages and
            // pages stay the full viewport width.
            BoxWithConstraints {
                val cell = (maxWidth - ShapeGridSpacing * (ShapeColumns - 1)) / ShapeColumns
                val pageHeight = cell * ShapeRows + ShapeGridSpacing * (ShapeRows - 1)

                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 8.dp,
                    modifier = Modifier.height(pageHeight),
                ) { page ->
                    ShapePage(
                        shapes = pages[page],
                        selected = spec.shape,
                        onSelect = { shape -> onUpdate { it.copy(shape = shape) } },
                    )
                }
            }

            // Absent at one page, where a single dot would say nothing about a pager that cannot be paged.
            if (pages.size > 1) PagerDots(current = pagerState.currentPage, count = pages.size)
        }
    }
}

/**
 * One page of the shape chooser: [ShapeRows] rows of [ShapeColumns] tiles.
 *
 * **Plain rows rather than a `LazyVerticalGrid`, which is what makes the height bug unrepeatable rather than
 * merely fixed.** A page holds at most eight tiles by construction, so laziness saves nothing — and it was an
 * active liability, because a lazy grid given a box too short for its contents *scrolls* instead of clipping. That
 * turned a wrong constant into a nested vertical scroller; plain rows cannot do that whatever height they are
 * handed. It is the grid plan's right-tool-per-surface rule, on a surface small enough that the answer is "no
 * tool".
 *
 * A short last page is **padded with empty weights**, which the lazy grid did for free and a `Row` does not: four
 * columns of `weight(1f)` given two children would hand each half the width, so the final page's tiles would come
 * out twice the size of every other page's.
 */
@Composable
private fun ShapePage(shapes: List<IconShape?>, selected: IconShape?, onSelect: (IconShape?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ShapeGridSpacing)) {
        shapes.chunked(ShapeColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShapeGridSpacing)) {
                row.forEach { shape ->
                    ShapeTile(
                        shape = shape,
                        selected = selected == shape,
                        onClick = { onSelect(shape) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(ShapeColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One shape, drawn at the size it is offered in.
 *
 * The silhouette is the shape's own vector, tinted — the same drawable the renderer builds its clip mask from, so what
 * is on the tile and what lands on the icon cannot disagree. [IconShapes.drawableResOrNull] returning null is a stale
 * persisted id rather than a state to design for, and it falls through to the same mark `null` uses.
 */
@Composable
private fun ShapeTile(
    shape: IconShape?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resource = shape?.let(IconShapes::drawableResOrNull)

    Box(
        modifier = modifier
            // Square, which is also the assumption the page height is derived from — so a tile that stopped being
            // square would have to change that arithmetic too.
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = resource?.let { painterResource(it) } ?: rememberVectorPainter(Icons.Default.Block),
            // The id is the honest description even though it is no longer drawn: it is what this shape is called
            // everywhere else, and a screen reader has nothing else to go on once the label is a picture.
            contentDescription = shape?.id?.replace('_', ' ') ?: "No shape",
            tint = StudioContentColor.copy(alpha = if (resource == null) 0.5f else 1f),
            modifier = Modifier
                .fillMaxSize()
                .padding(ShapeTileInset),
        )
    }
}

/** Which page of a pager is showing. Not a control — pressing one is not offered, since swiping is the gesture. */
@Composable
private fun PagerDots(current: Int, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StudioContentColor.copy(alpha = if (index == current) 1f else 0.3f)),
            )
        }
    }
}

/**
 * Four across, two rows down: eight to a page, which is exactly the seven built-ins plus "no shape" today.
 *
 * [ShapesPerPage] is the **product** rather than a third number, so the page size and the shape of the page cannot
 * disagree — which is what a flat `8` beside a `4` was one edit away from doing.
 */
private const val ShapeColumns = 4
private const val ShapeRows = 2
private const val ShapesPerPage = ShapeColumns * ShapeRows

/** Between tiles on both axes — and, being the gap the rows are separated by, an input to the page height. */
private val ShapeGridSpacing = 8.dp

/** Keeps the silhouette clear of the tile's own rounded corners. */
private val ShapeTileInset = 12.dp


/**
 * Where the layer's content comes from.
 *
 * **Which options are offered turns on two things, and they are different in kind.** Most of it is the layer's
 * [LayerRole], because the model says so: [LayerSource.AppDefault] is meaningless on a custom layer (there is no "the
 * app's custom layer" to resolve), and [LayerSource.AppDefaultMonochrome] is the foreground's alternate artwork and
 * nowhere else's. Offering either where it resolves to nothing would be a control that silently does nothing — which
 * this codebase treats as worse than a missing one.
 *
 * The rest is **which studio this is**, and that is a rule about what a global edit should be *allowed* to do rather
 * than about what resolves: [allowsFixedSource] and [onBrowsePack] each gate a source that would hand one specific
 * picture or color to every app on the device. They differ in reach — a fixed source is refused on the **foreground**
 * alone, that being the layer which identifies the app, while a *named* pack drawable is refused everywhere but the
 * individual studio — and both arrive as a decision made elsewhere rather than as a test performed here, since the
 * ViewModel refuses behind each of them.
 *
 * **Two ranks of control, which is what the layout says.** The tiles are the *providers* — whose artwork this is — and
 * beneath them sit refinements of whichever is chosen: monochrome under the app's own artwork, a named drawable under
 * a pack. Neither refinement changes the provider, so neither is a tile.
 *
 * @param allowsFixedSource whether this layer may take a source that is the same for every app — a solid color or a
 *   custom image; see `IconStudioState.canUseFixedSource`.
 * @param onToggleNormalize turns [IconLayerSpec.normalize] on or off — whether the app's artwork is resized so
 *   every icon covers about the same amount of its box. Beside monochrome because both refine the app's own artwork.
 * @param onToggleMonochrome switches the app's own artwork between its normal and monochrome forms. A command rather
 *   than an [onUpdate] written here, so the edit records itself in history — see `IconStudioViewModel`.
 * @param onPickAppDefault chooses the app's own artwork, in whichever form this layer was last showing it. A command
 *   for a second reason on top of that one: the form is remembered by the ViewModel, so this panel cannot write it.
 * @param onPickSolidFill fills the layer with a flat color, returning to the one it last held. A command for
 *   [onPickAppDefault]'s reason exactly, pointed at a value instead of a form.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceControls(
    spec: IconLayerSpec,
    packs: List<InstalledIconPack>,
    allowsFixedSource: Boolean,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
    onPickImage: () -> Unit,
    onPickAppDefault: () -> Unit,
    onPickSolidFill: () -> Unit,
    onToggleMonochrome: () -> Unit,
    onToggleNormalize: () -> Unit,
    onPickPack: (String) -> Unit,
    onBrowsePack: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledControl("Source") {
            // **A flow row of tiles rather than a column of rows**, because the choices are *pictures*: an icon pack is
            // recognized by its own artwork long before its name is read, so labeled text was asking the user to read
            // a list where they could have looked at one. Flowing rather than scrolling sideways, so a device with six
            // packs installed shows all six instead of hiding the last of them past an edge.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Absent on a custom layer, where it resolves to nothing: there is no "the app's custom layer".
                if (spec.role != LayerRole.CUSTOM) {
                    SourceTile(
                        label = "System default",
                        // **`AppDefaultMonochrome` reads as selected here too, and that is not a special case.**
                        // Monochrome is a *refinement of* this source rather than a peer of it — the app's own
                        // artwork either way — so the tile is genuinely the chosen one, and the row beneath is what
                        // says which form of it.
                        selected = spec.source == LayerSource.AppDefault ||
                            spec.source == LayerSource.AppDefaultMonochrome,
                        // **Which is also why the tile does not write a source itself.** Coming back from a pack or an
                        // image has to land on the form the layer was left in, and only the ViewModel remembers that —
                        // see `IconStudioViewModel.pickAppDefault`. Writing `AppDefault` here would drop the refinement
                        // the row beneath controls, with the tile looking identical before and after the press.
                        onClick = onPickAppDefault,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = StudioContentColor,
                            modifier = Modifier.size(SourceGlyphSide),
                        )
                    }
                }

                // Acts on every press rather than only when unselected, because pressing it again re-picks.
                if (allowsFixedSource) {
                    SourceTile(
                        label = "Custom image",
                        selected = spec.source is LayerSource.CustomImage,
                        onClick = onPickImage,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = StudioContentColor,
                            modifier = Modifier.size(SourceGlyphSide),
                        )
                    }
                }

                // **One tile per pack, drawn as the pack's own launcher icon** — which `InstalledIconPack.preview`
                // already carries, for exactly the reason its KDoc gives: packs are recognized by their artwork rather
                // than by their name. An empty list is the ordinary state on a device with none, and it is also what a
                // missing `<queries>` declaration looks like — see `IconPackManager`.
                packs.forEach { pack ->
                    SourceTile(
                        label = pack.label,
                        selected = (spec.source as? LayerSource.IconPack)?.packPackage == pack.packageName,
                        onClick = { onPickPack(pack.packageName) },
                    ) {
                        val preview = pack.preview
                        if (preview != null) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(SourcePackIconSide),
                            )
                        } else {
                            // A pack whose own icon could not be read still has to be pickable; its label is beneath
                            // the tile either way.
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = StudioContentColor,
                                modifier = Modifier.size(SourceGlyphSide),
                            )
                        }
                    }
                }
            }
        }

        // **The global foreground's missing tiles are left unexplained**, deliberately. The copy that used to sit here
        // named the alternative (add a custom layer) at the moment it was wanted, which is the argument for a note —
        // but it was four lines of prose in a panel of tiles and sliders, and it appeared on the layer a user opens the
        // global studio on, so it was the first thing on the busiest section. `IconStudioState.canUseFixedSource` is
        // still the rule; what is gone is stating it here.
        //
        // **A refinement of the chosen source, not a tile of its own** — which is the whole reason it sits here rather
        // than among them. The tiles answer "whose artwork is this?" and monochrome does not change the answer: it is
        // still the app's. As a fourth tile it would read as a peer of a pack and an image, and it would appear on one
        // layer only, so the row would change length as the selection moved.
        //
        // The shape is the pack-browse row's directly beneath: a refinement shown only while the source it refines is
        // chosen. Foreground-only, because the platform ships one silhouette and it is for that slot — there is no
        // "the app's monochrome background". Absent rather than disabled elsewhere, per the usual rule.
        //
        // **Offered whether or not this app ships a themed layer**, and it has to be: `IconLayerResolver` decides
        // which of the two monochromes an app gets, and in the global studio that is not one answer. Draining a layer
        // that is *not* app artwork — a pack, an image — is Saturation's job in Effects, not a second meaning here.
        if (spec.role == LayerRole.FOREGROUND &&
            (spec.source == LayerSource.AppDefault || spec.source == LayerSource.AppDefaultMonochrome)
        ) {
            ChoiceRow(
                label = "Monochrome",
                selected = spec.source == LayerSource.AppDefaultMonochrome,
                onClick = onToggleMonochrome,
            )

            // **The other refinement of the app's own artwork, so it sits here rather than under Transform.** It
            // ends up multiplying the layer's zoom, but what it decides is *how to read the artwork this source just
            // chose* — a question only this panel is asking. Under Transform it would sit beside a zoom slider it
            // silently scales, and the two would read as rivals.
            //
            // Foreground-only, matching where `normalized` applies: the background is deliberately left alone, and a
            // pack, an image or a fill was placed by somebody on purpose.
            ChoiceRow(
                label = "Normalize size",
                selected = spec.normalize,
                onClick = onToggleNormalize,
            )
        }

        // **Only when a pack is already chosen, and only for a single app.** Browsing offers a *named* drawable, which
        // the global default would hand to every app — so `onBrowsePack` is null there and the row is absent rather
        // than disabled.
        val chosen = spec.source as? LayerSource.IconPack
        if (chosen != null && onBrowsePack != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceChip(
                    label = chosen.drawableName?.let { "Icon: $it — change" } ?: "Choose a different icon",
                    selected = chosen.drawableName != null,
                    modifier = Modifier.weight(1f),
                ) { onBrowsePack(chosen.packPackage) }

                // **The way back out of a named drawable, and it needs to be a control rather than a trick.** Clearing
                // the name lets the pack's own `appfilter.xml` decide again — which re-picking the pack tile also does,
                // as a side effect of `pickPack` writing a name-less source. That is not something a user can be
                // expected to work out: nothing about a tile that is already selected suggests pressing it undoes
                // something else.
                //
                // **A chip, not an icon button**, because this row is made of chips and everything else in the section
                // is text on the same wash — a lone glyph at the end of a text row reads as chrome rather than as one
                // of the choices. It is also the honest form here: "reset" is a word, where the arrow-in-a-circle that
                // usually means it is one of the least specific glyphs there is.
                //
                // Present only once there is a name to clear, so it is never a button that does nothing — and beside
                // the row it undoes rather than somewhere in the section, since what it reverts is *that* choice.
                if (chosen.drawableName != null) {
                    ChoiceChip(label = "Reset", selected = false) {
                        onUpdate { it.copy(source = LayerSource.IconPack(chosen.packPackage)) }
                        onCommit()
                    }
                }
            }
        }

        // **Parked here pending a home, and deliberately not a tile.** A solid fill is not artwork *from* anywhere, so
        // it does not belong among the source kinds — the flow row answers "where do this layer's pixels come from",
        // and a flat color answers a different question. It stays reachable meanwhile, because a colored plate beneath
        // an icon is what a layer added empty most often becomes.
        //
        // TODO: move to whichever section ends up owning a layer's appearance.
        if (allowsFixedSource) {
            LabeledControl("Fill") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // **A command, for the "System default" tile's two reasons at once**: pressing it while a fill is
                    // already chosen must not throw away the color underneath, and returning to a fill must land on
                    // the color this layer last held — which only the ViewModel remembers. See `pickSolidFill`.
                    ChoiceRow("Solid color", spec.source is LayerSource.SolidFill, onPickSolidFill)
                    // Gated with the row that chooses a fill, not shown whenever one happens to be set: a layer that
                    // may not *take* a solid color must not offer to recolor one either.
                    (spec.source as? LayerSource.SolidFill)?.let { fill ->
                        ColorField(argb = fill.argb) { argb ->
                            onUpdate { it.copy(source = LayerSource.SolidFill(argb)) }
                            onCommit()
                        }
                    }
                }
            }
        }
    }
}

/** The side of a source tile — a press target in its own right, and large enough to read a pack's icon in. */
private val SourceTileSide = 64.dp

/** A source tile's corner: square with a radius, so a row of them reads as a set of chips rather than as buttons. */
private val SourceTileCorner = 14.dp

/** A glyph inside a tile, for the sources that have no artwork of their own to show. */
private val SourceGlyphSide = 26.dp

/** A pack's own launcher icon inside a tile — larger than a glyph, since it *is* the thing being chosen. */
private val SourcePackIconSide = 36.dp

/**
 * One choice in the source row: a rounded square with something drawn in the middle of it, and its name beneath.
 *
 * **Labeled despite being a picture**, which is the one place this departs from "a tile is recognized by its artwork":
 * two of the three kinds have no artwork, only a glyph, and an unlabeled glyph is the thing this studio's own notes
 * call worse than a wordy button. A label is also how two packs with similar icons are told apart.
 *
 * The label sits **outside** the tile and is constrained to its width, so a long pack name wraps beneath the square
 * rather than stretching it — every tile stays one size, which is what makes the row read as a set.
 */
@Composable
private fun SourceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier.width(SourceTileSide),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SourceTileSide)
                .clip(RoundedCornerShape(SourceTileCorner))
                .background(Color.White.copy(alpha = if (selected) 0.22f else 0.08f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = StudioContentColor.copy(alpha = if (selected) 1f else 0.2f),
                    shape = RoundedCornerShape(SourceTileCorner),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Text(
            text = label,
            color = StudioContentColor.copy(alpha = if (selected) 1f else 0.7f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The quick-pick palette behind every [ColorField] — neutrals, then hues, with the picker for everything else.
 *
 * **Material 3 tonal values rather than the saturated primaries this used to hold.** Those were Material *2*'s 600
 * level (`E53935`, `1E88E5`, `43A047`, `FDD835`) — the sRGB primaries barely darkened, which is exactly the look M3
 * replaced: a plate that loud competes with the artwork sitting on it instead of backing it. These are **tone 40** of
 * an M3 tonal palette, the level the `primary` role takes in a light scheme — deep enough to carry a white or
 * monochrome glyph, low enough in chroma to read as a surface. `6750A4` is M3's own baseline primary.
 *
 * **They are literals because the launcher's own scheme cannot supply them, and that is a trap worth stating.**
 * `MaterialTheme.colorScheme` here is the **monochrome** bridge (see `MorphicColors.toM3ColorScheme`), so reaching for
 * `colorScheme.primary` to get "the M3 purple" returns gray — correctly, since our chrome is grayscale so the
 * wallpaper and the icons carry the color.
 *
 * **A red among them does not breach that palette rule**, which reserves red for `error`: the rule is about *chrome*,
 * and these are content a user paints an icon with — the same exception the backdrop effects take in carrying the
 * wallpaper's hue.
 *
 * Ordered neutrals-first because the row is trimmed from the end: seven fit beside the picker, six when a "no color"
 * swatch takes the first slot, so the last entry is the one a tint's row drops.
 */
private val FillSwatches = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF79747E.toInt(),
    0xFF6750A4.toInt(), 0xFF415F91.toInt(), 0xFF386A20.toInt(),
    0xFF8F4C38.toInt(),
)

@Composable
private fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = StudioContentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChip(label = label, selected = selected, modifier = Modifier.fillMaxWidth(), onClick = onClick)
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        color = StudioContentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** What a row calls the layer. The role, not the index — "layer 2" tells the user nothing. */
internal val LayerRole.label: String
    get() = when (this) {
        LayerRole.FOREGROUND -> "Foreground"
        LayerRole.BACKGROUND -> "Background"
        LayerRole.CUSTOM -> "Layer"
    }

/** The one-line summary of where a layer's pixels come from, shown beside its role. */
internal val LayerSource.label: String
    get() = when (this) {
        // What a freshly added layer says about itself, and it has to say *something*: the row is the only place an
        // empty layer is visible at all, since it draws nothing on the canvas.
        LayerSource.Empty -> "empty"
        LayerSource.AppDefault -> "app default"
        LayerSource.AppDefaultMonochrome -> "monochrome"
        is LayerSource.CustomImage -> "image"
        is LayerSource.SolidFill -> "solid color"
        is LayerSource.IconPack -> "icon pack"
    }
