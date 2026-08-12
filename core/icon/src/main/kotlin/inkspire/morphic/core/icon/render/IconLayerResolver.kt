package inkspire.morphic.core.icon.render

import android.graphics.drawable.Drawable
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer

/**
 * Turns an [IconLayerSet] + the [ParsedIcon] for one app into the ordered, concrete layers the compositor
 * draws. Pure and testable: the one impure step — decoding a custom-image path — is injected as [customImage]
 * rather than done here, so this needs no `Context` or file I/O.
 */
class IconLayerResolver {

    /**
     * Resolves the visible layers of [layerSet] bottom→top against [icon]. A layer that resolves to no
     * content is dropped from the result (an empty background, or a custom image whose file is gone) — it
     * still exists in the set for the editor, it just contributes nothing to the composite.
     *
     * Both image lookups are **injected and pre-bound**: `packImage` already knows which app it is resolving
     * for, because this class deliberately does not. What it keeps is the `when` over [LayerSource] — one place
     * that decides what each source *means*, rather than a copy of that decision in each renderer.
     *
     * @param customImage decodes a [LayerSource.CustomImage] path to a drawable; returns `null` if missing.
     * @param packImage draws this app from an installed icon pack; `null` when the pack does not cover it, which
     *   is ordinary rather than exceptional.
     */
    fun resolve(
        layerSet: IconLayerSet,
        icon: ParsedIcon,
        customImage: (path: String) -> Drawable?,
        packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
    ): List<ResolvedLayer> =
        layerSet.layers
            .filter { it.visible }
            .mapNotNull { spec -> spec.resolveLayer(icon, customImage, packImage) }
}

/**
 * One spec against one app: the content its [LayerSource] points at, paired with the spec to draw it by — or `null`
 * when there is nothing to draw. `AppDefault` is meaningless on a custom layer, so that combination resolves to
 * nothing, as does an [LayerSource.Empty] layer and a pack that does not cover this app.
 *
 * **It returns a spec as well as content because one source rewrites it** — see the monochrome arm. Every other arm
 * hands back the spec it was given.
 */
private fun IconLayerSpec.resolveLayer(
    icon: ParsedIcon,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
): ResolvedLayer? = when (val src = source) {
    // An unfilled layer, which draws nothing — the same answer as a pack that does not cover this app, and reached the
    // same way, so both render paths already handle it.
    LayerSource.Empty -> null

    LayerSource.AppDefault -> when (role) {
        LayerRole.FOREGROUND -> icon.foreground
        LayerRole.BACKGROUND -> icon.background
        LayerRole.CUSTOM -> null
    }?.let { ResolvedLayer(it, this) }

    // **The one source whose meaning depends on what the app shipped, and deciding it here is the point.** Whether an
    // app carries a themed-icon layer is not something the user knows, and in the *global* studio it is not even one
    // answer — a single recipe covers apps that differ. So the branch belongs where per-app differences already live,
    // exactly as `AppDefault` resolves to different artwork per app.
    LayerSource.AppDefaultMonochrome -> when (val mono = icon.monochrome) {
        // No themed layer to swap in, so the foreground is drained of color instead — the best available
        // approximation, and the whole reason this used to be a visible no-op: it fell back to the *unfiltered*
        // foreground, so choosing monochrome on such an app changed nothing at all.
        //
        // Folded into the spec rather than baked into the content, which keeps it composable with the layer's own
        // recoloring: a tint the user set survives, and silhouette-plus-tint is the themed-icon recipe.
        null -> ResolvedLayer(icon.foreground, withColor(monochromeFallbackColor()))

        // The app ships one: draw it as it is. Typically a solid-alpha silhouette meant to be tinted, which is what
        // this layer's own tint control is for — so nothing is applied on its behalf here.
        else -> ResolvedLayer(mono, this)
    }

    is LayerSource.SolidFill -> ResolvedLayer(ParsedLayer.Color(src.argb), this)

    is LayerSource.CustomImage -> customImage(src.path)?.let { ResolvedLayer(ParsedLayer.Image(it), this) }

    is LayerSource.IconPack ->
        packImage(src.packPackage, src.drawableName)?.let { ResolvedLayer(ParsedLayer.Image(it), this) }
}

/**
 * The recoloring for a monochrome layer on an app that ships **no** themed artwork: the layer's own color effect,
 * drained of saturation.
 *
 * **And downgraded from [TintMode.SOLID], which is the one thing here that overrides the user.** A solid tint keeps
 * only alpha, which is exactly right over a themed silhouette and disastrous over an ordinary foreground: an adaptive
 * foreground's alpha is usually a large blob, so the icon would come out as a featureless colored splodge. The two are
 * the same setting reaching two different kinds of artwork, and only this layer knows which one it just handed back.
 *
 * It matters most in the **global** studio, which is the whole point of that setting — "make every icon a flat white
 * glyph" is one edit there, and without this it would silently produce blobs for every app without a themed layer.
 * Downgrading gives those apps a grayscale icon instead, which is the same answer they got before a tint was involved.
 *
 * The tint is *kept*, only its mode changes: a multiply over grayscale is the tinted-grayscale recipe, so the color
 * the user picked still shows.
 */
private fun IconLayerSpec.monochromeFallbackColor(): LayerEffect.Color =
    (color ?: LayerEffect.Color()).copy(saturation = 0f, tintMode = TintMode.MULTIPLY)
