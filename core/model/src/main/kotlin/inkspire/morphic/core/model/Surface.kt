package inkspire.morphic.core.model

/**
 * A full-screen destination the user gestures between. All values are peers: [HOME] is the center of the
 * launcher, and every other value is a surface reachable from a home edge (see [HomeEdge]).
 *
 * - [HOME]: the default surface — the main area plus its coupled side zone (see [HomeLayout]).
 * - [APPS]: the full app collection; its look is chosen per binding via [AppsLayout], so this single value
 *   covers both the former "drawer" and "library" styles.
 *
 * Further surfaces (e.g. a search surface) are added as they are built.
 */
enum class Surface { HOME, APPS }

/**
 * An edge of the [Surface.HOME] surface the user can swipe from to reach a side surface. Each edge is bound
 * independently, so different edges may point at different surfaces — or at the same surface with different
 * configuration (that binding lives in the settings layer, not here).
 */
enum class HomeEdge {
    LEFT, RIGHT, TOP, BOTTOM;

    /**
     * True for the two edges that lie on the **horizontal** axis, so a swipe crossing them is a horizontal one.
     *
     * Here rather than at each call site because "which axis does this edge live on?" is the question every gesture
     * and every policy lookup starts from — the surface pan locks to that axis, and the one-finger policy for an edge
     * is decided by whatever content owns it. Both spellings were private copies in the surface-pager playground.
     */
    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT

    /**
     * The edge directly across HOME from this one, on the same axis.
     *
     * Read when a crossing is described from the *other* end: opening the RIGHT surface pulls HOME's content toward
     * its right edge, and closing that same surface pulls the surface's own content back toward its **left**. One
     * expression, so the two directions cannot drift apart. Mirrors [SideZoneEdge.opposite], and for the same reason —
     * the flip must stay on the axis it started on.
     */
    val opposite: HomeEdge
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
            TOP -> BOTTOM
            BOTTOM -> TOP
        }
}

/**
 * The composition of the [Surface.HOME] surface. The main area's layout and its companion side zone are
 * coupled, so only these combinations are valid — modeling them as one enum makes any other pairing
 * unrepresentable.
 *
 * - [PAGER_WITH_DOCK]: the main area is a horizontal pager of grids; its side zone is a [HomeZone.DOCK].
 * - [LIST_WITH_WIDGET_AREA]: the main area is a single vertical list; its side zone is a [HomeZone.WIDGET_AREA].
 */
enum class HomeLayout { PAGER_WITH_DOCK, LIST_WITH_WIDGET_AREA }

/**
 * A placement region within the [Surface.HOME] surface — the zone an item is stored against. [MAIN] is the
 * primary content; [DOCK] and [WIDGET_AREA] are the two possible side zones. Only one side zone is active at
 * a time, determined by the current [HomeLayout], but items keep their zone identity even when their zone is
 * not the active one.
 */
enum class HomeZone { MAIN, DOCK, WIDGET_AREA }

/**
 * How the [Surface.APPS] surface renders its app collection. Unifies the former "drawer" and "library"
 * styles — the layout alone decides the look, so there is no separate drawer/library flag.
 *
 * - [VERTICAL_LIST]: one vertically-scrolling list.
 * - [VERTICAL_GRID]: one vertically-scrolling grid.
 * - [PAGER]: paged grids, no categories.
 * - [PAGER_WITH_CATEGORY]: paged grids with category tabs.
 * - [CATEGORY_CARD]: category cards (the iOS App-Library style).
 */
enum class AppsLayout {
    VERTICAL_LIST,
    VERTICAL_GRID,
    PAGER,
    PAGER_WITH_CATEGORY,
    CATEGORY_CARD,
}
