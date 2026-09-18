package inkspire.morphic.core.widget

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import inkspire.morphic.core.model.widget.LayerPath
import inkspire.morphic.core.model.widget.WidgetTap

/**
 * The layers of one drawn widget that answer a tap, and which of them a point lands on — what HOME asks when its
 * gesture machine reports a completed tap on the widget.
 *
 * **Each layer is held as its live [LayoutCoordinates], not a rectangle.** A widget on HOME sits in a pager, and a
 * rectangle published from layout goes stale the moment the page moves under a finger; a point is instead resolved
 * through the coordinates as they are when asked, rotation and scale included.
 *
 * Written from composition and read on the pointer thread, so it is a plain map: nothing draws from it.
 */
class WidgetTapTargets {
    private val targets = mutableMapOf<LayerPath, Target>()

    internal fun set(path: LayerPath, tap: WidgetTap, coordinates: LayoutCoordinates) {
        targets[path] = Target(tap, coordinates)
    }

    internal fun remove(path: LayerPath) {
        targets.remove(path)
    }

    /**
     * The topmost layer with a tap under [point], and what it does — or null when the point lands on none.
     *
     * @param point in [within]'s space, which must be the same tree the layers were laid out in.
     */
    fun at(point: Offset, within: LayoutCoordinates): Pair<LayerPath, WidgetTap>? {
        val hits = targets.filter { (_, target) ->
            val coordinates = target.coordinates
            if (!coordinates.isAttached || !within.isAttached) return@filter false
            val local = coordinates.localPositionOf(within, point)
            local.x in 0f..coordinates.size.width.toFloat() && local.y in 0f..coordinates.size.height.toFloat()
        }
        return topmost(hits.keys)?.let { it to hits.getValue(it).tap }
    }

    private class Target(val tap: WidgetTap, val coordinates: LayoutCoordinates)
}

/**
 * Of [paths], the one drawn on top. Later layers draw over earlier ones and a group's layers over the group, which is
 * exactly the order of the paths compared index by index, a path before any path it is the start of.
 */
internal fun topmost(paths: Collection<LayerPath>): LayerPath? = paths.maxWithOrNull(PathOrder)

private val PathOrder = Comparator<LayerPath> { a, b ->
    a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)
}

/** Where the layers being composed register their taps, when the widget is being drawn somewhere that runs them. */
internal val LocalTapTargets = compositionLocalOf<WidgetTapTargets?> { null }

/** The path of the group being composed, so each layer knows its own. */
internal val LocalLayerPath = compositionLocalOf<LayerPath> { emptyList() }
