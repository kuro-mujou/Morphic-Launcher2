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
