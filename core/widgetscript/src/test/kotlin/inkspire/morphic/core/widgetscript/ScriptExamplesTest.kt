package inkspire.morphic.core.widgetscript

import inkspire.morphic.core.widgetscript.function.ScriptFunctions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The examples are the language's documentation, shown to people learning it — so they are held to the language
 * rather than trusted to keep up with it.
 */
class ScriptExamplesTest {

    /** The fixture, with the one setting the `gv` example reads. */
    private val data = FixtureData.withGlobals(mapOf("text" to "#FFFFFFFF"))

    @Test
    fun `every example runs without a problem`() {
        ScriptExamples.all.forEach { example ->
            val result = WidgetExpression.parse(example.formula).evaluate(data)
            assertEquals("${example.title}: ${example.formula}", emptyList<ScriptProblem>(), result.problems)
        }
    }

    @Test
    fun `every function the language has is shown, and every family shown exists`() {
        val families = ScriptExamples.all.map { it.family }.toSet()
        assertEquals(ScriptFunctions.keys + "if", families)
        assertEquals(families, ScriptExamples.families.keys)
    }

    @Test
    fun `examples show what their titles say`() {
        fun shown(title: String) = eval(ScriptExamples.all.single { it.title == title }.formula, data)

        assertEquals("19:14", shown("Time, 24-hour"))
        assertEquals("92%", shown("Battery level"))
        assertEquals("FRIDAY", shown("Upper case"))
        assertEquals("Sep", shown("First three letters"))
        assertEquals("1154", shown("Minutes into the day"))
        assertEquals("Evening", shown("Morning or evening"))
        assertEquals("Day", shown("Night or day"))
    }

    @Test
    fun `a formula reads its settings through withGlobals`() {
        assertEquals("#FFFFFFFF", eval("\$gv(text)\$", data))
        assertTrue(WidgetExpression.parse("\$gv(text)\$").evaluate(FixtureData).problems.isNotEmpty())
    }
}
