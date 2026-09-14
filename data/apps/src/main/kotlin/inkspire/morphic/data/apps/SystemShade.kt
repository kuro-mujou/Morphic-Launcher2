package inkspire.morphic.data.apps

import inkspire.morphic.core.model.ShadePull
import timber.log.Timber

/**
 * Pulls down the system's panels — what [GestureActionRunner] calls for `GestureAction.OpenSystemPanel`.
 *
 * A type of its own, beside [AppInfoOpener] and for its reason: a fire-and-forget side effect on the platform, not
 * access to a store.
 */
interface SystemShade {

    /**
     * Performs [pull]. A refusal is a no-op, logged — never a crash.
     *
     * @param startX where the gesture began across the screen, 0 at the physical left and 1 at the right. Read by
     *   [ShadePull.BY_SIDE] alone; null for a gesture with no side to read. A by-side pull given none — which the picker
     *   never assigns — pulls down plainly rather than picking a side.
     */
    fun expand(pull: ShadePull, startX: Float?)
}

/**
 * Default [SystemShade], entirely through [MorphicGestureService]: its global expand for a plain pull-down, and a
 * replayed swipe on a panel's side for a named panel.
 *
 * **No member asks how the phone arranges its panels.** A swipe on the left opens notifications on a combined shade and
 * a separate one alike; a swipe on the right followed by a second opens quick settings on both — a separate shade on the
 * first, a combined one expanding into them on the second. The global quick-settings action would have been simpler on
 * a combined shade and is not used: RedMagic answers it, like every programmatic expand, with its control center, so it
 * would only be right once the arrangement was known.
 *
 * `internal` so only Koin constructs it — consumers depend on [SystemShade].
 */
internal class PlatformSystemShade : SystemShade {

    override fun expand(pull: ShadePull, startX: Float?) {
        val service = MorphicGestureService.connected
        val opened = service != null && when (pull) {
            ShadePull.PULL_DOWN -> service.pullDownShade()
            ShadePull.NOTIFICATIONS -> service.openNotifications()
            ShadePull.QUICK_SETTINGS -> service.openQuickSettings()
            ShadePull.BY_SIDE -> when {
                startX == null -> service.pullDownShade()
                startX < MIDDLE -> service.openNotifications()
                else -> service.openQuickSettings()
            }
        }
        if (!opened) Timber.w("Could not pull down the system panel as %s", pull)
    }

    private fun MorphicGestureService.openNotifications() = swipeDownFromTop(LEFT_SIDE)

    private fun MorphicGestureService.openQuickSettings() = swipeDownFromTop(RIGHT_SIDE, twice = true)

    private companion object {
        /** Where a replayed swipe lands for each side: a quarter in from the edge, clear of the middle and of One UI's
         *  70% line alike. */
        const val LEFT_SIDE = 0.25f

        /** See [LEFT_SIDE]. */
        const val RIGHT_SIDE = 0.75f

        /** Where [ShadePull.BY_SIDE] divides left from right — the middle, where RedMagic and HyperOS split their shade. */
        const val MIDDLE = 0.5f
    }
}
