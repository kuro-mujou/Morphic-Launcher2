package inkspire.morphic.feature.settings.dock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.DockEdit
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.settleDock
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
 * What the dock section shows — all three fields **resolved together or not at all**, since every one of them is keyed
 * by the device configuration the screen has yet to report. Null is "not yet", exactly as `IconSizingState.sizing` is.
 *
 * @property heightDp the strip's height. The dock's one genuine extent setting.
 * @property cols the column count the user wants. A wish rather than a promise — the surface clamps it to what fits
 *   and never writes the clamp back, so a count too large for today's icon size returns when the icons shrink.
 * @property rows likewise, except that a height commit which invalidates it *is* written down (see [setHeight]).
 * @property icon the dock's **resolved** icon sizing — both shown, since these controls now live in this section, and
 *   used: the smallest usable cell it implies is what the height divides into rows and the width into columns.
 */
data class DockState(
    val heightDp: Int? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val icon: IconSizing? = null,
)

/**
 * Screen-level state holder for the dock section: its extent, the grid inside it, and its icons.
 *
 * **Three settings across two stores, which is why they are joined here.** The height is the dock's own extent
 * (`setDockHeight`); the row and column counts are an ordinary grid override (`updateGrid`) like home's; the icon
 * sizing is an icon override, issued through [icons]. They belong on one screen because they constrain each other —
 * the icons set the smallest usable cell, the height divides into rows of at least that, and the width into columns.
 * L1 grouped them the same way, in a dock detail that held its layout controls and `IconLayoutControls` together.
 *
 * **No bounds in this class.** Every limit depends on the measured window and the current type scale, so they live in
 * the screen that has them; this holder only commits values. The one exception is deliberate and stated at
 * [setHeight]: a height commit reduces a row count it has invalidated.
 */
class DockViewModel(
    private val settingsRepository: SettingsRepository,
    private val layoutRepository: LayoutRepository,
) : ViewModel() {

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DockState> = device
        .flatMapLatest { configuration ->
            if (configuration == null) {
                flowOf(DockState())
            } else {
                combine(
                    settingsRepository.dockHeight(configuration),
                    settingsRepository.gridConfig(SLOT, configuration),
                    settingsRepository.iconSizing(SLOT, configuration),
                ) { heightDp, grid, icon -> DockState(heightDp, grid.visualCols, grid.visualRows, icon) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DockState())

    /**
     * The dock's own icon sizing, edited from this section rather than from a separate icons screen.
     *
     * Beside its grid because the two decide each other: the icon size sets the smallest usable cell, which is what
     * the dock's height divides into rows. L1 embedded the same controls in its dock detail for the same reason.
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
     * Commits the strip's height in whole dp, **and reduces the row count if the new height cannot carry it**.
     *
     * A cell is `height ÷ rows`, so shrinking the strip can leave the stored rows describing cells too short to draw
     * an icon in. Rather than leave storage holding a grid the dock will never draw, the rows come down to
     * [maxRows] — the last row is given up so the survivors keep their height, which is the dock rule the settings
     * plan states and the behaviour L1 has.
     *
     * **This is the one clamp that is written back rather than applied on read.** Columns get the opposite treatment
     * (their cap moves with the icon size, so the user's count returns when the icons shrink); rows are reduced
     * because the height that invalidated them is itself a deliberate, committed change.
     *
     * Where the swallowed row's occupants go is not settled here: the height reaches the home surface, which re-fits
     * the dock and spills what no longer fits onto the pager ([settleDock] via `HomeViewModel.fitDockTo`).
     *
     * @param maxRows how many rows the new height can hold, from the screen — the fit needs a measured window and the
     *   current type scale, neither of which a state holder has.
     */
    fun setHeight(dp: Int, maxRows: Int) {
        val configuration = device.value ?: return
        val rows = state.value.rows
        viewModelScope.launch {
            settingsRepository.setDockHeight(configuration, dp)
            if (rows != null && rows > maxRows) {
                settingsRepository.updateGrid(SLOT, configuration) { copy(rows = maxRows) }
            }
        }
    }

    /**
     * Adds or removes one row or column at [edge].
     *
     * **Two writes, as home's is**: the count, and the placements that count displaces. The edge is what makes the
     * second half necessary — removing the *left* column shifts every app left and the leftmost one leaves, where
     * removing the right one drops whatever sat there. A surface re-fitting the new size afterwards could not tell
     * those apart.
     *
     * **Adding a row is only offered while one still fits**, which the screen enforces by disabling the button: rows
     * divide the strip's height, so another row means shorter cells, and past a point they are too short to draw an
     * icon in. The height is not adjusted to make room — it is its own setting, and the user's.
     *
     * Where a displaced app goes is [settleDock]'s to say, not this screen's, and it is the same answer the home
     * surface gets when the strip shrinks under it: onto HOME's main area, never deleted.
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

        viewModelScope.launch {
            val dockConfig = SLOT.blueprint.toGridConfig(
                SLOT.blueprint.defaults.getValue(configuration).copy(cols = nextCols, rows = nextRows),
            )
            val placed = layoutRepository.placements(ORIENTATION).first()
            val moves = settleDock(
                dock = placed.filterValues { it.zone == HomeZone.DOCK }.mapValues { it.value.placement },
                main = placed.filterValues { it.zone == HomeZone.MAIN }.mapValues { it.value.placement },
                dockConfig = dockConfig,
                mainConfig = settingsRepository.gridConfig(GridSlot.HOME_MAIN, configuration).first(),
                edit = DockEdit(edge, add),
            )
            // Grow the grid before moving items into it, and move them out before shrinking it — L1's ordering rule,
            // so no observer ever sees a grid too small for its contents.
            if (add) {
                writeSize(configuration, nextCols, nextRows)
                if (moves.isNotEmpty()) layoutRepository.apply(ORIENTATION, moves)
            } else {
                if (moves.isNotEmpty()) layoutRepository.apply(ORIENTATION, moves)
                writeSize(configuration, nextCols, nextRows)
            }
        }
    }

    private suspend fun writeSize(configuration: DeviceConfiguration, cols: Int, rows: Int) {
        settingsRepository.updateGrid(SLOT, configuration) { copy(cols = cols, rows = rows) }
    }

    /**
     * Clears both overrides, returning the dock to its blueprint.
     *
     * Two writes because they are two stores, but each is a plain write of "nothing" — and both entries are then
     * *removed* rather than stored empty, so a reset leaves storage exactly as a fresh install has it.
     */
    fun reset() {
        val configuration = device.value ?: return
        viewModelScope.launch {
            settingsRepository.setDockHeight(configuration, null)
            settingsRepository.updateGrid(SLOT, configuration) { GridOverride() }
        }
    }

    private companion object {
        val SLOT = GridSlot.HOME_DOCK

        /** Portrait only, matching the home surface itself until it gains orientation support. */
        val ORIENTATION = Orientation.PORTRAIT
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
