package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior spec for [reconcileReportedOrder] — the guard that keeps a reorder from *dropping* members the UI could
 * not render. Every whole-order op (`ReorderFolder`, [AppsCategoryChange.Reorder]) replaces order wholesale, so this
 * function is the only thing between a UI-filtered report and a lost member.
 */
class ReportedOrderTest {

    private fun app(name: String) = ComponentKey("pkg", name)

    private val a = app("a")
    private val b = app("b")
    private val c = app("c")

    @Test
    fun `a fully rendered collection reorders exactly as reported`() {
        assertEquals(listOf(c, a, b), reconcileReportedOrder(known = listOf(a, b, c), reported = listOf(c, a, b)))
    }

    @Test
    fun `a member the UI could not render survives the reorder`() {
        // `c` has no AppInfo (uninstalled and not yet pruned), so the overlay never drew it and cannot report it.
        assertEquals(listOf(b, a, c), reconcileReportedOrder(known = listOf(a, b, c), reported = listOf(b, a)))
    }

    @Test
    fun `unrendered members keep their stored order at the end`() {
        val d = app("d")
        assertEquals(
            listOf(b, a, c, d),
            reconcileReportedOrder(known = listOf(a, b, c, d), reported = listOf(b, a)),
        )
    }

    @Test
    fun `an app the collection does not contain is not smuggled in`() {
        // A stale UI report (e.g. an inject the caller decided not to commit) must not add membership here;
        // adding is `addToFolder`'s / `Move`'s job, which puts the app into `known` first.
        assertEquals(listOf(a, b), reconcileReportedOrder(known = listOf(a, b), reported = listOf(a, b, c)))
    }

    @Test
    fun `an empty report leaves membership intact in stored order`() {
        assertEquals(listOf(a, b), reconcileReportedOrder(known = listOf(a, b), reported = emptyList()))
    }
}
