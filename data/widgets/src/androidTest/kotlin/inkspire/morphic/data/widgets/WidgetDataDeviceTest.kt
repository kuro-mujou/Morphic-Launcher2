package inkspire.morphic.data.widgets

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.data.widgets.internal.DefaultWidgetDataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * The cadence against the real platform: a widget wakes as often as what it reads can change, and no more.
 *
 * Timing-based, so the windows are wide — a date widget that emitted even once more inside four seconds, or a
 * seconds widget that emitted fewer than three times, is not jitter.
 */
@RunWith(AndroidJUnit4::class)
class WidgetDataDeviceTest {

    private val repository = DefaultWidgetDataRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun cadenceOf(vararg texts: String) =
        WidgetCadence.of(WidgetRecipe(texts.map { WidgetLayerSpec(WidgetSource.Text(it)) }))

    /** How many snapshots [cadence] produces in [windowMs] of real time. */
    private fun emissionsIn(cadence: WidgetCadence, windowMs: Long): List<ScriptData> = runBlocking {
        val seen = mutableListOf<ScriptData>()
        val job = launch { repository.data(cadence).collect { seen += it } }
        delay(windowMs.milliseconds)
        job.cancel()
        seen
    }

    @Test
    fun aDateOnlyWidgetDoesNotWakePerSecond() {
        // Crosses a minute boundary about once in fifteen runs, which a date ignores; only a run across midnight
        // would see a second emission.
        assertEquals(1, emissionsIn(cadenceOf("\$df(EEEE, d MMMM)\$"), windowMs = 4_000).size)
    }

    @Test
    fun aStaticWidgetIsDrawnOnce() {
        assertEquals(1, emissionsIn(cadenceOf("Hello", "\$tc(up, hi)\$"), windowMs = 3_000).size)
    }

    @Test
    fun aSecondsWidgetWakesEverySecond() {
        val seen = emissionsIn(cadenceOf("\$df(HH:mm:ss)\$"), windowMs = 4_000)
        assertTrue("only ${seen.size} emissions in 4s", seen.size >= 4)
        // Each lands on a new second rather than drifting from the subscription time.
        seen.drop(1).forEach { assertTrue("${it.now} is not on a second", it.now.nano < 200_000_000) }
    }

    @Test
    fun theBatteryIsReadWhenDeclared() = runBlocking {
        val battery = repository.data(cadenceOf("\$bi(level)\$")).first().battery
        assertTrue("level ${battery.level}", battery.level in 0..100)
        assertTrue("temperature ${battery.temperature}", battery.temperature > 0f)
    }

    @Test
    fun theSystemIsReadWhenDeclared() = runBlocking {
        val system = repository.data(cadenceOf("\$si(model)\$")).first().system
        assertEquals(Build.MODEL, system.model)
        assertEquals(Build.VERSION.RELEASE, system.androidVersion)
    }
}
