package inkspire.morphic.core.designsystem.component.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser's filter arithmetic, checked without a screen — which is the point of it being a pure function over
 * packed ints rather than something the composable does inline.
 *
 * Every failure here is silent on a device: a chip that quietly returns nothing looks like a bank with no green in it,
 * and a ranking that ignores how *much* of a palette is the asked-for color looks like a list in no order at all.
 */
class PaletteColorFilterTest {

    private val chipRed = 0xFFE53935.toInt()

    @Test
    fun `a color is no distance from itself`() {
        assertEquals(0f, PaletteColorFilter.distance(chipRed, chipRed), 0.0001f)
    }

    /** Black to white is the pair the normalizer is taken from, so it lands on the top of the range — just under it. */
    @Test
    fun `black to white spans the whole range`() {
        val span = PaletteColorFilter.distance(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(1f, span, 0.001f)
        assertTrue("black to white must not exceed the range", span <= 1f)
    }

    @Test
    fun `distance does not depend on which color is which`() {
        val a = 0xFF123B7A.toInt()
        val b = 0xFFA5D6A7.toInt()
        assertEquals(PaletteColorFilter.distance(a, b), PaletteColorFilter.distance(b, a), 0.0001f)
    }

    /** Alpha is not a color difference — a translucent red is still red, and the filter must find it. */
    @Test
    fun `alpha is ignored`() {
        assertEquals(0f, PaletteColorFilter.distance(0x40E53935, chipRed), 0.0001f)
    }

    @Test
    fun `a palette with nothing near the chip is left out`() {
        val greens = palette("Greens", 0xFF1B5E20, 0xFF43A047, 0xFFA5D6A7)
        assertEquals(emptyList<ColorPalette>(), PaletteColorFilter.matching(listOf(greens), chipRed))
    }

    /**
     * The ranking's whole job: "filter by red" means palettes that *are* red, not palettes that contain a red. A
     * single-accent palette must still appear — that is the difference from a bucket — but below the ramp.
     */
    @Test
    fun `a palette that is mostly the chip outranks one carrying it once`() {
        val ramp = palette("Ramp", 0xFFE53935, 0xFFEF5350, 0xFFE04A46)
        val accent = palette("Accent", 0xFF111111, 0xFF224466, 0xFFE53935)
        assertEquals(
            listOf("Ramp", "Accent"),
            PaletteColorFilter.matching(listOf(accent, ramp), chipRed).map { it.name },
        )
    }

    /** Equal share and equal nearness leaves the bank's own order, so the featured palettes stay ahead. */
    @Test
    fun `palettes that score alike keep the bank order`() {
        val first = palette("First", 0xFFE53935, 0xFF111111)
        val second = palette("Second", 0xFFE53935, 0xFF224466)
        assertEquals(
            listOf("First", "Second"),
            PaletteColorFilter.matching(listOf(first, second), chipRed).map { it.name },
        )
    }

    /**
     * The claim [PaletteColorFilter.Nearness]' KDoc makes about the shipped bank, asserted rather than remembered —
     * a chip that returns nothing is a dead control, and nothing else would notice it going dead as the bank changes.
     */
    @Test
    fun `every chip finds palettes in the shipped bank`() {
        PaletteColorFilter.chips.forEach { chip ->
            val found = PaletteColorFilter.matching(ColorPalettes.all, chip)
            assertTrue("chip #%06X found nothing".format(chip and 0xFFFFFF), found.isNotEmpty())
        }
    }

    /** And that no chip is so loose it returns most of the bank, which would be no filter at all. */
    @Test
    fun `no chip returns more than half the bank`() {
        val half = ColorPalettes.all.size / 2
        PaletteColorFilter.chips.forEach { chip ->
            val found = PaletteColorFilter.matching(ColorPalettes.all, chip)
            assertTrue("chip #%06X returned %d".format(chip and 0xFFFFFF, found.size), found.size <= half)
        }
    }
}
