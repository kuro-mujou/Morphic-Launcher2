package inkspire.morphic.core.designsystem.cell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two icon-size resolutions and the one thing that separates them: the lower guardrail.
 *
 * Worth pinning because the difference is invisible on screen right up until it matters — an icon that has stopped
 * answering to the space it is given looks exactly like one that is simply that size.
 */
class IconMetricsTest {

    private val metrics = IconMetrics(iconPercent = 1f, minIconDp = 24.dp, maxIconDp = 48.dp)

    @Test
    fun `both resolutions take the percentage of the smaller bound`() {
        val half = metrics.copy(iconPercent = 0.5f)
        assertEquals(40.dp, half.resolveIconSize(availWidth = 80.dp, availHeight = 200.dp))
        assertEquals(40.dp, half.resolveIconSizeUnfloored(availWidth = 80.dp, availHeight = 200.dp))
    }

    @Test
    fun `both stop at the upper guardrail`() {
        assertEquals(48.dp, metrics.resolveIconSize(availWidth = 300.dp, availHeight = 300.dp))
        assertEquals(48.dp, metrics.resolveIconSizeUnfloored(availWidth = 300.dp, availHeight = 300.dp))
    }

    /**
     * The whole difference, and the container's reason for wanting the second one: below the floor, the guarded
     * resolution stops tracking the space it is given while the unfloored one goes on shrinking with it.
     */
    @Test
    fun `only the guarded resolution stops shrinking at the floor`() {
        assertEquals(24.dp, metrics.resolveIconSize(availWidth = 10.dp, availHeight = 10.dp))
        assertEquals(10.dp, metrics.resolveIconSizeUnfloored(availWidth = 10.dp, availHeight = 10.dp))
    }

    /**
     * A container resizing through the floor moves continuously.
     *
     * The flattening below is what the floor does wherever it bites — three different slot sizes resolving to one
     * icon size — and it is pinned here as arithmetic rather than because anyone watched it happen.
     */
    @Test
    fun `unfloored tracks the slot continuously across the floor`() {
        val sizes = listOf(30.dp, 20.dp, 12.dp).map { metrics.resolveIconSizeUnfloored(it, it) }
        assertEquals(listOf(30.dp, 20.dp, 12.dp), sizes)
        // The same three, guarded: two of them collapse onto the floor.
        assertEquals(
            listOf(30.dp, 24.dp, 24.dp),
            listOf(30.dp, 20.dp, 12.dp).map { metrics.resolveIconSize(it, it) },
        )
    }

    /**
     * Inverted guardrails must not crash, and both resolutions must read them the same way round — `minOf`/`maxOf`
     * rather than trusting the field names. Swapped, the *larger* of the two is still the ceiling, which is what the
     * original single `coerceIn` did and what splitting it in two had to preserve.
     */
    @Test
    fun `guardrails are order-safe`() {
        val inverted = metrics.copy(minIconDp = 48.dp, maxIconDp = 24.dp)
        assertEquals(48.dp, inverted.resolveIconSizeUnfloored(availWidth = 300.dp, availHeight = 300.dp))
        assertEquals(48.dp, inverted.resolveIconSize(availWidth = 300.dp, availHeight = 300.dp))
        // And the floor is the smaller one, so a tiny bound still comes back up to it.
        assertEquals(24.dp, inverted.resolveIconSize(availWidth = 4.dp, availHeight = 4.dp))
    }
}
