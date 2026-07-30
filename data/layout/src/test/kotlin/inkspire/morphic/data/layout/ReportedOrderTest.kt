package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour spec for [reconcileFolderOrder] — the guard that keeps a folder reorder from *deleting* members the
 * UI could not render. `ReorderFolder` replaces membership wholesale, so this function is the only thing between
 * a UI-filtered order and permanent loss.
 */
class FolderOrderTest {

    private fun app(name: String) = ComponentKey("pkg", name)

    private val a = app("a")
    private val b = app("b")
    private val c = app("c")

    @Test
    fun `a fully rendered folder reorders exactly as reported`() {
        assertEquals(listOf(c, a, b), reconcileFolderOrder(known = listOf(a, b, c), reported = listOf(c, a, b)))
    }

    @Test
    fun `a member the UI could not render survives the reorder`() {
        // `c` has no AppInfo (uninstalled and not yet pruned), so the overlay never drew it and cannot report it.
        assertEquals(listOf(b, a, c), reconcileFolderOrder(known = listOf(a, b, c), reported = listOf(b, a)))
    }

    @Test
    fun `unrendered members keep their stored order at the end`() {
        val d = app("d")
        assertEquals(
            listOf(b, a, c, d),
            reconcileFolderOrder(known = listOf(a, b, c, d), reported = listOf(b, a)),
        )
    }

    @Test
    fun `an app the folder does not contain is not smuggled in`() {
        // A stale UI report (e.g. an inject the caller decided not to commit) must not add membership here;
        // adding is `addToFolder`'s job, which puts the app into `known` first.
        assertEquals(listOf(a, b), reconcileFolderOrder(known = listOf(a, b), reported = listOf(a, b, c)))
    }

    @Test
    fun `an empty report leaves membership intact in stored order`() {
        assertEquals(listOf(a, b), reconcileFolderOrder(known = listOf(a, b), reported = emptyList()))
    }
}
