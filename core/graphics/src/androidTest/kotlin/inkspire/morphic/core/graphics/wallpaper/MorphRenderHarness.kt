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

    /**
     * One Voronoi shuffle at its densest and most scattered — ten frames.
     *
     * What to look for: **cells should stretch and slide, and the seams stay a single even line** — a seam doubled
     * or broken at a junction would mean two cells' shared edge stopped being computed alike; and no cell should
     * pop into or out of existence, since every cell is re-cut around a seed that is always there.
     */
    @Test
    fun renderVoronoiMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val params = DesignParams(density = 1f, irregularity = 1f)
        val morph = requireNotNull(
            VoronoiGenerator.morph(
                VoronoiGenerator.plan(Width, Height, params, palette.size, seed = 42L),
                VoronoiGenerator.plan(Width, Height, params, palette.size, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            VoronoiGenerator.draw(Canvas(bitmap), morph.at(t), palette, Width, Height)
            saveHarnessPng(resolver, "morph_voronoi_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Waves shuffle at full distortion and full variation, gradient-filled — ten frames.
     *
     * What to look for: **crests should bend and slide, and where two cross the band between them should pinch out
     * and reopen smoothly** — a band flashing to another color at a crossing would mean the traced edges and the
     * count had come apart; and the shadow should ride under each crest the whole way rather than lag it.
     */
    @Test
    fun renderWavesMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(
            density = 1f,
            scale = 1f,
            irregularity = 1f,
            variant = 1,
            colorMode = WallpaperColorMode.COLORFUL,
        )
        val morph = requireNotNull(
            WavesGenerator.morph(WavesGenerator.plan(params, seed = 42L), WavesGenerator.plan(params, seed = 43L)),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            WavesGenerator.draw(Canvas(bitmap), morph.at(t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_waves_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Bauhaus shuffle at full variety and colorful — ten frames of tiles turning, blooming and recoloring.
     *
     * What to look for: **a quarter changing corner should turn about its tile, never cross it or jump** — which reads
     * as the tile rotating, the one motion a Bauhaus poster could make; a quarter arriving should grow out of its own
     * corner; and neighbouring tiles should stop reading as one larger circle mid-scrub and read as one again at `100`.
     */
    @Test
    fun renderBauhausMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val morph = requireNotNull(
            BauhausGenerator.morph(
                BauhausGenerator.plan(Width, Height, params, colorful.size, seed = 42L),
                BauhausGenerator.plan(Width, Height, params, colorful.size, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            morph.draw(Canvas(bitmap), t, colorful, Width, Height)
            saveHarnessPng(resolver, "morph_bauhaus_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Truchet shuffle on its coarsest grid — ten frames of the cells that flip turning a quarter.
     *
     * The coarsest grid because its cells are the largest, so the turn is easiest to read, and on a phone's shape
     * they are not square — the case where a corner's path is stretched with its cell. What to look for: **a flipping
     * cell should turn about its own center, both arcs together, and land without a jump at `100`**; the loops
     * through it should break as it turns and join again as it lands; and a cell that does not flip should not move.
     */
    @Test
    fun renderTruchetMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val params = DesignParams(density = 0f)
        val morph = requireNotNull(
            TruchetGenerator.morph(
                TruchetGenerator.plan(Width, Height, params, seed = 42L),
                TruchetGenerator.plan(Width, Height, params, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            morph.draw(Canvas(bitmap), t, palette, Width, Height)
            saveHarnessPng(resolver, "morph_truchet_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Halftone shuffle with its dots at full jitter, colorful — ten frames.
     *
     * What to look for: **dots should drift within their own cells and swell or shrink in place, never cross the
     * screen**; bare paper should open and close in patches rather than flicker dot by dot; and the middle frame should
     * be as contrasty a screen as the ends — big dots and bare paper both — rather than an even field of middling dots.
     */
    @Test
    fun renderHalftoneMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val morph = requireNotNull(
            HalftoneGenerator.morph(
                HalftoneGenerator.plan(Width, Height, params, seed = 42L),
                HalftoneGenerator.plan(Width, Height, params, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            HalftoneGenerator.draw(Canvas(bitmap), morph.at(t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_halftone_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Dot Grid shuffle at full dither, colorful, with no margin — ten frames of the seams' drifts moving.
     *
     * Colorful for five bands and so four seams; full dither and no margin so the drifts are as large as they get and
     * fill the frame. What to look for: **the intrusions along each seam should move as drifts, a few tiles switching
     * at a time**, never a whole seam switching in one frame; and the top edge should keep eroding through the middle,
     * not rule straight.
     */
    @Test
    fun renderDotGridMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, scale = 0f, colorMode = WallpaperColorMode.COLORFUL)
        val morph = requireNotNull(
            DotGridGenerator.morph(
                DotGridGenerator.plan(Width, Height, params, seed = 42L),
                DotGridGenerator.plan(Width, Height, params, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            DotGridGenerator.draw(Canvas(bitmap), morph.at(t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_dotgrid_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Mondrian shuffle at its finest, colorful — ten frames of rulings sliding.
     *
     * What to look for: **every ruling should stay horizontal or vertical and slide along its own axis**, never turn;
     * a block on its way out should narrow into an edge with its own subdivision shrinking inside it; and the ruling
     * should stay one even weight — a doubled line would mean a leaving sliver's outline reading on its own.
     */
    @Test
    fun renderMondrianMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(density = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val accents = MondrianGenerator.accents(colorful).size
        val morph = requireNotNull(
            MondrianGenerator.morph(
                MondrianGenerator.plan(params, accents, seed = 42L),
                MondrianGenerator.plan(params, accents, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            morph.draw(Canvas(bitmap), t, colorful, Width, Height)
            saveHarnessPng(resolver, "morph_mondrian_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Modern Mosaic shuffle at full skew, colorful — ten frames of tiles sliding, narrowing and drifting.
     *
     * What to look for: **the grout should stay one even band the whole way** — two tiles meeting at a corner move it
     * together, so a grout line pinching or widening mid-scrub would mean the skew was no longer one field; tiles
     * should slide and narrow away rather than pop; and the middle should be as skewed as the ends, not squarer.
     */
    @Test
    fun renderModernMosaicMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val tones = RampTones.countFor(colorful.size)
        val morph = requireNotNull(
            ModernMosaicGenerator.morph(
                ModernMosaicGenerator.plan(Width, Height, params, tones, seed = 42L),
                ModernMosaicGenerator.plan(Width, Height, params, tones, seed = 43L),
            ),
        )

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            morph.draw(Canvas(bitmap), t, colorful, Width, Height)
            saveHarnessPng(resolver, "morph_mosaic_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Rounded Tiles shuffle with the fan open and the bars overlapping, colorful — ten frames of the rank sliding.
     *
     * The fan open so each bar has its own angle, and overlapping so the blend has something to combine. What to
     * look for: **the whole rank should slide across its lanes as one**, each bar keeping its angle and color, and
     * the overlaps should brighten and dim smoothly as the bars move rather than flicker.
     */
    @Test
    fun renderRoundedTilesMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(scale = 0f, irregularity = 0.8f, colorMode = WallpaperColorMode.COLORFUL)
        val from = RoundedTilesGenerator.plan(params, seed = 42L)
        val to = RoundedTilesGenerator.plan(params, seed = 43L)

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            RoundedTilesGenerator.draw(Canvas(bitmap), RoundedTilesGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_tiles_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Ribbon Flow shuffle at full Distortion on the densest rank, colorful — ten frames of the lines' wander moving.
     *
     * The setting where the turn is steepest against the ordering bound. What to look for: **the lines should bend
     * and straighten continuously and never touch**, and the middle should wander as much as the ends — a calmer
     * middle would mean the fields were blending rather than turning.
     */
    @Test
    fun renderRibbonFlowMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(density = 1f, irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val from = RibbonFlowGenerator.plan(params, seed = 42L)
        val to = RibbonFlowGenerator.plan(params, seed = 43L)

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            RibbonFlowGenerator.draw(Canvas(bitmap), RibbonFlowGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_ribbonflow_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Ribbons shuffle between two bundles sweeping opposite ways across the frame, fully splayed, colorful.
     *
     * Opposite sweeps because that is the one this design's scrub is built around: read naively, the two would fold
     * into a vertical line halfway. What to look for: **the bundle should stay across the frame the whole way, its S
     * bending and its pinch sliding to the other side**, and the lines should stay nested — a fan, never a scribble.
     */
    @Test
    fun renderRibbonsMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val from = RibbonsGenerator.plan(params, seed = 42L)
        val leftward = from.spine.xs[0] > from.spine.xs[3]
        val to = (43L..200L).map { RibbonsGenerator.plan(params, it) }.first { (it.spine.xs[0] > it.spine.xs[3]) != leftward }

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            RibbonsGenerator.draw(Canvas(bitmap), RibbonsGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_ribbons_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Polygon Cascade shuffle, filled and shadowed, fully turned and wobbled, between two cascades turning
     * opposite ways — ten frames.
     *
     * What to look for: **the run should swing about the frame's centre and never shrink toward it** — copies bunching
     * into a rosette mid-scrub would mean the ends were interpolated rather than the heading; the cascade should
     * untwist through a straight stack and twist the other way; and the wobble's bends should travel round the shape.
     */
    @Test
    fun renderPolygonCascadeMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(
            irregularity = 1f,
            rotation = 1f,
            depth = 1f,
            finish = 1,
            colorMode = WallpaperColorMode.COLORFUL,
        )
        val from = PolygonCascadeGenerator.plan(params, seed = 42L)
        val to = (43L..200L).map { PolygonCascadeGenerator.plan(params, it) }.first { it.sense != from.sense }

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            PolygonCascadeGenerator.draw(Canvas(bitmap), PolygonCascadeGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_cascade_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Flow Lines shuffle between two fans twisting opposite ways, at full waviness, colorful — ten frames.
     *
     * The cascade's scrub on an open curve, so the same things to look for: **the fan should swing about the frame's
     * centre and never bunch toward it**, unwind through a straight rank and twist the other way, and its waves should
     * travel along the curve rather than fade out in place.
     */
    @Test
    fun renderFlowLinesMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(irregularity = 1f, colorMode = WallpaperColorMode.COLORFUL)
        val from = FlowLinesGenerator.plan(params, seed = 42L)
        val to = (43L..200L).map { FlowLinesGenerator.plan(params, it) }.first { it.sense != from.sense }

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            FlowLinesGenerator.draw(Canvas(bitmap), FlowLinesGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_flowlines_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Triangular Facets shuffle at full relief and a hair of leading, colorful — ten frames.
     *
     * What to look for: **the facets should drift and relight continuously**, the color regions should slide into one
     * another rather than fade in place, and the cells that change diagonal should each change once, a few at a time.
     */
    @Test
    fun renderTriangularFacetsMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(depth = 1f, scale = 0.6f, colorMode = WallpaperColorMode.COLORFUL)
        val from = TriangularFacetsGenerator.plan(Width, Height, params, colorful, seed = 42L)
        val to = TriangularFacetsGenerator.plan(Width, Height, params, colorful, seed = 43L)

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            TriangularFacetsGenerator.draw(Canvas(bitmap), TriangularFacetsGenerator.between(from, to, t), colorful, Width, Height)
            saveHarnessPng(resolver, "morph_facets_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Spray shuffle at the default, colorful — ten frames, each moment's cost logged under `MorphTiming`.
     *
     * What to look for: **every cloud should slide as a cloud, as grainy mid-scrub as at the ends** — a middle of
     * tight clumps would mean the offsets were blending, one of long streaks that the drift was being turned; and the
     * dots of trails that only one mist has should fade rather than gather at their start.
     */
    @Test
    fun renderSprayMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(colorMode = WallpaperColorMode.COLORFUL)
        val from = SprayGenerator.plan(Width, Height, params, seed = 42L)
        val to = SprayGenerator.plan(Width, Height, params, seed = 43L)
        val morph = SprayGenerator.Morph(from, to)

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            val started = System.nanoTime()
            val layers = when (step) {
                0 -> listOf(SprayGenerator.sorted(from))
                Steps -> listOf(SprayGenerator.sorted(to))
                else -> morph.at(t)
            }
            val sorted = System.nanoTime()
            SprayGenerator.draw(Canvas(bitmap), layers, from, colorful, Width, Height)
            android.util.Log.i(
                "MorphTiming",
                "spray t=$t moment ${(sorted - started) / 1_000_000} ms, draw ${(System.nanoTime() - sorted) / 1_000_000} ms",
            )
            saveHarnessPng(resolver, "morph_spray_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * One Contour shuffle at the default, colorful — ten frames of the terrain turning, each timed under `MorphTiming`.
     *
     * What to look for: **the contours should slide, pinch off and merge as hills rise and sink**, never jump; the
     * middle should be as busy a map as the ends, not a flatter one; and a contour's color should hold as it moves,
     * changing only where it crosses into another region.
     */
    @Test
    fun renderContourMorph() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val colorful = PaletteColorMode.resolve(Palette(Dusk), WallpaperColorMode.COLORFUL)
        val params = DesignParams(colorMode = WallpaperColorMode.COLORFUL)
        val from = ContourGenerator.plan(Width, Height, params, seed = 42L)
        val to = ContourGenerator.plan(Width, Height, params, seed = 43L)

        for (step in 0..Steps) {
            val t = step.toFloat() / Steps
            val bitmap = createBitmap(Width, Height)
            val started = System.nanoTime()
            ContourGenerator.drawLines(Canvas(bitmap), ContourGenerator.between(from, to, t), colorful, Width, Height)
            android.util.Log.i("MorphTiming", "contour t=$t ${(System.nanoTime() - started) / 1_000_000} ms")
            saveHarnessPng(resolver, "morph_contour_${(t * 100).toInt().toString().padStart(3, '0')}.png", bitmap)
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
