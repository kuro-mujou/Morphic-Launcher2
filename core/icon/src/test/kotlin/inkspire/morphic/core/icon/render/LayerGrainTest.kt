package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The noise field a grain effect pushes its pixels through.
 *
 * **Smoothness is the assertion that matters**, and it is the one nothing else would catch: a field that is not
 * continuous still *looks* like noise — it just scatters the artwork into confetti rather than tearing it into
 * pieces, which reads as the effect being wrong rather than as a bug. Determinism is the second, since a field that
 * varied between bakes would make the icon shimmer as the studio re-rendered and would stop a draft predicting the
 * full-size result.
 */
class LayerGrainTest {

    private fun grain(amplitude: Float = 0.02f, grainSize: Float = 0.08f) =
        LayerEffect.Grain(amplitude = amplitude, grainSize = grainSize)

    @Test
    fun `the field stays inside minus one and one`() {
        // The renderer multiplies this by an amplitude in pixels, so a value outside the range would push further
        // than the strength slider says it can.
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE

        for (step in 0 until 2_000) {
            val value = LayerGrain.noise(step * 0.37f, step * 0.61f, salt = 0)
            lowest = minOf(lowest, value)
            highest = maxOf(highest, value)
        }
        assertTrue("saw $lowest", lowest >= -1f)
        assertTrue("saw $highest", highest <= 1f)
        // And it genuinely varies, rather than the range being satisfied by a constant.
        assertTrue(highest - lowest > 1f)
    }

    @Test
    fun `the field is deterministic, so the same recipe grains the same way every bake`() {
        assertEquals(
            LayerGrain.noise(3.25f, 7.5f, salt = 0),
            LayerGrain.noise(3.25f, 7.5f, salt = 0),
        )
    }

    @Test
    fun `the field is smooth, which is what makes this grain rather than static`() {
        // Neighbouring samples must move *together*. A hash read per pixel would satisfy every other test here and
        // fail this one — and the difference on screen is confetti against torn pieces.
        var largestJump = 0f
        var previous = LayerGrain.noise(0f, 4f, salt = 0)

        for (step in 1..400) {
            val value = LayerGrain.noise(step * 0.01f, 4f, salt = 0)
            largestJump = maxOf(largestJump, abs(value - previous))
            previous = value
        }
        // A hundredth of a cell apart, so the field cannot legitimately swing anywhere near its full range.
        assertTrue("largest jump was $largestJump", largestJump < 0.2f)
    }

    @Test
    fun `two salts are two different fields`() {
        // What the salt is for: a displacement needs an independent field per axis, and sampling one field twice
        // would push every pixel along the diagonal — a shear rather than a scatter.
        val differ = (0 until 50).count { step ->
            val x = step * 0.53f
            LayerGrain.noise(x, 1.7f, salt = 0) != LayerGrain.noise(x, 1.7f, salt = 1)
        }
        assertEquals(50, differ)
    }

    @Test
    fun `the field changes across the lattice rather than repeating every cell`() {
        assertNotEquals(
            LayerGrain.noise(0.5f, 0.5f, salt = 0),
            LayerGrain.noise(1.5f, 0.5f, salt = 0),
        )
    }

    @Test
    fun `amplitude and cell size are fractions of the box, so one recipe grains the same at every bake size`() {
        assertEquals(1.92f, LayerGrain.amplitudePx(grain(), sizePx = 96), 0.001f)
        assertEquals(5.76f, LayerGrain.amplitudePx(grain(), sizePx = 288), 0.001f)

        assertEquals(7.68f, LayerGrain.cellPx(grain(), sizePx = 96), 0.001f)
        assertEquals(23.04f, LayerGrain.cellPx(grain(), sizePx = 288), 0.001f)
    }

    @Test
    fun `a cell never comes back at zero, which would divide by it`() {
        // `isIdentity` already refuses a grain size of zero; this is the guard for a stored recipe that never went
        // through it, and for one so fine the cell rounds away on a small bake.
        assertTrue(LayerGrain.cellPx(grain(grainSize = 0f), sizePx = 192) > 0f)
        assertTrue(LayerGrain.cellPx(grain(grainSize = 0.0001f), sizePx = 48) > 0f)
    }
}
