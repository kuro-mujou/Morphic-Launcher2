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
 * exactly that reason: a destination only `app` knows about has no business in a `core` module.
 */

/** The launcher itself: HOME plus the side surfaces panned to from its edges. The start destination. */
@Serializable
data object HomeRoute : NavKey

/**
 * The settings surface.
 *
 * Argument-free on purpose *for now*. L1's equivalent took an `initialSection` so a long-press could deep-link
 * straight to one group of settings, and something like that is likely to come back — but as of this commit
 * `feature:settings` has no sections to link to, and inventing the taxonomy before the screens exist is exactly the
 * "no model in a vacuum" mistake. Whether a section becomes a route argument or its own [NavKey] is a decision for
 * the port that introduces them, and it is worth making then: in L1 sections were *not* on the back stack, so
 * settings ended up with two incompatible back mechanisms stitched together by hand.
 */
@Serializable
data object SettingsRoute : NavKey
