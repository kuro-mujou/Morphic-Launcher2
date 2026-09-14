package inkspire.morphic.data.settings

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
     * **Safe only before HOME has been arranged.** A look can shrink a grid, and a shrink leaves placed items with nowhere
     * to sit unless `data:layout` re-homes them, which this write does not do. First run applies a look before anything
     * is placed; applying one over an arranged home needs that companion write as well.
     */
    suspend fun apply(look: Look)

    /** The look currently in force, called [name]: every slice a look carries, defaults spelled out. */
    suspend fun capture(name: String): Look
}
