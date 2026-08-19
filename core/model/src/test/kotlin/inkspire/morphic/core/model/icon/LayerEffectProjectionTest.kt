package inkspire.morphic.core.model.icon

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The values a renderer reads off an effect rather than the ones a user sets.
 *
 * **These live in the model for one reason and are tested for the same one.** Each is a projection of an effect's own
 * fields into what the drawing code actually needs — a vignette's clear middle from its reach, a centered stroke's
 * per-side width from its total. Done in the renderers instead, they would be done **twice**, and both failures are
 * silent: a vignette shaded from the wrong end is a perfectly plausible picture lit in the middle, and a centered
 * stroke measured from the total is a stroke exactly twice as heavy as the same number asks for in any other
 * position — which reads as the position control secretly also being a width control.
 *
 * `Bloom.placementX` is the same kind of thing and is covered where its consequences are, in `LayerGradientTest`.
 */
class LayerEffectProjectionTest {

    @Test
    fun `a vignette's clear middle is its reach read from the other end`() {
        assertEquals(0.65f, LayerEffect.Vignette(reach = 0.35f).clearArea, 0.0001f)
        assertEquals(0.0f, LayerEffect.Vignette(reach = 1f).clearArea, 0.0001f)
    }

    @Test
    fun `a vignette reaching nowhere leaves the whole frame clear`() {
        assertEquals(1f, LayerEffect.Vignette(reach = 0f).clearArea, 0.0001f)
    }

    @Test
    fun `a reach outside its range still names a fraction of the frame`() {
        // A stored recipe is not obliged to be sensible, and the ramp it feeds rejects an inverted range outright.
        assertEquals(1f, LayerEffect.Vignette(reach = -0.5f).clearArea, 0.0001f)
        assertEquals(0f, LayerEffect.Vignette(reach = 2f).clearArea, 0.0001f)
    }

    @Test
    fun `a one-sided stroke spends its whole width on that side`() {
        val width = 0.04f

        assertEquals(width, LayerEffect.Outline(width = width, position = OutlinePosition.OUTSIDE).perSideWidth, 0.0001f)
        assertEquals(width, LayerEffect.Outline(width = width, position = OutlinePosition.INSIDE).perSideWidth, 0.0001f)
    }

    @Test
    fun `a centered stroke splits its width, so it does not read twice as heavy as the others`() {
        // The whole point of the projection: the same number means the same visible thickness in all three
        // positions, so switching between them moves the band without also changing its weight.
        assertEquals(0.02f, LayerEffect.Outline(width = 0.04f, position = OutlinePosition.CENTER).perSideWidth, 0.0001f)
    }
}
