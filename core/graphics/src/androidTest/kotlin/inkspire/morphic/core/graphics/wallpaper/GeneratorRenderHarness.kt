package inkspire.morphic.core.graphics.wallpaper

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every generator to a PNG so its *look* can be judged — the one thing a pure-math unit test cannot do, and
 * the reason `android.graphics` needs a real emulator rather than a JVM stub.
 *
 * **Not an assertion, a viewer.** It does not pass or fail on the pixels — it exists to *produce* them.
 *
 * **Writes through the MediaStore into `Pictures/`, not the app's own files dir.** An app's scoped external
 * directory (`Android/data/<pkg>/files`) is invisible to `adb shell` on modern Android, so a file dropped there
 * cannot be pulled; the shared media collection can.
 *
 * **Clear the folder first, every time — the harness cannot.** On the emulator these files land with a *null*
 * `owner_package_name`, and MediaStore then silently refuses this instrumentation's `delete` on them (bulk selection
 * *and* per-item URI alike — both return without removing the file). So a re-run cannot overwrite: `insert` finds the
 * old file still on disk and appends " (1)", "(2)", … and a pull of the plain name reads a **stale** render. This
 * actually masked a fixed generator as unchanged during W5 — the render was right, the pulled file was old. Only
 * `adb shell` has the filesystem access to clear them, so the reliable loop is:
 *
 * ```
 * adb shell rm -rf /sdcard/Pictures/genharness
 * gradle :core:graphics:connectedDebugAndroidTest
 * adb pull /sdcard/Pictures/genharness
 * ```
 *
 * **A run can exit non-zero with every test passed and every PNG written.** Gradle has reported `255` here twice with
 * the results XML showing `tests="10" failures="0" errors="0"` and the full set of files on the device — the failure is
 * in the task's own teardown, not in the instrumentation. So check the results before re-running: the renders are
 * already there, and a re-run costs three and a half minutes to produce the same ones.
 *
 * It walks `WallpaperDesign.entries`, so a new generator is rendered the moment its enum value lands — no edit here.
 */
@RunWith(AndroidJUnit4::class)
class GeneratorRenderHarness {

    // A cohesive palette to render each design in — "Dusk", from the studio's curated sets: warm sand and terracotta
    // against deep teal, so a blend and a facet both have somewhere to go.
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
    fun renderEveryDesign() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

        // Every design in every color mode — the palette is reduced by the mode exactly as the studio does it, so the
        // restrained default (bichromatic) and the loud opt-in (colorful) can be judged side by side.
        for (mode in WallpaperColorMode.entries) {
            val moded = PaletteColorMode.resolve(palette, mode)
            for (design in WallpaperDesign.entries) {
                val bitmap = Generators.forDesign(design).render(
                    width = 1080,
                    height = 2400,
                    palette = moded,
                    params = DesignParams(colorMode = mode),
                    seed = 42L,
                )
                save(resolver, "gen_${mode.name}_${design.name}.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /**
     * Every design at the two ends of the *irregularity* knob (W7), in bichromatic — so the rigid `0` (a clean lattice,
     * straight crests, concentric rings) and the chaotic `1` can be judged against the default the other test renders.
     * A design that ignores irregularity renders the same at both ends, which is itself the thing to confirm.
     */
    @Test
    fun renderIrregularitySweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)

        for (irregularity in floatArrayOf(0f, 1f)) {
            for (design in WallpaperDesign.entries) {
                val bitmap = Generators.forDesign(design).render(
                    width = 1080,
                    height = 2400,
                    palette = moded,
                    params = DesignParams(irregularity = irregularity, colorMode = WallpaperColorMode.BICHROMATIC),
                    seed = 42L,
                )
                save(resolver, "irr_${(irregularity * 100).toInt()}_${design.name}.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /**
     * Every design that exposes a [DesignParams.variant] sub-look, at each of its variants, in bichromatic and
     * monochromatic — a design's second look is as much a look to judge as its first, and half the time it is the
     * restrained one.
     *
     * **The list is asked of the generators, not typed here.** Each declares its own `style.variant`, so a design that
     * grows a sub-look is swept the moment it does, and one whose options change is swept at the new count. A
     * hand-kept list of four names would go stale silently — as this one did, still naming only Contour after three
     * more designs grew variants.
     */
    @Test
    fun renderVariantSweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val designs = WallpaperDesign.entries.mapNotNull { design ->
            Generators.forDesign(design).style.variant?.let { design to it.options.indices }
        }

        for (mode in listOf(WallpaperColorMode.BICHROMATIC, WallpaperColorMode.MONOCHROMATIC)) {
            val moded = PaletteColorMode.resolve(palette, mode)
            for ((design, variants) in designs) {
                for (variant in variants) {
                    val bitmap = Generators.forDesign(design).render(
                        width = 1080,
                        height = 2400,
                        palette = moded,
                        params = DesignParams(variant = variant, colorMode = mode),
                        seed = 42L,
                    )
                    save(resolver, "var_${design.name}_${variant}_${mode.name}.png", bitmap)
                    bitmap.recycle()
                }
            }
        }
    }

    /**
     * [DesignParams.depth], flat and fully dimensional — the knob whose whole job is to stop a faceted field reading
     * as a blurred one, and which therefore cannot be judged from the default alone. On the cascade it is the
     * **shadow**, which exists only under the filled finish; see [arrangementsDeclaring].
     */
    @Test
    fun renderDepthSweep() = sweepFraction("depth", floatArrayOf(0f, 1f), { it.depth }) { params, v ->
        params.copy(depth = v)
    }

    /**
     * [DesignParams.taper], from a run whose elements are all one size to one whose far end has all but vanished —
     * the spacing family's second member, and what a design with a run has beside its [DesignParams.scale].
     */
    @Test
    fun renderTaperSweep() = sweepFraction("taper", floatArrayOf(0f, 0.5f, 1f), { it.taper }) { params, v ->
        params.copy(taper = v)
    }

    /**
     * [DesignParams.roundness], sharp and fully round — the knob whose two ends are two different designs (a Mondrian
     * in a light grout, and a field of pills), so neither can be judged from the middle.
     */
    @Test
    fun renderRoundnessSweep() = sweepFraction("round", floatArrayOf(0f, 0.5f, 1f), { it.roundness }) { params, v ->
        params.copy(roundness = v)
    }

    /** [DesignParams.rotation], square-on and turned as far as the design turns — the orientation family's sweep. */
    @Test
    fun renderRotationSweep() = sweepFraction("turn", floatArrayOf(0f, 0.5f, 1f), { it.rotation }) { params, v ->
        params.copy(rotation = v)
    }

    /**
     * One fraction knob at [values], wherever [declares] finds a generator reading it, saved under [name].
     *
     * **Four sweeps were one nested enumeration and a different field**, which is three near-copies of a loop, and
     * the taper's arrival would have made it four. The part worth sharing is not the loop but *where* it looks —
     * [arrangementsDeclaring].
     */
    private fun sweepFraction(
        name: String,
        values: FloatArray,
        declares: (DesignStyle) -> Any?,
        set: (DesignParams, Float) -> DesignParams,
    ) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val cases = arrangementsDeclaring(declares)

        for (value in values) {
            for ((design, params) in cases) {
                val bitmap = Generators.forDesign(design).render(
                    width = 1080,
                    height = 2400,
                    palette = moded,
                    params = set(params, value),
                    seed = 42L,
                )
                val where = "${design.name}_${params.variant}_${params.finish}"
                save(resolver, "${name}_${(value * 100).toInt()}_$where.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /**
     * Every arrangement of every design — each variant crossed with each finish — at which [declares] finds a knob.
     *
     * **A knob set hangs off a design's choices, not off the design**, which is why nothing here filters on `style`
     * alone. Flow Field's *Dots* belongs to *Pearls*; the cascade's rotation belongs to its five cornered shapes and
     * its shadow to its filled finish. Asking at the design level renders the arrangements where a knob does nothing
     * and misses the ones where it is the point — silently, which is the failure these sweeps exist to make visible.
     */
    private fun arrangementsDeclaring(declares: (DesignStyle) -> Any?): List<Pair<WallpaperDesign, DesignParams>> =
        WallpaperDesign.entries.flatMap { design ->
            val generator = Generators.forDesign(design)
            val variants = generator.style.variant?.options?.indices ?: 0..0
            variants.flatMap { variant ->
                val atVariant = DesignParams(variant = variant, colorMode = WallpaperColorMode.BICHROMATIC)
                val finishes = generator.styleFor(atVariant).finish?.options?.indices ?: 0..0
                finishes
                    .map { atVariant.copy(finish = it) }
                    .filter { declares(generator.styleFor(it)) != null }
                    .map { design to it }
            }
        }

    /**
     * Every design that reads [DesignParams.depthScale], at nothing / shipped / large — the size beside the count,
     * and the knob whose `0` has to render exactly what [DesignParams.depth] `0` renders.
     *
     * **The list is asked of the generators**, for the variant sweep's reason: a hand-kept one goes stale silently.
     */
    @Test
    fun renderDepthScaleSweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val designs = WallpaperDesign.entries.filter { Generators.forDesign(it).style.depthScale != null }

        for (size in floatArrayOf(0f, 0.5f, 1f)) {
            for (design in designs) {
                val bitmap = Generators.forDesign(design).render(
                    width = 1080,
                    height = 2400,
                    palette = moded,
                    params = DesignParams(depthScale = size, colorMode = WallpaperColorMode.BICHROMATIC),
                    seed = 42L,
                )
                save(resolver, "orbsize_${(size * 100).toInt()}_${design.name}.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /**
     * Every design that reads [DesignParams.scale], at both ends — the family the other sweeps had left uncovered.
     *
     * It earned one on the Layered Waves rebuild, where `scale` is *Variation* and its `0` is the design's whole
     * rigid end: the two band layouts go exactly even, so every crest goes flat and the frame is a stack of straight
     * stripes. That is a claim about the picture, and nothing here could render it.
     *
     * **The list is asked of the generators**, for the variant sweep's reason: a hand-kept one goes stale silently.
     */
    @Test
    fun renderScaleSweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val designs = WallpaperDesign.entries.filter { Generators.forDesign(it).style.scale != null }

        for (scale in floatArrayOf(0f, 1f)) {
            for (design in designs) {
                val bitmap = Generators.forDesign(design).render(
                    width = 1080,
                    height = 2400,
                    palette = moded,
                    params = DesignParams(scale = scale, colorMode = WallpaperColorMode.BICHROMATIC),
                    seed = 42L,
                )
                save(resolver, "scale_${(scale * 100).toInt()}_${design.name}.png", bitmap)
                bitmap.recycle()
            }
        }
    }

    /**
     * Every finish of every design that offers more than one — the panel's third segmented control, and the sweep the
     * *Mode* knob arrived with.
     *
     * **Swept across the variants too**, for the color layout sweep's reason and with the same live case: a finish is
     * how a shape is inked, so the shape decides how much the choice shows. An outlined star and a filled one are two
     * pictures; an outlined circle and a filled one are nearly one silhouette.
     *
     * **The list is asked of the generators**, for the variant sweep's reason: a hand-kept one goes stale silently.
     */
    @Test
    fun renderFinishSweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val cases = WallpaperDesign.entries.flatMap { design ->
            val generator = Generators.forDesign(design)
            val variants = generator.style.variant?.options?.indices ?: 0..0
            variants.flatMap { variant ->
                val finishes = generator.styleFor(DesignParams(variant = variant)).finish?.options?.indices
                    ?: IntRange.EMPTY
                finishes.map { Triple(design, variant, it) }
            }
        }

        for ((design, variant, finish) in cases) {
            val bitmap = Generators.forDesign(design).render(
                width = 1080,
                height = 2400,
                palette = moded,
                params = DesignParams(
                    variant = variant,
                    finish = finish,
                    colorMode = WallpaperColorMode.BICHROMATIC,
                ),
                seed = 42L,
            )
            save(resolver, "finish_${design.name}_${variant}_$finish.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * Every design that offers a [DesignParams.colorLayout], at each of its layouts *and at each of its sub-looks* —
     * the newest of the knob families, and one whose whole subject is color, so a layout that reads as a heap of
     * unrelated stops rather than as regions is the only way it can fail.
     *
     * **Swept across the variants too**, for the density sweep's reason: Topography's relief and its lines spend the
     * same layout on a filled band and on a stroked path, and a layout that composes one can be noise in the other.
     *
     * **The list is asked of the generators**, for the variant sweep's reason: a hand-kept one goes stale silently.
     */
    @Test
    fun renderColorLayoutSweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val cases = WallpaperDesign.entries.flatMap { design ->
            val generator = Generators.forDesign(design)
            val variants = generator.style.variant?.options?.indices ?: 0..0
            variants.flatMap { variant ->
                val layouts = generator.styleFor(DesignParams(variant = variant)).colorLayout?.options?.indices
                    ?: IntRange.EMPTY
                layouts.map { Triple(design, variant, it) }
            }
        }

        for ((design, variant, layout) in cases) {
            val bitmap = Generators.forDesign(design).render(
                width = 1080,
                height = 2400,
                palette = moded,
                params = DesignParams(
                    variant = variant,
                    colorLayout = layout,
                    colorMode = WallpaperColorMode.BICHROMATIC,
                ),
                seed = 42L,
            )
            save(resolver, "layout_${design.name}_${variant}_$layout.png", bitmap)
            bitmap.recycle()
        }
    }

    /**
     * Every design that reads [DesignParams.density], at both ends *and at each of its sub-looks* — the last of the
     * knob families to get a sweep, and the one whose ends are hardest to picture from the middle.
     *
     * It earned one on the Flow Field rebuild, where the separation became the design's *unit*: a mark's width, its
     * length and the step that traced it are all multiples of it, so winding density down does not merely spread the
     * marks out — it draws a different picture, of long fat lozenges, and winding it up draws a third one of short
     * hairlines. Nothing in the other sweeps renders either.
     *
     * **Swept across the variants too**, unlike the other knobs, because a design whose sub-looks differ in how they
     * *trace* rather than only in how they are colored answers this knob differently in each — and the variant sweep
     * renders only the default density.
     *
     * **The lists are asked of the generators**, for the variant sweep's reason: a hand-kept one goes stale silently.
     */
    @Test
    fun renderDensitySweep() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val moded = PaletteColorMode.resolve(palette, WallpaperColorMode.BICHROMATIC)
        val designs = WallpaperDesign.entries
            .filter { Generators.forDesign(it).style.amount != null }
            .map { it to (Generators.forDesign(it).style.variant?.options?.indices ?: 0..0) }

        for (density in floatArrayOf(0f, 0.5f, 1f)) {
            for ((design, variants) in designs) {
                for (variant in variants) {
                    val bitmap = Generators.forDesign(design).render(
                        width = 1080,
                        height = 2400,
                        palette = moded,
                        params = DesignParams(
                            density = density,
                            variant = variant,
                            colorMode = WallpaperColorMode.BICHROMATIC,
                        ),
                        seed = 42L,
                    )
                    save(resolver, "dens_${(density * 100).toInt()}_${design.name}_$variant.png", bitmap)
                    bitmap.recycle()
                }
            }
        }
    }

    private fun save(resolver: android.content.ContentResolver, name: String, bitmap: Bitmap) {
        // A plain insert. Overwriting an earlier render is *not attempted* — the class KDoc explains why it cannot work
        // here (null-owner files this instrumentation may not delete); the folder is cleared with `adb shell rm`
        // instead. An in-app delete would only be a no-op dressed up as a safeguard.
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/genharness")
            },
        )!!
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
