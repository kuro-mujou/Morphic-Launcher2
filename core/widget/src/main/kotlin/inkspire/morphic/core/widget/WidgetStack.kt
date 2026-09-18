package inkspire.morphic.core.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.drawn
import inkspire.morphic.core.widgetscript.ScriptData
import kotlin.math.roundToInt

/**
 * A [WidgetSource.Stack]: its visible layers one after another along its axis, each sized by its own extents against
 * the stack and aligned across it by the stack's [WidgetSource.Stack.align]. Takes an exact size when given one,
 * otherwise just the room its layers need.
 *
 * A layer is drawn by the same [WidgetLayer] a free group uses — rotation, opacity and scale included — and scale
 * counts toward the room it takes, as it does there.
 */
@Composable
internal fun WidgetStack(source: WidgetSource.Stack, data: ScriptData, globals: WidgetGlobals) {
    val visible = source.layers.drawn(globals)
    val vertical = source.axis == WidgetSource.Stack.Axis.VERTICAL
    Layout(content = { visible.forEach { WidgetLayer(it, data, globals) } }) { measurables, constraints ->
        val scales = FloatArray(visible.size) { WidgetPlacement.scaleOf(visible[it]) }
        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(layerMeasureConstraints(visible[i], constraints, density, scales[i]))
        }
        val widths = IntArray(placeables.size) { WidgetPlacement.scaled(placeables[it].width, scales[it]) }
        val heights = IntArray(placeables.size) { WidgetPlacement.scaled(placeables[it].height, scales[it]) }
        val spacing = (source.spacing * density).roundToInt()
        val (length, starts) = WidgetPlacement.stackMain(if (vertical) heights else widths, spacing)
        val cross = if (vertical) widths else heights
        val crossMax = cross.maxOrNull() ?: 0
        val stackWidth = constraints.constrainWidth(
            if (constraints.hasFixedWidth) constraints.maxWidth else if (vertical) crossMax else length,
        )
        val stackHeight = constraints.constrainHeight(
            if (constraints.hasFixedHeight) constraints.maxHeight else if (vertical) length else crossMax,
        )
        val bias = source.align.bias
        layout(stackWidth, stackHeight) {
            placeables.forEachIndexed { i, placeable ->
                val across = WidgetPlacement.position(if (vertical) stackWidth else stackHeight, cross[i], bias, 0f)
                val x = if (vertical) across else starts[i]
                val y = if (vertical) starts[i] else across
                placeLayer(placeable, x, y, scales[i])
            }
        }
    }
}

/** How far across the stack's cross axis a layer sits: 0 at its start, 1 at its end. */
private val WidgetSource.Stack.Align.bias: Float
    get() = when (this) {
        WidgetSource.Stack.Align.START -> 0f
        WidgetSource.Stack.Align.CENTER -> Half
        WidgetSource.Stack.Align.END -> 1f
    }

private const val Half = 0.5f

