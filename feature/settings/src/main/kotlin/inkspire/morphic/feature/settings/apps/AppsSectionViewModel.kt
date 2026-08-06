package inkspire.morphic.feature.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.settings.AppsChrome
import inkspire.morphic.data.settings.GridOverride
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.settings.icons.IconSizingEdits
import inkspire.morphic.feature.settings.icons.SamplePreviewApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The grid this layout draws.
 *
 * **A layout is what the user recognises; a [GridSlot] is what the store is keyed by.** The chips name layouts because
 * that is the choice a user has already made (per home edge, in the surface register), and every control below then
 * addresses that layout's grid. L1 did the same and made the same distinction: its drawer detail edited
 * `drawer.profile(layout)`, the profile of whichever layout was selected.
 *
 * **Total, and a `when` rather than a map so that it cannot be otherwise.** This began as a map that was *deliberately*
 * partial — [AppsLayout.CATEGORY_CARD] was left out and [ConfigurableLayouts] was its key set, so one structure carried
 * two facts. It held while the only caller was this section's chip row, and threw the moment the surface register
 * asked, because a card layout is a perfectly ordinary thing to bind to an edge even though this section cannot size
 * it. Every layout draws *some* grid; which of them has knobs is the other question, and it now has its own list.
 */
internal val AppsLayout.slot: GridSlot
    get() = when (this) {
        AppsLayout.VERTICAL_LIST -> GridSlot.APPS_LIST
        AppsLayout.VERTICAL_GRID -> GridSlot.APPS_SCROLL
        AppsLayout.PAGER -> GridSlot.APPS_PAGER
        AppsLayout.PAGER_WITH_CATEGORY -> GridSlot.APPS_CATEGORY
        AppsLayout.CATEGORY_CARD -> GridSlot.APPS_CARD
    }

/**
 * The layouts this section can configure, in the order their chips appear.
 *
 * [AppsLayout.CATEGORY_CARD] is deliberately absent, and it is the one gap in this section. Its lane count *is* the
 * user's in principle — `AppsCardGrid` declares an `editRange` — but a card is a **tile**, so how narrow one may get
 * is not an icon guardrail (its blueprint declares no icon sizing at all) and nothing else answers it yet. Offering a
 * ceiling picked by hand is what the rest of this port has avoided; L1 gave its library layout no grid knobs either.
 *
 * Stated as its own list rather than read off [slot]'s cases: "which grid does this draw" and "which can I edit" are
 * different questions, and deriving one from the other is what made asking the first one crash.
 */
internal val ConfigurableLayouts: List<AppsLayout> = listOf(
    AppsLayout.VERTICAL_LIST,
    AppsLayout.VERTICAL_GRID,
    AppsLayout.PAGER,
    AppsLayout.PAGER_WITH_CATEGORY,
)

/**
 * What the APPS section shows for the layout currently selected.
 *
 * @property layout which layout's grid is being edited — a *view* choice, not a stored one. Which layout a user
 *   actually sees is a property of the home edge they swiped from, and lives in the surface register.
 * @property size the selected grid's **stored** size. `rows` is null for a scrolling grid, which is [GridDefault]'s own
 *   meaning for it rather than a convention invented here — and is also what tells the screen which sizes it must clamp
 *   before showing them (a scrolling grid's columns are fitted to the measured width; the pager's capacity is not).
 * @property icon its resolved icon sizing.
 * @property rowHeightDp the vertical list's row height. Present whatever the selection, because it is read in the
 *   list's own arm only — carrying it always is cheaper than a second flow that appears and disappears.
 *
 * Every field but [layout] is null until the screen reports its device, since all of them are resolved per device
 * configuration. Null is "not yet", exactly as in the dock's and home's sections.
 */
data class AppsSectionState(
    val layout: AppsLayout = ConfigurableLayouts.first(),
    val size: GridDefault? = null,
    val icon: IconSizing? = null,
    val rowHeightDp: Int? = null,
    val paddingDp: Int? = null,
    val chrome: AppsChrome = AppsChrome.Default,
)

/**
 * Screen-level state holder for the **APPS section**: the grid of whichever layout is selected, its icons, and — for
 * the vertical list — its row height.
 *
 * **One section for five layouts, mirroring one `feature:apps` for five layouts.** The surface itself is a single
 * module precisely because the layouts differ only in arrangement; the same argument makes them one settings section
 * with a chip row rather than five sections. L1 had this split the other way and paid for it: its drawer and library
 * were separate modules *and* separate details, so "which one am I configuring?" had to be answered before "how?".
 *
 * **Resizing here is one write, unlike home's two** — and that difference is the ordered/coordinate split showing up
 * in settings. Home's grid editor must also move the items its edit displaces, because only the button press knows
 * which edge changed and a removed left column leaves apps somewhere a removed right column does not. Every APPS grid
 * is *ordered* (or derived): the flow re-densifies into whatever the new count is, and the pager's store re-paginates
 * itself when its capacity changes — a sync it already runs for installs. So there is nothing here to displace, which
 * is also why [edit] reads only the axis of the edge it is given and not its side, exactly as L1's drawer editor did.
 */
class AppsSectionViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
) : ViewModel() {

    /** The app the icon preview draws, and the dice that changes it. Shared by every section that has a preview. */
    internal val sample = SamplePreviewApp(appRepository, viewModelScope)

    private val layout = MutableStateFlow(ConfigurableLayouts.first())
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppsSectionState> =
        combine(layout, device) { current, configuration -> current to configuration }
            .flatMapLatest { (current, configuration) ->
                if (configuration == null) {
                    flowOf(AppsSectionState(current))
                } else {
                    combine(
                        sizeOf(current.slot, configuration),
                        settingsRepository.iconSizing(current.slot, configuration),
                        settingsRepository.listRowHeight(configuration),
                        settingsRepository.horizontalPadding(current.slot, configuration),
                        settingsRepository.appsChrome,
                    ) { size, icon, rowHeight, padding, chrome ->
                        AppsSectionState(current, size, icon, rowHeight, padding, chrome)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsSectionState())

    /**
     * The selected grid's stored size, as the one shape both kinds of grid can answer in.
     *
     * The repository splits its reads by what a grid *has* — a `GridConfig` needs rows, so a scrolling grid gets a
     * column count instead — and this is where the two rejoin, in the type `core:model` already uses to mean "a size,
     * with rows when there are rows".
     */
    private fun sizeOf(slot: GridSlot, configuration: DeviceConfiguration): Flow<GridDefault> =
        if (slot.blueprint.editsRows) {
            settingsRepository.gridConfig(slot, configuration).map { GridDefault(it.visualCols, it.visualRows) }
        } else {
            settingsRepository.gridCols(slot, configuration).map { GridDefault(cols = it) }
        }

    /**
     * The selected layout's icon sizing, edited from this section rather than from the icons screen.
     *
     * A surface's icons belong beside its grid — L1's structure, and the dependency runs that way here too: the icon
     * size is what this screen's column and row limits are computed from, and for the list it is what the row height
     * range is computed from.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { layout.value.slot },
        device = { device.value },
    )

    /** Reports the device configuration being edited — the UI's one job, as on every other surface. */
    fun setDevice(configuration: DeviceConfiguration) {
        device.value = configuration
    }

    /** Switches which layout's grid is being edited. A view choice; nothing is written. */
    fun selectLayout(value: AppsLayout) {
        layout.value = value
    }

    /**
     * Adds or removes one row or column at [edge] — **the count and nothing else**.
     *
     * Only the edge's *axis* is read. On a coordinate surface the side matters, because the items it displaces have to
     * be moved and only this press knows where they went; on an ordered one the flow simply re-densifies, so removing
     * the left column and removing the right leave the same list. The editor still draws a pair on each edge, which
     * stays honest: it says which side of the grid is changing, and here both sides mean the same thing.
     *
     * The count is floored by the store against the blueprint's `editRange` and capped by the caller (the editor
     * disables a button at the limit), so what arrives is already legal.
     *
     * **[from] is the size the surface is *drawing*, which is not always [AppsSectionState.size].** A stored column
     * count outlives the icon settings it was chosen under, so a scrolling grid draws it clamped to what its measured
     * width can hold (`CellFit.fitCols`) — and the screen, which has the measurement, is the only thing that can say
     * so. Counting from the drawn size is what makes − and + move the number the editor is showing rather than one
     * only storage remembers. Passed as a parameter for the reason `DockViewModel.setHeight` takes its row cap that
     * way: a bound that needs a measured area cannot come from a state holder.
     */
    fun edit(edge: GridEditorEdge, add: Boolean, from: GridDefault) {
        val configuration = device.value ?: return
        val delta = if (add) 1 else -1
        val resize: GridOverride.() -> GridOverride = if (edge == GridEditorEdge.TOP || edge == GridEditorEdge.BOTTOM) {
            // A scrolling grid has no row axis; its editor draws no row buttons, so reaching here means a caller went
            // wrong rather than a user did — and dropping the write is the honest response to an axis that isn't there.
            val rows = from.rows ?: return
            { copy(rows = rows + delta) }
        } else {
            { copy(cols = from.cols + delta) }
        }
        viewModelScope.launch { settingsRepository.updateGrid(layout.value.slot, configuration, resize) }
    }

    /**
     * Commits the vertical list's row height, in whole dp.
     *
     * Not part of [edit] because it is not a count: a list has one lane and no rows to add, so its cell height is the
     * dimension itself. Nothing is displaced by the write — rows flow, so a taller one shows fewer apps per screen and
     * leaves none without a place, which is the difference from the dock's height and the reason this needs no
     * settle step.
     */
    fun setRowHeight(dp: Int) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setListRowHeight(configuration, dp) }
    }

    /**
     * Sets where the search field sits.
     *
     * **Not per layout, unlike everything else in this section** — the chrome slice is one value for the surface. Which
     * *options* are offered does depend on the layout (a standalone layout pins to an edge; the category pager embeds
     * in its header), but that is `SearchPlacement`'s shape rather than five separate settings, and a user who wants
     * search at the bottom means it everywhere.
     */
    fun setSearch(placement: SearchPlacement) {
        viewModelScope.launch { settingsRepository.setSearchPlacement(placement) }
    }

    /** Sets which edge the category pager's tab bar sits on. */
    fun setTabBarEdge(edge: VerticalEdge) {
        viewModelScope.launch { settingsRepository.setTabBarEdge(edge) }
    }

    /**
     * Sets the **selected layout's** horizontal margin, in dp.
     *
     * Per layout rather than per surface, like everything else in this section: the chip decides the slot, and the
     * five arrangements are configured independently because they are five different shapes on one surface. One write
     * always — even the pager, whose *resize* is one write here where home's is two, needs no companion, since a
     * margin displaces nothing.
     */
    fun setPadding(dp: Int) {
        val configuration = device.value ?: return
        val slot = layout.value.slot
        viewModelScope.launch { settingsRepository.setHorizontalPadding(slot, configuration, dp) }
    }

    /**
     * Clears the selected layout's size override — and, for the list, its row height too.
     *
     * Both are "the size of this layout" from the user's side, so one button clears both rather than making them
     * discover that a list's size is stored somewhere else than a grid's.
     */
    fun resetSize() {
        val configuration = device.value ?: return
        val slot = layout.value.slot
        viewModelScope.launch {
            if (slot.blueprint.editRange != null) settingsRepository.updateGrid(slot, configuration) { GridOverride() }
            if (slot == GridSlot.APPS_LIST) settingsRepository.setListRowHeight(configuration, null)
            // The margin is part of "the size of this layout" from the user's side, so Reset clears it with the rest
            // rather than leaving one number behind that no button appears to own.
            settingsRepository.setHorizontalPadding(slot, configuration, null)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
