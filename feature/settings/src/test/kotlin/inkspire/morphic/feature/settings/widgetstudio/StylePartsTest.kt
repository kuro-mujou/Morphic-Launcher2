package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StylePartsTest {

    private fun color(value: Int) = WidgetGlobal.Color("text", "Text color", value)

    private fun block(name: String, value: Int) =
        WidgetLayerSpec(WidgetSource.Overlap(globals = listOf(color(value))), name = name)

    /** A background, then two blocks that each own a global named `text`. */
    private val recipe = WidgetRecipe(
        layers = listOf(WidgetLayerSpec(WidgetSource.Shape()), block("Time", 1), block("Date", 2)),
        globals = listOf(WidgetGlobal.Color("panel", "Background", 3)),
    )

    @Test
    fun `the parts are the named groups, by layer index`() {
        assertEquals(listOf(StylePart(1, "Time"), StylePart(2, "Date")), recipe.parts())
    }

    @Test
    fun `a named layer that is not a group is not a part`() {
        val named = WidgetRecipe(listOf(WidgetLayerSpec(WidgetSource.Text("hi"), name = "Label")))
        assertEquals(emptyList<StylePart>(), named.parts())
    }

    @Test
    fun `each part has its own settings, and the widget has the recipe's`() {
        assertEquals(listOf(color(2)), recipe.globalsOf(2))
        assertEquals(recipe.globals, recipe.globalsOf(null))
        assertEquals(emptyList<WidgetGlobal>(), recipe.globalsOf(0))
    }

    @Test
    fun `a change reaches the selected part and no other`() {
        // Both blocks call their color `text`: editing the Date's must leave the Time's alone.
        val edited = recipe.withGlobal(2, color(9))

        assertEquals(listOf(color(9)), edited.globalsOf(2))
        assertEquals(listOf(color(1)), edited.globalsOf(1))
        assertEquals(recipe.globals, edited.globals)
    }

    @Test
    fun `a change to a part that cannot hold it changes nothing`() {
        assertEquals(recipe, recipe.withGlobal(0, color(9)))
        assertEquals(recipe, recipe.withGlobal(7, color(9)))
    }

    @Test
    fun `a tap lands on the topmost part under it, or on the widget`() {
        val bounds = mapOf(1 to IntRect(0, 0, 100, 50), 2 to IntRect(50, 0, 150, 50))
        val parts = recipe.parts()

        assertEquals(1, partAt(Offset(10f, 10f), parts, bounds))
        // Where the two overlap, the later one is drawn on top and takes the tap.
        assertEquals(2, partAt(Offset(75f, 10f), parts, bounds))
        assertNull(partAt(Offset(10f, 80f), parts, bounds))
    }
}
