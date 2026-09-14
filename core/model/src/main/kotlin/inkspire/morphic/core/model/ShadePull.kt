package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * How a gesture pulls down the system's panels — what [GestureAction.OpenSystemPanel] holds.
 *
 * **Every member works without knowing how the phone arranges its panels**, which the launcher cannot find out: no API
 * exposes it, and One UI, HyperOS and stock each keep it privately. So nothing asks the user to describe their phone —
 * a description that could be wrong, and was, silently opening the other panel. Each member is a gesture instead, whose
 * result on a combined and on a separate shade is stated below; `PlatformSystemShade` performs it.
 */
@Serializable
enum class ShadePull {
    /**
     * Asks the system to pull its shade down and shows whatever that opens: notifications on a combined shade, and the
     * control center on RedMagic's separate one, which answers every programmatic expand that way.
     */
    PULL_DOWN,

    /**
     * [NOTIFICATIONS] from a gesture that starts on the left half of the screen, [QUICK_SETTINGS] from the right half.
     *
     * **Only for a gesture whose starting side is the user's choice** — a vertical swipe on HOME. A horizontal swipe's
     * side is decided by its direction, a double tap's by where the thumb landed and an icon's by where the icon sits,
     * so the picker offers it nowhere else.
     */
    BY_SIDE,

    /** A pull-down on the left of the status bar: notifications on either arrangement. */
    NOTIFICATIONS,

    /**
     * A pull-down on the right of the status bar, then a second: a separate shade opens quick settings on the first,
     * and a combined one expands into them on the second.
     */
    QUICK_SETTINGS,
}
