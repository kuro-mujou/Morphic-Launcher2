package inkspire.morphic.core.model.widget

import inkspire.morphic.core.model.GestureAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What tapping one layer of a widget does. A layer takes a tap and nothing else: swipes and double taps would fight
 * the widget's own, and HOME's page swipe, inside every box that had one, and a long press is HOME's for the item's
 * menu and its drag.
 *
 * The [SerialName]s are the stored contract.
 */
@Serializable
sealed interface WidgetTap {

    /** Anything a gesture on HOME can do — open an app, a shortcut, a system panel — run by the same runner. */
    @Serializable
    @SerialName("run")
    data class Run(val action: GestureAction) : WidgetTap

    /**
     * Flips one of the widget's own settings: a switch turns over, a choice steps to its next option. How a clock
     * toggles its 24-hour switch from the widget itself, without the studio.
     *
     * @property global read in the tapped layer's scope — the nearest setting of that name, a block's before the
     *   widget's — exactly as a binding on that layer would read it.
     */
    @Serializable
    @SerialName("flip")
    data class Flip(val global: String) : WidgetTap
}
