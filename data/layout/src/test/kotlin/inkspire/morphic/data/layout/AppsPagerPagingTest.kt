package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour spec for the APPS pager's arrangement maths — the rules a user feels as "my apps stayed where I put
 * them". Pure lists, no Room, mirroring how `FreeGridPlanner` and `FreePush` are tested.
 *
 * Items are written as single letters for legibility; `pages("ab", "cd")` is two pages of two apps.
 */
class AppsPagerPagingTest {

    private fun app(name: String): IconItem = IconItem.App(ComponentKey("pkg.$name", "Main"))
    private fun folder(id: Long): IconItem = IconItem.Folder(id)

    /** `pages("ab", "c")` → page 0 holds apps a and b, page 1 holds app c. */
    private fun pages(vararg rows: String): List<List<IconItem>> = rows.map { row -> row.map { app(it.toString()) } }

    /** The inverse, for readable assertions. */
    private fun List<List<IconItem>>.render(): List<String> = map { page ->
        page.joinToString("") { item ->
            when (item) {
                is IconItem.App -> item.component.packageName.removePrefix("pkg.")
                is IconItem.Folder -> "[${item.folderId}]"
            }
        }
    }

    private fun assertPages(expected: List<String>, actual: List<List<IconItem>>) =
        assertEquals(expected, actual.render())

    // ── Capacity: overflow cascades forward, empty pages disappear ──

    @Test
    fun `a page over capacity sheds its surplus onto the front of the next`() {
        assertPages(listOf("ab", "cd"), normalizePages(pages("abcd"), perPage = 2))
    }

    @Test
    fun `cascading continues for as many pages as it takes`() {
        // Six items at two per page: three pages, order preserved throughout.
        assertPages(listOf("ab", "cd", "ef"), normalizePages(pages("abcdef"), perPage = 2))
    }

    @Test
    fun `surplus lands in front of what is already on the next page`() {
        assertPages(listOf("ab", "cx"), normalizePages(pages("abc", "x"), perPage = 2))
    }

    @Test
    fun `empty pages are dropped, including in the middle`() {
        assertPages(listOf("a", "b"), normalizePages(pages("a", "", "b"), perPage = 4))
    }

    // ── Moving: pages are hard boundaries ──

    @Test
    fun `a move within a page reorders it`() {
        assertPages(listOf("bca"), movePagerItem(pages("abc"), app("a"), toPage = 0, toSlot = 2, perPage = 4))
    }

    @Test
    fun `a move across pages leaves the source page short rather than pulling the next page back`() {
        // The hard-boundary rule: page 0 keeps its hole at the end — d does not slide back to fill it.
        val moved = movePagerItem(pages("abc", "de"), app("a"), toPage = 1, toSlot = 0, perPage = 4)
        assertPages(listOf("bc", "ade"), moved)
    }

    @Test
    fun `a move that overfills the destination cascades the surplus onward`() {
        val moved = movePagerItem(pages("ab", "cd"), app("a"), toPage = 1, toSlot = 0, perPage = 2)
        assertPages(listOf("b", "ac", "d"), moved)
    }

    @Test
    fun `a move to a page past the end appends a new one`() {
        assertPages(listOf("b", "a"), movePagerItem(pages("ab"), app("a"), toPage = 1, toSlot = 0, perPage = 4))
    }

    @Test
    fun `a slot past the last icon appends within that page`() {
        assertPages(listOf("bca"), movePagerItem(pages("abc"), app("a"), toPage = 0, toSlot = 99, perPage = 4))
    }

    @Test
    fun `moving the only item off a page removes the page entirely`() {
        // Page 0 is emptied by the move, so it stops existing rather than leaving a blank screen to scroll past.
        assertPages(listOf("axy"), movePagerItem(pages("a", "xy"), app("a"), toPage = 1, toSlot = 0, perPage = 4))
    }

    // ── Insert and replace ──

    @Test
    fun `inserting places the item at the slot and shifts the rest right`() {
        assertPages(listOf("axb"), insertPagerItem(pages("ab"), app("x"), page = 0, slot = 1, perPage = 4))
    }

    @Test
    fun `replacing keeps the exact page and slot`() {
        assertPages(listOf("a[7]c"), replacePagerItem(pages("abc"), app("b"), folder(7)))
    }

    @Test
    fun `replacing something absent changes nothing`() {
        assertPages(listOf("abc"), replacePagerItem(pages("abc"), app("z"), folder(7)))
    }

    @Test
    fun `removing compacts only its own page`() {
        assertPages(listOf("ac", "de"), removePagerItem(pages("abc", "de"), app("b")))
    }

    // ── Sync: seed, append, prune ──

    @Test
    fun `an empty store seeds the whole list in the order given`() {
        val installed = listOf("a", "b", "c").map { ComponentKey("pkg.$it", "Main") }
        assertPages(listOf("ab", "c"), syncPagerPages(emptyList(), installed, perPage = 2))
    }

    @Test
    fun `a newly installed app is appended, not re-sorted into place`() {
        // The user arranged this list; a new app joins the end rather than shuffling their arrangement.
        val installed = listOf("c", "a", "z").map { ComponentKey("pkg.$it", "Main") }
        assertPages(listOf("caz"), syncPagerPages(pages("ca"), installed, perPage = 4))
    }

    @Test
    fun `an appended app starts a new page when the last one is full`() {
        val installed = listOf("a", "b", "c").map { ComponentKey("pkg.$it", "Main") }
        assertPages(listOf("ab", "c"), syncPagerPages(pages("ab"), installed, perPage = 2))
    }

    @Test
    fun `an uninstalled app is dropped and its page compacts`() {
        val installed = listOf("a", "c").map { ComponentKey("pkg.$it", "Main") }
        assertPages(listOf("ac"), syncPagerPages(pages("abc"), installed, perPage = 4))
    }

    @Test
    fun `folders survive a sync — they are not apps and cannot be uninstalled`() {
        val installed = listOf(ComponentKey("pkg.a", "Main"))
        val stored = listOf(listOf(app("a"), folder(7)))
        assertPages(listOf("a[7]"), syncPagerPages(stored, installed, perPage = 4))
    }

    @Test
    fun `syncing an unchanged list leaves it untouched`() {
        val installed = listOf("a", "b").map { ComponentKey("pkg.$it", "Main") }
        val stored = pages("ab")
        assertEquals(stored, syncPagerPages(stored, installed, perPage = 4))
    }

    // ── Locating ──

    @Test
    fun `locate reports the page and slot`() {
        assertEquals(PagerSlot(page = 1, slot = 1), locatePagerItem(pages("ab", "cd"), app("d")))
    }

    @Test
    fun `locate reports null for something absent`() {
        assertNull(locatePagerItem(pages("ab"), app("z")))
    }
}
