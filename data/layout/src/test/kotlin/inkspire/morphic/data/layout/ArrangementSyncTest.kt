package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior spec for the arrangement-sync extensions — the guards, mostly, since the arithmetic itself is
 * [ArrangementProjectionTest]'s. Each of them fails silently when wrong: a sync that clears a posture, one that
 * rewrites on every rotation, or a snapshot that tidies the layout it is preserving.
 */
class ArrangementSyncTest {

    private val portrait = ArrangementKey.PHONE_PORTRAIT
    private val landscape = ArrangementKey.PHONE_LANDSCAPE

    @Test
    fun `copy re-lays the source into the target's grid`() = runTest {
        val repository = FakeLayoutRepository(
            portrait to mapOf(app("a") to at(0, 0, 0), app("b") to at(0, 1, 0)),
        )

        assertTrue(repository.copyArrangement(portrait, landscape, grids(rows = 1, cols = 4)))
        assertEquals(
            mapOf(app("a") to at(0, 0, 0), app("b") to at(0, 0, 1)),
            repository.stored.getValue(landscape),
        )
    }

    @Test
    fun `an empty source leaves the target alone rather than clearing it`() = runTest {
        // The failure this guards is silent and total: "make them agree" is a plausible reading of "empty the
        // target", and it would wipe a home screen on the first launch that reached here before anything was placed.
        val existing = mapOf(app("a") to at(0, 0, 0))
        val repository = FakeLayoutRepository(portrait to emptyMap(), landscape to existing)

        assertFalse(repository.copyArrangement(portrait, landscape, grids(rows = 4, cols = 4)))
        assertEquals(existing, repository.stored.getValue(landscape))
        assertEquals(0, repository.replaceCount)
    }

    @Test
    fun `copying what the target already holds writes nothing`() = runTest {
        // The re-derive on entry is unconditional, so without this every configuration change rewrites the whole
        // target and the store re-emits a map identical to the one on screen.
        val repository = FakeLayoutRepository(portrait to mapOf(app("a") to at(0, 0, 0)))

        assertTrue(repository.copyArrangement(portrait, landscape, grids(rows = 4, cols = 4)))
        assertEquals(1, repository.replaceCount)
        assertFalse(repository.copyArrangement(portrait, landscape, grids(rows = 4, cols = 4)))
        assertEquals(1, repository.replaceCount)
    }

    @Test
    fun `copy replaces rather than merges`() = runTest {
        val repository = FakeLayoutRepository(
            portrait to mapOf(app("a") to at(0, 0, 0)),
            landscape to mapOf(app("gone") to at(0, 3, 3)),
        )

        repository.copyArrangement(portrait, landscape, grids(rows = 4, cols = 4))
        assertEquals(setOf(app("a")), repository.stored.getValue(landscape).keys)
    }

    @Test
    fun `copying a key onto itself does nothing`() = runTest {
        val repository = FakeLayoutRepository(portrait to mapOf(app("a") to at(0, 2, 2)))

        assertFalse(repository.copyArrangement(portrait, portrait, grids(rows = 4, cols = 4)))
        assertEquals(0, repository.replaceCount)
    }

    @Test
    fun `copyIfEmpty refuses a target that already holds a layout`() = runTest {
        val theirs = mapOf(app("theirs") to at(0, 2, 2))
        val repository = FakeLayoutRepository(portrait to mapOf(app("a") to at(0, 0, 0)), landscape to theirs)

        assertFalse(repository.copyArrangementIfEmpty(portrait, landscape, grids(rows = 4, cols = 4)))
        assertEquals(theirs, repository.stored.getValue(landscape))
    }

    @Test
    fun `mirror preserves gaps where copy would close them`() = runTest {
        // The whole reason the mode seed is not a copy: source and target are the same posture in the two modes, so
        // they describe the same grid, and re-laying would tidy away the arrangement it exists to carry over.
        val gapped = mapOf(app("a") to at(0, 2, 2))
        val repository = FakeLayoutRepository(ArrangementKey.PHONE_PORTRAIT_LINKED to gapped)

        assertTrue(repository.mirrorArrangementIfEmpty(ArrangementKey.PHONE_PORTRAIT_LINKED, portrait))
        assertEquals(gapped, repository.stored.getValue(portrait))

        repository.copyArrangement(portrait, landscape, grids(rows = 4, cols = 4))
        assertEquals(mapOf(app("a") to at(0, 0, 0)), repository.stored.getValue(landscape))
    }

    @Test
    fun `mirror refuses a target that already holds a layout`() = runTest {
        // The guard that makes the mode seed safe on every configuration change rather than exactly once: flipping
        // the toggle back and forth must not overwrite the layout the user made on the independent pair.
        val theirs = mapOf(app("theirs") to at(0, 2, 2))
        val repository = FakeLayoutRepository(
            ArrangementKey.PHONE_PORTRAIT_LINKED to mapOf(app("linked") to at(0, 0, 0)),
            portrait to theirs,
        )

        assertFalse(repository.mirrorArrangementIfEmpty(ArrangementKey.PHONE_PORTRAIT_LINKED, portrait))
        assertEquals(theirs, repository.stored.getValue(portrait))
    }

    @Test
    fun `each zone is laid out against its own grid`() = runTest {
        val repository = FakeLayoutRepository(
            portrait to mapOf(
                app("main") to at(0, 3, 3),
                app("dock") to atIn(HomeZone.DOCK, 0, 0, 3),
            ),
        )

        repository.copyArrangement(
            portrait,
            landscape,
            mapOf(
                HomeZone.MAIN to GridConfig(rows = 4, cols = 4),
                // A rail: one column, so the dock item can only land at column 0 however wide it was before.
                HomeZone.DOCK to GridConfig(rows = 4, cols = 1),
                HomeZone.WIDGET_AREA to GridConfig(rows = 4, cols = 4),
            ),
        )
        val out = repository.stored.getValue(landscape)
        assertEquals(PlacedItem(GridPlacement(0, 0, 0), HomeZone.MAIN), out.getValue(app("main")))
        assertEquals(PlacedItem(GridPlacement(0, 0, 0), HomeZone.DOCK), out.getValue(app("dock")))
    }

    // Typed as the sealed parent so a map literal of these infers `Map<GridItem, _>` rather than the App
    // subtype, which is what the repository signatures take.
    private fun app(id: String): GridItem = GridItem.App(ComponentKey("pkg.$id", "pkg.$id.Main"))

    private fun at(page: Int, row: Int, col: Int) = atIn(HomeZone.MAIN, page, row, col)

    private fun atIn(zone: HomeZone, page: Int, row: Int, col: Int) =
        PlacedItem(GridPlacement(page, row, col), zone)

    private fun grids(rows: Int, cols: Int) = HomeZone.entries.associateWith { GridConfig(rows = rows, cols = cols) }
}

/**
 * An in-memory [LayoutRepository] holding only what [ArrangementSync] touches.
 *
 * The definition flows return nothing: this class exists to check which rows a sync writes, and a folder's *label*
 * has no bearing on that. [replaceCount] is what the write-skipping guards are asserted against — "the target is
 * correct" and "the target was not rewritten" are different claims, and only the second catches churn.
 */
private class FakeLayoutRepository(
    vararg initial: Pair<ArrangementKey, Map<GridItem, PlacedItem>>,
) : LayoutRepository {

    val stored: MutableMap<ArrangementKey, Map<GridItem, PlacedItem>> = initial.toMap().toMutableMap()
    var replaceCount: Int = 0
        private set

    override fun placements(arrangement: ArrangementKey): Flow<Map<GridItem, PlacedItem>> =
        flowOf(stored[arrangement].orEmpty())

    override fun folders(): Flow<List<Folder>> = flowOf(emptyList())

    override fun iconContainers(): Flow<List<IconContainer>> = flowOf(emptyList())

    override fun widgetContainers(): Flow<List<WidgetContainer>> = flowOf(emptyList())

    override fun widgets(): Flow<List<WidgetInfo>> = flowOf(emptyList())

    override suspend fun apply(arrangement: ArrangementKey, changes: List<LayoutChange>) = Unit

    override suspend fun replacePlacements(arrangement: ArrangementKey, placements: Map<GridItem, PlacedItem>) {
        stored[arrangement] = placements
        replaceCount++
    }
}
