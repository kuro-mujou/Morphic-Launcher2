package inkspire.morphic.core.designsystem.cell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list row's height range and its read-side clamp — the one cell dimension a user sets outright, and the one whose
 * *bounds* are governed by another control.
 *
 * Checkable without an emulator for the reason `CellFitTest` is: the arithmetic is pure `Float` dp, with the row's own
 * padding read from the cell rather than restated here (`RowPadV` is private, so the expected numbers below say where
 * the 16dp comes from instead of importing it).
 */
class RowHeightTest {

    private val metrics = IconMetrics(iconPercent = 1f, minIconDp = 28.dp, maxIconDp = 72.dp)

    /** `RowPadV * 2` — the row's vertical inset, added to both ends of the guardrail range. */
    private val padding = 16f

    @Test
    fun `the range is the guardrail range shifted by the row's own inset`() {
        val range = rowHeightRangeDp(metrics)

        assertEquals(28f + padding, range.start, 0.01f)
        assertEquals(72f + padding, range.endInclusive, 0.01f)
    }

    @Test
    fun `the icon fraction does not move either end`() {
        // **The property this range exists in its current form for.** The fraction scales the icon *within* the
        // guardrails, so it cannot change which heights are honourable. An earlier cut divided both ends by it, which
        // inverted the control: asking for a smaller icon raised the floor and pushed the row *taller* — a 56dp row
        // clamped up to 72dp for wanting 50% icons.
        val base = rowHeightRangeDp(metrics)
        listOf(0.3f, 0.5f, 0.88f, 1f).forEach { percent ->
            val range = rowHeightRangeDp(metrics.copy(iconPercent = percent))
            assertEquals("$percent moved the floor", base.start, range.start, 0.01f)
            assertEquals("$percent moved the ceiling", base.endInclusive, range.endInclusive, 0.01f)
        }
    }

    @Test
    fun `widening the guardrails widens the range, and narrowing narrows it`() {
        // The coupling the settings section relies on: the icon range slider is the authority, and this follows it.
        val wide = rowHeightRangeDp(metrics.copy(minIconDp = 16.dp, maxIconDp = 140.dp))
        val narrow = rowHeightRangeDp(metrics.copy(minIconDp = 40.dp, maxIconDp = 48.dp))

        assertEquals(16f + padding, wide.start, 0.01f)
        assertEquals(140f + padding, wide.endInclusive, 0.01f)
        assertEquals(40f + padding, narrow.start, 0.01f)
        assertEquals(48f + padding, narrow.endInclusive, 0.01f)
    }

    @Test
    fun `crossed guardrails are read order-safe, and equal ones still give a usable control`() {
        // `resolveIconSize` coerces with minOf/maxOf, so a crossed pair must describe the same range rather than an
        // empty one — and equal guardrails must still leave a slider something to travel.
        val crossed = rowHeightRangeDp(metrics.copy(minIconDp = 72.dp, maxIconDp = 28.dp))
        assertEquals(rowHeightRangeDp(metrics).start, crossed.start, 0.01f)
        assertEquals(rowHeightRangeDp(metrics).endInclusive, crossed.endInclusive, 0.01f)

        val equal = rowHeightRangeDp(metrics.copy(minIconDp = 48.dp, maxIconDp = 48.dp))
        assertTrue("an equal pair must not give an empty range", equal.endInclusive > equal.start)
    }

    @Test
    fun `a stored height outside the range is clamped, and comes back when the guardrails widen`() {
        // Clamp on read, never written — the same rule a grid's column count lives by, so the two controls behave
        // alike. A 56dp row under 80dp icons draws at 96dp; lower the guardrail again and it is 56dp once more.
        val tallIcons = metrics.copy(minIconDp = 80.dp, maxIconDp = 96.dp)

        assertEquals(96f, fitRowHeightDp(56f, tallIcons), 0.01f)
        assertEquals(56f, fitRowHeightDp(56f, metrics), 0.01f)
        // And the other end: a height above what the largest allowed icon can fill comes down to it.
        assertEquals(88f, fitRowHeightDp(200f, metrics), 0.01f)
    }

    @Test
    fun `the blueprint's default row height is offered by the blueprint's own guardrails`() {
        // The invariant that keeps a fresh install off a clamp: `AppsListGrid`'s 56dp row must sit inside the range its
        // own `IconSizing` implies, or the first frame would show a height nobody chose.
        val range = rowHeightRangeDp(metrics)

        assertTrue("56dp must be offerable at 28–72dp icons", 56f in range)
        assertEquals(56f, fitRowHeightDp(56f, metrics), 0.01f)
    }
}
