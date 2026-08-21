package inkspire.morphic.core.designsystem.component.slider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stepper's increment, the readout it is matched to, and the grid a drag lands on.
 *
 * **Tested because the failure mode is a control that appears broken.** A step finer than the number on screen moves a
 * value nothing can show, so the press looks like it did nothing; a step coarser than the control needs turns the
 * buttons into a second, worse slider; and a drag that lands off the grid the buttons walk leaves the readout, the
 * thumb and the store holding three different numbers. None of the three throws, and all read as "this control is a
 * bit off" rather than as a bug — which is why they live in one derivation and are pinned here.
 */
class StepGridTest {

    @Test
    fun `a unit range steps by the hundredth its readout ends on`() {
        assertEquals(0.01f, finestStep(0f..1f))
        assertEquals("%.2f", finestFormat(0f..1f))
    }

    @Test
    fun `a narrow range gets the extra digit, and the step gains it too`() {
        // Half the icon-effect sliders run 0..0.1 or 0..0.2 — a blur radius, a ripple's amplitude, a halo's spread. At
        // two decimals one press moved five to ten percent of everything the control could express.
        assertEquals(0.001f, finestStep(0f..0.1f))
        assertEquals("%.3f", finestFormat(0f..0.1f))
        assertEquals(0.001f, finestStep(0f..0.2f))
    }

    @Test
    fun `a slider merely missing the bottom of its track is still a unit slider`() {
        // `0.05..1` and `0.05..1.5` are plainly the same *kind* of value as the `0..1` ones beside them, and a
        // threshold of one whole unit would have given them a third decimal for the sake of the missing 0.05.
        assertEquals(0.01f, finestStep(0.05f..1f))
        assertEquals("%.2f", finestFormat(0.05f..1f))
        assertEquals(0.01f, finestStep(-1f..1f))
    }

    @Test
    fun `the step and the readout always agree`() {
        // The property that matters, stated over the ranges the launcher actually uses: one press must move the last
        // digit the readout prints — never less (invisible) and never more (a jump).
        val ranges = listOf(
            0f..1f, 0f..2f, -1f..1f, 0.2f..2f, 0.05f..1f, 0.05f..1.5f,
            0f..0.1f, 0f..0.15f, 0f..0.2f, 0.05f..0.25f, 0.05f..0.4f, -0.5f..0.5f,
        )
        ranges.forEach { range ->
            val decimals = finestFormat(range).substringAfter('.').first().digitToInt()
            val implied = Math.pow(10.0, -decimals.toDouble()).toFloat()
            assertEquals("readout and step disagree on $range", implied, finestStep(range), 1e-6f)
        }
    }

    @Test
    fun `a press lands on the grid rather than adding to a dragged value`() {
        // What makes a fine step usable: from 0.037 one press up gives 0.04, not 0.047 — so the round numbers stay
        // one press away instead of the drag's debris being carried forever.
        assertEquals(0.04f, snappedStep(0.037f, 0.01f, up = true), 1e-5f)
        assertEquals(0.03f, snappedStep(0.037f, 0.01f, up = false), 1e-5f)
        assertEquals(0.038f, snappedStep(0.0374f, 0.001f, up = true), 1e-5f)
    }

    @Test
    fun `a value already on the grid moves a whole step`() {
        // The epsilon's job: without it a value arrived at *by* a press reads as a hair below its own grid line and
        // steps only to itself, which presents as a button that works every other press.
        assertEquals(0.05f, snappedStep(0.04f, 0.01f, up = true), 1e-5f)
        assertEquals(0.039f, snappedStep(0.04f, 0.001f, up = false), 1e-5f)
        // Far up the range, where `value / step` is largest and float error worst.
        assertEquals(0.201f, snappedStep(0.2f, 0.001f, up = true), 1e-5f)
    }

    @Test
    fun `a drag lands on the same grid a press walks`() {
        // The whole-dp case is the one that bites: a finger reporting 41.6 on a slider whose store holds `Int` dp must
        // resolve to 42, not to 41.6 — otherwise the readout says one number, the store keeps another, and the next
        // press moves the value by a fraction while the display jumps by a whole unit.
        assertEquals(42f, quantizedTo(41.6f, 1f), 1e-5f)
        assertEquals(41f, quantizedTo(41.4f, 1f), 1e-5f)
        // And a fraction lands where its readout can print it.
        assertEquals(0.37f, quantizedTo(0.3712f, 0.01f), 1e-5f)
        assertEquals(0.371f, quantizedTo(0.3712f, 0.001f), 1e-5f)
        // A quantized value is already on the grid, so one press moves it a whole step — the two must agree, or a
        // press after a drag moves less than one step and reads as a dead button.
        val dragged = quantizedTo(41.6f, 1f)
        assertEquals(43f, snappedStep(dragged, 1f, up = true), 1e-5f)
        assertEquals(41f, snappedStep(dragged, 1f, up = false), 1e-5f)
    }
}
