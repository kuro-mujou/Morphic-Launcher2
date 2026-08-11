package inkspire.morphic.data.icons.internal

import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a stored per-app recipe does on the way in and out — in particular, **every way it can fail to come back**.
 *
 * The failure cases are the reason this codec exists as its own object rather than two lines inside the repository:
 * a row that cannot be decoded must produce `null`, because that is what makes the repository drop it and the app
 * fall back to the global default. Anything that let a bad row throw would take down every surface drawing that
 * icon, which is a large consequence for one malformed string.
 */
class IconLayerSetCodecTest {

    private val customised = IconLayerSet(
        listOf(
            IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.SolidFill(argb = -0x10000)),
            IconLayerSpec(
                role = LayerRole.FOREGROUND,
                source = LayerSource.AppDefault,
                shape = IconShape("hexagon"),
                zoom = 1.25f,
            ),
        ),
    )

    @Test
    fun `a recipe survives a round trip`() {
        assertEquals(customised, IconLayerSetCodec.decode(IconLayerSetCodec.encode(customised)))
    }

    @Test
    fun `a corrupt blob decodes to null rather than throwing`() {
        assertNull(IconLayerSetCodec.decode("{ not json"))
        assertNull(IconLayerSetCodec.decode(""))
    }

    @Test
    fun `well-formed JSON describing an illegal stack also decodes to null`() {
        // The case a plain `Json.decodeFromString` would not survive: the parse succeeds and `IconLayerSet.init`
        // then rejects the value. Both failures have to land on the same fallback, or a stack with no background
        // would be fatal where a truncated file is merely ignored.
        val noBackground = """{"layers":[{"role":"FOREGROUND","source":{"type":"app_default"}}]}"""

        assertNull(IconLayerSetCodec.decode(noBackground))
    }

    @Test
    fun `an unknown effect is dropped, so a newer build's row still loads on an older one`() {
        // Effects are an open sealed list precisely so adding one is not a migration; the other half of that promise
        // is that a build which predates an effect can still read a row containing it.
        val fromTheFuture = """
            {"layers":[
              {"role":"BACKGROUND","source":{"type":"app_default"},"somethingAddedLater":7},
              {"role":"FOREGROUND","source":{"type":"app_default"}}
            ]}
        """.trimIndent()

        assertEquals(IconLayerSet.Base, IconLayerSetCodec.decode(fromTheFuture))
    }

    @Test
    fun `defaults are not written, so a row stores only what the user changed`() {
        val encoded = IconLayerSetCodec.encode(IconLayerSet.Base)

        assertEquals(
            """{"layers":[{"role":"BACKGROUND","source":{"type":"app_default"}},""" +
                """{"role":"FOREGROUND","source":{"type":"app_default"}}]}""",
            encoded,
        )
    }
}
