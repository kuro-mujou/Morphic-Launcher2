package inkspire.morphic.data.settings.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The setup a user had when they started first-run setup over, kept to be offered back. */
class RestartLookSliceTest {

    @Test
    fun `a captured setup survives being stored`() {
        val captured = captureLook(
            name = "Current setup",
            stored = mapOf(SurfaceRegisterKey to """{"homeLayout":"LIST_WITH_WIDGET_AREA"}""")::get,
        )

        val read = RestartLookSlice.decode(RestartLookSlice.encode(captured))

        assertEquals(captured.name, read?.name)
        assertEquals(captured.slices, read?.slices)
    }

    @Test
    fun `nothing stored is no setup to offer back`() {
        assertNull(RestartLookSlice.decode(null))
    }
}
