package inkspire.morphic.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days

/** When the launcher may ask again to be the default home app. */
class DefaultLauncherAskTest {

    private val askedAt = 10.days.inWholeMilliseconds
    private val cooldown = DefaultLauncherAsk.Cooldown.inWholeMilliseconds

    @Test
    fun `a launcher that never asked may ask`() {
        assertTrue(DefaultLauncherAsk.Default.isDue(nowMillis = 0L))
    }

    @Test
    fun `it does not ask again inside the cooldown`() {
        val ask = DefaultLauncherAsk(lastAskedAtMillis = askedAt)

        assertFalse(ask.isDue(nowMillis = askedAt))
        assertFalse(ask.isDue(nowMillis = askedAt + cooldown - 1))
    }

    @Test
    fun `it asks again once the cooldown has passed`() {
        val ask = DefaultLauncherAsk(lastAskedAtMillis = askedAt)

        assertTrue(ask.isDue(nowMillis = askedAt + cooldown))
    }

    @Test
    fun `a last ask in the future, from a clock set back, does not silence it`() {
        val ask = DefaultLauncherAsk(lastAskedAtMillis = askedAt)

        assertTrue(ask.isDue(nowMillis = askedAt - 1.days.inWholeMilliseconds))
    }
}
