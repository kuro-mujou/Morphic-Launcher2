package inkspire.morphic.feature.settings.widgetstudio

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widget.WidgetScales
import kotlin.math.roundToInt

/**
 * One property of a layer as the Advanced view edits it.
 *
 * **Most are described as a [WidgetGlobal]**, so the Style tab's own controls draw them — a layer's color is edited by
 * exactly the control a design's "Accent" is, and the two cannot drift into different pickers.
 */
internal sealed interface LayerField {
    /** Unique within one layer's fields; what keeps a control's open state attached to the right row. */
    val key: String

    /** A property drawn by a Style control: [control] carries its label and value, [apply] writes a new one back. */
    data class Setting(
        override val key: String,
        val control: WidgetGlobal,
        val apply: (WidgetLayerSpec, WidgetGlobal) -> WidgetLayerSpec,
    ) : LayerField

    /** Formula source, typed as text and shown with what the parser makes of it. */
    data class Formula(
        override val key: String,
        val label: String,
        val source: String,
        val apply: (WidgetLayerSpec, String) -> WidgetLayerSpec,
    ) : LayerField

    /**
     * A property a setting decides. It gets no control — editing it would change nothing while the setting wins — only
     * the setting's name and a way to let go of it.
     */
    data class Bound(
        override val key: String,
        val label: String,
        val setting: String,
        val unbind: (WidgetLayerSpec) -> WidgetLayerSpec,
    ) : LayerField
}

/**
 * Everything the Advanced view edits on [layer]: what it draws first — a text layer is opened to change its text —
 * then where it sits and how, and its name last.
 *
 * @param scope the settings in scope where the layer sits, which decides whether a binding on it is live.
 */
internal fun layerFields(layer: WidgetLayerSpec, scope: WidgetGlobals): List<LayerField> =
    when (val source = layer.source) {
        is WidgetSource.Text -> textFields(source, scope)
        is WidgetSource.Shape -> shapeFields(source, scope)
        is WidgetSource.Progress -> progressFields(source, scope)
        is WidgetSource.Overlap, is WidgetSource.Image -> emptyList()
    } + placementFields(layer) +
        text("name", "Name", layer.name.orEmpty()) { l, value -> l.copy(name = value.ifBlank { null }) }

private fun placementFields(layer: WidgetLayerSpec): List<LayerField> = listOf(
    switch("visible", "Visible", layer.visible) { l, value -> l.copy(visible = value) },
    choice("across", "Across", Across, layer.anchor.across) { l, i -> l.copy(anchor = anchorOf(i, l.anchor.down)) },
    choice("down", "Down", Down, layer.anchor.down) { l, i -> l.copy(anchor = anchorOf(l.anchor.across, i)) },
    number("x", "Offset across", layer.offsetX, -Reach, Reach) { l, value -> l.copy(offsetX = value) },
    number("y", "Offset down", layer.offsetY, -Reach, Reach) { l, value -> l.copy(offsetY = value) },
) + extentFields("width", "Width", layer.width) { l, extent -> l.copy(width = extent) } +
    extentFields("height", "Height", layer.height) { l, extent -> l.copy(height = extent) } + listOf(
        number("scale", "Scale %", layer.scale * Percent, MinScale, MaxScale) { l, value -> l.copy(scale = value / Percent) },
        number("rotation", "Rotation", layer.rotation, -HalfTurn, HalfTurn) { l, value -> l.copy(rotation = value) },
        number("opacity", "Opacity %", layer.opacity * Percent, 0f, Percent) { l, value ->
            l.copy(opacity = value / Percent)
        },
    )

/** How big along one axis: its kind, and — for a fixed size or a share — how much, which a fitted size has not. */
private fun extentFields(
    key: String,
    label: String,
    extent: WidgetExtent,
    apply: (WidgetLayerSpec, WidgetExtent) -> WidgetLayerSpec,
): List<LayerField> {
    val kind = choice(key, label, listOf("Fit", "Fixed", "Share"), extent.kind) { layer, i ->
        apply(layer, if (i == extent.kind) extent else extentOfKind(i))
    }
    return listOfNotNull(
        kind,
        when (extent) {
            WidgetExtent.Content -> null
            is WidgetExtent.Dp -> number("$key.dp", "$label (dp)", extent.value, 0f, MaxDp) { l, v ->
                apply(l, WidgetExtent.Dp(v))
            }
            is WidgetExtent.Fraction -> number("$key.share", "$label %", extent.value * Percent, 0f, Percent) { l, v ->
                apply(l, WidgetExtent.Fraction(v / Percent))
            }
        },
    )
}

private fun textFields(source: WidgetSource.Text, scope: WidgetGlobals): List<LayerField> = listOf(
    LayerField.Formula("text", "Text", source.text) { l, v -> l.withText { it.copy(text = v) } },
    boundOr(scope, source.sizeGlobal, "size", "Size", { l -> l.withText { it.copy(sizeGlobal = null) } }) {
        number("size", "Size", source.size, MinText, MaxText) { l, v -> l.withText { it.copy(size = v) } }
    },
    number("weight", "Weight", source.weight.toFloat(), MinWeight, MaxWeight) { l, v ->
        l.withText { it.copy(weight = (v / WeightStep).roundToInt() * WeightStep.toInt()) }
    },
    boundOr(scope, source.colorGlobal, "color", "Color", { l -> l.withText { it.copy(colorGlobal = null) } }) {
        color("color", "Color", source.color) { l, v -> l.withText { it.copy(color = v) } }
    },
    boundOr(scope, source.fontGlobal, "font", "Font", { l -> l.withText { it.copy(fontGlobal = null) } }) {
        LayerField.Setting("font", WidgetGlobal.Font("font", "Font", source.font)) { l, g ->
            l.withText { it.copy(font = (g as WidgetGlobal.Font).value) }
        }
    },
    choice("align", "Align", listOf("Left", "Center", "Right"), source.align.ordinal) { l, i ->
        l.withText { it.copy(align = WidgetSource.Text.Align.entries[i]) }
    },
    number("lines", "Lines", source.maxLines.toFloat(), 1f, MaxLines) { l, v ->
        l.withText { it.copy(maxLines = v.roundToInt()) }
    },
)

private fun shapeFields(source: WidgetSource.Shape, scope: WidgetGlobals): List<LayerField> = listOf(
    choice("kind", "Shape", listOf("Rectangle", "Oval"), source.kind.ordinal) { l, i ->
        l.withShape { it.copy(kind = WidgetSource.Shape.Kind.entries[i]) }
    },
    boundOr(scope, source.colorGlobal, "color", "Color", { l -> l.withShape { it.copy(colorGlobal = null) } }) {
        color("color", "Color", source.color) { l, v -> l.withShape { it.copy(color = v) } }
    },
    boundOr(
        scope,
        source.cornerRadiusGlobal,
        "corner",
        "Corner radius",
        { l -> l.withShape { it.copy(cornerRadiusGlobal = null) } },
    ) {
        number("corner", "Corner radius", source.cornerRadius, 0f, MaxCorner) { l, v ->
            l.withShape { it.copy(cornerRadius = v) }
        }
    },
)

private fun progressFields(source: WidgetSource.Progress, scope: WidgetGlobals): List<LayerField> = listOf(
    LayerField.Formula("value", "Value", source.value) { l, v -> l.withProgress { it.copy(value = v) } },
    choice("kind", "Kind", listOf("Bar", "Arc"), source.kind.ordinal) { l, i ->
        l.withProgress { it.copy(kind = WidgetSource.Progress.Kind.entries[i]) }
    },
    number("min", "From", source.min, 0f, MaxRange) { l, v -> l.withProgress { it.copy(min = v) } },
    number("max", "To", source.max, 0f, MaxRange) { l, v -> l.withProgress { it.copy(max = v) } },
    boundOr(scope, source.colorGlobal, "color", "Color", { l -> l.withProgress { it.copy(colorGlobal = null) } }) {
        color("color", "Color", source.color) { l, v -> l.withProgress { it.copy(color = v) } }
    },
    boundOr(scope, source.trackColorGlobal, "track", "Track", { l -> l.withProgress { it.copy(trackColorGlobal = null) } }) {
        color("track", "Track", source.trackColor) { l, v -> l.withProgress { it.copy(trackColor = v) } }
    },
) + if (source.kind == WidgetSource.Progress.Kind.ARC) {
    listOf(
        number("thickness", "Thickness", source.thickness, 1f, MaxThickness) { l, v ->
            l.withProgress { it.copy(thickness = v) }
        },
        number("start", "Start angle", source.startAngle, -HalfTurn, HalfTurn) { l, v ->
            l.withProgress { it.copy(startAngle = v) }
        },
        number("sweep", "Sweep", source.sweep, MinSweep, FullTurn) { l, v -> l.withProgress { it.copy(sweep = v) } },
        switch("rounded", "Round ends", source.rounded) { l, v -> l.withProgress { it.copy(rounded = v) } },
    )
} else {
    listOf(switch("rounded", "Round ends", source.rounded) { l, v -> l.withProgress { it.copy(rounded = v) } })
}

/**
 * [editable] unless [binding] names a setting in scope — then the property is that setting's, and shows as bound. A
 * binding that names nothing is broken and draws the property's own value, so it is edited like any other.
 */
private fun boundOr(
    scope: WidgetGlobals,
    binding: String?,
    key: String,
    label: String,
    unbind: (WidgetLayerSpec) -> WidgetLayerSpec,
    editable: () -> LayerField,
): LayerField = scope[binding]?.let { LayerField.Bound(key, label, it.label, unbind) } ?: editable()

@Suppress("LongParameterList") // A slider's label, value and range, and where the value goes.
private fun number(
    key: String,
    label: String,
    value: Float,
    min: Float,
    max: Float,
    apply: (WidgetLayerSpec, Float) -> WidgetLayerSpec,
) =
    LayerField.Setting(key, WidgetGlobal.Number(key, label, value.coerceIn(min, max), min, max)) { layer, global ->
        apply(layer, (global as WidgetGlobal.Number).value)
    }

private fun switch(key: String, label: String, value: Boolean, apply: (WidgetLayerSpec, Boolean) -> WidgetLayerSpec) =
    LayerField.Setting(key, WidgetGlobal.Switch(key, label, value)) { layer, global ->
        apply(layer, (global as WidgetGlobal.Switch).value)
    }

private fun choice(
    key: String,
    label: String,
    options: List<String>,
    selected: Int,
    apply: (WidgetLayerSpec, Int) -> WidgetLayerSpec,
) =
    LayerField.Setting(key, WidgetGlobal.Choice(key, label, options, selected)) { layer, global ->
        apply(layer, (global as WidgetGlobal.Choice).selected)
    }

private fun color(key: String, label: String, value: Int, apply: (WidgetLayerSpec, Int) -> WidgetLayerSpec) =
    LayerField.Setting(key, WidgetGlobal.Color(key, label, value)) { layer, global ->
        apply(layer, (global as WidgetGlobal.Color).value)
    }

private fun text(key: String, label: String, value: String, apply: (WidgetLayerSpec, String) -> WidgetLayerSpec) =
    LayerField.Setting(key, WidgetGlobal.Text(key, label, value)) { layer, global ->
        apply(layer, (global as WidgetGlobal.Text).value)
    }

private fun WidgetLayerSpec.withText(change: (WidgetSource.Text) -> WidgetSource.Text) =
    (source as? WidgetSource.Text)?.let { copy(source = change(it)) } ?: this

private fun WidgetLayerSpec.withShape(change: (WidgetSource.Shape) -> WidgetSource.Shape) =
    (source as? WidgetSource.Shape)?.let { copy(source = change(it)) } ?: this

private fun WidgetLayerSpec.withProgress(change: (WidgetSource.Progress) -> WidgetSource.Progress) =
    (source as? WidgetSource.Progress)?.let { copy(source = change(it)) } ?: this

/** The anchor's column: 0 left, 1 center, 2 right. */
private val WidgetAnchor.across: Int get() = ordinal % Sides

/** The anchor's row: 0 top, 1 middle, 2 bottom. */
private val WidgetAnchor.down: Int get() = ordinal / Sides

/** `WidgetAnchor` is declared row by row, three to a row, which is what makes this and the two above exact. */
private fun anchorOf(across: Int, down: Int): WidgetAnchor = WidgetAnchor.entries[down * Sides + across]

private val WidgetExtent.kind: Int
    get() = when (this) {
        WidgetExtent.Content -> 0
        is WidgetExtent.Dp -> 1
        is WidgetExtent.Fraction -> 2
    }

/** A starting value for an extent switched to another kind: a size to adjust from, not a guess at the right one. */
private fun extentOfKind(kind: Int): WidgetExtent = when (kind) {
    1 -> WidgetExtent.Dp(DefaultDp)
    2 -> WidgetExtent.Fill
    else -> WidgetExtent.Content
}

private val Across = listOf("Left", "Center", "Right")
private val Down = listOf("Top", "Middle", "Bottom")
private const val Sides = 3
private const val Percent = 100f

/** The renderer's own limits, as percentages, so the slider cannot set a scale that would silently draw as another. */
private val MinScale = WidgetScales.start * Percent
private val MaxScale = WidgetScales.endInclusive * Percent
private const val Reach = 300f
private const val MaxDp = 400f
private const val DefaultDp = 80f
private const val HalfTurn = 180f
private const val FullTurn = 360f
private const val MinSweep = 10f
private const val MinText = 6f
private const val MaxText = 160f
private const val MinWeight = 100f
private const val MaxWeight = 900f
private const val WeightStep = 100f
private const val MaxLines = 6f
private const val MaxCorner = 100f
private const val MaxRange = 1440f
private const val MaxThickness = 40f
