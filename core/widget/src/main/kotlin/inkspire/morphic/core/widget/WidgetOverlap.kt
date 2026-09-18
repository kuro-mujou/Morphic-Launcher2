package inkspire.morphic.core.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.drawn
import inkspire.morphic.core.widgetscript.ScriptData

/**
 * A free-placement group: every visible layer sized by its extents and pinned by its anchor and offset, later layers
 * over earlier ones. The widget itself is one of these, and so is a [inkspire.morphic.core.model.widget.WidgetSource.Overlap].
 *
 * Takes the size it is given when that is exact; otherwise it is exactly as big as what its layers cover, offsets
 * included. Layers are not clipped to it — only the widget as a whole is.
 */
@Composable
internal fun WidgetOverlap(
    layers: List<WidgetLayerSpec>,
    data: ScriptData,
    globals: WidgetGlobals,
    modifier: Modifier = Modifier,
    onLayout: ((Map<Int, IntRect>) -> Unit)? = null,
) {
    val visible = layers.drawn(globals)
    Layout(
        content = { visible.forEach { WidgetLayer(it, data, globals) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(layerConstraints(visible[i], constraints, density))
        }
        val xs = axis(
            fixed = constraints.hasFixedWidth,
            size = constraints.maxWidth,
            sizes = IntArray(placeables.size) { placeables[it].width },
            biases = FloatArray(visible.size) { WidgetPlacement.horizontalBias(visible[it].anchor) },
            offsetsPx = FloatArray(visible.size) { visible[it].offsetX * density },
        )
        val ys = axis(
            fixed = constraints.hasFixedHeight,
            size = constraints.maxHeight,
            sizes = IntArray(placeables.size) { placeables[it].height },
            biases = FloatArray(visible.size) { WidgetPlacement.verticalBias(visible[it].anchor) },
            offsetsPx = FloatArray(visible.size) { visible[it].offsetY * density },
        )
        layout(constraints.constrainWidth(xs.first), constraints.constrainHeight(ys.first)) {
            placeables.forEachIndexed { i, placeable -> placeable.place(xs.second[i], ys.second[i]) }
            onLayout?.invoke(
                // `drawn` filters without copying, so each visible layer is found in the full list by identity — which
                // two equal layers would defeat if it were by equality.
                visible.indices.associate { i ->
                    layers.indexOfFirst { it === visible[i] } to
                        IntRect(IntOffset(xs.second[i], ys.second[i]), IntSize(placeables[i].width, placeables[i].height))
                },
            )
        }
    }
}

/**
 * One axis of the group: its size and each layer's leading edge. A size the group was given exactly places the layers
 * within it; otherwise the group [hugs][WidgetPlacement.hug] them.
 */
private fun axis(fixed: Boolean, size: Int, sizes: IntArray, biases: FloatArray, offsetsPx: FloatArray): Pair<Int, IntArray> =
    if (fixed) {
        size to IntArray(sizes.size) { WidgetPlacement.position(size, sizes[it], biases[it], offsetsPx[it]) }
    } else {
        WidgetPlacement.hug(sizes, biases, offsetsPx)
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
private fun WidgetLayer(spec: WidgetLayerSpec, data: ScriptData, globals: WidgetGlobals) {
    Box(
        modifier = Modifier.graphicsLayer {
            rotationZ = spec.rotation
            alpha = spec.opacity.coerceIn(0f, 1f)
        },
        propagateMinConstraints = true,
    ) {
        WidgetSourceContent(spec.source, data, globals)
    }
}
