package inkspire.morphic.core.widgetscript

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluatorTest {

    @Test
    fun `arithmetic follows precedence`() {
        assertEquals("7", eval("\$1 + 2 * 3\$"))
        assertEquals("9", eval("\$(1 + 2) * 3\$"))
        assertEquals("1", eval("\$7 % 3\$"))
        assertEquals("-4", eval("\$-(2 + 2)\$"))
        assertEquals("2", eval("\$5 - 2 - 1\$"))
    }

    @Test
    fun `whole numbers print without a point`() {
        assertEquals("92", eval("\$46 * 2\$"))
        assertEquals("0.5", eval("\$1 / 2\$"))
        assertEquals("0.3333333333", eval("\$1 / 3\$"))
    }

    @Test
    fun `a computed number is not re-read from its formatting`() {
        assertEquals("1", eval("\$1 / 3 * 3\$"))
    }

    @Test
    fun `a literal keeps its spelling until arithmetic needs it`() {
        assertEquals("007", eval("\$007\$"))
        assertEquals("8", eval("\$007 + 1\$"))
    }

    @Test
    fun `an operator between words is the text that was written`() {
        assertEquals("dd-MM-yyyy", eval("\$dd-MM-yyyy\$"))
        assertEquals("dd / MM", eval("\$dd / MM\$"))
        assertEquals("-x", eval("\$-x\$"))
    }

    @Test
    fun `words side by side keep the space between them`() {
        assertEquals("hh:mm  a", eval("\$hh:mm  a\$"))
        assertEquals("up 19", eval("\$up df(HH)\$"))
    }

    @Test
    fun `a name with a space before its parenthesis is not a call`() {
        assertEquals("tc x", eval("\$tc (x)\$"))
    }

    @Test
    fun `equality compares text when either side is not a number`() {
        assertEquals("1", eval("\$abc = abc\$"))
        assertEquals("0", eval("\$abc = ABC\$"))
        assertEquals("1", eval("\$1.0 = 1\$"))
        assertEquals("1", eval("\$a != b\$"))
    }

    @Test
    fun `comparisons and logic yield one or zero`() {
        assertEquals("1", eval("\$3 > 2 & 2 >= 2\$"))
        assertEquals("0", eval("\$3 < 2 | 0\$"))
    }

    @Test
    fun `if picks the first true condition`() {
        assertEquals("high", eval("\$if(92 > 50, high, low)\$"))
        assertEquals("low", eval("\$if(12 > 50, high, low)\$"))
        assertEquals("mid", eval("\$if(40 > 50, high, 40 > 20, mid, low)\$"))
    }

    @Test
    fun `if with no match and no else is empty`() {
        assertEquals("[]", eval("[\$if(0, x)\$]"))
    }

    @Test
    fun `only the branch taken is evaluated`() {
        assertEquals("ok", eval("\$if(1, ok, 1/0)\$"))
    }

    @Test
    fun `truthiness is zero and empty`() {
        assertEquals("no", eval("\$if(\"\", yes, no)\$"))
        assertEquals("no", eval("\$if(0.0, yes, no)\$"))
        assertEquals("yes", eval("\$if(text, yes, no)\$"))
    }

    @Test
    fun `function names ignore case`() {
        assertEquals("ABC", eval("\$TC(UP, abc)\$"))
    }
}
