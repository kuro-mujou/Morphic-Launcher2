package inkspire.morphic.core.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.drawn
import inkspire.morphic.core.widgetscript.ScriptData

/**
 * A free-placement group: every visible layer sized by its extents and pinned by its anchor and offset, later layers
 * over earlier ones. The widget itself is one of these, and so is a [inkspire.morphic.core.model.widget.WidgetSource.Overlap].
 *
 * Takes the size it is given when that is exact; otherwise it is as big as its largest layer, as a `Box` would be.
 * Layers are not clipped to it — only the widget as a whole is.
 */
@Composable
internal fun WidgetOverlap(layers: List<WidgetLayerSpec>, data: ScriptData, modifier: Modifier = Modifier) {
    val visible = layers.drawn()
    Layout(
        content = { visible.forEach { WidgetLayer(it, data) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(layerConstraints(visible[i], constraints, density))
        }
        val width = if (constraints.hasFixedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = if (constraints.hasFixedHeight) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        val groupWidth = constraints.constrainWidth(width)
        val groupHeight = constraints.constrainHeight(height)
        layout(groupWidth, groupHeight) {
            placeables.forEachIndexed { i, placeable ->
                val spec = visible[i]
                placeable.place(
                    x = WidgetPlacement.position(
                        groupWidth, placeable.width, WidgetPlacement.horizontalBias(spec.anchor), spec.offsetX * density,
                    ),
                    y = WidgetPlacement.position(
                        groupHeight, placeable.height, WidgetPlacement.verticalBias(spec.anchor), spec.offsetY * density,
                    ),
                )
            }
        }
    }
}

/** An exact size on each axis the layer's extent fixes; up to the group's own bound on each it leaves to content. */
private fun layerConstraints(spec: WidgetLayerSpec, group: Constraints, density: Float): Constraints {
    val width = WidgetPlacement.extentPx(spec.width, group.maxWidth, density)
    val height = WidgetPlacement.extentPx(spec.height, group.maxHeight, density)
    return Constraints(
        minWidth = width ?: 0,
        maxWidth = width ?: group.maxWidth,
        minHeight = height ?: 0,
        maxHeight = height ?: group.maxHeight,
    )
}

/**
 * One layer, always exactly one node — an image still loading draws nothing but still occupies its slot, so the
 * group's measurables stay in step with its layer list.
 */
@Composable
private fun WidgetLayer(spec: WidgetLayerSpec, data: ScriptData) {
    Box(
        modifier = Modifier.graphicsLayer {
            rotationZ = spec.rotation
            alpha = spec.opacity.coerceIn(0f, 1f)
        },
        propagateMinConstraints = true,
    ) {
        WidgetSourceContent(spec.source, data)
    }
}
