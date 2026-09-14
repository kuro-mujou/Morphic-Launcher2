package inkspire.morphic.data.setup

import android.content.Intent
import inkspire.morphic.data.settings.SetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * **The setup hub's steps that are still unfinished** — read by the settings hub, which lists them, and by HOME's menu,
 * which offers "Finish setup" while there are any.
 *
 * **Derived, never recorded.** Each step's doneness is asked of the subsystem that owns it, and only the user's
 * dismissals are stored. One implementation for both readers, so the two can never disagree about whether the hub is
 * empty.
 */
interface SetupSteps {

    /** The unfinished steps the user has not put away, in [SetupStep] order. Empty when there is nothing left to ask. */
    val pending: Flow<List<SetupStep>>

    /**
     * How to ask to become the home app, or null when there is nothing to ask — because this launcher already is one,
     * or because the device offers no way to choose. [SetupStep.DEFAULT_LAUNCHER] is pending exactly while this is not
     * null, so a row offering it always has something to launch.
     */
    val defaultLauncherRequest: StateFlow<Intent?>

    /**
     * Re-reads whether this launcher holds the home role. **Call it on every resume**: that step is finished in a system
     * dialog that reports nothing back, so being shown again is the only moment the answer can be learned.
     */
    suspend fun refresh()

    /** Puts [step] away for good. Throws for a step that cannot be dismissed. */
    suspend fun dismiss(step: SetupStep)
}
