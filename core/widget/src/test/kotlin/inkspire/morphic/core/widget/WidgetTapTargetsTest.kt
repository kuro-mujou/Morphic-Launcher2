package inkspire.morphic.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetTapTargetsTest {

    @Test
    fun `a later layer is on top of an earlier one`() {
        assertEquals(listOf(2), topmost(listOf(listOf(1), listOf(2), listOf(0))))
    }

    @Test
    fun `a group's layer is on top of the group, and of what the group is drawn over`() {
        assertEquals(listOf(1, 0), topmost(listOf(listOf(1), listOf(1, 0), listOf(0))))
    }

    @Test
    fun `a later group is on top of everything inside an earlier one`() {
        assertEquals(listOf(2), topmost(listOf(listOf(1, 5, 3), listOf(2))))
    }

    @Test
    fun `nothing is on top of nothing`() {
        assertNull(topmost(emptyList()))
    }
}
