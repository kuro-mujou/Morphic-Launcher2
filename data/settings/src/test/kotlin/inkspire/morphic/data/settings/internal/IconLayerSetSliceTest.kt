package inkspire.morphic.data.settings.internal

import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The global icon recipe's trip through storage.
 *
 * [SettingsSliceTest] already covers the codec's general rules; this covers what is specific to a layer set, and
 * both specifics come from the same fact — **it is the only slice whose type validates itself on construction**.
 * A set requires exactly one foreground, one background, and the foreground above the background, checked in an
 * `init` block that deserialization runs. So a blob can be perfectly well-formed JSON and still be an illegal
 * value, which no other slice can manage.
 *
 * The other half is that the stack is **ordered and positional**: a layer means what it means because of where it
 * sits, so a round trip that preserved the set but not the order would be silently wrong.
 */
class IconLayerSetSliceTest {

    private val slice = SettingsSlice(
        name = "icon_layer_set",
        serializer = serializer<IconLayerSet>(),
        default = IconLayerSet.Base,
    )

    /** A set exercising every part of the shape: a custom layer, a non-default source, a shape, and transforms. */
    private val customized = IconLayerSet(
        listOf(
            IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.SolidFill(argb = 0xFF2196F3.toInt())),
            IconLayerSpec(
                role = LayerRole.FOREGROUND,
                source = LayerSource.AppDefault,
                shape = IconShape("circle"),
                zoom = 0.8f,
                offsetX = 0.05f,
            ),
            IconLayerSpec(
                role = LayerRole.CUSTOM,
                source = LayerSource.CustomImage(path = "/data/user/0/app/files/icon_layers/sparkle.png"),
                rotation = 45f,
                visible = false,
            ),
        ),
    )

    @Test
    fun `a customized set survives a round trip`() {
        assertEquals(customized, slice.decode(slice.encode(customized)))
    }

    @Test
    fun `layer order survives, because a layer's index is what places it in the stack`() {
        val decoded = slice.decode(slice.encode(customized))

        assertEquals(customized.layers.map { it.role }, decoded.layers.map { it.role })
    }

    @Test
    fun `nothing stored yields the plain app-default set`() {
        assertEquals(IconLayerSet.Base, slice.decode(null))
    }

    @Test
    fun `a well-formed blob describing an illegal stack falls back rather than throwing`() {
        // Valid JSON, valid specs, impossible set: two foregrounds. `IconLayerSet.init` throws, `decode` catches it,
        // and the user gets plain icons they can see and fix — where an escaping throw would take down every surface
        // that draws one.
        val twoForegrounds = """
            {"layers":[
              {"role":"FOREGROUND","source":{"type":"app_default"}},
              {"role":"FOREGROUND","source":{"type":"app_default"}}
            ]}
        """.trimIndent()

        assertEquals(IconLayerSet.Base, slice.decode(twoForegrounds))
    }

    @Test
    fun `a stack with the foreground below the background falls back too`() {
        val inverted = """
            {"layers":[
              {"role":"FOREGROUND","source":{"type":"app_default"}},
              {"role":"BACKGROUND","source":{"type":"app_default"}}
            ]}
        """.trimIndent()

        assertEquals(IconLayerSet.Base, slice.decode(inverted))
    }

    @Test
    fun `the on-disk source names are the contract, so a hand-written blob still decodes`() {
        // Pins the `@SerialName`s and the shape-as-id form against a rename: these strings are in users' stores, so a
        // refactor that changes one has to change the data too. Written by hand rather than round-tripped for exactly
        // that reason — a round trip would agree with itself whatever the names became.
        val stored = """
            {"layers":[
              {"role":"BACKGROUND","source":{"type":"solid_fill","argb":-16777216}},
              {"role":"FOREGROUND","source":{"type":"app_default_monochrome"},"shape":"squircle"}
            ]}
        """.trimIndent()

        val decoded = slice.decode(stored)

        assertEquals(LayerSource.SolidFill(argb = -0x1000000), decoded.background.source)
        assertEquals(LayerSource.AppDefaultMonochrome, decoded.foreground.source)
        assertEquals(IconShape("squircle"), decoded.foreground.shape)
    }

    @Test
    fun `layer defaults are not written, so an untouched set stores almost nothing`() {
        // Same guarantee as every other slice, and it matters more here: a set is written whole on every edit, so
        // omitting defaults is what keeps a three-layer blob from carrying thirty fields of nothing.
        val encoded = slice.encode(IconLayerSet.Base)

        assertEquals(
            """{"layers":[{"role":"BACKGROUND","source":{"type":"app_default"}},""" +
                """{"role":"FOREGROUND","source":{"type":"app_default"}}]}""",
            encoded,
        )
    }
}
