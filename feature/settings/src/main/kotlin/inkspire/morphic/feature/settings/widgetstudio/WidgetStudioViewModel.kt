package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.movedTo
import inkspire.morphic.core.widget.scaledBy
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.widgets.BuiltInBlocks
import inkspire.morphic.data.widgets.WidgetBlock
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * What is being edited: in Style, a block or the widget; in Advanced, any layer of the tree.
 *
 * **One value, not three**, because they are one fact seen at two depths — Advanced opened on a selected block starts
 * at that block, and a block tapped on the preview while in Advanced moves the path there.
 *
 * @property selected the block being styled, by its layer index, or null for the widget itself.
 * @property path the layer open in Advanced; empty for the widget.
 */
data class StudioFocus(val selected: Int? = null, val path: LayerPath = emptyList(), val advanced: Boolean = false)

/**
 * What the Style tab draws.
 *
 * @property recipe the widget as being edited, or null until it has loaded — and null for good if the widget was
 *   removed meanwhile, which the screen answers by leaving.
 * @property data live readings for the preview, or null until the first arrives.
 * @property missing the widget no longer exists.
 * @property initial each part's globals as they were when the screen opened, by part and then name — what a reset
 *   returns to.
 * @property removed the name of what was just removed, while that removal can still be undone.
 * @property library the blocks on offer while the Add sheet is open, and null while it is closed.
 */
data class WidgetStudioState(
    val recipe: WidgetRecipe? = null,
    val data: ScriptData? = null,
    val missing: Boolean = false,
    val focus: StudioFocus = StudioFocus(),
    val initial: Map<Int?, Map<String, WidgetGlobal>> = emptyMap(),
    val removed: String? = null,
    val library: List<WidgetBlock>? = null,
) {
    /** The block being styled, by its layer index, or null for the widget itself. */
    val selected: Int? get() = focus.selected

    /** The blocks there are to select. */
    val parts: List<StylePart> get() = recipe?.parts().orEmpty()

    /** The settings of whatever is selected — all the Style tab shows. */
    val globals: List<WidgetGlobal> get() = recipe?.globalsOf(selected).orEmpty()

    /** Whether the selected block has a block above it to move past. */
    val canRaise: Boolean get() = selected?.let { recipe?.restacked(it, +1) } != null

    /** Whether the selected block has a block below it to move past. */
    val canLower: Boolean get() = selected?.let { recipe?.restacked(it, -1) } != null

    /** The layer open in Advanced, or null for the widget itself. */
    val layer: WidgetLayerSpec? get() = recipe?.layerAt(focus.path)

    /** The open layer's properties, in the scope its bindings read. */
    internal val fields: List<LayerField>
        get() = recipe?.let { recipe -> layer?.let { layerFields(it, recipe.globalsAt(focus.path)) } }.orEmpty()
}

/**
 * The Style tab for one placed widget: one part at a time — the widget or one of its blocks — its settings edited in
 * place and saved as they change; and, behind Advanced, any layer's own properties.
 *
 * **There is no Save.** Every change is the widget's at once — the preview is the widget, and a user who styled it and
 * then backed out expecting it kept would otherwise lose it. Writes are debounced, since a color drag is a stream,
 * and run on the [ApplicationScope] so leaving the screen mid-debounce still lands the last value.
 */
@Suppress("TooManyFunctions") // One verb per thing the screen can do to a widget, which is what a ViewModel is here.
class WidgetStudioViewModel(
    private val route: WidgetStudioRoute,
    private val layoutRepository: LayoutRepository,
    widgetData: WidgetDataRepository,
    private val applicationScope: ApplicationScope,
) : ViewModel() {

    private val recipe = MutableStateFlow<WidgetRecipe?>(null)
    private val missing = MutableStateFlow(false)
    private val focus = MutableStateFlow(StudioFocus())
    private val initial = MutableStateFlow<Map<Int?, Map<String, WidgetGlobal>>>(emptyMap())
    private val undo = MutableStateFlow<Removal?>(null)
    private val picking = MutableStateFlow(false)
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
     * the moment it is turned on, through the same cadence HOME uses. While the Add sheet is open it reads what every
     * block on offer reads too, so a battery block previewed on a clock shows the battery rather than nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val data = combine(recipe.filterNotNull(), picking) { recipe, picking ->
        WidgetCadence.of(if (picking) recipe.copy(layers = recipe.layers + BuiltInBlocks.all.map { it.layer }) else recipe)
    }
        .distinctUntilChanged()
        .flatMapLatest(widgetData::data)

    val state: StateFlow<WidgetStudioState> =
        // Started at null, since `combine` waits for every input: a removed widget never reads anything, and its
        // screen still has to learn it is missing.
        combine(
            combine(recipe, data.map<ScriptData, ScriptData?> { it }.onStart { emit(null) }, missing, ::Triple),
            focus,
            initial,
            undo,
            picking,
        ) { (recipe, data, missing), focus, initial, undo, picking ->
            WidgetStudioState(recipe, data, missing, focus, initial, undo?.name, BuiltInBlocks.all.takeIf { picking })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMs), WidgetStudioState())

    /**
     * Styles [part] — a block by its layer index, or the widget itself for null. In Advanced this also opens that
     * block, so a tap on the preview goes to the same place at either depth.
     */
    fun select(part: Int?) {
        focus.update { it.copy(selected = part, path = if (it.advanced) listOfNotNull(part) else it.path) }
    }

    /** Switches between Style and Advanced, Advanced opening on whatever Style had selected. */
    fun toggleAdvanced() {
        focus.update { it.copy(advanced = !it.advanced, path = listOfNotNull(it.selected)) }
    }

    /** Opens the layer at [path] in Advanced; the block it lies in becomes the one selected. */
    fun open(path: LayerPath) {
        focus.update { it.copy(path = path, selected = path.firstOrNull()) }
    }

    /**
     * Replaces the global of [global]'s name, in the selected part, with [global] — a new value from one of the Style
     * tab's controls.
     */
    fun set(global: WidgetGlobal) {
        edit { it.withGlobal(focus.value.selected, global) }
    }

    /** A new value for one of the open layer's properties. */
    internal fun set(field: LayerField.Setting, value: WidgetGlobal) {
        edit { recipe -> recipe.updatedAt(focus.value.path) { field.apply(it, value) } }
    }

    /** New source for one of the open layer's formulas. */
    internal fun set(field: LayerField.Formula, source: String) {
        edit { recipe -> recipe.updatedAt(focus.value.path) { field.apply(it, source) } }
    }

    /** Lets go of the setting deciding one of the open layer's properties, which it then decides itself. */
    internal fun unbind(field: LayerField.Bound) {
        edit { recipe -> recipe.updatedAt(focus.value.path) { field.unbind(it) } }
    }

    /**
     * Adds a new [layer] inside the open group — or beside the open layer, in its group, when that is not one — and
     * opens it.
     */
    fun addLayer(layer: WidgetLayerSpec) {
        val current = recipe.value ?: return
        val path = focus.value.path
        val container = if (current.isContainer(path)) path else path.dropLast(1)
        val children = current.childrenAt(container)
        edit { it.withChildrenAt(container, children + layer) }
        open(container + children.size)
    }

    /** Removes the layer open in Advanced, undoably like a block; the group it was in is opened. */
    fun removeLayer() {
        val path = focus.value.path
        val current = recipe.value ?: return
        val name = current.layerAt(path)?.label ?: return
        remove(name, path)
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
        val part = focus.value.selected ?: return
        val name = recipe.value?.parts()?.firstOrNull { it.index == part }?.name ?: return
        remove(name, listOf(part))
    }

    /** Opens or closes the sheet of blocks to add. */
    fun showLibrary(open: Boolean) {
        picking.value = open
    }

    /**
     * Adds [block] on top of the widget, centered and selected — the drag and pinch that follow are how it is placed,
     * since only the person adding it knows where it should go.
     */
    fun add(block: WidgetBlock) {
        val current = recipe.value ?: return
        val index = current.layers.size
        edit { it.copy(layers = it.layers + block.layer) }
        initial.value += index to current.copy(layers = current.layers + block.layer).globalsOf(index).associateBy { it.name }
        select(index)
        picking.value = false
    }

    /** Moves the selected block one step up ([step] +1) or down (-1) among the blocks, keeping it selected. */
    fun restack(step: Int) {
        val part = focus.value.selected ?: return
        val (next, index) = recipe.value?.restacked(part, step) ?: return
        edit { next }
        // The two blocks traded indices, so their baselines trade with them.
        val baselines = initial.value
        initial.value = baselines + (part to baselines[index].orEmpty()) + (index to baselines[part].orEmpty())
        select(index)
    }

    /** Puts back what the last removal took out, open or selected as it was. */
    fun undoRemove() {
        val removal = undo.value ?: return
        edit { removal.recipe }
        initial.value = removal.initial
        focus.value = removal.focus
    }

    /** The removal can no longer be undone — its prompt has gone. */
    fun forgetRemoval() {
        undo.value = null
    }

    /**
     * Removes the layer at [path], undoably until the next edit, along with widget settings only it read. A block's
     * removal also shifts every later block down an index, and its reset baselines with it.
     */
    private fun remove(name: String, path: LayerPath) {
        val current = recipe.value ?: return
        val before = Removal(name, current, initial.value, focus.value)
        edit { it.removedAt(path).withoutUnusedGlobals() }
        if (path.size == 1) initial.value = before.initial.afterRemoving(path.single())
        focus.update { it.copy(selected = if (path.size == 1) null else it.selected, path = path.dropLast(1)) }
        undo.value = before
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

    /** What a removal took out, and everything needed to put it back as it was. */
    private data class Removal(
        val name: String,
        val recipe: WidgetRecipe,
        val initial: Map<Int?, Map<String, WidgetGlobal>>,
        val focus: StudioFocus,
    )

    private companion object {
        const val SaveDebounceMs = 250L
        const val StopTimeoutMs = 5_000L
    }
}
