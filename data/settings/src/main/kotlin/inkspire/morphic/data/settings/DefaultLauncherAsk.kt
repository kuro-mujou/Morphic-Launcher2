package inkspire.morphic.data.settings

import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

/**
 * When the launcher last asked, unprompted, to be made the default home app — so that it asks again only after a while.
 *
 * **Only the asking is stored, never whether the launcher is the default**, which is derived each time it is needed:
 * a stored answer would go stale the moment the user changed it in the system's own settings.
 *
 * **Kept with the preferences although nobody chose it**, which `SettingsRepository` otherwise refuses: it is a record
 * of what the user has already been asked, the same kind of thing as the onboarding plan's stored dismissals, and
 * nothing could re-derive it.
 *
 * @property lastAskedAtMillis wall-clock time of the last ask, in epoch milliseconds, or null if never asked.
 */
@Serializable
data class DefaultLauncherAsk(val lastAskedAtMillis: Long? = null) {

    /**
     * Whether the launcher may ask again at [nowMillis]: never asked, or [Cooldown] has passed since it last did.
     *
     * **A last ask in the future counts as due.** That happens when the clock is set back, and treating it as recent
     * would silence the ask until the clock caught up — days or years, without a word.
     */
    fun isDue(nowMillis: Long): Boolean {
        val last = lastAskedAtMillis ?: return true
        return nowMillis < last || nowMillis - last >= Cooldown.inWholeMilliseconds
    }

    companion object {
        /** Never asked. */
        val Default = DefaultLauncherAsk()

        /** How long after one ask the launcher waits before the next: long enough not to nag a user who declined. */
        val Cooldown = 3.days
    }
}
