package inkspire.morphic.core.model.widget

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A recipe is stored and will be shared, so its serialization is a contract: a round trip is lossless, a recipe
 * written before a field existed still reads, and the names on disk do not move.
 */
class WidgetRecipeTest {

    // A store's settings: defaults are not written, unknown keys are dropped rather than throwing.
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `a recipe using every source and extent survives a round trip`() {
        val recipe = WidgetRecipe(
            listOf(
                WidgetLayerSpec(
                    source = WidgetSource.Shape(WidgetSource.Shape.Kind.RECTANGLE, 0x80000000.toInt(), 24f),
                    width = WidgetExtent.Fill,
                    height = WidgetExtent.Fill,
                ),
                WidgetLayerSpec(
                    source = WidgetSource.Text(
                        text = "\$df(hh:mm)\$",
                        size = 48f,
                        color = 0xFFFFD9A0.toInt(),
                        font = WidgetSource.Text.Font.MONO,
                        weight = 700,
                        align = WidgetSource.Text.Align.CENTER,
                        maxLines = 2,
                    ),
                    anchor = WidgetAnchor.TOP_LEFT,
                    offsetX = 16f,
                    offsetY = -4f,
                    width = WidgetExtent.Fraction(0.5f),
                    rotation = 12f,
                    opacity = 0.8f,
                ),
                WidgetLayerSpec(
                    source = WidgetSource.Overlap(
                        listOf(
                            WidgetLayerSpec(WidgetSource.Image("widgets/leaf.png", WidgetSource.Image.Fit.FIT)),
                            WidgetLayerSpec(WidgetSource.Shape(WidgetSource.Shape.Kind.OVAL), visible = false),
                        ),
                    ),
                    anchor = WidgetAnchor.BOTTOM_RIGHT,
                    width = WidgetExtent.Dp(64f),
                    height = WidgetExtent.Dp(64f),
                ),
            ),
        )

        assertEquals(recipe, json.decodeFromString<WidgetRecipe>(json.encodeToString(recipe)))
    }

    @Test
    fun `the stored form is pinned`() {
        // The discriminators and field names are what a stored or shared recipe is made of. Renaming one in code
        // without meaning to would orphan every recipe written before it, and nothing else would fail.
        val recipe = WidgetRecipe(
            listOf(
                WidgetLayerSpec(
                    source = WidgetSource.Text("\$df(hh:mm)\$"),
                    anchor = WidgetAnchor.TOP_LEFT,
                    offsetX = 12f,
                    width = WidgetExtent.Fill,
                ),
            ),
        )

        assertEquals(
            """{"layers":[{"source":{"type":"text","text":"${'$'}df(hh:mm)${'$'}"},"anchor":"TOP_LEFT",""" +
                """"offsetX":12.0,"width":{"type":"fraction","value":1.0}}]}""",
            json.encodeToString(recipe),
        )
    }

    @Test
    fun `a layer stored as only its source reads back at the defaults`() {
        val layer = json.decodeFromString<WidgetRecipe>("""{"layers":[{"source":{"type":"shape"}}]}""").layers.single()

        assertEquals(WidgetLayerSpec(WidgetSource.Shape()), layer)
        assertEquals(WidgetAnchor.CENTER, layer.anchor)
        assertEquals(WidgetExtent.Content, layer.width)
    }

    @Test
    fun `an unknown key is dropped rather than throwing`() {
        // What a newer build writes when it adds a field — effects on a layer, say.
        val recipe = json.decodeFromString<WidgetRecipe>(
            """{"layers":[{"source":{"type":"text","text":"hi","shadow":2},"effects":[]}],"globals":[]}""",
        )

        assertEquals(WidgetSource.Text("hi"), recipe.layers.single().source)
    }

    @Test
    fun `an unknown source kind throws, so the store must catch it`() {
        // The case ignoreUnknownKeys does not cover: a newer build's source kind is a discriminator this one cannot
        // map. Pinned so that a reader relying on it never silently becoming lenient is visible here.
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<WidgetRecipe>("""{"layers":[{"source":{"type":"progress"}}]}""")
        }
    }
}
