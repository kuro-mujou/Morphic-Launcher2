package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders a morph frame by frame so it can be *looked at* — the half of a scrub no assertion reaches.
 *
 * **Not an assertion, a viewer**, like [GeneratorRenderHarness]. `VitrallGeneratorTest` proves that the frame is
 * exactly divided at every moment of a scrub, and that holds while the picture is still wrong: a window can be a
 * perfect partition and pass through a moment that reads as a different design, a cut can swing across the frame on
 * its way to a partner it should never have been paired with, and the middle can go so coarse that the density knob
 * appears to move. The numbers say the geometry is sound. Only the pictures say it is a *morph*.
 *
 * What to look for, pulling the frames with `adb pull /sdcard/Pictures/genharness` (see [saveHarnessPng] for the
 * clearing rule):
 *
 * - **Panes should slide and stretch, not swap.** A pane in one place at `25` and somewhere unrelated at `50` means
 *   two cuts were paired that had no business being paired, not that the pane moved.
 * - **The middle should still read as a window** — leaded panes over a came ground, at about the density the ends
 *   have. That the frame is *divided* is now assured; that it is divided into a window anyone would recognize is
 *   not, and a middle that goes conspicuously coarse is this mechanism's own failure mode: it is what a cut being
 *   flattened out of the frame looks like when the two windows' cuts diverge high up.
 * - **The bones should stay on pane boundaries.** They are cuts like any other now, so a bone crossing open glass
 *   would mean the tree and the panes had come apart.
 * - **Nothing should pop at `0` or `100`.** Those two are the plans themselves rather than re-cuts of them, so a jump
 *   there would mean the ends of the scrub are not the windows it is between.
 */
@RunWith(AndroidJUnit4::class)
class MorphRenderHarness {

    private val palette = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.BICHROMATIC)

    /**
     * One shuffle, as the scrub would walk it — two seeds, eleven frames.
     *
     * Eleven rather than five because the failure this is looking for is a *passage*, not a state: a pane taking a
     * wrong partner looks unremarkable at the ends and only reveals itself halfway, and a fold can open and close
     * inside a quarter of the travel.
     */
    @Test
    fun renderVitrallMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val params = DesignParams(colorMode = WallpaperColorMode.BICHROMATIC)
        val morph = VitrallGenerator.morph(
            VitrallGenerator.plan(Width, Height, params, seed = 42L),
            VitrallGenerator.plan(Width, Height, params, seed = 43L),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            VitrallGenerator.draw(Canvas(bitmap), morph.at(t), palette, Width, Height)
            saveHarnessPng(resolver, "morph_vitrall_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Confetti shuffle, colorful and focused near — the setting where the most can go wrong at once.
     *
     * Colorful, because bichromatic leaves one ink and nothing to fade between. *Near*, because the blur is quantized
     * to a few levels and the painter's order is by depth, and both change as depths cross mid-scrub. What to look
     * for: **discs should drift and swell within their own neighborhood, never cross the frame**; a disc changing
     * color should pass through a blend rather than flip; and the frame-to-frame difference should stay small and
     * even. Forty steps rather than ten, so a pop shows as one frame differing sharply from both its neighbors.
     */
    @Test
    fun renderConfettiMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(variant = 1, colorMode = WallpaperColorMode.COLORFUL)
        val inks = colorful.size - 1
        val morph = requireNotNull(
            ConfettiGenerator.morph(
                ConfettiGenerator.plan(Width, Height, params, inks, seed = 42L),
                ConfettiGenerator.plan(Width, Height, params, inks, seed = 43L),
            ),
        )

        for (step in 0..FineSteps) {
            val t = step.toFloat() / FineSteps
            val bitmap = createBitmap(Width, Height)
            ConfettiGenerator.draw(Canvas(bitmap), morph.at(t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_confetti_${step.toString().padStart(2, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Soft Overlaps shuffle at its most deformed and most crowded, in colorful, in Screen — ten frames.
     *
     * What to look for: **each form should slide, swell and change shape without ever folding** — a ring point
     * crossing its neighbors would show as a pinched or looped outline mid-scrub; and the overlaps, which are where
     * this design's color lives, should brighten and dim smoothly as forms cross rather than flicker.
     */
    @Test
    fun renderSoftOverlapsMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(density = 1f, roundness = 0f, colorMode = WallpaperColorMode.COLORFUL)
        val morph = requireNotNull(
            SoftOverlapsGenerator.morph(
                SoftOverlapsGenerator.plan(params, seed = 42L),
                SoftOverlapsGenerator.plan(params, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            SoftOverlapsGenerator.draw(Canvas(bitmap), morph.at(t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_overlaps_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    private companion object {
        /** "Dusk", the render harness's palette — warm sand and terracotta against deep teal. */
        val Dusk = listOf(
            0xFFF2E2C4.toInt(),
            0xFFE6A15C.toInt(),
            0xFFC9603E.toInt(),
            0xFF2C6E6B.toInt(),
            0xFF1F3A4D.toInt(),
            0xFF121E2B.toInt(),
        )
        const val FineSteps = 40
        const val Width = 1080
        const val Height = 2400
        const val Steps = 10
    }
}
