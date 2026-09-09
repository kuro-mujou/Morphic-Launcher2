package inkspire.morphic.data.settings

import inkspire.morphic.core.model.RotationMode
import kotlinx.serialization.Serializable

/**
 * **What the launcher does when the device turns or folds.**
 *
 * Its own slice rather than a field elsewhere, on [SurfacePaging]'s reasoning: [SurfaceMetrics] is per-grid *sizes*,
 * every one of them keyed `slot × device`, and nothing here is a size or belongs to a grid. What these settings share
 * with each other is the trigger — a posture change — which is a coherent enough concern to name.
 *
 * @property rotation which orientations the launcher allows itself to be drawn in.
 */
@Serializable
data class OrientationSettings(
    val rotation: RotationMode = RotationMode.AUTO,
) {
    companion object {
        /** Follow the device, which is what a launcher that has never been configured should do. */
        val Default = OrientationSettings()
    }
}
