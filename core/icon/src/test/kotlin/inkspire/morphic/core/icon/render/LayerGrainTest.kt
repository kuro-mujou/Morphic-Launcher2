package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.compose.DraftPx
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

    private fun grain(
        amplitude: Float = 0.02f,
        grainSize: Float = 0.5f,
        directionality: Float = 0f,
        angleDegrees: Float = 0f,
    ) = LayerEffect.Grain(
        amplitude = amplitude,
        grainSize = grainSize,
        directionality = directionality,
        angleDegrees = angleDegrees,
    )

    /**
     * The drift a grain resolves to — what a bake resolves **once** and hands to every pixel.
     *
     * The displacement tests go through this rather than through the effect because that is the whole API now: the
     * angle's `sin`/`cos` used to be computed inside `displace`, i.e. twice per output pixel, on a value that cannot
     * change within a bake. See [LayerGrain.driftOf].
     */
    private fun drift(directionality: Float, angleDegrees: Float = 0f) =
        LayerGrain.driftOf(grain(directionality = directionality, angleDegrees = angleDegrees))

    @Test
    fun `the field stays inside minus one and one`() {
        // The renderer multiplies this by an amplitude in pixels, so a value outside the range would push further
        // than the strength slider says it can.
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE

        for (step in 0 until 2_000) {
            val value = LayerGrain.field(step * 0.37f, step * 0.61f, salt = 0)
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
            LayerGrain.field(3.25f, 7.5f, salt = 0),
            LayerGrain.field(3.25f, 7.5f, salt = 0),
        )
    }

    @Test
    fun `the field is smooth, which is what makes this grain rather than static`() {
        // Neighbouring samples must move *together*. A hash read per pixel would satisfy every other test here and
        // fail this one — and the difference on screen is confetti against torn pieces.
        var largestJump = 0f
        var previous = LayerGrain.field(0f, 4f, salt = 0)

        for (step in 1..400) {
            val value = LayerGrain.field(step * 0.01f, 4f, salt = 0)
            largestJump = maxOf(largestJump, abs(value - previous))
            previous = value
        }
        // A hundredth of a cell apart, so the field cannot legitimately swing anywhere near its full range. The
        // bound allows for the octaves: the finest of them moves four times as fast as the base.
        assertTrue("largest jump was $largestJump", largestJump < 0.2f)
    }

    @Test
    fun `the field is zero at a lattice point, which is what gradient noise buys`() {
        // **The defect this replaced, and it is invisible as a bug.** Value noise puts its extremes *on* the
        // lattice, so the field carries a square grid at the cell size and the artwork tears into axis-aligned
        // chunks at every setting. Gradient noise reads zero at every corner and does its varying in between.
        for (x in 0..4) {
            for (y in 0..4) {
                assertEquals(0f, LayerGrain.noise(x.toFloat(), y.toFloat(), salt = 0), 0.0001f)
            }
        }
        // Which must not mean the field is zero everywhere — it varies between the corners.
        assertNotEquals(0f, LayerGrain.noise(0.5f, 0.5f, salt = 0))
    }

    @Test
    fun `two salts are two different fields`() {
        // What the salt is for: a displacement needs an independent field per axis, and sampling one field twice
        // would push every pixel along the diagonal — a shear rather than a scatter.
        val differ = (0 until 50).count { step ->
            val x = step * 0.53f
            LayerGrain.field(x, 1.7f, salt = 0) != LayerGrain.field(x, 1.7f, salt = 1)
        }
        assertEquals(50, differ)
    }

    @Test
    fun `the field changes across the lattice rather than repeating every cell`() {
        assertNotEquals(
            LayerGrain.field(0.5f, 0.5f, salt = 0),
            LayerGrain.field(1.5f, 0.5f, salt = 0),
        )
    }

    @Test
    fun `the field carries more than one size of detail`() {
        // What the octaves are for. Sampled a whole cell apart the base octave has moved a long way; sampled a
        // sixteenth of a cell apart, a single-octave field is nearly flat and this one is not.
        var fineMovement = 0f
        var previous = LayerGrain.field(2f, 2f, salt = 0)

        for (step in 1..16) {
            val value = LayerGrain.field(2f + step / 16f, 2f, salt = 0)
            fineMovement = maxOf(fineMovement, abs(value - previous))
            previous = value
        }
        assertTrue("the fine detail is missing: $fineMovement", fineMovement > 0.01f)
    }

    @Test
    fun `amplitude is a fraction of the box, so one recipe grains the same at every bake size`() {
        assertEquals(1.92f, LayerGrain.amplitudePx(grain(), sizePx = 96), 0.001f)
        assertEquals(5.76f, LayerGrain.amplitudePx(grain(), sizePx = 288), 0.001f)
    }

    @Test
    fun `the cell size is geometric in the control, so the fine end has as much travel as the coarse`() {
        // **The fix for a slider whose useful half was unreachable.** Equal steps of the control must be equal
        // *ratios* of cell size — which is what puts dust, clusters and blocks each on a third of the travel
        // instead of crowding the first three into the bottom few percent.
        val size = 1000
        val quarter = LayerGrain.cellPx(grain(grainSize = 0.25f), size)
        val half = LayerGrain.cellPx(grain(grainSize = 0.5f), size)
        val threeQuarters = LayerGrain.cellPx(grain(grainSize = 0.75f), size)

        assertEquals(half / quarter, threeQuarters / half, 0.01f)
        // And the ends are the two the mapping names: about a seventy-second of the box, and half of it.
        assertEquals(size / 72f, LayerGrain.cellPx(grain(grainSize = 0f), size), size / 800f)
        assertEquals(size * 0.5f, LayerGrain.cellPx(grain(grainSize = 1f), size), 0.5f)
    }

    /**
     * That **every position on the slider is a different grain**, at the sizes icons are really baked at.
     *
     * **The defect this pins was reported as the preview freezing.** The fine end of the ramp used to be a cell of
     * well under a pixel, so on any real bake the whole bottom third of the control clamped to the 4px floor and drew
     * the *same picture* — a slider doing nothing across a third of its travel on a device, and a studio draft that
     * stopped responding entirely down there. Nothing failed; the control was simply inert, which is indistinguishable
     * from a preview that has stopped updating.
     *
     * **Checked against [DraftPx] itself rather than against a repeated `144`, and that is the point of the import.**
     * Three sizes coincide there today — a home icon, the size the studio drafts at, and `GrainFidelityPx` — and the
     * guarantee is only worth anything while the ramp is no finer than the *draft*, since that is the picture a finger
     * is dragging against. Raising `GrainFidelityPx` above it was tried: the bottom sixth of the control went inert in
     * the draft, which is this file's original defect wearing a different hat. Reading the constant is what makes that
     * fail a test rather than reach a device — writing the number again is exactly how it got through the first time.
     */
    @Test
    fun `moving the control changes the cell at every size the draft has to show`() {
        for (size in listOf(DraftPx, 768)) {
            var previous = LayerGrain.cellPx(grain(grainSize = 0f), size)
            for (step in 1..20) {
                val cell = LayerGrain.cellPx(grain(grainSize = step / 20f), size)
                assertTrue(
                    "at ${size}px the control did nothing between ${(step - 1) / 20f} and ${step / 20f}",
                    cell > previous,
                )
                previous = cell
            }
        }
    }

    /**
     * That a pixel is sampled at its **centre**, which is what lets the lattice go as fine as it does.
     *
     * **Invisible when wrong, which is the only reason this is worth a test.** Gradient noise reads zero at every
     * lattice point, so sampling a pixel's corner drops every `cellPx`-th sample onto nothing — at a two-pixel cell,
     * half of them. The picture that comes out still looks like noise; it is simply weaker and coarser than the
     * recipe asked for, and no assertion about smoothness, determinism or range would notice.
     */
    @Test
    fun `a pixel is sampled at its centre, so no sample lands on a lattice zero`() {
        // At a two-pixel cell the corners fall on the lattice every other pixel. Centres cannot: they sit at quarter
        // and three-quarter phases whatever the pixel.
        for (pixel in 0 until 16) {
            val phase = LayerGrain.latticeAt(pixel, cellPx = 2f) % 1f
            assertNotEquals(0f, phase)
        }
        // And the whole point of it: a field sampled that way still moves at the floor, where corners would not.
        var largest = 0f
        for (pixel in 0 until 64) {
            val at = LayerGrain.latticeAt(pixel, cellPx = 2f)
            largest = maxOf(largest, abs(LayerGrain.field(at, at, salt = 0)))
        }
        assertTrue("the field vanished at a two-pixel cell", largest > 0.1f)
    }

    @Test
    fun `the finest grain still displaces at the size a home icon bakes at`() {
        // **End to end, where the test above is the mechanism.** The finest position on the control, resolved at the
        // smallest size an icon is baked at, has to actually move the pixels — which is what a cell size alone does
        // not tell you, since `cellPx` coerces to the floor and so can never report anything too small to work.
        //
        // The regression it guards was invisible in the studio: at the finest setting a 144px icon displaced nothing
        // at all, while the much larger canvas showed grain the home screen would never draw. The two-renderer
        // hazard's own shape, reached through a bake size rather than through a second renderer.
        val homeIcon = 144
        val cellPx = LayerGrain.cellPx(grain(grainSize = 0f), homeIcon)
        var largest = 0f

        for (pixel in 0 until homeIcon) {
            val at = LayerGrain.latticeAt(pixel, cellPx)
            largest = maxOf(largest, abs(LayerGrain.field(at, at, salt = 0)))
        }
        assertTrue("the field vanished at cell $cellPx", largest > 0.1f)
    }

    @Test
    fun `with no directionality the displacement is the field itself`() {
        val into = FloatArray(2)
        LayerGrain.displace(drift(directionality = 0f), fieldX = 0.3f, fieldY = -0.7f, into = into)

        assertEquals(0.3f, into[0], 0.0001f)
        assertEquals(-0.7f, into[1], 0.0001f)
    }

    @Test
    fun `full directionality puts every pixel on one line`() {
        // 90° is along +x by the studio's convention, so the sideways component must be gone entirely — that is
        // what "smeared" means, and it is the end the old two-valued control could reach.
        val into = FloatArray(2)
        LayerGrain.displace(drift(directionality = 1f, angleDegrees = 90f), 0.3f, -0.7f, into)

        assertEquals(0.3f, into[0], 0.0001f)
        assertEquals(0f, into[1], 0.0001f)
    }

    @Test
    fun `half directionality keeps half the sideways movement`() {
        // **The middle the enum could not express**, and the reason this is a continuum: the along-axis part is
        // untouched at every setting, and only the across-axis part is squashed.
        val into = FloatArray(2)
        LayerGrain.displace(drift(directionality = 0.5f, angleDegrees = 90f), 0.3f, -0.8f, into)

        assertEquals(0.3f, into[0], 0.0001f)
        assertEquals(-0.4f, into[1], 0.0001f)
    }

    @Test
    fun `the angle rotates the axis the smear runs along`() {
        // Straight down at 0°, so a fully directed displacement keeps only its y component there — the mirror of
        // the 90° case above, which is what makes the angle slider mean a rotation rather than a swap.
        val into = FloatArray(2)
        LayerGrain.displace(drift(directionality = 1f, angleDegrees = 0f), 0.3f, -0.7f, into)

        assertEquals(0f, into[0], 0.0001f)
        assertEquals(-0.7f, into[1], 0.0001f)
    }
}
