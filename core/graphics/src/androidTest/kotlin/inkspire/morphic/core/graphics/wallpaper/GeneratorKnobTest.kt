package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every knob a generator *declares* must change the pixels it draws — the one claim [DesignStyle] rests on and the
 * one nothing else could check.
 *
 * **This is an assertion, unlike [GeneratorRenderHarness], which is a viewer.** The two live apart on purpose: that
 * one exists to produce pictures for a person to judge and must never fail, this one exists to fail.
 *
 * **It was written after a knob was offered for a while that did nothing.** Flow Field declared `scale = "Thickness"`
 * and its `render` never read `params.scale`: the mapping had its own unit test and passed, the panel drew the
 * slider, the wallpaper re-rendered on every drag, and the marks came out the same width every time. That is the
 * exact failure [DesignStyle]'s KDoc argues the declaration exists to prevent, and the declaration alone cannot
 * prevent it — a generator can name a knob and then forget to read it. Only rendering both ends and comparing can
 * tell, which needs `android.graphics` and so belongs here rather than in a JVM test.
 *
 * A textual check — does this file mention `params.scale` — would have caught that one instance and is worth running
 * by hand, but it passes for a generator that reads a param into a variable it never uses, and it cannot see a knob
 * whose two ends happen to resolve to the same number.
 */
@RunWith(AndroidJUnit4::class)
class GeneratorKnobTest {

    // The harness's palette, for its reason: enough stops that a blend, a facet and a ramp all have somewhere to go.
    // A knob is likeliest to look dead on a palette too short to show what it does.
    private val palette = Palette(
        listOf(
            0xFFF2E2C4.toInt(),
            0xFFE6A15C.toInt(),
            0xFFC9603E.toInt(),
            0xFF2C6E6B.toInt(),
            0xFF1F3A4D.toInt(),
            0xFF121E2B.toInt(),
        ),
    )

    @Test
    fun everyDeclaredKnobChangesThePicture() {
        val dead = mutableListOf<String>()

        for (design in WallpaperDesign.entries) {
            val generator = Generators.forDesign(design)
            val variants = generator.style.variant?.options?.indices ?: 0..0

            for (variant in variants) {
                val style = generator.styleFor(variant)
                val base = DesignParams(variant = variant, colorMode = WallpaperColorMode.COLORFUL)

                // Each knob at both ends, against the same everything else — so a difference can only be this knob.
                val knobs = buildList {
                    if (style.amount != null) add(Knob("amount", base.copy(density = 0f), base.copy(density = 1f)))
                    if (style.scale != null) add(Knob("scale", base.copy(scale = 0f), base.copy(scale = 1f)))
                    if (style.irregularity != null) {
                        add(Knob("irregularity", base.copy(irregularity = 0f), base.copy(irregularity = 1f)))
                    }
                    if (style.depth != null) add(Knob("depth", base.copy(depth = 0f), base.copy(depth = 1f)))
                    if (style.depthScale != null) {
                        add(Knob("depthScale", base.copy(depthScale = 0f), base.copy(depthScale = 1f)))
                    }
                    if (style.roundness != null) {
                        add(Knob("roundness", base.copy(roundness = 0f), base.copy(roundness = 1f)))
                    }
                    if (style.rotation != null) {
                        add(Knob("rotation", base.copy(rotation = 0f), base.copy(rotation = 1f)))
                    }
                }

                for (knob in knobs) {
                    if (drawsTheSame(design, knob.low, knob.high)) {
                        dead.add("${design.name}[$variant] declares ${knob.name} and ignores it")
                    }
                }

                // The color layouts are the sub-look check on one of the panel's *other* segmented controls, and
                // they are asked per variant because a design's two looks can spend a layout differently — a filled
                // band and a stroked path take their color from the same knob and could easily agree in one and not
                // the other.
                val layouts = style.colorLayout?.options?.indices ?: IntRange.EMPTY
                for (layout in layouts.drop(1)) {
                    val previous = base.copy(colorLayout = layout - 1)
                    if (drawsTheSame(design, previous, base.copy(colorLayout = layout))) {
                        dead.add("${design.name}[$variant] color layout $layout draws what layout ${layout - 1} draws")
                    }
                }

                // And the finishes are the same check on the last of them, asked per variant for the same reason:
                // a finish is how a shape is inked, so a design whose shapes differ could ink one of them alike.
                val finishes = style.finish?.options?.indices ?: IntRange.EMPTY
                for (finish in finishes.drop(1)) {
                    val previous = base.copy(finish = finish - 1)
                    if (drawsTheSame(design, previous, base.copy(finish = finish))) {
                        dead.add("${design.name}[$variant] finish $finish draws what finish ${finish - 1} draws")
                    }
                }
            }

            // A sub-look that draws what the look before it draws is the same failure wearing the variant's clothes.
            for (variant in variants.drop(1)) {
                val previous = DesignParams(variant = variant - 1, colorMode = WallpaperColorMode.COLORFUL)
                val current = DesignParams(variant = variant, colorMode = WallpaperColorMode.COLORFUL)
                if (drawsTheSame(design, previous, current)) {
                    dead.add("${design.name} variant $variant draws what variant ${variant - 1} draws")
                }
            }
        }

        assertFalse(dead.joinToString("\n", prefix = "\n"), dead.isNotEmpty())
    }

    /** One knob's two ends, named for the failure message. */
    private class Knob(val name: String, val low: DesignParams, val high: DesignParams)

    /** Whether [design] draws the same pixels at [low] as at [high] — which, for a declared knob, is the bug. */
    private fun drawsTheSame(design: WallpaperDesign, low: DesignParams, high: DesignParams): Boolean {
        val a = draws(design, low)
        val b = draws(design, high)
        val same = a.sameAs(b)
        // Recycled here rather than by the caller: this sweep renders a couple of hundred frames, and holding them
        // all is how it would fail as an out-of-memory rather than as the report it is meant to produce.
        a.recycle()
        b.recycle()
        return same
    }

    /**
     * [design] rendered at [params], on a frame small enough that a whole sweep of these is affordable.
     *
     * **Small, but not square and not tiny.** A generator frames itself to the aspect it is given, and a square would
     * hide anything that depends on the long side; a frame of a few hundred pixels would let a knob that moves a
     * mark by less than a pixel read as dead. This is the studio's own aspect at a third of its width.
     */
    private fun draws(design: WallpaperDesign, params: DesignParams): Bitmap {
        val moded = PaletteColorMode.resolve(palette, params.colorMode)
        return Generators.forDesign(design).render(360, 800, moded, params, seed = 42L)
    }
}
