package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blob codec's rules, checked without Android — which is the point of keeping [SettingsSlice] free of DataStore.
 *
 * These are the guarantees the storage design rests on: absence and corruption both mean "defaults", a blob holds only
 * what differs from the defaults, and an unknown field does not take the settings down. L1's equivalent codec (693
 * lines) had no tests, and every one of these properties was load-bearing there too.
 */
class SettingsSliceTest {

    private val slice = SettingsSlice(
        name = "surface_register",
        serializer = serializer<SurfaceRegister>(),
        default = SurfaceRegister.Default,
    )

    private val bound = SurfaceRegister(
        homeLayout = HomeLayout.PAGER_WITH_DOCK,
        sides = mapOf(HomeEdge.BOTTOM to SideBinding.Apps(AppsLayout.PAGER)),
        transition = SurfaceTransition.FADE,
    )

    @Test
    fun `a value survives a round trip`() {
        assertEquals(bound, slice.decode(slice.encode(bound)))
    }

    @Test
    fun `nothing stored yields the defaults`() {
        assertEquals(SurfaceRegister.Default, slice.decode(null))
    }

    @Test
    fun `an unreadable blob falls back to the defaults rather than throwing`() {
        // The behaviour a corrupt store must have: recoverable, not fatal. (It is also logged — see `decode`.)
        assertEquals(SurfaceRegister.Default, slice.decode("{ not json"))
        assertEquals(SurfaceRegister.Default, slice.decode(""))
    }

    @Test
    fun `an unknown field is ignored, so an older build can read a newer blob`() {
        val fromTheFuture = """{"homeLayout":"PAGER_WITH_DOCK","somethingAddedLater":42}"""

        assertEquals(SurfaceRegister.Default, slice.decode(fromTheFuture))
    }

    @Test
    fun `a missing field falls back to its default, so a newer build can read an older blob`() {
        // Written before `transition` existed: everything else must still load, and `transition` takes its default.
        val fromThePast = """{"sides":{"LEFT":{"type":"inkspire.morphic.data.settings.SideBinding.Apps"}}}"""

        val decoded = slice.decode(fromThePast)

        assertEquals(SurfaceTransition.SLIDE, decoded.transition)
        assertEquals(HomeLayout.PAGER_WITH_DOCK, decoded.homeLayout)
        assertEquals(setOf(HomeEdge.LEFT), decoded.sides.keys)
    }

    @Test
    fun `defaults are not written, so an untouched register stores almost nothing`() {
        // What keeps "the default lives in exactly one place" true: storage never copies a default in, so changing a
        // default later still reaches every user who hasn't overridden it.
        val encoded = slice.encode(SurfaceRegister.Default)

        assertEquals("{}", encoded)
    }

    @Test
    fun `only the changed field is written`() {
        val encoded = slice.encode(SurfaceRegister.Default.copy(transition = SurfaceTransition.ZOOM))

        assertTrue("expected only `transition`, got: $encoded", encoded.contains("transition"))
        assertTrue("expected no `homeLayout`, got: $encoded", !encoded.contains("homeLayout"))
    }

    @Test
    fun `an unbound edge is absent rather than null, so the key set is the swipeable set`() {
        val encoded = slice.encode(bound.copy(sides = emptyMap()))

        assertEquals("{\"transition\":\"FADE\"}", encoded)
    }
}
