package inkspire.morphic.data.apps

import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ShadeRequest

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
     * @param shade what [GestureAction.OpenSystemPanel] needs, built by the caller — only it knows where the swipe
     *   began. Ignored by every other action.
     */
    fun run(action: GestureAction, shade: ShadeRequest)
}

/** Default [GestureActionRunner], over the three commands that do the work. */
internal class DefaultGestureActionRunner(
    private val appLauncher: AppLauncher,
    private val appShortcuts: AppShortcuts,
    private val systemShade: SystemShade,
) : GestureActionRunner {

    override fun run(action: GestureAction, shade: ShadeRequest) {
        when (action) {
            is GestureAction.LaunchApp -> appLauncher.launch(action.component)
            is GestureAction.LaunchShortcut -> appShortcuts.start(action.id, action.packageName, action.userSerial)
            GestureAction.OpenSystemPanel -> systemShade.expand(shade)
        }
    }
}
