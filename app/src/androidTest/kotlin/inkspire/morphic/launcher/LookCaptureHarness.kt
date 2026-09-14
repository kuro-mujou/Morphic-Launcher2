package inkspire.morphic.launcher

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.LookRepository
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Captures looks as files in `Download/looks` — how a shipped look is made.
 *
 * **Not an assertion but a tool**, like the wallpaper render harnesses, and an instrumentation test because only a
 * process of the launcher itself can read the launcher's store. Two entry points, each run on its own with `#method`:
 * - [captureLook] saves whatever the device is configured with, as `<name>.json`.
 * - [captureBuiltIn] configures one of the first-run screen's looks from [BuiltInRecipes] and saves it as
 *   `<name, lowercased>.json`, the file name `feature:onboarding` reads from its assets.
 *
 * **Run it with `am instrument`, never `connectedDebugAndroidTest`.** The Gradle task uninstalls the app when it ends,
 * and uninstalling a launcher deletes its data — the arrangement just captured, and everything else with it.
 *
 * ```
 * gradle :app:installDebug :app:installDebugAndroidTest
 * adb shell rm -rf /sdcard/Download/looks
 * adb shell am instrument -w -e name Classic -e class inkspire.morphic.launcher.LookCaptureHarness#captureLook \
 *     inkspire.morphic.launcher.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Download/looks
 * ```
 *
 * For a built-in, **clear the store before each look** — `adb shell pm clear inkspire.morphic.launcher`, after backing
 * up anything on the device worth keeping — then run `#captureBuiltIn` with `-e name` set to a key of [BuiltInRecipes].
 * A capture spells out every carried slice, so a store that is not empty would put whatever it held into the shipped
 * look; the harness refuses a store with an edge already bound, which catches the usual way of forgetting.
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
        saveLookFile("$name.json", look.toJson())
    }

    @Test
    fun captureBuiltIn() = runBlocking {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("name")) { "Pass -e name <look>" }
        val recipe = requireNotNull(BuiltInRecipes[name]) { "No recipe named $name; known: ${BuiltInRecipes.keys}" }
        val settings = GlobalContext.get().get<SettingsRepository>()
        check(settings.surfaceRegister.first() == SurfaceRegister.Default) {
            "The store already has a register; pm clear the launcher before capturing a built-in look"
        }
        settings.recipe()
        val look = GlobalContext.get().get<LookRepository>().capture(name)
        saveLookFile("${name.lowercase()}.json", look.toJson())
    }

    /**
     * Writes [json] into `Download/looks`, marked pending until it is complete — a pull can otherwise be handed a file
     * still being written, as the render harnesses found.
     */
    private fun saveLookFile(fileName: String, json: String) {
        val resolver: ContentResolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
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

    private companion object {
        /**
         * **How each first-run look is made**, through the same setters the settings screens call — so every blob in the
         * file is written by the code that owns its slice, never spelled by hand. Everything a recipe does not set stays
         * at its default, which is also what keeps HOME's grid the same size in all four: switching between them in the
         * first-run screen then never strands what HOME seeded while it was being previewed.
         */
        val BuiltInRecipes: Map<String, suspend SettingsRepository.() -> Unit> = mapOf(
            "Classic" to {
                setHomeLayout(HomeLayout.PAGER_WITH_DOCK)
                setSide(HomeEdge.BOTTOM, SideBinding.Apps(AppsLayout.PAGER))
            },
            "Library" to {
                setHomeLayout(HomeLayout.PAGER_WITH_DOCK)
                setSide(HomeEdge.RIGHT, SideBinding.Apps(AppsLayout.CATEGORY_CARD))
            },
            "Minimal" to {
                setHomeLayout(HomeLayout.LIST_WITH_WIDGET_AREA)
                setSide(HomeEdge.BOTTOM, SideBinding.Apps(AppsLayout.VERTICAL_LIST))
            },
            "Index" to {
                setHomeLayout(HomeLayout.PAGER_WITH_DOCK)
                setSide(HomeEdge.BOTTOM, SideBinding.Apps(AppsLayout.VERTICAL_GRID))
                setAlphabetStripEnabled(true)
            },
        )
    }
}
