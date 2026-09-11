package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paints one Waves plan through both rasterizers in both fills, and measures the agreement — `VitrallLivePathTest`'s
 * question asked of the one call Waves makes that nothing else does.
 *
 * **The risk is the shadow's `drawBitmapMesh`.** It is the only way to lay a fade along a curve that the hardware
 * canvas draws at every API level, and a hardware canvas that dropped it would not throw: every band would lose the
 * darkening under its crest, a sixteenth of the frame deep, which reads as the design at *Shadow* `0` rather than as
 * anything having failed. Shadow at full depth, so a dropped mesh would light up a band of every crest.
 */
@RunWith(AndroidJUnit4::class)
class WavesLivePathTest {

    @Test
    fun theHardwareCanvasShadesTheCrestsAsTheBakeDoes() {
        assumeTrue("offscreen hardware rendering needs API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val palette = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)

        val failures = mutableListOf<String>()
        for (fill in WavesGenerator.Fill.entries) {
            val params = DesignParams(
                density = 1f,
                irregularity = 1f,
                depth = 1f,
                variant = fill.ordinal,
                colorMode = WallpaperColorMode.COLORFUL,
            )
            val plan = WavesGenerator.plan(params, Seed)
            val baked = createBitmap(Width, Height).also {
                WavesGenerator.draw(Canvas(it), plan, palette, Width, Height)
            }
            val live = LivePath.renderOnHardware(Width, Height) { WavesGenerator.draw(it, plan, palette, Width, Height) }
            val difference = LivePath.compare(baked, live)
            Log.i(Tag, "${fill.name} mean=${difference.mean} max=${difference.max} loud=${difference.loudShare}")
            saveHarnessPng(resolver, "live_waves_${fill.name}_difference.png", difference.map)
            if (difference.loudShare >= MaxLoudShare) failures += "${fill.name}: ${difference.loudShare}"
        }
        assertTrue(
            "the crests must shade alike on both paths — past $MaxLoudShare of pixels differing by more than " +
                "${LivePath.Loud}, which is a shadow the hardware canvas dropped: $failures",
            failures.isEmpty(),
        )
    }

    private companion object {
        const val Tag = "WavesLive"
        const val Seed = 42L
        const val Width = 1080
        const val Height = 2400

        /** Vitrall's bar, until this design has a measurement of its own. */
        const val MaxLoudShare = 0.005

        val Dusk = listOf(
            0xFFF2E2C4.toInt(),
            0xFFE6A15C.toInt(),
            0xFFC9603E.toInt(),
            0xFF2C6E6B.toInt(),
            0xFF1F3A4D.toInt(),
            0xFF121E2B.toInt(),
        )
    }
}
