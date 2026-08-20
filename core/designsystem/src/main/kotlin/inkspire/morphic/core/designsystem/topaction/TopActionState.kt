package inkspire.morphic.core.designsystem.topaction

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the finger rests in the collapsed strip before the band opens up. Short — opening only commits the band
 * to being a target, it performs nothing — but not zero, so brushing the top edge on the way somewhere else does not
 * flash a delete panel across the screen.
 */
private const val ExpandDwellMs = 300L

/**
 * How long an expanded [TopActionMode.ADD_TO_HOME] band waits before it actually hands the drag to HOME. Much longer
 * than the expand dwell, because this one *acts*: the band is already open and naming what it will do, and this is
 * the window in which the user can still change their mind by moving away.
 */
private const val EjectDwellMs = 700L

/**
 * The extra pause before the band re-opens **immediately after it has just acted** — i.e. the finger never left the
 * top of the screen while the drawer closed underneath it.
 *
 * Without it, handing the drag to HOME would replace "Drop to home" with "Remove | Uninstall" under a stationary
 * finger in the same instant, which is exactly how a user ends up hovering a delete target they never went looking
 * for. It is also why the band deliberately **collapses** when it fires: what the
 * user sees is act → shrink away → pause → open again, now offering something else.
 */
private const val SwitchGraceMs = 500L

/**
 * Everything [TopActionZone] needs to draw itself — resolved by [rememberTopActionState], which owns the timing.
 *
 * A snapshot rather than a mutable holder because every field is derived from the drag and the clock: there is no
 * state here a caller could sensibly *set*, and returning a value keeps the hovered half a plain computation instead
 * of a write during composition.
 */
@Immutable
class TopActionState internal constructor(
    val mode: TopActionMode?,
    val expanded: Boolean,
    val showUninstall: Boolean,
    val hoveredTarget: TopActionTarget?,
)

/**
 * The band's **collapse ⇄ expand ⇄ fire** machine: when it opens, when it acts, and which half is armed.
 *
 * ## The threshold is asymmetric, and that is the whole of the interaction
 *
 * The band counts as reached when the finger is above its *current* height — the status-bar inset while collapsed,
 * [TopActionExpandedHeight] once expanded. That hysteresis is what makes it usable: a deliberate push into the very
 * top of the screen arms it, and only a deliberate pull well clear disarms it, so it cannot chatter open and shut
 * while the finger sits near the boundary — the status bar's height to enter, the expanded band's to leave.
 *
 * ## The two modes differ in timing, and only in timing
 *
 * - [TopActionMode.ADD_TO_HOME] opens **at once** and fires after [EjectDwellMs]. At once, because it is about to
 *   act and the user has to see what; the long dwell is the window to back out.
 * - [TopActionMode.DELETE] opens after [ExpandDwellMs] and fires on **release**, never on a hold. A destructive
 *   action that armed itself under a stationary finger would be a trap.
 *
 * After a hand-off the next opening additionally serves [SwitchGraceMs] — see that constant, which is the whole
 * reason the band collapses on firing rather than swapping its labels in place.
 *
 * @param mode what the band is offering, or null when it is not being offered at all (no drag, or nothing it can
 *   take). Null resets everything, so a band that goes away cannot come back mid-dwell.
 * @param fingerInRoot the dragged finger, or null when no drag is in flight. Deliberately a position rather than a
 *   `DragCoordinator`: what opens this band is a finger and a clock, and it has no business knowing what a drag is.
 * @param viewportWidth used to split an expanded DELETE band into halves.
 * @param showUninstall whether the band has two targets. With one, that one is always the hovered target, so the
 *   band always names what a release would do rather than leaving its only option dim.
 * @param onEngage fired once per hand-off, when an [TopActionMode.ADD_TO_HOME] dwell completes.
 */
@Composable
fun rememberTopActionState(
    mode: TopActionMode?,
    fingerInRoot: Offset?,
    viewportWidth: Float,
    showUninstall: Boolean,
    onEngage: () -> Unit,
): TopActionState {
    val density = LocalDensity.current
    val collapsedPx = WindowInsets.statusBars.getTop(density).toFloat()
    val expandedPx = with(density) { TopActionExpandedHeight.toPx() }

    var expanded by remember { mutableStateOf(false) }
    // Survives the effect below restarting, which is the point: it is armed *by* a hand-off and has to still be set
    // when the finger's next arrival restarts the dwell.
    var graceArmed by remember { mutableStateOf(false) }
    // Held live so a completed dwell invokes whatever the caller means *now*; the effect outlives recompositions.
    val engage by rememberUpdatedState(onEngage)

    // Reading `expanded` here is what makes the threshold hysteretic, and it cannot feed back on itself: a finger
    // inside the collapsed strip is also inside the expanded one, so arming never un-arms.
    val reached = mode != null && fingerInRoot != null &&
        fingerInRoot.y <= if (expanded) expandedPx else collapsedPx

    LaunchedEffect(reached, mode) {
        if (mode == null) {
            expanded = false
            graceArmed = false
            return@LaunchedEffect
        }
        if (!reached) {
            // Leaving collapses at once — the band is no longer being aimed at, and shrinking is the feedback. No
            // dwell needs canceling: restarting this effect canceled whichever one was running.
            expanded = false
            return@LaunchedEffect
        }
        if (graceArmed) {
            delay(SwitchGraceMs.milliseconds)
            graceArmed = false
        }
        when (mode) {
            TopActionMode.ADD_TO_HOME -> {
                expanded = true
                delay(EjectDwellMs.milliseconds)
                // Collapse *before* handing over, so the band the finger is now sitting in is the small one again and
                // the grace below governs whatever opens next.
                expanded = false
                graceArmed = true
                engage()
            }
            TopActionMode.DELETE -> {
                delay(ExpandDwellMs.milliseconds)
                expanded = true
            }
        }
    }

    val hoveredTarget = when {
        !expanded || mode != TopActionMode.DELETE -> null
        !showUninstall -> TopActionTarget.REMOVE
        fingerInRoot == null -> null
        fingerInRoot.x < viewportWidth / 2f -> TopActionTarget.REMOVE
        else -> TopActionTarget.UNINSTALL
    }

    return TopActionState(
        mode = mode,
        expanded = expanded,
        showUninstall = showUninstall,
        hoveredTarget = hoveredTarget,
    )
}
