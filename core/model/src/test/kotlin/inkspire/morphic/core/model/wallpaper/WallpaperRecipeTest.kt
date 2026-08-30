package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The recipe is what gets stored and shared, so its serialization is a contract — a round trip has to be lossless,
 * and a recipe written before a field existed has to still read.
 */
class WallpaperRecipeTest {

    // The two icon stores' settings, so this checks the shape a real store round-trips: defaults are not written,
    // unknown keys are dropped rather than throwing.
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `a full recipe survives a round trip unchanged`() {
        val recipe = WallpaperRecipe(
            design = WallpaperDesign.LINEAR_GRADIENT,
            seed = 42L,
            params = DesignParams(density = 0.8f, variant = 2),
            palette = Palette(listOf(0xFF241B4E.toInt(), 0xFFB65A78.toInt(), 0xFFFFD9A0.toInt())),
            aspect = WallpaperAspect.SQUARED,
        )

        assertEquals(recipe, json.decodeFromString<WallpaperRecipe>(json.encodeToString(recipe)))
    }

    @Test
    fun `a recipe stored as only its required fields reads back at the defaults`() {
        // What `encodeDefaults = false` leaves on disk for a recipe nobody customized past picking a design and a
        // seed — the params, palette and aspect must default rather than fail to parse.
        val minimal = json.decodeFromString<WallpaperRecipe>("""{"design":"linearGradient","seed":7}""")

        assertEquals(WallpaperDesign.LINEAR_GRADIENT, minimal.design)
        assertEquals(7L, minimal.seed)
        assertEquals(DesignParams(), minimal.params)
        assertEquals(Palette.Fallback, minimal.palette)
        assertEquals(WallpaperAspect.VERTICAL, minimal.aspect)
    }

    @Test
    fun `an unknown key is dropped rather than throwing`() {
        // A recipe from a newer build carrying a field this one does not know — a filter list, say — must degrade to
        // the rest of the recipe, not fail the whole read.
        val recipe = json.decodeFromString<WallpaperRecipe>(
            """{"design":"linearGradient","seed":1,"filters":[{"type":"someFutureFilter"}]}""",
        )

        assertEquals(WallpaperDesign.LINEAR_GRADIENT, recipe.design)
    }
}
