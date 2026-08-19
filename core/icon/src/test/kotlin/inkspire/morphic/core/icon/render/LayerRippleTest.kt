package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a rippled pixel reads from.
 *
 * [LayerShadowTest]'s reason: only the bake draws this, so nothing is competing with the arithmetic — it is here
 * because a displacement that is subtly wrong produces a perfectly plausible ripple rather than an error, and
 * because pulled out of `IconRenderer` it can be checked without an emulator.
 */
class LayerRippleTest {

    private fun ripple(amplitude: Float = 0.03f, waves: Float = 8f) =
        LayerEffect.Ripple(amplitude = amplitude, waves = waves)

    @Test
    fun `the center of a wave reads from where it already is`() {
        // `sin` is zero at every whole wavelength, so those rings are undisplaced — which is what makes a ripple a
        // set of moving rings rather than a uniform swell.
        val amplitude = 10f
        val wavelength = 40f

        assertEquals(0f, LayerRipple.sampleDistancePx(0f, amplitude, wavelength), 0.001f)
        assertEquals(40f, LayerRipple.sampleDistancePx(40f, amplitude, wavelength), 0.001f)
        assertEquals(80f, LayerRipple.sampleDistancePx(80f, amplitude, wavelength), 0.001f)
    }

    @Test
    fun `a quarter wavelength out reads from a full amplitude further`() {
        // The peak of the sine, and the assertion that pins which way round the amplitude is applied.
        assertEquals(10f + 10f, LayerRipple.sampleDistancePx(10f, amplitudePx = 10f, wavelengthPx = 40f), 0.001f)
        // ...and three quarters the other way, which is what makes it a wave rather than a swell.
        assertEquals(30f - 10f, LayerRipple.sampleDistancePx(30f, amplitudePx = 10f, wavelengthPx = 40f), 0.001f)
    }

    @Test
    fun `no amplitude reads from exactly where it is, at every distance`() {
        listOf(0f, 7f, 33.3f, 200f).forEach { distance ->
            assertEquals(distance, LayerRipple.sampleDistancePx(distance, 0f, 40f), 0.001f)
        }
    }

    @Test
    fun `amplitude is a fraction of the box, so one recipe ripples the same at every bake size`() {
        assertEquals(2.88f, LayerRipple.amplitudePx(ripple(), sizePx = 96), 0.001f)
        assertEquals(8.64f, LayerRipple.amplitudePx(ripple(), sizePx = 288), 0.001f)
    }

    @Test
    fun `more waves makes them finer rather than the ripple bigger`() {
        // The whole reason the wavelength is a division: `waves` counts crests across the box, so asking for more of
        // them has to shorten each one. Getting this backwards would make the count read as a size.
        val few = LayerRipple.wavelengthPx(ripple(waves = 4f), sizePx = 192)
        val many = LayerRipple.wavelengthPx(ripple(waves = 16f), sizePx = 192)

        assertEquals(48f, few, 0.001f)
        assertEquals(12f, many, 0.001f)
        assertTrue(many < few)
    }

    @Test
    fun `a wavelength never comes back at zero, which would divide by it`() {
        // `isIdentity` already refuses a wave count of zero; this is the guard for a stored recipe that never went
        // through it, and for a count so high the wavelength rounds away on a small bake.
        assertTrue(LayerRipple.wavelengthPx(ripple(waves = 0f), sizePx = 192) > 0f)
        assertTrue(LayerRipple.wavelengthPx(ripple(waves = 10_000f), sizePx = 48) > 0f)
    }

    @Test
    fun `a center of zero is the middle of the box`() {
        assertEquals(96f, LayerRipple.centerPx(center = 0f, sizePx = 192), 0.001f)
        // And a fraction moves it by that share of the box, positive toward the far edge.
        assertEquals(144f, LayerRipple.centerPx(center = 0.25f, sizePx = 192), 0.001f)
        assertEquals(48f, LayerRipple.centerPx(center = -0.25f, sizePx = 192), 0.001f)
    }
}
