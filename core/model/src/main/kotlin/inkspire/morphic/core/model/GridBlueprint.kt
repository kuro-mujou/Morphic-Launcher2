package inkspire.morphic.core.model

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
 * The static, per-surface description of one grid: how it is sized, how its cells subdivide, whether items
 * are free-placed, its default size for each [DeviceConfiguration], and (when editable) its edit limits.
 *
 * This is the single source of truth for grid configuration across every surface × layout. Runtime maxima
 * (how many rows/cols actually fit) are deliberately NOT stored here — they depend on screen area and icon
 * size, and are computed by the resolver in `core:designsystem`.
 *
 * @property sizing How the grid is bounded (see [GridSizing]).
 * @property cellMultiplier Logical cells per visual cell — 2 for free-placement surfaces, 1 otherwise; see [GridConfig].
 * @property freePlacement True when items hold explicit positions and edits reflow them (home, dock); false
 *   for auto-flowed app lists.
 * @property editRange The user-editable range, or null when the grid has no editor (e.g. folder grids).
 * @property defaults The default [GridDefault] for each [DeviceConfiguration].
 */
data class GridBlueprint(
    val sizing: GridSizing,
    val cellMultiplier: Int,
    val freePlacement: Boolean,
    val editRange: GridEditRange?,
    val defaults: Map<DeviceConfiguration, GridDefault>,
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

/** Home free-placement pager — sub-cell grid (multiplier 2); rows and columns both editable. */
val HomePagerGrid = GridBlueprint(
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
)

/** Home dock — a free-placement strip (default single row); rows and columns editable down to 1×1. */
val DockGrid = GridBlueprint(
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
)

/** App drawer paged grid — fixed rows × cols per page, one icon per cell; rows and columns editable. */
val DrawerPagerGrid = GridBlueprint(
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
)

/** App drawer "Classic" — one vertically-scrolling grid; only the column count is editable. */
val DrawerClassicGrid = GridBlueprint(
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
)

/** App drawer "Grouped" — category tabs over scrolling grids; only the column count is editable. */
val DrawerGroupedGrid = GridBlueprint(
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
)

/** Folder / category-card grid — fixed defaults with no editor (sized by icon config only). */
val FolderGrid = GridBlueprint(
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
)
