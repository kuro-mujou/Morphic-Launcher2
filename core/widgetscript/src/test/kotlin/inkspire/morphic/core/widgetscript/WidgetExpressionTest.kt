package inkspire.morphic.core.widgetscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WidgetExpressionTest {

    @Test
    fun `text without formulas is itself`() {
        assertEquals("Good morning", eval("Good morning"))
        assertEquals("", eval(""))
    }

    @Test
    fun `formulas are spliced into the text around them`() {
        assertEquals("It is 07:14 PM now", eval("It is \$df(hh:mm a)\$ now"))
        assertEquals("19·Friday", eval("\$df(HH)\$·\$df(EEEE)\$"))
    }

    @Test
    fun `a doubled dollar is a literal one`() {
        assertEquals("\$5 at 19", eval("\$\$5 at \$df(HH)\$"))
    }

    @Test
    fun `a dollar inside quotes does not close the formula`() {
        assertEquals("A\$B", eval("\$tc(up, \"a\$b\")\$"))
    }

    @Test
    fun `the zone is the widget's own`() {
        val tokyo = object : ScriptData by FixtureData {
            override val zone: ZoneId = ZoneId.of("Asia/Tokyo")
        }
        assertEquals("04:14", eval("\$df(HH:mm)\$", tokyo))
    }

    // --- providers -----------------------------------------------------------------------------------------------

    @Test
    fun `text reads nothing`() {
        assertEquals(emptySet<ProviderId>(), WidgetExpression.parse("static \$tc(up, hi)\$ \$mu(max, 1, 2)\$").providers)
    }

    @Test
    fun `a date reads the clock`() {
        assertEquals(setOf(ProviderId.CLOCK), WidgetExpression.parse("\$df(hh:mm)\$").providers)
    }

    @Test
    fun `a provider nested inside another call is found`() {
        assertEquals(setOf(ProviderId.CLOCK), WidgetExpression.parse("\$tc(up, df(EEEE))\$").providers)
    }

    @Test
    fun `a provider in a branch not taken is still declared`() {
        // The cadence cannot depend on which branch ran last: the condition could flip with nothing redrawing it.
        val expression = WidgetExpression.parse("\$if(0, df(ss), static)\$")
        assertEquals("static", expression.evaluate(FixtureData).text)
        assertEquals(setOf(ProviderId.CLOCK), expression.providers)
    }

    @Test
    fun `a formula that does not parse declares nothing`() {
        assertEquals(emptySet<ProviderId>(), WidgetExpression.parse("\$df(hh:mm\$").providers)
    }

    // --- problems ------------------------------------------------------------------------------------------------

    @Test
    fun `an unknown function stays as typed and is reported where it is`() {
        val result = WidgetExpression.parse("x \$zz(1)\$ y").evaluate(FixtureData)
        assertEquals("x \$zz(1)\$ y", result.text)
        assertEquals(listOf(3..4), result.problems.map { it.range })
    }

    @Test
    fun `a wrong argument count is reported without running anything`() {
        val expression = WidgetExpression.parse("\$df(a, b)\$")
        assertEquals("df takes 1 argument", expression.problems.single().message)
    }

    @Test
    fun `an unclosed dollar leaves the rest as text`() {
        val result = WidgetExpression.parse("cost \$df(hh").evaluate(FixtureData)
        assertEquals("cost \$df(hh", result.text)
        assertEquals(listOf(5..5), result.problems.map { it.range })
    }

    @Test
    fun `an unclosed quote is reported rather than swallowing the text`() {
        val result = WidgetExpression.parse("\$tc(up, \"abc)\$ tail").evaluate(FixtureData)
        assertEquals("\$tc(up, \"abc)\$ tail", result.text)
        assertEquals("Unclosed quote", result.problems.single().message)
    }

    @Test
    fun `a failure while running shows the formula and keeps the rest`() {
        val result = WidgetExpression.parse("a \$1/0\$ b \$df(HH)\$").evaluate(FixtureData)
        assertEquals("a \$1/0\$ b 19", result.text)
        assertEquals("Division by zero", result.problems.single().message)
    }

    @Test
    fun `a stray closing parenthesis is reported`() {
        assertTrue(WidgetExpression.parse("\$1 + 2)\$").problems.isNotEmpty())
    }

    @Test
    fun `an empty formula is reported`() {
        assertEquals("Expression ends too soon", WidgetExpression.parse("\$ \$").problems.single().message)
    }
}
