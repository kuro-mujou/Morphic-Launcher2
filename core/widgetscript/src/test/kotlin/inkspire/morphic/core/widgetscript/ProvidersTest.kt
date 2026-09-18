package inkspire.morphic.core.widgetscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a script declares it reads, and how often its clock matters — the two things a widget's cadence is made of. */
class ProvidersTest {

    private fun tick(source: String) = WidgetExpression.parse(source).clockTick

    @Test
    fun `a pattern ticks at its finest field`() {
        assertEquals(ClockTick.SECOND, tick("\$df(HH:mm:ss)\$"))
        assertEquals(ClockTick.MINUTE, tick("\$df(hh:mm)\$"))
        assertEquals(ClockTick.HOUR, tick("\$df(h a)\$"))
        assertEquals(ClockTick.DAY, tick("\$df(EEEE)\$"))
        assertEquals(ClockTick.DAY, tick("\$df(\"d MMMM yyyy\")\$"))
    }

    @Test
    fun `an unquoted date pattern with operators in it is still a fixed pattern`() {
        // dd-MM-yyyy parses as words and minus signs; it must not fall back to every second for it.
        assertEquals(ClockTick.DAY, tick("\$df(dd-MM-yyyy)\$"))
        assertEquals(ClockTick.MINUTE, tick("\$df(hh:mm a)\$"))
    }

    @Test
    fun `quoted text in a pattern is not read as fields`() {
        assertEquals(ClockTick.DAY, tick("\$df(\"'on a' EEEE\")\$"))
    }

    @Test
    fun `a pattern built at run time ticks every second`() {
        assertEquals(ClockTick.SECOND, tick("\$df(tc(low, EEEE))\$"))
    }

    @Test
    fun `the whole script ticks at its finest call`() {
        assertEquals(ClockTick.MINUTE, tick("\$df(EEEE)\$ at \$df(HH:mm)\$"))
    }

    @Test
    fun `a script that does not read the clock has no tick`() {
        assertNull(tick("\$bi(level)\$% \$tc(up, x)\$"))
        assertNull(tick("plain"))
    }

    @Test
    fun `each provider is declared by the functions that read it`() {
        assertEquals(setOf(ProviderId.BATTERY), WidgetExpression.parse("\$bi(level)\$").providers)
        assertEquals(setOf(ProviderId.SYSTEM), WidgetExpression.parse("\$si(model)\$").providers)
        assertEquals(
            setOf(ProviderId.CLOCK, ProviderId.BATTERY),
            WidgetExpression.parse("\$if(bi(charging), df(HH:mm), x)\$").providers,
        )
    }

    @Test
    fun `bi reads the battery`() {
        assertEquals("92%", eval("\$bi(level)\$%"))
        assertEquals("1", eval("\$bi(charging)\$"))
        assertEquals("usb", eval("\$bi(source)\$"))
        assertEquals("31.5", eval("\$bi(temp)\$"))
        assertEquals("low", eval("\$if(bi(level) < 95, low, full)\$"))
    }

    @Test
    fun `si reads the device`() {
        assertEquals("Google Pixel 9 on Android 16", eval("\$si(man)\$ \$si(model)\$ on Android \$si(aver)\$"))
        assertEquals("dark", eval("\$if(si(darkmode), dark, light)\$"))
    }

    @Test
    fun `an unknown key is reported`() {
        val result = WidgetExpression.parse("\$bi(volts)\$").evaluate(FixtureData)
        assertEquals("bi has no \"volts\"", result.problems.single().message)
    }
}
