package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour spec for [ItemGestureMachine] — one test per branch of the launcher's gesture contract
 * (docs/DRAG_AND_DROP_DESIGN.md §5). Slop is 10px, so an offset of 20 is "past slop" and 5 is a wobble.
 */
class ItemGestureMachineTest {

    private val slop = 10f
    private fun machine() =
        ItemGestureMachine(ItemGestureConfig(touchSlopPx = slop, longPressTimeoutMillis = 400L))

    private val wobble = Offset(3f, 3f)          // under slop
    private fun far(x: Float, y: Float) = Offset(x, y)  // past slop

    @Test
    fun `tap - down then quick up opens the item`() {
        val m = machine()
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.Down))
        assertEquals(listOf(ItemGestureEffect.OpenItem), m.onEvent(ItemGestureEvent.Up))
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }

    @Test
    fun `a wobble under slop keeps the press pending so long-press can still fire`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.Move(wobble)))
        assertEquals(ItemGesturePhase.Pressed, m.phase)
    }

    @Test
    fun `press and swipe right fires the edge action on release`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.Move(far(30f, 2f))))
        assertEquals(ItemGesturePhase.Swiped(SwipeDirection.RIGHT), m.phase)
        assertEquals(
            listOf(ItemGestureEffect.EdgeAction(SwipeDirection.RIGHT)),
            m.onEvent(ItemGestureEvent.Up),
        )
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }

    @Test
    fun `swipe classifies each cardinal direction by its dominant axis`() {
        fun swipe(offset: Offset): SwipeDirection {
            val m = machine()
            m.onEvent(ItemGestureEvent.Down)
            m.onEvent(ItemGestureEvent.Move(offset))
            return (m.phase as ItemGesturePhase.Swiped).direction
        }
        assertEquals(SwipeDirection.RIGHT, swipe(far(30f, 5f)))
        assertEquals(SwipeDirection.LEFT, swipe(far(-30f, 5f)))
        assertEquals(SwipeDirection.DOWN, swipe(far(5f, 30f)))
        assertEquals(SwipeDirection.UP, swipe(far(5f, -30f)))
    }

    @Test
    fun `a committed swipe locks its direction against later movement`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.Move(far(30f, 0f)))          // commit RIGHT
        m.onEvent(ItemGestureEvent.Move(far(0f, 30f)))          // now mostly down
        assertEquals(ItemGesturePhase.Swiped(SwipeDirection.RIGHT), m.phase)
        assertEquals(
            listOf(ItemGestureEffect.EdgeAction(SwipeDirection.RIGHT)),
            m.onEvent(ItemGestureEvent.Up),
        )
    }

    @Test
    fun `long-press shows the menu`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        assertEquals(listOf(ItemGestureEffect.ShowMenu), m.onEvent(ItemGestureEvent.LongPress))
        assertEquals(ItemGesturePhase.MenuOpen, m.phase)
    }

    @Test
    fun `releasing with the menu open dismisses it and does NOT fire a tap`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.LongPress)
        val effects = m.onEvent(ItemGestureEvent.Up)
        assertEquals(listOf(ItemGestureEffect.DismissMenu), effects)
        assertTrue("must not open the item", ItemGestureEffect.OpenItem !in effects)
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }

    @Test
    fun `moving after the menu opens dismisses it and begins a drag`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.LongPress)
        val move = far(0f, 40f)
        assertEquals(
            listOf(
                ItemGestureEffect.DismissMenu,
                ItemGestureEffect.BeginDrag,
                ItemGestureEffect.DragTo(move),
            ),
            m.onEvent(ItemGestureEvent.Move(move)),
        )
        assertEquals(ItemGesturePhase.Dragging, m.phase)
    }

    @Test
    fun `a wobble with the menu open does not start a drag`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.LongPress)
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.Move(wobble)))
        assertEquals(ItemGesturePhase.MenuOpen, m.phase)
    }

    @Test
    fun `dragging tracks moves and drops on release`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.LongPress)
        m.onEvent(ItemGestureEvent.Move(far(0f, 40f)))         // begin drag
        val next = far(10f, 60f)
        assertEquals(listOf(ItemGestureEffect.DragTo(next)), m.onEvent(ItemGestureEvent.Move(next)))
        assertEquals(listOf(ItemGestureEffect.Drop), m.onEvent(ItemGestureEvent.Up))
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }

    @Test
    fun `cancel during a drag abandons it`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.LongPress)
        m.onEvent(ItemGestureEvent.Move(far(0f, 40f)))
        assertEquals(listOf(ItemGestureEffect.CancelDrag), m.onEvent(ItemGestureEvent.Cancel))
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }

    @Test
    fun `a stale long-press after a swipe is ignored`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        m.onEvent(ItemGestureEvent.Move(far(30f, 0f)))         // now Swiped
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.LongPress))
        assertEquals(ItemGesturePhase.Swiped(SwipeDirection.RIGHT), m.phase)
    }

    @Test
    fun `cancel while pending resets without acting`() {
        val m = machine()
        m.onEvent(ItemGestureEvent.Down)
        assertEquals(emptyList<ItemGestureEffect>(), m.onEvent(ItemGestureEvent.Cancel))
        assertEquals(ItemGesturePhase.Idle, m.phase)
    }
}
