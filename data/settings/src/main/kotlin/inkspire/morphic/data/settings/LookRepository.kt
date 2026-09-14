package inkspire.morphic.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * Applies and captures [Look]s — the one way a whole arrangement enters or leaves the settings store.
 *
 * Its own type beside [SettingsRepository] rather than two more members on it: that interface is a flow and a setter per
 * slice, and a look is the operation that crosses all of them at once.
 */
interface LookRepository {

    /**
     * Writes what [look] carries **in one transaction**, and nothing else: a slice the look is silent on keeps its stored
     * value, and a slice no look may carry is never written, whatever the file holds.
     *
     * **Over an arranged home, icons can move.** A look can shrink a grid; HOME then re-homes what no longer fits onto
     * later pages, and the dock evicts to HOME. Nothing is deleted, but first run — which applies a look before anything is
     * placed — is the only moment it moves nothing.
     */
    suspend fun apply(look: Look)

    /** The look currently in force, called [name]: every slice a look carries, defaults spelled out. */
    suspend fun capture(name: String): Look

    /**
     * The look in force when setup was last started over, or null when setup was never started over or has since
     * finished. The first-run screen offers it first, as the setup the user already had.
     */
    val restartLook: Flow<Look?>

    /**
     * Starts first-run setup over: captures the look in force as [restartLook] and marks setup unfinished, **in one
     * transaction** — so the first-run screen can never open without the user's own setup to offer back, and never on
     * a capture taken after anything changed.
     *
     * Setup's flag is reset whole: the edge hint returns, and the setup hub's dismissed steps with it.
     */
    suspend fun startOver()

    /** Forgets [restartLook] — once setup has finished, when there is nothing left to offer it for. */
    suspend fun clearRestartLook()
}
