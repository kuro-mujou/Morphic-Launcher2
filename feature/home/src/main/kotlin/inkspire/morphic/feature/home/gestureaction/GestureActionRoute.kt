package inkspire.morphic.feature.home.gestureaction

import androidx.navigation3.runtime.NavKey
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.SwipeDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Choosing what one gesture does — on a home item, or a swipe on HOME itself — a full-screen destination of its own.
 *
 * **Declared by `feature:home` and mapped in `app`**, like the container settings beside it: a home item's gesture
 * is home's vocabulary, and `entryProvider` is a mapping rather than a registry.
 *
 * **A sealed type over what can hold a gesture — the two item kinds, and HOME**, matching `ContainerSettingsRoute`'s shape. An
 * app is named by its component and a folder by its id, and a single key carrying both would leave one field
 * meaningless whichever way it was used. Widgets and containers are not here because they are not offered gestures
 * — a widget owns its own area, and a container is a page of items rather than one.
 *
 * The component travels **flattened**, for the reason the icon studio's route gives: a [NavKey] is serialized into
 * the saved back stack, and `ComponentKey` is not a shape to pin there.
 */
@Serializable
sealed interface GestureActionRoute : NavKey {

    /** An app on the grid, the dock, or a folder. */
    @Serializable
    @SerialName("gesture_action_app")
    data class App(val component: String, val gesture: ItemGesture) : GestureActionRoute

    /** A folder, which carries gestures for the same reason an app does: it is one icon a finger can pull. */
    @Serializable
    @SerialName("gesture_action_folder")
    data class Folder(val folderId: Long, val gesture: ItemGesture) : GestureActionRoute

    /** A swipe on HOME itself, chosen from the Gestures section in settings rather than from an item's sheet. */
    @Serializable
    @SerialName("gesture_action_home_swipe")
    data class HomeSwipe(val direction: SwipeDirection) : GestureActionRoute
}
