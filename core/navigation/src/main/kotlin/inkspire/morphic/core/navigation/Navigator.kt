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
    }
}
