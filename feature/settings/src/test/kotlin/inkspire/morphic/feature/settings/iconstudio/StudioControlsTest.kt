package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.core.designsystem.component.slider.finestStep
import inkspire.morphic.core.designsystem.component.slider.snappedStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two steps the *studio* chooses rather than derives.
 *
 * The derivation itself is pinned in `core:designsystem`'s `StepGridTest`; what is left here is what this screen adds
 * to it — a range it passes to a control, and the one quantity in the studio that is not a fraction.
 */
class StudioControlsTest {

    @Test
    fun `the position pad's nudge follows its own range, like every slider`() {
        // Four of the six pads run over a full frame and are unaffected; the two narrow ones are why this matters.
        // A shadow's throw is ±0.15 and a chromatic split's ±0.05, and a flat hundredth there was 3% and **10%** of
        // everything the control could express — the jump these buttons exist not to be. The pad's readout already
        // printed three decimals on those, so the step and the number on screen disagreed by a factor of ten.
        assertEquals(0.01f, finestStep(PositionRange))
        assertEquals(0.001f, finestStep(-0.15f..0.15f))
        assertEquals(0.001f, finestStep(-0.05f..0.05f))
    }

    @Test
    fun `an angle steps by one degree`() {
        // The one step still stated rather than derived — degrees are not fractions. Every whole angle is on the
        // grid, so 45, 90 and 180 stay reachable, which is all a five-degree step would buy.
        assertEquals(1f, AngleStep)
        assertTrue(snappedStep(87f, AngleStep, up = true) == 88f)
    }
}
