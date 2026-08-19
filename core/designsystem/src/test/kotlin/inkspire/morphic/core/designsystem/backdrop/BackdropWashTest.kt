package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.ui.graphics.Color
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.BackdropTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What a blur's wash resolves to — the one piece of the backdrop that is pure color arithmetic and so can be checked
 * without a device.
 *
 * **These exist because the failure they guard is a picture, not an exception.** A wash that comes out black when it
 * should come out to nothing renders perfectly happily; it just quietly makes one entry in the chooser behave like the
 * entry beside it.
 */
class BackdropWashTest {

    /** Stands in for the mode's `surfaceVariant` blended with the wallpaper's accent — see `wallpaperTone`. */
    private val tone = Color(0xFF3A4A5A)

    /**
     * One channel step, which is the tolerance every alpha assertion here needs.
     *
     * `Color` keeps each channel in 8 bits, so an amount of 0.42 comes back as 107/255 = 0.4196…. Asserting to a
     * hundred-thousandth is asserting that a float survived a byte, which it does not — and the round trip is exactly
     * what a stored `0f..1f` preference goes through on its way to a wash.
     */
    private val channel = 1f / 255f

    @Test
    fun `a tint of none paints nothing, whatever amount is stored beside it`() {
        // The bug: `Color.Transparent.copy(alpha = 0.3f)` is 30% *black*, because transparent is transparent black. So
        // "None" used to paint a dark film at whatever the last tint's amount was — behaving like Dark, and taking its
        // strength from a slider the section hides while None is selected.
        val blur = BackdropEffect.Blur(tint = BackdropTint.NONE, tintAmount = 0.3f)

        assertEquals(Color.Transparent, blur.wash(tone))
    }

    @Test
    fun `the amount is kept while none is selected, so choosing a color returns to it`() {
        // The reason the fix asks the *tint* rather than zeroing the amount: the amount is still the user's, exactly as
        // `customTintArgb` is kept while another swatch is selected.
        val none = BackdropEffect.Blur(tint = BackdropTint.NONE, tintAmount = 0.42f)

        assertEquals(0.42f, none.copy(tint = BackdropTint.DARK).wash(tone).alpha, channel)
    }

    @Test
    fun `every other tint carries the amount as its alpha`() {
        for (tint in BackdropTint.entries - BackdropTint.NONE) {
            val wash = BackdropEffect.Blur(tint = tint, tintAmount = 0.25f).wash(tone)

            assertEquals("$tint", 0.25f, wash.alpha, channel)
            assertNotEquals("$tint", Color.Transparent, wash)
        }
    }

    @Test
    fun `a custom tint paints the color it was given, and its alpha is the amount rather than the color's`() {
        // `customTintArgb` is documented as having its alpha ignored — two ways to set one thing is how they disagree.
        val blur = BackdropEffect.Blur(
            tint = BackdropTint.CUSTOM,
            tintAmount = 0.5f,
            customTintArgb = 0x00FF8800,
        )

        val wash = blur.wash(tone)
        assertEquals(0.5f, wash.alpha, channel)
        assertEquals(1f, wash.red, 0.01f)
        assertEquals(0f, wash.blue, 0.01f)
    }
}
