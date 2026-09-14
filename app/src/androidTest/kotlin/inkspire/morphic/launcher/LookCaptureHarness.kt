package inkspire.morphic.launcher

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.data.settings.LookRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Captures the look this device is configured with and saves it as `Download/looks/<name>.json` — how a shipped look is
 * made: configure a device, run this, commit the file.
 *
 * **Not an assertion but a tool**, like the wallpaper render harnesses, and an instrumentation test because only a
 * process of the launcher itself can read the launcher's store.
 *
 * **Run it with `am instrument`, never `connectedDebugAndroidTest`.** The Gradle task uninstalls the app when it ends,
 * and uninstalling a launcher deletes its data — the arrangement just captured, and everything else with it.
 *
 * ```
 * gradle :app:installDebug :app:installDebugAndroidTest
 * adb shell rm -rf /sdcard/Download/looks
 * adb shell am instrument -w -e name Classic -e class inkspire.morphic.launcher.LookCaptureHarness \
 *     inkspire.morphic.launcher.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Download/looks
 * ```
 *
 * The `rm` is not optional: MediaStore will not overwrite a file an earlier run left, and names the new one " (1)"
 * instead, so a pull of the plain name reads the old capture — `HarnessOutput.kt` in `core:graphics` has the detail. On
 * Git Bash both device paths need `MSYS_NO_PATHCONV=1`.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // MediaStore's Downloads collection
class LookCaptureHarness {

    @Test
    fun captureLook() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("name") ?: "Captured"
        val look = GlobalContext.get().get<LookRepository>().capture(name)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        saveLookFile(resolver, "$name.json", look.toJson())
    }

    /**
     * Writes [json] into `Download/looks`, marked pending until it is complete — a pull can otherwise be handed a file
     * still being written, as the render harnesses found.
     */
    private fun saveLookFile(resolver: ContentResolver, fileName: String, json: String) {
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/looks")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        ) { "MediaStore refused a row for $fileName" }
        requireNotNull(resolver.openOutputStream(uri)) { "No stream for $fileName" }.use { it.write(json.toByteArray()) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }
}
