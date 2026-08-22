package inkspire.morphic.feature.home.gestureaction

import androidx.navigation3.runtime.NavKey
import inkspire.morphic.core.model.ItemGesture
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Choosing what one gesture on one home item does — a full-screen destination of its own.
 *
 * **Declared by `feature:home` and mapped in `app`**, like the container settings beside it: a home item's gesture
 * is home's vocabulary, and `entryProvider` is a mapping rather than a registry.
 *
 * **A sealed pair over the two item kinds that can hold a gesture**, matching `ContainerSettingsRoute`'s shape. An
 * app is named by its component and a folder by its id, and a single key carrying both would leave one field
 * meaningless whichever way it was used. Widgets and containers are not here because they are not offered gestures
 * — a widget owns its own area, and a container is a page of items rather than one.
 *
 * The component travels **flattened**, for the reason the icon studio's route gives: a [NavKey] is serialized into
 * the saved back stack, and `ComponentKey` is not a shape to pin there.
 */
@Serializable
sealed interface GestureActionRoute : NavKey {

    /** Which gesture is being assigned. Shared, since the screen's whole job is the same either way. */
    val gesture: ItemGesture

    /** An app on the grid, the dock, or a folder. */
    @Serializable
    @SerialName("gesture_action_app")
    data class App(val component: String, override val gesture: ItemGesture) : GestureActionRoute

    /** A folder, which carries gestures for the same reason an app does: it is one icon a finger can pull. */
    @Serializable
    @SerialName("gesture_action_folder")
    data class Folder(val folderId: Long, override val gesture: ItemGesture) : GestureActionRoute
}
