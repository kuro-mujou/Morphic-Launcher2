package inkspire.morphic.feature.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.cell.CategoryPreviewSlots
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.pagerSlot
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.settings.AppsChrome
import inkspire.morphic.data.settings.GridOverride
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.feature.settings.icons.IconSizingEdits
import inkspire.morphic.feature.settings.icons.SamplePreviewApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The grid this layout draws.
 *
 * **A layout is what the user recognizes; a [GridSlot] is what the store is keyed by.** The chips name layouts because
 * that is the choice a user has already made (per home edge, in the surface register), and every control below then
 * addresses that layout's grid.
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
 * **All five**, which closes the gap this list used to describe. The category card was held back because a card is a
 * **tile**: how narrow one may get is not an icon guardrail, its blueprint declares no icon sizing at all, and the
 * note here said picking a ceiling by hand was what the rest of this port had avoided. What changed is that the
 * ceiling is now *derived* where every other grid's is (`CellFit.cardMinCell`, the guardrails inverted), rather
 * than being a number picked by eye — and that the card's lane count and margin were *already stored and read by
 * the surface*, so the gap was not a missing feature but a value the user could not reach.
 *
 * Stated as its own list rather than read off [slot]'s cases: "which grid does this draw" and "which can I edit" are
 * different questions, and deriving one from the other is what made asking the first one crash. Keeping it separate
 * still earns its place now that it is total — the day a layout gains a grid with no editor, only this list moves.
 */
internal val ConfigurableLayouts: List<AppsLayout> = listOf(
    AppsLayout.VERTICAL_LIST,
    AppsLayout.VERTICAL_GRID,
    AppsLayout.PAGER,
    AppsLayout.PAGER_WITH_CATEGORY,
    AppsLayout.CATEGORY_CARD,
)

/**
 * What the APPS section shows for the layout currently selected.
 *
 * @property layout which layout's grid is being edited — a *view* choice, not a stored one. Which layout a user
 *   actually sees is a property of the home edge they swiped from, and lives in the surface register.
 * @property size the selected grid's **stored** size. `rows` is null for a scrolling grid, which is [GridDefault]'s own
 *   meaning for it rather than a convention invented here — and is also what tells the screen which sizes it must clamp
 *   before showing them (a scrolling grid's columns are fitted to the measured width; the pager's capacity is not).
 * @property card the category card's resolved tile chrome, or null on every other layout — which is the same
 *   null-means-*not this grid* the blueprint uses, so a section draws the card group exactly when there is one.
 * @property icon its resolved icon sizing — or null on the **category card**, where that means something different
 *   from "not yet": a card is a tile, so that grid declares no icon sizing at all and the section draws no icon group.
 *   [layout] is what tells the two nulls apart, exactly as it does in the dock section's `DockState.icon`.
 * @property rowHeightDp the vertical list's row height. Present whatever the selection, because it is read in the
 *   list's own arm only — carrying it always is cheaper than a second flow that appears and disappears.
 *
 * Every field but [layout] is null until the screen reports its device, since all of them are resolved per device
 * configuration. Null is "not yet", exactly as in the dock's and home's sections.
 */
/**
 * The four sources that are **not keyed by the selected grid**, grouped so the section's outer `combine` stays within
 * its five flows — the same folding [AppsViewModel]'s `PerDevice` does, one screen over. `Triple` covered three of
 * them until page memory made a fourth.
 */
private data class SurfacePagingBits(
    val chrome: AppsChrome,
    val wraps: Map<GridSlot, Boolean>,
    val remembersPage: Map<GridSlot, Boolean>,
    val card: CardChrome?,
)

data class AppsSectionState(
    val layout: AppsLayout = ConfigurableLayouts.first(),
    val size: GridDefault? = null,
    val icon: IconSizing? = null,
    val rowHeightDp: Int? = null,
    val paddingDp: Int? = null,
    val chrome: AppsChrome = AppsChrome.Default,
    val wraps: Boolean? = null,
    val rememberPage: Boolean? = null,
    val card: CardChrome? = null,
    val boundLayouts: Set<AppsLayout> = emptySet(),
)

/**
 * Screen-level state holder for the **APPS section**: the grid of whichever layout is selected, its icons, and — for
 * the vertical list — its row height.
 *
 * **One section for five layouts, mirroring one `feature:apps` for five layouts.** The surface itself is a single
 * module precisely because the layouts differ only in arrangement; the same argument makes them one settings section
 * with a chip row rather than five sections. Split the other way — a module and a section per layout — "which one am
 * I configuring?" has to be answered before "how?".
 *
 * **Resizing here is one write, unlike home's two** — and that difference is the ordered/coordinate split showing up
 * in settings. Home's grid editor must also move the items its edit displaces, because only the button press knows
 * which edge changed and a removed left column leaves apps somewhere a removed right column does not. Every APPS grid
 * is *ordered* (or derived): the flow re-densifies into whatever the new count is, and the pager's store re-paginates
 * itself when its capacity changes — a sync it already runs for installs. So there is nothing here to displace, which
 * is also why [edit] reads only the axis of the edge it is given and not its side.
 */
class AppsSectionViewModel(
    private val settingsRepository: SettingsRepository,
    appRepository: AppRepository,
) : ViewModel() {

    /** The app the icon preview draws, and the dice that changes it. Shared by every section that has a preview. */
    internal val sample = SamplePreviewApp(appRepository, viewModelScope)

    /**
     * Enough apps to fill a category card's preview, from the same dice — held here rather than asked for per read,
     * since each call builds its own flow.
     *
     * The card is the one preview that draws a *collection*, so it needs a set rather than a single app. They are
     * installed apps standing in for a category's contents, deliberately not a real category's: previewing a real one
     * would draw whatever that phone happens to hold, and a category with two apps in it leaves half the slots empty —
     * which is exactly the state in which the spacing and padding sliders show nothing.
     *
     * **Enough for a cluster, not just for the slots**: a card shows `CategoryPreviewSlots - 1` icons plus an overflow
     * tile of up to `CategoryPreviewSlots` more, so the preview needs both to draw the layout a full category really
     * gets. Asking for four filled the slots and left nothing over, which drew the one arrangement — four apps, no
     * cluster — that a category large enough to need this screen never has.
     */
    internal val sampleApps = sample.apps(CategoryPreviewSlots * 2 - 1)

    private val layout = MutableStateFlow(ConfigurableLayouts.first())
    private val device = MutableStateFlow<DeviceConfiguration?>(null)

    /**
     * The layouts a home edge actually opens.
     *
     * **Read here so the chip row can tell "the one I am editing" from "the one I can reach"**, which are different
     * questions on this surface and on no other. [layout] is a view choice, and the register is free to bind a
     * different layout to every edge or none at all — so a highlighted chip on its own says nothing about whether the
     * arrangement under it is one the user can swipe to. Without this the section will happily spend a minute tuning
     * a grid that is not on the device.
     *
     * A set rather than the register: which *edge* opens what is the register's own screen to answer, and reaching
     * for it here would put a second, worse copy of that screen at the top of this one.
     */
    private val boundLayouts: Flow<Set<AppsLayout>> =
        settingsRepository.surfaceRegister
            .map { register ->
                register.sides.values.filterIsInstance<SideBinding.Apps>().mapTo(mutableSetOf()) { it.layout }
            }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppsSectionState> =
        combine(layout, device) { current, configuration -> current to configuration }
            .flatMapLatest { (current, configuration) ->
                if (configuration == null) {
                    flowOf(AppsSectionState(current))
                } else {
                    combine(
                        sizeOf(current.slot, configuration),
                        // Asked only of a grid that draws icon cells. The category card is a grid of *tiles*, so its
                        // blueprint declares no icon sizing and `iconSizing` rightly throws for it — the same branch
                        // the dock section takes for the widget area, and the same null that tells the screen to draw
                        // no icon group rather than "not yet".
                        if (current.slot.blueprint.icon == null) {
                            flowOf(null)
                        } else {
                            settingsRepository.iconSizing(current.slot, configuration)
                        },
                        settingsRepository.rowHeight(GridSlot.APPS_LIST, configuration),
                        settingsRepository.horizontalPadding(current.slot, configuration),
                        // Eight sources against `combine`'s five, so the four that are **not keyed by the selected
                        // grid** are grouped: the chrome slice is one value for the whole surface, wrapping and page
                        // memory are behaviors, and the card's tile chrome belongs to one fixed slot. The four above
                        // all follow whichever layout's chip is selected.
                        combine(
                            settingsRepository.appsChrome,
                            settingsRepository.pagerWraps,
                            settingsRepository.pagerRemembersPage,
                            // Asked only of the grid that draws tiles; `cardChrome` rightly throws for the rest, and
                            // the null is what tells the screen to draw no card group — `icon`'s convention, one
                            // group over.
                            if (current.slot.blueprint.card == null) {
                                flowOf(null)
                            } else {
                                settingsRepository.cardChrome(current.slot, configuration)
                            },
                            ::SurfacePagingBits,
                        ),
                    ) { size, icon, rowHeight, padding, bits ->
                        // Null on the three layouts that do not page, which is what tells the screen to draw no
                        // control — and what makes the toggle follow the chip: selecting the category pager selects
                        // *its* grid's setting, not the plain pager's. Page memory follows the same slot; only the two
                        // pagers carry it, so home never appears in either map.
                        val pagerWraps = current.pagerSlot?.let { bits.wraps[it] }
                        val pagerRemembers = current.pagerSlot?.let { bits.remembersPage[it] }
                        AppsSectionState(
                            current, size, icon, rowHeight, padding, bits.chrome, pagerWraps, pagerRemembers, bits.card,
                        )
                    }
                }
            }
            // Joined outside the inner combine rather than squeezed into its grouped triple: this is the one source
            // keyed by neither the selected layout nor the device, so nesting it there would re-subscribe it on every
            // chip press and every rotation.
            .combine(boundLayouts) { base, bound -> base.copy(boundLayouts = bound) }
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
     * A surface's icons belong beside its grid, and the dependency runs that way: the icon
     * size is what this screen's column and row limits are computed from, and for the list it is what the row height
     * range is computed from.
     */
    internal val icons = IconSizingEdits(
        settings = settingsRepository,
        scope = viewModelScope,
        slot = { layout.value.slot },
        device = { device.value },
    )

    /**
     * The card's chrome writes, beside [icons] — two groups of settings on one screen, each owning its own commits.
     */
    internal val cardChrome = CardChromeEdits(
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
     * only storage remembers. Passed as a parameter for the reason `DockViewModel.setExtent` takes its row cap that
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
    fun setRowHeight(dp: Int?) {
        val configuration = device.value ?: return
        viewModelScope.launch { settingsRepository.setRowHeight(GridSlot.APPS_LIST, configuration, dp) }
    }

    /**
     * Sets where the search field sits.
     *
     * **Per layout, like the margin and the wrap flag.** Each arrangement draws its own chrome, so a field pinned to
     * the bottom of the list says nothing about where it belongs on the category cards. It is also what keeps the
     * control honest: the options a layout offers depend on it (a standalone layout pins to an edge, the category
     * pager embeds in its header), so one shared value meant the stored placement was routinely absent from the list
     * the user was being shown, and the control had nothing to mark.
     *
     * Writes against the **selected** layout, as `setWraps` does — the chip row above chooses what is being edited.
     */
    fun setSearch(placement: SearchPlacement) {
        val target = layout.value
        viewModelScope.launch { settingsRepository.setSearchPlacement(target, placement) }
    }

    /**
     * Sets which edge the category pager's tab bar sits on.
     *
     * **Takes no layout, unlike [setSearch]**, because exactly one arrangement draws tabs and the setting is named for
     * it. A layout parameter here would accept four values that nothing could ever read back.
     */
    fun setCategoryTabEdge(edge: VerticalEdge) {
        viewModelScope.launch { settingsRepository.setCategoryTabEdge(edge) }
    }

    /**
     * Turns the **selected** pager's page wrapping on or off.
     *
     * Per layout, like the margin and unlike the search placement. One global flag would make the two pagers on this
     * surface one question, and they are not: pages of loose apps, and pages that *are* categories.
     *
     * No device, because wrapping is a behavior rather than a size; and guarded by `pagerSlot`, so a stale press
     * while a non-paging chip is selected writes nothing rather than reaching a repository that would throw.
     */
    fun setWraps(value: Boolean) {
        val slot = layout.value.pagerSlot ?: return
        viewModelScope.launch { settingsRepository.setPagerWrap(slot, value) }
    }

    /**
     * Turns the **selected** pager's page memory on or off.
     *
     * [setWraps]'s twin, guarded by the same `pagerSlot` — a stale press while a non-paging chip is selected writes
     * nothing rather than reaching a repository that would throw. Only the two APPS pagers offer this, so the guard is
     * exactly the set the store accepts.
     */
    fun setRememberPage(value: Boolean) {
        val slot = layout.value.pagerSlot ?: return
        viewModelScope.launch { settingsRepository.setPagerRemembersPage(slot, value) }
    }

    /**
     * Sets the **selected layout's** horizontal margin, in dp.
     *
     * Per layout rather than per surface, like everything else in this section: the chip decides the slot, and the
     * five arrangements are configured independently because they are five different shapes on one surface. One write
     * always — even the pager, whose *resize* is one write here where home's is two, needs no companion, since a
     * margin displaces nothing.
     */
    fun setPadding(dp: Int?) {
        val configuration = device.value ?: return
        val slot = layout.value.slot
        viewModelScope.launch { settingsRepository.setHorizontalPadding(slot, configuration, dp) }
    }

    /**
     * Clears the selected layout's **size** override, returning its lane count to the blueprint's default.
     *
     * The editor's own reset, and only the counts. It used to clear the row height and the margin with them, on the
     * grounds that all three are "the size of this layout" and one button should not leave a number behind that nothing
     * appears to own — true while the only reset was a text button under the section, and now false: each of those has
     * its own arrow beside its own value, which is a better answer than one button clearing three things at once.
     */
    fun resetGrid() {
        val configuration = device.value ?: return
        val slot = layout.value.slot
        if (slot.blueprint.editRange == null) return
        viewModelScope.launch { settingsRepository.updateGrid(slot, configuration) { GridOverride() } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
