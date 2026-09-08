package inkspire.morphic.core.common.render

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Keeps an expensive picture caught up to a value that changes faster than it can be drawn: a cheap [draft] of the
 * newest value at once, then a [settled] pass once nothing newer has arrived for [settle].
 *
 * The two studios both draw from a recipe a finger is dragging — an icon's layer set, a wallpaper's design
 * parameters — and both cost far more per picture than the gap between two frames of that drag. This is the loop
 * that makes such a preview move at all, and every clause of it is a bug that was shipped once.
 *
 * ## The draft runs to completion; only the settled pass is abandoned
 *
 * The obvious shape is a `collectLatest` — cancel whatever is running the moment a newer value arrives. That
 * conflates by cancellation, and **it works only while the work is shorter than the gap between two emissions**. A
 * slider thumb emits per pointer event, about seven milliseconds apart on a 144Hz phone, so for anything heavier than
 * that *every* pass was killed before it finished and the preview did not move at all until the finger lifted. It
 * reads as the preview freezing on a drag while a discrete step — a +/- button, a chip — still works, which sounds
 * like two bugs and is one: a step leaves a gap long enough for a draft to land.
 *
 * Letting the draft finish and *then* taking whatever the newest value is gives the property actually wanted — the
 * preview updates as fast as the machine can draft, never slower and never not at all — while still coalescing, since
 * everything emitted mid-draft collapses into one value. The settled pass keeps the cancelling behavior, because
 * there the original reasoning holds: it is slow, it is superseded the instant the recipe moves, and a stale sharp
 * picture is worth nothing.
 *
 * **No queue.** A drag emits far more values than any renderer can service, and conflating them is the whole
 * requirement.
 *
 * ## The settle is a second, different question
 *
 * The loop asks *"has something newer arrived?"*; [settle] asks *"is the user still going?"* — which nothing else
 * here can see, and without which a settled pass fast enough to finish between two slider frames makes the preview
 * alternate soft and sharp several times a second. That reads as flashing.
 *
 * ## What the caller still owes
 *
 * **Cancelling a coroutine gives the *intent* to abandon work, never the fact of it.** Cancellation is cooperative,
 * so a renderer that never checks runs to the end however dead its coroutine is — which is how one studio once
 * queued a full pass per frame of a drag and delivered the preview in a backlog seconds after the finger lifted. A
 * [settled] pass that is worth abandoning has to check, and a caller that cannot make it check should say so and
 * pay for it.
 *
 * **Publish only after a suspending hop returns.** Assigning the result of a completed-but-cancelled render still
 * runs, since an assignment is not a suspension point; wrapping the work in `withContext` is what turns
 * "was cancelled" into a throw before the picture is shown.
 *
 * @receiver the values to render, newest wins — a [StateFlow] or something conflated from one, since the loop asks
 *   for *the current value* and equality is what suppresses a re-render of a value already drawn. A cold flow that
 *   replays nothing would hang here.
 * @param draft draws the cheap pass, and answers **whether it drew one**. False skips the [settle] wait and goes
 *   straight to [settled] — right where a draft would be the same picture twice, on a target already small enough.
 * @param settled draws the real pass. Cancelled when a newer value arrives.
 */
suspend fun <T : Any> Flow<T>.draftThenSettle(
    settle: Duration,
    draft: suspend (T) -> Boolean,
    settled: suspend (T) -> Unit,
): Nothing {
    var rendered: T? = null
    while (true) {
        val next = first { it != rendered }
        rendered = next

        if (draft(next) && withTimeoutOrNull(settle) { first { it != rendered } } != null) continue

        coroutineScope {
            val pass = launch { settled(next) }
            val superseded = launch {
                first { it != rendered }
                pass.cancel()
            }
            pass.join()
            superseded.cancel()
        }
    }
}
