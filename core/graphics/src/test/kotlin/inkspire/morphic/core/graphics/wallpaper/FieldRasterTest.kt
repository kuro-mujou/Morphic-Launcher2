package inkspire.morphic.core.graphics.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a field is read for each pixel of a buffer — the one piece of the shared loop that is arithmetic rather than
 * a canvas call, and the one that is silently wrong.
 *
 * **Read where the pixel lands, and at full size exactly as the bake always read it.** Get the first half wrong and a
 * downscaled scrub is the field stretched by up to half a buffer pixel toward each edge — five screen pixels at a
 * tenth of the size — which shows as levels of error only where the colors are steep and so passes any look at a
 * gentle gradient; the plasma's measured error fell from 26% of pixels to 0.6% when it was put right. Get the second
 * half wrong and every bake moves.
 */
class FieldRasterTest {

    @Test
    fun `at full size a pixel is read exactly where the bake always read it`() {
        for (x in listOf(0, 1, 539, 1078, 1079)) {
            assertEquals(x.toFloat() / 1079, FieldRaster.share(x, 1080, 1080), 0f)
        }
    }

    @Test
    fun `a buffer pixel is read at the frame point its center lands on`() {
        // A tenth of the width: buffer pixel x's center lands at (x + 0.5) · 10 frame pixels, less the half a pixel the
        // frame's own shares are measured from.
        for (x in listOf(0, 17, 107)) {
            val landing = ((x + 0.5f) * 1080 / 108 - 0.5f) / 1079
            assertEquals(landing, FieldRaster.share(x, 108, 1080), 1e-6f)
        }
    }

    @Test
    fun `a downscaled buffer is read symmetrically, not stretched toward one edge`() {
        assertEquals(1f - FieldRaster.share(0, 120, 1080), FieldRaster.share(119, 120, 1080), 1e-6f)
        assertEquals(0.5f, FieldRaster.share(60, 121, 1080), 1e-6f)
    }

    @Test
    fun `a frame of one pixel reads its middle`() {
        assertEquals(0.5f, FieldRaster.share(0, 1, 1), 0f)
    }
}
