package inkspire.morphic.feature.settings.gestures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.gesture.describeGestureAction
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadeStyle
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.core.model.needsGestureService
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.GestureServiceAccess
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
 * @property swipes what each swipe on HOME is set to, already named. A direction with no entry has no action.
 * @property doubleTap what a double tap on HOME is set to, already named, or null for nothing.
 * @property showsShadeStyle whether any gesture on home — HOME's own or an item's — opens a panel: the only time the
 *   panel style does anything, and so the only time it is offered.
 * @property shadeStyle the stored panel style.
 * @property needsService whether any gesture on home, an item's included, runs an action that needs the gesture service.
 * @property serviceOn whether the gesture service is switched on — as fresh as the last
 *   [GesturesViewModel.refreshService].
 */
data class GesturesState(
    val swipes: Map<SwipeDirection, String> = emptyMap(),
    val doubleTap: String? = null,
    val showsShadeStyle: Boolean = false,
    val shadeStyle: ShadeStyle = ShadeStyle.COMBINED,
    val needsService: Boolean = false,
    val serviceOn: Boolean = false,
)

/**
 * Screen-level state holder for **Gestures**: what a swipe or a double tap on HOME itself does, the panel style, and
 * whether the gesture service some of those actions need is on.
 *
 * It reads the app catalog as well as the store because an action is listed by name, and an app's name is the
 * catalog's — see `describeGestureAction`. It reads the items' gestures too, though it lists none of them, because an
 * icon's panel action depends on the same style and service as HOME's. Assigning happens in the action picker, a
 * destination of its own, so the only write here is the style.
 */
class GesturesViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
    private val gestureService: GestureServiceAccess,
) : ViewModel() {

    /** Re-read by [refreshService] rather than observed: the service is switched in the system's own settings. */
    private val serviceOn = MutableStateFlow(gestureService.isOn)

    val state: StateFlow<GesturesState> =
        combine(
            settingsRepository.homeGestures,
            settingsRepository.homeItemGestures,
            appRepository.observeApps(),
            serviceOn,
        ) { gestures, itemGestures, apps, on ->
            val catalog = apps.associateBy { it.componentKey }
            val everyAction = gestures.actions + itemGestures.actions
            GesturesState(
                swipes = gestures.swipes.mapValues { describeGestureAction(it.value, catalog) },
                doubleTap = gestures.doubleTap?.let { describeGestureAction(it, catalog) },
                showsShadeStyle = everyAction.any { it is GestureAction.OpenSystemPanel },
                shadeStyle = gestures.shadeStyle,
                needsService = everyAction.any { it.needsGestureService },
                serviceOn = on,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GesturesState())

    /** Sets how the user's phone arranges its system panels. */
    fun setShadeStyle(style: ShadeStyle) {
        viewModelScope.launch { settingsRepository.setShadeStyle(style) }
    }

    /** Re-reads whether the gesture service is on — as the section comes back from the system's settings. */
    fun refreshService() {
        serviceOn.value = gestureService.isOn
    }

    /** Opens the system's accessibility settings, where the gesture service is switched on. */
    fun openServiceSettings() = gestureService.openSettings()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
