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
 * **A sealed type over what can hold a gesture — the two item kinds, HOME, and part of a widget**, matching
 * `ContainerSettingsRoute`'s shape. An app is named by its component and a folder by its id, and a single key carrying
 * both would leave one field meaningless whichever way it was used. A whole widget and a container are not here: a
 * widget owns its own area, and a container is a page of items rather than one. What a widget offers instead is a tap
 * on one of its parts, [WidgetTap], chosen from its studio.
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

    /** A double tap on HOME's empty space, chosen from the Gestures section in settings. */
    @Serializable
    @SerialName("gesture_action_home_double_tap")
    data object HomeDoubleTap : GestureActionRoute

    /** A tap on one part of one of the launcher's own widgets, chosen from its studio. */
    @Serializable
    @SerialName("gesture_action_widget_tap")
    data class WidgetTap(val widgetId: Long, val path: List<Int>) : GestureActionRoute
}
