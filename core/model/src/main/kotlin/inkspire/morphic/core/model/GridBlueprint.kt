package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/** An edge of a grid that a row or column can be added to or removed from while editing. */
enum class GridEditorEdge { TOP, BOTTOM, LEFT, RIGHT }

/**
 * How a grid is bounded along the row axis.
 *
 * - [FIXED_PAGER]: a fixed rows × cols page; both axes are bounded by what fits the screen (home, dock,
 *   drawer paged, folders).
 * - [SCROLL_GRID]: columns are bounded by what fits; rows flow and scroll freely (drawer Classic / Grouped).
 */
enum class GridSizing { FIXED_PAGER, SCROLL_GRID }

/**
 * A default grid size for one device configuration, in visual units.
 *
 * @property cols Default column count.
 * @property rows Default row count, or null for column-only grids where rows are derived at runtime
 *   (fitted per page, or unbounded scroll).
 */
data class GridDefault(val cols: Int, val rows: Int? = null)

/**
 * The user-editable range of a grid.
 *
 * @property minCols Smallest allowed column count.
 * @property minRows Smallest allowed row count, or null when only columns are editable. (Maxima are not
 *   stored — they depend on screen area and icon size and are computed at runtime.)
 */
data class GridEditRange(val minCols: Int, val minRows: Int?)

/**
 * The chrome of a grid of **tiles** — everything about an APPS category card that is not its icons or its lane count.
 *
 * A card is the one thing on the launcher that is neither a cell nor a surface: it is a titled tile whose *contents*
 * are a small grid. So the numbers that shape it have no home among the grid metrics — a cell has no corner radius, a
 * surface has no title — and collecting them here is what stops four unrelated scalars being four more maps in
 * `SurfaceMetrics`, each meaningful for exactly one slot.
 *
 * **The three dp values default to zero, deliberately.** A card starts as a plain rectangle with its icons packed
 * edge to edge, and every bit of decoration is something the user adds. That is the opposite of the usual "ship a
 * tasteful default" instinct, and it is the better one here: the previous shape carried a hand-picked 12dp inset, an
 * 8dp gap and a 24dp corner that nothing owned and no control could reach, so the only way to discover they were
 * wrong was to look at a screenshot. Starting from nothing makes each of them a decision someone made on purpose.
 *
 * @property titleScale multiplier on the theme's title style, matching [IconSizing.labelScale]'s shape and range so
 *   the two text controls read the same way. A multiplier rather than an sp value so a user who has scaled text
 *   system-wide keeps that scaling.
 * @property cornerRadiusDp the card's rounded corner. Zero is a square card.
 * @property outerPaddingDp the inset between the card's edge and its **icon area**, on all four sides. Zero puts the
 *   icons flush against the card. It does *not* inset the title, which keeps a fixed inset of its own — a title
 *   touching the edge of a card reads as a rendering fault rather than as a choice, and there is no reason to let one
 *   number express both.
 * @property innerPaddingDp the gap **between** icon slots. Zero packs them into a solid block.
 */
data class CardChrome(
    val titleScale: Float = 1f,
    val cornerRadiusDp: Int = 0,
    val outerPaddingDp: Int = 0,
    val innerPaddingDp: Int = 0,
)

/** The bounds each [CardChrome] control offers, the counterpart of [IconSizingRanges]. */
object CardChromeRanges {

    /** Shares [IconSizingRanges.LabelScale]'s window, since it is the same kind of control over the same kind of text. */
    val TitleScale: ClosedFloatingPointRange<Float> = 0.7f..1.5f

    /**
     * Up to half of the smallest card a phone can draw, past which "rounded" becomes "pill" and then a circle that
     * clips its own contents.
     */
    val CornerRadiusDp: IntRange = 0..48

    /**
     * Both paddings share one window, and it is small on purpose: these inset a tile whose whole job is to show four
     * recognizable icons, so a padding competing with the icons for room is the failure mode. The icon guardrails,
     * not this, are what a user reaches for to make icons bigger.
     */
    val PaddingDp: IntRange = 0..24
}

/**
 * Which grid a blueprint, a stored override, or a resolved metric belongs to — **the launcher's grids, named**.
 *
 * There is exactly one value per [GridBlueprint], and that is the point: "surface × layout" is not a pair to be
 * keyed on, it is a named grid. The home pager and the dock are different grids on the same surface; the APPS pager
 * and the APPS scrolling grid are different grids for different layouts of one surface. Naming them makes both cases
 * one axis instead of two, and stops an illegal pair (HOME crossed with an `AppsLayout`) from being expressible.
 *
 * It lives on the blueprint rather than beside it so the two cannot drift: adding a value here does not compile
 * until a blueprint claims it, and `GridBlueprints` proves the mapping is total.
 *
 * These names are **persisted** — they key the per-grid overrides in `data:settings` — so renaming a value is a
 * storage migration, not a refactor.
 */
enum class GridSlot {
    /** HOME's paged main area, in [HomeLayout.PAGER_WITH_DOCK]. */
    HOME_MAIN,

    /** HOME's dock strip — the side zone of [HomeLayout.PAGER_WITH_DOCK]. */
    HOME_DOCK,

    /**
     * HOME's vertical **list**, the main area of [HomeLayout.LIST_WITH_WIDGET_AREA].
     *
     * A separate slot from [APPS_LIST] even though both are one-lane lists of apps, because they are two grids on two
     * surfaces: a user who wants chunky rows on their home screen and a dense A–Z drawer must be able to say so, and
     * "each grid gets an independent icon config" is the rule that makes that automatic. It is the same reason
     * [HOME_MAIN] and [APPS_PAGER] are not one slot despite both being paged grids of icons.
     */
    HOME_LIST,

    /**
     * HOME's **widget area** — the side zone of [HomeLayout.LIST_WITH_WIDGET_AREA], and the one grid that holds
     * widgets rather than icons.
     */
    HOME_WIDGET_AREA,

    /** The APPS paged grid, and the pages of the category pager. */
    APPS_PAGER,

    /** The APPS vertically-scrolling grid of every app. */
    APPS_SCROLL,

    /**
     * The APPS vertical *list* of every app.
     *
     * A list is a **one-lane scrolling grid**, which is why it belongs in this enum rather than beside it: it draws
     * icon cells and therefore needs its own icon sizing, and "each grid config gets an independent icon config" is
     * the rule that makes that automatic.
     */
    APPS_LIST,

    /** One category's page on the APPS category pager. */
    APPS_CATEGORY,

    /** The APPS grid of category **cards** — a grid of tiles, not of icons. */
    APPS_CARD,

    /** An opened folder, and an expanded category card — one grid, one overlay. */
    FOLDER,
}

/**
 * How icons are sized in a grid's cells, in **persistable primitives**.
 *
 * The model-layer twin of `core:designsystem`'s `IconMetrics`, which holds the same six facts as Compose types
 * (`Dp`) and cannot live here — `core:model` is a plain JVM library. `IconMetrics.of(...)` already exists to bridge
 * the two, and its KDoc already said it was "for the settings/layout layer": this is that layer arriving.
 *
 * **It belongs on a grid blueprint rather than beside it**, because icon size is not independent of the grid:
 * [iconPercent] is a fraction *of the cell*, so the same number means a different icon on a 4-column grid than on
 * an 8-column one. A grid and what it draws in a cell are one description.
 *
 * **These defaults are the launcher's, and every blueprint takes them.** No grid states its own any more: an icon fills
 * its cell (`iconPercent = 1f`) up to a 48dp cap, and never draws below 24dp. Earlier cuts gave home 88% and the app
 * grids 75%, reasoning about how much slack each cell could afford — which was the fraction doing a job the **upper
 * guardrail** does better. At 100%, [maxIconDp] *is* the icon size on any cell bigger than it, so "how large are my
 * icons" is one number in dp rather than a percentage of a cell size the user has to picture. The fraction stays for
 * what it is good at: shrinking an icon *within* its cell, on a grid whose cells are already small.
 *
 * @property iconPercent the icon's edge length as a fraction of the cell's smaller bound. 1f fills the cell, which is
 *   the default; the guardrails then decide what "filled" means in dp.
 * @property labelScale multiplier on the base label text size.
 * @property showLabel whether a label is drawn under the icon at all.
 * @property minIconDp lower guardrail in dp — and the floor on a *cell*, since `CellFit` inverts it, so raising it
 *   takes columns and rows away.
 * @property maxIconDp upper guardrail in dp, and at the default fraction the effective icon size on any cell that
 *   can hold more.
 * @property showIcon whether the icon is drawn — meaningful for a text-only list.
 */
@Serializable
data class IconSizing(
    val iconPercent: Float = 1f,
    val labelScale: Float = 1f,
    val showLabel: Boolean = true,
    val minIconDp: Int = 24,
    val maxIconDp: Int = 48,
    val showIcon: Boolean = true,
)

/**
 * The bounds a user may move each [IconSizing] field within.
 *
 * Here rather than in the settings UI for the reason [GridEditRange] is here: a range is a fact about the value, so
 * the store can eventually clamp a write against it — the same write-side clamp the grid dimensions will use. Until
 * then its consumer is the sliders, which is why it exists at all rather than being invented ahead of one.
 *
 * The dp bounds are `IntRange` because [IconSizing] stores dp as whole numbers; a slider derives its step count from
 * the range rather than repeating it as a magic number.
 */
object IconSizingRanges {

    /** Fraction of a cell the icon fills. Floored well above zero — an invisible icon is `showIcon = false`, not 0%. */
    val IconPercent: ClosedFloatingPointRange<Float> = 0.3f..1f

    /** Multiplier on the base label size. */
    val LabelScale: ClosedFloatingPointRange<Float> = 0.7f..1.5f

    /**
      * The dp window both guardrails live in — one bound, because one two-thumb control sets both.
      *
      * One range rather than a separate one per thumb: a range slider cannot cross its own thumbs, so the invariant
      * is structural. Split caps are only ever a consequence of using two independent sliders.
      *
      * **The floor is 24dp because the floor is really a *cell* floor.** `CellFit` inverts [IconSizing.minIconDp] into
      * the smallest usable cell, so a bound of 16 offered a 24dp cell — thirteen rows in a 320dp dock, fifteen columns
      * across a phone — legal arithmetic that nothing could be tapped in. 24dp is the press-area floor.
      *
      * **The ceiling is 120dp, which is a judgment rather than a derivation**, so here is the reasoning: at the default
      * fraction the upper guardrail *is* the icon size, so it has to reach far enough for a tablet cell to be filled
      * (~150dp inner at eight columns) while keeping the 24–48 default legible on the track. 120 also stays clear of the
      * one place a bound can misbehave — the *lower* thumb drives the cell floor, and 120 + a cell's padding still
      * leaves a 360dp phone two columns rather than none.
      */
    val IconDp: IntRange = 24..120
}

/**
 * The bounds a user may set [GridBlueprint.horizontalPaddingDp] within.
 *
 * Beside [IconSizingRanges] and for its stated reason: a range is a fact about the value, so it belongs with the value
 * rather than in whichever slider happens to edit it.
 *
 * **The ceiling is where it is because padding competes with columns, and the loser is the grid.** 64dp a side takes
 * 128dp off a 360dp phone, which at the 24dp cell floor still leaves room for several columns — enough to be a
 * deliberate inset, short of enough to make a grid unusable. Nothing clamps *against* the column count, because the
 * fit already does: `CellFit` sees the reduced width and reports fewer columns, exactly as it does when icons grow.
 */
val HorizontalPaddingRange: IntRange = 0..64

/**
 * The static, per-surface description of one grid: which grid it *is*, how it is sized, how its cells subdivide,
 * whether items are free-placed, its default size for each [DeviceConfiguration], how icons fill its cells, and
 * (when editable) its edit limits.
 *
 * This is the single source of truth for grid **and cell** configuration across every surface × layout — which is
 * what lets `data:settings` store only what a user *changed* and resolve everything else from here, so a default
 * exists in exactly one place. Runtime maxima (how many rows/cols actually fit) are deliberately NOT stored here —
 * they depend on screen area and icon size, and are computed by the resolver in `core:designsystem`.
 *
 * @property slot which grid this describes; unique across all blueprints (see [GridBlueprints]).
 * @property sizing How the grid is bounded (see [GridSizing]).
 * @property cellMultiplier Logical cells per visual cell — 2 for free-placement surfaces, 1 otherwise; see [GridConfig].
 * @property freePlacement True when items hold explicit positions and edits reflow them (home, dock); false
 *   for auto-flowed app lists.
 * @property editRange The user-editable range, or null when the grid has no editor (e.g. folder grids). A null
 *   `minRows` inside one means the *row* axis alone is not the user's to set — a scrolling grid's case, whose rows
 *   are however many its content reaches.
 * @property defaults The default [GridDefault] for each [DeviceConfiguration].
 * @property icon The default [IconSizing] for this grid's cells, before any user override — or **null** for a grid
 *   whose cells are not icons. The card grid draws *tiles*, each containing its own small icon arrangement, so
 *   there is no "icon per cell" for a user to size; a null says so instead of carrying a value nothing reads.
 * @property extentDp The thickness in **dp** this grid occupies when it is a fixed-extent strip, or **null** for one
 *   that takes whatever space its parent gives it — every grid but the dock. `Int` rather than `Dp` because
 *   `core:model` is a plain JVM library, the same reason [IconSizing] stores dp as whole numbers. A grid that
 *   declares one **bounds the count along that axis** — a cell being `extent ÷ count`, there is a point past which
 *   another line leaves cells too small to draw an icon in.
 *
 *   **An extent rather than a height, because which dimension it is depends on the posture.** A side zone is a strip
 *   on three configurations and a rail on the fourth ([SideZoneEdge]), so the same stored number is a height there
 *   and a width here — and it bounds the *rows* in the first case and the *columns* in the second, which is why it
 *   is an *extent* rather than a height.
 *
 *   One value rather than a per-[DeviceConfiguration] map, matching [icon]: the thickness that fits an icon and its
 *   label is a physical size, and does not vary by posture the way a *count* of cells does. A user who wants a
 *   different dock in landscape overrides it there, since overrides are keyed per configuration.
 * @property rowHeightDp How tall one **row** of this grid is, in dp — or **null** for a grid whose rows take their
 *   height from somewhere else, which is every grid but the list. There are exactly three ways a cell gets a height,
 *   and a blueprint says which by what it declares:
 *   1. **divided out of an extent** — the dock, where a cell is [extentDp] ÷ rows (or ÷ columns, on a rail);
 *   2. **derived from its width** — every scrolling grid, whose columns fix the cell width, leaving the icon and
 *      label to decide the height (`cellHeight` in `core:designsystem`);
 *   3. **declared**, here — the vertical list, and only it. A list is one lane, so it has no cell width to derive
 *      from and no extent to divide: nothing determines the row, which makes it genuinely the user's to set, with
 *      the icon then a fraction of *it* (hence this grid's `iconPercent = 1f`).
 *
 *   Not folded into [extentDp]: that is a whole grid's thickness and this is one row of one, and a list has no total
 *   height at all — it scrolls. `Int` dp for the reason [extentDp] is, [IconSizing] included.
 * @property horizontalPaddingDp Blank margin kept at the grid's left and right edges, in dp. 0 by default, so an
 *   unconfigured launcher runs edge to edge.
 *
 *   **It is width the grid does not get**, which is what makes it a grid property rather than a decoration applied by
 *   whoever draws one: every cell dimension is divided out of the remaining width, so a surface that padded itself
 *   *after* computing its columns would draw cells narrower than the ones it sized its icons for. Every consumer
 *   therefore subtracts it before fitting, and `CellFit` sees the reduced area.
 *
 *   One value rather than a per-[DeviceConfiguration] map, matching [extentDp] and [rowHeightDp] and for the same
 *   reason: a margin is a physical size, not a count that should change with posture. A user who wants a wider one in
 *   landscape overrides it there, since overrides are keyed per configuration.
 * @property wraps Whether this grid's **pages wrap around** at the ends — or **null** for a grid whose wrapping is not
 *   the user's to set, which is every grid but three.
 *
 *   Null covers two different reasons, deliberately collapsed because the consequence is the same: a grid that is not
 *   paged at all (a list, a scrolling grid, the dock, the widget area), and a paged one that is bounded by
 *   construction (a folder, whose pages are a handful of apps in a card). This is [extentDp] and [rowHeightDp]'s
 *   convention, and it is what lets `SettingsRepository` refuse a slot that has no such setting rather than every
 *   caller checking first.
 *
 *   Not per-[DeviceConfiguration], and not for [horizontalPaddingDp]'s reason — this one is not a size at all. Whether
 *   pages loop is a fact about how the user wants to move through their apps, and it does not change when the phone is
 *   turned on its side.
 *
 *   **Off by default, which the surface-swipe rules force.** A wrapping pager has no edge to hand off from, so
 *   `AxisScroll.INFINITE` makes the one-finger swipe on that axis `OneFingerSwipe.NEVER` — with wrapping on by
 *   default, a horizontal edge binding could only be opened with **two fingers**, and a user who never found this
 *   setting would never know why. Off by default, opt in.
 * @property card the tile chrome of a grid of **cards** — or **null** for every grid that draws cells rather than
 *   tiles, which is all but one. [extentDp]'s convention again, and for its reason: it is what lets
 *   `SettingsRepository` refuse a slot that has no such setting instead of every caller checking first.
 */
data class GridBlueprint(
    val slot: GridSlot,
    val sizing: GridSizing,
    val cellMultiplier: Int,
    val freePlacement: Boolean,
    val editRange: GridEditRange?,
    val defaults: Map<DeviceConfiguration, GridDefault>,
    val icon: IconSizing? = null,
    val extentDp: Int? = null,
    val rowHeightDp: Int? = null,
    val horizontalPaddingDp: Int = 0,
    val wraps: Boolean? = null,
    val card: CardChrome? = null,
) {
    /** True when the row count is user-editable (a full rows + columns editor). */
    val editsRows: Boolean get() = editRange?.minRows != null

    /** True when this grid is a pager whose wrapping the user may turn on and off — see [wraps]. */
    val pages: Boolean get() = wraps != null
}

/** Builds the per-[DeviceConfiguration] default map from one [GridDefault] per configuration. */
private fun byDevice(
    phonePortrait: GridDefault,
    phoneLandscape: GridDefault,
    tabletPortrait: GridDefault,
    tabletLandscape: GridDefault,
) = mapOf(
    DeviceConfiguration.PHONE_PORTRAIT to phonePortrait,
    DeviceConfiguration.PHONE_LANDSCAPE to phoneLandscape,
    DeviceConfiguration.TABLET_PORTRAIT to tabletPortrait,
    DeviceConfiguration.TABLET_LANDSCAPE to tabletLandscape,
)

/**
 * Resolves this blueprint's default size for [device] into a concrete [GridConfig] in **logical** cells.
 *
 * [GridDefault] holds *visual* columns/rows; both axes are multiplied by [GridBlueprint.cellMultiplier] so the
 * returned config carries the blueprint's sub-cell resolution — e.g. the home pager's 4×5 visual default becomes
 * an 8×10 logical grid at multiplier 2. Requires a fixed row count, so it is only meaningful for
 * [GridSizing.FIXED_PAGER] grids (home, dock, drawer paged, folders); a [GridSizing.SCROLL_GRID] blueprint is
 * column-only and derives its rows at runtime, so it has nothing to resolve here.
 */
fun GridBlueprint.toGridConfig(device: DeviceConfiguration): GridConfig = toGridConfig(defaults.getValue(device))

/**
 * The same resolution from an explicit [size] rather than from this blueprint's own defaults.
 *
 * The overload `data:settings` needs: it resolves a user's sparse override against the blueprint default, then asks for
 * the config of the *result*. Without it that layer would have to re-do the multiplier arithmetic and could drift from
 * this one.
 */
fun GridBlueprint.toGridConfig(size: GridDefault): GridConfig {
    val visualRows = requireNotNull(size.rows) {
        "toGridConfig needs a fixed row count; $sizing grids resolve rows at runtime"
    }
    return GridConfig(
        rows = visualRows * cellMultiplier,
        cols = size.cols * cellMultiplier,
        cellMultiplier = cellMultiplier,
    )
}

/**
 * The default **visual** column count for [device] — which is the whole of a column-only
 * ([GridSizing.SCROLL_GRID]) blueprint's size, since its row count is whatever its content reaches at runtime.
 *
 * The counterpart of [toGridConfig], which cannot serve those grids at all: a [GridConfig] requires a row count,
 * and a scrolling grid has none to give. Visual (not logical) columns, because the consumer is a lazy grid asking
 * "how many cells across?" — a scrolling app grid has no sub-cell items to need the multiplier.
 */
fun GridBlueprint.colsFor(device: DeviceConfiguration): Int = defaults.getValue(device).cols

/** Home free-placement pager — sub-cell grid (multiplier 2); rows and columns both editable. */
val HomePagerGrid = GridBlueprint(
    slot = GridSlot.HOME_MAIN,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 2,
    freePlacement = true,
    editRange = GridEditRange(minCols = 4, minRows = 4),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4, rows = 5),
        phoneLandscape = GridDefault(cols = 6, rows = 4),
        tabletPortrait = GridDefault(cols = 5, rows = 7),
        tabletLandscape = GridDefault(cols = 8, rows = 6),
    ),
    // The launcher's defaults, unmodified — as every blueprint takes them. A per-grid fraction (88% here, on the
    // grounds that a 2×2 visual slot can afford slack) double-counts density: at 100% the same slack is expressed by
    // the 48dp cap instead, in the units a user reads.
    icon = IconSizing(),
    // Paged, and the wrapping is the user's — see `wraps` for why it starts off.
    wraps = false,
)

/**
 * Home dock — a free-placement strip with **an extent of its own**, divided into rows and columns within it.
 *
 * The one grid whose *extent* is a setting ([extentDp]) as well as its counts — and the two are not two ways of
 * saying one thing. The extent decides how much screen the strip takes; the counts divide it into cells. So the extent
 * **bounds** a count rather than replacing it: a cell is `extent ÷ count`, and that count may only go as high as keeps
 * the cell at least the smallest usable one (`CellFit` in `core:designsystem`). Both axes are the user's, which is why
 * [editRange] gives each a minimum.
 *
 * **Which count the extent bounds depends on where the dock sits** ([SideZoneEdge]): a bottom strip's height bounds
 * its *rows*, a rail's width bounds its *columns*. That is also why the phone-landscape [defaults] are the transpose
 * of the others — a column of four down the trailing edge, where the rest have a row of four across the bottom.
 *
 * **An extent change can invalidate the count it bounds, and shrinking the strip reduces that count to what it can now
 * hold** — a real write, so what is stored is always a grid the dock can actually draw. The *other* axis is untouched
 * by the extent and gets the opposite treatment: its cap moves only with the icon size, so a count too large for
 * today's icons is clamped on read and comes back when the icons shrink.
 *
 * [defaults] start both counts, and stand in for a frame that has no measurement yet.
 */
val DockGrid = GridBlueprint(
    slot = GridSlot.HOME_DOCK,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 2,
    freePlacement = true,
    editRange = GridEditRange(minCols = 1, minRows = 1),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4, rows = 1),
        // The rail: one column wide, four cells down it — the transpose of the strip, because on this posture the
        // extent below is a width rather than a height.
        phoneLandscape = GridDefault(cols = 1, rows = 4),
        tabletPortrait = GridDefault(cols = 5, rows = 1),
        tabletLandscape = GridDefault(cols = 5, rows = 1),
    ),
    icon = IconSizing(),
    // The dock's default extent; a user's own value overrides it through `SurfaceMetrics.extentDp`.
    extentDp = 96,
)

/**
 * HOME's vertical list ([HomeLayout.LIST_WITH_WIDGET_AREA]'s main area) — **one lane, scrolling**, ordered by hand.
 *
 * The home twin of [AppsListGrid], and everything structural about it is that grid's: one lane means no cell width to
 * derive a height from and no extent to divide, so the row height is [rowHeightDp] — *declared* — and the icon is a
 * fraction of it rather than the other way round. [editRange] is null for the same reason: a list is one lane by
 * definition, so there is no count to edit, which is not the same as saying nothing about it is configurable.
 *
 * **Two things separate it from the APPS list, and both are about what the surface is for.** Its rows are 64dp where
 * the drawer's are 56, because a home list holds the handful of apps you chose,
 * where a drawer holds all of them, so density is worth less here and reach is worth more. And its order is **stored**
 * (`home_list_item`) rather than derived A–Z: the whole point of this layout is that the user arranges it.
 *
 * **Deliberately not a view of the home placements.** Flattening them by (page, row, col) makes reordering the list
 * write `MoveApp(page = 0, row = index, col = 0)` for every app, destroying the grid arrangement underneath. Two
 * stores is what stops one layout scrambling the other; the flattening survives only as the *seed* (see
 * `HomeListRepository`).
 */
val HomeListGrid = GridBlueprint(
    slot = GridSlot.HOME_LIST,
    sizing = GridSizing.SCROLL_GRID,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = null,
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 1),
        phoneLandscape = GridDefault(cols = 1),
        tabletPortrait = GridDefault(cols = 1),
        tabletLandscape = GridDefault(cols = 1),
    ),
    icon = IconSizing(),
    rowHeightDp = 64,
)

/**
 * HOME's **widget area** ([HomeLayout.LIST_WITH_WIDGET_AREA]'s side zone) — a free-placement grid with an extent of
 * its own, and **the one grid whose cells are not icons**.
 *
 * Structurally the dock: same [cellMultiplier], same [freePlacement], same "the extent bounds the count divided out of
 * it" rule, same [SideZoneEdge] deciding which count that is. What differs is what goes in it, and that shows up in
 * exactly one field — [icon] is **null**, as [AppsCardGrid]'s is, because a widget is not an icon in a cell and there
 * is no fraction, guardrail or label for a user to set. That null is load-bearing: it is what makes the widget-area
 * settings section the smallest of the five: an editor and two sliders, no icon group.
 *
 * **A null [icon] also means `CellFit` cannot size this grid from an icon guardrail**, so the smallest usable cell
 * comes from `MinWidgetCellDp` instead — a widget's own floor.
 *
 * [extentDp] is 280dp: a widget area is meant to be *looked* at, so it takes far more of the screen than a dock's
 * 96.
 */
val WidgetAreaGrid = GridBlueprint(
    slot = GridSlot.HOME_WIDGET_AREA,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 2,
    freePlacement = true,
    editRange = GridEditRange(minCols = 1, minRows = 1),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4, rows = 3),
        // The rail: narrower and taller, because on this posture the extent below is a width.
        phoneLandscape = GridDefault(cols = 3, rows = 4),
        tabletPortrait = GridDefault(cols = 5, rows = 3),
        tabletLandscape = GridDefault(cols = 6, rows = 3),
    ),
    icon = null,
    extentDp = 280,
)

/**
 * APPS paged grid ([AppsLayout.PAGER], and the pages of [AppsLayout.PAGER_WITH_CATEGORY]) — fixed rows × cols
 * per page, one icon per cell; rows and columns editable.
 */
val AppsPagerGrid = GridBlueprint(
    slot = GridSlot.APPS_PAGER,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = GridEditRange(minCols = 4, minRows = 4),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4, rows = 5),
        phoneLandscape = GridDefault(cols = 6, rows = 4),
        tabletPortrait = GridDefault(cols = 5, rows = 7),
        tabletLandscape = GridDefault(cols = 8, rows = 6),
    ),
    // The same defaults as every other grid. Asking for 75% on the grounds that a page packs more columns than home
    // double-counts the density: a narrower cell already produces a smaller icon at 100%, the fraction being *of the
    // cell*.
    icon = IconSizing(),
    wraps = false,
)

/**
 * APPS scrolling grid ([AppsLayout.VERTICAL_GRID]) — one vertically-scrolling grid of every app; only the column
 * count is editable, because the rows are however many the app count reaches.
 *
 * Named for its *sizing*, not its layout value, so it doesn't collide with the composable that renders it
 * (`AppsVerticalGrid`) — and because "scroll vs paged" is exactly what distinguishes it from [AppsPagerGrid].
 */
val AppsScrollGrid = GridBlueprint(
    slot = GridSlot.APPS_SCROLL,
    sizing = GridSizing.SCROLL_GRID,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = GridEditRange(minCols = 2, minRows = null),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4),
        phoneLandscape = GridDefault(cols = 6),
        tabletPortrait = GridDefault(cols = 5),
        tabletLandscape = GridDefault(cols = 8),
    ),
    icon = IconSizing(),
)

/**
 * APPS category-pager grid ([AppsLayout.PAGER_WITH_CATEGORY]) — that layout is a **pager whose every page is one
 * category**, and this is the grid a page holds: a single vertically-scrolling grid of that category's apps. Only
 * the column count is editable, since the rows are however many the category reaches.
 *
 * **Apps are dragged to reorder, both within a page and across pages** — carrying an app onto another page is how
 * it changes category, so the drag is the layout's only editing gesture.
 *
 * **No folders live here.** That is a property of the layout, not a gap: a category *is* the grouping, so a folder
 * inside one would be a second, redundant one. It has a direct consequence for the drag partition — with nothing
 * to merge into, a hovered cell splits into **halves** (gap before / gap after) and there is no center merge ring,
 * unlike every coordinate surface. Prototyped in the `CategoryPagerPlayground` harness.
 *
 * Not the [AppsLayout.CATEGORY_CARD] grid: a card is a small fixed preview of a category rather than a full page of
 * it, and the view it *opens into* is sized by [FolderGrid] — the same grid, and the same overlay, a folder uses.
 */
val AppsCategoryGrid = GridBlueprint(
    slot = GridSlot.APPS_CATEGORY,
    sizing = GridSizing.SCROLL_GRID,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = GridEditRange(minCols = 2, minRows = null),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4),
        phoneLandscape = GridDefault(cols = 6),
        tabletPortrait = GridDefault(cols = 5),
        tabletLandscape = GridDefault(cols = 8),
    ),
    icon = IconSizing(),
    // The one grid that is `SCROLL_GRID` *and* paged: each page scrolls vertically, and the pages themselves are the
    // pager. So `sizing` could never be what says a grid wraps — which is why [GridBlueprint.wraps] is declared
    // rather than derived from it.
    wraps = false,
)

/**
 * Folder / category-card grid — fixed defaults with no editor (sized by icon config only).
 *
 * One blueprint for both because they are one view: an opened folder and an expanded category card are the same
 * bounded, paged grid of an ordered app list (the same `AppCollectionOverlay` renders them), differing only in where their
 * contents come from.
 */
val FolderGrid = GridBlueprint(
    slot = GridSlot.FOLDER,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = null,
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 3, rows = 4),
        phoneLandscape = GridDefault(cols = 5, rows = 2),
        tabletPortrait = GridDefault(cols = 4, rows = 5),
        tabletLandscape = GridDefault(cols = 6, rows = 3),
    ),
    icon = IconSizing(),
)

/**
 * APPS vertical list ([AppsLayout.VERTICAL_LIST]) — **one lane, scrolling**, which is all a list is.
 *
 * It has a blueprint for two reasons, and they are the two things a list still has to be told. It draws icon cells, so
 * it needs somewhere for its icon sizing to live; and it is **the one grid that declares its own row height**
 * ([rowHeightDp]), because being one lane leaves it nothing to derive one from. Its *columns* are not editable — a
 * list is one lane by definition — which is why [editRange] stays null even though a row height is very much the
 * user's to set: that range bounds counts, not extents, exactly as the dock's height sits outside it too.
 *
 * The icon fills its row: there is no label *underneath* to leave space for, since `AppRowCell` sets the label beside
 * the icon rather than below it. So the row is the primary quantity and the icon a fraction of it — the reverse of a
 * grid cell, where the icon size is chosen and the cell follows.
 *
 * A 56dp row with a 40dp icon inside it, expressed as a row height and a fraction rather than two constants — so the
 * list answers to its own icon settings instead of hardcoding both.
 */
val AppsListGrid = GridBlueprint(
    slot = GridSlot.APPS_LIST,
    sizing = GridSizing.SCROLL_GRID,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = null,
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 1),
        phoneLandscape = GridDefault(cols = 1),
        tabletPortrait = GridDefault(cols = 1),
        tabletLandscape = GridDefault(cols = 1),
    ),
    icon = IconSizing(),
    rowHeightDp = 56,
)

/**
 * APPS category-card grid ([AppsLayout.CATEGORY_CARD]) — a vertically-scrolling grid of **square tiles**, one per
 * category.
 *
 * **Vertical in every orientation, with the lane count carrying the difference.** A card is square, so its size is
 * `shortEdge / lanes`; in landscape the width *is* the long edge, so keeping cards the same physical size means more
 * lanes, not a different scroll axis. Checked against iPadOS's App Library, which likewise stays a vertical grid and
 * only varies its column count. That is why [GridSizing] needs no horizontal variant and `GridDefault.cols` stays
 * non-null — the simpler model turned out to be the correct one.
 *
 * **No icon sizing** ([icon] is null): the cells are tiles, and the icons *inside* a tile are derived rather than
 * configured. A square card divides into four square preview slots and the icon fills its slot, so there is no
 * fraction for a user to choose. Making the lane count device-aware is also what retires the note on the card's
 * preview metrics, which observed that a 72dp icon cap "*will* bind on a tablet, where two columns of cards give a
 * slot far wider than that" and concluded: fix the columns, not the cap.
 *
 * Editable, floored at two lanes — a single lane means one card per row filling the whole width, which reads as a bug
 * rather than as a choice.
 */
val AppsCardGrid = GridBlueprint(
    slot = GridSlot.APPS_CARD,
    sizing = GridSizing.SCROLL_GRID,
    cellMultiplier = 1,
    freePlacement = false,
    editRange = GridEditRange(minCols = 2, minRows = null),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 2),
        // Twice portrait's, because a phone's landscape width is roughly twice its portrait width — which is what
        // keeps a card the same size on screen rather than doubling it.
        phoneLandscape = GridDefault(cols = 4),
        tabletPortrait = GridDefault(cols = 3),
        // A tablet is nearer square than a phone, so the long edge is not twice the short one; four, not six.
        tabletLandscape = GridDefault(cols = 4),
    ),
    // **A card's preview icons are sized like any other grid's.** A null here would say "a card is a tile, not a
    // cell" — true of the *card*, and wrong about what is inside one: the four slots are icons, and leaving them
    // unowned makes their size a pure consequence of the
    // lane count — tiny at four lanes, enormous at two, and different again on rotating the device, with no control
    // anywhere to say otherwise. Declaring sizing makes that the user's, and buys the lane ceiling for free: the
    // narrowest usable card is `2 × minIconDp` plus the paddings below, which is `CellFit`'s ordinary inversion of a
    // guardrail rather than the hand-picked constant it replaced.
    //
    // `showLabel = false` because the slots carry no labels — a card is a thumbnail of a category, and four
    // ellipsized words would eat the room the icons need. It is the same statement `AppsListGrid` makes with
    // `showIcon`, from the other side, and it is what hides the two text controls in the section.
    icon = IconSizing(showLabel = false),
    card = CardChrome(),
)

/**
 * Every blueprint, by [GridSlot] — the registry that makes slot → blueprint total.
 *
 * `data:settings` resolves a stored override against `GridBlueprints.getValue(slot)`, so a slot with no blueprint
 * would be a runtime failure the moment someone read a setting for it. That the map covers [GridSlot] exactly once
 * is checked by a test rather than asserted here, so a mistake fails the build instead of the launcher.
 */
val GridBlueprints: Map<GridSlot, GridBlueprint> = listOf(
    HomePagerGrid,
    DockGrid,
    HomeListGrid,
    WidgetAreaGrid,
    AppsPagerGrid,
    AppsScrollGrid,
    AppsListGrid,
    AppsCategoryGrid,
    AppsCardGrid,
    FolderGrid,
).associateBy { it.slot }

/** This slot's blueprint — its grid defaults, its cell subdivision, and its default icon sizing. */
val GridSlot.blueprint: GridBlueprint get() = GridBlueprints.getValue(this)
