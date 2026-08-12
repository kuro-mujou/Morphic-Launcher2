package inkspire.morphic.core.model.icon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a layer's content comes from. Persisted inside the layer set, so the [SerialName]s are a stable
 * on-disk contract — rename with care.
 *
 * A layer is one of: nothing yet, the parsed app icon, the app's monochrome layer, an imported image, a flat color,
 * or an installed icon pack's rendering of this app.
 */
@Serializable
sealed interface LayerSource {

    /**
     * No content at all — a layer that has been added but not yet filled.
     *
     * **What a new custom layer starts as.** It used to start as a mid-gray [SolidFill], which is a decision the user
     * did not make: adding a layer would drop an opaque plate into the stack and the icon would change before anything
     * had been chosen. Empty means the insert is visible in the stack and invisible on the canvas, which is the honest
     * split — the layer exists, and what goes in it is the next choice.
     *
     * Draws nothing, by the same path an icon pack that does not cover this app already takes: the resolver returns
     * null and whatever is beneath shows through. So no renderer needed teaching.
     *
     * Legal on the foreground and background too, where it reads as "this layer contributes nothing" — distinct from
     * `IconLayerSpec.visible`, which is a temporary hide the editor toggles rather than a content choice.
     */
    @Serializable
    @SerialName("empty")
    data object Empty : LayerSource

    /** The parsed app-icon content for this layer's role — the foreground or background from `ParsedIcon`. */
    @Serializable
    @SerialName("app_default")
    data object AppDefault : LayerSource

    /**
     * The app's OS monochrome (themed-icon) layer, used *in place of* the foreground. Foreground-only: the platform
     * ships one silhouette, for that slot, so there is no "the app's monochrome background" for this to mean.
     *
     * **It means "monochrome", not "the themed drawable" — the renderer decides which of the two that is.** An app
     * that ships a themed layer gets it; an app that does not gets its foreground drained of color instead. That
     * branch is deliberately *not* the user's to make: whether an app carries one is not something they know, and in
     * the **global** studio it is not even a single answer, since one recipe covers apps that differ. So it resolves
     * per app in `IconLayerResolver`, exactly as [AppDefault] already does.
     *
     * That also corrects what this used to say — "only selectable when the app actually ships a monochrome layer",
     * which was written before the global studio existed and cannot be honored there. The old fallback drew the
     * *unfiltered* foreground, making the choice a silent no-op on every app without a themed layer.
     *
     * **Whichever branch it takes, the result is gray** — the renderer drains the saturation of the app's own themed
     * artwork exactly as it drains the foreground's. The themed slot is *meant* to hold a silhouette, but that is a
     * convention with no enforcement and a fair number of apps ship full-color artwork in it; passing those through
     * untouched inverted the feature, since switching everything to monochrome left precisely those apps in color and
     * so made them the icons that stood out.
     *
     * Distinct from [LayerEffect.Color] with `saturation = 0`, which recolors whatever a layer already holds: this
     * one swaps in different artwork *and* guarantees it is gray. Both still compose — a tint set on this layer
     * applies after the drain, and silhouette plus tint is the themed-icon recipe.
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
