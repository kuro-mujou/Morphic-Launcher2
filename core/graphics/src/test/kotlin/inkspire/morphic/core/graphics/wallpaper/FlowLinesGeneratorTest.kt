package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Flow Lines' pure mappings — how many copies, how many waves the base curve carries, how heavy a stroke is and how
 * far the whole fan turns. Each is measured against the reference's own ruler, and each is silently wrong when it is
 * wrong: a fan at the wrong pitch, weight or turn is still a plausible fan.
 */
class FlowLinesGeneratorTest {

    @Test
    fun `density maps to the copy count range`() {
        assertEquals(8, FlowLinesGenerator.copyCount(0f))
        assertEquals(80, FlowLinesGenerator.copyCount(1f))
        assertEquals(8, FlowLinesGenerator.copyCount(-1f)) // clamped
        assertEquals(80, FlowLinesGenerator.copyCount(2f)) // clamped
    }

    /** The reference's *Complexity* `0` is a dead straight line, and a wave count is the only mapping that reaches it. */
    @Test
    fun `waviness zero asks for no waves at all`() {
        assertEquals(0f, FlowLinesGenerator.waveCount(0f), 1e-6f)
        assertEquals(4f, FlowLinesGenerator.waveCount(1f), 1e-6f)
        // Their *Complexity* `10` draws about two waves, so the field's midpoint must too.
        assertEquals(2f, FlowLinesGenerator.waveCount(0.5f), 1e-6f)
    }

    /**
     * The reference strokes `2.56px` per unit of its `1..10` ruler on a 1080-wide frame, defaulting to `1.8` — so its
     * default is about `4.6px` there and its top about `26px`.
     */
    @Test
    fun `thickness lands the field's default on the reference's shipped stroke`() {
        assertEquals(4.6f, FlowLinesGenerator.strokeWidthPx(0.5f, 1080), 0.5f)
        assertEquals(25.9f, FlowLinesGenerator.strokeWidthPx(1f, 1080), 0.5f)
    }

    @Test
    fun `a stroke never falls below a pixel, whatever the frame`() {
        assertEquals(1f, FlowLinesGenerator.strokeWidthPx(0f, 1080), 1e-6f)
        assertEquals(1f, FlowLinesGenerator.strokeWidthPx(0.01f, 100), 1e-6f)
    }

    /** Their *Delta rotation* defaults to `150°` and reaches `500°`; ours must put that default on the field's `0.5`. */
    @Test
    fun `turn is zero when untouched and lands the reference's default mid-knob`() {
        assertEquals(0f, FlowLinesGenerator.turnRadians(0f), 1e-6f)
        assertEquals(150f, FlowLinesGenerator.turnRadians(0.5f) * 180f / PI.toFloat(), 1f)
        assertTrue(
            "the far end must pass a full revolution",
            FlowLinesGenerator.turnRadians(1f) > 2f * PI.toFloat(),
        )
    }
}
