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
 * @property cols the grid's **visual** column count.
 * @property rows its visual row count.
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
     * Adds or removes one visual row/column at [edge].
     *
     * The count is clamped by the caller (the editor disables a button at the limit) and floored again by the store,
     * so what arrives here is already legal; what this adds is the placement half. `GridReflow.edit` shifts items for
     * a TOP/LEFT change and re-homes whatever the new grid cannot hold — onto a further page, since home is paged and
     * always has one.
     *
     * A no-op edit (a far-edge growth over an empty grid) reports `changed = false` and writes no placements, which is
     * why the two halves are checked separately rather than assumed to go together.
     */
    fun edit(edge: GridEditorEdge, add: Boolean) {
        val configuration = device.value ?: return
        val current = state.value
        val cols = current.cols ?: return
        val rows = current.rows ?: return

        val isRow = edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM
        val delta = if (add) 1 else -1
        val nextCols = if (isRow) cols else cols + delta
        val nextRows = if (isRow) rows + delta else rows
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
