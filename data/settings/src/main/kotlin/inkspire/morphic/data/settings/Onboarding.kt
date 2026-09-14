package inkspire.morphic.data.settings

import kotlinx.serialization.Serializable

/**
 * Whether this install has been through first-run setup — what the onboarding gate reads to choose between the look
 * picker and the launcher.
 *
 * **Only the finishing is stored.** Whether any later setup step has been done is answered by the subsystem that owns
 * it; this records the one fact nothing else can re-derive, that the user got past the picker.
 *
 * @property completed true once a look has been applied from the first-run screen. **An absent flag on an install
 *   that already stored a surface register reads as true**, so a launcher configured before the flag existed never
 *   meets the picker.
 */
@Serializable
data class Onboarding(val completed: Boolean = false) {

    companion object {
        /** Not set up: the picker has not been passed. */
        val Default = Onboarding()

        /** Set up. */
        val Completed = Onboarding(completed = true)
    }
}
