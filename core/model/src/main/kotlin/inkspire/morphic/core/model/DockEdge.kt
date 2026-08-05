package inkspire.morphic.core.model

/**
 * **Which edge of HOME the dock occupies** — and therefore whether its configured extent is a height or a width, and
 * which of home's two dimensions the pager is left with.
 *
 * One fact with several consequences, which is why it is a type rather than a `landscape` boolean threaded through
 * each of them: the surface stacks its zones along this axis, the dock's extent is measured along it, the settings
 * slider is titled by it, and the grid editor draws the companion zone on this side. L1 passed a `landscape: Boolean`
 * to `homeGridArea`, `HomeGridEditor`, `DockGridEditor` and its extent slider separately, and each one re-derived what
 * that meant for it.
 *
 * See [dockEdge] for the rule. There is deliberately no setting behind it: a rail is what a short, wide window can
 * afford, not a preference.
 */
enum class DockEdge {
    /** A strip across the bottom: its extent is a **height**, and the pager takes the height that is left. */
    BOTTOM,

    /**
     * A rail down the trailing side: its extent is a **width**, and the pager takes the width that is left.
     *
     * `END` rather than `RIGHT` because it is a layout-direction edge — a `Row` places it last, which lands it on the
     * right in LTR and the left in RTL, and that is what a user of either expects.
     */
    END,
}

/**
 * Where the dock sits on this device: a **rail** on a phone in landscape, a **bottom strip** everywhere else.
 *
 * The one posture that is short and wide: a phone in landscape has perhaps 350dp of height, and a bottom dock would
 * take a third of it for a single row of icons while the pager was left with two. The other three have height to spare
 * — a tablet in landscape included, which is why this keys on the whole [DeviceConfiguration] rather than on
 * [DeviceConfiguration.isLandscape].
 */
val DeviceConfiguration.dockEdge: DockEdge
    get() = if (this == DeviceConfiguration.PHONE_LANDSCAPE) DockEdge.END else DockEdge.BOTTOM
