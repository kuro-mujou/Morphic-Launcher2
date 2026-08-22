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
 *
 * The system actions of the eventual picker — screen off, notification shade, recents — are deliberately absent.
 * Every one of them needs an `AccessibilityService` on modern Android, which is a feature of its own with its own
 * permission flow; adding members for them here before that exists would be a model with nothing able to perform it.
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
}
