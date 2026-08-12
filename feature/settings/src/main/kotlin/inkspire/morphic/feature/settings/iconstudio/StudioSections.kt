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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.color.MorphicColorPicker
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
            // Disabled rather than hidden, because *which* move is illegal is the information: a greyed arrow says
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
 * Every control edits live and calls [onCommit] when the gesture *ends* — so the preview follows the finger, while
 * undo steps over the whole drag rather than through a hundred frames of it.
 */
@Composable
internal fun TransformControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    LabelledControl("Position") {
        Morphic2DPad(
            x = spec.offsetX,
            y = spec.offsetY,
            onValueChange = { x, y -> onUpdate { it.copy(offsetX = x, offsetY = y) } },
            xRange = -0.5f..0.5f,
            yRange = -0.5f..0.5f,
            onValueChangeFinished = onCommit,
            modifier = Modifier.fillMaxWidth().size(140.dp),
        )
    }
    LabelledControl("Zoom  ${"%.2f".format(spec.zoom)}") {
        MorphicSlider(
            value = spec.zoom,
            onValueChange = { value -> onUpdate { it.copy(zoom = value) } },
            valueRange = 0.2f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Rotation  ${"%.0f".format(spec.rotation)}°") {
        MorphicSlider(
            value = spec.rotation,
            onValueChange = { value -> onUpdate { it.copy(rotation = value) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
}

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

    LabelledControl("Opacity  ${"%.2f".format(spec.opacity)}") {
        MorphicSlider(
            value = spec.opacity,
            onValueChange = { value -> onUpdate { it.copy(opacity = value) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Blend") {
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
    LabelledControl("Saturation  ${"%.2f".format(color.saturation)}") {
        MorphicSlider(
            value = color.saturation,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(saturation = value)) } },
            valueRange = 0f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Brightness  ${"%.2f".format(color.brightness)}") {
        MorphicSlider(
            value = color.brightness,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(brightness = value)) } },
            valueRange = 0.2f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Hue  ${"%.0f".format(color.hueDegrees)}°") {
        MorphicSlider(
            value = color.hueDegrees,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(hueDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Tint") {
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
        LabelledControl("Tint style") {
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

    LabelledControl("Strength  ${"%.2f".format(gradient.strength)}") {
        MorphicSlider(
            value = gradient.strength,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(strength = value)) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("Angle  ${"%.0f".format(gradient.angleDegrees)}°") {
        MorphicSlider(
            value = gradient.angleDegrees,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(angleDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
    LabelledControl("From") {
        ColorField(argb = gradient.startArgb) { argb ->
            onUpdate { it.withGradient(gradient.copy(startArgb = argb)) }
        }
    }
    LabelledControl("To") {
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
    var picking by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (clearable) Swatch(argb = null, selected = argb == null) { onChange(null) }
            FillSwatches.take(if (clearable) 6 else 7).forEach { swatch ->
                Swatch(argb = swatch, selected = argb == swatch) { onChange(swatch) }
            }
            // The way to a color that is not on the row. Shows the current one, so it doubles as the readout.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(argb?.let { Color(it) } ?: Color.Transparent)
                    .border(
                        width = if (picking) 2.dp else 1.dp,
                        color = if (picking) StudioContentColor else Color.White.copy(0.3f),
                        shape = CircleShape,
                    )
                    .clickable { picking = !picking },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = StudioContentColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (picking) {
            MorphicColorPicker(
                // Black when there is nothing yet: the picker has to start somewhere, and it is the one value a
                // user reading the panel will not mistake for a color that was already chosen.
                argb = argb ?: 0xFF000000.toInt(),
                onArgbChange = onChange,
            )
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
 */
@Composable
internal fun ShapeControls(spec: IconLayerSpec, onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit) {
    LabelledControl("Shape") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // "None" first and always reachable: unshaped is what every icon renders as today, so it has to be a
            // choice rather than a state you can only get back to by undoing.
            ChoiceRow(label = "None", selected = spec.shape == null) { onUpdate { it.copy(shape = null) } }
            IconShapes.All.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { shape ->
                        ChoiceChip(
                            label = shape.id.replace('_', ' '),
                            selected = spec.shape == shape,
                            modifier = Modifier.fillMaxWidth(1f / row.size),
                        ) { onUpdate { it.copy(shape = shape) } }
                    }
                }
            }
        }
    }
}

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
 * picture or color to every app on the device. They differ in reach — a fixed source is refused only on the two
 * app-artwork layers, a *named* pack drawable everywhere but the individual studio — and both arrive as a decision made
 * elsewhere rather than as a test performed here, since the ViewModel refuses behind each of them.
 *
 * **Two ranks of control, which is what the layout says.** The tiles are the *providers* — whose artwork this is — and
 * beneath them sit refinements of whichever is chosen: monochrome under the app's own artwork, a named drawable under
 * a pack. Neither refinement changes the provider, so neither is a tile.
 *
 * @param allowsFixedSource whether this layer may take a source that is the same for every app — a solid color or a
 *   custom image; see `IconStudioState.canUseFixedSource`.
 * @param onToggleMonochrome switches the app's own artwork between its normal and monochrome forms. A command rather
 *   than an [onUpdate] written here, so the edit records itself in history — see `IconStudioViewModel`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceControls(
    spec: IconLayerSpec,
    packs: List<InstalledIconPack>,
    allowsFixedSource: Boolean,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onPickImage: () -> Unit,
    onToggleMonochrome: () -> Unit,
    onPickPack: (String) -> Unit,
    onBrowsePack: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabelledControl("Source") {
            // **A flow row of tiles rather than a column of rows**, because the choices are *pictures*: an icon pack is
            // recognised by its own artwork long before its name is read, so labelled text was asking the user to read
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
                        // says which form of it. Tapping re-picks the plain default, which is also the way off
                        // monochrome for anyone who does not spot the toggle.
                        selected = spec.source == LayerSource.AppDefault ||
                            spec.source == LayerSource.AppDefaultMonochrome,
                        onClick = { onUpdate { it.copy(source = LayerSource.AppDefault) } },
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
                // already carries, for exactly the reason its KDoc gives: packs are recognised by their artwork rather
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

        // **Says why a tile is missing**, which its absence earns: on the global background with no packs installed the
        // row would otherwise be a single already-selected tile — a section that does nothing, the state this studio
        // keeps being mistaken for broken in. See `IconStudioState.canUseFixedSource` for the rule; this is also the one
        // place the alternative can be named at the moment it is wanted.
        if (!allowsFixedSource) {
            Text(
                text = "A color or image here would replace every app's own artwork. " +
                    "Add a layer instead, and put it under the background.",
                color = StudioContentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

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
        }

        // **Only when a pack is already chosen, and only for a single app.** Browsing offers a *named* drawable, which
        // the global default would hand to every app — so `onBrowsePack` is null there and the row is absent rather
        // than disabled.
        val chosen = spec.source as? LayerSource.IconPack
        if (chosen != null && onBrowsePack != null) {
            ChoiceRow(
                label = chosen.drawableName?.let { "Icon: $it — change" } ?: "Choose a different icon",
                selected = chosen.drawableName != null,
            ) { onBrowsePack(chosen.packPackage) }
        }

        // **Parked here pending a home, and deliberately not a tile.** A solid fill is not artwork *from* anywhere, so
        // it does not belong among the source kinds — the flow row answers "where do this layer's pixels come from",
        // and a flat color answers a different question. It stays reachable meanwhile, because a colored plate beneath
        // an icon is what a layer added empty most often becomes.
        //
        // TODO: move to whichever section ends up owning a layer's appearance.
        if (allowsFixedSource) {
            LabelledControl("Fill") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceRow("Solid color", spec.source is LayerSource.SolidFill) {
                        onUpdate { it.copy(source = LayerSource.SolidFill(FillSwatches.first())) }
                    }
                    // Gated with the row that chooses a fill, not shown whenever one happens to be set: a layer that
                    // may not *take* a solid color must not offer to recolor one either.
                    (spec.source as? LayerSource.SolidFill)?.let { fill ->
                        ColorField(argb = fill.argb) { argb ->
                            onUpdate { it.copy(source = LayerSource.SolidFill(argb)) }
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
 * **Labelled despite being a picture**, which is the one place this departs from "a tile is recognised by its artwork":
 * two of the three kinds have no artwork, only a glyph, and an unlabelled glyph is the thing this studio's own notes
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

/** The quick-pick palette behind every [ColorField] — greys plus the primaries, with the picker for the rest. */
private val FillSwatches = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(),
    0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
    0xFFFDD835.toInt(), 0xFF8E24AA.toInt(),
)

@Composable
private fun LabelledControl(label: String, content: @Composable () -> Unit) {
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
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
