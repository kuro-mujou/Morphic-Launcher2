package inkspire.morphic.core.designsystem.gesture

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction

/**
 * What an assigned [GestureAction] is called wherever one is listed — an item's gesture sheet on home, and the
 * Gestures section in settings. One function for both, so a gesture reads the same in the two places it is set.
 *
 * **Resolved through the app catalog rather than stored**, for the reason home items resolve the same way: a label
 * copied at assignment time goes stale when the app is renamed, and an app that has been uninstalled has no label
 * at all — which is exactly the state worth showing, since a gesture pointing at nothing is otherwise invisible.
 * A shortcut is the exception and carries its own, because resolving one costs a platform query per row.
 */
fun describeGestureAction(action: GestureAction, catalog: Map<ComponentKey, AppInfo>): String =
    when (action) {
        is GestureAction.LaunchApp -> catalog[action.component]?.label ?: MissingApp
        is GestureAction.LaunchShortcut -> action.label
        GestureAction.OpenSystemPanel -> "System panel"
    }

/** An assignment whose app is no longer installed. Named so, rather than left blank, which reads as a bug. */
private const val MissingApp = "App not installed"
