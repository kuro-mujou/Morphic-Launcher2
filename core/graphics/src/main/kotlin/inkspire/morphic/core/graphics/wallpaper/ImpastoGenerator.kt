package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ragged translucent dabs laid along a sweep across the frame, building into a painted wash — the impasto (gart's
 * `arts/monet`).
 *
 * **The catalog's only painterly design.** Everything else here is a *field* (a value per pixel) or a *shape* (an edge
 * you can trace). This is neither: a heap of overlapping marks whose torn edges are the texture, where the picture is
 * what the marks do together and no single one of them is legible.
 *
 * Three mechanisms carry it, and each was got wrong once before the render said so.
 *
 * **1. A layer is a random walk.** gart's `deformPath` inserts a displaced midpoint into every edge and is run ten
 * times at a **constant** displacement — the push never decays, so the boundary is Brownian: as ragged at its finest
 * scale as at its coarsest. Replacing that with a tidier fractal whose push halves each round gives a compact
 * hard-edged blob, and the frame then reads as a paper collage. [dabPoints] keeps the constant push. What is ours is
 * the **bound**: ten rounds double the point count to 8,192 a layer, which at fifty layers and four hundred places is
 * some hundred and sixty million points a frame — fine on a desktop JVM with Skija, not on a phone.
 *
 * **2. The excursion's size against the blot's is what has to match, not the round count.** gart's push is an eighth
 * of its blot spread over 8,192 boundary points, which reads as fuzz; the same walk taken as `radius / √rounds` over
 * 128 points is several times taller than its neighbor is wide and reads as a ring of coarse triangles. [SeedShare]
 * carries the size and [PushShare] only roughens the rim, which puts the fuzz back at a fortieth of the points.
 *
 * **3. The marks are drawn *interleaved*, and this is the one that makes it a painting.** gart chunks each mark's
 * fifty layers into five groups and draws group `n` of **every** mark before group `n+1` of any of them, so no mark is
 * ever finished before its neighbors are started. Drawing each mark to completion instead — the obvious way, and what
 * the first two builds did — lets a finished opaque dab cover the one beneath it, and the frame comes out as
 * discrete blobs with hard edges however soft each blob's own rim is. Interleaved, every mark is translucent while
 * its neighbors are laid over and under it, and the colors *mix* where they meet. It is the difference between a
 * collage and a wash, and it is free.
 *
 * **The geometry is derived from the brush, because that is what all three mechanisms are measured against.** Read
 * off gart's own frame: its blot is about `0.15` of the side, its serpentine's sweeps are `0.34` of a blot apart and
 * its marks `0.68` of one along the path — so every point of the frame is inside three or four blots and the wash is
 * a consequence. A fixed sweep count and a fixed mark count cannot hold that at another aspect or another brush size;
 * [DesignParams.scale] therefore sets the blot and the rest follows, with [DesignParams.density] scaling only how
 * closely the marks are spaced along the path.
 *
 * **The ground is the palette's first stop and the marks are the tones above it** ([RampTones]), as
 * [DiagonalBandsGenerator]'s bands are — gart clears to white and paints color onto it, which is the same idea said
 * in a palette that has no white in it. **The tone advances by a sliver per mark and cycles** ([toneAt]): gart indexes
 * a forty-eight color palette by the mark's number and its `safe` *wraps*, so consecutive marks are near-neighbors on
 * a long ramp. Two overlapping marks are then almost never the same color, which is what the interleave has to mix.
 *
 * [serpentineAt], [toneAt] and [dabPoints] are pure and tested.
 */
object ImpastoGenerator : Generator {

    override val style = DesignStyle(
        // A fraction rather than a count: what the knob sets is how closely the marks are spaced along the sweep, and
        // at any useful setting there are several hundred of them overlapping three deep — not a number anyone counts.
        amount = AmountKnob.Fraction("Coverage"),
        scale = "Brush size",
        irregularity = "Roughness",
        depth = "Thickness",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // the ground is the palette's light end, as gart's white canvas is
        if (RampTones.countFor(palette.size) <= 0) return bitmap // a single-stop palette is all ground

        val brush = min(width, height) * (MinBrush + (MaxBrush - MinBrush) * params.scale.coerceIn(0f, 1f))
        val sweeps = sweepCount(brush, height)
        val marks = markCount(brush, params.density, width, height, sweeps)
        val roughness = params.irregularity.coerceIn(0f, 1f)
        val layers = MinLayers + ((MaxLayers - MinLayers) * params.depth.coerceIn(0f, 1f)).roundToInt()

        // Where each mark sits and what color it takes, resolved once — the draw below revisits every mark on every
        // pass, and re-deriving these there would make the jitter depend on which pass was asking.
        val atX = FloatArray(marks)
        val atY = FloatArray(marks)
        val tone = IntArray(marks)
        for (m in 0 until marks) {
            val random = Random(seed + m * MarkStride)
            val along = if (marks <= 1) 0f else m.toFloat() / (marks - 1)
            val jitter = brush * JitterShare
            atX[m] = serpentineAt(along, width.toFloat(), sweeps) + (random.nextFloat() * 2f - 1f) * jitter
            atY[m] = along * (height - 1) + (random.nextFloat() * 2f - 1f) * jitter
            tone[m] = toneAt(along, palette)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()

        // Layer-major, not mark-major — see the class note. Each layer is seeded from its own (mark, layer) pair so
        // it can be drawn in this order at all: a stream per mark would hand out its layers in sequence only.
        for (layer in 0 until layers) {
            for (m in 0 until marks) {
                val random = Random(seed + m * MarkStride + layer * LayerStride)
                val points = dabPoints(atX[m], atY[m], brush, roughness, random)
                path.rewind()
                path.moveTo(points[0], points[1])
                for (p in 2 until points.size step 2) path.lineTo(points[p], points[p + 1])
                path.close()
                paint.color = tone[m]
                paint.alpha = DabAlpha // setting the color carries its own alpha in, so this has to follow it
                canvas.drawPath(path, paint)
            }
        }

        return bitmap
    }

    /**
     * How many diagonals the serpentine makes down a frame of this [height] for a blot of this [brush].
     *
     * Derived rather than fixed, because the overlap *between* sweeps is half of what makes the wash: gart's sweeps
     * are about a third of a blot apart, so a blot reaches across three of them. A constant count holds that on one
     * aspect at one brush size and nowhere else — on a phone it leaves the rows visibly separate, which is exactly
     * how this design first looked.
     */
    internal fun sweepCount(brush: Float, height: Int): Int =
        (height / (brush * SweepShare)).roundToInt().coerceAtLeast(MinSweeps)

    /**
     * How many marks are laid along that serpentine at this [density].
     *
     * The spacing is a share of the blot, so the marks overlap by construction whatever the brush; [density] only
     * loosens it, from gart's own [MarkShare] down to a spacing where the ground shows between them. Capped, because
     * past [MaxMarks] the frame is already covered several deep and the rest is cost with nothing to show.
     */
    internal fun markCount(brush: Float, density: Float, width: Int, height: Int, sweeps: Int): Int {
        val path = sweeps * hypot(width.toFloat(), height.toFloat() / sweeps)
        val loosen = SparseSpacing - (SparseSpacing - 1f) * density.coerceIn(0f, 1f)
        return (path / (brush * MarkShare * loosen)).roundToInt().coerceIn(MinMarks, MaxMarks)
    }

    /**
     * Where along the frame's width the serpentine sits, [along] of the way down it — gart's zig-zag of [sweeps]
     * diagonals, each running from one side to the other while descending a band.
     *
     * The vertical position is simply [along] of the height, which is what makes this a single-axis function: the
     * sweep descends at a constant rate and only its horizontal place turns over. Equal steps in [along] are equal
     * steps of *arc length* too, because every diagonal is the same length — which is why the marks come out evenly
     * spaced without resampling a polyline the way gart's `toPoints` does.
     */
    internal fun serpentineAt(along: Float, width: Float, sweeps: Int): Float {
        val t = along.coerceIn(0f, 1f) * sweeps
        val band = t.toInt().coerceAtMost(sweeps - 1)
        val within = t - band
        return if (band % 2 == 0) within * width else (1f - within) * width
    }

    /**
     * The color a mark [along] the sweep is painted — the ramp **above the ground**, read continuously and looped
     * [PaletteCycles] times.
     *
     * **Continuous rather than stepped, and looped rather than run once.** The design mixes only where two
     * overlapping marks differ, so the tone has to move by a sliver from one mark to the next: stepping [RampTones]'
     * handful of tones gives a band of identical marks with nothing to mix, and running the ramp once over hundreds
     * of marks is so fine it reads as a single wash from top to bottom. Looping gives both — a fine gradation
     * locally and a color that returns, which is what makes the frame read as several passes of paint.
     *
     * The ramp starts one tone above the palette's first stop, so a mark is never painted in the ground it sits on —
     * [RampTones]' own convention that tone `i` of `n` lives at `(i + 1) / n`, read as a range instead of a set.
     */
    internal fun toneAt(along: Float, palette: Palette): Int {
        val tones = RampTones.countFor(palette.size)
        if (tones <= 0) return palette.colorAt(0)
        val first = 1f / tones
        val cycled = along.coerceIn(0f, 1f) * PaletteCycles
        return LinearGradientGenerator.colorAt(first + (cycled - floor(cycled)) * (1f - first), palette)
    }

    /**
     * One layer of a dab: a closed ring of `x, y` pairs around ([cx], [cy]), walked out from an octagon to about
     * [radius] by [roughness].
     *
     * **The push does not decay** — see the class note. Each round inserts a point in the middle of every edge and
     * displaces it by the *same* amount as the round before, so the boundary is Brownian: as ragged at its finest
     * scale as at its coarsest, and different enough from one layer to the next that a stack of them makes a
     * soft-cored blot instead of a stack of copies.
     *
     * **[roughness] carries the walk *and* shrinks the seed it walks from**, which is what keeps the mark near
     * [radius] across the whole knob rather than growing with it. At `0` the seed is the full radius and the push is
     * nothing, so the mark is a **clean octagon** — the rigid end [DesignParams.irregularity]'s contract asks for, and
     * a real second look rather than a degenerate one, since a field of flat translucent octagons is a paper-collage
     * reading of this design.
     *
     * The push is uniform rather than gaussian, unlike gart's. Rendered side by side at these numbers the uniform
     * draw lands closer to gart's own blot — a gaussian's unbounded tail sprawls the walk wider — and it is one call
     * rather than three.
     */
    internal fun dabPoints(cx: Float, cy: Float, radius: Float, roughness: Float, random: Random): FloatArray {
        val torn = roughness.coerceIn(0f, 1f)
        val seed = radius * (1f - torn * (1f - SeedShare))
        var ring = FloatArray(DabSides * 2) { i ->
            val vertex = i / 2
            val angle = vertex * (2f * PI.toFloat() / DabSides)
            if (i % 2 == 0) cx + seed * cos(angle) else cy + seed * sin(angle)
        }
        val push = radius * torn * PushShare

        repeat(DabRounds) {
            val grown = FloatArray(ring.size * 2)
            val vertices = ring.size / 2
            var out = 0
            for (v in 0 until vertices) {
                val next = (v + 1) % vertices
                grown[out++] = ring[v * 2]
                grown[out++] = ring[v * 2 + 1]
                grown[out++] = (ring[v * 2] + ring[next * 2]) / 2f + (random.nextFloat() * 2f - 1f) * push
                grown[out++] = (ring[v * 2 + 1] + ring[next * 2 + 1]) / 2f + (random.nextFloat() * 2f - 1f) * push
            }
            ring = grown
        }
        return ring
    }

    /**
     * The blot's radius as a share of the frame's short side — a fine dab up to a broad loaded one.
     *
     * gart's is about `0.15` of its frame, which lands near the middle here. It is where the *walk* reaches rather
     * than a radius drawn anywhere, so a mark's silhouette runs past it wherever an excursion did.
     */
    private const val MinBrush = 0.11f
    private const val MaxBrush = 0.26f

    /** How far a mark may sit off the sweep, as a share of its own blot — enough that the sweep is not a ruled line. */
    private const val JitterShare = 0.55f

    /** How far apart the serpentine's sweeps and its marks are, as shares of a blot — gart's own proportions. */
    private const val SweepShare = 0.34f
    private const val MarkShare = 0.68f

    /** How much [DesignParams.density] `0` loosens the mark spacing, so the ground shows between them. */
    private const val SparseSpacing = 2.8f

    /**
     * The fewest sweeps and marks a degenerate frame still draws, and the most a dense one is allowed to cost.
     *
     * **[MaxMarks] is a safety net the declared brush range never reaches, and [MinBrush] is set so it cannot.** The
     * mark count goes as the *square* of the reciprocal brush — a finer blot needs both more sweeps and more marks
     * along each — so a low enough [MinBrush] runs into the cap, and a cap that bites does not merely cost less: it
     * spaces the marks *further apart than a blot*, they stop overlapping, and the wash the whole design is made of
     * is gone. That is a silent failure, so the bound is a test rather than a comment.
     */
    private const val MinSweeps = 3
    private const val MinMarks = 8
    private const val MaxMarks = 900

    /**
     * How many walks one mark is built from, at each end of *Thickness* — a thin wash up to loaded paint.
     *
     * gart runs fifty. Fewer serve here because the marks overlap three or four deep by construction, so the density
     * a mark does not build on its own it gets from its neighbors. The floor is not `1`: at a handful of layers the
     * interleave has nothing to interleave and the marks read as flat shapes again, which is the failure the whole
     * mechanism exists to avoid — a thin wash is eight of these, not one.
     */
    private const val MinLayers = 8
    private const val MaxLayers = 26

    /**
     * How opaque one dab is.
     *
     * Low, and it has to be: the design is the *accumulation*, and a dab that covered the ground would defeat the
     * interleave — a mark opaque by its tenth layer hides its neighbor's first ten however they are ordered.
     */
    private const val DabAlpha = 20

    /** The dab before it is torn: an octagon, gart's starting shape. */
    private const val DabSides = 8

    /**
     * How many rounds of the walk a layer takes, how small the octagon it walks from is at full roughness, and how
     * far each round pushes.
     *
     * Four rounds take the octagon to 128 points against gart's ten and 8,192. The difference does not show because
     * no single outline is legible at this alpha under this many neighbors: what the eye reads is the density the
     * ensemble makes, and what has to be matched for that is [PushShare] — the excursion's size against the blot's —
     * rather than the round count.
     */
    private const val DabRounds = 4
    private const val SeedShare = 0.70f
    private const val PushShare = 0.13f

    /**
     * How many times the palette rolls over along the sweep — gart's own, near enough: it lays two hundred marks
     * through a forty-eight color palette that wraps, which is a little over four passes.
     */
    private const val PaletteCycles = 4f

    /** Spaces the per-mark and per-layer streams apart, so no two draw the same numbers in the same order. */
    private const val MarkStride = 0x27BB2EE687B0B0FDL
    private const val LayerStride = 0x165667B19E3779F9L
}
