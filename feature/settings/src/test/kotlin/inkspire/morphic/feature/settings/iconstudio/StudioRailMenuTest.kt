package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.ui.unit.IntRect
import inkspire.morphic.core.designsystem.menu.MenuPlacement
import inkspire.morphic.data.settings.LayerRailAxis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which side the rail's menu opens on.
 *
 * **Tested because a menu on the wrong side is still a menu.** It draws, its rows work, and the only symptom is that
 * it covers the tiles it is about — which is exactly the sort of thing that reads as "fine" in a screenshot and wrong
 * in the hand. The rule also has to hold for a rail the *user* has dragged anywhere and turned on its side, so there
 * is no resting arrangement to eyeball it against.
 */
class StudioRailMenuTest {

    private val frame = IntRect(left = 0, top = 0, right = 1000, bottom = 2000)

    /** A vertical rail: narrow and tall, at [left]. */
    private fun column(left: Int) = IntRect(left = left, top = 200, right = left + 60, bottom = 800)

    /** A horizontal rail: wide and short, at [top]. */
    private fun row(top: Int) = IntRect(left = 200, top = top, right = 800, bottom = top + 60)

    @Test
    fun `a column at the end edge opens toward the canvas`() {
        assertEquals(
            MenuPlacement.LEFT,
            railMenuPlacement(column(left = 900), frame, LayerRailAxis.VERTICAL),
        )
    }

    @Test
    fun `a column dragged to the start edge opens the other way`() {
        // The case the old arrangement could not express at all: the menu was a fixed leading sibling of the rail, so
        // dragging the rail to the left edge put its menu off the screen.
        assertEquals(
            MenuPlacement.RIGHT,
            railMenuPlacement(column(left = 20), frame, LayerRailAxis.VERTICAL),
        )
    }

    @Test
    fun `a row opens across itself, not beside itself`() {
        // **The one the shared `menuPlacementFor` would get wrong.** That rule picks its axis from the *screen's*
        // shape, so on this tall frame it would answer BELOW for both of these — over the tiles of a vertical rail,
        // and only accidentally right for a horizontal one.
        assertEquals(MenuPlacement.BELOW, railMenuPlacement(row(top = 100), frame, LayerRailAxis.HORIZONTAL))
        assertEquals(MenuPlacement.ABOVE, railMenuPlacement(row(top = 1800), frame, LayerRailAxis.HORIZONTAL))
    }

    @Test
    fun `the axis follows the rail even on a wide frame`() {
        // A landscape window, where the shared rule would flip to beside-the-item. The rail's own direction is what
        // decides here, so a vertical rail still opens sideways and a horizontal one still opens above or below.
        val wide = IntRect(left = 0, top = 0, right = 2000, bottom = 1000)
        val railOnTheRight = IntRect(left = 1900, top = 100, right = 1960, bottom = 700)
        val railAcrossTheTop = IntRect(left = 400, top = 40, right = 1600, bottom = 100)

        assertEquals(MenuPlacement.LEFT, railMenuPlacement(railOnTheRight, wide, LayerRailAxis.VERTICAL))
        assertEquals(MenuPlacement.BELOW, railMenuPlacement(railAcrossTheTop, wide, LayerRailAxis.HORIZONTAL))
    }

    @Test
    fun `the decision is made on the rail's center, not its near edge`() {
        // A wide-ish rail straddling the midline. Judging by `left` would flip it a whole rail's width early, which
        // is the difference between a menu that has room and one that is clamped back over the rail.
        val straddling = IntRect(left = 450, top = 200, right = 620, bottom = 800)
        // Center is 535, past the midline of 500, so the menu takes the roomier side: the left.
        assertEquals(MenuPlacement.LEFT, railMenuPlacement(straddling, frame, LayerRailAxis.VERTICAL))
    }
}
