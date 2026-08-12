package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.icon.PreviewBackground
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The studio backdrop's trip through storage.
 *
 * [SettingsSliceTest] covers the codec's general rules; what is specific here is that this is the only slice stored as a
 * **bare enum**, so its blob is a single JSON string and that string is the constant's own name. Two consequences are
 * worth pinning rather than discovering: every value must survive the trip (nothing is silently unserializable), and an
 * unrecognized name — which is what a *renamed* value looks like to an older or newer build — must fall back to the
 * default rather than throw.
 */
class IconStudioBackgroundSliceTest {

    private val slice = SettingsSlice(
        name = "icon_studio_background",
        serializer = serializer<PreviewBackground>(),
        default = PreviewBackground.Default,
    )

    @Test
    fun `every backdrop survives a round trip`() {
        PreviewBackground.entries.forEach { background ->
            assertEquals(background, slice.decode(slice.encode(background)))
        }
    }

    @Test
    fun `nothing stored means the studio opens on the checkerboard`() {
        assertEquals(PreviewBackground.CHECKERBOARD, slice.decode(null))
    }

    /**
     * The exposure of storing a bare enum: the stored form is the constant's name, so renaming a value orphans whatever
     * users had chosen. Falling back is the right behavior — it is visible and one tap from fixed — and this pins that
     * it is a fall back rather than a crash.
     */
    @Test
    fun `an unrecognized backdrop falls back to the default`() {
        assertEquals(PreviewBackground.Default, slice.decode("\"BLACK_WITH_GLITTER\""))
    }

    /** A stored blob is the constant's name and nothing else — no wrapper object, since the setting *is* the value. */
    @Test
    fun `a backdrop is stored as its bare name`() {
        assertEquals("\"WHITE_WITH_CHECKER\"", slice.encode(PreviewBackground.WHITE_WITH_CHECKER))
    }
}
