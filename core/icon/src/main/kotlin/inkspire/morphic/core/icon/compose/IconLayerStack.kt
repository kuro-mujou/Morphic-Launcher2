package inkspire.morphic.core.icon.compose

import android.graphics.Matrix
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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.withMatrix
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.icon.render.IconLayerResolver
import inkspire.morphic.core.icon.render.LayerFilter
import inkspire.morphic.core.icon.render.LayerExtrude
import inkspire.morphic.core.icon.render.LayerChromatic
import inkspire.morphic.core.icon.render.LayerGradient
import inkspire.morphic.core.icon.render.LayerPattern
import inkspire.morphic.core.icon.render.LayerTransform
import inkspire.morphic.core.icon.render.ResolvedLayer
import inkspire.morphic.core.icon.render.ShapeMask
import inkspire.morphic.core.model.icon.Falloff
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
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
 * the editor structurally cannot show you. Nine things make the paths agree, and each is a shared *thing* rather
 * than a shared intention:
 * - [IconLayerResolver] decides which layers draw and what content each one means, for both.
 * - [LayerTransform] does the offset/zoom/rotation arithmetic **and hands back the matrix**, for both — which is
 *   what retired the one question the two could not have answered the same way, since Compose's `cameraDistance`
 *   and `android.graphics.Camera`'s z are not in the same units.
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
 * - [LayerPattern] decides a tile's pixel size, the matrix that turns it, and how a stencil becomes colored marks.
 * - [LayerExtrude] decides how many copies a slab is made of and how far apart they sit.
 * - [LayerChromatic] decides which channel leads and which trails.
 *
 * **The last three are worth reading as a group, because the pattern repeats.** An effect earns a derivation
 * exactly when its two implementations would differ in something *invisible*: a tile at half the intended scale is
 * still a texture, an extrusion of twelve copies instead of forty is still an extrusion, and a red fringe on the
 * left is as plausible as one on the right. None of those fail, and none look wrong until the editor and the home
 * screen are seen together.
 *
 * What is *not* shared is the drawing API (Android's `Canvas` there, [DrawScope] here). That is unavoidable, and
 * it is exactly why the nine above are.
 *
 * **The per-layer order is content → shape mask → effects in list order → composite**, and it is the same on both
 * sides for different-looking reasons: the bake gets it from statement order inside one loop, and this path gets it
 * from folding the **reversed** list into nested modifiers. Worth checking against `IconRenderer` if either is
 * touched — see [layerEffects] for why the reversal is the subtle half.
 *
 * **Six effects are deliberately absent** — glow, drop shadow, pixelate, ripple, grain and progressive blur — and
 * they are the ones this path structurally cannot reach: Compose's only blur (`RenderEffect`) is API 31+ and its
 * only per-pixel route (AGSL) is API 33+, against a `minSdk` of 26. The bake can draw all six at every API, so the
 * answer is not to gate them but to preview from the bake when one is present, which is what
 * [inkspire.morphic.core.model.icon.LayerEffect.drawsLive] exists to signal. See `docs/ICON_EFFECTS_PLAN.md` §7.
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
            // **The set's own mask again, outside its effects** — the step a layer does not take, because a stack
            // shape is the icon's *boundary* rather than one more mask. Half the effect list grows alpha outward, so
            // without this the icon's soft edges escape the silhouette and are stopped by the box instead. Which
            // shape, and when there is nothing to trim, is [IconLayerSet.effectTrimShape]'s answer for both paths —
            // and it matters most for the effects this path cannot draw at all, where only the bake shows the result.
            .shapeMask(layerSet.effectTrimShape)
            // **The set's own effects, applied to the finished stack.** Inside the offscreen layer, so what they
            // transform is the composited children and not whatever is behind the icon. The composite has no
            // transform and no measured ink, so anything anchored to content falls back to the box — the same
            // degrade an unmeasured layer takes, stated by passing no spec.
            .layerEffects(layerSet.activeEffects, spec = null, inkFit = ShapeMask.InkFit.Box)
            // **The set's own mask, inside those effects** — mask then effects, which is the per-layer order below
            // and the bake's. No matrix, because the composite's only frame is the box: the same fact that sends its
            // effects to `InkFit.Box` above.
            .shapeMask(layerSet.shape)
            // **The whole icon's angles, innermost, so the mask and the effects sit outside them** — a turned or
            // leaning icon slides under a silhouette that stays put, which is what a box-anchored mask means one
            // scope down, and is exactly the look a fixed icon shape is for.
            .compositeTransform(layerSet),
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
                    .shapeMask(layer.spec.shape) { ShapeMask.matrixOf(layer.spec, layer.content, it) },
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

/**
 * Turns and leans the finished stack — the live twin of the matrix the bake puts on its canvas before drawing the
 * layers into it.
 *
 * **The same matrix, from the same place**, which is the whole reason [LayerTransform] took an `IconLayerSet`
 * overload rather than each path configuring a camera of its own: `graphicsLayer.cameraDistance` and
 * `android.graphics.Camera`'s z are not in the same units, so two implementations that looked equivalent would
 * foreshorten differently — the one difference this file exists to prevent, since the editor cannot show it to you.
 *
 * Concatenated onto the native canvas rather than expressed as a `graphicsLayer`, for exactly that reason, and
 * applied around `drawContent()` so what it carries is every layer at once. An unturned set — every icon nobody has
 * angled — gets no modifier at all.
 *
 * **Gated on the whole transform being identity, not on [LayerTransform.isTilted]**: the composite has an in-plane
 * rotation as well now, and a tilt test would skip the matrix for an icon that is only turned — which draws it
 * upright with nothing to say it went wrong.
 */
@Composable
private fun Modifier.compositeTransform(layerSet: IconLayerSet): Modifier {
    val transform = LayerTransform.of(layerSet)
    if (transform.isIdentity) return this

    return this.drawWithContent {
        // Held because `drawIntoCanvas` hands its block a `Canvas`, not this scope — the same shape the Extrude
        // effect's own re-draws take.
        val scope = this
        // Square, from the width, like every other size read in this file.
        val matrix = transform.toMatrix(size.width.toInt())
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val depth = native.save()
            native.concat(matrix)
            scope.drawContent()
            native.restoreToCount(depth)
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
    // Three effects, one drawing: each resolves to a color matrix over everything drawn so far, and only where the
    // matrix comes from differs — composed from four sliders, mapped onto two chosen colors, or looked up by id.
    is LayerEffect.Color, is LayerEffect.Duotone, is LayerEffect.Filter -> {
        val matrix = remember(effect) {
            when (effect) {
                is LayerEffect.Color -> LayerFilter.colorMatrixOf(effect)
                is LayerEffect.Duotone -> LayerFilter.duotoneMatrixOf(effect)
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

    is LayerEffect.Vignette -> Modifier.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds = Rect(0f, 0f, size.width, size.height), paint = Paint())
            drawContent()
            drawVignetteOverlay(effect, spec, inkFit)
            canvas.restore()
        }
    }

    // **The one effect that draws the content more than once, and it is why `MaxSteps` exists.** Every copy is a
    // re-run of the layer's own drawing rather than a blit of a finished bitmap, which the bake gets for free by
    // already holding one. Back to front, then the layer itself on top — the same order, and the same picture, as
    // `IconRenderer.extruded`.
    is LayerEffect.Extrude -> Modifier.drawWithContent {
        // Held because `translate` hands its block a plain `DrawScope`, which has no `drawContent` — the same
        // canvas and the same transform, only a narrower receiver.
        val scope = this
        val steps = LayerExtrude.steps(effect, size.width.toInt())
        val bounds = Rect(0f, 0f, size.width, size.height)
        val slab = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(LayerFilter.solidMatrixOf(effect.argb)))
            alpha = effect.strength.coerceIn(0f, 1f)
        }

        drawIntoCanvas { canvas ->
            // **One layer over the whole slab, not one per copy**, which reverses what this did and what its own
            // comment argued for. Per copy, `strength` was each copy's alpha and compounded where they overlapped:
            // the slab reached `1 - (1 - strength)^count`, so it was 97% opaque by 0.3 and the top of the slider did
            // nothing — and `count` follows the depth *and* the bake size, so one recipe was denser at a greater
            // depth and denser again at a larger size. Flattened, `strength` is the slab's own opacity and both
            // couplings go.
            //
            // The old comment's worry was the color matrix, and it does not apply: `solid` replaces the color and
            // keeps the alpha, so filtering the assembled union gives the same flat silhouette as filtering each
            // copy. What is genuinely given up is the density gradient the compounding made — darker at the base,
            // fading at the tip — which was an artifact of the technique rather than a thing that was asked for.
            canvas.saveLayer(bounds, slab)
            for (step in steps.count downTo 1) {
                translate(steps.dxPx * step, steps.dyPx * step) { scope.drawContent() }
            }
            canvas.restore()
        }
        drawContent()
    }

    // **The ones this path cannot draw, and the reason [IconPreview] exists.** The two halos need the finished
    // silhouette *blurred*, which Compose can only do with `RenderEffect` from API 31; the ripple needs each pixel
    // read from somewhere else, which needs AGSL and API 33. Both against a `minSdk` of 26. They answer `drawsLive`
    // false, so `IconPreview` routes an icon carrying any of them to the bake and never reaches here — this arm is
    // what a caller gets for using [IconLayerStack] *directly*, and drawing nothing is the honest answer: a wrong
    // effect would be worse than a missing one, since only the missing one is noticed.
    is LayerEffect.Outline,
    is LayerEffect.Bevel,
    is LayerEffect.Glow,
    is LayerEffect.Shadow,
    is LayerEffect.InnerShadow,
    is LayerEffect.InnerGlow,
    is LayerEffect.Ripple,
    is LayerEffect.Grain,
    is LayerEffect.Pixelate,
    is LayerEffect.ProgressiveBlur,
    -> Modifier

    // **The only effect that draws the content instead of over it**, so the layer's own pixels never appear: what
    // comes out is the three channels, displaced and added. The outer layer is what keeps `Plus` adding against
    // nothing rather than against whatever the pipeline had already drawn — the same isolation the bake gets free by
    // building into a new bitmap.
    is LayerEffect.ChromaticSplit -> Modifier.drawWithContent {
        val scope = this
        val bounds = Rect(0f, 0f, size.width, size.height)

        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, Paint())
            LayerChromatic.fringes(effect, size.width.toInt()).forEach { fringe ->
                canvas.saveLayer(
                    bounds = bounds,
                    paint = Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(fringe.matrix))
                        blendMode = BlendMode.Plus
                    },
                )
                translate(fringe.dxPx, fringe.dyPx) { scope.drawContent() }
                canvas.restore()
            }
            canvas.restore()
        }
    }

    // **The one overlay whose tile is remembered rather than derived per frame.** A gradient is three numbers handed
    // to a constructor; a pattern is a rasterised drawable, so building it inside the draw scope would allocate two
    // bitmaps on every frame of a slider drag. Keyed on the effect and the resources, which is everything the tile
    // depends on except the node's size — see [drawPatternOverlay] for why that one is handled where it is.
    is LayerEffect.Pattern -> {
        val res = IconPatterns.drawableResOrNull(effect.pattern)
        val resource = LocalResources.current
        // `mutate` for `IconLayerResolver.owned`'s reason: the instance is fresh but its constant state is shared, and
        // a vector caches a rendered bitmap in there — so this tile and a bake's copy of the same pattern would fight
        // over one cache at two sizes.
        val drawable = remember(res, resource) { res?.let { resource.getDrawable(it, null)?.mutate() } }

        if (drawable == null) {
            // An id this build does not know. Nothing drawn, matching the bake — a recipe from a later build loses
            // one effect rather than failing to render.
            Modifier
        } else {
            Modifier.drawWithContent {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(bounds = Rect(0f, 0f, size.width, size.height), paint = Paint())
                    drawContent()
                    drawPatternOverlay(effect, drawable)
                    canvas.restore()
                }
            }
        }
    }
}

/**
 * Tiles a pattern over whatever has been drawn, clipped to it — the live twin of `IconRenderer.applyPattern`.
 *
 * The tile, its size and the matrix that turns it are all [LayerPattern]'s answers, so the two paths cannot come to
 * texture the same icon at different scales. What differs is only the wrapper: a `BitmapShader` there, Compose's
 * [ImageShader] here, over the same bitmap.
 *
 * **Built in the draw scope rather than remembered**, unlike the drawable above, because the tile's size depends on
 * the node's — which composition does not know. The cost is one rasterise per frame of a drag; the alternative is
 * plumbing the size back out of layout, and a tile is a handful of pixels square.
 */
private fun DrawScope.drawPatternOverlay(pattern: LayerEffect.Pattern, drawable: Drawable) {
    val sizePx = size.width.toInt()
    val tile = LayerPattern.tile(drawable, pattern, LayerPattern.tileSizePx(pattern.scale, sizePx))
    val shader = ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated).apply {
        LayerPattern.localMatrix(pattern.angleDegrees, sizePx)?.let(::setLocalMatrix)
    }

    drawRect(
        brush = ShaderBrush(shader),
        alpha = pattern.strength.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcAtop,
    )
}

/**
 * Paints a sheen over whatever has been drawn, clipped to it — the live twin of `IconRenderer.applyGloss`.
 *
 * Every decision is [LayerGradient.sweep]'s: where the disc sits, where its stops fall, and which side of the rim is
 * lit. What is left here is turning four ARGB ints into Compose colors and handing them to a brush.
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
 * Gathers a vignette's color in from the frame's edges, clipped to what has been drawn — the live twin of
 * `IconRenderer.applyVignette`.
 *
 * The disc spans the frame to its corners either way and the *stops* are what move; both come from [LayerGradient],
 * as does the inversion from reach to clear area — which is the model's, so the two paths cannot come to shade from
 * opposite ends.
 */
private fun DrawScope.drawVignetteOverlay(
    vignette: LayerEffect.Vignette,
    spec: IconLayerSpec?,
    inkFit: ShapeMask.InkFit,
) {
    val sizePx = size.width.toInt()
    val transform = spec?.let { LayerTransform.of(it, sizePx) } ?: LayerTransform.Identity
    val frame = LayerGradient.frameOf(vignette.anchor, inkFit, transform, sizePx)
    val radial = LayerGradient.radial(frame, radiusFraction = 1f)
    val stops = LayerGradient.rampStops(vignette.clearArea, vignette.softness)

    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                stops[0] to Color(LayerGradient.fadeOut(vignette.argb)),
                stops[1] to Color(vignette.argb),
            ),
            center = Offset(radial.centerX, radial.centerY),
            radius = radial.radiusPx,
        ),
        alpha = vignette.strength.coerceIn(0f, 1f),
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
        Falloff.LINEAR -> {
            val (x0, y0, x1, y1) = LayerGradient.endpoints(frame, bloom.angleDegrees).toList()
            Brush.linearGradient(colors = colors, start = Offset(x0, y0), end = Offset(x1, y1))
        }

        Falloff.RADIAL -> {
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

/**
 * The Compose blend mode for [LayerBlend], or `null` for `NORMAL` — which is the default source-over.
 *
 * **Public because the studio's blend swatches draw through it**, which is the point of them: a tile claiming to
 * show what a mode does has to compose with the mode the renderer will actually use. Its own mapping would be a
 * second answer to that, and a swatch that showed `Overlay` while the layer got `Screen` is a lie no test would
 * catch — the same argument every shared derivation in the `render` package is built on.
 */
fun LayerBlend.composeBlendMode(): BlendMode? = when (this) {
    LayerBlend.NORMAL -> null
    LayerBlend.MULTIPLY -> BlendMode.Multiply
    LayerBlend.SCREEN -> BlendMode.Screen
    LayerBlend.OVERLAY -> BlendMode.Overlay
    LayerBlend.DARKEN -> BlendMode.Darken
    LayerBlend.LIGHTEN -> BlendMode.Lighten
}

