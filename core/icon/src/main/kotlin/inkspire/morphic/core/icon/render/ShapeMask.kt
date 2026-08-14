package inkspire.morphic.core.icon.render

import android.graphics.Matrix
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.ShapeAnchor

/**
 * Where a layer's shape silhouette is drawn — the fifth thing the two render paths share, and it exists for the
 * reason the other four do.
 *
 * [LayerTransform] pins down where a layer's *content* sits, [LayerFilter] what color it comes out, [LayerGradient]
 * which way an angle runs; this pins down where the mask that trims it sits. An icon that is trimmed correctly in
 * the editor and wrongly on the home screen is the bug neither renderer can show you, so the arithmetic happens
 * once, here, and each path only applies the answer with its own drawing API.
 *
 * **The whole idea is that a content-anchored shape goes through the same matrix its content does.** That is not an
 * optimization, it is what makes the two provably agree: the silhouette cannot drift off the artwork under zoom,
 * rotation or an offset, because it is not being positioned alongside the artwork — it is being positioned *in the
 * artwork's own frame*, and the layer transform then moves both together. Anything computed to "match" the content
 * would be a second derivation to keep in step, which is the shape of every bug this file exists to prevent.
 *
 * **The fit is split from the matrix that carries it**, the same split [inkspire.morphic.core.icon.parse.LegacyBackground]
 * and [inkspire.morphic.core.icon.parse.ContentMetrics] make against the rasterizing that feeds them, and for the
 * same reason: [inkFit] is the decision and is pure, so it is unit-testable without an emulator, while [matrixOf]
 * is assembly against an Android type that no JVM test can exercise honestly.
 */
object ShapeMask {

    /**
     * The square the shape is drawn into, in fractions of the icon's box — the frame [ShapeAnchor.CONTENT] fits the
     * silhouette to, before the layer's transform carries it.
     *
     * @property scale the square's side as a fraction of the box. 1 is the box itself.
     * @property centerX where that square is centered, 0.5 being the middle of the box.
     */
    data class InkFit(val scale: Float, val centerX: Float, val centerY: Float) {

        /** True when this is the box itself, so there is nothing to fit and a caller can skip the step. */
        val isBox: Boolean get() = scale == 1f && centerX == 0.5f && centerY == 0.5f

        companion object {
            /** The whole box — what unmeasured content falls back to. */
            val Box = InkFit(scale = 1f, centerX = 0.5f, centerY = 0.5f)
        }
    }

    /**
     * The square [content]'s ink occupies, or [InkFit.Box] when there is nothing measured to fit to.
     *
     * **The ink's bounding *square*, never its rectangle.** Side is `ContentMetrics.longestSide` — so a wide logo
     * gets a frame as wide as it is, not one squashed to its height. Stretching the silhouette to the rectangle
     * would turn a circle into an ellipse and a hexagon into something that is not a hexagon, which is the one
     * thing a shape mask must not do; every built-in is authored square and comes out square here.
     *
     * **Unmeasured content is the box, and that is a degrade rather than a refusal.** Only the app's own artwork is
     * measured — see [IconLayerResolver]'s note on why measurement and normalization share a scope — so a pack
     * drawable, an imported image and a flat fill arrive with no metrics. Falling back to the box keeps the anchor
     * doing its other half (following the transform) rather than making the control silently inert on those layers.
     * A zero-width measurement takes the same path: it would divide the silhouette to nothing.
     */
    fun inkFit(content: ParsedLayer): InkFit {
        val metrics = (content as? ParsedLayer.Image)?.metrics ?: return InkFit.Box
        if (metrics.longestSide <= 0f) return InkFit.Box
        return InkFit(scale = metrics.longestSide, centerX = metrics.centerX, centerY = metrics.centerY)
    }

    /**
     * The matrix to draw [spec]'s shape drawable under, given the [content] that layer resolved to and a square box
     * of [sizePx] — or `null` when the shape is drawn plainly at box size, which is [ShapeAnchor.BOX] and so the
     * overwhelmingly common case.
     *
     * Returning `null` rather than an identity matrix keeps the untouched path free of matrix work entirely, the
     * same bargain [LayerTransform.isIdentity] makes one file over.
     *
     * Two steps, for [ShapeAnchor.CONTENT]: the [inkFit] above, then the layer's own transform **post**-concatenated
     * so it applies *after* the fit — matching the order the content itself goes through, which is drawn at box size
     * and then transformed. That ordering is the whole correctness argument, so it is worth not swapping for
     * `preConcat` on the grounds that it reads better.
     */
    fun matrixOf(spec: IconLayerSpec, content: ParsedLayer, sizePx: Int): Matrix? {
        if (spec.shapeAnchor == ShapeAnchor.BOX) return null

        val fit = inkFit(content)
        val center = sizePx / 2f

        return Matrix().apply {
            if (!fit.isBox) {
                // About the box center, then displaced to the ink's — the same two-step `LayerTransform.toMatrix`
                // uses, so a reader comparing the two is comparing like with like.
                postScale(fit.scale, fit.scale, center, center)
                postTranslate((fit.centerX - 0.5f) * sizePx, (fit.centerY - 0.5f) * sizePx)
            }
            postConcat(LayerTransform.of(spec, sizePx).toMatrix(sizePx))
        }
    }
}
