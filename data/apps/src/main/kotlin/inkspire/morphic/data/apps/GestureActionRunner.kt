package inkspire.morphic.data.apps

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.needsGestureService

/**
 * Performs a [GestureAction] — the one place each kind of action becomes a platform call.
 *
 * **One type because actions fire from two modules**: an item's gesture on home, and a swipe on HOME itself in the
 * shell. A `when` in each would both need the next member added, and the one that was missed would do nothing without
 * saying so.
 */
interface GestureActionRunner {

    /**
     * Runs [action]. Fire-and-forget: an uninstalled app, a withdrawn shortcut or a refusing platform does nothing.
     *
     * **An action that needs Morphic gestures while it is off is reported instead** ([GestureServiceAccess.blocked]),
     * because that is the one failure the user can fix, and the gesture is the only moment that also catches a service
     * switched off after the action was assigned.
     *
     * @param startX where the gesture began across the screen, 0 at the left and 1 at the right, or null for a gesture
     *   whose side means nothing. Only a by-side panel action reads it — see [SystemShade.expand].
     */
    fun run(action: GestureAction, startX: Float?)
}

/** Default [GestureActionRunner], over the commands that do the work. */
internal class DefaultGestureActionRunner(
    private val appLauncher: AppLauncher,
    private val appShortcuts: AppShortcuts,
    private val systemShade: SystemShade,
    private val screenLock: ScreenLock,
    private val gestureService: GestureServiceAccess,
) : GestureActionRunner {

    override fun run(action: GestureAction, startX: Float?) {
        if (action.needsGestureService && !gestureService.isOn) {
            gestureService.reportBlocked(action)
            return
        }
        when (action) {
            is GestureAction.LaunchApp -> appLauncher.launch(action.component)
            is GestureAction.LaunchShortcut -> appShortcuts.start(action.id, action.packageName, action.userSerial)
            is GestureAction.OpenSystemPanel -> systemShade.expand(action.pull, startX)
            GestureAction.LockScreen -> screenLock.lock()
        }
    }
}
