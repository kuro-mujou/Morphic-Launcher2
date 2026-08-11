package inkspire.morphic.core.icon.render

import android.graphics.drawable.Drawable
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
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
            .mapNotNull { spec ->
                spec.resolveContent(icon, customImage, packImage)?.let { ResolvedLayer(it, spec) }
            }
}

/**
 * The concrete content a spec's [LayerSource] points at, or `null` when there is nothing to draw. Monochrome
 * falls back to the foreground if the app exposes no monochrome layer; `AppDefault` is meaningless on a custom
 * layer, so that combination resolves to nothing.
 */
private fun IconLayerSpec.resolveContent(
    icon: ParsedIcon,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
): ParsedLayer? = when (val src = source) {
    LayerSource.AppDefault -> when (role) {
        LayerRole.FOREGROUND -> icon.foreground
        LayerRole.BACKGROUND -> icon.background
        LayerRole.CUSTOM -> null
    }

    LayerSource.AppDefaultMonochrome -> icon.monochrome ?: icon.foreground

    is LayerSource.SolidFill -> ParsedLayer.Color(src.argb)

    is LayerSource.CustomImage -> customImage(src.path)?.let { ParsedLayer.Image(it) }

    is LayerSource.IconPack -> packImage(src.packPackage, src.drawableName)?.let { ParsedLayer.Image(it) }
}
