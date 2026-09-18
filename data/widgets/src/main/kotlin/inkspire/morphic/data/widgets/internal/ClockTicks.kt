package inkspire.morphic.data.widgets.internal

import inkspire.morphic.core.widgetscript.ClockTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * The current instant now, and again at every [tick] boundary in [zone] — on the minute, not a minute after
 * subscribing, so a clock turns over when the minute does.
 *
 * Sleeps between boundaries rather than polling. A wake-up that comes early re-reads the clock and sleeps the rest,
 * so it costs an extra emission of the same value at most. What it cannot see is the clock being *set*; the caller
 * restarts it on that broadcast.
 */
internal fun clockTicks(tick: ClockTick, zone: ZoneId, now: () -> Instant): Flow<Instant> = flow {
    while (true) {
        val current = now()
        emit(current)
        val wait = Duration.between(current, nextBoundary(current, tick, zone)).toMillis()
        delay(wait.coerceAtLeast(1).milliseconds)
    }
}

/**
 * The first [tick] boundary after [instant] in [zone]. A day starts at the zone's own midnight, which is not always
 * 24 hours after the last one.
 */
internal fun nextBoundary(instant: Instant, tick: ClockTick, zone: ZoneId): Instant {
    val local = ZonedDateTime.ofInstant(instant, zone)
    val next = when (tick) {
        ClockTick.SECOND -> local.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)
        ClockTick.MINUTE -> local.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        ClockTick.HOUR -> local.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        ClockTick.DAY -> local.toLocalDate().plusDays(1).atStartOfDay(zone)
    }
    return next.toInstant()
}
