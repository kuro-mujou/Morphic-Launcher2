package inkspire.morphic.core.icon.render

import android.graphics.drawable.Drawable
import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.icon.layer.IconLayerSpec
import inkspire.morphic.core.icon.layer.LayerRole
import inkspire.morphic.core.icon.layer.LayerSource
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
     * @param customImage decodes a [LayerSource.CustomImage] path to a drawable; returns `null` if missing.
     */
    fun resolve(
        layerSet: IconLayerSet,
        icon: ParsedIcon,
        customImage: (path: String) -> Drawable?,
    ): List<ResolvedLayer> =
        layerSet.layers
            .filter { it.visible }
            .mapNotNull { spec ->
                spec.resolveContent(icon, customImage)?.let { content -> ResolvedLayer(content, spec) }
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
): ParsedLayer? = when (val src = source) {
    LayerSource.AppDefault -> when (role) {
        LayerRole.FOREGROUND -> icon.foreground
        LayerRole.BACKGROUND -> icon.background
        LayerRole.CUSTOM -> null
    }

    LayerSource.AppDefaultMonochrome -> icon.monochrome ?: icon.foreground

    is LayerSource.SolidFill -> ParsedLayer.Color(src.argb)

    is LayerSource.CustomImage -> customImage(src.path)?.let { ParsedLayer.Image(it) }
}
