package inkspire.morphic.core.widgetscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FunctionsTest {

    private fun problem(source: String): String =
        WidgetExpression.parse(source).evaluate(FixtureData).problems.single().message

    // --- tc ------------------------------------------------------------------------------------------------------

    @Test
    fun `tc changes case`() {
        assertEquals("hello", eval("\$tc(low, HeLLo)\$"))
        assertEquals("HELLO", eval("\$tc(up, hello)\$"))
        assertEquals("Good Morning You", eval("\$tc(cap, \"good morning you\")\$"))
    }

    @Test
    fun `tc changes case in the script's locale`() {
        val turkish = object : ScriptData by FixtureData {
            override val locale: Locale = Locale.forLanguageTag("tr")
        }
        assertEquals("İ", eval("\$tc(up, i)\$", turkish))
    }

    @Test
    fun `tc measures and slices`() {
        assertEquals("5", eval("\$tc(len, hello)\$"))
        assertEquals("ell", eval("\$tc(cut, hello, 1, 3)\$"))
        assertEquals("llo", eval("\$tc(cut, hello, 2)\$"))
        assertEquals("lo", eval("\$tc(cut, hello, -2)\$"))
        assertEquals("", eval("\$tc(cut, hello, 9)\$"))
        assertEquals("llo", eval("\$tc(cut, hello, 2, 99999999999)\$"))
    }

    @Test
    fun `tc splits, and past the end is empty`() {
        assertEquals("b", eval("\$tc(split, \"a;b;c\", \";\", 1)\$"))
        assertEquals("", eval("\$tc(split, \"a;b\", \";\", 5)\$"))
    }

    @Test
    fun `tc replaces by pattern`() {
        assertEquals("x-x-x", eval("\$tc(reg, \"1-22-333\", \"[0-9]+\", x)\$"))
        assertEquals("b a", eval("\$tc(reg, \"a b\", \"(a) (b)\", \"\$2 \$1\")\$"))
    }

    @Test
    fun `tc pads without shortening`() {
        assertEquals("007", eval("\$tc(lpad, 7, 3, 0)\$"))
        assertEquals("ab  ", eval("\$tc(rpad, ab, 4)\$"))
        assertEquals("abcdef", eval("\$tc(lpad, abcdef, 3)\$"))
        assertEquals("ab", eval("\$tc(lpad, ab, -4)\$"))
    }

    @Test
    fun `tc counts`() {
        assertEquals("2", eval("\$tc(count, banana, an)\$"))
        assertEquals("0", eval("\$tc(count, banana, \"\")\$"))
    }

    @Test
    fun `tc reports what it cannot do`() {
        assertEquals("tc has no mode \"shout\"", problem("\$tc(shout, x)\$"))
        assertEquals("tc(split, …) takes 3 arguments after the mode", problem("\$tc(split, x)\$"))
        assertEquals("Length must be a number, not \"long\"", problem("\$tc(lpad, x, long)\$"))
        assertTrue(problem("\$tc(reg, x, \"(\", y)\$").startsWith("Not a valid pattern"))
    }

    // --- mu ------------------------------------------------------------------------------------------------------

    @Test
    fun `mu rounds half away from zero`() {
        assertEquals("3", eval("\$mu(round, 2.5)\$"))
        assertEquals("-3", eval("\$mu(round, -2.5)\$"))
        assertEquals("3.14", eval("\$mu(round, 3.14159, 2)\$"))
        assertEquals("1.01", eval("\$mu(round, 1.005, 2)\$"))
        assertEquals("1240", eval("\$mu(round, 1235, -1)\$"))
        assertEquals("0.1", eval("\$mu(round, 0.1, 1000000000)\$"))
    }

    @Test
    fun `mu does the rest`() {
        assertEquals("2", eval("\$mu(floor, 2.9)\$"))
        assertEquals("3", eval("\$mu(ceil, 2.1)\$"))
        assertEquals("4", eval("\$mu(abs, -4)\$"))
        assertEquals("3", eval("\$mu(sqrt, 9)\$"))
        assertEquals("1024", eval("\$mu(pow, 2, 10)\$"))
        assertEquals("-1", eval("\$mu(min, 3, -1, 2)\$"))
        assertEquals("3", eval("\$mu(max, 3, -1, 2)\$"))
    }

    @Test
    fun `mu reports what it cannot do`() {
        assertEquals("sqrt of a negative number", problem("\$mu(sqrt, -1)\$"))
        assertEquals("pow takes 2 numbers", problem("\$mu(pow, 2)\$"))
        assertEquals("mu has no operation \"rnd\"", problem("\$mu(rnd, 1, 9)\$"))
        assertEquals("floor must be a number, not \"x\"", problem("\$mu(floor, x)\$"))
    }

    // --- df ------------------------------------------------------------------------------------------------------

    @Test
    fun `df formats the clock`() {
        assertEquals("19:14:05", eval("\$df(HH:mm:ss)\$"))
        assertEquals("Friday, 18 September 2026", eval("\$df(\"EEEE, d MMMM yyyy\")\$"))
        assertEquals("at 07", eval("\$df(\"'at' hh\")\$"))
    }

    @Test
    fun `df reads in the script's locale`() {
        val french = object : ScriptData by FixtureData {
            override val locale: Locale = Locale.FRENCH
        }
        assertEquals("vendredi", eval("\$df(EEEE)\$", french))
    }

    @Test
    fun `df rejects a letter that is not a pattern letter`() {
        assertTrue(problem("\$df(hh:mm b)\$").startsWith("Not a date pattern"))
    }
}
