package inkspire.morphic.core.icon.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.BloomFalloff
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withMatrix

/**
 * Composites an [IconLayerSet] + the app's [ParsedIcon] into one square [Bitmap] of `sizePx` — the baked icon
 * shown on the home screen and other surfaces.
 *
 * Each layer is drawn into its own bitmap — so its transform and shape mask apply in isolation — and is then
 * composited onto the output through one paint carrying its opacity, blend mode and color matrix. Compositing is
 * **synchronous and CPU/heavy** (bitmap allocation + drawing); callers run it off the main thread and cache the
 * result by `IconId`.
 *
 * **Why the paint is applied at the join and not while the content is drawn**: a blend mode has to mean "against
 * everything beneath this layer", and inside the layer's own bitmap there is nothing beneath. The live path
 * ([inkspire.morphic.core.icon.compose.IconLayerStack]) composes the same three into one paint for the same
 * reason, and shares [LayerFilter]'s matrix arithmetic so the two cannot disagree about a tint.
 *
 * A layer's effects are applied **after its shape mask**, so an overlay colors the shaped silhouette rather than
 * the square it was cut from — the live path orders it the same way, by which node carries which modifier.
 *
 * Still deferred: a **shadow** effect (not additive — it could not be matched in the live path below API 31; see
 * the plan's S6 note), and adaptive-layer overshoot scaling (`AppDefault` layers draw to the full box; expect to
 * tune on device).
 */
class IconRenderer(
    private val context: Context,
    private val resolver: IconLayerResolver = IconLayerResolver(),
) {
    /** Keeps a layer's pixels only where the shape silhouette is opaque. */
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * Renders the visible layers of [layerSet] for [icon] into one `sizePx` × `sizePx` bitmap.
     *
     * @param packImage this app's artwork from an installed icon pack, pre-bound to the component by the caller —
     *   this class draws pixels and has no business knowing which app they belong to. Defaults to nothing, which
     *   is what a recipe with no pack layer needs and what the harness passes.
     */
    fun render(
        icon: ParsedIcon,
        layerSet: IconLayerSet,
        sizePx: Int,
        packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
    ): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)

        resolver.resolve(layerSet, icon, ::decodeCustomImage, packImage).forEach { layer ->
            val layerBitmap = renderLayer(layer, sizePx)
            // Opacity, blend and color are applied **as the layer joins the stack**, not while its content is
            // drawn — which is what makes a blend mode mean "against everything beneath" rather than "against the
            // one bitmap I am in". The live path composes the same three into one paint for the same reason.
            canvas.drawBitmap(layerBitmap, 0f, 0f, compositePaint(layer.spec))
            layerBitmap.recycle()
        }

        // **The set's own effects, over the finished picture** — the same pipeline a layer's run through, which is
        // the point of them being the same type. What the composite does not have is a transform or measured ink, so
        // anything anchored to content falls back to the box, exactly as an unmeasured layer's does.
        return applyEffects(output, layerSet.activeEffects, ShapeMask.InkFit.Box, LayerTransform.Identity, sizePx)
    }

    /**
     * Alpha and blend mode for one layer, or `null` when it composites plainly.
     *
     * **The color matrix used to ride here and now does not.** Recoloring is an effect, so it belongs in the layer's
     * own pipeline where its position relative to the other effects is the user's; opacity and blend stay because
     * they describe how the finished layer *joins the stack*, which is not something an effect can be ordered
     * against. Moving it changes nothing on a layer that only recolors — a color filter is per-pixel, so filtering
     * into the buffer and then compositing gives the same pixels as compositing through the filter.
     */
    private fun compositePaint(spec: IconLayerSpec): Paint? {
        val mode = spec.blend.porterDuff()
        if (spec.opacity == 1f && mode == null) return null

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (spec.opacity.coerceIn(0f, 1f) * 255).toInt()
            mode?.let { xfermode = PorterDuffXfermode(it) }
        }
    }

    /**
     * Draws one resolved layer into its own bitmap: content, transform, shape mask, then its effects **in order**.
     *
     * The order is `IconLayerSpec.activeEffects`' order, which is the list's, which is the user's. The live path
     * expresses the same sequence by nesting modifiers in reverse — see `IconLayerStack`, and expect to check both
     * if either is touched.
     */
    private fun renderLayer(layer: ResolvedLayer, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val transform = LayerTransform.of(layer.spec, sizePx)

        canvas.withMatrix(transform.toMatrix(sizePx)) {
            drawContent(canvas, layer.content, sizePx)
        }

        // Masked after the matrix is restored, because where the silhouette goes is [ShapeMask]'s answer and not
        // the content's: anchored to the box it stays put while the content moves under it, anchored to the content
        // it is handed that same transform to go through.
        layer.spec.shape?.let {
            applyShapeMask(canvas, it, sizePx, ShapeMask.matrixOf(layer.spec, layer.content, sizePx))
        }

        // **Effects come after the mask**, so one colors or covers the shaped silhouette rather than the square it
        // was cut from — which is what made a bloom an overlay rather than a rectangle, and holds for every effect
        // that follows it.
        //
        // Measured once for the whole pipeline rather than per effect: it is a property of the layer's artwork, and
        // every effect that can be anchored to content is anchored to the *same* square a shape mask would use.
        return applyEffects(bitmap, layer.spec.activeEffects, ShapeMask.inkFit(layer.content), transform, sizePx)
    }

    /**
     * Runs [effects] over [bitmap] in order, returning whatever the pipeline ends up holding.
     *
     * **One function for a layer and for the whole icon**, which is what makes an icon-wide effect cost a call rather
     * than a second implementation: the composite is a thing with pixels, so the only difference is what it is
     * placed against — [ShapeMask.InkFit.Box] and [LayerTransform.Identity], since it has neither ink of its own to
     * measure nor a transform to follow.
     *
     * **Two kinds of effect, and the difference is a buffer.** An *overlay* paints onto what is already there and
     * needs nothing; a *filter* transforms pixels that have already been drawn, which a canvas cannot do in place —
     * so it costs one bitmap. Keeping that visible here is the point: it is the honest cost of the pipeline, and the
     * shape every effect added later has to declare itself against.
     *
     * Takes ownership of [bitmap]: a filter recycles it and hands back its replacement, so the caller must use the
     * return value and must not touch what it passed in.
     */
    private fun applyEffects(
        bitmap: Bitmap,
        effects: List<LayerEffect>,
        inkFit: ShapeMask.InkFit,
        transform: LayerTransform,
        sizePx: Int,
    ): Bitmap {
        var current = bitmap
        var canvas = Canvas(current)

        for (effect in effects) {
            val matrix = when (effect) {
                is LayerEffect.Bloom -> {
                    applyBloom(canvas, effect, LayerGradient.frameOf(effect, inkFit, transform, sizePx), sizePx)
                    null
                }

                is LayerEffect.Gloss -> {
                    val frame = LayerGradient.frameOf(effect.anchor, inkFit, transform, sizePx)
                    applyGloss(canvas, effect, LayerGradient.sweep(frame, effect.angleDegrees, effect.curve), sizePx)
                    null
                }

                is LayerEffect.Pattern -> {
                    applyPattern(canvas, effect, sizePx)
                    null
                }

                is LayerEffect.Color -> LayerFilter.colorMatrixOf(effect)
                // Null for an id this build does not know, which then draws nothing rather than failing.
                is LayerEffect.Filter -> IconFilters.matrixOrNull(effect.filter)
            }

            if (matrix != null) {
                val filtered = createBitmap(sizePx, sizePx)
                Canvas(filtered).drawBitmap(
                    current,
                    0f,
                    0f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
                    },
                )
                current.recycle()
                current = filtered
                canvas = Canvas(current)
            }
        }
        return current
    }

    /**
     * Paints [bloom] over the layer, clipped to what the layer has already drawn.
     *
     * Both falloffs go through the same paint and the same rectangle, so only the shader differs — and *where* each
     * shader sits is [frame]'s answer rather than this method's, which is what keeps the live path drawing the same
     * disc in the same place.
     */
    private fun applyBloom(canvas: Canvas, bloom: LayerEffect.Bloom, frame: LayerGradient.Frame, sizePx: Int) {
        val fade = LayerGradient.fadeOut(bloom.argb)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = when (bloom.falloff) {
                BloomFalloff.LINEAR -> {
                    val (x0, y0, x1, y1) = LayerGradient.endpoints(frame, bloom.angleDegrees).toList()
                    LinearGradient(x0, y0, x1, y1, bloom.argb, fade, Shader.TileMode.CLAMP)
                }

                BloomFalloff.RADIAL -> {
                    val radial = LayerGradient.radial(frame, bloom.radius)
                    RadialGradient(
                        radial.centerX,
                        radial.centerY,
                        radial.radiusPx,
                        bloom.argb,
                        fade,
                        Shader.TileMode.CLAMP,
                    )
                }
            }
            // SRC_ATOP is what makes this an overlay rather than a rectangle: it keeps the layer's own alpha, so
            // the bloom colors the artwork and stops at its edge. The shader's own alpha then decides how much of
            // the artwork survives at each pixel, which is what makes the light *fade* rather than replace.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            alpha = (bloom.strength.coerceIn(0f, 1f) * 255).toInt()
        }
        // Always the whole box, whatever the frame is: a frame says where the light is laid out, not where it may
        // land. A content-anchored bloom on small artwork still lights the pixels its ramp reaches past the ink.
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    }

    /**
     * Paints [gloss]'s sheen over the layer, clipped to what it has already drawn.
     *
     * The same source-atop overlay [applyBloom] is, differing only in the shader — a four-stop radial whose rim is
     * the light's boundary. Where it sits and which side is lit are [sweep]'s answers, so the live path draws the
     * same arc on the same side.
     */
    private fun applyGloss(canvas: Canvas, gloss: LayerEffect.Gloss, sweep: LayerGradient.Sweep, sizePx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sweep.centerX,
                sweep.centerY,
                sweep.radiusPx,
                sweep.colorsOf(gloss.argb),
                sweep.stops.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            alpha = (gloss.strength.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    }

    /**
     * Tiles [pattern] over the layer, clipped to what it has already drawn.
     *
     * An **unknown id draws nothing**, which is the same degrade `IconShapes` and `IconFilters` take: a recipe from a
     * later build loses one effect rather than failing to render at all.
     */
    private fun applyPattern(canvas: Canvas, pattern: LayerEffect.Pattern, sizePx: Int) {
        val res = IconPatterns.drawableResOrNull(pattern.pattern) ?: return
        val drawable = context.getDrawable(res) ?: return
        val tile = LayerPattern.tile(drawable, pattern, LayerPattern.tileSizePx(pattern.scale, sizePx))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                LayerPattern.localMatrix(pattern.angleDegrees, sizePx)?.let(::setLocalMatrix)
            }
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            alpha = (pattern.strength.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        tile.recycle()
    }

    private fun drawContent(canvas: Canvas, content: ParsedLayer, sizePx: Int) {
        when (content) {
            // A flat fill covers the whole box; the transform is a no-op for it, the shape mask trims it.
            is ParsedLayer.Color -> canvas.drawColor(content.argb)
            is ParsedLayer.Image -> content.drawable.apply {
                setBounds(0, 0, sizePx, sizePx)
                draw(canvas)
            }
        }
    }

    /**
     * Cuts the layer down to [shape]'s silhouette, drawn under [matrix] — `null` meaning plainly at box size.
     *
     * The bounds are always the full box: [matrix] is what places the silhouette, so the drawable is asked for its
     * authoring square either way and the anchor is expressed in one place rather than two.
     */
    private fun applyShapeMask(canvas: Canvas, shape: IconShape, sizePx: Int, matrix: Matrix?) {
        val res = IconShapes.drawableResOrNull(shape) ?: return
        val shapeDrawable = context.getDrawable(res) ?: return
        val mask = createBitmap(sizePx, sizePx)
        Canvas(mask).let { maskCanvas ->
            shapeDrawable.setBounds(0, 0, sizePx, sizePx)
            if (matrix == null) shapeDrawable.draw(maskCanvas)
            else maskCanvas.withMatrix(matrix) { shapeDrawable.draw(this) }
        }
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
        mask.recycle()
    }

    private fun decodeCustomImage(path: String): Drawable? =
        BitmapFactory.decodeFile(path)?.toDrawable(context.resources)

    /**
     * The Porter-Duff mode for [LayerBlend], or `null` for [LayerBlend.NORMAL] — which is source-over, i.e. what a
     * paint with no xfermode already does.
     */
    private fun LayerBlend.porterDuff(): PorterDuff.Mode? = when (this) {
        LayerBlend.NORMAL -> null
        LayerBlend.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        LayerBlend.SCREEN -> PorterDuff.Mode.SCREEN
        LayerBlend.OVERLAY -> PorterDuff.Mode.OVERLAY
        LayerBlend.DARKEN -> PorterDuff.Mode.DARKEN
        LayerBlend.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }
}
