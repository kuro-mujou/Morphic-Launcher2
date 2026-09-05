package inkspire.morphic.core.designsystem.component.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants the palette bank is *assembled* to hold, none of which the compiler can see — the banks are four
 * hand-and-script-written lists concatenated into one, and every failure below is silent at build time and loud on a
 * device: a repeated name crashes the picker's keyed `LazyRow`, repeated colors light two pills on one selection, and
 * an over-long entry turns a pill into a smear.
 */
class ColorPalettesTest {

    /** The picker keys its list on the name, so a repeat is a runtime crash rather than a cosmetic clash. */
    @Test
    fun `every palette name is unique`() {
        val repeats = ColorPalettes.all.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), repeats)
    }

    /**
     * Selection is compared by color list (`palette.colors == recipe.palette.colors`), so two banks carrying the same
     * colors under different names would light both pills at once. The harvest drops exact repeats; this is the guard
     * that says so.
     */
    @Test
    fun `no two palettes carry the same colors`() {
        val repeats = ColorPalettes.all.groupBy { it.colors }.filterValues { it.size > 1 }.values
        assertEquals(emptyList<List<ColorPalette>>(), repeats.toList())
    }

    /**
     * Two or more stops, at most eight. The lower bound is what makes a palette a *set*; the upper is the thinning
     * rule [colormapPalettes] applies to gart's 256-sample ramps, asserted here because nothing else re-checks it.
     */
    @Test
    fun `every palette holds between two and eight stops`() {
        ColorPalettes.all.forEach { palette ->
            assertTrue("${palette.name} has ${palette.colors.size} stops", palette.colors.size in 2..8)
        }
    }

    /** Wallpaper palettes may carry alpha, but nothing harvested does — a transparent swatch would read as a gap. */
    @Test
    fun `every harvested color is opaque`() {
        ColorPalettes.all.forEach { palette ->
            palette.colors.forEach { color ->
                assertEquals("${palette.name} has a translucent stop", 0xFF, color ushr 24 and 0xFF)
            }
        }
    }

    /** The bank is offered decorative-first: the featured dozen, then cool, then designer, and the colormaps last. */
    @Test
    fun `all runs featured then the three banks in order`() {
        assertEquals(
            ColorPalettes.featured + coolPalettes + designerPalettes + colormapPalettes,
            ColorPalettes.all,
        )
    }
}
