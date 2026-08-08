package inkspire.morphic.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The launcher's navigation destinations — one `@Serializable` [NavKey] per full-screen place the app can be.
 *
 * **Type-safe keys, not route strings.** Navigation 3's back stack is a plain list of [NavKey]s, so a destination
 * *is* a value: arguments are constructor parameters, checked by the compiler, and a typo cannot compile. There are
 * no path templates, no argument bundles, and no nested graphs to keep in sync.
 *
 * **What belongs here, and what deliberately does not.** These are *destinations*: places with their own back-stack
 * entry. A section, tab or pane *inside* a destination is that feature's own state and stays in that feature — L1 put
 * its 11-value `SettingsSection` enum in this module purely because a route carried it, which meant every module
 * that touched navigation could see (and did import) the whole settings taxonomy. Keeping feature vocabulary out is
 * what stops this file becoming that dumping ground again.
 *
 * **A module may declare its own keys.** Nothing here is a registry — `entryProvider` in `app` maps keys to
 * composables, and it can just as well map a key declared elsewhere. `app` declares its own dev-harness key for
 * exactly that reason: a destination only `app` knows about has no business in a `core` module — and
 * `feature:settings` declares all three of its own, including the settings destination itself.
 *
 * **What is left here is one destination and the [Navigator]**, which is the shape this file was always arguing
 * for: the start destination is genuinely shared (the shell, `app` and back-handling all name it), and everything
 * else belongs to whoever owns the screen. `SettingsRoute` lived here while it was argument-free and moved to
 * `feature:settings` the moment it needed to carry a section — see that file for why the move *is* the answer to
 * the question this one reserved.
 */

/** The launcher itself: HOME plus the side surfaces panned to from its edges. The start destination. */
@Serializable
data object HomeRoute : NavKey

