package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour spec for the APPS category arrangement — one dense list per category, no capacity.
 *
 * Much shorter than `AppsPagerPagingTest` because the store is much simpler: nothing cascades, nothing compacts,
 * and there is no orientation. What is left is where an app lands and, more importantly, **when the classifier is
 * allowed to have an opinion at all**.
 */
class AppsCategoryOrderingTest {

    private fun app(name: String) = ComponentKey("pkg.$name", "Main")

    /** `items("MEDIA" to "ab")` — category MEDIA holds apps a then b. */
    private fun items(vararg rows: Pair<String, String>): Map<String, List<ComponentKey>> =
        rows.associate { (id, apps) -> id to apps.map { app(it.toString()) } }

    private fun render(items: Map<String, List<ComponentKey>>): Map<String, String> =
        items.mapValues { (_, apps) -> apps.joinToString("") { it.packageName.removePrefix("pkg.") } }

    private fun assertItems(expected: Map<String, String>, actual: Map<String, List<ComponentKey>>) =
        assertEquals(expected, render(actual))

    // ── Moving: reordering and re-filing are the same op ──

    @Test
    fun `a move within a category reorders it`() {
        val moved = moveCategoryItem(items("MEDIA" to "abc"), app("a"), toCategory = "MEDIA", toSlot = 2)
        assertItems(mapOf("MEDIA" to "bca"), moved)
    }

    @Test
    fun `a move to another category re-files the app and takes it out of the old one`() {
        val moved = moveCategoryItem(items("MEDIA" to "abc", "GAMES" to "xy"), app("b"), "GAMES", toSlot = 1)
        assertItems(mapOf("MEDIA" to "ac", "GAMES" to "xby"), moved)
    }

    @Test
    fun `a slot past the end appends`() {
        val moved = moveCategoryItem(items("MEDIA" to "ab", "GAMES" to "xy"), app("a"), "GAMES", toSlot = 99)
        assertItems(mapOf("MEDIA" to "b", "GAMES" to "xya"), moved)
    }

    @Test
    fun `a move into a category holding nothing creates its bucket`() {
        // The emptied-page case: its definition row survives, so it must be droppable into again.
        val moved = moveCategoryItem(items("MEDIA" to "ab"), app("a"), "GAMES", toSlot = 0)
        assertItems(mapOf("MEDIA" to "b", "GAMES" to "a"), moved)
    }

    @Test
    fun `emptying a category leaves it present but empty`() {
        val moved = moveCategoryItem(items("MEDIA" to "a", "GAMES" to "x"), app("a"), "GAMES", toSlot = 0)
        assertItems(mapOf("MEDIA" to "", "GAMES" to "ax"), moved)
    }

    // ── Sync: the classifier gets a first answer, never a second one ──

    @Test
    fun `an empty store is seeded in the order given`() {
        val assignments = linkedMapOf(app("a") to "MEDIA", app("b") to "GAMES", app("c") to "MEDIA")
        assertItems(mapOf("MEDIA" to "ac", "GAMES" to "b"), syncCategoryItems(emptyMap(), assignments))
    }

    @Test
    fun `a filed app keeps its category even when the classifier disagrees`() {
        // The rule the whole store depends on. The user dragged 'a' into GAMES; classification still says MEDIA and
        // runs on every launch. Re-applying it would silently undo that drag — and every other one — at startup.
        val stored = items("GAMES" to "a")
        val assignments = mapOf(app("a") to "MEDIA")
        assertItems(mapOf("GAMES" to "a"), syncCategoryItems(stored, assignments))
    }

    @Test
    fun `a newly installed app is appended to its category`() {
        val stored = items("MEDIA" to "ab")
        val assignments = linkedMapOf(app("a") to "MEDIA", app("b") to "MEDIA", app("z") to "MEDIA")
        assertItems(mapOf("MEDIA" to "abz"), syncCategoryItems(stored, assignments))
    }

    @Test
    fun `a new app whose category has nothing yet starts it off`() {
        val stored = items("MEDIA" to "a")
        val assignments = linkedMapOf(app("a") to "MEDIA", app("z") to "GAMES")
        assertItems(mapOf("MEDIA" to "a", "GAMES" to "z"), syncCategoryItems(stored, assignments))
    }

    @Test
    fun `an uninstalled app is dropped from its category`() {
        val stored = items("MEDIA" to "abc")
        val assignments = linkedMapOf(app("a") to "MEDIA", app("c") to "MEDIA")
        assertItems(mapOf("MEDIA" to "ac"), syncCategoryItems(stored, assignments))
    }

    @Test
    fun `syncing an unchanged set changes nothing`() {
        // What lets the repository skip the write on the common launch, which is what stops the whole surface
        // re-rendering every time the app starts.
        val stored = items("MEDIA" to "ab", "GAMES" to "x")
        val assignments = linkedMapOf(app("a") to "MEDIA", app("b") to "MEDIA", app("x") to "GAMES")
        assertEquals(stored, syncCategoryItems(stored, assignments))
    }

    // ── A category that no longer exists cannot hold apps ──

    @Test
    fun `apps under an unknown category are unfiled`() {
        val stored = items("MEDIA" to "ab", "INTERNET" to "cd")
        assertItems(mapOf("MEDIA" to "ab"), dropUnknownCategories(stored, setOf("MEDIA", "GAMES")))
    }

    @Test
    fun `unfiling hands those apps back to the classifier`() {
        // The rebalance path end to end: INTERNET stopped existing, so its apps are re-filed where classification
        // now says they go — the one case where an assignment overrules a stored row, because there is no row left.
        val stored = items("MEDIA" to "a", "INTERNET" to "bc")
        val known = setOf("MEDIA", "SHOPPING", "FINANCE")
        val assignments = linkedMapOf(app("a") to "MEDIA", app("b") to "SHOPPING", app("c") to "FINANCE")
        val synced = syncCategoryItems(dropUnknownCategories(stored, known), assignments)
        assertItems(mapOf("MEDIA" to "a", "SHOPPING" to "b", "FINANCE" to "c"), synced)
    }

    // ── Locating ──

    @Test
    fun `the category an app is filed under is reported`() {
        assertEquals("GAMES", categoryOf(items("MEDIA" to "ab", "GAMES" to "xy"), app("x")))
    }

    @Test
    fun `an unfiled app has no category`() {
        assertNull(categoryOf(items("MEDIA" to "ab"), app("z")))
    }
}
