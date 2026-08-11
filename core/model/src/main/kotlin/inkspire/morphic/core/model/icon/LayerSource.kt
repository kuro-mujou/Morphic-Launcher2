package inkspire.morphic.core.icon.layer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a layer's content comes from. Persisted inside the layer set, so the [SerialName]s are a stable
 * on-disk contract — rename with care.
 *
 * Icon packs are intentionally absent for now (deferred), so a layer is one of: the parsed app icon, the
 * app's monochrome layer, an imported image, or a flat colour.
 */
@Serializable
sealed interface LayerSource {

    /** The parsed app-icon content for this layer's role — the foreground or background from `ParsedIcon`. */
    @Serializable
    @SerialName("app_default")
    data object AppDefault : LayerSource

    /**
     * The app's OS monochrome (themed-icon) layer, used *in place of* the foreground. Foreground-only, and
     * only selectable when the app actually ships a monochrome layer — this is the "use the monochrome icon"
     * side of the foreground source toggle (the other side is [AppDefault] with a monochrome effect applied).
     */
    @Serializable
    @SerialName("app_default_monochrome")
    data object AppDefaultMonochrome : LayerSource

    /** A user-imported image stored at [path]. */
    @Serializable
    @SerialName("custom_image")
    data class CustomImage(val path: String) : LayerSource

    /** A flat colour fill; [argb] is a packed ARGB colour (e.g. a solid background for a legacy icon). */
    @Serializable
    @SerialName("solid_fill")
    data class SolidFill(val argb: Int) : LayerSource
}
