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

    private val metrics = IconMetrics(iconPercent = 1f, minIconDp = 24.dp, maxIconDp = 48.dp)

    /** `RowPadV * 2` — the row's vertical inset, added to both ends of the guardrail range. */
    private val padding = 16f

    /** A row label's measured height, which only the text-only case reads. `bodyLarge`'s line height, near enough. */
    private val labelHeight = 24f

    @Test
    fun `the range is the guardrail range shifted by the row's own inset`() {
        val range = rowHeightRangeDp(metrics, labelHeight)

        assertEquals(24f + padding, range.start, 0.01f)
        assertEquals(48f + padding, range.endInclusive, 0.01f)
    }

    @Test
    fun `the icon fraction does not move either end`() {
        // **The property this range exists in its current form for.** The fraction scales the icon *within* the
        // guardrails, so it cannot change which heights are honourable. An earlier cut divided both ends by it, which
        // inverted the control: asking for a smaller icon raised the floor and pushed the row *taller* — a 56dp row
        // clamped up to 72dp for wanting 50% icons.
        val base = rowHeightRangeDp(metrics, labelHeight)
        listOf(0.3f, 0.5f, 0.88f, 1f).forEach { percent ->
            val range = rowHeightRangeDp(labelHeightDp = labelHeight, metrics = metrics.copy(iconPercent = percent))
            assertEquals("$percent moved the floor", base.start, range.start, 0.01f)
            assertEquals("$percent moved the ceiling", base.endInclusive, range.endInclusive, 0.01f)
        }
    }

    @Test
    fun `widening the guardrails widens the range, and narrowing narrows it`() {
        // The coupling the settings section relies on: the icon range slider is the authority, and this follows it.
        val wide = rowHeightRangeDp(labelHeightDp = labelHeight, metrics = metrics.copy(minIconDp = 24.dp, maxIconDp = 120.dp))
        val narrow = rowHeightRangeDp(labelHeightDp = labelHeight, metrics = metrics.copy(minIconDp = 40.dp, maxIconDp = 48.dp))

        assertEquals(24f + padding, wide.start, 0.01f)
        assertEquals(120f + padding, wide.endInclusive, 0.01f)
        assertEquals(40f + padding, narrow.start, 0.01f)
        assertEquals(48f + padding, narrow.endInclusive, 0.01f)
    }

    @Test
    fun `crossed guardrails are read order-safe, and equal ones still give a usable control`() {
        // `resolveIconSize` coerces with minOf/maxOf, so a crossed pair must describe the same range rather than an
        // empty one — and equal guardrails must still leave a slider something to travel.
        val crossed = rowHeightRangeDp(labelHeightDp = labelHeight, metrics = metrics.copy(minIconDp = 48.dp, maxIconDp = 24.dp))
        assertEquals(rowHeightRangeDp(metrics, labelHeight).start, crossed.start, 0.01f)
        assertEquals(rowHeightRangeDp(metrics, labelHeight).endInclusive, crossed.endInclusive, 0.01f)

        val equal = rowHeightRangeDp(labelHeightDp = labelHeight, metrics = metrics.copy(minIconDp = 48.dp, maxIconDp = 48.dp))
        assertTrue("an equal pair must not give an empty range", equal.endInclusive > equal.start)
    }

    @Test
    fun `a stored height outside the range is clamped, and comes back when the guardrails widen`() {
        // Clamp on read, never written — the same rule a grid's column count lives by, so the two controls behave
        // alike. A 56dp row under 80dp icons draws at 96dp; lower the guardrail again and it is 56dp once more.
        val tallIcons = metrics.copy(minIconDp = 80.dp, maxIconDp = 96.dp)

        assertEquals(96f, fitRowHeightDp(56f, tallIcons, labelHeight), 0.01f)
        assertEquals(56f, fitRowHeightDp(56f, metrics, labelHeight), 0.01f)
        // And the other end: a height above what the largest allowed icon can fill comes down to it.
        assertEquals(64f, fitRowHeightDp(200f, metrics, labelHeight), 0.01f)
    }

    @Test
    fun `with icons off the label is the floor and the ceiling opens up`() {
        // A pure-text row draws no icon, so neither guardrail bounds it. Bounding it by one is not merely odd, it is
        // wrong in the direction that hurts: chunky guardrails set *before* the icons were switched off would forbid a
        // compact text list (72–140dp icons → no row under 88dp of plain text).
        val chunky = metrics.copy(minIconDp = 72.dp, maxIconDp = 120.dp)

        val withIcons = rowHeightRangeDp(chunky, labelHeight)
        val textOnly = rowHeightRangeDp(chunky.copy(showIcon = false), labelHeight)

        assertEquals(72f + padding, withIcons.start, 0.01f)
        assertEquals(labelHeight + padding, textOnly.start, 0.01f)
        // "Opens up": the widest row the launcher offers at all (`IconSizingRanges.IconDp`'s ceiling), not a
        // text-derived cap — with no icon there is nothing to say a spacious row is wrong.
        assertEquals(120f + padding, textOnly.endInclusive, 0.01f)
        assertTrue("the text-only range must not be narrower", textOnly.start < withIcons.start)
    }

    @Test
    fun `with icons off the guardrails stop moving the range at all`() {
        // They describe an icon that isn't there, so neither end may follow them — the same reasoning that keeps
        // `iconPercent` out of the range while icons are on.
        val narrow = rowHeightRangeDp(metrics.copy(showIcon = false, minIconDp = 40.dp, maxIconDp = 48.dp), labelHeight)
        val wide = rowHeightRangeDp(metrics.copy(showIcon = false, minIconDp = 24.dp, maxIconDp = 120.dp), labelHeight)

        assertEquals(narrow.start, wide.start, 0.01f)
        assertEquals(narrow.endInclusive, wide.endInclusive, 0.01f)
        // And a taller label does raise the floor, since that is the one thing a text row cannot be shorter than.
        assertTrue(
            "a bigger label must raise the floor",
            rowHeightRangeDp(metrics.copy(showIcon = false), labelHeightDp = 40f).start > narrow.start,
        )
    }

    @Test
    fun `turning icons off widens the range rather than moving the stored height`() {
        // The clamp is on read, so the switch must not silently rewrite anything: a height legal with icons on stays
        // legal with them off, and one the guardrails had clamped *up* is released rather than pinned.
        val chunky = metrics.copy(minIconDp = 72.dp, maxIconDp = 120.dp)

        assertEquals(88f, fitRowHeightDp(56f, chunky, labelHeight), 0.01f)
        assertEquals(56f, fitRowHeightDp(56f, chunky.copy(showIcon = false), labelHeight), 0.01f)
    }

    @Test
    fun `the blueprint's default row height is offered by the blueprint's own guardrails`() {
        // The invariant that keeps a fresh install off a clamp: `AppsListGrid`'s 56dp row must sit inside the range its
        // own `IconSizing` implies, or the first frame would show a height nobody chose.
        val range = rowHeightRangeDp(metrics, labelHeight)

        assertTrue("56dp must be offerable at 24–48dp icons", 56f in range)
        assertEquals(56f, fitRowHeightDp(56f, metrics, labelHeight), 0.01f)
    }
}
