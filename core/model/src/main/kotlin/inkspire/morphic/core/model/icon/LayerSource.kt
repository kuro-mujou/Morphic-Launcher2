package inkspire.morphic.core.model.icon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a layer's content comes from. Persisted inside the layer set, so the [SerialName]s are a stable
 * on-disk contract — rename with care.
 *
 * A layer is one of: the parsed app icon, the app's monochrome layer, an imported image, a flat color, or an
 * installed icon pack's rendering of this app.
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

    /**
     * This app's icon as drawn by the installed icon pack [packPackage].
     *
     * **A source, not a mode**, which is what makes "apply a pack to everything" not a feature: it is setting the
     * global default's foreground source, and it then goes through the same commit, the same cache key and the
     * same invalidation as any other edit. A pack picked for *one* app is the same thing in that app's own
     * recipe. Either way, **every decoration layer the user added is untouched** — a pack only ever occupies the
     * slot it was put in.
     *
     * Resolves to nothing when the pack does not cover this app, which is ordinary rather than exceptional: no
     * pack themes everything. The layer then draws nothing and whatever is beneath shows through.
     *
     * @property drawableName one specific drawable in the pack, or `null` to let its `appfilter.xml` decide which
     *   one belongs to this app. **Only meaningful for a single app**: the global default is inherited by every
     *   app, so a name there would give all of them the same picture — which is why the studio offers the browser
     *   in individual mode alone. Added as a defaulted field, so recipes written before it existed still read.
     */
    @Serializable
    @SerialName("icon_pack")
    data class IconPack(
        val packPackage: String,
        val drawableName: String? = null,
    ) : LayerSource

    /** A flat color fill; [argb] is a packed ARGB color (e.g. a solid background for a legacy icon). */
    @Serializable
    @SerialName("solid_fill")
    data class SolidFill(val argb: Int) : LayerSource
}

/**
 * The identity of the artwork an [LayerSource.IconPack] layer draws — the pack **and** the chosen drawable.
 *
 * Both, because two layers may name the same pack and different drawables, so keying a resolved-artwork cache on
 * the package alone would have the second layer show the first one's icon.
 */
val LayerSource.IconPack.key: String get() = "$packPackage/${drawableName ?: ""}"
