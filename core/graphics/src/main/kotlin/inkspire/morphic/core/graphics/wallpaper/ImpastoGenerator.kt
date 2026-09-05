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
 * **A layer is a random walk, and the walk is the whole design.** gart's `deformPath` inserts a displaced midpoint
 * into every edge and is run ten times at a **constant** displacement — the push never decays, so the boundary is
 * Brownian rather than smooth and each layer sprawls from a ten-pixel octagon out to around a hundred and fifty.
 * Fifty of those, each landing somewhere else, is what makes a dab: dense where they all overlap, feathered where
 * only the long excursions reach, with the occasional spike. That soft-cored blot with a ragged corona *is* the
 * impasto mark, and nothing about it survives if the walk is replaced with something tidier.
 *
 * **Which the first build did, and it produced a different design.** It read gart's ten rounds as a cost to be
 * reduced and swapped the constant push for one that **halved** each round — a well-behaved fractal, cheap, and a
 * compact hard-edged blob. Every mark then came out a flat cut-out and the frame read as a **paper collage**:
 * handsome, and not a painting. Two further compensations were bolted on (a per-layer offset and a per-layer size)
 * to fake the spread the walk gives for free, and they are gone with the cause.
 *
 * **What is ours is the *bound*, not the shape.** Ten rounds double the point count to 8,192 a layer, which at fifty
 * layers and two hundred places is some eighty million points a frame — affordable on a desktop JVM with Skija and
 * not on a phone. [dabPoints] runs [DabRounds] of the same walk instead, and takes the displacement from the brush
 * (`extent / √rounds`, so the walk's RMS lands on the size asked for) rather than leaving it a constant the extent
 * falls out of. gart's mark has no size control at all — it is however far the walk went; this one has the walk *and*
 * a size, which is what lets [DesignParams.scale] mean anything.
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
                // Every layer walks from the same octagon and ends up somewhere else on its own — see the class note.
                val points = dabPoints(cx, cy, radius, roughness, random)
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
     * One layer of a dab: a closed ring of `x, y` pairs around ([cx], [cy]), walked out from an octagon to about
     * [radius] by [roughness].
     *
     * **The push does not decay, which is the one thing that has to be right** — see the class note. Each round
     * inserts a point in the middle of every edge and displaces it by the *same* amount as the round before, so the
     * boundary is Brownian: as ragged at its finest scale as at its coarsest, sprawling rather than compact, and
     * different enough from one layer to the next that fifty of them make a soft-cored blot instead of fifty copies
     * of one blob.
     *
     * **[roughness] carries the walk *and* shrinks the seed it walks from**, which is what keeps the mark near
     * [radius] across the whole knob rather than growing with it. At `0` the seed is the full radius and the push is
     * nothing, so the mark is a **clean octagon** — the rigid end [DesignParams.irregularity]'s contract asks for, and
     * a real second look rather than a degenerate one, since a field of flat translucent octagons is a paper-collage
     * reading of this design. At `1` the seed is [SeedShare] of the radius and the walk roughens it from there.
     *
     * **[PushShare] is small against the radius, and getting that ratio wrong is what makes a blot look like a
     * star.** A push of a fifth of the radius over 256 boundary points puts each excursion five times taller than its
     * neighbor is wide, so the corona reads as a ring of coarse triangles; gart's is an eighth of its blot, spread
     * over 8,192 points, and reads as fuzz. Matching the *ratio* rather than the round count is what gets the fuzz
     * back at a fortieth of the cost — the seed carries the size and the walk only has to roughen the rim.
     *
     * The push is uniform rather than gaussian, unlike gart's. Rendered side by side at these numbers the uniform
     * draw lands closer to gart's own blot than a gaussian does — its unbounded tail sprawls the walk wider — and it
     * is one call rather than three.
     */
    internal fun dabPoints(cx: Float, cy: Float, radius: Float, roughness: Float, random: Random): FloatArray {
        val torn = roughness.coerceIn(0f, 1f)
        val seed = radius * (1f - torn * (1f - SeedShare))
        var ring = FloatArray(DabSides * 2) { i ->
            val vertex = i / 2
            val angle = vertex * (2f * PI.toFloat() / DabSides)
            if (i % 2 == 0) cx + seed * cos(angle) else cy + seed * sin(angle)
        }
        // Constant, not decaying — the walk is the design; see the class note.
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
     * The brush's reach as a share of the frame's short side — a fine dab up to a broad loaded one.
     *
     * It is where the *walk* lands rather than a radius drawn anywhere, so a mark's silhouette runs past it wherever
     * an excursion did. gart's blot is around `0.15` of its frame, and the top here matches it.
     */
    private const val MinBrush = 0.03f
    private const val MaxBrush = 0.15f

    /** How far a mark may sit off the sweep, as a share of its own brush — enough that the sweep is not a ruled line. */
    private const val JitterShare = 0.55f

    /**
     * How many walks one mark is built from, at each end of *Thickness* — a thin wash up to loaded paint.
     *
     * The top is gart's own order: fifty is what turns a heap of ragged outlines into a blot with a core, and much
     * below a dozen the layers read as separate shapes rather than as one mark.
     */
    private const val MinLayers = 1
    private const val MaxLayers = 36

    /**
     * How opaque one dab is.
     *
     * Low, and it has to be: the design is the *accumulation*, so a dab that covered the ground would make
     * [MaxLayers] of them look exactly like one of them and the thickness knob would do nothing past its first step.
     * gart's is 20 of 255 over fifty layers, and this is the same trade at a slightly shorter stack.
     */
    private const val DabAlpha = 22

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
     * How many rounds of the walk a layer takes, and how small the octagon it walks from is at full roughness.
     *
     * Five rounds take the octagon to 256 points. gart runs ten, which is 8,192 — and the difference need not show,
     * because at a stack of `22`-alpha layers no single outline is legible: what the eye reads is the *density* the
     * ensemble makes. What has to be matched is not the round count but [PushShare], the excursion's size against the
     * blot's; rendered side by side against gart's own numbers, these three land on its blot at a fortieth of the
     * points.
     */
    private const val DabRounds = 5
    private const val SeedShare = 0.70f
    private const val PushShare = 0.13f

    /** Spaces the per-mark streams far apart, so two marks never draw the same numbers in the same order. */
    private const val MarkStride = 0x27BB2EE687B0B0FDL
}
