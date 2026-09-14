package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Look
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** What applying a look writes, what capturing one reads, and that a look survives being a file. */
class LookPlanTest {

    private val listRegister = """{"homeLayout":"LIST_WITH_WIDGET_AREA"}"""

    private fun look(vararg slices: Pair<String, String>) =
        Look(name = "Test", slices = slices.associate { (name, value) -> name to Json.parseToJsonElement(value) })

    private fun defaultOf(name: String) = SettingsSlices.getValue(name).canonical(null)

    private fun canonicalOf(name: String, stored: String) = SettingsSlices.getValue(name).canonical(stored)

    @Test
    fun `a slice the look holds is written`() {
        val writes = lookWrites(look(SurfaceRegisterKey to listRegister))

        assertEquals(mapOf(SurfaceRegisterKey to canonicalOf(SurfaceRegisterKey, listRegister)), writes)
    }

    @Test
    fun `an unknown slice name is ignored`() {
        val writes = lookWrites(look("no_such_slice" to "{}", SurfaceRegisterKey to listRegister))

        assertEquals(setOf(SurfaceRegisterKey), writes.keys)
    }

    @Test
    fun `no excluded slice is ever written`() {
        val everyExcluded = LookScope.excluded.map { it to defaultOf(it) }.toTypedArray()

        assertEquals(emptyMap<String, String>(), lookWrites(look(*everyExcluded)))
    }

    @Test
    fun `a carried slice the look does not hold is left alone`() {
        val writes = lookWrites(look(SurfaceRegisterKey to listRegister))

        assertEquals(false, "backdrop_effect" in writes)
    }

    @Test
    fun `a value that does not read as its slice is not written`() {
        val writes = lookWrites(look(SurfaceRegisterKey to """{"homeLayout":"SIDEWAYS"}"""))

        assertEquals(emptyMap<String, String>(), writes)
    }

    @Test
    fun `an icon recipe re-stamps the applied preset with the look's own name`() {
        val writes = lookWrites(
            look(LookScope.IconAppearance to defaultOf(LookScope.IconAppearance), LookScope.IconAppliedPreset to "\"Mono\""),
        )

        assertEquals("\"Mono\"", writes[LookScope.IconAppliedPreset])
    }

    @Test
    fun `an icon recipe with no preset name clears the stamp`() {
        val writes = lookWrites(look(LookScope.IconAppearance to defaultOf(LookScope.IconAppearance)))

        assertEquals("null", writes[LookScope.IconAppliedPreset])
    }

    @Test
    fun `a preset name without an icon recipe is not written`() {
        assertEquals(emptyMap<String, String>(), lookWrites(look(LookScope.IconAppliedPreset to "\"Mono\"")))
    }

    @Test
    fun `a capture holds every carried slice and nothing else`() {
        val captured = captureLook(name = "Fresh", stored = { null })

        assertEquals(LookScope.carried, captured.slices.keys)
    }

    @Test
    fun `a capture applied back writes what was stored, and every other carried slice at its default`() {
        val stored = mapOf(SurfaceRegisterKey to listRegister, "onboarding" to """{"completed":true}""")

        val writes = lookWrites(captureLook(name = "Mine", stored = stored::get))

        assertEquals(LookScope.carried, writes.keys)
        assertEquals(canonicalOf(SurfaceRegisterKey, listRegister), writes[SurfaceRegisterKey])
        assertEquals(defaultOf("backdrop_effect"), writes["backdrop_effect"])
    }

    @Test
    fun `a look survives being a file`() {
        val captured = captureLook(name = "Mine", stored = mapOf(SurfaceRegisterKey to listRegister)::get)

        val read = Look.parse(captured.toJson())

        assertEquals(captured.name, read.name)
        assertEquals(captured.slices, read.slices)
    }
}
