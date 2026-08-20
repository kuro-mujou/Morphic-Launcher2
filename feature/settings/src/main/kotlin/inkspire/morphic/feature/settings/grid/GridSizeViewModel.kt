package inkspire.morphic.feature.settings.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.mainSlot
import inkspire.morphic.core.model.pagerSlot
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.layout.GridReflow
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.settings.GridOverride
import inkspire.morphic.data.apps.AppRepository
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
 * **How HOME's main area is sized** — a sum type, because the two layouts configure a different quantity rather than
 * the same quantity differently.
 *
 * A pager divides the space it is given, so its setting is a pair of counts; a list is one lane and has nothing to
 * divide, so its setting is how tall one row is. Neither could supply the other's value, which is what a sealed type
 * says and what a record of four nullable fields would not. It mirrors `feature:home`'s own `HomeMainSizing`, and the
 * two are separate because each carries what *its* layer needs — this one the stored counts, that one a resolved
 * `GridConfig`.
 */
sealed interface MainAreaSize {

    /** The pager's: a stored visual column and row count. */
    data class Grid(val cols: Int, val rows: Int) : MainAreaSize

    /** The list's: how tall one row is, in dp. */
    data class Rows(val heightDp: Int) : MainAreaSize
}

/**
 * What the home section shows — resolved together or not at all, since each is keyed by the device configuration the
 * screen has yet to report.
 *
 * @property layout which pairing HOME is drawing, and therefore **which grid this section edits**: the pager, or the
 *   vertical list. It has a real default rather than being nullable because the screen must decide which controls to
 *   draw before any of them has resolved.
 * @property main the main area's stored size — a **wish rather than a promise** on either layout. A grid count that
 *   fits today's icons is smaller whenever the icons have grown since, and a row height outside today's guardrails is
 *   clamped where it is drawn; the screen applies the same clamps the surface does, and nothing writes them back, so
 *   shrinking the icons again brings the stored value straight back.
 * @property icon the resolved icon sizing, which is what decides how many cells fit and how short a row may be.
 * @property sideExtentDp how thick the side zone is (its height, or its width where it is a rail), so the preview can
 *   show the share of the screen the main area actually gets — and so the bounds are computed against that area
 *   rather than against the whole window.
 * @property wraps whether the pager's pages loop, or **null on the pairing that has no pager**. Nullable rather than
 *   false-by-default so the screen draws the control from the state alone instead of re-deciding from [layout] which
 *   pairing has one — the same "absent means the question does not apply" the blueprint uses one layer down.
 */
data class GridSizeState(
    val layout: HomeLayout = HomeLayout.PAGER_WITH_DOCK,
    val main: MainAreaSize? = null,
    val icon: IconSizing? = null,
    val sideExtentDp: Int? = null,
    val paddingDp: Int? = null,
    val wraps: Boolean? = null,
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
    appRepository: AppRepository,
) : ViewModel() {

    /** The app the icon preview draws, and the dice that changes it. Shared by every section that has a preview. */
    internal val sample = SamplePreviewApp(appRepository, viewModelScope)

    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    /**
     * Which pairing HOME is drawing — the one input that changes *which grid* this section edits.
     *
     * Read rather than chosen: the pairing is the surface register's setting, and this section configures whichever
     * main area it leaves. `Eagerly` so the writes below can read it without a UI subscriber.
     */
    private val layout: StateFlow<HomeLayout> =
        settingsRepository.surfaceRegister
            .map { it.homeLayout }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.PAGER_WITH_DOCK)

    /** Device × layout — see `HomeViewModel`'s own posture, which combines the same pair for the same reason. */
    private val posture: StateFlow<Pair<DeviceConfiguration, HomeLayout>?> =
        combine(device, layout) { configuration, current -> configuration?.let { it to current } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GridSizeState> = posture
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(GridSizeState())
            } else {
                val (configuration, homeLayout) = current
                val slot = homeLayout.mainSlot
                combine(
                    mainSize(slot, configuration),
                    settingsRepository.iconSizing(slot, configuration),
                    settingsRepository.extent(homeLayout.sideSlot, configuration),
                    settingsRepository.horizontalPadding(slot, configuration),
                    settingsRepository.pagerWraps,
                ) { main, icon, sideExtentDp, padding, wraps ->
                    // The only field here that is *not* keyed by the device: wrapping is a behavior, so it is read
                    // straight off the resolved map. `pagerSlot` is what turns "this pairing has no pager" into the
                    // null the screen reads as "draw no control".
                    GridSizeState(homeLayout, main, icon, sideExtentDp, padding, homeLayout.pagerSlot?.let { wraps[it] })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GridSizeState())

    /**
     * The stored size of [slot], in whichever shape that grid has one.
     *
     * The choice is made from the **blueprint**, not from the layout, which is what keeps the two in step: a grid that
     * declares a row height is a one-lane list and has no counts to read, and `gridConfig` would rightly refuse it —
     * a scrolling grid has no row count to resolve.
     */
    private fun mainSize(slot: GridSlot, configuration: DeviceConfiguration) =
        slot.blueprint.rowHeightDp
            ?.let { settingsRepository.rowHeight(slot, configuration).map(MainAreaSize::Rows) }
            ?: settingsRepository.gridConfig(slot, configuration)
                .map { MainAreaSize.Grid(it.visualCols, it.visualRows) }

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
        // The *current* main slot, read per write rather than captured: switching HOME's pairing changes which grid
        // this section is editing, and an icon edit must land on the one on screen. That is what the lambda in
        // `IconSizingEdits`' signature was for — the APPS section's chip row was its first user, and this is its
        // second.
        slot = { layout.value.mainSlot },
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
     * state holder has (the same reason `DockViewModel.setExtent` is told its row cap). Counting from the drawn grid is
     * what makes the − and + buttons mean what they show: pressing − on a four-row home writes three, rather than
     * writing four because storage still remembers five. It also means the reflow moves items relative to the grid the
     * user can see, instead of to one nothing draws.
     *
     * The counterpart write is deliberately absent: an icon change that invalidates a count is *not* written down, so
     * the count returns when the icons shrink. Only a press writes — which is exactly the asymmetry the dock's rows and
     * columns already live by. L1 reconciled it the other way, from a `LaunchedEffect` in this screen that wrote the
     * clamped counts into storage, and so destroyed a row count for good the first time anyone touched an icon slider.
     */
    /**
     * Sets the main area's horizontal margin, in dp.
     *
     * One write, unlike [edit]: a margin removes no cell, so nothing is displaced and there is no placement half. The
     * columns it costs are re-reported on read and come back when it narrows.
     */
    fun setPadding(dp: Int) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setHorizontalPadding(slot, configuration, dp) }
    }

    /**
     * Sets the vertical list's row height, in dp.
     *
     * **The list's whole size setting**, and the counterpart of [edit] on the other layout: one lane means there is no
     * count to press, so the row is what there is to choose. One write, because rows flow — a taller row shows fewer
     * apps per screen rather than leaving any without a place, so nothing is displaced.
     *
     * The bounds belong to the screen, as the extent's do: what a row must be at least depends on the current icon
     * guardrails and the current type scale (`rowHeightRangeDp`), and a stored height outside them is clamped where it
     * is drawn rather than written down.
     */
    fun setRowHeight(dp: Int) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setRowHeight(GridSlot.HOME_LIST, configuration, dp) }
    }

    /**
     * Turns the main pager's page wrapping on or off.
     *
     * **The one write on this screen that is not keyed by device**, which is why it takes no `configuration`: whether
     * pages loop is a behavior, and turning the phone on its side is not a reason for it to change.
     *
     * Guarded by `pagerSlot` rather than by the layout, so a stale press on the list pairing writes nothing — the same
     * shape as [edit]'s guard, and for the same reason: the screen draws no control there, but a press that arrives
     * anyway must not reach a repository that would throw.
     */
    fun setWraps(value: Boolean) {
        val slot = layout.value.pagerSlot ?: return
        viewModelScope.launch { settingsRepository.setPagerWrap(slot, value) }
    }

    fun edit(edge: GridEditorEdge, add: Boolean, fromCols: Int, fromRows: Int) {
        val configuration = device.value ?: return
        // The list has no counts to edit, so this is only ever reached on the pager layout — the screen draws no
        // buttons on the other one. Guarded rather than assumed, because a stale press must not resize a grid the
        // user is no longer looking at.
        val blueprint = GridSlot.HOME_MAIN.blueprint
        if (layout.value.mainSlot != GridSlot.HOME_MAIN) return

        val isRow = edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM
        val delta = if (add) 1 else -1
        val nextCols = if (isRow) fromCols else fromCols + delta
        val nextRows = if (isRow) fromRows + delta else fromRows
        val nextConfig = blueprint.toGridConfig(
            blueprint.defaults.getValue(configuration).copy(cols = nextCols, rows = nextRows),
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

    /**
     * Clears the size override, returning the main area to its blueprint. Placements are then settled by the surface.
     *
     * Two shapes because the two layouts store different things: the pager's counts are a grid override, the list's
     * row height is its own setting. Both are a plain write of "nothing", after which the entry is *removed* rather
     * than stored empty — so a reset leaves storage exactly as a fresh install has it.
     */
    fun reset() {
        val configuration = device.value ?: return
        val slot = layout.value.mainSlot
        viewModelScope.launch {
            if (slot.blueprint.rowHeightDp != null) {
                settingsRepository.setRowHeight(slot, configuration, null)
            } else {
                settingsRepository.updateGrid(slot, configuration) { GridOverride() }
            }
        }
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
        settingsRepository.updateGrid(GridSlot.HOME_MAIN, configuration) { copy(cols = cols, rows = rows) }
    }

    /** The grid this section is editing right now — the pager's or the list's, as HOME's pairing says. */
    private val slot: GridSlot get() = layout.value.mainSlot

    private companion object {
        /** Portrait only, matching the home surface itself until it gains orientation support. */
        val ORIENTATION = Orientation.PORTRAIT
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
