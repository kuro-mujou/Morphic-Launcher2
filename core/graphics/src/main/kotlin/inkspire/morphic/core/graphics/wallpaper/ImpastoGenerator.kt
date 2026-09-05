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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ragged translucent dabs laid along a sweep across the frame, building into a painted wash — the impasto (gart's
 * `arts/monet`).
 *
 * **The catalog's first painterly design, and the family the teardown's third principle keeps naming.** Everything
 * else here is a *field* (a value per pixel) or a *shape* (an edge you can trace). This is neither: it is a heap of
 * overlapping marks whose edges are meant to be seen as brush-work, where the picture comes from what the marks do
 * together rather than from any one of them. Nothing else in the catalog has a mark with a texture of its own.
 *
 * **The construction is gart's and the arithmetic under it is not, because gart's cannot run here.** Its
 * `deformPath` inserts a displaced midpoint into *every* edge, so a round doubles the point count: ten rounds take an
 * octagon to 8,192 points, and fifty of those at each of two hundred places is some eighty million points a frame.
 * That is affordable on a desktop JVM and is not affordable on a phone. The look does not actually need it — what
 * makes a mark read as torn is that its edge is ragged at *several scales at once*, which is a fractal rather than a
 * long random walk. So [dabPoints] subdivides a fixed [DabRounds] times with the displacement **halving each round**:
 * bounded cost, an edge ragged from its silhouette down to its finest wobble, and one thing gart's version could not
 * have — a mark whose *size* is set rather than wherever the walk happened to wander, which is what lets
 * [DesignParams.scale] mean anything at all.
 *
 * **A mark's layers differ in *place and size*, not only in edge, and that is the part the port had to add back.**
 * gart gets it for free: its layers are independent random walks from the same octagon, so each one ends up somewhere
 * else and some sizes wander far — the dab comes out dense in the middle and feathered at the rim, which is what
 * reads as paint. A bounded fractal does not do that. The first cut stacked every layer on one center at one radius,
 * and since they then covered almost exactly the same pixels the alpha saturated: each mark came out a single flat
 * shape with a hard torn edge, and the design read as a **paper collage** rather than a painting — handsome, and not
 * what it is for. So each layer takes its own offset ([LayerWander]) and its own radius ([MinLayerSize]), which is
 * the random walk's spread put back deliberately.
 *
 * **The marks are laid along a serpentine**, gart's zig-zag of [Zags] diagonals from one side of the frame to the
 * other and down. It is what gives the palette somewhere to run — the tone is read from how far along that sweep a
 * mark sits, so the color moves through the frame in broad diagonal bands — and it is why the design does not need a
 * placement knob: the sweep covers the frame by construction, and [DesignParams.density] only decides how thickly it
 * is painted along the way.
 *
 * **The ground is the palette's first stop and the marks are the tones above it** ([RampTones]), as
 * [DiagonalBandsGenerator]'s bands are. gart clears to white and paints color onto it, which is the same idea said in
 * a palette that has no white in it.
 *
 * **The tone advances by a *sliver* per mark and cycles, which is the second thing the port had to get right.** gart
 * indexes a forty-eight color palette by the mark's number and its `safe` **wraps**, so consecutive marks land on
 * consecutive stops of a long ramp — near-neighbors in color — and the palette rolls over about four times along the
 * sweep. That is the whole reason the design mixes: two marks that overlap are almost never the same color, so their
 * translucent edges make a third. The first cut read the ramp as [RampTones]' handful of flat tones instead, which
 * put every mark in a band on *one* color; overlapping marks then had nothing to mix and the painting came out as
 * flat cut-outs with hard torn edges. [toneAt] reads the ramp **continuously** and loops it [PaletteCycles] times, so
 * a palette of five stops gives the same fine gradation gart gets from forty-eight.
 *
 * [serpentineAt] and [dabPoints] are pure and tested: where a mark goes and what shape it is are the two things a
 * bitmap cannot show you are wrong, only that something looks off.
 */
object ImpastoGenerator : Generator {

    /** What [DesignParams.density] resolves to — the marks laid along the sweep, and the *Strokes* slider's range. */
    private val Amount = AmountKnob.Count("Strokes", 60..420)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Brush size",
        taper = "Size variation",
        irregularity = "Roughness",
        depth = "Thickness",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // the ground is the palette's light end, as gart's white canvas is
        if (RampTones.countFor(palette.size) <= 0) return bitmap // a single-stop palette is all ground

        val strokes = strokeCount(params.density)
        val shortSide = min(width, height)
        val brush = shortSide * (MinBrush + (MaxBrush - MinBrush) * params.scale.coerceIn(0f, 1f))
        val spread = params.taper.coerceIn(0f, 1f)
        val roughness = params.irregularity.coerceIn(0f, 1f)
        val layers = MinLayers + ((MaxLayers - MinLayers) * params.depth.coerceIn(0f, 1f)).roundToInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            alpha = DabAlpha
        }
        val path = Path()

        for (i in 0 until strokes) {
            // A stream per mark rather than one for the whole frame, so a knob that changes how many draws a mark
            // takes — [DesignParams.depth] counts them — cannot shift every mark after it. That reshuffle is
            // invisible until someone drags the knob and the painting rearranges itself; see [SeededHarmonics].
            val random = Random(seed + i * MarkStride)
            val along = if (strokes <= 1) 0f else i.toFloat() / (strokes - 1)
            val jitter = brush * JitterShare
            val cx = serpentineAt(along, width.toFloat()) + (random.nextFloat() * 2f - 1f) * jitter
            val cy = along * (height - 1) + (random.nextFloat() * 2f - 1f) * jitter
            // Smaller than the brush by up to the spread and never larger, as [SoftOverlapsGenerator]'s forms are:
            // winding the variation up thins the painting out rather than growing it past its own brush.
            val radius = brush * (1f - spread * random.nextFloat())
            paint.color = toneAt(along, palette)

            repeat(layers) {
                // Each layer somewhere else and its own size — see the class note; without it the layers cover one
                // another exactly, the alpha saturates and the mark is a flat cut-out.
                val wander = radius * LayerWander
                val lx = cx + (random.nextFloat() * 2f - 1f) * wander
                val ly = cy + (random.nextFloat() * 2f - 1f) * wander
                val lr = radius * (MinLayerSize + (1f - MinLayerSize) * random.nextFloat())
                val points = dabPoints(lx, ly, lr, roughness, random)
                path.rewind()
                path.moveTo(points[0], points[1])
                for (p in 2 until points.size step 2) path.lineTo(points[p], points[p + 1])
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        return bitmap
    }

    /** How many marks [density] lays along the sweep — a sparse scatter of dabs up to a solid painted surface. */
    internal fun strokeCount(density: Float): Int = Amount.at(density)

    /**
     * The color a mark [along] the sweep is painted — the ramp **above the ground**, read continuously and looped
     * [PaletteCycles] times.
     *
     * **Continuous rather than stepped, and looped rather than run once**, both for the reason in the class note: the
     * design mixes only where two overlapping marks differ, so the tone has to move by a sliver from one mark to the
     * next. Stepping [RampTones]' handful of tones gives a band of identical marks with nothing to mix; running the
     * ramp once over hundreds of marks would be so fine it reads as a single wash from top to bottom. Looping gives
     * both — a fine gradation locally and a color that returns, which is what makes the picture read as several
     * passes of paint rather than one gradient.
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
     * Where along the frame's width the serpentine sits, [along] of the way down it — gart's zig-zag of [Zags]
     * diagonals, each running from one side to the other while descending a band.
     *
     * The vertical position is simply [along] of the height, which is what makes this a single-axis function: the
     * sweep descends at a constant rate and only its horizontal place turns over. Equal steps in [along] are equal
     * steps of *arc length* too, because every diagonal is the same length — which is why the marks come out evenly
     * spaced without resampling a polyline the way gart does.
     */
    internal fun serpentineAt(along: Float, width: Float): Float {
        val t = along.coerceIn(0f, 1f) * Zags
        val band = t.toInt().coerceAtMost(Zags - 1)
        val within = t - band
        return if (band % 2 == 0) within * width else (1f - within) * width
    }

    /**
     * One dab: a closed ring of `x, y` pairs around ([cx], [cy]), an octagon of [radius] torn up by [roughness].
     *
     * **Subdivision with a halving displacement, which is the whole of the port** — see the class note for what gart
     * does instead and why it cannot run here. Each round inserts a point in the middle of every edge and pushes it
     * off; the push starts at [FirstDisplacement] of the radius and halves each round, so the first round decides the
     * silhouette and the last only roughens it. The total is bounded by twice the first push whatever the round
     * count, which is what keeps a mark the size the brush asked for.
     *
     * **[roughness] `0` is a clean octagon**, the rigid end [DesignParams.irregularity]'s contract asks for — and a
     * real second look rather than a degenerate one, since a field of flat translucent octagons is a paper-collage
     * reading of the same design.
     *
     * The push is uniform rather than gaussian, unlike gart's. A gaussian's tail would let the occasional vertex fly,
     * which reads as a splash; at these displacements the difference is otherwise not visible, and a uniform draw is
     * one call rather than three.
     */
    internal fun dabPoints(cx: Float, cy: Float, radius: Float, roughness: Float, random: Random): FloatArray {
        var ring = FloatArray(DabSides * 2) { i ->
            val vertex = i / 2
            val angle = vertex * (2f * PI.toFloat() / DabSides)
            if (i % 2 == 0) cx + radius * cos(angle) else cy + radius * sin(angle)
        }
        var push = radius * roughness.coerceIn(0f, 1f) * FirstDisplacement

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
            push /= 2f
        }
        return ring
    }

    /** The brush's radius as a share of the frame's short side — a fine dab up to a broad loaded one. */
    private const val MinBrush = 0.025f
    private const val MaxBrush = 0.10f

    /** How far a mark may sit off the sweep, as a share of its own brush — enough that the sweep is not a ruled line. */
    private const val JitterShare = 0.55f

    /** How many overlapping dabs one mark is built from, at each end of *Thickness* — a thin wash up to loaded paint. */
    private const val MinLayers = 1
    private const val MaxLayers = 24

    /**
     * How far a layer sits off its mark's center and how small it may be drawn, as shares of the mark's radius.
     *
     * Together they are what stops a mark saturating into a cut-out — see the class note. The wander is well under
     * half, so a mark still reads as one dab rather than as a cluster of several, and the size floor is low enough
     * that the smallest layers pile into a dense core while the largest reach past it and feather.
     */
    private const val LayerWander = 0.45f
    private const val MinLayerSize = 0.35f

    /**
     * How opaque one dab is.
     *
     * Low, and it has to be: the design is the *accumulation*, so a dab that covered the ground would make
     * [MaxLayers] of them look exactly like one of them and the thickness knob would do nothing past its first step.
     */
    private const val DabAlpha = 30

    /**
     * How many times the palette rolls over along the sweep — gart's own, near enough: it lays two hundred marks
     * through a forty-eight color palette that wraps, which is a little over four passes.
     */
    private const val PaletteCycles = 4f

    /** Diagonals the serpentine makes across the frame — gart's own count, and enough that its sweeps overlap. */
    private const val Zags = 20

    /** The dab before it is torn: an octagon, gart's starting shape. */
    private const val DabSides = 8

    /**
     * How many times a dab's edge is subdivided, and how far the first round pushes as a share of the radius.
     *
     * Four rounds take the octagon to 128 points, which is where a further round stops being visible at any brush
     * size this design draws — the fifth would push by a thirty-second of the radius, well under a pixel on a fine
     * dab. The first push is over half the radius, so a fully rough mark is torn rather than merely wobbly.
     */
    private const val DabRounds = 4
    private const val FirstDisplacement = 0.55f

    /** Spaces the per-mark streams far apart, so two marks never draw the same numbers in the same order. */
    private const val MarkStride = 0x27BB2EE687B0B0FDL
}
