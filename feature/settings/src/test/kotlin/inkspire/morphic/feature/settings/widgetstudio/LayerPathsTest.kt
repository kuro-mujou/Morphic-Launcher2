package inkspire.morphic.feature.settings.widgetstudio

import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerPathsTest {

    private val time = WidgetLayerSpec(WidgetSource.Text("time"))
    private val date = WidgetLayerSpec(WidgetSource.Text("date"))

    /** A background, then a block holding two lines and owning a color. */
    private val recipe = WidgetRecipe(
        layers = listOf(
            WidgetLayerSpec(WidgetSource.Shape()),
            WidgetLayerSpec(
                WidgetSource.Overlap(listOf(time, date), globals = listOf(WidgetGlobal.Color("text", "Block text", 1))),
                name = "Clock",
            ),
        ),
        globals = listOf(WidgetGlobal.Color("text", "Widget text", 2)),
    )

    @Test
    fun `a path leads down through groups`() {
        assertEquals(date, recipe.layerAt(listOf(1, 1)))
        assertNull(recipe.layerAt(emptyList()))
        assertNull(recipe.layerAt(listOf(1, 5)))
        assertNull(recipe.layerAt(listOf(0, 0)))
    }

    @Test
    fun `a change deep in the tree reaches that layer alone`() {
        val edited = recipe.updatedAt(listOf(1, 0)) { it.copy(offsetX = 9f) }

        assertEquals(9f, edited.layerAt(listOf(1, 0))?.offsetX)
        assertEquals(date, edited.layerAt(listOf(1, 1)))
        assertEquals(recipe.layers[0], edited.layers[0])
        assertEquals(recipe, recipe.updatedAt(listOf(1, 7)) { it.copy(offsetX = 9f) })
    }

    @Test
    fun `a layer is removed from its own group`() {
        val removed = recipe.removedAt(listOf(1, 0))

        assertEquals(listOf(date), removed.childrenAt(listOf(1)))
        assertEquals(2, removed.layers.size)
    }

    @Test
    fun `only the widget and groups hold layers`() {
        assertEquals(true, recipe.isContainer(emptyList()))
        assertEquals(true, recipe.isContainer(listOf(1)))
        assertEquals(false, recipe.isContainer(listOf(1, 0)))
    }

    @Test
    fun `a layer inside a block reads the block's settings, the block itself the widget's`() {
        // The block's own globals are a scope for what is inside it, not for the block as a layer.
        assertEquals(1, recipe.globalsAt(listOf(1, 0)).color("text", fallback = 0))
        assertEquals(2, recipe.globalsAt(listOf(1)).color("text", fallback = 0))
    }

    @Test
    fun `repeated labels are numbered from the second`() {
        val shape = WidgetLayerSpec(WidgetSource.Shape())
        assertEquals(listOf("Text", "Shape", "Text 2", "Clock"), listOf(time, shape, date, recipe.layers[1]).labels())
    }
}
