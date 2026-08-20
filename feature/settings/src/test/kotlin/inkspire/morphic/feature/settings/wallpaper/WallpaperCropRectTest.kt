package inkspire.morphic.feature.settings.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior spec for [cropRectOf] — where the crop screen's viewport sits on the image being framed.
 *
 * Worth testing because it is silent when wrong: a rectangle that is off by a little produces a wallpaper that is
 * cropped slightly differently from what the user framed, which nobody notices at the moment it happens and nobody
 * can attribute later. It is also the only part of that screen a JVM test can reach — everything around it is a
 * gesture and a draw.
 */
class WallpaperCropRectTest {

    private val image = IntSize(1000, 1000)

    /** The state the screen opens in for a square image in a square viewport: the whole image, exactly. */
    @Test
    fun `cover scale and no offset is the whole image`() {
        val crop = cropRectOf(image, viewport = IntSize(500, 500), scale = 0.5f, offset = Offset.Zero)

        assertEquals(0f, crop.left, TOLERANCE)
        assertEquals(0f, crop.top, TOLERANCE)
        assertEquals(1f, crop.right, TOLERANCE)
        assertEquals(1f, crop.bottom, TOLERANCE)
    }

    /**
     * Zoomed to twice the cover scale and left at the origin, the viewport shows the image's **top-left quarter**.
     *
     * The direction is the part worth pinning: a sign error here still produces a plausible rectangle, and it would
     * crop the opposite corner of every wallpaper.
     */
    @Test
    fun `zooming in shows less of the image, anchored where the offset says`() {
        val crop = cropRectOf(image, viewport = IntSize(500, 500), scale = 1f, offset = Offset.Zero)

        assertEquals(0f, crop.left, TOLERANCE)
        assertEquals(0f, crop.top, TOLERANCE)
        assertEquals(0.5f, crop.right, TOLERANCE)
        assertEquals(0.5f, crop.bottom, TOLERANCE)
    }

    /** A negative offset is the image dragged up and left, so the window moves *down and right* across it. */
    @Test
    fun `a negative offset moves the window down and right`() {
        val crop = cropRectOf(image, viewport = IntSize(500, 500), scale = 1f, offset = Offset(-500f, -500f))

        assertEquals(0.5f, crop.left, TOLERANCE)
        assertEquals(0.5f, crop.top, TOLERANCE)
        assertEquals(1f, crop.right, TOLERANCE)
        assertEquals(1f, crop.bottom, TOLERANCE)
    }

    /**
     * **A non-square viewport takes a band, not a square** — the case the rotating pair's landscape half is framed in.
     *
     * The two axes are divided independently, which is what lets the frame be a different shape from the image.
     */
    @Test
    fun `a letterboxed viewport crops one axis only`() {
        val crop = cropRectOf(image, viewport = IntSize(1000, 500), scale = 1f, offset = Offset.Zero)

        assertEquals(0f, crop.left, TOLERANCE)
        assertEquals(1f, crop.right, TOLERANCE)
        assertEquals(0f, crop.top, TOLERANCE)
        assertEquals(0.5f, crop.bottom, TOLERANCE)
    }

    /**
     * **Clamped to the image, so a viewport larger than what covers it cannot ask for pixels that are not there.**
     *
     * The screen's own gesture floors the scale at cover and clamps the offset, so this should be unreachable from the
     * UI — which is exactly why it is worth pinning here: the guard is the last one, and a rectangle reaching past 1
     * would be handed to the decoder rather than rejected.
     */
    @Test
    fun `the rectangle never leaves the image`() {
        val crop = cropRectOf(image, viewport = IntSize(4000, 4000), scale = 1f, offset = Offset(200f, 200f))

        assertEquals(0f, crop.left, TOLERANCE)
        assertEquals(0f, crop.top, TOLERANCE)
        assertEquals(1f, crop.right, TOLERANCE)
        assertEquals(1f, crop.bottom, TOLERANCE)
    }

    private companion object {
        /** A fraction of an image, so anything past this is a rounding artifact rather than a different crop. */
        const val TOLERANCE = 0.0001f
    }
}
