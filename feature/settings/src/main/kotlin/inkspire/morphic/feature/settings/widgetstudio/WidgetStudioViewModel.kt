package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.movedTo
import inkspire.morphic.core.widget.scaledBy
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.widgets.WidgetCadence
import inkspire.morphic.data.widgets.WidgetDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the Style tab draws.
 *
 * @property recipe the widget as being edited, or null until it has loaded — and null for good if the widget was
 *   removed meanwhile, which the screen answers by leaving.
 * @property data live readings for the preview, or null until the first arrives.
 * @property missing the widget no longer exists.
 * @property selected the block being styled, by its layer index, or null for the widget itself.
 * @property initial each part's globals as they were when the screen opened, by part and then name — what a reset
 *   returns to.
 * @property removed the name of the block just removed, while that removal can still be undone.
 */
data class WidgetStudioState(
    val recipe: WidgetRecipe? = null,
    val data: ScriptData? = null,
    val missing: Boolean = false,
    val selected: Int? = null,
    val initial: Map<Int?, Map<String, WidgetGlobal>> = emptyMap(),
    val removed: String? = null,
) {
    /** The blocks there are to select. */
    val parts: List<StylePart> get() = recipe?.parts().orEmpty()

    /** The settings of whatever is selected — all the Style tab shows. */
    val globals: List<WidgetGlobal> get() = recipe?.globalsOf(selected).orEmpty()
}

/**
 * The Style tab for one placed widget: one part at a time — the widget or one of its blocks — its settings edited in
 * place and saved as they change.
 *
 * **There is no Save.** Every change is the widget's at once — the preview is the widget, and a user who styled it and
 * then backed out expecting it kept would otherwise lose it. Writes are debounced, since a color drag is a stream,
 * and run on the [ApplicationScope] so leaving the screen mid-debounce still lands the last value.
 */
class WidgetStudioViewModel(
    private val route: WidgetStudioRoute,
    private val layoutRepository: LayoutRepository,
    widgetData: WidgetDataRepository,
    private val applicationScope: ApplicationScope,
) : ViewModel() {

    private val recipe = MutableStateFlow<WidgetRecipe?>(null)
    private val missing = MutableStateFlow(false)
    private val selected = MutableStateFlow<Int?>(null)
    private val initial = MutableStateFlow<Map<Int?, Map<String, WidgetGlobal>>>(emptyMap())
    private val undo = MutableStateFlow<Removal?>(null)
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            val widget = layoutRepository.widgets().first().firstOrNull { it.id == route.widgetId }
            if (widget == null) {
                missing.value = true
            } else {
                val loaded = widget.recipe
                initial.value = (listOf(null) + loaded.parts().map { it.index }).associateWith { part ->
                    loaded.globalsOf(part).associateBy { it.name }
                }
                recipe.value = loaded
            }
        }
    }

    /**
     * Live readings for whatever the recipe reads *now* — a switch that shows a battery line subscribes to the battery
     * the moment it is turned on, through the same cadence HOME uses.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val data = recipe.filterNotNull()
        .map(WidgetCadence::of)
        .distinctUntilChanged()
        .flatMapLatest(widgetData::data)

    val state: StateFlow<WidgetStudioState> =
        // Started at null, since `combine` waits for every input: a removed widget never reads anything, and its
        // screen still has to learn it is missing.
        combine(
            combine(recipe, data.map<ScriptData, ScriptData?> { it }.onStart { emit(null) }, missing, ::Triple),
            selected,
            initial,
            undo,
        ) { (recipe, data, missing), selected, initial, undo ->
            WidgetStudioState(recipe, data, missing, selected, initial, undo?.name)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMs), WidgetStudioState())

    /** Styles [part] — a block by its layer index, or the widget itself for null. */
    fun select(part: Int?) {
        selected.value = part
    }

    /**
     * Replaces the global of [global]'s name, in the selected part, with [global] — a new value from one of the Style
     * tab's controls.
     */
    fun set(global: WidgetGlobal) {
        edit { it.withGlobal(selected.value, global) }
    }

    /** Moves [part] by a drag's step, in dp. The anchor is kept until [settle] re-pins it where the drag ended. */
    fun move(part: Int, dx: Float, dy: Float) {
        edit { recipe -> recipe.withLayer(part) { it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy) } }
    }

    /** Scales [part] by a pinch's step. */
    fun zoom(part: Int, factor: Float) {
        edit { recipe -> recipe.withLayer(part) { it.scaledBy(factor) } }
    }

    /**
     * Re-pins [part], now drawn at [box] inside a widget of [widget] size, to the anchor nearest where it ended up — so
     * a block dragged into a corner stays in that corner when the widget is resized.
     */
    fun settle(part: Int, box: IntRect, widget: IntSize, density: Float) {
        edit { recipe -> recipe.withLayer(part) { it.movedTo(box, widget, density) } }
    }

    /** Removes the selected block, undoably until the next edit. The widget itself is not a block and stays. */
    fun removeSelected() {
        val part = selected.value ?: return
        val current = recipe.value ?: return
        val name = current.parts().firstOrNull { it.index == part }?.name ?: return
        val before = Removal(name, current, initial.value, part)
        edit { it.without(part) }
        initial.value = before.initial.afterRemoving(part)
        selected.value = null
        undo.value = before
    }

    /** Puts back the block [removeSelected] took out, selected again. */
    fun undoRemove() {
        val removal = undo.value ?: return
        edit { removal.recipe }
        initial.value = removal.initial
        selected.value = removal.part
    }

    /** The removal can no longer be undone — its prompt has gone. */
    fun forgetRemoval() {
        undo.value = null
    }

    /** Applies [change], shows it at once and saves it shortly after; any edit ends the chance to undo a removal. */
    private fun edit(change: (WidgetRecipe) -> WidgetRecipe) {
        val current = recipe.value ?: return
        val next = change(current)
        if (next == current) return
        recipe.value = next
        undo.value = null
        pendingSave?.cancel()
        pendingSave = applicationScope.launch {
            delay(SaveDebounceMs.milliseconds)
            layoutRepository.setWidgetRecipe(route.widgetId, next)
        }
    }

    /** A removed block and everything needed to put it back as it was. */
    private data class Removal(
        val name: String,
        val recipe: WidgetRecipe,
        val initial: Map<Int?, Map<String, WidgetGlobal>>,
        val part: Int,
    )

    private companion object {
        const val SaveDebounceMs = 250L
        const val StopTimeoutMs = 5_000L
    }
}
