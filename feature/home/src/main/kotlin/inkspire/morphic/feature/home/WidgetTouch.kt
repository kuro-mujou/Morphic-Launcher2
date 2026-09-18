package inkspire.morphic.feature.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import inkspire.morphic.core.model.widget.LayerPath
import inkspire.morphic.core.model.widget.WidgetTap
import inkspire.morphic.core.widget.WidgetTapTargets

/**
 * Which part of which widget a tap on HOME landed on — the bridge between the gesture machine, which knows only that
 * a widget was tapped, and the widget, which knows where it was pressed and what each of its parts does.
 *
 * **The press is recorded by the widget itself, not reported by the machine.** Every tap arrives through the one
 * gesture contract, which is what keeps a long press from also tapping and a drag from tapping on drop; the widget
 * only *watches* where each finger comes down, never consuming it, so everything else that contract decides stays as
 * it is. One per surface, provided to the widget cells under it.
 */
internal class WidgetTouch {
    private val cells = mutableMapOf<Long, Cell>()

    /** The record for widget [id], created on first use. */
    fun cell(id: Long): Cell = cells.getOrPut(id) { Cell() }

    /** Forgets widget [id], once its cell has left the screen. */
    fun release(id: Long) {
        cells.remove(id)
    }

    /** What the last press on widget [id] landed on, when it landed on a part that answers a tap. */
    fun tapped(id: Long): Pair<LayerPath, WidgetTap>? {
        val cell = cells[id] ?: return null
        val coordinates = cell.coordinates ?: return null
        val press = cell.press ?: return null
        return cell.targets.at(press, coordinates)
    }

    /**
     * One widget's side of it.
     *
     * @property press where the last finger came down, in [coordinates]' space.
     */
    class Cell {
        val targets = WidgetTapTargets()
        var coordinates: LayoutCoordinates? = null
        var press: Offset? = null
    }
}

/** The surface's [WidgetTouch], for the widget cells it draws; null where widgets are not tapped, as in the picker. */
internal val LocalWidgetTouch = staticCompositionLocalOf<WidgetTouch?> { null }
