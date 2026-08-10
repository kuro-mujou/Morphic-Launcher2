package inkspire.morphic.core.designsystem.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **Claims on the launcher's surface-switching swipe** — who, right now, has a better use for the finger than
 * panning to another surface.
 *
 * A count rather than a flag, because the claimants are independent and can overlap: an item can be held down with
 * its menu up *inside* an open folder, and each half has to be able to let go without cancelling the other's claim.
 * Nothing reads *why* it is locked, only that it is, which is what keeps the set of reasons open — the item options
 * menu (P7) joins by claiming, with no change here or in [SurfacePager].
 *
 * **Why a lock and not pointer consumption.** Consumption cannot express this, in either direction. The pager runs on
 * `PointerEventPass.Initial` — ahead of every item — so an item's own consumption is not even visible to it yet. And
 * ordering cannot arbitrate: the pager claims at the platform touch slop (~8dp) while an item needs its own 20dp, so
 * the pager reaches its threshold *first* and there is nothing consumed yet to see. The two gestures overlap in time
 * and have to be settled by state.
 *
 * This is also why the item gestures read the finger with `positionChangedIgnoreConsumed()`: an item must go on
 * knowing where the finger is even while the pan owns it, or a swipe begun on an icon reaches `Up` having never
 * moved, and is read as a tap.
 *
 * **A claim may be provisional, which is why the pan waits rather than declining.** Most claimants know they want the
 * finger before they take it. [EmbeddedViewTouchFrame] cannot: an embedded Android View is handed movement on the
 * `Final` pass, after the pan has decided, so it claims at the **down** on the chance that it wants the gesture and
 * releases once it has declined. `surfacePagerGesture` therefore treats a claim at slop as *"not yet"* and decides on
 * the first event at which the count is zero. Nothing here changes for the other claimants — they simply never
 * release mid-gesture.
 *
 * Hosted once at the launcher shell, beside the pager it protects. Absent (null local) means nothing is claiming and
 * nothing can — the dev harness and previews, where the pager runs unguarded exactly as it did before.
 */
class SurfaceGestureLock {

    private var claims by mutableIntStateOf(0)

    /** True while anything holds a claim. Read by the pager's gate. */
    val isLocked: Boolean get() = claims > 0

    /** Takes a claim. Pair with exactly one [release]; see [LockSurfaceGesture] for the declarative form. */
    fun acquire() {
        claims++
    }

    /** Drops a claim. Floored at zero so a double release cannot unlock what someone else is still holding. */
    fun release() {
        if (claims > 0) claims--
    }
}

/**
 * The lock the launcher's surfaces claim against, or null outside the shell.
 *
 * `staticCompositionLocalOf` because it never changes for the life of the shell — the *count* inside it is what moves,
 * and that is ordinary snapshot state, so a claim invalidates only what actually reads [SurfaceGestureLock.isLocked].
 */
val LocalSurfaceGestureLock = staticCompositionLocalOf<SurfaceGestureLock?> { null }

/**
 * Holds a claim on the surface swipe for as long as [locked] is true — **the declarative form**, for a claimant whose
 * reason is already a piece of state.
 *
 * An open folder is the case it was written for: the overlay is composed either way, so "is it presenting" is a
 * `Boolean` that wants no event wiring. A claimant whose reason is an *event* — a gesture starting and ending — takes
 * the lock directly instead; `launcherItemGestures` does.
 *
 * The claim is released on dispose as well as on [locked] going false, so a surface that leaves the tree mid-claim
 * cannot leave the swipe locked for the rest of the session.
 */
@Composable
fun LockSurfaceGesture(locked: Boolean) {
    val lock = LocalSurfaceGestureLock.current ?: return
    DisposableEffect(lock, locked) {
        if (locked) lock.acquire()
        onDispose { if (locked) lock.release() }
    }
}
