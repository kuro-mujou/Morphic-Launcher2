package inkspire.morphic.core.model

/**
 * **Which edge of HOME the side zone occupies** — and therefore whether its configured extent is a height or a width,
 * and which of home's two dimensions the main area is left with.
 *
 * One fact with several consequences, which is why it is a type rather than a `landscape` boolean threaded through
 * each of them: the surface stacks its zones along this axis, the zone's extent is measured along it, the settings
 * slider is titled by it, and the grid editor draws the companion zone on this side. L1 passed a `landscape: Boolean`
 * to `homeGridArea`, `HomeGridEditor`, `DockGridEditor` and its extent slider separately, and each one re-derived what
 * that meant for it — *and* it had to pass a second flag (`dockAtStart`) beside it, because a boolean cannot say
 * "which of four edges".
 *
 * **Four values, because HOME has two layouts and they put their side zone on opposite ends.** This was `DockEdge`
 * with two values while `PAGER_WITH_DOCK` was the only layout: a dock is a bottom strip or a trailing rail.
 * `LIST_WITH_WIDGET_AREA` mirrors both — a widget area is a *top* strip or a *leading* rail — so the enum is the four
 * edges and [HomeLayout] chooses the pair. See [sideZoneEdge] for the rule.
 *
 * `START`/`END` rather than `LEFT`/`RIGHT` because they are layout-direction edges — a `Row` places one first and the
 * other last, which lands them on opposite sides in LTR and RTL, and that is what a user of either expects. `TOP` and
 * `BOTTOM` need no such care.
 *
 * There is deliberately no setting behind any of this: which end the zone sits on is what the layout means plus what a
 * short, wide window can afford, not a preference.
 */
enum class SideZoneEdge {
    /** A strip across the top: its extent is a **height**, and the main area takes the height that is left. */
    TOP,

    /** A strip across the bottom: its extent is a **height**, and the main area takes the height that is left. */
    BOTTOM,

    /** A rail down the leading side: its extent is a **width**, and the main area takes the width that is left. */
    START,

    /** A rail down the trailing side: its extent is a **width**, and the main area takes the width that is left. */
    END;

    /**
     * True when the zone is stacked along the window's **height** — a strip rather than a rail.
     *
     * The one derived fact every consumer wants: it decides whether the extent is a height or a width, whether the
     * zones go in a `Column` or a `Row`, and which of the two counts the extent bounds. Here rather than at each call
     * site so "is this a strip?" reads the same everywhere.
     */
    val isStrip: Boolean get() = this == TOP || this == BOTTOM

    /**
     * True when the zone is placed **first** in its container — a `Column`'s top or a `Row`'s start.
     *
     * L1's `dockAtStart`, which it had to pass beside its `landscape` boolean for want of a type that could say both.
     */
    val isLeading: Boolean get() = this == TOP || this == START

    /**
     * The edge **the main area sits against** — this one flipped along the same axis.
     *
     * Read by the side zone's settings section, which draws the *other* zone as its companion: a widget area at the
     * top has the list below it, a dock at the bottom has the pager above. Here rather than as a `when` in that
     * screen because the flip must stay on the same axis — a strip's opposite is a strip.
     */
    val opposite: SideZoneEdge
        get() = when (this) {
            TOP -> BOTTOM
            BOTTOM -> TOP
            START -> END
            END -> START
        }
}

/**
 * Where HOME's side zone sits for [layout] on this device: a **rail** on a phone in landscape, a **strip** everywhere
 * else — at the end the layout puts it.
 *
 * **Why the rail at all.** A phone in landscape is the one posture that is short and wide: perhaps 350dp of height, so
 * a horizontal strip would take a third of it for a single row while the main area was left with two. The other three
 * have height to spare — a tablet in landscape included, which is why this keys on the whole [DeviceConfiguration]
 * rather than on [DeviceConfiguration.isLandscape].
 *
 * **Why the two layouts differ.** A dock is the thing you reach for, so it sits under your thumb: bottom, or trailing.
 * A widget area is the thing you *look* at, so it sits where reading starts: top, or leading. Neither is configurable
 * — a layout that put its widgets under its list would not be the layout the user picked.
 */
fun DeviceConfiguration.sideZoneEdge(layout: HomeLayout): SideZoneEdge {
    val rail = this == DeviceConfiguration.PHONE_LANDSCAPE
    return when (layout) {
        HomeLayout.PAGER_WITH_DOCK -> if (rail) SideZoneEdge.END else SideZoneEdge.BOTTOM
        HomeLayout.LIST_WITH_WIDGET_AREA -> if (rail) SideZoneEdge.START else SideZoneEdge.TOP
    }
}
