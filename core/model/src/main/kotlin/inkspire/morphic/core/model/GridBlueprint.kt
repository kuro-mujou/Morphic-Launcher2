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
    /** HOME's paged main area. */
    HOME_MAIN,

    /** HOME's dock strip. */
    HOME_DOCK,

    /** The APPS paged grid, and the pages of the category pager. */
    APPS_PAGER,

    /** The APPS vertically-scrolling grid of every app. */
    APPS_SCROLL,

    /**
     * The APPS vertical *list* of every app.
     *
     * A list is a **one-lane scrolling grid**, which is why it belongs in this enum rather than beside it: it draws
     * icon cells and therefore needs its own icon sizing, and "each grid config gets an independent icon config" is
     * the rule that makes that automatic. (L1 modelled its list layout the same way — a profile of one column.)
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
 * @property iconPercent the icon's edge length as a fraction of the cell's smaller bound — the primary control.
 * @property labelScale multiplier on the base label text size.
 * @property showLabel whether a label is drawn under the icon at all.
 * @property minIconDp lower guardrail in dp; a wide bound, not the primary limit.
 * @property maxIconDp upper guardrail in dp; likewise.
 * @property showIcon whether the icon is drawn — meaningful for a text-only list.
 */
@Serializable
data class IconSizing(
    val iconPercent: Float = 0.88f,
    val labelScale: Float = 1f,
    val showLabel: Boolean = true,
    val minIconDp: Int = 28,
    val maxIconDp: Int = 72,
    val showIcon: Boolean = true,
)

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
 * @property editRange The user-editable range, or null when the grid has no editor (e.g. folder grids).
 * @property defaults The default [GridDefault] for each [DeviceConfiguration].
 * @property icon The default [IconSizing] for this grid's cells, before any user override — or **null** for a grid
 *   whose cells are not icons. The card grid draws *tiles*, each containing its own small icon arrangement, so
 *   there is no "icon per cell" for a user to size; a null says so instead of carrying a value nothing reads.
 */
data class GridBlueprint(
    val slot: GridSlot,
    val sizing: GridSizing,
    val cellMultiplier: Int,
    val freePlacement: Boolean,
    val editRange: GridEditRange?,
    val defaults: Map<DeviceConfiguration, GridDefault>,
    val icon: IconSizing? = null,
) {
    /** True when the row count is user-editable (a full rows + columns editor). */
    val editsRows: Boolean get() = editRange?.minRows != null
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
fun GridBlueprint.toGridConfig(device: DeviceConfiguration): GridConfig {
    val default = defaults.getValue(device)
    val visualRows = requireNotNull(default.rows) {
        "toGridConfig needs a fixed row count; $sizing grids resolve rows at runtime"
    }
    return GridConfig(
        rows = visualRows * cellMultiplier,
        cols = default.cols * cellMultiplier,
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
    // 0.88 — a home cell is a 2x2 visual slot around one icon, so it can afford slack. This is the value home
    // inherited from `LocalIconMetrics`' own default rather than ever setting.
    icon = IconSizing(iconPercent = 0.88f),
)

/** Home dock — a free-placement strip (default single row); rows and columns editable down to 1×1. */
val DockGrid = GridBlueprint(
    slot = GridSlot.HOME_DOCK,
    sizing = GridSizing.FIXED_PAGER,
    cellMultiplier = 2,
    freePlacement = true,
    editRange = GridEditRange(minCols = 1, minRows = 1),
    defaults = byDevice(
        phonePortrait = GridDefault(cols = 4, rows = 1),
        phoneLandscape = GridDefault(cols = 4, rows = 1),
        tabletPortrait = GridDefault(cols = 5, rows = 1),
        tabletLandscape = GridDefault(cols = 5, rows = 1),
    ),
    icon = IconSizing(iconPercent = 0.88f),
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
    // Denser than home's: a page packs four to eight columns where a home cell holds one icon with slack.
    icon = IconSizing(iconPercent = 0.75f),
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
    icon = IconSizing(iconPercent = 0.75f),
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
 * to merge into, a hovered cell splits into **halves** (gap before / gap after) and there is no centre merge ring,
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
    icon = IconSizing(iconPercent = 0.75f),
)

/**
 * Folder / category-card grid — fixed defaults with no editor (sized by icon config only).
 *
 * One blueprint for both because they are one view: an opened folder and an expanded category card are the same
 * bounded, paged grid of an ordered app list (the same `FolderOverlay` renders them), differing only in where their
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
    icon = IconSizing(iconPercent = 0.75f),
)

/**
 * APPS vertical list ([AppsLayout.VERTICAL_LIST]) — **one lane, scrolling**, which is all a list is.
 *
 * It has a blueprint for one reason: it draws icon cells, so it needs somewhere for its icon sizing to live, and every
 * grid's icon config hangs off its blueprint. Nothing is editable — a list is one lane by definition, and its row
 * *height* is a separate metric (S4) rather than a grid dimension.
 *
 * The icon fills its row: there is no label *underneath* to leave space for, since `AppRowCell` sets the label beside
 * the icon rather than below it.
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
    icon = IconSizing(iconPercent = 1f),
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
    AppsPagerGrid,
    AppsScrollGrid,
    AppsListGrid,
    AppsCategoryGrid,
    AppsCardGrid,
    FolderGrid,
).associateBy { it.slot }

/** This slot's blueprint — its grid defaults, its cell subdivision, and its default icon sizing. */
val GridSlot.blueprint: GridBlueprint get() = GridBlueprints.getValue(this)
