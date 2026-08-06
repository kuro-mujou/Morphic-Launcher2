package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsCategoryGrid
import inkspire.morphic.core.model.AppsPagerGrid
import inkspire.morphic.core.model.FolderGrid
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.blueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for [SurfacePaging] and the blueprint declarations behind it.
 *
 * The sparseness rule is the interesting half — a cleared toggle must *remove* its entry rather than store the
 * default back, or the blob stops being the difference from the defaults and a later change to a default stops
 * reaching anyone who has visited the screen. That is L1's ~265 permanently-populated keys, in miniature.
 */
class SurfacePagingTest {

    @Test
    fun `nothing stored follows the blueprint`() {
        val paging = SurfacePaging.Default
        assertFalse(paging.wrapsFor(GridSlot.HOME_MAIN, base = false))
        assertTrue(paging.wrapsFor(GridSlot.HOME_MAIN, base = true))
    }

    @Test
    fun `a stored value overrides the blueprint in both directions`() {
        val on = SurfacePaging.Default.withWrap(GridSlot.APPS_PAGER, true)
        assertTrue(on.wrapsFor(GridSlot.APPS_PAGER, base = false))

        // The direction that only works because the value is stored rather than merely present: a user turning
        // wrapping *off* on a pager whose blueprint defaults it on must survive, which `wraps[slot] ?: base` gives
        // and a `wraps.contains(slot)` check would not.
        val off = SurfacePaging.Default.withWrap(GridSlot.APPS_PAGER, false)
        assertFalse(off.wrapsFor(GridSlot.APPS_PAGER, base = true))
    }

    @Test
    fun `clearing removes the entry rather than storing the default`() {
        val stored = SurfacePaging.Default.withWrap(GridSlot.HOME_MAIN, true)
        assertEquals(mapOf(GridSlot.HOME_MAIN to true), stored.wraps)

        val cleared = stored.withWrap(GridSlot.HOME_MAIN, null)
        assertEquals(emptyMap<GridSlot, Boolean>(), cleared.wraps)
        assertEquals(SurfacePaging.Default, cleared)
    }

    @Test
    fun `each pager is stored independently`() {
        val paging = SurfacePaging.Default
            .withWrap(GridSlot.APPS_PAGER, true)
            .withWrap(GridSlot.APPS_CATEGORY, false)

        assertTrue(paging.wrapsFor(GridSlot.APPS_PAGER, base = false))
        assertFalse(paging.wrapsFor(GridSlot.APPS_CATEGORY, base = true))
        // The one L1 could not express at all: home is untouched by either write, where its single global flag made
        // all three the same value.
        assertFalse(paging.wrapsFor(GridSlot.HOME_MAIN, base = false))
    }

    /**
     * The blueprint is where "is this grid a configurable pager" is declared, and the repository defers to it rather
     * than keeping a second list — so this pins the three that answer and one that must not.
     */
    @Test
    fun `exactly the three pagers declare a wrap default, and all start off`() {
        val wrappable = GridSlot.entries.filter { it.blueprint.pages }
        assertEquals(
            listOf(GridSlot.HOME_MAIN, GridSlot.APPS_PAGER, GridSlot.APPS_CATEGORY).sorted(),
            wrappable.sorted(),
        )
        for (slot in wrappable) assertFalse("$slot should default to bounded", slot.blueprint.wraps!!)

        // Named individually as well, so a blueprint edit that flips one shows up as this test rather than as a
        // launcher that quietly needs two fingers to reach its app drawer.
        assertFalse(HomePagerGrid.wraps!!)
        assertFalse(AppsPagerGrid.wraps!!)
        assertFalse(AppsCategoryGrid.wraps!!)

        // Paged, but bounded by construction — a folder's pages are a handful of apps in a card, so there is no
        // setting here to offer and the repository must refuse it.
        assertNull(FolderGrid.wraps)
        assertFalse(FolderGrid.pages)
    }
}
