package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A lattice of colored nodes, blended smoothly and pushed out of shape — the *Mesh Gradient*.
 *
 * **A bilinear mesh sampled through a warp, not points weighted by distance.** The frame is a grid of nodes, each
 * carrying a color; a pixel's color is the bilinear blend of the four nodes around it, read at a coordinate that a
 * *second, much coarser* lattice of displacements has pushed off true. The two halves are separable and each says
 * one thing: the colors say what the gradient is, the displacements say how it bulges.
 *
 * **The warp lattice is deliberately fixed at [WarpSide] patches rather than tied to the color lattice.** A
 * displacement field as fine as the colors puts a tongue one cell wide wherever a node is pushed hard, and a tongue
 * as tall as it is wide reads as a *drip* — a stalactite hanging off a band — rather than as the broad lobe this
 * design wants. Two movable nodes per axis is about as much frequency as a bulge can carry, and holding it there
 * also stops the density knob quietly changing the character of the warp knob.
 *
 * **This replaced inverse-distance weighting, and the reason is visible at the rigid end.** Weighting every node by
 * `1/(d² + ε)` leaves each one a core, and between two same-colored nodes a pixel sits fractionally further from both,
 * so the neighbouring row leaks in and the field **beads** into columns — a stripe pattern nobody asked for, which
 * survives any amount of softening short of flattening the design into a plain gradient. A bilinear patch has no cores
 * at all: at `warp = 0` it *is* the exact gradient, which is what the reference shows and what the old blend could not
 * reach at any setting. It is also `O(1)` per pixel rather than `O(nodes)`.
 *
 * **[DesignParams.variant] is how the palette is laid over the lattice, and it is what the design lives or dies by.**
 * Cycling the stops through the nodes — the only thing this did at first — puts a different color beside every color,
 * so the frame comes out a quilt of blotches with mud between them. *Vertical* instead reads a node's color off the
 * ramp at its **row**, which turns the same machinery into a progression down the frame that the warp bulges
 * organically; *Corners* interpolates four ramp samples across the lattice, for a calm two-way wash. The cycle
 * survives as *Scattered*, for a palette that is a set of accents rather than a ramp.
 *
 * **[DesignParams.scale] is softness, and it works on the colors rather than on the blend** — each node is drawn
 * toward the average of its neighbours, [SoftenPasses] times. That is what washing out actually is here: the ramp
 * keeps its ends and loses its middle contrast. There is nothing left to soften in the blend, which is already exact.
 *
 * [mesh] is pure and tested: which node takes which color decides whether the bands run across the frame or diagonally
 * across it, and the boundary displacements must stay pinned or the frame's own corners drift off the distribution —
 * both silent when wrong, and neither needs a bitmap.
 */
object MeshGradientGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — patches per side, and the slider's own range. */
    private val Amount = AmountKnob.Count("Grid", 2..8)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Softness",
        irregularity = "Warp",
        variant = VariantKnob("Colors", listOf("Vertical", "Corners", "Scattered")),
    )

    /**
     * The lattice: `([side] + 1)²` nodes in row-major order, each with a color and a displacement.
     *
     * @property side patches per axis on the **color** lattice, so there are `side + 1` color nodes per axis.
     * @property colors each color node's ARGB, `(side + 1)²`.
     * @property dx horizontal displacement per **warp** node, in unit-square coordinates; **zero on the boundary**.
     *   Its lattice is `([WarpSide] + 1)²`, a different and coarser grid — see the class note.
     * @property dy vertical displacement per warp node.
     */
    internal class Mesh(val side: Int, val colors: IntArray, val dx: FloatArray, val dy: FloatArray) {

        /** Color nodes per axis. */
        val span: Int get() = side + 1
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        paint(plan(params, palette, seed), bitmap)
        return bitmap
    }

    /**
     * The lattice [params], [palette] and [seed] describe — this design's plan, and the whole of what its picture is
     * a function of.
     *
     * **It takes no size at all**, where the primitive bucket's plans take one for their aspect: a [Mesh] lives in the
     * unit square and is read at whatever resolution it is asked for. That is the field bucket's shape, and it is why
     * these designs downscale for a scrub while the primitive ones must not — see docs/MORPH_ENGINE_PLAN.md.
     *
     * **The colors are resolved here rather than kept as ramp positions**, which is a deliberate departure from the
     * morph plan's rule and the one design that cannot follow it. A node's color is not a position on the ramp: under
     * *Corners* it is a blend of four ramp samples, and [soften] then draws every node channel-wise toward its
     * neighbours' mean, which is a *color* average with no meaning in ramp space. Keeping positions would mean
     * softening a different quantity, which is a different design. What it costs is that a palette change re-plans
     * rather than merely re-draws, and that two palettes cannot be interpolated across a scrub — neither of which any
     * caller asks for, since a shuffle keeps the palette and a palette change re-renders anyway.
     */
    internal fun plan(params: DesignParams, palette: Palette, seed: Long): Mesh =
        mesh(Amount.at(params.density), params.irregularity, params.scale, params.variant, palette, seed)

    /**
     * Paints [mesh] into [canvas] at `[width]` × `[height]`, evaluating the field on a buffer whose short side is
     * [shortSide] pixels and letting the canvas scale it up.
     *
     * **Evaluating small and scaling up is this bucket's whole economy.** The cost is per *pixel*, not per element,
     * so a scrub frame at a fraction of the size costs a fraction as much — and it is faithful rather than merely
     * cheap, because the field is smooth by construction: a bilinear mesh read through a smoothstepped warp has no
     * detail finer than its own lattice to lose. The primitive bucket is exactly the other way round, which is the
     * inversion the morph plan is sized by.
     *
     * Defaulting [shortSide] to the frame's own leaves the bake at full resolution, where this is one buffer copied
     * once.
     */
    internal fun draw(canvas: Canvas, mesh: Mesh, width: Int, height: Int, shortSide: Int = min(width, height)) {
        val frame = min(width, height)
        val scale = if (frame <= 0) 1f else (shortSide.toFloat() / frame).coerceAtMost(1f)
        val source = createBitmap(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
        )
        paint(mesh, source)
        canvas.drawBitmap(source, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), FieldPaint)
        source.recycle()
    }

    /**
     * The pixel loop, and the only place it exists — [render] runs it straight into the bitmap it hands back, and
     * [draw] runs it into whatever buffer a scrub can afford.
     *
     * Shared rather than written twice because a field evaluated two ways is the same hazard as a picture drawn two
     * ways, and quieter: the two would agree at every setting anyone checked and drift wherever they were not.
     */
    private fun paint(mesh: Mesh, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val row = IntArray(width)
        for (y in 0 until height) {
            val v = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val u = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val wu = u + sample(mesh.dx, WarpSpan, u, v)
                val wv = v + sample(mesh.dy, WarpSpan, u, v)
                row[x] = sampleColor(mesh, wu, wv)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
    }

    /**
     * Two lattices prepared to interpolate, or **null where they are not the same shape**.
     *
     * **A field plan is a struct, so there is nothing to pair and nothing to resample** — every node of one lattice
     * has an obvious counterpart in the other, and a moment between them is one linear pass over two arrays. That is
     * the whole of the field bucket's morph, and the contrast with the primitive bucket's is the point: there, the
     * elements are a *set* with no natural correspondence, and finding one is the hard part.
     *
     * The lattices differ in size only when the two recipes differ in density, which a shuffle never does. Resampling
     * one onto the other is what a scrub *between densities* would need and there is no such caller.
     */
    internal fun morph(from: Mesh, to: Mesh): Morph? =
        if (from.side != to.side) null else Morph(from, to)

    /**
     * A field scrub: the lattice interpolates and the field is re-evaluated on a small buffer, blown up by the canvas.
     *
     * **The downscale is where this bucket's affordability comes from**, and its resolution is the design's own measured
     * floor rather than one number for everything — see [ScrubShortSide].
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(params, palette, from), plan(params, palette, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), w, h, ScrubShortSide) }
    }

    /** Two lattices and every moment between them. */
    internal class Morph(private val from: Mesh, private val to: Mesh) {

        /** The lattice [t] of the way across; the ends are the plans themselves, as the primitive bucket's are. */
        fun at(t: Float): Mesh = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Mesh(
                side = from.side,
                colors = IntArray(from.colors.size) {
                    LinearGradientGenerator.lerpArgb(from.colors[it], to.colors[it], t)
                },
                dx = FloatArray(from.dx.size) { from.dx[it] + (to.dx[it] - from.dx[it]) * t },
                dy = FloatArray(from.dy.size) { from.dy[it] + (to.dy[it] - from.dy[it]) * t },
            )
        }
    }

    /**
     * The lattice for [side] patches: colors from [variant] over [palette], softened by [softness], displaced by
     * [warp], all drawn from [seed].
     *
     * **Boundary nodes are never displaced.** The warp moves where the *inside* of the picture is read from; moving
     * the edge nodes would drag the frame's own corners off whatever the distribution put there — a *Corners* wash
     * whose corners are not the palette's ends reads as the design being slightly wrong rather than as anything
     * having moved.
     */
    internal fun mesh(side: Int, warp: Float, softness: Float, variant: Int, palette: Palette, seed: Long): Mesh {
        val n = side.coerceAtLeast(1)
        val span = n + 1
        val colors = IntArray(span * span)
        for (r in 0 until span) {
            for (c in 0 until span) {
                val i = r * span + c
                colors[i] = nodeColor(c.toFloat() / n, r.toFloat() / n, i, variant, palette)
            }
        }
        soften(colors, span, softness)

        val dx = FloatArray(WarpSpan * WarpSpan)
        val dy = FloatArray(WarpSpan * WarpSpan)
        val reach = warp.coerceIn(0f, 1f) * MaxWarp
        val random = Random(seed)
        for (r in 0 until WarpSpan) {
            for (c in 0 until WarpSpan) {
                // Both draws happen for every node, boundary included, so the warp slider re-shapes the same
                // lattice instead of reshuffling it — the discipline every jittered design here keeps.
                val jx = (random.nextFloat() * 2f - 1f) * reach
                val jy = (random.nextFloat() * 2f - 1f) * reach
                if (r in 1 until WarpSpan - 1 && c in 1 until WarpSpan - 1) {
                    dx[r * WarpSpan + c] = jx
                    dy[r * WarpSpan + c] = jy
                }
            }
        }
        return Mesh(n, colors, dx, dy)
    }

    /**
     * The color for the node at ([u], [v]) in the lattice — both `0..1` — under [variant].
     *
     * *Vertical* ignores [u] entirely, which is the point: the color is a function of how far down the frame the node
     * sits, so the palette reads as one progression rather than as a set of patches.
     */
    internal fun nodeColor(u: Float, v: Float, index: Int, variant: Int, palette: Palette): Int = when (variant) {
        VariantCorners -> {
            val top = LinearGradientGenerator.lerpArgb(rampAt(0, palette), rampAt(1, palette), u)
            val bottom = LinearGradientGenerator.lerpArgb(rampAt(2, palette), rampAt(3, palette), u)
            LinearGradientGenerator.lerpArgb(top, bottom, v)
        }

        VariantScattered -> palette.colorAt(index % palette.size)
        else -> LinearGradientGenerator.colorAt(v, palette)
    }

    /** The [corner]-th of [CornerSamples] samples evenly spaced along the palette ramp. */
    private fun rampAt(corner: Int, palette: Palette): Int {
        val t = corner.toFloat() / (CornerSamples - 1)
        return LinearGradientGenerator.colorAt(t, palette)
    }

    /**
     * Draws every node [strength] of the way toward the average of its four lattice neighbours, [SoftenPasses] times —
     * the softness knob. Each pass reads a copy, so a node's new value never feeds the one beside it in the same pass.
     */
    internal fun soften(colors: IntArray, span: Int, strength: Float) {
        val t = strength.coerceIn(0f, 1f)
        if (t <= 0f || span < 2) return
        repeat(SoftenPasses) {
            val source = colors.copyOf()
            for (r in 0 until span) {
                for (c in 0 until span) {
                    val mean = neighbourMean(source, span, r, c) ?: continue
                    colors[r * span + c] = LinearGradientGenerator.lerpArgb(source[r * span + c], mean, t)
                }
            }
        }
    }

    /** The channel-wise average of the four lattice neighbours of `([r], [c])`, or `null` where there are none. */
    private fun neighbourMean(source: IntArray, span: Int, r: Int, c: Int): Int? {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        var count = 0
        for ((nr, nc) in listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)) {
            if (nr !in 0 until span || nc !in 0 until span) continue
            val argb = source[nr * span + nc]
            val a = argb ushr AlphaShift and ChannelMask
            val rd = argb shr RedShift and ChannelMask
            val gn = argb shr GreenShift and ChannelMask
            val bl = argb and ChannelMask
            alpha += a
            red += rd
            green += gn
            blue += bl
            count++
        }
        if (count == 0) return null
        val mean = (alpha / count shl AlphaShift) or (red / count shl RedShift) or
            (green / count shl GreenShift) or (blue / count)
        return mean
    }

    /**
     * The sample of the per-node scalar [field] on a `[span] × [span]` lattice at ([u], [v]) in `0..1`, **smoothstepped
     * within each cell**.
     *
     * A plain bilinear field is only C0: its slope jumps at every node line, and because this field is used to
     * *displace a coordinate*, those jumps land in the picture as hard creases running along the lattice — vertical
     * drips down a gradient that is supposed to bulge. Easing the cell parameter is the cheapest fix that makes the
     * field C1, and it is why this is not the same routine as [ColorLattice.sample]: a kink in a monotone color ramp is
     * invisible, a kink in a warp is not.
     */
    private fun sample(field: FloatArray, span: Int, u: Float, v: Float): Float {
        val fx = u.coerceIn(0f, 1f) * (span - 1)
        val fy = v.coerceIn(0f, 1f) * (span - 1)
        val x0 = fx.toInt().coerceIn(0, span - 1)
        val y0 = fy.toInt().coerceIn(0, span - 1)
        val x1 = (x0 + 1).coerceAtMost(span - 1)
        val y1 = (y0 + 1).coerceAtMost(span - 1)
        val tx = Easing.smoothstep(fx - x0)
        val ty = Easing.smoothstep(fy - y0)
        val top = field[y0 * span + x0] + (field[y0 * span + x1] - field[y0 * span + x0]) * tx
        val bottom = field[y1 * span + x0] + (field[y1 * span + x1] - field[y1 * span + x0]) * tx
        return top + (bottom - top) * ty
    }

    /** The bilinear sample of [mesh]'s colors at ([u], [v]) — clamped, so a warp off the edge reads the edge. */
    internal fun sampleColor(mesh: Mesh, u: Float, v: Float): Int =
        ColorLattice.sample(mesh.colors, mesh.span, mesh.span, u, v)

    /**
     * What a downscaled field is blown back up with — bilinear, which for a field is exact rather than merely tidy.
     *
     * Nearest-neighbour would show the buffer's own pixel grid, which is the one artifact that would make a scrub
     * look like a *preview* of the picture rather than the picture. There is no dithering to preserve and no edge to
     * keep crisp: every gradient here is continuous, so the filter is reconstructing the field rather than guessing.
     */
    private val FieldPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * The short side a scrub frame of this design is evaluated at — **measured, not guessed** (2026-09-10,
     * `FieldDownscaleHarness`).
     *
     * Swept over every corner of the knob space at nine resolutions and compared against the full-resolution render,
     * the worst case is the finest colour lattice (density 8) under *Scattered* at full warp. There, evaluating at a
     * short side of `120` leaves a mean difference of **1.2 of 255** with **0.001%** of pixels differing by more than
     * four levels — reconstruction, not a changed picture. At `60` that share is 1.7% and at `30` it is 36%, which is
     * the lattice starting to be lost rather than softened.
     *
     * **`DraftShortSidePx`'s 360 is Contour's number and this design owes it nothing**, which is the whole point of
     * the measurement: a third of the short side is a *ninth* of the pixels, and this bucket's cost is per pixel. A
     * full-frame phone scrub is 2.6M pixels; this is 32 thousand.
     */
    internal const val ScrubShortSide = 120

    /** How many samples of the ramp *Corners* spreads across the lattice — one per corner. */
    private const val CornerSamples = 4

    /** Where each channel sits in a packed ARGB int, and the byte that reads it. */
    private const val AlphaShift = 24
    private const val RedShift = 16
    private const val GreenShift = 8
    private const val ChannelMask = 0xFF

    /** [DesignParams.variant] values this design draws differently; anything else is *Vertical*, the default look. */
    private const val VariantCorners = 1
    private const val VariantScattered = 2

    /**
     * How far a warp node can be pushed at full warp, as a fraction of the frame. Past about this the field folds
     * through itself and the bulge stops reading as one shape.
     */
    private const val MaxWarp = 0.22f

    /** Patches per axis on the warp lattice — two movable nodes each way. See the class note on why it is fixed. */
    private const val WarpSide = 3

    /** Warp nodes per axis. */
    private const val WarpSpan = WarpSide + 1

    /** Smoothing passes the softness knob spends; two reaches a flat wash without a loop nobody can feel the end of. */
    private const val SoftenPasses = 2
}
