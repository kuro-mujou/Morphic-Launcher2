package inkspire.morphic.feature.settings.widgetstudio

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerFieldTest {

    private val none = WidgetGlobals(emptyList())

    private fun LayerField.Setting.write(layer: WidgetLayerSpec, change: (WidgetGlobal) -> WidgetGlobal) =
        apply(layer, change(control))

    private fun List<LayerField>.setting(key: String) = filterIsInstance<LayerField.Setting>().single { it.key == key }

    @Test
    fun `every key is unique within a layer`() {
        listOf(
            WidgetLayerSpec(WidgetSource.Text("x")),
            WidgetLayerSpec(WidgetSource.Shape()),
            WidgetLayerSpec(WidgetSource.Progress("1", kind = WidgetSource.Progress.Kind.ARC)),
            WidgetLayerSpec(WidgetSource.Overlap(), width = WidgetExtent.Dp(4f), height = WidgetExtent.Fill),
        ).forEach { layer ->
            val keys = layerFields(layer, none).map { it.key }
            assertEquals("$layer", keys.distinct(), keys)
        }
    }

    @Test
    fun `the anchor is edited as a column and a row that together name one of nine`() {
        val layer = WidgetLayerSpec(WidgetSource.Text("x"), anchor = WidgetAnchor.TOP_RIGHT)
        val fields = layerFields(layer, none)
        val down = fields.setting("down")

        assertEquals(2, (fields.setting("across").control as WidgetGlobal.Choice).selected)
        assertEquals(0, (down.control as WidgetGlobal.Choice).selected)
        val moved = down.write(layer) { (it as WidgetGlobal.Choice).copy(selected = 2) }
        assertEquals(WidgetAnchor.BOTTOM_RIGHT, moved.anchor)
    }

    @Test
    fun `a property a setting decides shows as bound, and unbinding hands it back`() {
        val layer = WidgetLayerSpec(WidgetSource.Text("x", colorGlobal = "accent"))
        val scope = WidgetGlobals(listOf(WidgetGlobal.Color("accent", "Accent", 1)))

        val bound = layerFields(layer, scope).filterIsInstance<LayerField.Bound>().single()
        assertEquals("Accent", bound.setting)
        assertEquals(null, (bound.unbind(layer).source as WidgetSource.Text).colorGlobal)
    }

    @Test
    fun `a binding to a setting that does not exist is edited like any property`() {
        // It draws the property's own value, so a control on it is not a control that changes nothing.
        val layer = WidgetLayerSpec(WidgetSource.Text("x", colorGlobal = "gone"))
        assertTrue(layerFields(layer, none).none { it is LayerField.Bound })
    }

    @Test
    fun `percent sliders write fractions, and a weight lands on a hundred`() {
        val layer = WidgetLayerSpec(WidgetSource.Text("x"))
        val fields = layerFields(layer, none)

        val faded = fields.setting("opacity").write(layer) { (it as WidgetGlobal.Number).copy(value = 40f) }
        assertEquals(0.4f, faded.opacity, 1e-6f)
        val heavier = fields.setting("weight").write(layer) { (it as WidgetGlobal.Number).copy(value = 640f) }
        assertEquals(600, (heavier.source as WidgetSource.Text).weight)
    }

    @Test
    fun `a size is offered for a fixed or shared extent and not for a fitted one`() {
        assertTrue(layerFields(WidgetLayerSpec(WidgetSource.Shape()), none).none { it.key == "width.dp" })
        val fixed = WidgetLayerSpec(WidgetSource.Shape(), width = WidgetExtent.Dp(40f))
        assertEquals(40f, (layerFields(fixed, none).setting("width.dp").control as WidgetGlobal.Number).value)
    }

    @Test
    fun `a stack's layer is not offered the placement the stack decides for it`() {
        val layer = WidgetLayerSpec(WidgetSource.Text("x"))
        val keys = layerFields(layer, none, inStack = true).map { it.key }

        assertTrue(keys.none { it in setOf("across", "down", "x", "y") })
        assertTrue("width" in keys && "opacity" in keys)
    }
}
