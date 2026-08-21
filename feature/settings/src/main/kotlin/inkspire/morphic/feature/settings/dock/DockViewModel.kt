package inkspire.morphic.feature.settings.dock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.SideZoneEdge
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.core.model.sideZone
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the dock section shows — all three fields **resolved together or not at all**, since every one of them is keyed
 * by the device configuration the screen has yet to report. Null is "not yet" — the convention every device-keyed
 * state in this module follows, so nothing acts on a blueprint fallback the user never chose.
 *
 * @property extentDp the strip's thickness — its height as a bottom strip, its width as a rail. The dock's one genuine
 *   extent setting.
 * @property cols the column count the user wants. A wish rather than a promise — the surface clamps it to what fits
 *   and never writes the clamp back, so a count too large for today's icon size returns when the icons shrink.
 * @property rows likewise. Whichever of the two the extent divides is *also* written down when an extent commit
 *   invalidates it (see [setExtent]).
 * @property icon the zone's **resolved** icon sizing — both shown, since these controls live in this section, and
 *   used: the smallest usable cell it implies is what the extent divides into cells. **Null on the widget area**, and
 *   there it means something different from "not yet": a widget is not an icon in a cell, so that grid declares no
 *   icon sizing at all and the section draws no icon group. [layout] is what tells the two nulls apart.
 * @property homeIcon HOME's main-area icon sizing, or null when the main area is a list (which has no count for this
 *   extent to invalidate). Nothing here edits it and nothing here shows it; it is needed because this extent decides
 *   how much screen the main area is left with, and how many cells *that* carries depends on its own smallest usable
 *   cell. See [setExtent].
 * @property layout which pairing HOME is drawing, and therefore **which zone this section edits** — the dock, or the
 *   widget area. A real default rather than a null, because the screen must decide which controls to draw before any
 *   of them has resolved.
 */
data class DockState(
    val extentDp: Int? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val icon: IconSizing? = null,
    val homeIcon: IconSizing? = null,
    val paddingDp: Int? = null,
    val layout: HomeLayout = HomeLayout.PAGER_WITH_DOCK,
)

/**
 * Screen-level state holder for the dock section: its extent, the grid inside it, and its icons.
 *
 * **Three settings across two stores, which is why they are joined here.** The extent is the dock's own
 * (`setExtent`); the row and column counts are an ordinary grid override (`updateGrid`) like home's; the icon
 * sizing is an icon override, issued through [icons]. They belong on one screen because they constrain each other —
 * the icons set the smallest usable cell, and the extent divides into cells of at least that.
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

    /**
     * Which pairing HOME is drawing — the one input that changes *which zone* this section edits.
     *
     * Read rather than chosen, exactly as the home section reads it: the pairing is the surface register's setting,
     * and this section configures whichever side zone it brings. `Eagerly` so the writes below can read it without a
     * UI subscriber.
     */
    private val layout: StateFlow<HomeLayout> =
        settingsRepository.surfaceRegister
            .map { it.homeLayout }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.PAGER_WITH_DOCK)

    private val posture: StateFlow<Pair<DeviceConfiguration, HomeLayout>?> =
        combine(device, layout) { configuration, current -> configuration?.let { it to current } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DockState> = posture
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(DockState())
            } else {
                val (configuration, homeLayout) = current
                val slot = homeLayout.sideSlot
                combine(
                    settingsRepository.extent(slot, configuration),
                    settingsRepository.gridConfig(slot, configuration),
                    // Asked only of a grid that draws icon cells: the widget area declares none, and `iconSizing`
                    // rightly throws for it rather than inventing a value nobody configured.
                    if (slot.blueprint.icon == null) {
                        flowOf(null)
                    } else {
                        settingsRepository.iconSizing(slot, configuration)
                    },
                    // And of the main area only when it *has* counts for this extent to invalidate. A list scrolls,
                    // so taking space from it costs rows on screen and nothing in storage.
                    if (homeLayout.mainHasCounts) {
                        settingsRepository.iconSizing(GridSlot.HOME_MAIN, configuration)
                    } else {
                        flowOf(null)
                    },
                    settingsRepository.horizontalPadding(slot, configuration),
                ) { extentDp, grid, icon, homeIcon, padding ->
                    DockState(extentDp, grid.visualCols, grid.visualRows, icon, homeIcon, padding, homeLayout)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DockState())

    /**
     * The dock's own icon sizing, edited from this section rather than from a separate icons screen.
     *
     * Beside its grid because the two decide each other: the icon size sets the smallest usable cell, which is what
     * the dock's extent divides into cells.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        // The *current* side slot, read per write rather than captured — see the home section's, and note that on the
        // widget area nothing calls these at all, since that grid draws no icon controls.
        slot = { slot },
        device = { device.value },
    )

    /**
     * Sets the dock's horizontal margin, in dp.
     *
     * **One write, where [setExtent] is two.** A margin takes no cell away — it makes every cell narrower — so the
     * columns that no longer fit are re-reported on read rather than removed, and nothing is displaced for
     * `GridReflow` to re-home. That is the same distinction the repository's own KDoc draws between this and a resize.
     */
    fun setPadding(dp: Int?) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setHorizontalPadding(slot, configuration, dp) }
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
     * [maxCells] — the last line is given up so the survivors keep their size.
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
     * **The same reduction is applied to HOME's main grid, for the same reason one step out.** Driving it from the
     * *home surface* instead — a `LaunchedEffect` on its measured pager bounds — is correct in effect and writes the
     * clamp on every cause, including an icon-size change
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
    fun setExtent(dp: Int, edge: SideZoneEdge, maxCells: Int, homeMaxCells: Int) {
        val configuration = device.value ?: return
        val homeLayout = layout.value
        val zoneSlot = homeLayout.sideSlot
        val current = state.value.let { if (edge.isStrip) it.rows else it.cols }
        viewModelScope.launch {
            settingsRepository.setExtent(zoneSlot, configuration, dp)
            if (current != null && current > maxCells) {
                settingsRepository.updateGrid(zoneSlot, configuration) {
                    if (edge.isStrip) copy(rows = maxCells) else copy(cols = maxCells)
                }
            }
            // The main area's half applies only when it has a count to invalidate. A vertical list scrolls, so a
            // taller widget area simply shows fewer rows — there is nothing stored for this commit to reduce.
            if (!homeLayout.mainHasCounts) return@launch
            val home = settingsRepository.gridConfig(GridSlot.HOME_MAIN, configuration).first()
            val homeCurrent = if (edge.isStrip) home.visualRows else home.visualCols
            if (homeCurrent > homeMaxCells) {
                settingsRepository.updateGrid(GridSlot.HOME_MAIN, configuration) {
                    if (edge.isStrip) copy(rows = homeMaxCells) else copy(cols = homeMaxCells)
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
        val homeLayout = layout.value
        val zoneSlot = homeLayout.sideSlot

        val isRow = edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM
        val delta = if (add) 1 else -1
        val nextCols = if (isRow) fromCols else fromCols + delta
        val nextRows = if (isRow) fromRows + delta else fromRows

        viewModelScope.launch {
            val zoneConfig = zoneSlot.blueprint.toGridConfig(
                zoneSlot.blueprint.defaults.getValue(configuration).copy(cols = nextCols, rows = nextRows),
            )
            val placed = layoutRepository.placements(ORIENTATION).first()
            // **Only the dock has a placement half today, and that is a gap rather than a rule.** `settleDock` evicts
            // to HOME's main area, which exists to be evicted onto only when it is a coordinate grid; the widget area
            // sits beside a *list*, which has nowhere to put a widget. It is also moot until widgets exist — nothing
            // can be in the zone to displace — so the count is written and the re-homing is owed with the widgets.
            // Deliberately not deleting what would not fit.
            val moves = if (homeLayout.sideZone != HomeZone.DOCK) {
                emptyList()
            } else settleDock(
                dock = placed.filterValues { it.zone == HomeZone.DOCK }.mapValues { it.value.placement },
                main = placed.filterValues { it.zone == HomeZone.MAIN }.mapValues { it.value.placement },
                dockConfig = zoneConfig,
                mainConfig = settingsRepository.gridConfig(GridSlot.HOME_MAIN, configuration).first(),
                edit = DockEdit(edge, add),
            )
            // Grow the grid before moving items into it, and move them out before shrinking it, so no observer ever
            // sees a grid too small for its contents.
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
        settingsRepository.updateGrid(slot, configuration) { copy(cols = cols, rows = rows) }
    }

    /** The zone this section is editing right now — the dock's grid or the widget area's, as HOME's pairing says. */
    private val slot: GridSlot get() = layout.value.sideSlot

    /**
     * Clears both overrides, returning the dock to its blueprint.
     *
     * Two writes because they are two stores, but each is a plain write of "nothing" — and both entries are then
     * *removed* rather than stored empty, so a reset leaves storage exactly as a fresh install has it.
     */
    fun resetGrid() {
        val configuration = device.value ?: return
        viewModelScope.launch {
            settingsRepository.updateGrid(layout.value.sideSlot, configuration) { GridOverride() }
        }
    }

    /**
     * Clears the extent override, returning the zone to its blueprint thickness.
     *
     * **Separate from [resetGrid], though one button used to do both.** The extent and the counts are two fields with a
     * control each — the slider and the editor — and each now clears its own, which is what lets either arrow say
     * whether *that* value has moved. Clearing the extent deliberately does not touch the counts: a zone back at its
     * default thickness still divides into however many cells the user asked for, and reducing them is only warranted
     * when they no longer fit (which is [setExtent]'s job, on the extent the user actually chose).
     */
    fun clearExtent() {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setExtent(layout.value.sideSlot, configuration, null) }
    }

    private companion object {
        /** Portrait only, matching the home surface itself until it gains orientation support. */
        val ORIENTATION = Orientation.PORTRAIT
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Whether this pairing's main area has stored **counts** — true for the pager, false for the list.
 *
 * The one thing the side zone's section needs to know about the *other* zone: growing an extent takes space from the
 * main area, and only a main area sized by counts can have one invalidated by that. A list re-flows instead, which is
 * a rendering consequence and not a stored one.
 */
private val HomeLayout.mainHasCounts: Boolean
    get() = this == HomeLayout.PAGER_WITH_DOCK
