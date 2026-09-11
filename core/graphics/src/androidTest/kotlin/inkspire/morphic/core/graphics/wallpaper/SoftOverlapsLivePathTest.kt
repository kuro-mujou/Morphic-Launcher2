package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapBlend
import inkspire.morphic.core.graphics.wallpaper.SoftOverlapsGenerator.OverlapLook
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paints one Soft Overlaps plan through both rasterizers in every blend and both looks, and measures the agreement —
 * `VitrallLivePathTest`'s question asked of the one thing Vitrall does not use.
 *
 * **The risk is the blend.** Every form but Normal is laid with a `PorterDuffXfermode` — Screen, Multiply, Overlay —
 * and on a hardware canvas those blend against the render target rather than a bitmap. A mode the GPU path ignored
 * would not throw: it would paint over, which at a form's 85% opacity looks entirely plausible and is simply not the
 * design. So the bar is the same statement about *area* the Vitrall test makes, taken per blend, where an ignored
 * mode lights up every overlap and most of each form's interior.
 *
 * Colorful rather than the default bichromatic, so overlapping forms are different tones and a blend has something
 * to do; with one tone, Screen over itself and Normal over itself differ far less.
 */
@RunWith(AndroidJUnit4::class)
class SoftOverlapsLivePathTest {

    private val palette = PaletteColorMode.resolve(
        Palette(
            listOf(
                0xFFF2E2C4.toInt(),
                0xFFE6A15C.toInt(),
                0xFFC9603E.toInt(),
                0xFF2C6E6B.toInt(),
                0xFF1F3A4D.toInt(),
                0xFF121E2B.toInt(),
            ),
        ),
        WallpaperColorMode.COLORFUL,
    )

    @Test
    fun theHardwareCanvasBlendsTheFormsAsTheBakeDoes() {
        assumeTrue("offscreen hardware rendering needs API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

        val failures = mutableListOf<String>()
        for (look in OverlapLook.entries) {
            for (blend in OverlapBlend.entries) {
                val params = DesignParams(
                    density = 1f,
                    variant = look.ordinal,
                    finish = blend.ordinal,
                    colorMode = WallpaperColorMode.COLORFUL,
                )
                val plan = SoftOverlapsGenerator.plan(params, Seed)
                val baked = createBitmap(Width, Height).also {
                    SoftOverlapsGenerator.draw(Canvas(it), plan, palette, Width, Height)
                }
                val live = LivePath.renderOnHardware(Width, Height) {
                    SoftOverlapsGenerator.draw(it, plan, palette, Width, Height)
                }
                val difference = LivePath.compare(baked, live)
                val name = "${look.name}_${blend.name}"
                // A wallpaper is shown over whatever is behind it, so a bake with any alpha in it is a hole — which is
                // what Multiply baked until its forms were laid opaque, and what first made the two paths disagree.
                val pixels = IntArray(Width * Height).also { baked.getPixels(it, 0, Width, 0, 0, Width, Height) }
                if (pixels.any { it ushr 24 != 0xFF }) failures += "$name: the bake is not opaque"
                Log.i(Tag, "$name mean=${difference.mean} max=${difference.max} loud=${difference.loudShare}")
                saveHarnessPng(resolver, "live_overlaps_${name}_difference.png", difference.map)
                if (difference.loudShare >= MaxLoudShare) failures += "$name: ${difference.loudShare}"
            }
        }
        assertTrue(
            "forms must blend alike on both paths — past $MaxLoudShare of pixels differing by more than " +
                "${LivePath.Loud}, which is a blend the hardware canvas ignored: $failures",
            failures.isEmpty(),
        )
    }

    private companion object {
        const val Tag = "OverlapsLive"
        const val Seed = 42L
        const val Width = 1080
        const val Height = 2400

        /**
         * How much of the frame may disagree loudly, per look and blend, before the two paths are not drawing the
         * same forms.
         *
         * **Measured at `4.5e-4` at worst on an API 36 emulator** (Fill × Normal; Multiply, Overlay and every Glow at
         * exactly zero) — antialiasing along form edges. The bar is Vitrall's, an order of magnitude above that for
         * other GPUs, and a blend the two paths disagree on clears it by another: a Glow × Multiply laid translucent
         * disagrees over `4.5e-2` of the frame.
         */
        const val MaxLoudShare = 0.005
    }
}
