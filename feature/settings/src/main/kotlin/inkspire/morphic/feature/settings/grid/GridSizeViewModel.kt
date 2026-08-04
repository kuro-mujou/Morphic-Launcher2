package inkspire.morphic.feature.settings.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.GridReflow
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.settings.GridOverride
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.icons.IconSizingEdits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the home-grid section shows — resolved together or not at all, since each is keyed by the device
 * configuration the screen has yet to report.
 *
 * @property cols the **stored** visual column count — a wish rather than a promise, exactly as the dock's is. The
 *   count that fits today's icons is smaller whenever the icons have grown since, and the screen clamps it with the
 *   same `CellFit.fitGridConfig` the home surface draws through; nothing writes that clamp back, so shrinking the
 *   icons again brings the count straight back.
 * @property rows likewise. The one write that *does* reduce it is the dock's height commit, which is a deliberate
 *   change to the space home is left with rather than a passing consequence of an icon slider.
 * @property icon the resolved icon sizing, which is what decides how many of either fit.
 * @property dockHeightDp how tall the dock is, so the preview can show the share of the screen home actually gets —
 *   and so the bounds are computed against that area rather than the whole window.
 */
data class GridSizeState(
    val cols: Int? = null,
    val rows: Int? = null,
    val icon: IconSizing? = null,
    val dockHeightDp: Int? = null,
)

/**
 * Screen-level state holder for **HOME's main grid size**: reads its dimensions, and applies an edge edit as the two
 * writes it really is.
 *
 * **Resizing a grid is a settings write *and* a placement write, and this is the one actor that can do both.**
 * Changing the count is `updateGrid`; moving the items that count displaces is `LayoutRepository.apply`. A surface
 * re-reading the new size later could reflow — that is what `HomeViewModel.fitDockTo` does for the dock — but it
 * cannot tell a removed *left* column from a removed *right* one, and the two leave your apps in different places.
 * The edge is knowledge that exists only at the button press, so the placement edit belongs here with it.
 *
 * **Ordering matters, and it is L1's rule kept**: grow the grid before moving items into it, and move items out
 * before shrinking it. Either way the intermediate state a flow observer sees is a grid that can hold its contents,
 * rather than a frame of items hanging outside it.
 */
class GridSizeViewModel(
    private val settingsRepository: SettingsRepository,
    private val layoutRepository: LayoutRepository,
) : ViewModel() {

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GridSizeState> = device
        .flatMapLatest { configuration ->
            if (configuration == null) {
                flowOf(GridSizeState())
            } else {
                combine(
                    settingsRepository.gridConfig(SLOT, configuration),
                    settingsRepository.iconSizing(SLOT, configuration),
                    settingsRepository.dockHeight(configuration),
                ) { config, icon, dockHeightDp ->
                    GridSizeState(config.visualCols, config.visualRows, icon, dockHeightDp)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GridSizeState())

    /**
     * HOME's own icon sizing, edited from this section rather than from a separate icons screen.
     *
     * A surface's icons belong beside its grid, which is how L1 had it — every one of its details embedded
     * `IconLayoutControls` under the layout controls — and the two genuinely depend on each other: the icon size
     * decides how many rows and columns fit, which this screen's bounds are computed from.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { SLOT },
        device = { device.value },
    )

    /** Reports the device configuration being edited — the UI's one job, as on every other surface. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /**
     * Adds or removes one visual row/column at [edge], **starting from the grid the surface is actually drawing**.
     *
     * The count is clamped by the caller (the editor disables a button at the limit) and floored again by the store,
     * so what arrives here is already legal; what this adds is the placement half. `GridReflow.edit` shifts items for
     * a TOP/LEFT change and re-homes whatever the new grid cannot hold — onto a further page, since home is paged and
     * always has one.
     *
     * A no-op edit (a far-edge growth over an empty grid) reports `changed = false` and writes no placements, which is
     * why the two halves are checked separately rather than assumed to go together.
     *
     * **[fromCols]/[fromRows] come from the screen, and are not [GridSizeState]'s stored pair.** A stored count outlives
     * the conditions it was chosen under — enlarge the icons and fewer rows fit — so the grid on screen is the stored
     * one *clamped to what fits*, and that clamp needs a measured area and the current type scale, neither of which a
     * state holder has (the same reason `DockViewModel.setHeight` is told its row cap). Counting from the drawn grid is
     * what makes the − and + buttons mean what they show: pressing − on a four-row home writes three, rather than
     * writing four because storage still remembers five. It also means the reflow moves items relative to the grid the
     * user can see, instead of to one nothing draws.
     *
     * The counterpart write is deliberately absent: an icon change that invalidates a count is *not* written down, so
     * the count returns when the icons shrink. Only a press writes — which is exactly the asymmetry the dock's rows and
     * columns already live by. L1 reconciled it the other way, from a `LaunchedEffect` in this screen that wrote the
     * clamped counts into storage, and so destroyed a row count for good the first time anyone touched an icon slider.
     */
    fun edit(edge: GridEditorEdge, add: Boolean, fromCols: Int, fromRows: Int) {
        val configuration = device.value ?: return

        val isRow = edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM
        val delta = if (add) 1 else -1
        val nextCols = if (isRow) fromCols else fromCols + delta
        val nextRows = if (isRow) fromRows + delta else fromRows
        val nextConfig = SLOT.blueprint.toGridConfig(
            SLOT.blueprint.defaults.getValue(configuration).copy(cols = nextCols, rows = nextRows),
        )

        viewModelScope.launch {
            val moves = placementMoves(edge, add, nextConfig)
            if (add) {
                writeSize(configuration, nextCols, nextRows)
                if (moves.isNotEmpty()) layoutRepository.apply(ORIENTATION, moves)
            } else {
                if (moves.isNotEmpty()) layoutRepository.apply(ORIENTATION, moves)
                writeSize(configuration, nextCols, nextRows)
            }
        }
    }

    /** Clears the size override, returning the grid to its blueprint. Placements are then settled by the surface. */
    fun reset() {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.updateGrid(SLOT, configuration) { GridOverride() } }
    }

    /**
     * The `Move`s that carry HOME's main-zone items into [nextConfig].
     *
     * Scoped to [HomeZone.MAIN]: the dock is its own coordinate space on the same rows, so including it would reflow
     * dock items against home's grid and re-stamp them into it.
     */
    private suspend fun placementMoves(
        edge: GridEditorEdge,
        add: Boolean,
        nextConfig: GridConfig,
    ): List<LayoutChange> {
        val placed = layoutRepository.placements(ORIENTATION).first()
        val onMain = placed.filterValues { it.zone == HomeZone.MAIN }.mapValues { it.value.placement }
        val edited = GridReflow.edit(onMain, edge, add, nextConfig)
        if (!edited.changed) return emptyList()
        return edited.placements
            .filterNot { (item: GridItem, at) -> onMain[item] == at }
            .map { (item, at) -> LayoutChange.Move(item, at, HomeZone.MAIN) }
    }

    private suspend fun writeSize(configuration: DeviceConfiguration, cols: Int, rows: Int) {
        settingsRepository.updateGrid(SLOT, configuration) { copy(cols = cols, rows = rows) }
    }

    private companion object {
        val SLOT = GridSlot.HOME_MAIN

        /** Portrait only, matching the home surface itself until it gains orientation support. */
        val ORIENTATION = Orientation.PORTRAIT
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
