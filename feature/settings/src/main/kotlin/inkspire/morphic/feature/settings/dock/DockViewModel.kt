package inkspire.morphic.feature.settings.dock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DockEdge
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.DockEdit
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.settleDock
import inkspire.morphic.data.settings.GridOverride
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.icons.IconSizingEdits
import inkspire.morphic.feature.settings.icons.SamplePreviewApp
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
 * @property extentDp the strip's thickness — its height as a bottom strip, its width as a rail. The dock's one genuine
 *   extent setting.
 * @property cols the column count the user wants. A wish rather than a promise — the surface clamps it to what fits
 *   and never writes the clamp back, so a count too large for today's icon size returns when the icons shrink.
 * @property rows likewise. Whichever of the two the extent divides is *also* written down when an extent commit
 *   invalidates it (see [setExtent]).
 * @property icon the dock's **resolved** icon sizing — both shown, since these controls now live in this section, and
 *   used: the smallest usable cell it implies is what the extent divides into cells.
 * @property homeIcon HOME's main-area icon sizing. Nothing here edits it and nothing here shows it; it is needed
 *   because this extent decides how much screen home is left with, and how many cells *that* carries depends on home's
 *   own smallest usable cell. See [setExtent].
 */
data class DockState(
    val extentDp: Int? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val icon: IconSizing? = null,
    val homeIcon: IconSizing? = null,
    val paddingDp: Int? = null,
)

/**
 * Screen-level state holder for the dock section: its extent, the grid inside it, and its icons.
 *
 * **Three settings across two stores, which is why they are joined here.** The extent is the dock's own
 * (`setDockExtent`); the row and column counts are an ordinary grid override (`updateGrid`) like home's; the icon
 * sizing is an icon override, issued through [icons]. They belong on one screen because they constrain each other —
 * the icons set the smallest usable cell, and the extent divides into cells of at least that.
 * L1 grouped them the same way, in a dock detail that held its layout controls and `IconLayoutControls` together.
 *
 * **No bounds in this class.** Every limit depends on the measured window and the current type scale, so they live in
 * the screen that has them; this holder only commits values. The one exception is deliberate and stated at
 * [setExtent]: an extent commit reduces the count it has invalidated.
 */
class DockViewModel(
    private val settingsRepository: SettingsRepository,
    private val layoutRepository: LayoutRepository,
    appRepository: AppRepository,
) : ViewModel() {

    /** The app the icon preview draws, and the dice that changes it. Shared by every section that has a preview. */
    internal val sample = SamplePreviewApp(appRepository, viewModelScope)

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DockState> = device
        .flatMapLatest { configuration ->
            if (configuration == null) {
                flowOf(DockState())
            } else {
                combine(
                    settingsRepository.dockExtent(configuration),
                    settingsRepository.gridConfig(SLOT, configuration),
                    settingsRepository.iconSizing(SLOT, configuration),
                    settingsRepository.iconSizing(GridSlot.HOME_MAIN, configuration),
                    settingsRepository.horizontalPadding(SLOT, configuration),
                ) { extentDp, grid, icon, homeIcon, padding ->
                    DockState(extentDp, grid.visualCols, grid.visualRows, icon, homeIcon, padding)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DockState())

    /**
     * The dock's own icon sizing, edited from this section rather than from a separate icons screen.
     *
     * Beside its grid because the two decide each other: the icon size sets the smallest usable cell, which is what
     * the dock's extent divides into cells. L1 embedded the same controls in its dock detail for the same reason.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { SLOT },
        device = { device.value },
    )

    /**
     * Sets the dock's horizontal margin, in dp.
     *
     * **One write, where [setExtent] is two.** A margin takes no cell away — it makes every cell narrower — so the
     * columns that no longer fit are re-reported on read rather than removed, and nothing is displaced for
     * `GridReflow` to re-home. That is the same distinction the repository's own KDoc draws between this and a resize.
     */
    fun setPadding(dp: Int) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setHorizontalPadding(SLOT, configuration, dp) }
    }

    /** Reports the device configuration being edited — the UI's one job, as on every other surface. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /**
     * Commits the strip's extent in whole dp, **and reduces the count it divides if the new extent cannot carry it**.
     *
     * A cell is `extent ÷ count`, so shrinking the strip can leave the stored count describing cells too small to draw
     * an icon in. Rather than leave storage holding a grid the dock will never draw, that count comes down to
     * [maxCells] — the last line is given up so the survivors keep their size, which is the dock rule the settings plan
     * states and the behaviour L1 has.
     *
     * **Which count that is, is [edge]'s to say**: a bottom strip's height divides into *rows*, a rail's width into
     * *columns*. The other axis is untouched, and one step out the same is true of home — a bottom dock takes height
     * off the pager and a rail takes width.
     *
     * **This is the one clamp that is written back rather than applied on read.** The other axis gets the opposite
     * treatment (its cap moves with the icon size, so the user's count returns when the icons shrink); this one is
     * reduced because the extent that invalidated it is itself a deliberate, committed change.
     *
     * Where the swallowed line's occupants go is not settled here: the extent reaches the home surface, which re-fits
     * the dock and spills what no longer fits onto the pager ([settleDock] via `HomeViewModel.fitDockTo`).
     *
     * **The same reduction is applied to HOME's main grid, for the same reason one step out.** L1 did this too, but
     * from the *home surface* (a `LaunchedEffect` on its measured pager bounds, whose comment names "the dock is turned
     * back on" as the case): correct in effect, and it wrote the clamp on every cause, including an icon-size change
     * that was never about home's rows. Here the write belongs to the **deliberate** change that caused it, which is
     * this commit — the same asymmetry that governs this dock's own two axes.
     *
     * Home's displaced items are not moved here. The home surface re-fits itself against whatever it reads
     * (`HomeViewModel.fitMainTo`), so the items follow the count without this screen reaching into another surface's
     * placements — unlike [edit], where the *edge* is knowledge only this press has.
     *
     * @param edge where the dock sits, which decides whether the numbers below are rows or columns.
     * @param maxCells how many cells the new extent can hold along the axis it divides, from the screen — the fit needs
     *   a measured window and the current type scale, neither of which a state holder has.
     * @param homeMaxCells how many the space *left over* can hold, on the same axis, from the same screen and for the
     *   same reason.
     */
    fun setExtent(dp: Int, edge: DockEdge, maxCells: Int, homeMaxCells: Int) {
        val configuration = device.value ?: return
        val current = state.value.let { if (edge == DockEdge.BOTTOM) it.rows else it.cols }
        viewModelScope.launch {
            settingsRepository.setDockExtent(configuration, dp)
            if (current != null && current > maxCells) {
                settingsRepository.updateGrid(SLOT, configuration) {
                    if (edge == DockEdge.BOTTOM) copy(rows = maxCells) else copy(cols = maxCells)
                }
            }
            val home = settingsRepository.gridConfig(GridSlot.HOME_MAIN, configuration).first()
            val homeCurrent = if (edge == DockEdge.BOTTOM) home.visualRows else home.visualCols
            if (homeCurrent > homeMaxCells) {
                settingsRepository.updateGrid(GridSlot.HOME_MAIN, configuration) {
                    if (edge == DockEdge.BOTTOM) copy(rows = homeMaxCells) else copy(cols = homeMaxCells)
                }
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
     *
     * **[fromCols]/[fromRows] are the counts the dock is *drawing*, not [DockState]'s stored pair** — the same
     * parameters home's editor passes, and for the same reason. A stored count outlives its conditions (grow the icons
     * and fewer columns fit), the fit needs a measured width and the current type scale, and this screen already
     * computes it for the preview. Counting from it is what makes a press mean what the preview shows: − on a
     * four-column dock writes three, rather than writing eight because storage still remembers nine.
     */
    fun edit(edge: GridEditorEdge, add: Boolean, fromCols: Int, fromRows: Int) {
        val configuration = device.value ?: return

        val isRow = edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM
        val delta = if (add) 1 else -1
        val nextCols = if (isRow) fromCols else fromCols + delta
        val nextRows = if (isRow) fromRows + delta else fromRows

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
            settingsRepository.setDockExtent(configuration, null)
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
