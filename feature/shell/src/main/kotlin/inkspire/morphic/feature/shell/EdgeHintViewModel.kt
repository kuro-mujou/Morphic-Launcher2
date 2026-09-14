package inkspire.morphic.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The hint HOME shows once, after first-run setup: which edge opens the apps, and whether it takes two fingers.
 *
 * @property twoFingers true when HOME keeps that edge's one-finger swipe for an action of its own, so the surface opens
 *   with two — said in the hint, since a one-finger swipe would do something else.
 */
internal data class EdgeHint(val edge: HomeEdge, val twoFingers: Boolean)

/**
 * State holder for [EdgeHintOverlay]: whether HOME should point out the edge the chosen look bound.
 *
 * **Read from the stored register, never from the look**, so the hint cannot name an edge that is not bound. A look binds
 * one edge; should several be bound, the bottom is named first, being the one no user has to be taught to find.
 *
 * **Its own holder rather than members of `ShellViewModel`**, for `DefaultLauncherPromptViewModel`'s reason: it shares
 * nothing with the shell's state, and lives in the shell only because the shell is where HOME and its edges meet.
 */
internal class EdgeHintViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** The hint to show, or null once it has been dismissed, before setup is finished, or with no edge bound. */
    val hint: StateFlow<EdgeHint?> = combine(
        settingsRepository.onboarding,
        settingsRepository.surfaceRegister,
        settingsRepository.homeGestures,
    ) { onboarding, register, gestures ->
        if (!onboarding.completed || onboarding.edgeHintDismissed) return@combine null
        val edges = register.sides.keys
        val edge = HomeEdge.BOTTOM.takeIf { it in edges } ?: HomeEdge.entries.firstOrNull { it in edges }
        edge?.let { EdgeHint(edge = it, twoFingers = it in gestures.twoFingerEdges) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Records that the hint has served its purpose; it does not come back. */
    fun dismiss() {
        viewModelScope.launch { settingsRepository.dismissEdgeHint() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
