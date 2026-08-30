package inkspire.morphic.core.graphics.wallpaper

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
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
 * cannot be pulled; the shared media collection can. Run it, then pull and look:
 *
 * ```
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

        for (design in WallpaperDesign.entries) {
            val bitmap = Generators.forDesign(design).render(
                width = 1080,
                height = 2400,
                palette = palette,
                params = DesignParams(),
                seed = 42L,
            )
            save(resolver, "gen_${design.name}.png", bitmap)
            bitmap.recycle()
        }
    }

    private fun save(resolver: android.content.ContentResolver, name: String, bitmap: Bitmap) {
        // Replace any earlier render of the same design, so a re-run does not pile up `gen (1).png` duplicates.
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/genharness")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
