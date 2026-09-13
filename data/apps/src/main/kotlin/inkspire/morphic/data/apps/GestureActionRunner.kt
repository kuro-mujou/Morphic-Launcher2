package inkspire.morphic.data.apps

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadeStyle
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
     * @param shadeStyle how the phone arranges its panels, which decides how a panel action opens. Ignored by the rest.
     */
    fun run(action: GestureAction, shadeStyle: ShadeStyle)
}

/** Default [GestureActionRunner], over the commands that do the work. */
internal class DefaultGestureActionRunner(
    private val appLauncher: AppLauncher,
    private val appShortcuts: AppShortcuts,
    private val systemShade: SystemShade,
    private val screenLock: ScreenLock,
    private val gestureService: GestureServiceAccess,
) : GestureActionRunner {

    override fun run(action: GestureAction, shadeStyle: ShadeStyle) {
        if (action.needsGestureService && !gestureService.isOn) {
            gestureService.reportBlocked(action)
            return
        }
        when (action) {
            is GestureAction.LaunchApp -> appLauncher.launch(action.component)
            is GestureAction.LaunchShortcut -> appShortcuts.start(action.id, action.packageName, action.userSerial)
            is GestureAction.OpenSystemPanel -> systemShade.expand(action.panel, shadeStyle)
            GestureAction.LockScreen -> screenLock.lock()
        }
    }
}
