package inkspire.morphic.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which panel a swipe opens — wrong here and the swipe opens the other panel, with nothing to say so. */
class ShadeStyleTest {

    @Test
    fun `a combined shade opens notifications from anywhere`() {
        assertEquals(ShadePanel.NOTIFICATIONS, ShadeStyle.COMBINED.panelAt(0f))
        assertEquals(ShadePanel.NOTIFICATIONS, ShadeStyle.COMBINED.panelAt(0.9f))
    }

    @Test
    fun `a separate shade splits at the middle, and the middle itself is quick settings`() {
        assertEquals(ShadePanel.NOTIFICATIONS, ShadeStyle.SEPARATE.panelAt(0.49f))
        assertEquals(ShadePanel.QUICK_SETTINGS, ShadeStyle.SEPARATE.panelAt(0.5f))
        assertEquals(ShadePanel.QUICK_SETTINGS, ShadeStyle.SEPARATE.panelAt(1f))
    }
}
