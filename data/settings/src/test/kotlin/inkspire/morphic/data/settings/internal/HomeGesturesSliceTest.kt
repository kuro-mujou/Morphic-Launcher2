package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction
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
                SwipeDirection.DOWN to GestureAction.OpenSystemPanel,
                SwipeDirection.UP to GestureAction.LaunchApp(ComponentKey("com.example", "com.example.Main", 0L)),
            ),
            shadeStyle = ShadeStyle.SEPARATE,
        )

        assertEquals(gestures, slice.decode(slice.encode(gestures)))
    }

    @Test
    fun `the system panel action is stored under its short name, which is what a stored swipe is matched on`() {
        val encoded = slice.encode(HomeGestures(swipes = mapOf(SwipeDirection.DOWN to GestureAction.OpenSystemPanel)))

        assertEquals("""{"swipes":{"DOWN":{"type":"system_panel"}}}""", encoded)
    }

    @Test
    fun `clearing a swipe removes it rather than storing nothing, and keeps the panel style`() {
        val set = HomeGestures(shadeStyle = ShadeStyle.SEPARATE).withSwipe(SwipeDirection.DOWN, GestureAction.OpenSystemPanel)

        val cleared = set.withSwipe(SwipeDirection.DOWN, null)

        assertEquals(emptyMap<SwipeDirection, GestureAction>(), cleared.swipes)
        assertEquals(ShadeStyle.SEPARATE, cleared.shadeStyle)
    }
}
