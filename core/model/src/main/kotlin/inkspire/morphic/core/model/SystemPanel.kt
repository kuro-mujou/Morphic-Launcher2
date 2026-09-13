package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/** One of the system's pull-down panels — what [GestureAction.OpenSystemPanel] opens once it knows which. */
enum class ShadePanel {
    NOTIFICATIONS,
    QUICK_SETTINGS,
}

/**
 * How the user's phone arranges its pull-down panels — which the launcher is **told**, because it cannot find out.
 *
 * There is no API for it. Stock Android, One UI and HyperOS each keep the choice in a private setting of their own, and
 * a detection keyed on any of them would fail silently on the next skin, so the user picks the style their phone uses.
 */
@Serializable
enum class ShadeStyle {
    /** One panel holding notifications and quick settings, pulled from anywhere along the top. */
    COMBINED,

    /** Notifications pulled from the left of the top edge, quick settings from the right. */
    SEPARATE,
    ;

    /**
     * The panel a swipe that started at [startX] opens, where [startX] is a fraction of the screen's width, 0 at the
     * **physical** left.
     *
     * The split is the middle for every skin: close to HyperOS and stock Android, and off on One UI, whose own split
     * is about 70/30.
     */
    fun panelAt(startX: Float): ShadePanel =
        if (this == SEPARATE && startX >= SEPARATE_SPLIT) ShadePanel.QUICK_SETTINGS else ShadePanel.NOTIFICATIONS
}

/** Where a [ShadeStyle.SEPARATE] shade splits, as a fraction of the screen's width. */
private const val SEPARATE_SPLIT = 0.5f

/**
 * What opening a system panel needs: the phone's [style] and where the swipe began, [startX] — a fraction of the
 * screen's width, 0 at the physical left.
 *
 * **Both, not the resolved [panel] alone**, because *how* a panel is opened depends on the style too: a separate shade
 * is decided by where a finger lands, which makes it the one arrangement a replayed swipe can address where a platform
 * call cannot.
 */
data class ShadeRequest(val style: ShadeStyle, val startX: Float) {

    /** The panel this asks for. */
    val panel: ShadePanel get() = style.panelAt(startX)

    companion object {
        /** The whole shade, for a gesture with no side of the screen to choose a panel by. */
        val WholeShade = ShadeRequest(ShadeStyle.COMBINED, 0f)
    }
}
