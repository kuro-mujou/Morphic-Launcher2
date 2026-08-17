package inkspire.morphic.core.icon.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import inkspire.morphic.core.graphics.BitmapBlur
import inkspire.morphic.core.model.icon.Falloff
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.OutlinePosition
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

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
 * **This path has no API restrictions, and that is the fact the effects plan turns on.** It owns a software bitmap,
 * so a blur is a `BlurMaskFilter` and a displacement is arithmetic over an `IntArray` at every API level — where the
 * live path's only blur is API 31+ and its only per-pixel route API 33+. So the six effects still to come (glow,
 * drop shadow, pixelate, ripple, grain, progressive blur) land *here* first, and the studio previews from this
 * rather than gating them. See `docs/ICON_EFFECTS_PLAN.md` §7.
 *
 * Still deferred: adaptive-layer overshoot scaling (`AppDefault` layers draw to the full box; expect to tune on
 * device).
 */
class IconRenderer(
    private val context: Context,
    private val resolver: IconLayerResolver = IconLayerResolver(),
) {
    /**
     * How many bands [resample] splits its rows into — one fewer than the cores, capped, and never below one.
     *
     * **One fewer, deliberately**, for `IconRenderManager`'s reason one layer up: this runs while the studio is
     * drawing the panel whose slider is being dragged, and a split that took every core would win the bake and lose
     * the frame. The cap keeps a many-core device from oversubscribing when real icons are baking beside the preview.
     */
    private val BakeBands: Int =
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

    /**
     * The per-pixel and per-row callbacks the parallel passes take — **`fun interface`s rather than function types,
     * and that is a performance decision rather than a style one.**
     *
     * Kotlin's function types are generic (`Function3<Integer, Integer, FloatArray, Unit>`) and it does not specialise
     * them over primitives, so `(x: Int, y: Int, into: FloatArray) -> Unit` **boxes both `Int`s at every call**. In
     * [resample] that call is the innermost statement of the hottest loop in this class: at preview size it ran six
     * hundred thousand times per effect, allocating over a million `Integer`s — on the order of twenty megabytes of
     * garbage per bake, which is paid twice over, once in the allocation and again when the collector runs and takes
     * the main thread's cores with it.
     *
     * A `fun interface` compiles to one method with primitive parameters, so the loop allocates nothing and a lambda
     * at the call site still reads exactly as it did. [Rows] matters far less than [SourceOf] — it is one call per row
     * rather than per pixel — but the two are the same mechanism and one rule is easier to keep than two.
     */
    private fun interface SourceOf {
        /** Writes the source position for output ([x], [y]) into [into] as `[srcX, srcY]`, in pixels, unrounded. */
        fun into(x: Int, y: Int, into: FloatArray)
    }

    /** @see SourceOf */
    private fun interface Rows {
        /** Runs one output row. Called from several threads at once, one `y` each. */
        fun row(y: Int)
    }

    /** Keeps a layer's pixels only where the shape silhouette is opaque. */
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * [maskPaint]'s opposite — keeps what is drawn only where the silhouette is *transparent*.
     *
     * Which is how [complementOf] inverts an alpha channel without a colour matrix: destination-out over a filled
     * buffer leaves exactly the region the artwork does not cover.
     */
    private val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /**
     * Renders the visible layers of [layerSet] for [icon] into one `sizePx` × `sizePx` bitmap.
     *
     * @param packImage this app's artwork from an installed icon pack, pre-bound to the component by the caller —
     *   this class draws pixels and has no business knowing which app they belong to. Defaults to nothing, which
     *   is what a recipe with no pack layer needs and what the harness passes.
     * @param customImage resolves a custom-image layer's stored path. Defaults to reading the file, which is right
     *   for every surface — and **wrong for the studio**, whose whole point is that a freshly picked image is
     *   previewed before anything is written to disk (see `CustomIconStore`). So the editor passes the same lambda
     *   it hands the live path, and the two draw the same picture rather than one of them showing a missing layer.
     *
     * ## Suspend, because a bake has to be **abandonable**
     *
     * Cancellation in coroutines is cooperative, and a `for` loop over half a million pixels cooperates in nothing:
     * cancelling the coroutine around this used to leave it running to the end regardless. That defeated the whole
     * of `IconPreview`'s draft-then-full design, whose throttle *is* cancellation — during a slider drag every
     * emitted recipe queued a bake that ran to completion, so the previews arrived in a backlog after the finger
     * lifted rather than while it moved, and the studio's runaway bakes starved every other icon on the screen of
     * the same dispatcher.
     *
     * So the pixel loops check the calling context and throw when it has been cancelled. Being `suspend` is what
     * makes that context reachable without every caller remembering to hand one in — the wiring cannot be forgotten
     * because there is nowhere to forget it.
     *
     * An abandoned bake leaves its buffers to the collector rather than recycling them: the alternative is a
     * `try`/`finally` around every intermediate in the pipeline to save an allocation that is about to be garbage
     * anyway, on a path that only runs when the work is already being thrown away.
     */
    suspend fun render(
        icon: ParsedIcon,
        layerSet: IconLayerSet,
        sizePx: Int,
        packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
        customImage: (path: String) -> Drawable? = ::decodeCustomImage,
    ): Bitmap {
        val bake = currentCoroutineContext()
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)

        // **The whole icon's angles are on the canvas, so the layers are drawn *through* them** rather than
        // composited flat and then re-sampled. Cheaper — no second bitmap — and sharper, since each layer's own
        // bitmap is the thing being transformed. It cannot separate the layers from one another either: they all
        // take the one matrix, so nothing slides relative to anything else. See `IconLayerSet.rotation`.
        val placement = LayerTransform.of(layerSet).toMatrix(sizePx)
        resolver.resolve(layerSet, icon, customImage, packImage).forEach { layer ->
            val layerBitmap = renderLayer(layer, sizePx, bake)
            // Opacity and blend are applied **as the layer joins the stack**, not while its content is drawn —
            // which is what makes a blend mode mean "against everything beneath" rather than "against the one
            // bitmap I am in". The live path composes both into one paint for the same reason.
            //
            // **A blended layer takes a different route, and it is a correction rather than an optimisation.**
            // Handing a [LayerBlend] to a `PorterDuffXfermode` looked like the whole job and was wrong for one of
            // the five: `PorterDuff.Mode.MULTIPLY` multiplies the *alpha* as well, so a layer set to multiply erased
            // everything beneath it wherever it was itself transparent. See [LayerComposite].
            if (layer.spec.blend == LayerBlend.NORMAL) {
                canvas.withMatrix(placement) {
                    drawBitmap(layerBitmap, 0f, 0f, opacityPaint(layer.spec))
                }
            } else {
                blendOnto(output, layerBitmap, layer.spec, placement, sizePx, bake)
            }
            layerBitmap.recycle()
        }

        // **The set's own mask, then its own effects, then the mask again.** The first two are the order a layer takes
        // — which is what makes "shape the whole icon" mean what "shape this layer" means — and both sit outside the
        // angles above for the reason a layer's sit outside its transform: the mask trims the *finished* picture, so a
        // turned or leaning icon slides under a silhouette that stays put. Passing no matrix says that: the frame is
        // the box, which is also why the effects fall back to [ShapeMask.InkFit.Box] and [LayerTransform.Identity].
        //
        // **The third step is the one a layer does not take**, and [IconLayerSet.effectTrimShape] is where the rule
        // lives. A stack shape is the icon's boundary rather than one more mask, and half the effect list grows alpha
        // outward — so without it a blur's soft edge escapes the silhouette and is stopped by the box instead, ringing
        // a rounded icon with squared-off haze. One silhouette, built once and applied at both ends.
        val mask = layerSet.shape?.let { shapeMaskOrNull(it, sizePx, matrix = null) }
        mask?.let { applyShapeMask(canvas, it) }

        val finished =
            applyEffects(output, layerSet.activeEffects, ShapeMask.InkFit.Box, LayerTransform.Identity, sizePx, bake)

        if (mask != null) {
            if (layerSet.effectTrimShape != null) applyShapeMask(Canvas(finished), mask)
            mask.recycle()
        }
        return finished
    }

    /**
     * Alpha for one layer joining the stack plainly, or `null` when it joins at full strength.
     *
     * **The color matrix used to ride here and now does not.** Recoloring is an effect, so it belongs in the layer's
     * own pipeline where its position relative to the other effects is the user's; opacity and blend stay because
     * they describe how the finished layer *joins the stack*, which is not something an effect can be ordered
     * against. Moving it changes nothing on a layer that only recolors — a color filter is per-pixel, so filtering
     * into the buffer and then compositing gives the same pixels as compositing through the filter.
     *
     * **The blend mode used to ride here too, as a `PorterDuffXfermode`, and that was a bug rather than a
     * simplification** — see [LayerComposite]. A layer that blends now goes through [blendOnto] instead, so what is
     * left here is exactly the case a canvas can be trusted with.
     */
    private fun opacityPaint(spec: IconLayerSpec): Paint? {
        if (spec.opacity == 1f) return null

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (spec.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
    }

    /**
     * Lays [layerBitmap] onto [output] through the layer's blend mode, in place.
     *
     * **The layer is placed through [placement] first**, because the whole icon's own angles are a matrix on the
     * canvas and a per-pixel blend has no canvas to inherit it from. The scratch is what the canvas would have drawn,
     * and the blend then happens between two buffers in the same frame.
     *
     * The rows split across cores like every other per-pixel pass here — each one reads only [src] and writes only
     * its own slots of [into], so there is nothing to coordinate.
     */
    private suspend fun blendOnto(
        output: Bitmap,
        layerBitmap: Bitmap,
        spec: IconLayerSpec,
        placement: Matrix,
        sizePx: Int,
        bake: CoroutineContext,
    ) {
        val placed = createBitmap(sizePx, sizePx)
        Canvas(placed).withMatrix(placement) { drawBitmap(layerBitmap, 0f, 0f, null) }

        val into = IntArray(sizePx * sizePx)
        output.getPixels(into, 0, sizePx, 0, 0, sizePx, sizePx)
        val src = IntArray(into.size)
        placed.getPixels(src, 0, sizePx, 0, 0, sizePx, sizePx)
        placed.recycle()

        overRows(sizePx, bake) { y ->
            for (x in 0 until sizePx) {
                val at = y * sizePx + x
                into[at] = LayerComposite.blend(into[at], src[at], spec.blend, spec.opacity)
            }
        }
        output.setPixels(into, 0, sizePx, 0, 0, sizePx, sizePx)
    }

    /**
     * Draws one resolved layer into its own bitmap: content, transform, shape mask, then its effects **in order**.
     *
     * The order is `IconLayerSpec.activeEffects`' order, which is the list's, which is the user's. The live path
     * expresses the same sequence by nesting modifiers in reverse — see `IconLayerStack`, and expect to check both
     * if either is touched.
     */
    private suspend fun renderLayer(layer: ResolvedLayer, sizePx: Int, bake: CoroutineContext): Bitmap {
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
        return applyEffects(bitmap, layer.spec.activeEffects, ShapeMask.inkFit(layer.content), transform, sizePx, bake)
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
    private suspend fun applyEffects(
        bitmap: Bitmap,
        effects: List<LayerEffect>,
        inkFit: ShapeMask.InkFit,
        transform: LayerTransform,
        sizePx: Int,
        bake: CoroutineContext,
    ): Bitmap {
        var current = bitmap
        var canvas = Canvas(current)

        /**
         * Swaps in a buffer built from the current one, which is what every non-overlay effect does.
         *
         * **[with] must return a *new, mutable* bitmap.** Returning the one it was given would recycle the buffer
         * and then keep drawing into it, and returning an immutable one — which is what `Bitmap.copy(config, false)`
         * hands back — makes the `Canvas` below throw outright. The identity check guards the first of those; the
         * second has no guard but a rule, which is that an effect with nothing to do must not be given to this at
         * all. See the `ProgressiveBlur` arm for the shape that takes.
         */
        suspend fun replace(with: suspend (Bitmap) -> Bitmap) {
            val next = with(current)
            if (next === current) return
            current.recycle()
            current = next
            canvas = Canvas(current)
        }

        for (effect in effects) {
            when (effect) {
                is LayerEffect.Bloom ->
                    applyBloom(canvas, effect, LayerGradient.frameOf(effect, inkFit, transform, sizePx), sizePx)

                is LayerEffect.Gloss -> {
                    val frame = LayerGradient.frameOf(effect.anchor, inkFit, transform, sizePx)
                    applyGloss(canvas, effect, LayerGradient.sweep(frame, effect.angleDegrees, effect.curve), sizePx)
                }

                is LayerEffect.Vignette ->
                    applyVignette(
                        canvas,
                        effect,
                        LayerGradient.frameOf(effect.anchor, inkFit, transform, sizePx),
                        sizePx,
                    )

                is LayerEffect.Pattern -> applyPattern(canvas, effect, sizePx)

                is LayerEffect.Color ->
                    LayerFilter.colorMatrixOf(effect)?.let { m -> replace { filtered(it, m, sizePx) } }

                // Unconditional where the two above are not: a duotone has no pair of colours that resolves to
                // nothing, so there is no null to guard. Its own floor is the strength, which `activeEffects`
                // has already filtered on before this loop is reached.
                is LayerEffect.Duotone ->
                    LayerFilter.duotoneMatrixOf(effect).let { m -> replace { filtered(it, m, sizePx) } }

                // An id this build does not know draws nothing rather than failing.
                is LayerEffect.Filter ->
                    IconFilters.matrixOrNull(effect.filter)?.let { m -> replace { filtered(it, m, sizePx) } }

                is LayerEffect.Extrude -> replace { extruded(it, effect, sizePx) }

                is LayerEffect.ChromaticSplit -> replace { split(it, effect, sizePx) }

                is LayerEffect.Ripple -> replace { rippled(it, effect, sizePx, bake) }

                is LayerEffect.Grain -> replace { grained(it, effect, sizePx, bake) }

                is LayerEffect.Pixelate -> replace { pixelated(it, effect, sizePx, bake) }

                // **The side is resolved *before* `replace`, not inside it**, so a radius too small to blur skips
                // the buffer swap entirely rather than handing it a copy. The same shape `Filter` above takes for
                // an id it cannot resolve, and for the same reason: an effect with nothing to do must do nothing.
                is LayerEffect.ProgressiveBlur ->
                    LayerProgressiveBlur.boxRadiusPxOrNull(effect.radius, sizePx)?.let { box ->
                        replace { progressivelyBlurred(it, effect, box, sizePx) }
                    }

                // The same halo twice: a glow spreads and does not move, a shadow moves and does not spread.
                is LayerEffect.Glow -> replace {
                    haloed(
                        source = it,
                        argb = effect.argb,
                        strength = effect.strength,
                        radiusPx = LayerShadow.radiusPxOrNull(effect.radius, sizePx),
                        spreadPx = LayerShadow.spreadPx(effect.spread, sizePx),
                        dxPx = 0f,
                        dyPx = 0f,
                        sizePx = sizePx,
                    )
                }

                is LayerEffect.Outline -> replace { outlined(it, effect, sizePx) }

                // The radius is resolved *before* `replace`, so a bevel too narrow to have a slope skips the buffer
                // swap entirely rather than being handed a copy — `ProgressiveBlur`'s arm and its reason.
                is LayerEffect.Bevel ->
                    LayerBevel.radiusPxOrNull(effect, sizePx)?.let { radius ->
                        replace { bevelled(it, effect, radius, sizePx, bake) }
                    }

                // The same halo again, cast by the *complement* of the silhouette and laid back inside it — a recess
                // thrown and laid on plainly, or light centred on the edge and screened onto it.
                is LayerEffect.InnerShadow -> replace {
                    insetHaloed(
                        source = it,
                        argb = effect.argb,
                        strength = effect.strength,
                        radiusPx = LayerShadow.radiusPxOrNull(effect.radius, sizePx),
                        spreadPx = LayerShadow.spreadPx(effect.spread, sizePx),
                        dxPx = LayerShadow.offsetPx(effect.offsetX, sizePx),
                        dyPx = LayerShadow.offsetPx(effect.offsetY, sizePx),
                        blend = null,
                        sizePx = sizePx,
                    )
                }

                is LayerEffect.InnerGlow -> replace {
                    insetHaloed(
                        source = it,
                        argb = effect.argb,
                        strength = effect.strength,
                        radiusPx = LayerShadow.radiusPxOrNull(effect.radius, sizePx),
                        spreadPx = LayerShadow.spreadPx(effect.spread, sizePx),
                        dxPx = 0f,
                        dyPx = 0f,
                        blend = PorterDuff.Mode.SCREEN,
                        sizePx = sizePx,
                    )
                }

                is LayerEffect.Shadow -> replace {
                    haloed(
                        source = it,
                        argb = effect.argb,
                        strength = effect.strength,
                        radiusPx = LayerShadow.radiusPxOrNull(effect.radius, sizePx),
                        spreadPx = 0f,
                        dxPx = LayerShadow.offsetPx(effect.offsetX, sizePx),
                        dyPx = LayerShadow.offsetPx(effect.offsetY, sizePx),
                        sizePx = sizePx,
                    )
                }
            }
        }
        return current
    }

    /** [source] through one colour matrix, in a buffer of its own — a canvas cannot filter its pixels in place. */
    private fun filtered(source: Bitmap, matrix: FloatArray, sizePx: Int): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        Canvas(out).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
            },
        )
        return out
    }

    /**
     * [source] with its own silhouette repeated behind it — the slab, then the layer standing on it.
     *
     * **Drawn back to front rather than with a destination-over paint**, which is the same picture and one fewer
     * thing to get wrong: the furthest copy first, the nearest last, the untouched layer on top. Each copy is the
     * source through `ColorMatrices.solid`, so it comes out as a flat silhouette of the extrusion colour whatever
     * the layer is made of.
     *
     * **`strength` is the opacity of the finished slab, and getting there took one layer over the whole loop.** It
     * used to be the alpha of each *copy*, which compounds where they overlap: with `n` copies the slab came out at
     * `1 - (1 - strength)^n`, so it reached 97% by a strength of 0.3 and the top two thirds of the slider did
     * nothing at all. Worse, `n` is a function of depth *and* of [sizePx] — so the same recipe was denser at a
     * greater depth, and denser again baked at 288px than at 96px, which is one icon looking like two.
     *
     * Compositing the copies opaque and multiplying once makes the control linear and both couplings vanish. What
     * is given up is the density gradient the compounding produced — a slab darker at its base and fading at the
     * tip — and that is the honest trade: a solid object has one opacity, and the gradient was an artifact of the
     * technique rather than anything anyone asked for.
     *
     * The colour matrix still lands correctly on the union rather than per copy, which is what makes one layer
     * enough: `solid` replaces the colour and keeps the alpha, so a silhouette assembled from overlapping copies and
     * then filtered is the same flat colour as each copy filtered and then assembled.
     */
    private fun extruded(source: Bitmap, extrude: LayerEffect.Extrude, sizePx: Int): Bitmap {
        val steps = LayerExtrude.steps(extrude, sizePx)
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)

        val slab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(LayerFilter.solidMatrixOf(extrude.argb)))
        }
        val alpha = (extrude.strength.coerceIn(0f, 1f) * 255).toInt()
        val layer = canvas.saveLayerAlpha(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), alpha)
        for (step in steps.count downTo 1) {
            canvas.drawBitmap(source, steps.dxPx * step, steps.dyPx * step, slab)
        }
        canvas.restoreToCount(layer)

        canvas.drawBitmap(source, 0f, 0f, null)
        return out
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
                Falloff.LINEAR -> {
                    val (x0, y0, x1, y1) = LayerGradient.endpoints(frame, bloom.angleDegrees).toList()
                    LinearGradient(x0, y0, x1, y1, bloom.argb, fade, Shader.TileMode.CLAMP)
                }

                Falloff.RADIAL -> {
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
     * Gathers [vignette]'s colour in from the edges of [frame], clipped to what the layer has already drawn.
     *
     * **The same source-atop overlay [applyBloom] is, with the ramp run the other way**: the clear end at the middle
     * and the colour at the rim, where a bloom has the colour at a point and clears outward. The disc always spans
     * the frame to its corners — [LayerGradient.radial] at 1 — and where the colour *starts* is the stops' job, so
     * the reach and the softness move the shading without resizing the gradient under it.
     */
    private fun applyVignette(
        canvas: Canvas,
        vignette: LayerEffect.Vignette,
        frame: LayerGradient.Frame,
        sizePx: Int,
    ) {
        val radial = LayerGradient.radial(frame, radiusFraction = 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                radial.centerX,
                radial.centerY,
                radial.radiusPx,
                intArrayOf(LayerGradient.fadeOut(vignette.argb), vignette.argb),
                LayerGradient.rampStops(vignette.clearArea, vignette.softness),
                // Clamped, which is what puts the full colour in the corners: the disc reaches them at a stop of 1,
                // and everything the square holds beyond that stays at the last colour rather than repeating.
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            alpha = (vignette.strength.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    }

    /**
     * [source] with every pixel read from somewhere else along its own radius — the layer seen through water.
     *
     * **The first per-pixel effect, and the first that leaves the canvas entirely.** Everything up to now has been
     * something the platform could draw; this is arithmetic over the pixels, which is exactly what the live path
     * cannot reach below API 33 and what a software bitmap makes free at any API.
     *
     * **Nearest-neighbour, and outside the box reads as transparent.** Clamping to the edge instead would smear the
     * outermost row outward wherever a trough reaches past the box, which reads as a smudge rather than as water —
     * and an icon is transparent out there, so nothing is the truthful sample.
     *
     * The loop itself is [resample], shared with [grained] — extracted on that second consumer rather than in
     * anticipation of it, which is this codebase's usual point.
     */
    private suspend fun rippled(source: Bitmap, ripple: LayerEffect.Ripple, sizePx: Int, bake: CoroutineContext): Bitmap {
        val centerX = LayerRipple.centerPx(ripple.centerX, sizePx)
        val centerY = LayerRipple.centerPx(ripple.centerY, sizePx)
        val amplitudePx = LayerRipple.amplitudePx(ripple, sizePx)
        val wavelengthPx = LayerRipple.wavelengthPx(ripple, sizePx)

        return resample(source, sizePx, bake) { x, y, into ->
            val dx = x - centerX
            val dy = y - centerY
            val distance = hypot(dx, dy)
            val sampled = LayerRipple.sampleDistancePx(distance, amplitudePx, wavelengthPx)

            // Dead centre has no radius to travel along, so it reads from itself — which is also what stops the
            // division from being one by zero.
            into[0] = if (distance == 0f) x.toFloat() else centerX + dx / distance * sampled
            into[1] = if (distance == 0f) y.toFloat() else centerY + dy / distance * sampled
        }
    }

    /**
     * [source] with every pixel read from wherever a noise field pushes it — the artwork torn into pieces.
     *
     * **Two independent fields, one per axis**, which is what [LayerGrain.field]'s salt is for: sampling the same
     * field twice would give every pixel the same displacement in x and y, so the whole picture would shear along
     * the diagonal instead of scattering.
     *
     * Everything about *how* the pair is spent belongs to [LayerGrain.displace] — how much of it is forced onto the
     * effect's angle, and which way that runs. This is the loop and the pixels; that is the arithmetic, where it can
     * be checked without an emulator.
     */
    private suspend fun grained(source: Bitmap, grain: LayerEffect.Grain, sizePx: Int, bake: CoroutineContext): Bitmap {
        val amplitudePx = LayerGrain.amplitudePx(grain, sizePx)
        val cellPx = LayerGrain.cellPx(grain, sizePx)
        // Resolved here rather than inside the loop: it is two transcendental calls, and the angle it reads cannot
        // change within a bake. See [LayerGrain.driftOf].
        val drift = LayerGrain.driftOf(grain)

        return resample(source, sizePx, bake) { x, y, into ->
            // Pixel *centres*, which is [LayerGrain.latticeAt]'s whole job: the field is zero at every lattice point,
            // so sampling corners drops every cellPx-th sample onto nothing.
            val u = LayerGrain.latticeAt(x, cellPx)
            val v = LayerGrain.latticeAt(y, cellPx)
            // **Written into `into` and then read back out of it, rather than into a scratch array of its own.**
            // A scratch held here would be closed over by the lambda and shared by every band — which is the one
            // way this loop's parallelism can go wrong, and it would show as individually wrong pixels scattered
            // through the picture rather than as anything recognisable as a race. `into` is per band by
            // construction, so there is nothing to share.
            LayerGrain.displace(
                drift = drift,
                fieldX = LayerGrain.field(u, v, salt = 0),
                fieldY = LayerGrain.field(u, v, salt = 1),
                into = into,
            )
            into[0] = x + into[0] * amplitudePx
            into[1] = y + into[1] * amplitudePx
        }
    }

    /**
     * [source] blurred, and then let through only where a ramp says so — sharp in one region, soft away from it.
     *
     * **The one effect built from two mechanisms**, which is why it is last: a blurred copy *and* a gradient that
     * decides how much of it shows. [LayerGradient] places the ramp exactly as it does a bloom's, and the blur is
     * [BitmapBlur] — a real one, since this effect shipped with a `Bitmap.scale` down-and-up standing in for it and
     * the result was visibly terraced rather than soft. See [LayerProgressiveBlur.boxRadiusPxOrNull].
     *
     * **Destination-in on the blurred copy, then the sharp one underneath.** The ramp's *alpha* is the mixture, so
     * the blurred layer is erased back to nothing across the sharp region and left whole across the soft one; laying
     * the untouched layer beneath then fills in what was erased. Doing it the other way round — masking the sharp
     * copy — would leave the two overlapping at every partial alpha and the icon looking doubled rather than
     * blurred.
     */
    private fun progressivelyBlurred(
        source: Bitmap,
        blur: LayerEffect.ProgressiveBlur,
        boxRadiusPx: Int,
        sizePx: Int,
    ): Bitmap {
        val blurred = BitmapBlur.blurred(source, boxRadiusPx)

        val stops = LayerGradient.rampStops(blur.sharpArea, blur.softness)
        val frame = LayerGradient.Frame.box(sizePx)
        Canvas(blurred).drawRect(
            0f,
            0f,
            sizePx.toFloat(),
            sizePx.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = rampShader(blur, frame, stops, sizePx)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            },
        )

        val out = createBitmap(sizePx, sizePx)
        Canvas(out).apply {
            drawBitmap(source, 0f, 0f, null)
            drawBitmap(blurred, 0f, 0f, null)
        }
        blurred.recycle()
        return out
    }

    /**
     * The ramp deciding how much blur shows: transparent where the layer stays sharp, opaque where it is fully soft.
     *
     * Only the alpha is read — the destination-in above ignores the colour — so both stops are black and the two
     * numbers doing the work are [stops].
     */
    private fun rampShader(
        blur: LayerEffect.ProgressiveBlur,
        frame: LayerGradient.Frame,
        stops: FloatArray,
        sizePx: Int,
    ): Shader {
        val colors = intArrayOf(0x00000000, 0xFF000000.toInt())
        return when (blur.falloff) {
            Falloff.LINEAR -> {
                val (x0, y0, x1, y1) = LayerGradient.endpoints(frame, blur.angleDegrees).toList()
                LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)
            }

            Falloff.RADIAL -> {
                // The sharp disc is placed by its own centre, so the frame is moved rather than the stops shifted —
                // which is what `Frame.movedBy` is for, and what keeps this the same placement a bloom gets.
                val moved = frame.movedBy(blur.centerX, blur.centerY)
                val radial = LayerGradient.radial(moved, radiusFraction = 1f)
                RadialGradient(radial.centerX, radial.centerY, radial.radiusPx, colors, stops, Shader.TileMode.CLAMP)
            }
        }
    }

    /**
     * [source] redrawn as a field of dots, one colour averaged per cell.
     *
     * **Drawn rather than resampled**, which is why it does not go through [resample]: the gaps between dots and
     * their rounded corners are things *painted*, and a per-pixel sampler has nowhere to put them. That also means
     * the corners come out antialiased for free, where an `IntArray` would have to do its own coverage arithmetic.
     *
     * A cell whose average is fully transparent is skipped rather than drawn — cheap, and it keeps the artwork's
     * outline made of dots rather than of a square block of them.
     */
    private fun pixelated(source: Bitmap, pixelate: LayerEffect.Pixelate, sizePx: Int, bake: CoroutineContext): Bitmap {
        val pixels = IntArray(sizePx * sizePx)
        source.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)

        val cellPx = LayerPixelate.cellPx(pixelate, sizePx)
        val inset = LayerPixelate.insetPx(cellPx, pixelate.fill)
        val dotPx = cellPx - inset * 2f
        val radius = LayerPixelate.cornerRadiusPx(dotPx, pixelate.roundness)

        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Stepped in pixels rather than counted in cells, so the last partial cell at the far edge is drawn too —
        // dropping it would leave a bare strip whose width changed with the cell size.
        var top = 0f
        while (top < sizePx) {
            // Once a row of cells, for [resample]'s reason: this reads every pixel of the source too, so it is the
            // other loop long enough to need abandoning.
            bake.ensureActive()
            var left = 0f
            while (left < sizePx) {
                val argb = LayerPixelate.averageArgb(
                    pixels = pixels,
                    sizePx = sizePx,
                    left = left.toInt(),
                    top = top.toInt(),
                    cellPx = cellPx.toInt().coerceAtLeast(1),
                )
                if (argb ushr 24 != 0) {
                    paint.color = argb
                    canvas.drawRoundRect(
                        left + inset,
                        top + inset,
                        left + cellPx - inset,
                        top + cellPx - inset,
                        radius,
                        radius,
                        paint,
                    )
                }
                left += cellPx
            }
            top += cellPx
        }
        return out
    }

    /**
     * [source] with every output pixel read from wherever [sourceOf] says — the shape both per-pixel effects take.
     *
     * **Outside the box reads as transparent, not clamped.** Clamping would smear the outermost row outward wherever
     * a displacement reaches past the box, which looks like a smudge; an icon *is* transparent out there, so nothing
     * is the truthful sample. Both effects want that, which is part of why the loop is worth sharing rather than
     * being two loops that could answer it differently.
     *
     * **The position is a *fraction* of a pixel and is read as one** — [LayerSample.bilinear] — which is the
     * difference between these effects looking made and looking cheap. Rounding to a whole pixel discarded exactly
     * the part that matters at small amplitudes, where the whole displacement *is* the fraction: a fine grain came
     * out as hard aliased specks rather than as dust, and a shallow ripple stepped instead of flowing. Four reads
     * and a blend per pixel is what that costs.
     *
     * **Rows are split across cores, which is the one optimisation here that helps the home screen too.** Every
     * output pixel reads only the *source* buffer and writes only its own slot, so the loop is parallel with no
     * coordination at all — bands of rows, one coroutine each, and the sum of them is the same picture. It is worth
     * a few lines rather than a shader precisely because it speeds up **baking real icons** as well as the editor's
     * preview, where an AGSL path would only ever have helped the editor.
     *
     * **[BakeBands] leaves a core alone**, which is the same bargain `IconRenderManager`'s concurrency cap makes one
     * layer up: the point of the split is a preview that keeps up with a finger, and taking every core to get there
     * would starve the main thread drawing the panel the finger is on.
     *
     * @param sourceOf writes the source position for output ([x], [y]) into [into] as `[srcX, srcY]`, in pixels and
     *   **not rounded**. An out-parameter rather than a returned pair because this runs once per pixel — six hundred
     *   thousand times at preview size — and a pair there is six hundred thousand allocations. **Called from several
     *   threads at once**, so it must read only what it closed over and write only [into]; every current caller does,
     *   and the scratch each one keeps is now created per band rather than per bake.
     */
    private suspend fun resample(
        source: Bitmap,
        sizePx: Int,
        bake: CoroutineContext,
        sourceOf: SourceOf,
    ): Bitmap {
        val pixels = IntArray(sizePx * sizePx)
        source.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        val out = IntArray(pixels.size)

        overRows(sizePx, bake) { y ->
            // One scratch per row, never one shared across the bands: two threads writing the same two floats is
            // the whole class of bug this split could introduce, and it would show as a scattering of individually
            // wrong pixels rather than as anything that looks like a race. A `FloatArray(2)` per row is a few
            // hundred allocations across a bake, which is nothing beside the pixels themselves.
            val at = FloatArray(2)
            for (x in 0 until sizePx) {
                sourceOf.into(x, y, at)
                out[y * sizePx + x] = LayerSample.bilinear(pixels, sizePx, at[0], at[1])
            }
        }

        val bitmap = createBitmap(sizePx, sizePx)
        bitmap.setPixels(out, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }

    /**
     * Runs [row] for every row of a `sizePx` square, split across cores.
     *
     * **Extracted on its second consumer**, which is the bevel — [resample] had held this inline while it was the
     * only per-pixel pass whose rows were independent. What the two share is the whole of the concurrency: every
     * output pixel reads only buffers nobody writes and writes only its own slot, so bands of rows need no
     * coordination at all and the sum of them is the same picture.
     *
     * **[BakeBands] leaves a core alone**, the same bargain `IconRenderManager`'s concurrency cap makes one layer up:
     * the point of the split is a preview that keeps up with a finger, and taking every core to get there would
     * starve the main thread drawing the panel the finger is on.
     *
     * **[row] is called from several threads at once**, so it must read only what it closed over and write only slots
     * belonging to its own `y`. Anything mutable it needs belongs *inside* it, per row.
     *
     * The cancellation check is once a row, which is what makes a bake abandonable at all — see [render]. A row is
     * short enough that a cancelled preview stops within a millisecond or so, and long enough that the check never
     * shows up in the cost.
     */
    private suspend fun overRows(sizePx: Int, bake: CoroutineContext, row: Rows) {
        val bands = BakeBands.coerceAtMost(sizePx)
        val rowsPerBand = (sizePx + bands - 1) / bands
        coroutineScope {
            for (band in 0 until bands) {
                val from = band * rowsPerBand
                val until = ((band + 1) * rowsPerBand).coerceAtMost(sizePx)
                if (from >= until) continue

                launch(Dispatchers.Default) {
                    for (y in from until until) {
                        bake.ensureActive()
                        row.row(y)
                    }
                }
            }
        }
    }

    /**
     * [source] with a blurred copy of its own silhouette behind it — a glow when it spreads, a shadow when it moves.
     *
     * **`extractAlpha` is the whole of it, and it is why this needs no bitmap arithmetic.** It hands back the
     * silhouette as an `ALPHA_8` mask with the [android.graphics.BlurMaskFilter] already applied, *grown* to fit the
     * blur — hence the offset it fills in, which has to be added back or the halo sits up and to the left of the
     * layer casting it. Drawing that mask with a coloured paint is what turns it into the halo.
     *
     * **The halo is clipped to the icon's box**, which is inherent rather than an oversight: the output is one
     * `sizePx` square and always was. A radius large enough to reach the edge is a radius the user can see reaching
     * the edge, so it corrects itself.
     */
    private fun haloed(
        source: Bitmap,
        argb: Int,
        strength: Float,
        radiusPx: Float?,
        spreadPx: Float,
        dxPx: Float,
        dyPx: Float,
        sizePx: Int,
    ): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)

        val grown = if (spreadPx > 0f) dilated(source, spreadPx, sizePx) else source
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            // Set **after** the colour, which carries its own alpha and would otherwise overwrite this.
            alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
        }

        if (radiusPx == null) {
            // No blur asked for: the silhouette itself, which is a hard-edged shadow and a legitimate thing to want.
            canvas.drawBitmap(grown, dxPx, dyPx, halo.apply { colorFilter = solidFilter(argb) })
        } else {
            val offset = IntArray(2)
            val blur = Paint().apply { maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL) }
            val mask = grown.extractAlpha(blur, offset)
            canvas.drawBitmap(mask, offset[0] + dxPx, offset[1] + dyPx, halo)
            mask.recycle()
        }

        canvas.drawBitmap(source, 0f, 0f, null)
        if (grown !== source) grown.recycle()
        return out
    }

    /**
     * [source] read as a raised surface and lit — the bevel.
     *
     * **The one effect here built from a *neighbourhood* rather than a point**, which is why it does not go through
     * [resample]: that helper asks "which single pixel does this one read?" and answers with a bilinear sample,
     * where this asks how the surface is *tilted* at each pixel and answers with a colour. What the two do share is
     * the row split, which is [overRows].
     *
     * Three steps, and each is somewhere else. The **height map** is the layer's own alpha blurred, which is
     * `extractAlpha` doing the same job it does for a halo. The **slope** is a Sobel gradient of that, scaled by
     * [LayerBevel.slopeScale] so the bevel's strength does not follow its width. The **lighting** is
     * [LayerBevel.relief], which is where every sign that could be wrong lives.
     *
     * **The two bands are blended per pixel rather than composited as buffers**, which is a correction rather than
     * an economy. A slope facing the light is *screened* and one facing away is *multiplied*, and the obvious way to
     * get that — two band bitmaps drawn with `PorterDuff.Mode.SCREEN` and `MULTIPLY` — **erased the icon**. Those
     * modes are not the blends of the same name: multiply is `[Sa × Da, Sc × Dc]`, so the result *alpha* is the
     * product too, and a band that is transparent across most of the artwork multiplies its alpha by zero. What was
     * left on screen was the shaded slopes alone, on a canvas of nothing.
     *
     * Doing both blends in [LayerBevel.lit] keeps the artwork's own alpha untouched by construction, needs no band
     * buffers and no trim, and works at every API — where the honest canvas fix would have been `BlendMode`, which
     * is API 29 against a `minSdk` of 26.
     */
    private suspend fun bevelled(
        source: Bitmap,
        bevel: LayerEffect.Bevel,
        radiusPx: Float,
        sizePx: Int,
        bake: CoroutineContext,
    ): Bitmap {
        val heights = blurredAlpha(source, radiusPx, sizePx)
        val light = LayerBevel.light(bevel.angleDegrees, bevel.altitudeDegrees)
        val scale = LayerBevel.slopeScale(radiusPx)

        val pixels = IntArray(sizePx * sizePx)
        source.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        val out = IntArray(pixels.size)

        overRows(sizePx, bake) { y ->
            for (x in 0 until sizePx) {
                val at = y * sizePx + x
                val pixel = pixels[at]
                // Nothing to light where there is no surface. Also the common case by far, an icon being mostly
                // transparent, so it is worth skipping the twelve neighbourhood reads below.
                if (pixel ushr 24 == 0) {
                    out[at] = pixel
                    continue
                }

                // Sobel over the height field, divided by its own weight so the result is a rise per pixel. Sampled
                // through `clamp`, so the box's own border reads as a continuation of itself rather than as a cliff
                // — an edge treated as a drop would light the whole rim of every full-bleed layer.
                val slopeX = scale * (
                    heights.at(x + 1, y - 1, sizePx) + 2f * heights.at(x + 1, y, sizePx) +
                        heights.at(x + 1, y + 1, sizePx) - heights.at(x - 1, y - 1, sizePx) -
                        2f * heights.at(x - 1, y, sizePx) - heights.at(x - 1, y + 1, sizePx)
                    ) / 8f
                val slopeY = scale * (
                    heights.at(x - 1, y + 1, sizePx) + 2f * heights.at(x, y + 1, sizePx) +
                        heights.at(x + 1, y + 1, sizePx) - heights.at(x - 1, y - 1, sizePx) -
                        2f * heights.at(x, y - 1, sizePx) - heights.at(x + 1, y - 1, sizePx)
                    ) / 8f

                out[at] = LayerBevel.lit(pixel, LayerBevel.relief(slopeX, slopeY, light), bevel)
            }
        }

        val bitmap = createBitmap(sizePx, sizePx)
        bitmap.setPixels(out, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }

    /**
     * [source]'s own alpha, blurred by [radiusPx] and aligned back to the box — the bevel's height map.
     *
     * `extractAlpha` hands back a mask **grown** to fit the blur, with the offset it grew by; drawing it back at that
     * offset is what re-aligns it with the layer, and forgetting to would slide the whole relief up and to the left
     * of the artwork casting it.
     */
    private fun blurredAlpha(source: Bitmap, radiusPx: Float, sizePx: Int): FloatArray {
        val offset = IntArray(2)
        val blur = Paint().apply { maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL) }
        val mask = source.extractAlpha(blur, offset)

        val aligned = createBitmap(sizePx, sizePx)
        Canvas(aligned).drawBitmap(mask, offset[0].toFloat(), offset[1].toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        mask.recycle()

        val pixels = IntArray(sizePx * sizePx)
        aligned.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        aligned.recycle()
        return FloatArray(pixels.size) { (pixels[it] ushr 24) / 255f }
    }

    /** The height at ([x], [y]), with the box's own border reading as a continuation rather than as a cliff. */
    private fun FloatArray.at(x: Int, y: Int, sizePx: Int): Float =
        this[y.coerceIn(0, sizePx - 1) * sizePx + x.coerceIn(0, sizePx - 1)]

    /**
     * [source] with a hard band of colour following its silhouette — the stroke.
     *
     * **No drawing of its own at all**, which is the whole of why this effect was cheap: an outside stroke is
     * [haloed] with no blur, an inside stroke is [insetHaloed] with no blur, and a centred one is both. The
     * dilation each of those already performs *is* the stroke once nothing softens it.
     *
     * **The centred case runs inside first, and the order is load-bearing.** [insetHaloed] trims its band to the
     * artwork, so it changes no alpha at all — which means the silhouette [haloed] then grows outward is still the
     * artwork's own edge. The other way round, the outward band would have fattened the silhouette first and the
     * inward one would then be measured from the *stroke's* edge, putting the whole thing a width too far out.
     */
    private fun outlined(source: Bitmap, outline: LayerEffect.Outline, sizePx: Int): Bitmap {
        val widthPx = LayerShadow.spreadPx(outline.perSideWidth, sizePx)

        fun outward(from: Bitmap): Bitmap = haloed(
            source = from,
            argb = outline.argb,
            strength = outline.strength,
            // No blur is what makes a halo a stroke — the dilation's own edge, undisturbed.
            radiusPx = null,
            spreadPx = widthPx,
            dxPx = 0f,
            dyPx = 0f,
            sizePx = sizePx,
        )

        fun inward(from: Bitmap): Bitmap = insetHaloed(
            source = from,
            argb = outline.argb,
            strength = outline.strength,
            radiusPx = null,
            spreadPx = widthPx,
            dxPx = 0f,
            dyPx = 0f,
            blend = null,
            sizePx = sizePx,
        )

        return when (outline.position) {
            OutlinePosition.OUTSIDE -> outward(source)
            OutlinePosition.INSIDE -> inward(source)
            OutlinePosition.CENTER -> inward(source).let { inner ->
                outward(inner).also { inner.recycle() }
            }
        }
    }

    /**
     * [source] with a blurred copy of everything **outside** it laid back **inside** its own silhouette — a recess
     * when [blend] is plain, a rim light when it screens.
     *
     * [haloed] turned outside in, and it shares that method's pieces rather than restating them: the same
     * [LayerShadow] numbers, the same [dilated] for the choke, the same `extractAlpha` + [BlurMaskFilter] for the
     * softening. Three things about it are worth knowing.
     *
     * **One function for both inner effects**, extracted when the rim arrived — the second consumer, as usual. They
     * differ in exactly two arguments: a recess is thrown so it takes an offset and lays its band on plainly, a rim
     * is centred on the edge it lights so it takes none and screens. Everything between the complement and the trim
     * is identical, which is precisely the kind of near-copy that drifts if it is written twice.
     *
     * **The halo is trimmed in its own buffer rather than by the composite**, which is what made that possible. The
     * first cut relied on source-atop to do the clipping *and* the compositing at once — correct for a shadow and
     * impossible for anything that has to add light, since the mode is then spent. Destination-in first, any mode
     * after.
     *
     * **The complement is built in a padded buffer, and that is the part that would be silently wrong.** An inner
     * halo is cast by what surrounds the artwork; a layer whose artwork reaches the icon's box has nothing
     * surrounding it *within* the bitmap, so it would fade in from nothing along exactly those edges — and a
     * full-bleed background plate is the commonest thing anyone recesses. Padded, the region beyond the box is
     * genuinely filled and the blur gathers from it. See [LayerShadow.innerMarginPx].
     *
     * @param blend how the trimmed halo joins the layer — `null` for plain source-over, which is a recess, and
     *   [PorterDuff.Mode.SCREEN] for light that brightens the artwork's own colours rather than covering them.
     */
    private fun insetHaloed(
        source: Bitmap,
        argb: Int,
        strength: Float,
        radiusPx: Float?,
        spreadPx: Float,
        dxPx: Float,
        dyPx: Float,
        blend: PorterDuff.Mode?,
        sizePx: Int,
    ): Bitmap {
        val marginPx = LayerShadow.innerMarginPx(radiusPx, spreadPx, dxPx, dyPx)
        val paddedPx = sizePx + marginPx * 2

        val outside = complementOf(source, marginPx, paddedPx)
        // A choke grows the *complement*, which is the same thing as shrinking the opening the halo falls into.
        val grown = if (spreadPx > 0f) dilated(outside, spreadPx, paddedPx) else outside

        // **The halo is built in its own buffer and trimmed there**, which is what let one function serve both
        // effects. Trimming with destination-in first means the composite below is free to be *any* mode: a recess
        // lays its band on plainly, a rim screens its light onto the artwork's own colours, and neither has to
        // double as the clip the way a lone source-atop did.
        val halo = createBitmap(sizePx, sizePx)
        val haloCanvas = Canvas(halo)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }

        if (radiusPx == null) {
            // No blur asked for: a hard band, which is the flat inset a stamped label has. The complement is an
            // ordinary bitmap rather than a mask, so its colour is replaced the way [haloed] replaces one.
            paint.colorFilter = solidFilter(argb)
            haloCanvas.drawBitmap(grown, dxPx - marginPx, dyPx - marginPx, paint)
        } else {
            val offset = IntArray(2)
            val blur = Paint().apply { maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL) }
            val mask = grown.extractAlpha(blur, offset)
            haloCanvas.drawBitmap(mask, offset[0] + dxPx - marginPx, offset[1] + dyPx - marginPx, paint)
            mask.recycle()
        }
        // Trimmed to the artwork, which is the whole of what makes this halo an *inner* one.
        haloCanvas.drawBitmap(source, 0f, 0f, maskPaint)

        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawBitmap(
            halo,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                blend?.let { xfermode = PorterDuffXfermode(it) }
                alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
            },
        )

        halo.recycle()
        if (grown !== outside) grown.recycle()
        outside.recycle()
        return out
    }

    /**
     * Everything [source] is **not**, in a [paddedPx] square with [source] drawn [marginPx] in from its corner.
     *
     * **A filled rectangle with the silhouette punched out of it**, which is an alpha inversion reached without one:
     * destination-out leaves `dstAlpha × (1 − srcAlpha)`, so a solid buffer minus the artwork is exactly the region
     * around it. The plan expected this to need an alpha-inverting colour matrix; a matrix would have had to reason
     * about premultiplication to invert an alpha channel, where two canvas calls simply do not.
     *
     * The colour is arbitrary and never seen — only the alpha survives, the caller replacing the colour or drawing
     * the extracted mask.
     */
    private fun complementOf(source: Bitmap, marginPx: Int, paddedPx: Int): Bitmap {
        val out = createBitmap(paddedPx, paddedPx)
        val canvas = Canvas(out)
        canvas.drawColor(OpaqueBlack)
        canvas.drawBitmap(source, marginPx.toFloat(), marginPx.toFloat(), punchPaint)
        return out
    }

    /**
     * [source] grown by [spreadPx] — its own silhouette swept around a ring, which is a dilation approximated the
     * only way a canvas offers.
     *
     * `LayerExtrude`'s problem in two dimensions, and cheap here for a reason that one is not: this effect never
     * draws live, so the copies are blits of a bitmap the bake already holds rather than re-runs of a layer's
     * content per frame.
     */
    private fun dilated(source: Bitmap, spreadPx: Float, sizePx: Int): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val steps = LayerShadow.spreadSteps(spreadPx)

        for (step in 0 until steps) {
            val radians = step * 2f * Math.PI.toFloat() / steps
            canvas.drawBitmap(source, cos(radians) * spreadPx, sin(radians) * spreadPx, null)
        }
        // The un-displaced copy as well, or a spread larger than the artwork would leave a hole in the middle.
        canvas.drawBitmap(source, 0f, 0f, null)
        return out
    }

    /** Flattens whatever is drawn to [argb], keeping its alpha — the un-blurred halo's equivalent of the mask. */
    private fun solidFilter(argb: Int) = ColorMatrixColorFilter(ColorMatrix(LayerFilter.solidMatrixOf(argb)))

    /**
     * [source] as its three colour channels, displaced and added back together.
     *
     * **Additive rather than layered**, which is what makes the channels recombine into the original colour where
     * they overlap and leave a single-channel fringe where they do not — the whole of the effect. Drawing them with
     * ordinary source-over would stack three coloured silhouettes and the last would simply win.
     */
    private fun split(source: Bitmap, split: LayerEffect.ChromaticSplit, sizePx: Int): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)

        LayerChromatic.fringes(split, sizePx).forEach { fringe ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(fringe.matrix))
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            canvas.drawBitmap(source, fringe.dxPx, fringe.dyPx, paint)
        }
        return out
    }

    /**
     * Tiles [pattern] over the layer, clipped to what it has already drawn.
     *
     * An **unknown id draws nothing**, which is the same degrade `IconShapes` and `IconFilters` take: a recipe from a
     * later build loses one effect rather than failing to render at all.
     */
    private fun applyPattern(canvas: Canvas, pattern: LayerEffect.Pattern, sizePx: Int) {
        val res = IconPatterns.drawableResOrNull(pattern.pattern) ?: return
        // `mutate` for [shapeMaskOrNull]'s reason — a pattern is a vector too, and `LayerPattern.tile` rasterizes it
        // at a size derived from the bake's, so two bakes would thrash one shared cache.
        val drawable = context.getDrawable(res)?.mutate() ?: return
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
     * The one-shot form, for the caller that masks once: build, apply, discard. The whole icon's own mask is applied
     * at both ends of its effect pipeline (see [render]), so it holds the silhouette itself rather than rasterising the
     * same drawable twice.
     */
    private fun applyShapeMask(canvas: Canvas, shape: IconShape, sizePx: Int, matrix: Matrix?) {
        val mask = shapeMaskOrNull(shape, sizePx, matrix) ?: return
        applyShapeMask(canvas, mask)
        mask.recycle()
    }

    /**
     * [shape]'s silhouette as a bitmap, or `null` for an id this build does not know — which stale stored data can
     * still produce, and which then masks nothing rather than failing.
     *
     * The bounds are always the full box: [matrix] is what places the silhouette, so the drawable is asked for its
     * authoring square either way and the anchor is expressed in one place rather than two.
     */
    private fun shapeMaskOrNull(shape: IconShape, sizePx: Int, matrix: Matrix?): Bitmap? {
        val res = IconShapes.drawableResOrNull(shape) ?: return null
        // **`mutate` because the instance is fresh and its constant state is not.** `getDrawable` hands back a new
        // `Drawable` over a *shared* state, and a `VectorDrawable` — which every shape is — caches a rendered bitmap
        // in there, so two bakes masking with the same shape at two sizes would fight over that one cache.
        val shapeDrawable = context.getDrawable(res)?.mutate() ?: return null
        val mask = createBitmap(sizePx, sizePx)
        Canvas(mask).let { maskCanvas ->
            shapeDrawable.setBounds(0, 0, sizePx, sizePx)
            if (matrix == null) shapeDrawable.draw(maskCanvas)
            else maskCanvas.withMatrix(matrix) { shapeDrawable.draw(this) }
        }
        return mask
    }

    /** Keeps only what [mask]'s silhouette covers. The mask stays the caller's, since one can be applied twice. */
    private fun applyShapeMask(canvas: Canvas, mask: Bitmap) {
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
    }

    /**
     * Any opaque colour will do for a buffer whose alpha is the only thing that survives — see [complementOf].
     */
    private val OpaqueBlack = 0xFF000000.toInt()

    private fun decodeCustomImage(path: String): Drawable? =
        BitmapFactory.decodeFile(path)?.toDrawable(context.resources)

}
