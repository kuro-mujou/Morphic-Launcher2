package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.slider.Morphic2DPad
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource

/**
 * The studio's floating settings surface: the layer stack, and the controls for whichever layer is selected.
 *
 * **There is no "this layer / whole icon" scope toggle, and that is a simplification the model earned rather than a
 * decision taken here.** L1's editor mixed per-layer tools (transform, colour, shadow) with whole-icon ones (icon
 * shape, background, theming, size, skin, pack) in one flat row, and its UI plan left the split as an open question.
 * In L2 every one of those whole-icon tools has already gone somewhere else: the tile shape became a *per-layer*
 * shape (there is no stack-level mask), the background is the background layer's source, theming is
 * [LayerSource.AppDefaultMonochrome] on the foreground, sizing is `data:settings` and a different screen entirely,
 * the skin is deferred, and an icon pack will be a per-layer source. So everything here acts on one layer, and the
 * question does not arise.
 */
@Composable
fun StudioPanel(
    state: IconStudioState,
    hazeState: HazeState,
    onSelectLayer: (Int) -> Unit,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
    onToggleVisible: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .studioSurface(hazeState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LayerStackRows(
            state = state,
            onSelectLayer = onSelectLayer,
            onToggleVisible = onToggleVisible,
            onMove = onMove,
            onAdd = onAdd,
            onRemove = onRemove,
        )

        state.selectedLayer?.let { spec ->
            SelectedLayerControls(
                spec = spec,
                onUpdate = onUpdate,
                onCommit = onCommit,
                onPickImage = onPickImage,
            )
        }
    }
}

/**
 * The stack, top layer first — **drawn in the order it is drawn on screen**, which is the reverse of the list's
 * index order. A layer editor that showed the bottom layer at the top would be asking the user to hold an inversion
 * in their head for no reason.
 */
@Composable
private fun LayerStackRows(
    state: IconStudioState,
    onSelectLayer: (Int) -> Unit,
    onToggleVisible: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.editing.layers.indices.reversed().forEach { index ->
            val spec = state.editing.layers[index]
            LayerRow(
                spec = spec,
                selected = index == state.selected,
                onClick = { onSelectLayer(index) },
                onToggleVisible = onToggleVisible,
            )
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Disabled rather than hidden, because *which* move is illegal is the information: a greyed arrow says
            // "the foreground cannot go below its background" before the move is attempted, where a vanished button
            // says nothing and a refused drag says nothing twice.
            StudioIconButton(Icons.Default.KeyboardArrowUp, "Move up", state.canMoveUp) { onMove(true) }
            StudioIconButton(Icons.Default.KeyboardArrowDown, "Move down", state.canMoveDown) { onMove(false) }
            StudioIconButton(Icons.Default.Add, "Add layer", enabled = true, onClick = onAdd)
            StudioIconButton(Icons.Default.Delete, "Remove layer", state.canRemoveSelected, onClick = onRemove)
        }
    }
}

/** One row of the stack: what the layer is, whether it is showing, and whether it is the one being edited. */
@Composable
private fun LayerRow(
    spec: IconLayerSpec,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
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

/** Which control group is showing. */
private enum class LayerTool(val label: String) {
    TRANSFORM("Transform"),
    SHAPE("Shape"),
    SOURCE("Source"),

    /**
     * Opacity, blend mode and recolouring together.
     *
     * They are **not** one thing in the model — opacity and blend are compositing fields on the spec, recolouring
     * is a `LayerEffect` — but that distinction is about what can vary independently in storage, and a user
     * adjusting how a layer reads colour-wise does not care which side of it a control sits on. One tab.
     */
    COLOR("Color"),

    /** The gradient overlay. Its own tab because it is four controls, not because it is a different *kind* of thing. */
    GRADIENT("Gradient"),
}

@Composable
private fun SelectedLayerControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
    onPickImage: () -> Unit,
) {
    var tool by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MorphicSegmentedButtons(
            options = LayerTool.entries.map { it.label },
            selectedIndex = tool,
            onSelect = { tool = it },
            modifier = Modifier.fillMaxWidth(),
        )

        when (LayerTool.entries[tool]) {
            LayerTool.TRANSFORM -> TransformControls(spec, onUpdate, onCommit)
            LayerTool.SHAPE -> ShapeControls(spec, onUpdate)
            LayerTool.SOURCE -> SourceControls(spec, onUpdate, onPickImage)
            LayerTool.COLOR -> ColorControls(spec, onUpdate, onCommit)
            LayerTool.GRADIENT -> GradientControls(spec, onUpdate, onCommit)
        }
    }
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
private fun TransformControls(
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
 * How the layer joins the stack (opacity, blend) and how it is recoloured (tint, saturation, brightness, hue).
 *
 * **The recolouring controls write one `LayerEffect.Color`, never four**, via `IconLayerSpec.withColor` — which is
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // "None" first, because a tint is the one recolouring that cannot be undone by returning a slider to
            // the middle — without a way off, picking one would be a one-way door.
            Swatch(argb = null, selected = color.tintArgb == null) {
                onUpdate { it.withColor(color.copy(tintArgb = null)) }
            }
            FillSwatches.take(6).forEach { argb ->
                Swatch(argb = argb, selected = color.tintArgb == argb) {
                    onUpdate { it.withColor(color.copy(tintArgb = argb)) }
                }
            }
        }
    }
}

/**
 * The gradient overlay's two stops, its direction and how strongly it is laid on.
 *
 * **Strength doubles as the on/off switch**: at zero the effect is identity and `withGradient` drops it from the
 * list entirely, so there is no separate toggle to disagree with the slider. That is the same shape the colour
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FillSwatches.forEach { argb ->
                Swatch(argb = argb, selected = gradient.startArgb == argb) {
                    onUpdate { it.withGradient(gradient.copy(startArgb = argb)) }
                }
            }
        }
    }
    LabelledControl("To") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FillSwatches.forEach { argb ->
                Swatch(argb = argb, selected = gradient.endArgb == argb) {
                    onUpdate { it.withGradient(gradient.copy(endArgb = argb)) }
                }
            }
        }
    }
}

/** One colour dot. A null [argb] is the "no tint" dot, drawn hollow. */
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
 * the UI invented — and a shaped custom layer is an obviously useful thing (a colour fill trimmed to a circle is
 * how you put a coloured disc behind a legacy icon).
 */
@Composable
private fun ShapeControls(spec: IconLayerSpec, onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit) {
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
 * Which options are offered depends on the layer's [LayerRole], because the model says so:
 * [LayerSource.AppDefault] is meaningless on a custom layer (there is no "the app's custom layer" to resolve), and
 * [LayerSource.AppDefaultMonochrome] is the foreground's alternate artwork and nowhere else's. Offering either
 * where it resolves to nothing would be a control that silently does nothing — which this codebase treats as worse
 * than a missing one.
 */
@Composable
private fun SourceControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onPickImage: () -> Unit,
) {
    LabelledControl("Source") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (spec.role != LayerRole.CUSTOM) {
                ChoiceRow("App default", spec.source == LayerSource.AppDefault) {
                    onUpdate { it.copy(source = LayerSource.AppDefault) }
                }
            }
            if (spec.role == LayerRole.FOREGROUND) {
                ChoiceRow("App monochrome", spec.source == LayerSource.AppDefaultMonochrome) {
                    onUpdate { it.copy(source = LayerSource.AppDefaultMonochrome) }
                }
            }
            ChoiceRow("Solid colour", spec.source is LayerSource.SolidFill) {
                onUpdate { it.copy(source = LayerSource.SolidFill(FillSwatches.first())) }
            }
            // Offered on *every* layer, foreground and background included — replacing an app's own artwork is
            // what makes this more than decoration, and the renderer draws an image wherever it is put. Tapping
            // it again re-picks, which is why the row is a button rather than a selected state.
            ChoiceRow(
                label = if (spec.source is LayerSource.CustomImage) "Custom image — change" else "Custom image",
                selected = spec.source is LayerSource.CustomImage,
                onClick = onPickImage,
            )

            (spec.source as? LayerSource.SolidFill)?.let { fill ->
                // **A swatch row, not a colour picker** — L1 had a full HSV picker in its design system and it is
                // not ported. A fixed palette is enough to make the source usable now, and it is the control that
                // is replaced rather than a workaround that has to be unpicked.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FillSwatches.forEach { argb ->
                        Swatch(argb = argb, selected = fill.argb == argb) {
                            onUpdate { it.copy(source = LayerSource.SolidFill(argb)) }
                        }
                    }
                }
            }
        }
    }
}

/** The fixed palette a solid fill can take until a real colour picker exists. Greys plus the primaries. */
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
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = StudioContentColor.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** What a row calls the layer. The role, not the index — "layer 2" tells the user nothing. */
private val LayerRole.label: String
    get() = when (this) {
        LayerRole.FOREGROUND -> "Foreground"
        LayerRole.BACKGROUND -> "Background"
        LayerRole.CUSTOM -> "Layer"
    }

/** The one-line summary of where a layer's pixels come from, shown beside its role. */
private val LayerSource.label: String
    get() = when (this) {
        LayerSource.AppDefault -> "app default"
        LayerSource.AppDefaultMonochrome -> "monochrome"
        is LayerSource.CustomImage -> "image"
        is LayerSource.SolidFill -> "solid colour"
    }
