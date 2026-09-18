package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetGlobal
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
    fun `a progress's value is read`() {
        // A battery ring reads the battery through its value alone; missed, it would be drawn once and never again.
        val recipe = WidgetRecipe(listOf(WidgetLayerSpec(WidgetSource.Progress("\$bi(level)\$"))))
        assertEquals(WidgetCadence(setOf(ProviderId.BATTERY), clockTick = null), WidgetCadence.of(recipe))
    }

    @Test
    fun `a hidden layer asks for nothing`() {
        // A hidden seconds clock would otherwise wake the widget every second to draw nothing.
        val recipe = WidgetRecipe(listOf(text("\$df(EEEE)\$"), text("\$df(ss)\$", visible = false)))
        assertEquals(ClockTick.DAY, WidgetCadence.of(recipe).clockTick)

        val hiddenGroup = WidgetLayerSpec(WidgetSource.Overlap(listOf(text("\$bi(level)\$"))), visible = false)
        assertEquals(emptySet<ProviderId>(), WidgetCadence.of(WidgetRecipe(listOf(hiddenGroup))).providers)
    }

    @Test
    fun `a layer a switch turned off asks for nothing`() {
        val recipe = WidgetRecipe(
            layers = listOf(text("\$df(EEEE)\$"), WidgetLayerSpec(WidgetSource.Text("\$df(ss)\$"), visibleGlobal = "secs")),
            globals = listOf(WidgetGlobal.Switch("secs", "Seconds", false)),
        )
        assertEquals(ClockTick.DAY, WidgetCadence.of(recipe).clockTick)
        val on = recipe.copy(globals = listOf(WidgetGlobal.Switch("secs", "Seconds", true)))
        assertEquals(ClockTick.SECOND, WidgetCadence.of(on).clockTick)
    }

    @Test
    fun `a block's own switch is read in its own scope`() {
        // The recipe turns seconds on and the block turns them off; the block's is the nearer, so nothing ticks.
        val block = WidgetLayerSpec(
            WidgetSource.Overlap(
                layers = listOf(WidgetLayerSpec(WidgetSource.Text("\$df(ss)\$"), visibleGlobal = "secs")),
                globals = listOf(WidgetGlobal.Switch("secs", "Seconds", false)),
            ),
            name = "Time",
        )
        val recipe = WidgetRecipe(listOf(block), globals = listOf(WidgetGlobal.Switch("secs", "Seconds", true)))
        assertEquals(WidgetCadence(emptySet(), clockTick = null), WidgetCadence.of(recipe))
    }

    @Test
    fun `no starter design wakes every second`() {
        // The guard on the design library: a template that ticks per second costs every user who places it battery
        // for something a person never asked to see, and nothing on screen would say so.
        BuiltInWidgetTemplates.all.forEach { template ->
            val tick = WidgetCadence.of(template.recipe).clockTick
            assert(tick == null || tick > ClockTick.SECOND) { "${template.id} ticks $tick" }
        }
    }

    @Test
    fun `no library block wakes every second`() {
        // The same guard for what a user adds by hand: a block ticking per second would cost battery on every widget
        // it is added to.
        BuiltInBlocks.all.forEach { block ->
            val tick = WidgetCadence.of(WidgetRecipe(listOf(block.layer))).clockTick
            assert(tick == null || tick > ClockTick.SECOND) { "${block.id} ticks $tick" }
        }
    }
}
