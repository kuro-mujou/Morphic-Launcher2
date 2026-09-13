package inkspire.morphic.data.apps

import inkspire.morphic.core.model.ShadePanel
import inkspire.morphic.core.model.ShadeStyle
import timber.log.Timber

/**
 * Opens one of the system's pull-down panels — what [GestureActionRunner] calls for `GestureAction.OpenSystemPanel`.
 *
 * A type of its own, beside [AppInfoOpener] and for its reason: a fire-and-forget side effect on the platform, not
 * access to a store.
 */
interface SystemShade {

    /** Opens [panel] the way a phone arranged as [style] shows it. A refusal is a no-op, logged — never a crash. */
    fun expand(panel: ShadePanel, style: ShadeStyle)
}

/**
 * Default [SystemShade], entirely through [MorphicGestureService]: its global action for a combined shade, and a
 * replayed swipe on the panel's side for a separate one — the service's own KDoc says why each.
 *
 * **No platform call behind it.** `StatusBarManager`'s hidden expand methods need no service on stock Android, but as a
 * fallback they open the control center on RedMagic whichever panel was asked for — so with the service off, the
 * runner asks for it rather than letting this guess.
 *
 * `internal` so only Koin constructs it — consumers depend on [SystemShade].
 */
internal class PlatformSystemShade : SystemShade {

    override fun expand(panel: ShadePanel, style: ShadeStyle) {
        val service = MorphicGestureService.connected
        val opened = when {
            service == null -> false
            style == ShadeStyle.COMBINED -> service.openPanel(panel)
            else -> service.swipeDownFromTop(if (panel == ShadePanel.NOTIFICATIONS) LEFT_SIDE else RIGHT_SIDE)
        }
        if (!opened) Timber.w("Could not open the system %s panel", panel)
    }

    private companion object {
        /** Where a replayed swipe lands for each side: a quarter in from the edge, clear of the middle and of One UI's
         *  70% line alike. */
        const val LEFT_SIDE = 0.25f

        /** See [LEFT_SIDE]. */
        const val RIGHT_SIDE = 0.75f
    }
}
