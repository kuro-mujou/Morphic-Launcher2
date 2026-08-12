package inkspire.morphic.core.icon.render

import android.graphics.Matrix
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Where one layer's content sits inside the icon's square box, in pixels — the spec's normalized transform
 * resolved against a concrete size.
 *
 * **This exists to be the single interpretation of a layer's transform, because there are two renderers.** The
 * baked path composites to a bitmap for display; the editor renders the same layers live as Compose nodes so a
 * slider responds per frame. They use different graphics APIs, and if they read [IconLayerSpec.offsetX] even
 * slightly differently the result is the worst kind of bug: an icon that looks right while you are editing it and
 * wrong everywhere else, which the editor by definition cannot show you. So the arithmetic happens once, here, and
 * each path only has to apply the answer.
 *
 * The conventions this pins down, all of which are choices rather than facts:
 * - **Offsets are fractions of the box**, not pixels — so a set moved 10% left looks the same at every bake size.
 * - **Positive [translateYPx] is down**, matching both Android's canvas and Compose's.
 * - **Zoom and rotation are about the box's center**, not its origin, so an item scales in place.
 * - **[rotationDegrees] is clockwise.**
 */
data class LayerTransform(
    val zoom: Float,
    val rotationDegrees: Float,
    val translateXPx: Float,
    val translateYPx: Float,
) {

    /**
     * True when this transform would not move a single pixel, so a caller can skip the work of applying it.
     *
     * Worth having because it is the common case by a wide margin: every layer of an unedited icon is identity,
     * which is every icon on screen for a user who has never opened the studio.
     */
    val isIdentity: Boolean
        get() = zoom == 1f && rotationDegrees == 0f && translateXPx == 0f && translateYPx == 0f

    /**
     * The same transform as an Android [Matrix], for the baked path.
     *
     * Order matters and mirrors the field order: scale about the center, then rotate about the center, then
     * translate. Compose's `graphicsLayer` composes its scale, rotation and translation the same way about
     * `TransformOrigin.Center`, which is what lets the live path consume this without converting anything.
     */
    fun toMatrix(sizePx: Int): Matrix {
        val center = sizePx / 2f
        return Matrix().apply {
            postScale(zoom, zoom, center, center)
            postRotate(rotationDegrees, center, center)
            postTranslate(translateXPx, translateYPx)
        }
    }

    companion object {
        /** Resolves [spec]'s normalized transform against a square box of [sizePx]. */
        fun of(spec: IconLayerSpec, sizePx: Int): LayerTransform = LayerTransform(
            zoom = spec.zoom,
            rotationDegrees = spec.rotation,
            translateXPx = spec.offsetX * sizePx,
            translateYPx = spec.offsetY * sizePx,
        )
    }
}
