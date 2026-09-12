package inkspire.morphic.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * What a screen may do to the back stack.
 *
 * The point of an interface rather than handing screens the [NavBackStack] itself: a list is a list, so any caller
 * could `clear()` it, reorder it, or drop the start destination. Naming the operations means the set of things that
 * can happen to navigation is enumerable — and small.
 *
 * **Keep it exactly as wide as its callers need.** It has two methods because two are used. When a third is wanted
 * — reset-the-stack, say — the way it goes wrong is reaching *around* the interface to mutate the raw list inline in
 * a `NavDisplay` entry lambda, which is how a deliberately narrow API turns into a wide one nobody documented. If a
 * caller needs `resetTo`, add `resetTo`; do not add it before one does, and do not work around it.
 *
 * Obtained from [LocalNavigator]; navigation is a **composition** concern here and no ViewModel takes this as a
 * dependency, and that is worth keeping deliberately: a ViewModel that can navigate is a ViewModel you cannot
 * unit-test without a back stack.
 */
interface Navigator {

    /** Pushes [route] on top of the stack. */
    fun goTo(route: NavKey)

    /**
     * Pops the top destination.
     *
     * @return true if something was popped; false when already at the start destination, which is the signal for a
     *   caller (or the system back handler) to let the gesture mean something else — on a launcher, "already home".
     */
    fun goBack(): Boolean

    /**
     * Pops everything above the start destination in one move.
     *
     * **The third method this interface's own notes said to add when a caller finally needed it**, and the caller is
     * the home button. A launcher is `singleTask` and already running, so pressing home does not relaunch anything —
     * it delivers a fresh HOME intent to the live instance, and without this the settings screen or the icon studio
     * simply stays on top of the launcher the user just asked to see. That is the one navigation failure a launcher
     * cannot have: home is the button that is supposed to always work.
     *
     * Not `goBack()` in a loop at the call site, which is the shape this would otherwise take: that is the reaching
     * *around* the interface those notes warn about, and it animates every intermediate destination on the way past.
     *
     * @return true if anything was popped; false when already at the start destination, so a caller can tell "went
     *   home" from "was already there" — the shell needs that difference to decide whether anything else must reset.
     */
    fun goHome(): Boolean
}

/**
 * The [Navigator] for the current composition.
 *
 * `staticCompositionLocalOf` because it never changes for the life of the host — a changing navigator would mean a
 * new back stack, which is a new app. Failing loudly when absent is deliberate: a screen that silently cannot
 * navigate is worse than one that doesn't compose.
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator in composition. Provide LocalNavigator from the NavDisplay host (see app's LauncherNavHost).")
}

/**
 * A [Navigator] backed by [backStack].
 *
 * Lives here rather than as an anonymous object inside a `setContent`, so the back-stack rules are stated once, in
 * the module that owns navigation, instead of being
 * incidental detail in an Activity. Remembered against the stack it drives, so identity is stable across
 * recomposition and nothing downstream re-reads a new navigator every frame.
 */
@Composable
fun rememberLauncherNavigator(backStack: NavBackStack<NavKey>): Navigator = remember(backStack) {
    object : Navigator {
        override fun goTo(route: NavKey) {
            backStack.add(route)
        }

        // Guards the start destination: the launcher's HOME must always be under everything, so back from HOME is
        // "nothing to pop" rather than an empty stack with no screen to show.
        override fun goBack(): Boolean = backStack.size > 1 && backStack.removeLastOrNull() != null

        // Same guard, applied until it bites. A loop rather than `subList(1, size).clear()` because the backing list
        // is snapshot state and the sublist view of one is not something to hand a bulk mutation to; the stack is a
        // handful of entries deep and the writes coalesce into the frame either way.
        override fun goHome(): Boolean {
            val popped = backStack.size > 1
            while (backStack.size > 1) backStack.removeLastOrNull()
            return popped
        }
    }
}
