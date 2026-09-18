package inkspire.morphic.core.model.widget

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPathsTest {

    private val line = WidgetLayerSpec(WidgetSource.Text("x"))

    /** A block owning a 24-hour switch, with a line inside; the widget owns its own switch of the same name. */
    private val recipe = WidgetRecipe(
        layers = listOf(
            WidgetLayerSpec(WidgetSource.Shape()),
            WidgetLayerSpec(
                WidgetSource.Overlap(
                    listOf(line),
                    globals = listOf(
                        WidgetGlobal.Switch("h24", "24-hour", true),
                        WidgetGlobal.Choice("style", "Style", listOf("A", "B", "C"), selected = 2),
                    ),
                ),
                name = "Time",
            ),
        ),
        globals = listOf(WidgetGlobal.Switch("h24", "24-hour", true), WidgetGlobal.Color("panel", "Background", 1)),
    )

    private fun WidgetRecipe.blockGlobals() = (layers[1].source as WidgetSource.Overlap).globals

    @Test
    fun `a path leads down through groups, and nowhere past them`() {
        assertEquals(line, recipe.layerAt(listOf(1, 0)))
        assertNull(recipe.layerAt(emptyList()))
        assertNull(recipe.layerAt(listOf(1, 3)))
        assertNull(recipe.layerAt(listOf(0, 0)))
    }

    @Test
    fun `a tap flips the nearest setting of the name - the block's, not the widget's`() {
        val flipped = recipe.withFlipped(listOf(1, 0), "h24")

        assertEquals(false, (flipped.blockGlobals()[0] as WidgetGlobal.Switch).value)
        assertEquals(recipe.globals, flipped.globals)
    }

    @Test
    fun `a tap on a block reaches the block's own settings`() {
        assertEquals(false, (recipe.withFlipped(listOf(1), "h24").blockGlobals()[0] as WidgetGlobal.Switch).value)
        // The block's own settings are in a tap's scope on the block, and not on a layer outside it.
        assertEquals("style", recipe.tapScope(listOf(1))["style"]?.name)
        assertNull(recipe.tapScope(listOf(0))["style"])
    }

    @Test
    fun `outside any block the widget's own setting flips`() {
        val flipped = recipe.withFlipped(listOf(0), "h24")
        assertEquals(false, (flipped.globals[0] as WidgetGlobal.Switch).value)
        assertEquals(recipe.blockGlobals(), flipped.blockGlobals())
    }

    @Test
    fun `a choice steps to its next option, wrapping`() {
        val stepped = recipe.withFlipped(listOf(1), "style")
        assertEquals(0, (stepped.blockGlobals()[1] as WidgetGlobal.Choice).selected)
    }

    @Test
    fun `a setting that cannot flip, or does not exist, changes nothing`() {
        assertEquals(recipe, recipe.withFlipped(listOf(0), "panel"))
        assertEquals(recipe, recipe.withFlipped(listOf(0), "nope"))
    }

    @Test
    fun `a tap is stored under pinned names`() {
        val json = Json { encodeDefaults = false }
        val run = WidgetLayerSpec(
            WidgetSource.Text("x"),
            onTap = WidgetTap.Run(GestureAction.LaunchApp(ComponentKey("com.example", "com.example.Main", 0L))),
        )
        val flip = WidgetLayerSpec(WidgetSource.Text("x"), onTap = WidgetTap.Flip("h24"))

        assertEquals(run, json.decodeFromString<WidgetLayerSpec>(json.encodeToString(run)))
        assertEquals(
            """{"source":{"type":"text","text":"x"},"onTap":{"type":"flip","global":"h24"}}""",
            json.encodeToString(flip),
        )
    }
}
