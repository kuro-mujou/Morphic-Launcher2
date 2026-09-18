package inkspire.morphic.core.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.BatteryReading
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.SystemReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Draws a gallery of recipes with [WidgetRender] and saves each as a PNG, so the renderer's *look* can be judged — the
 * placement arithmetic has unit tests, but whether a clock looks like a clock needs a device.
 *
 * **A viewer, not an assertion.** Every recipe is drawn at a wide and a square size, which is how re-laying rather
 * than scaling shows: corner layers stay in their corners and keep their size.
 *
 * Files go to this test app's own `files/widgetharness`, which a fresh run overwrites. Install and run it with
 * `am instrument` rather than the Gradle task — the task uninstalls the test app, and the renders with it:
 *
 * ```
 * gradle :core:widget:installDebugAndroidTest
 * adb shell am instrument -w inkspire.morphic.core.widget.test/androidx.test.runner.AndroidJUnitRunner
 * adb exec-out run-as inkspire.morphic.core.widget.test tar c files/widgetharness > widgetharness.tar
 * ```
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderHarness {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Friday 18 September 2026, 19:14:05 UTC, read in English. */
    private val data = object : ScriptData {
        override val now: Instant = Instant.parse("2026-09-18T19:14:05Z")
        override val battery = BatteryReading.Unknown
        override val system = SystemReading.Unknown
        override val zone: ZoneId = ZoneId.of("UTC")
        override val locale: Locale = Locale.US
    }

    @Test
    fun renderGallery() {
        val out = File(context.filesDir, "widgetharness").apply { deleteRecursively(); mkdirs() }
        val image = File(context.filesDir, "leaf.png").also { writeGradient(it) }
        var current by mutableStateOf(WidgetRecipe() to DpSize(0.dp, 0.dp))

        compose.setContent {
            val (recipe, size) = current
            // A mid-gray stand-in for a wallpaper, so white text and a translucent panel both read.
            Box(Modifier.background(Color(0xFF3A4A5A)).testTag("widget")) {
                WidgetRender(recipe, data, Modifier.size(size))
            }
        }

        val sizes = mapOf("wide" to DpSize(320.dp, 160.dp), "square" to DpSize(160.dp, 160.dp))
        gallery(image.path).forEach { (name, recipe) ->
            sizes.forEach { (sizeName, size) ->
                current = recipe to size
                compose.waitForIdle()
                // The image layer decodes off the main thread, which the compose idling does not wait for.
                Thread.sleep(300)
                compose.waitForIdle()
                val bitmap = compose.onNodeWithTag("widget").captureToImage().asAndroidBitmap()
                File(out, "${name}_$sizeName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun gallery(imagePath: String): Map<String, WidgetRecipe> = mapOf(
        "clock" to WidgetRecipe(
            listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(HH:mm)\$", size = 56f, weight = 300),
                    anchor = WidgetAnchor.CENTER,
                    offsetY = -10f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$tc(up, df(\"EEEE, d MMMM\"))\$", size = 12f, weight = 600, color = Dim),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -16f,
                ),
            ),
        ),
        "anchors" to WidgetRecipe(
            listOf(panel()) + WidgetAnchor.entries.map { anchor ->
                WidgetLayerSpec(WidgetSource.Text(anchor.name, size = 10f), anchor = anchor)
            },
        ),
        "extents" to WidgetRecipe(
            listOf(
                panel(),
                bar(WidgetExtent.Fraction(0.5f), WidgetAnchor.TOP_LEFT, 0xFFE6A15C.toInt()),
                bar(WidgetExtent.Dp(120f), WidgetAnchor.LEFT, 0xFF2C9E8B.toInt()),
                bar(WidgetExtent.Fill, WidgetAnchor.BOTTOM, 0xFFC9603E.toInt()),
                WidgetLayerSpec(
                    WidgetSource.Shape(WidgetSource.Shape.Kind.OVAL, 0xFFF2E2C4.toInt()),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -12f,
                    width = WidgetExtent.Dp(48f),
                    height = WidgetExtent.Dp(48f),
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("centered in a full-width box", size = 11f, align = WidgetSource.Text.Align.CENTER),
                    width = WidgetExtent.Fill,
                ),
            ),
        ),
        "group" to WidgetRecipe(
            listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Image(imagePath),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 12f,
                    width = WidgetExtent.Dp(64f),
                    height = WidgetExtent.Dp(96f),
                ),
                WidgetLayerSpec(
                    WidgetSource.Overlap(
                        listOf(
                            WidgetLayerSpec(
                                WidgetSource.Shape(WidgetSource.Shape.Kind.OVAL, 0xFF2C9E8B.toInt()),
                                width = WidgetExtent.Fill,
                                height = WidgetExtent.Fill,
                            ),
                            WidgetLayerSpec(WidgetSource.Text("TILT", size = 18f, weight = 800), rotation = -20f),
                        ),
                    ),
                    anchor = WidgetAnchor.TOP_RIGHT,
                    offsetX = -12f,
                    offsetY = 12f,
                    width = WidgetExtent.Dp(72f),
                    height = WidgetExtent.Dp(72f),
                    opacity = 0.6f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("hidden", size = 30f),
                    visible = false,
                ),
            ),
        ),
        "problems" to WidgetRecipe(
            listOf(
                panel(),
                WidgetLayerSpec(WidgetSource.Text("open: \$df(hh:mm\$", size = 12f), anchor = WidgetAnchor.TOP, offsetY = 16f),
                WidgetLayerSpec(WidgetSource.Text("zero: \$1/0\$", size = 12f), anchor = WidgetAnchor.CENTER),
                WidgetLayerSpec(
                    WidgetSource.Text("a long line that runs out of room before it ends", size = 12f),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -16f,
                ),
            ),
        ),
    )

    private fun panel() = WidgetLayerSpec(
        WidgetSource.Shape(color = 0x99000000.toInt(), cornerRadius = 24f),
        width = WidgetExtent.Fill,
        height = WidgetExtent.Fill,
    )

    private fun bar(width: WidgetExtent, anchor: WidgetAnchor, color: Int) = WidgetLayerSpec(
        WidgetSource.Shape(color = color, cornerRadius = 4f),
        anchor = anchor,
        width = width,
        height = WidgetExtent.Dp(8f),
    )

    private fun writeGradient(file: File) {
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPaint(
            Paint().apply {
                shader = LinearGradient(0f, 0f, 200f, 300f, 0xFFE6A15C.toInt(), 0xFF1F3A4D.toInt(), Shader.TileMode.CLAMP)
            },
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val Dim = 0xB3FFFFFF.toInt()
    }
}
