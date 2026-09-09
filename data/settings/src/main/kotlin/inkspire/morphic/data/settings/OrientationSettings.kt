package inkspire.morphic.data.settings

import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.core.model.SyncMode
import kotlinx.serialization.Serializable

/**
 * **What the launcher does when the device turns or folds.**
 *
 * Its own slice rather than a field elsewhere, on [SurfacePaging]'s reasoning: [SurfaceMetrics] is per-grid *sizes*,
 * every one of them keyed `slot × device`, and nothing here is a size or belongs to a grid. What these settings share
 * with each other is the trigger — a posture change — which is a coherent enough concern to name.
 *
 * @property rotation which orientations the launcher allows itself to be drawn in.
 * @property independentLayout whether landscape keeps a layout of its own. Off by default: one arrangement, shown
 *   re-laid whichever way the device is held, which is what a user who never thinks about this should get. On, the
 *   two stop being kept in step and each is edited on its own.
 * @property syncMode how a layout is carried between the two orientations while they *are* kept in step. Read only
 *   on a form factor whose board turns, and only while [independentLayout] is off — the control is absent
 *   otherwise, so a stored value simply sits unused rather than meaning something invisible.
 */
@Serializable
data class OrientationSettings(
    val rotation: RotationMode = RotationMode.AUTO,
    val independentLayout: Boolean = false,
    val syncMode: SyncMode = SyncMode.REFLOW,
) {
    companion object {
        /** Follow the device, which is what a launcher that has never been configured should do. */
        val Default = OrientationSettings()
    }
}
