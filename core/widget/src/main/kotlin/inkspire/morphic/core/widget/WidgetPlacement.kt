package inkspire.morphic.core.widget

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import kotlin.math.roundToInt

/**
 * Where a layer goes inside its parent, as plain arithmetic on pixels — the part of the renderer that has to be exactly
 * right, since the editor's drag will resolve a finger position back into an anchor and offset through the inverse
 * of it, and any disagreement between the two is a layer that jumps when it is let go.
 */
internal object WidgetPlacement {

    /** The bias of an anchor on neither edge. */
    private const val Middle = 0.5f

    /**
     * The size [extent] asks for along one axis, in pixels, or null when it asks for its content's size.
     *
     * A [WidgetExtent.Fraction] of an unbounded parent has nothing to be a fraction of, so it falls back to the
     * content's size rather than to infinity.
     *
     * @param available the parent's size along this axis, in pixels — `Int.MAX_VALUE` when unbounded.
     */
    fun extentPx(extent: WidgetExtent, available: Int, density: Float): Int? = when (extent) {
        WidgetExtent.Content -> null
        is WidgetExtent.Dp -> (extent.value * density).roundToInt().coerceAtLeast(0)
        is WidgetExtent.Fraction -> {
            if (available == Int.MAX_VALUE) null else (available * extent.value).roundToInt().coerceAtLeast(0)
        }
    }

    /** How far across the parent [anchor] sits: 0 at the left edge, 1 at the right. */
    fun horizontalBias(anchor: WidgetAnchor): Float = when (anchor) {
        WidgetAnchor.TOP_LEFT, WidgetAnchor.LEFT, WidgetAnchor.BOTTOM_LEFT -> 0f
        WidgetAnchor.TOP, WidgetAnchor.CENTER, WidgetAnchor.BOTTOM -> Middle
        WidgetAnchor.TOP_RIGHT, WidgetAnchor.RIGHT, WidgetAnchor.BOTTOM_RIGHT -> 1f
    }

    /** How far down the parent [anchor] sits: 0 at the top edge, 1 at the bottom. */
    fun verticalBias(anchor: WidgetAnchor): Float = when (anchor) {
        WidgetAnchor.TOP_LEFT, WidgetAnchor.TOP, WidgetAnchor.TOP_RIGHT -> 0f
        WidgetAnchor.LEFT, WidgetAnchor.CENTER, WidgetAnchor.RIGHT -> Middle
        WidgetAnchor.BOTTOM_LEFT, WidgetAnchor.BOTTOM, WidgetAnchor.BOTTOM_RIGHT -> 1f
    }

    /**
     * The child's leading edge along one axis: its own anchor point laid on the parent's, then moved by [offsetPx].
     * A child larger than its parent overhangs both sides by the same share the bias gives, as a centered one should.
     */
    fun position(parent: Int, child: Int, bias: Float, offsetPx: Float): Int =
        (bias * (parent - child) + offsetPx).roundToInt()

    /**
     * One axis of a group sized by its content: every child placed by [position] inside a box as big as the largest
     * of them, and then the box shrunk to exactly what they cover — offsets included — with the children moved with
     * it. So a block whose second line sits 20dp below its first is a box around both lines, not around the wider one.
     *
     * @return the group's size and each child's leading edge within it.
     */
    fun hug(sizes: IntArray, biases: FloatArray, offsetsPx: FloatArray): Pair<Int, IntArray> {
        if (sizes.isEmpty()) return 0 to IntArray(0)
        val box = sizes.max()
        val starts = IntArray(sizes.size) { position(box, sizes[it], biases[it], offsetsPx[it]) }
        val min = starts.min()
        val max = sizes.indices.maxOf { starts[it] + sizes[it] }
        return (max - min) to IntArray(sizes.size) { starts[it] - min }
    }
}
