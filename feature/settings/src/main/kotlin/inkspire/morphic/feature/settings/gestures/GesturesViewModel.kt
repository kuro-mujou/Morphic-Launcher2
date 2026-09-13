package inkspire.morphic.feature.settings.gestures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.gesture.describeGestureAction
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadeStyle
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.SystemShade
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Gestures section shows.
 *
 * @property actions what each swipe on HOME is set to, already named. A direction with no entry has no action.
 * @property showsShadeStyle whether any swipe opens the system panel — the only time the panel style does anything,
 *   and so the only time it is offered.
 * @property shadeStyle the stored panel style.
 * @property swipeServiceOn whether the gesture service is switched on — as fresh as the last
 *   [GesturesViewModel.refreshSwipeService].
 */
data class GesturesState(
    val actions: Map<SwipeDirection, String> = emptyMap(),
    val showsShadeStyle: Boolean = false,
    val shadeStyle: ShadeStyle = ShadeStyle.COMBINED,
    val swipeServiceOn: Boolean = false,
)

/**
 * Screen-level state holder for **Gestures**: what a swipe on HOME itself does, per direction, the panel style, and
 * whether the gesture service a separate shade may need is on.
 *
 * It reads the app catalog as well as the store because an action is listed by name, and an app's name is the
 * catalog's — see `describeGestureAction`. Assigning happens in the action picker, a destination of its own, so the
 * only write here is the style.
 */
class GesturesViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
    private val systemShade: SystemShade,
) : ViewModel() {

    /** Re-read by [refreshSwipeService] rather than observed: the service is switched in the system's own settings. */
    private val swipeServiceOn = MutableStateFlow(systemShade.swipeServiceOn)

    val state: StateFlow<GesturesState> =
        combine(settingsRepository.homeGestures, appRepository.observeApps(), swipeServiceOn) { gestures, apps, serviceOn ->
            val catalog = apps.associateBy { it.componentKey }
            GesturesState(
                actions = gestures.swipes.mapValues { describeGestureAction(it.value, catalog) },
                showsShadeStyle = GestureAction.OpenSystemPanel in gestures.swipes.values,
                shadeStyle = gestures.shadeStyle,
                swipeServiceOn = serviceOn,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GesturesState())

    /** Sets how the user's phone arranges its system panels. */
    fun setShadeStyle(style: ShadeStyle) {
        viewModelScope.launch { settingsRepository.setShadeStyle(style) }
    }

    /** Re-reads whether the gesture service is on — as the section comes back from the system's settings. */
    fun refreshSwipeService() {
        swipeServiceOn.value = systemShade.swipeServiceOn
    }

    /** Opens the system's accessibility settings, where the gesture service is switched on. */
    fun openSwipeServiceSettings() = systemShade.openSwipeServiceSettings()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
