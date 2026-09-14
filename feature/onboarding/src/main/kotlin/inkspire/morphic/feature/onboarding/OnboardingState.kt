package inkspire.morphic.feature.onboarding

import inkspire.morphic.core.model.HomeEdge

/** Which side of the onboarding gate the launcher is on. */
internal enum class OnboardingGateState {
    /** The flag has not been read yet. */
    UNRESOLVED,

    /** Setup is unfinished, so the first-run screen stands in for the launcher. */
    OPEN,

    /** Setup is finished, so the launcher is shown. */
    CLOSED,
}

/**
 * Everything the gate and the first-run screen draw.
 *
 * @property looks the offered looks, in order; empty until they have been read.
 * @property selected the id of the look the user is looking at — already applied, since the preview only shows what the
 *   store holds — or null before the first one lands.
 * @property previewEdge the edge the applied look opens its apps from, read back from the stored register rather than
 *   from the look, so the preview's crossing follows what is really bound.
 */
internal data class OnboardingState(
    val gate: OnboardingGateState = OnboardingGateState.UNRESOLVED,
    val looks: List<LookRow> = emptyList(),
    val selected: String? = null,
    val previewEdge: HomeEdge? = null,
)

/** One offered look as a row: its id, its name, and the line under it. */
internal data class LookRow(val id: String, val name: String, val summary: String)
