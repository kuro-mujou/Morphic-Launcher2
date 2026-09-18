package inkspire.morphic.data.widgets

import inkspire.morphic.core.widgetscript.ClockTick
import inkspire.morphic.data.widgets.internal.clockTicks
import inkspire.morphic.data.widgets.internal.nextBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ClockTicksTest {

    private val utc = ZoneId.of("UTC")
    private val start = Instant.parse("2026-09-18T19:14:05.250Z")

    @Test
    fun `boundaries are the next whole unit`() {
        assertEquals(Instant.parse("2026-09-18T19:14:06Z"), nextBoundary(start, ClockTick.SECOND, utc))
        assertEquals(Instant.parse("2026-09-18T19:15:00Z"), nextBoundary(start, ClockTick.MINUTE, utc))
        assertEquals(Instant.parse("2026-09-18T20:00:00Z"), nextBoundary(start, ClockTick.HOUR, utc))
        assertEquals(Instant.parse("2026-09-19T00:00:00Z"), nextBoundary(start, ClockTick.DAY, utc))
    }

    @Test
    fun `a day ends at the zone's own midnight`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 19:14 UTC is 04:14 on the 19th in Tokyo, whose next midnight is 15:00 UTC on the 19th.
        assertEquals(Instant.parse("2026-09-19T15:00:00Z"), nextBoundary(start, ClockTick.DAY, tokyo))
    }

    @Test
    fun `a day across a clock change is not 24 hours`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 25 October 2026 is 25 hours long in Berlin: from 22:00 UTC on the 24th to 23:00 UTC on the 25th.
        val night = Instant.parse("2026-10-24T22:30:00Z")
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), nextBoundary(night, ClockTick.DAY, berlin))
    }

    @Test
    fun `a minute clock emits now and then on each minute`() = runTest {
        val origin = start.toEpochMilli()
        val ticks = clockTicks(ClockTick.MINUTE, utc) { Instant.ofEpochMilli(origin + testScheduler.currentTime) }
            .take(3)
            .toList()

        assertEquals(
            listOf(start, Instant.parse("2026-09-18T19:15:00Z"), Instant.parse("2026-09-18T19:16:00Z")),
            ticks,
        )
    }
}
