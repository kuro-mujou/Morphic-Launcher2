package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadePull
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
    fun `swipe and double-tap actions survive a round trip`() {
        val gestures = HomeGestures(
            swipes = mapOf(
                SwipeDirection.DOWN to GestureAction.OpenSystemPanel(ShadePull.BY_SIDE),
                SwipeDirection.UP to GestureAction.LaunchApp(ComponentKey("com.example", "com.example.Main", 0L)),
            ),
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
    fun `the panel action is stored under its short name, with how it pulls under the panel key`() {
        val swipes = mapOf(SwipeDirection.DOWN to GestureAction.OpenSystemPanel(ShadePull.QUICK_SETTINGS))

        val encoded = slice.encode(HomeGestures(swipes = swipes))

        assertEquals("""{"swipes":{"DOWN":{"type":"panel","panel":"QUICK_SETTINGS"}}}""", encoded)
    }

    @Test
    fun `a blob from when the phone's panel style was stored keeps its panel actions and drops the style`() {
        val stored = """{"swipes":{"DOWN":{"type":"panel","panel":"NOTIFICATIONS"}},"shadeStyle":"SEPARATE"}"""

        val panel = GestureAction.OpenSystemPanel(ShadePull.NOTIFICATIONS)
        assertEquals(HomeGestures(swipes = mapOf(SwipeDirection.DOWN to panel)), slice.decode(stored))
    }

    @Test
    fun `a blob holding the retired panel-less action reads as the defaults rather than guessing a panel`() {
        val stored = """{"swipes":{"DOWN":{"type":"system_panel"}},"shadeStyle":"SEPARATE"}"""

        assertEquals(HomeGestures.Default, slice.decode(stored))
    }

    @Test
    fun `clearing a swipe removes it rather than storing nothing`() {
        val set = HomeGestures.Default
            .withSwipe(SwipeDirection.DOWN, GestureAction.OpenSystemPanel(ShadePull.NOTIFICATIONS))

        val cleared = set.withSwipe(SwipeDirection.DOWN, null)

        assertEquals(emptyMap<SwipeDirection, GestureAction>(), cleared.swipes)
    }
}
