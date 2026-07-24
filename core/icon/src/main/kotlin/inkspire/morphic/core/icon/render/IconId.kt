package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.model.ComponentKey

/**
 * The cache key for a baked icon bitmap: the app [component], the exact [layerSet] it was rendered from, and
 * the target [sizePx].
 *
 * Value equality across all three (they are data/value classes all the way down) is what makes the bitmap
 * cache self-invalidating: change the layer set or the size and it is simply a different key, so a stale
 * bitmap can never be returned for a changed icon.
 */
data class IconId(
    val component: ComponentKey,
    val layerSet: IconLayerSet,
    val sizePx: Int,
)
