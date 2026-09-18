package inkspire.morphic.core.widget

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetLayerSpec

/**
 * This layer re-pinned so it draws exactly at [box] inside a parent of [parent] size: anchored to whichever of the
 * nine anchors its center is nearest, with the offset that puts it there. What a drag resolves to when it is let go.
 *
 * **The inverse of [WidgetPlacement.position]**, and it has to be exact — any disagreement between the two is a block
 * that jumps when it is released. Choosing the anchor by where the block ended up is what keeps a design re-laying well:
 * a block dropped in a corner stays in that corner when the widget is resized.
 *
 * @param box where the layer is drawn, in the parent's pixels, as `WidgetRender`'s `onLayout` reports it.
 */
fun WidgetLayerSpec.movedTo(box: IntRect, parent: IntSize, density: Float): WidgetLayerSpec {
    val column = third(box.left + box.width / 2f, parent.width)
    val row = third(box.top + box.height / 2f, parent.height)
    val anchor = Anchors[row][column]
    return copy(
        anchor = anchor,
        offsetX = offsetFor(box.left, box.width, parent.width, WidgetPlacement.horizontalBias(anchor)) / density,
        offsetY = offsetFor(box.top, box.height, parent.height, WidgetPlacement.verticalBias(anchor)) / density,
    )
}

/**
 * This layer [factor] times bigger than it is drawn now — a pinch — held to the scales the renderer will draw, so a
 * pinch past the limit stops rather than storing a value that silently draws as something else.
 */
fun WidgetLayerSpec.scaledBy(factor: Float): WidgetLayerSpec =
    copy(scale = (WidgetPlacement.scaleOf(this) * factor).coerceIn(WidgetPlacement.MinScale, WidgetPlacement.MaxScale))

/** Which third of [size] [center] falls in: 0, 1 or 2. */
private fun third(center: Float, size: Int): Int =
    if (size <= 0) 1 else (center / size * Thirds).toInt().coerceIn(0, Thirds - 1)

/** How many ways each axis is split when choosing an anchor: its start, its middle and its end. */
private const val Thirds = 3

/** The offset that lands a [child]-sized box's leading edge on [start], given the anchor's [bias]. */
private fun offsetFor(start: Int, child: Int, parent: Int, bias: Float): Float = start - bias * (parent - child)

/** The anchors by row, then column — the third a block's center lands in, down and across. */
private val Anchors = listOf(
    listOf(WidgetAnchor.TOP_LEFT, WidgetAnchor.TOP, WidgetAnchor.TOP_RIGHT),
    listOf(WidgetAnchor.LEFT, WidgetAnchor.CENTER, WidgetAnchor.RIGHT),
    listOf(WidgetAnchor.BOTTOM_LEFT, WidgetAnchor.BOTTOM, WidgetAnchor.BOTTOM_RIGHT),
)
