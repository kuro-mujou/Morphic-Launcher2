package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a gesture does when it fires.
 *
 * **There is no `None`, and that is the store's sparseness rather than an omission.** A gesture nobody has assigned
 * simply has no entry; the picker's "None" row clears one. A member for it would make "unassigned" expressible two
 * ways, and the two would drift.
 *
 * **Short [SerialName]s**, because these reach a user's stored blob: without them the discriminator is the
 * fully-qualified class name, so moving or renaming a member would orphan every gesture using it.
 */
@Serializable
sealed interface GestureAction {

    /** Opens an app, exactly as tapping its icon would. */
    @Serializable
    @SerialName("app")
    data class LaunchApp(val component: ComponentKey) : GestureAction

    /**
     * Starts one of an app's own shortcuts — the entries its context menu lists.
     *
     * **A stored handle, which is a departure the rest of the codebase avoids.** `AppShortcut` is documented as
     * something never persisted: the id means something only to [packageName] under [userSerial], and only while
     * that app keeps publishing it. A gesture assignment has no choice — the whole point is that it outlives the
     * menu it was picked from — so the staleness has to be handled instead of avoided. An app update that withdraws
     * a shortcut leaves an assignment pointing at nothing, and firing it does nothing rather than crashing, the same
     * way launching an uninstalled app does.
     *
     * @property label what the shortcut called itself when it was picked, kept **for display only**. Resolving the
     *   live one costs a platform query per row, and the sheet that shows it is not worth that; a renamed shortcut
     *   therefore shows its old name until it is reassigned.
     */
    @Serializable
    @SerialName("shortcut")
    data class LaunchShortcut(
        val id: String,
        val packageName: String,
        val userSerial: Long,
        val label: String,
    ) : GestureAction

    /**
     * Pulls down [panel]. **The assignment names it**, never where the gesture started, so the action does the same
     * thing from every gesture — a double tap and an item's swipe included.
     *
     * **Performed by the launcher's accessibility service** under either [ShadeStyle], which only decides how.
     */
    @Serializable
    @SerialName("panel")
    data class OpenSystemPanel(val panel: ShadePanel) : GestureAction

    /**
     * Turns the screen off and locks it, as the power button does.
     *
     * **Performed by the launcher's accessibility service**, since an app has no other way short of device admin; it does
     * not exist below API 28. Offered on HOME's own gestures only.
     */
    @Serializable
    @SerialName("lock_screen")
    data object LockScreen : GestureAction
}

/**
 * Whether [this] is performed by the launcher's accessibility service, and so cannot run while it is off.
 *
 * One answer for the runner that asks for the service in place of running, and the settings card that shows whether it
 * is on — so the two cannot disagree about which gestures depend on it.
 */
val GestureAction.needsGestureService: Boolean
    get() = when (this) {
        is GestureAction.LaunchApp, is GestureAction.LaunchShortcut -> false
        is GestureAction.OpenSystemPanel, GestureAction.LockScreen -> true
    }
