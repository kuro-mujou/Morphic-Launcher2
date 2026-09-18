package inkspire.morphic.core.model.widget

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGlobalsTest {

    private val globals = WidgetGlobals(
        listOf(
            WidgetGlobal.Color("text", "Text color", 0xFF112233.toInt()),
            WidgetGlobal.Number("round", "Roundness", value = 12f, min = 0f, max = 48f),
            WidgetGlobal.Switch("showDate", "Show date", false),
            WidgetGlobal.Choice("style", "Style", listOf("Light", "Dark"), selected = 1),
            WidgetGlobal.Font("font", "Font", WidgetSource.Text.Font.SERIF),
            WidgetGlobal.Text("name", "Name", "Ada"),
        ),
    )

    @Test
    fun `a binding reads its global`() {
        assertEquals(0xFF112233.toInt(), globals.color("text", fallback = 0))
        assertEquals(12f, globals.number("round", fallback = 0f))
        assertEquals(false, globals.switch("showDate", fallback = true))
        assertEquals(WidgetSource.Text.Font.SERIF, globals.font("font", fallback = WidgetSource.Text.Font.SANS))
    }

    @Test
    fun `an absent, unknown or mistyped binding falls back to the property's own value`() {
        assertEquals(7, globals.color(null, fallback = 7))
        assertEquals(7, globals.color("nope", fallback = 7))
        // A color binding naming a number is a broken binding, not a crash.
        assertEquals(7, globals.color("round", fallback = 7))
        assertEquals(true, globals.switch("text", fallback = true))
    }

    @Test
    fun `formulas read every global as text`() {
        assertEquals(
            mapOf(
                "text" to "#FF112233",
                "round" to "12",
                "showDate" to "0",
                "style" to "Dark",
                "font" to "serif",
                "name" to "Ada",
            ),
            globals.asScriptValues(),
        )
    }

    @Test
    fun `a layer switched off by a global is not drawn`() {
        val layers = listOf(
            WidgetLayerSpec(WidgetSource.Text("time")),
            WidgetLayerSpec(WidgetSource.Text("date"), visibleGlobal = "showDate"),
            WidgetLayerSpec(WidgetSource.Text("hidden"), visible = false),
        )
        assertEquals(listOf("time"), layers.drawn(globals).map { (it.source as WidgetSource.Text).text })
    }

    @Test
    fun `a recipe with globals and bindings survives a round trip`() {
        val json = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        val recipe = WidgetRecipe(
            layers = listOf(
                WidgetLayerSpec(
                    WidgetSource.Text("x", colorGlobal = "text", sizeGlobal = "size", fontGlobal = "font"),
                    visibleGlobal = "show",
                ),
                WidgetLayerSpec(WidgetSource.Shape(colorGlobal = "panel", cornerRadiusGlobal = "round")),
            ),
            globals = listOf(
                WidgetGlobal.Color("text", "Text", 1),
                WidgetGlobal.Number("size", "Size", 3f, 1f, 9f),
                WidgetGlobal.Switch("show", "Show", true),
                WidgetGlobal.Choice("style", "Style", listOf("A", "B"), 1),
                WidgetGlobal.Font("font", "Font", WidgetSource.Text.Font.MONO),
                WidgetGlobal.Text("name", "Name", "hi"),
            ),
        )
        assertEquals(recipe, json.decodeFromString<WidgetRecipe>(json.encodeToString(recipe)))
    }

    @Test
    fun `a group's globals shadow the recipe's, and the rest show through`() {
        val block = WidgetSource.Overlap(globals = listOf(WidgetGlobal.Color("text", "Text color", 0xFFABCDEF.toInt())))
        val inner = globals.inside(block)

        assertEquals(0xFFABCDEF.toInt(), inner.color("text", fallback = 0))
        assertEquals(12f, inner.number("round", fallback = 0f))
        // The outer scope is untouched: a second block reading `text` outside this one still gets the recipe's.
        assertEquals(0xFF112233.toInt(), globals.color("text", fallback = 0))
    }

    @Test
    fun `the nearest global of a name decides, even when it is mistyped`() {
        // Falling through to an outer global of the right type would make a broken binding read someone else's
        // setting — silently, and only while that other global happened to exist.
        val block = WidgetSource.Overlap(globals = listOf(WidgetGlobal.Switch("text", "Text", true)))
        assertEquals(7, globals.inside(block).color("text", fallback = 7))
    }

    @Test
    fun `a formula inside a group reads the nearest value of each name`() {
        val block = WidgetSource.Overlap(globals = listOf(WidgetGlobal.Text("name", "Name", "Grace")))
        val values = globals.inside(block).asScriptValues()

        assertEquals("Grace", values["name"])
        assertEquals("12", values["round"])
    }

    @Test
    fun `a group declaring nothing opens no scope`() {
        assertEquals(globals, globals.inside(WidgetSource.Overlap()))
    }

    @Test
    fun `a recipe is styleable through its own globals or any block's`() {
        val block = WidgetLayerSpec(
            WidgetSource.Overlap(globals = listOf(WidgetGlobal.Switch("h24", "24-hour", true))),
            name = "Time",
        )
        assertEquals(false, WidgetRecipe(listOf(WidgetLayerSpec(WidgetSource.Overlap()))).isStyleable)
        assertEquals(true, WidgetRecipe(listOf(block)).isStyleable)
        assertEquals(true, WidgetRecipe(globals = listOf(WidgetGlobal.Switch("a", "A", true))).isStyleable)
    }
}
