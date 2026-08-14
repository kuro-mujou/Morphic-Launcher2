package inkspire.morphic.core.icon.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.withMatrix
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.icon.render.IconLayerResolver
import inkspire.morphic.core.icon.render.LayerFilter
import inkspire.morphic.core.icon.render.LayerGradient
import inkspire.morphic.core.icon.render.LayerTransform
import inkspire.morphic.core.icon.render.ResolvedLayer
import inkspire.morphic.core.icon.render.ShapeMask
import inkspire.morphic.core.model.icon.BloomFalloff
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect

/**
 * The **live** half of the hybrid render: an [IconLayerSet] drawn as Compose nodes, one per layer, rather than
 * composited into a bitmap.
 *
 * This is the editor's render path, and nothing else draws through it. Surfaces use [LauncherIcon], which shows a
 * baked bitmap — right for hundreds of icons that rarely change. The editor is the opposite case: one icon,
 * changing every frame a slider moves, where baking per frame would allocate a bitmap per frame. Here a transform
 * is a matrix on a node that already exists, so a slider drag costs a redraw and no re-composite at all.
 *
 * ## Staying honest with the baked path
 *
 * Two renderers is a real hazard — an icon that looks right while being edited and wrong on every surface is a bug
 * the editor structurally cannot show you. Six things make the paths agree, and each is a shared *thing* rather
 * than a shared intention:
 * - [IconLayerResolver] decides which layers draw and what content each one means, for both.
 * - [LayerTransform] does the offset/zoom/rotation arithmetic, for both.
 * - [LayerFilter] does the color-matrix arithmetic, for both — and shares the *same shape*, since Android's and
 *   Compose's `ColorMatrix` are each a row-major `FloatArray(20)`, so neither side converts anything.
 * - [IconFilters] is the table of built-in looks both paths resolve an id through, so a filter is one authored
 *   matrix rather than two that drifted. It composes from the same builders `LayerFilter` does.
 * - [LayerGradient] decides which way an angle runs and where a bloom's light sits, for both — including the frame
 *   it is laid out in, so one anchored to the artwork lands on the same ink in the editor and on the home screen.
 * - [ShapeMask] decides where the silhouette sits, for both. The mask itself is built from the **same** vector
 *   drawable via [IconShapes] and applied the same way — as a destination-in mask over the finished layer — but
 *   *where* it lands stopped being "the box" the moment a shape could be anchored to the artwork, and that is
 *   arithmetic, so it went the way the others did rather than being written twice.
 *
 * What is *not* shared is the drawing API (Android's `Canvas` there, [DrawScope] here). That is unavoidable, and
 * it is exactly why the six above are.
 *
 * **The per-layer order is content → shape mask → effects in list order → composite**, and it is the same on both
 * sides for different-looking reasons: the bake gets it from statement order inside one loop, and this path gets it
 * from folding the **reversed** list into nested modifiers. Worth checking against `IconRenderer` if either is
 * touched — see [layerEffects] for why the reversal is the subtle half.
 *
 * A **shadow** effect is deliberately absent: it is the one effect that is not additive here, because it derives
 * from the layer's finished silhouette and Compose's only blur (`RenderEffect`) is API 31+ against a `minSdk` of
 * 26 — so it could not be made to match the bake on every device. See the plan's S6 note.
 *
 * @param icon the app's parsed layers, from `ParsedIconLoader` — the same input the bake takes.
 * @param customImage resolves a custom-image layer's stored path to a drawable. Defaults to drawing nothing, and
 *   is a parameter rather than something this composable does for itself because decoding a file is I/O: the host
 *   decodes off the main thread and hands the results in, where doing it here would put a disk read in a
 *   composition that reruns on every slider frame.
 * @param packImage the same arrangement for an icon-pack layer, and for a sharper version of the same reason —
 *   the first lookup into a pack parses an `appfilter.xml` of thousands of entries.
 * @param modifier must resolve to a **square**. The layer geometry is defined in a square box, so a non-square
 *   node would stretch every transform along one axis — and, being only a distortion, would look plausible.
 */
@Composable
fun IconLayerStack(
    icon: ParsedIcon,
    layerSet: IconLayerSet,
    modifier: Modifier = Modifier,
    customImage: (path: String) -> Drawable? = { null },
    packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
) {
    val resolver = remember { IconLayerResolver() }
    val layers = remember(layerSet, icon, customImage, packImage) {
        resolver.resolve(layerSet, icon, customImage, packImage)
    }

    // **The stack composites offscreen, and a blend mode is why.** Sibling nodes draw onto whatever canvas they
    // are given, so without its own buffer a `MULTIPLY` on the bottom layer would multiply against the *studio
    // canvas* — the black, white or checkerboard behind the icon — instead of against nothing. The baked path
    // gets this for free by drawing into a fresh bitmap.
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // **The set's own effects, applied to the finished stack.** Inside the offscreen layer, so what they
            // transform is the composited children and not whatever is behind the icon. The composite has no
            // transform and no measured ink, so anything anchored to content falls back to the box — the same
            // degrade an unmeasured layer takes, stated by passing no spec.
            .layerEffects(layerSet.activeEffects, spec = null, inkFit = ShapeMask.InkFit.Box),
    ) {
        layers.forEach { layer ->
            // Three nested nodes now, and the nesting is the whole trick. The composite (opacity / blend / color)
            // is **outermost**, so it applies to the finished layer and blends against the layers already drawn;
            // the mask sits inside it but **outside the transform**, so a shape stays put in the box while the
            // content moves under it. The baked path gets the same ordering from its draw order.
            val inkFit = remember(layer.content) { ShapeMask.inkFit(layer.content) }
            Box(
                Modifier
                    .fillMaxSize()
                    .layerComposite(layer.spec)
                    .layerEffects(layer.spec.activeEffects, layer.spec, inkFit)
                    .shapeMask(layer),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // **The shared matrix, not a `graphicsLayer`'s fields — and perspective is why.** Reading zoom,
                    // rotation and translation into a layer was equivalent while the transform was affine, but a
                    // tilt is expressed there as a `cameraDistance` whose unit is not `android.graphics.Camera`'s,
                    // and matching two camera models by eye is exactly the agreement `LayerTransform` exists to make
                    // unnecessary. Taking the same matrix the bake takes retires the question.
                    //
                    // Resolved here rather than in composition because a matrix is in **pixels**, and this is where
                    // the node's real size is known.
                    val sizePx = size.width.toInt()
                    val matrix = LayerTransform.of(layer.spec, sizePx).toMatrix(sizePx)
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val depth = native.save()
                        native.concat(matrix)
                        drawLayerContent(layer.content)
                        native.restoreToCount(depth)
                    }
                }
            }
        }
    }
}

/** Draws one layer's resolved content over the whole box — the live twin of `IconRenderer.drawContent`. */
private fun DrawScope.drawLayerContent(content: ParsedLayer) {
    when (content) {
        is ParsedLayer.Color -> drawRect(Color(content.argb))
        is ParsedLayer.Image -> drawIntoCanvas { canvas ->
            content.drawable.apply {
                setBounds(0, 0, size.width.toInt(), size.height.toInt())
                draw(canvas.nativeCanvas)
            }
        }
    }
}

/**
 * Applies a layer's opacity and blend mode as it joins the stack — the live twin of `IconRenderer.compositePaint`.
 *
 * Through `saveLayer` rather than `graphicsLayer`, because `GraphicsLayerScope` has an `alpha` but no blend mode;
 * capturing the node into its own buffer and compositing *that* with one paint is the only way to get both, and it
 * is the same technique [shapeMask] and [layerEffects] each need one level in.
 *
 * **The color matrix and the gradient used to live here and are now effects**, so that where they sit relative to
 * each other is the user's rather than this function's. What is left is exactly what an effect cannot be ordered
 * against: how the finished layer meets the layers beneath it.
 *
 * A layer that composites plainly gets no modifier at all, which is the common case — an unedited icon.
 */
@Composable
private fun Modifier.layerComposite(spec: IconLayerSpec): Modifier {
    val blend = spec.blend.composeBlendMode()
    if (spec.opacity == 1f && blend == null) return this

    return this.drawWithContent {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                alpha = spec.opacity.coerceIn(0f, 1f)
                blend?.let { blendMode = it }
            }
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/**
 * The layer's effect pipeline — the live twin of the `for` loop in `IconRenderer.renderLayer`.
 *
 * **Built in reverse, and that is the whole subtlety.** A modifier written earlier in a chain *wraps* the ones
 * after it, so it draws around them — the outermost modifier is the last thing applied. The bake applies
 * `activeEffects` front to back as plain statements; to get the same sequence here the first effect has to end up
 * **innermost**, which is what folding the reversed list produces. Get it backwards and the icon is still an icon,
 * just a differently-colored one, on the one axis neither renderer can check against the other.
 *
 * Each effect takes its own `saveLayer` rather than sharing one. That is not tidiness: a filter has to see the
 * pixels every earlier effect produced, and source-atop has to have them as its destination, so the isolation is
 * what makes the pipeline a pipeline instead of four things painted on the same sheet.
 */
@Composable
private fun Modifier.layerEffects(
    effects: List<LayerEffect>,
    spec: IconLayerSpec?,
    inkFit: ShapeMask.InkFit,
): Modifier {
    if (effects.isEmpty()) return this

    return effects.reversed().fold(this) { chain, effect ->
        chain.then(effectModifier(effect, spec, inkFit))
    }
}

/**
 * One effect as a draw modifier. Exhaustive, so a new variant cannot be added without a live answer or a bake.
 *
 * Takes the spec and the ink fit rather than the resolved frame, because a frame is in **pixels** and the node's
 * real size is only known inside the draw scope — which is the same reason [LayerTransform] is resolved in the
 * draw scope rather than in composition.
 *
 * A null [spec] is the **composite**: it has no transform of its own, so anything placed against it sits in the box.
 */
@Composable
private fun effectModifier(effect: LayerEffect, spec: IconLayerSpec?, inkFit: ShapeMask.InkFit): Modifier = when (effect) {
    // Two effects, one drawing: both resolve to a colour matrix over everything drawn so far, and only where the
    // matrix comes from differs — composed from four sliders, or looked up by id.
    is LayerEffect.Color, is LayerEffect.Filter -> {
        val matrix = remember(effect) {
            when (effect) {
                is LayerEffect.Color -> LayerFilter.colorMatrixOf(effect)
                // Null for an id this build does not know, which then draws nothing rather than failing.
                is LayerEffect.Filter -> IconFilters.matrixOrNull(effect.filter)
            }
        }
        if (matrix == null) {
            Modifier
        } else {
            Modifier.drawWithContent {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(
                        bounds = Rect(0f, 0f, size.width, size.height),
                        paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)) },
                    )
                    drawContent()
                    canvas.restore()
                }
            }
        }
    }

    // Two overlays, one arrangement: each takes its own layer so source-atop's destination is what the pipeline has
    // drawn so far and nothing else — which is what makes them light the artwork instead of covering the icon with a
    // rectangle.
    is LayerEffect.Bloom -> Modifier.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds = Rect(0f, 0f, size.width, size.height), paint = Paint())
            drawContent()
            drawBloomOverlay(effect, spec, inkFit)
            canvas.restore()
        }
    }

    is LayerEffect.Gloss -> Modifier.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds = Rect(0f, 0f, size.width, size.height), paint = Paint())
            drawContent()
            drawGlossOverlay(effect, spec, inkFit)
            canvas.restore()
        }
    }
}

/**
 * Paints a sheen over whatever has been drawn, clipped to it — the live twin of `IconRenderer.applyGloss`.
 *
 * Every decision is [LayerGradient.sweep]'s: where the disc sits, where its stops fall, and which side of the rim is
 * lit. What is left here is turning four ARGB ints into Compose colours and handing them to a brush.
 */
private fun DrawScope.drawGlossOverlay(gloss: LayerEffect.Gloss, spec: IconLayerSpec?, inkFit: ShapeMask.InkFit) {
    val sizePx = size.width.toInt()
    val transform = spec?.let { LayerTransform.of(it, sizePx) } ?: LayerTransform.Identity
    val frame = LayerGradient.frameOf(gloss.anchor, inkFit, transform, sizePx)
    val sweep = LayerGradient.sweep(frame, gloss.angleDegrees, gloss.curve)
    val colors = sweep.colorsOf(gloss.argb)

    drawRect(
        brush = Brush.radialGradient(
            colorStops = Array(colors.size) { sweep.stops[it] to Color(colors[it]) },
            center = Offset(sweep.centerX, sweep.centerY),
            radius = sweep.radiusPx,
        ),
        alpha = gloss.strength.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcAtop,
    )
}

/**
 * Paints a bloom over whatever has been drawn, clipped to it — the live twin of `IconRenderer.applyBloom`.
 *
 * The `when` chooses a brush and nothing else: both falloffs take the same stops, the same alpha and the same
 * source-atop, and where each one sits comes from [LayerGradient] — including the frame, so a bloom anchored to the
 * artwork lands on the same ink in both paths and turns with it by the same arithmetic.
 */
private fun DrawScope.drawBloomOverlay(bloom: LayerEffect.Bloom, spec: IconLayerSpec?, inkFit: ShapeMask.InkFit) {
    // Square, from the width — the same quantity the transform and the shape mask read the box by.
    val sizePx = size.width.toInt()
    val transform = spec?.let { LayerTransform.of(it, sizePx) } ?: LayerTransform.Identity
    val frame = LayerGradient.frameOf(bloom, inkFit, transform, sizePx)
    val colors = listOf(Color(bloom.argb), Color(LayerGradient.fadeOut(bloom.argb)))

    val brush = when (bloom.falloff) {
        BloomFalloff.LINEAR -> {
            val (x0, y0, x1, y1) = LayerGradient.endpoints(frame, bloom.angleDegrees).toList()
            Brush.linearGradient(colors = colors, start = Offset(x0, y0), end = Offset(x1, y1))
        }

        BloomFalloff.RADIAL -> {
            val radial = LayerGradient.radial(frame, bloom.radius)
            Brush.radialGradient(
                colors = colors,
                center = Offset(radial.centerX, radial.centerY),
                radius = radial.radiusPx,
            )
        }
    }
    drawRect(brush = brush, alpha = bloom.strength.coerceIn(0f, 1f), blendMode = BlendMode.SrcAtop)
}

/** The Compose blend mode for [LayerBlend], or `null` for `NORMAL` — which is the default source-over. */
private fun LayerBlend.composeBlendMode(): BlendMode? = when (this) {
    LayerBlend.NORMAL -> null
    LayerBlend.MULTIPLY -> BlendMode.Multiply
    LayerBlend.SCREEN -> BlendMode.Screen
    LayerBlend.OVERLAY -> BlendMode.Overlay
    LayerBlend.DARKEN -> BlendMode.Darken
    LayerBlend.LIGHTEN -> BlendMode.Lighten
}

/**
 * Keeps the node's pixels only where the layer's shape silhouette is opaque. A null shape — or an unknown id, which
 * stale stored data can still produce — is a no-op, matching the baked path's early return.
 *
 * **The mask goes through `saveLayer` because a `Drawable` cannot be drawn with a blend mode.** It paints with its
 * own paints, so the silhouette has to be captured into its own buffer first and *that* composited with
 * [BlendMode.DstIn]. [CompositingStrategy.Offscreen] is the matching requirement one level out: the blend's
 * destination must be this node's own pixels, and without it the blend would apply against whatever happened to be
 * on the canvas beneath — which for the bottom layer of a stack is the surface behind the icon.
 *
 * **This node stays outside the transform whatever the anchor is, and only what it draws inside changes.** The mask
 * has to be applied to the layer's *finished* pixels, so its position cannot come from where the node sits — it
 * comes from [ShapeMask], the same answer the baked path gets. Nesting the mask inside the content's transform
 * would look equivalent and is not: the content is drawn through the transform, so a rotation would turn the mask's
 * own buffer and its edges with it.
 *
 * Takes the whole [ResolvedLayer] rather than the shape, because a content-anchored mask is fitted to the ink of
 * the artwork this layer actually resolved to — the same correction that made normalization right for a themed
 * layer, and for the same reason: a layer's own content is the only honest thing to measure it against.
 */
@Composable
private fun Modifier.shapeMask(layer: ResolvedLayer): Modifier {
    val res = layer.spec.shape?.let { IconShapes.drawableResOrNull(it) } ?: return this
    val resource = LocalResources.current
    val maskDrawable = remember(res, resource) { resource.getDrawable(res, null) } ?: return this

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawIntoCanvas { canvas ->
                canvas.saveLayer(
                    bounds = Rect(0f, 0f, size.width, size.height),
                    paint = Paint().apply { blendMode = BlendMode.DstIn },
                )
                // Square, from the width — the same quantity the transform above is resolved against, so the two
                // read the box the same way.
                val sizePx = size.width.toInt()
                maskDrawable.setBounds(0, 0, sizePx, sizePx)
                val matrix = ShapeMask.matrixOf(layer.spec, layer.content, sizePx)
                val native = canvas.nativeCanvas
                if (matrix == null) maskDrawable.draw(native)
                else native.withMatrix(matrix) { maskDrawable.draw(this) }
                canvas.restore()
            }
        }
}
