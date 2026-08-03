package inkspire.morphic.feature.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The settings surface's own navigation destinations — **one [NavKey] per section**, not one route carrying a section
 * enum.
 *
 * L1 made its 11 sections *panes inside* a single destination and paid for it three times: back became two mechanisms
 * stitched together by hand (`if (selected != null) closeDetail() else navigator.goBack()`), section state survived
 * rotation through a hand-written `Saver` instead of the back stack that already does that, and its two-pane tablet
 * mode silently dropped the "close detail" concept entirely. Three symptoms, one cause — the thing the user navigated
 * to was not a navigation destination. Navigation 3 makes a key cheap enough that there is no reason to repeat it.
 *
 * **These live here, not in `core:navigation`.** That module holds the destinations *other* modules need to reach
 * (`HomeRoute`, `SettingsRoute`); a section is settings' own business. L1 put its `SettingsSection` enum in the
 * navigation module because a route carried it, which is how `feature:home` ended up importing
 * `SettingsSection.WALLPAPER` and every module that touched navigation could see the whole settings taxonomy. `app`
 * maps these keys to screens, exactly as it already does for its own dev-harness key — `entryProvider` is a mapping,
 * not a registry.
 */

/**
 * The **surface register**: which surface each HOME edge opens, in which layout.
 *
 * The first section, because it is the one the launcher visibly lacks — with nothing bound, no edge of HOME is
 * swipeable at all.
 */
@Serializable
data object SurfaceRegisterRoute : NavKey

/**
 * **Icon sizing**: how big icons and labels are in each of the launcher's grids.
 *
 * The port of L1's per-surface icon controls. One section rather than five, because L2 has no per-surface sections yet;
 * the controls themselves stay a reusable group so those sections embed them rather than copying them.
 */
@Serializable
data object IconSizingRoute : NavKey
