package inkspire.morphic.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.widget.WidgetRender
import inkspire.morphic.core.widgetscript.BatteryReading
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.SystemReading
import inkspire.morphic.data.widgets.BuiltInWidgetTemplates
import inkspire.morphic.data.widgets.WidgetTemplate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Draws every template in the library as HOME does — its span's cells, less `WidgetCellInset` — and saves each as a
 * PNG: the library is a design job, and whether a design works is judged by looking at it.
 *
 * **A viewer, not an assertion.** Each template is drawn at two cell sizes (the default phone grid's, and a denser
 * one) to show it re-lays, and over a light and a dark backdrop, since a design has to read over whatever wallpaper
 * it lands on.
 *
 * Files go to this test app's own `files/templategallery`, which a fresh run overwrites. Run it with `am instrument`
 * rather than the Gradle task, which uninstalls the test app and the renders with it:
 *
 * ```
 * gradle :feature:home:installDebugAndroidTest
 * adb shell am instrument -w inkspire.morphic.feature.home.test/androidx.test.runner.AndroidJUnitRunner
 * adb exec-out run-as inkspire.morphic.feature.home.test tar c files/templategallery > templategallery.tar
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TemplateGalleryHarness {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Friday 18 September 2026, 19:14:05 UTC, read in English, on a battery at 72% and charging. */
    private val data = object : ScriptData {
        override val now: Instant = Instant.parse("2026-09-18T19:14:05Z")
        override val battery = BatteryReading(level = 72, charging = true, source = BatteryReading.PowerSource.AC, temperature = 31f)
        override val system = SystemReading.Unknown
        override val zone: ZoneId = ZoneId.of("UTC")
        override val locale: Locale = Locale.US
    }

    @Test
    fun renderLibrary() {
        val out = File(context.filesDir, "templategallery").apply { deleteRecursively(); mkdirs() }
        var current by mutableStateOf(Frame(BuiltInWidgetTemplates.all.first(), PhoneCell, Light))

        compose.setContent {
            val frame = current
            val span = frame.template.recipe.span
            Box(
                Modifier
                    .background(frame.backdrop)
                    .padding(16.dp)
                    .testTag("frame"),
            ) {
                WidgetRender(
                    frame.template.recipe,
                    data,
                    Modifier
                        .size(frame.cell.width * span.cols, frame.cell.height * span.rows)
                        .then(WidgetCellInset),
                )
            }
        }

        BuiltInWidgetTemplates.all.forEach { template ->
            mapOf("phone" to PhoneCell, "dense" to DenseCell).forEach { (cellName, cell) ->
                mapOf("light" to Light, "dark" to Dark).forEach { (backdropName, backdrop) ->
                    current = Frame(template, cell, backdrop)
                    compose.waitForIdle()
                    val bitmap = compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
                    File(out, "${template.id}_${cellName}_$backdropName.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }

    private data class Frame(val template: WidgetTemplate, val cell: DpSize, val backdrop: Brush)

    private companion object {
        /** A visual cell on a 1080 × 2400, 420 dpi phone at the default 4 × 5 grid, measured on HOME. */
        val PhoneCell = DpSize(103.dp, 154.dp)

        /** A smaller phone or a denser grid: five columns, six rows. */
        val DenseCell = DpSize(82.dp, 120.dp)

        /** A pale sky over sand — the wallpaper a translucent dark panel has the most trouble on. */
        val Light = Brush.verticalGradient(listOf(Color(0xFFBFD6EA), Color(0xFFEBDCC5)))
        val Dark = Brush.verticalGradient(listOf(Color(0xFF1C2733), Color(0xFF3E3348)))
    }
}
