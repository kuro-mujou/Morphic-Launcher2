package inkspire.morphic.data.icons.internal

import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconPlate
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a stored per-app appearance does on the way in and out — in particular, **every way it can fail to come back**.
 *
 * The failure cases are the reason this codec exists as its own object rather than two lines inside the repository:
 * a row that cannot be decoded must produce `null`, because that is what makes the repository drop it and the app
 * fall back to the global default. Anything that let a bad row throw would take down every surface drawing that
 * icon, which is a large consequence for one malformed string.
 */
class IconAppearanceCodecTest {

    private val customized = IconAppearance(
        layerSet = IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.SolidFill(argb = -0x10000)),
                IconLayerSpec(
                    role = LayerRole.FOREGROUND,
                    source = LayerSource.AppDefault,
                    shape = IconShape("hexagon"),
                    zoom = 1.25f,
                ),
            ),
        ),
        plate = IconPlate(enabled = true, shape = IconShape("rounded_square")),
        zoom = 0.82f,
    )

    @Test
    fun `an appearance survives a round trip`() {
        assertEquals(customized, IconAppearanceCodec.decode(IconAppearanceCodec.encode(customized)))
    }

    @Test
    fun `a corrupt blob decodes to null rather than throwing`() {
        assertNull(IconAppearanceCodec.decode("{ not json"))
        assertNull(IconAppearanceCodec.decode(""))
    }

    @Test
    fun `well-formed JSON describing an illegal stack also decodes to null`() {
        // The case a plain `Json.decodeFromString` would not survive: the parse succeeds and `IconLayerSet.init`
        // then rejects the value. Both failures have to land on the same fallback, or a stack with no background
        // would be fatal where a truncated file is merely ignored. Still reachable through the appearance, which is
        // the half worth pinning now that the recipe is nested inside one.
        val noBackground = """{"layerSet":{"layers":[{"role":"FOREGROUND","source":{"type":"app_default"}}]}}"""

        assertNull(IconAppearanceCodec.decode(noBackground))
    }

    @Test
    fun `an unknown effect is dropped, so a newer build's row still loads on an older one`() {
        // Effects are an open sealed list precisely so adding one is not a migration; the other half of that promise
        // is that a build which predates an effect can still read a row containing it.
        val fromTheFuture = """
            {"layerSet":{"layers":[
              {"role":"BACKGROUND","source":{"type":"app_default"},"somethingAddedLater":7},
              {"role":"FOREGROUND","source":{"type":"app_default"}}
            ]}}
        """.trimIndent()

        assertEquals(IconAppearance.Base, IconAppearanceCodec.decode(fromTheFuture))
    }

    @Test
    fun `defaults are not written, so a row stores only what the user changed`() {
        // **An untouched appearance is two bytes**, and it is worth knowing *why* rather than only that: `layerSet`
        // has a default of its own, so `encodeDefaults = false` omits the recipe as well as the plate and the zoom.
        // That is the same promise the recipe alone used to make, one level out — widening the stored unit cost
        // every un-plated icon on the device nothing at all.
        assertEquals("{}", IconAppearanceCodec.encode(IconAppearance.Base))
    }

    @Test
    fun `a plated appearance stores the plate and nothing else`() {
        // The other half of the claim above: what a user *has* set is written, and only that. This is also the
        // shape a plate-only edit takes on disk — no recipe, because the plain one is the default.
        val plated = IconAppearance.Base.copy(plate = IconPlate(enabled = true, shape = IconShape("circle")))

        assertEquals("""{"plate":{"enabled":true,"shape":"circle"}}""", IconAppearanceCodec.encode(plated))
    }

    @Test
    fun `a row from before the plate reads back as one with no plate`() {
        // The old shape nested under the new key: what a `layerSet` blob looks like once it is one field of an
        // appearance. It is *not* what the old column held — that was the bare recipe, which is why the column and
        // the settings key both changed rather than being re-interpreted in place.
        val recipeOnly = """{"layerSet":{"layers":[{"role":"BACKGROUND","source":{"type":"app_default"}},""" +
            """{"role":"FOREGROUND","source":{"type":"app_default"}}]}}"""

        assertEquals(IconAppearance.Base, IconAppearanceCodec.decode(recipeOnly))
    }
}
