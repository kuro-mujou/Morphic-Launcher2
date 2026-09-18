package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.ClockTick
import inkspire.morphic.core.widgetscript.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCadenceTest {

    private fun text(source: String, visible: Boolean = true) = WidgetLayerSpec(WidgetSource.Text(source), visible = visible)

    @Test
    fun `static text asks for nothing`() {
        val recipe = WidgetRecipe(listOf(text("Hello"), WidgetLayerSpec(WidgetSource.Shape())))
        assertEquals(WidgetCadence(emptySet(), clockTick = null), WidgetCadence.of(recipe))
    }

    @Test
    fun `a date-only widget ticks daily`() {
        val recipe = WidgetRecipe(listOf(text("\$df(EEEE)\$"), text("\$df(\"d MMMM\")\$")))
        assertEquals(WidgetCadence(setOf(ProviderId.CLOCK), ClockTick.DAY), WidgetCadence.of(recipe))
    }

    @Test
    fun `the finest layer sets the tick, and every provider is gathered`() {
        val recipe = WidgetRecipe(listOf(text("\$df(EEEE)\$"), text("\$df(HH:mm)\$ \$bi(level)\$")))
        assertEquals(
            WidgetCadence(setOf(ProviderId.CLOCK, ProviderId.BATTERY), ClockTick.MINUTE),
            WidgetCadence.of(recipe),
        )
    }

    @Test
    fun `text inside a group is read`() {
        val recipe = WidgetRecipe(listOf(WidgetLayerSpec(WidgetSource.Overlap(listOf(text("\$si(model)\$"))))))
        assertEquals(WidgetCadence(setOf(ProviderId.SYSTEM), clockTick = null), WidgetCadence.of(recipe))
    }

    @Test
    fun `a hidden layer asks for nothing`() {
        // A hidden seconds clock would otherwise wake the widget every second to draw nothing.
        val recipe = WidgetRecipe(listOf(text("\$df(EEEE)\$"), text("\$df(ss)\$", visible = false)))
        assertEquals(ClockTick.DAY, WidgetCadence.of(recipe).clockTick)

        val hiddenGroup = WidgetLayerSpec(WidgetSource.Overlap(listOf(text("\$bi(level)\$"))), visible = false)
        assertEquals(emptySet<ProviderId>(), WidgetCadence.of(WidgetRecipe(listOf(hiddenGroup))).providers)
    }
}
