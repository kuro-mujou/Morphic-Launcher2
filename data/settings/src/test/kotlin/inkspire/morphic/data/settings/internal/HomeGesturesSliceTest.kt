package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadePanel
import inkspire.morphic.core.model.ShadeStyle
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.data.settings.HomeGestures
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored shape of HOME's swipe actions. */
class HomeGesturesSliceTest {

    private val slice = SettingsSlice(
        name = "home_gestures",
        serializer = serializer<HomeGestures>(),
        default = HomeGestures.Default,
    )

    @Test
    fun `swipe actions and the panel style survive a round trip`() {
        val gestures = HomeGestures(
            swipes = mapOf(
                SwipeDirection.DOWN to GestureAction.OpenSystemPanel(ShadePanel.NOTIFICATIONS),
                SwipeDirection.UP to GestureAction.LaunchApp(ComponentKey("com.example", "com.example.Main", 0L)),
            ),
            shadeStyle = ShadeStyle.SEPARATE,
            doubleTap = GestureAction.LockScreen,
        )

        assertEquals(gestures, slice.decode(slice.encode(gestures)))
    }

    @Test
    fun `the lock action is stored under its short name`() {
        val encoded = slice.encode(HomeGestures(doubleTap = GestureAction.LockScreen))

        assertEquals("""{"doubleTap":{"type":"lock_screen"}}""", encoded)
    }

    @Test
    fun `the panel action is stored under its short name, with the panel it names`() {
        val swipes = mapOf(SwipeDirection.DOWN to GestureAction.OpenSystemPanel(ShadePanel.QUICK_SETTINGS))

        val encoded = slice.encode(HomeGestures(swipes = swipes))

        assertEquals("""{"swipes":{"DOWN":{"type":"panel","panel":"QUICK_SETTINGS"}}}""", encoded)
    }

    @Test
    fun `a blob holding the retired panel-less action reads as the defaults rather than guessing a panel`() {
        val stored = """{"swipes":{"DOWN":{"type":"system_panel"}},"shadeStyle":"SEPARATE"}"""

        assertEquals(HomeGestures.Default, slice.decode(stored))
    }

    @Test
    fun `clearing a swipe removes it rather than storing nothing, and keeps the panel style`() {
        val set = HomeGestures(shadeStyle = ShadeStyle.SEPARATE)
            .withSwipe(SwipeDirection.DOWN, GestureAction.OpenSystemPanel(ShadePanel.NOTIFICATIONS))

        val cleared = set.withSwipe(SwipeDirection.DOWN, null)

        assertEquals(emptyMap<SwipeDirection, GestureAction>(), cleared.swipes)
        assertEquals(ShadeStyle.SEPARATE, cleared.shadeStyle)
    }
}
