package inkspire.morphic.feature.settings

import androidx.navigation3.runtime.NavKey
import inkspire.morphic.core.model.AppsLayout
import kotlinx.serialization.Serializable

/**
 * The settings surface, optionally opened straight onto one pane.
 *
 * **It moved here from `core:navigation`, and that move *is* the answer to a question that file reserved.** Its
 * KDoc said the route was argument-free "for now", that a deep link into a section was likely to come back, and
 * that "whether a section becomes a route argument or its own `NavKey` is a decision for the port that introduces
 * them". This is that port — the APPS surface's long-press menu opens the settings for the arrangement it is
 * showing — and the decision has three parts:
 *
 * - **A route argument, not a second destination.** Settings is *one* destination whose sections are panes, two of
 *   which share the screen on a tablet; a pane is not a place on the back stack. An earlier cut gave each section
 *   its own key and was reversed for exactly that reason, and nothing here re-opens it.
 * - **Declared in `feature:settings`, which is what lets the argument be a [SettingsSection] at all.** L1 put that
 *   enum in its navigation module purely because its route carried one, and every module that touched navigation
 *   could then see — and did import — the whole settings taxonomy. Moving the *route* to the feature keeps both
 *   inside it: `app` maps the key (as it already does for the two wallpaper routes) and nobody else names either.
 * - **`core:navigation` keeps only what is shared** — `HomeRoute` and the `Navigator` contract. That it is now one
 *   destination and an interface is the shape that module was always arguing for.
 *
 * @property section which pane to open on, or null to open where settings normally opens. Null is what every route
 *   in *except* a deep link passes, which is why it is the default rather than a required argument.
 * @property layout which arrangement the APPS pane should select — the payload only that section can carry, kept
 *   beside [section] rather than inside it for the reason `SettingsScreen` keeps two saveable enums rather than one
 *   compound selection: the section list's vocabulary is not the place for one section's argument. Meaningless
 *   with any other [section], and ignored there.
 */
@Serializable
data class SettingsRoute(
    val section: SettingsSection? = null,
    val layout: AppsLayout? = null,
) : NavKey
