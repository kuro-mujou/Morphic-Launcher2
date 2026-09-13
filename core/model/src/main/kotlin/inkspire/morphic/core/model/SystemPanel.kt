package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/** One of the system's pull-down panels — what [GestureAction.OpenSystemPanel] names. */
@Serializable
enum class ShadePanel {
    NOTIFICATIONS,
    QUICK_SETTINGS,
}

/**
 * How the user's phone arranges its pull-down panels — which the launcher is **told**, because it cannot find out.
 *
 * There is no API for it. Stock Android, One UI and HyperOS each keep the choice in a private setting of their own, and
 * a detection keyed on any of them would fail silently on the next skin, so the user picks the style their phone uses.
 *
 * **It decides how a panel is opened, never which.** The action names its panel; a separate shade is reached by a touch
 * on that panel's side and a combined one by a global action — see `PlatformSystemShade`. A wrong answer here opens the
 * other panel, without a word.
 */
@Serializable
enum class ShadeStyle {
    /** One panel holding notifications and quick settings, pulled from anywhere along the top. */
    COMBINED,

    /** Notifications pulled from the left of the top edge, quick settings from the right. */
    SEPARATE,
}
