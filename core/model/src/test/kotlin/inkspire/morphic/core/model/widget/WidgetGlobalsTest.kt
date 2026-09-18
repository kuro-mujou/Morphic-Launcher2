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
}
