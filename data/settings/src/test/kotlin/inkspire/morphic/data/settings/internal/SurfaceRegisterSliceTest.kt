package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The stored shape of the surface register, and the move from its old key. */
class SurfaceRegisterSliceTest {

    private val slice = SettingsSlice(
        name = SurfaceRegisterKey,
        serializer = serializer<SurfaceRegister>(),
        default = SurfaceRegister.Default,
    )

    private val legacyTwoEdges =
        """{"sides":{"TOP":{"type":"inkspire.morphic.data.settings.SideBinding.Apps"},""" +
            """"BOTTOM":{"type":"inkspire.morphic.data.settings.SideBinding.Apps","layout":"PAGER"}}}"""

    private val twoEdges = SurfaceRegister(
        sides = mapOf(
            HomeEdge.TOP to SideBinding.Apps(),
            HomeEdge.BOTTOM to SideBinding.Apps(AppsLayout.PAGER),
        ),
    )

    @Test
    fun `an apps binding is stored under its short name`() {
        val register = SurfaceRegister(sides = mapOf(HomeEdge.BOTTOM to SideBinding.Apps(AppsLayout.PAGER)))

        assertEquals("""{"sides":{"BOTTOM":{"type":"apps","layout":"PAGER"}}}""", slice.encode(register))
    }

    @Test
    fun `the old long discriminator does not read as the new one, which is why the key moved`() {
        assertEquals(SurfaceRegister.Default, slice.decode(legacyTwoEdges))
    }

    @Test
    fun `a register under the old key migrates with its edges`() {
        val migrated = migratedSurfaceRegister(legacy = legacyTwoEdges, current = null)

        assertEquals(twoEdges, slice.decode(migrated))
    }

    @Test
    fun `the home layout survives the migration beside the edges`() {
        val legacy = """{"homeLayout":"LIST_WITH_WIDGET_AREA",""" +
            """"sides":{"LEFT":{"type":"inkspire.morphic.data.settings.SideBinding.Apps","layout":"CATEGORY_CARD"}}}"""

        val migrated = slice.decode(migratedSurfaceRegister(legacy = legacy, current = null))

        assertEquals(
            SurfaceRegister(
                homeLayout = HomeLayout.LIST_WITH_WIDGET_AREA,
                sides = mapOf(HomeEdge.LEFT to SideBinding.Apps(AppsLayout.CATEGORY_CARD)),
            ),
            migrated,
        )
    }

    @Test
    fun `a register already under the new key is never overwritten`() {
        assertNull(migratedSurfaceRegister(legacy = legacyTwoEdges, current = slice.encode(twoEdges)))
    }

    @Test
    fun `nothing under the old key migrates nothing`() {
        assertNull(migratedSurfaceRegister(legacy = null, current = null))
    }

    @Test
    fun `an unreadable old blob is carried across as it was, to fall back where it is read`() {
        assertEquals("not json", migratedSurfaceRegister(legacy = "not json", current = null))
    }
}
