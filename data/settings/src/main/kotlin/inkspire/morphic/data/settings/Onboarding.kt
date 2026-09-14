package inkspire.morphic.data.settings

import kotlinx.serialization.Serializable

/**
 * Where this install is in first-run setup — what the onboarding gate reads to choose between the look picker and the
 * launcher, and what HOME reads to decide whether to point out the edge the look bound.
 *
 * **Only what the user has done is stored.** Whether any later setup step has been done is answered by the subsystem
 * that owns it; this records what nothing else can re-derive — that the user got past the picker, and that they have
 * seen the one hint that follows it.
 *
 * @property completed true once a look has been kept from the first-run screen. **An absent flag on an install that
 *   already stored a surface register reads as [Legacy]**, so a launcher configured before the flag existed never meets
 *   the picker.
 * @property edgeHintDismissed true once HOME's hint about the bound edge has gone — on the first crossing to a side
 *   surface, or when tapped away. Until then the default-launcher prompt holds back, so it never covers the hint.
 */
@Serializable
data class Onboarding(
    val completed: Boolean = false,
    val edgeHintDismissed: Boolean = false,
) {

    companion object {
        /** Not set up: the picker has not been passed. */
        val Default = Onboarding()

        /** Set up, with the hint still to come. */
        val Completed = Onboarding(completed = true)

        /** An install set up before onboarding existed: finished, and nothing left to point out to it. */
        val Legacy = Onboarding(completed = true, edgeHintDismissed = true)
    }
}
