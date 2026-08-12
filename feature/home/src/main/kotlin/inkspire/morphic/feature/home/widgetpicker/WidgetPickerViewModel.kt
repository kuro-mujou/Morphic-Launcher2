package inkspire.morphic.feature.home.widgetpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.widgets.WidgetCatalog
import inkspire.morphic.data.widgets.WidgetProviderGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the widget picker renders.
 *
 * @property groups the installed widgets by app, or **null while the catalog is still being read** — which is a
 *   different thing from an empty list and is drawn differently: null is a spinner, empty is a device with no
 *   widgets on it. Collapsing the two would show "no widgets" for the second or so the read takes.
 */
data class WidgetPickerState(val groups: List<WidgetProviderGroup>? = null)

/**
 * State holder for the widget picker.
 *
 * **A ViewModel for a sheet, because a modal sheet is a screen**: it has its own state, its own lifetime and its
 * own one-shot load. The alternative — `koinInject` a `WidgetCatalog` in the composable and `produceState` over it,
 * which is what L1 did — puts a platform read inside composition and re-runs it on every recomposition key change.
 *
 * **The read happens once, in `init`, and the result is kept.** Scoped to the host screen's `ViewModelStore`, so
 * closing and reopening the sheet shows the catalog immediately rather than spinning again. What that trades away
 * is freshness: a widget installed while the launcher is open will not appear until the screen is recreated. That
 * is the right way round for now — the picker is opened deliberately and briefly, and `data:apps`' package listener
 * exists to keep the *app* cache live, not this. When it matters, this becomes a flow off the same signal.
 */
class WidgetPickerViewModel(
    private val widgetCatalog: WidgetCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow(WidgetPickerState())
    val state: StateFlow<WidgetPickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = WidgetPickerState(groups = widgetCatalog.installed())
        }
    }
}
