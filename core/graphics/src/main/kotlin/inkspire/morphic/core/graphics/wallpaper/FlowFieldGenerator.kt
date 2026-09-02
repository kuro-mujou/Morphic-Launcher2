package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Packed strokes combed through a noise flow field, with moons floating among them — *Flow Field*, after gart's
 * `arts/flowforce/eclectic`.
 *
 * **A trail stops growing when it comes near another, and that is the whole design.** Every trail of one color pass
 * is grown a step at a time; before each step its head is tested against the points every *other* started trail has
 * laid down, and the moment it is within [Separation] the trail is finished. That is what makes the marks short, of
 * wildly varying length, evenly spaced and never crossing — the packed dash look. What this replaced traced a fixed
 * number of steps per particle and drew them all, so the strokes ran over one another into a weave, which is a
 * different design rather than a coarser one. gart is where the rule comes from (`TrailPath.collide`), and driving
 * the reference confirms it: wind its *Density* up and the marks crowd but never touch.
 *
 * **One full set of trails is grown and drawn per palette tone, not one set colored at random.** The collision test
 * is *within* a pass, so same-colored marks keep their distance while different colors overlap freely — which is
 * exactly what the reference does, and what a single shuffled pass cannot produce.
 *
 * **The moons are drawn between the color passes, and that is why strokes pass both in front of and behind them.**
 * gart's trick, and it is what stops them reading as discs pasted over a finished picture. [DesignParams.depth] is
 * their count, on that field because layering is the only thing they add to a flat field of marks; the reference
 * splits it into an *Orbs* count and an *Orb size*, and ours takes the size from [seed] for want of a field.
 *
 * **[DesignParams.scale] is *Thickness*** — theirs, and the knob that takes the design from hairlines to fat
 * lozenges. **[DesignParams.irregularity] is *Curl*,** the angle range the field sweeps: a near-parallel drift at
 * `0`, tight swirls at `1`, and `0.5` landing on gart's own three radians.
 *
 * **[DesignParams.variant] is their *Style*, and the second look is gart's `flowforce/perl`** — the trails drawn as
 * chains of beads rather than strokes, over a field that sweeps a whole turn instead of a fraction of one, which is
 * where its tight swirls come from. Two departures from that art, both because this build keeps one pass per tone:
 * gart picks each line's color *from its width* and beads only one line in six, where here every line beads and the
 * pass owns the color.
 *
 * **The ground is the palette's *last* stop and the marks are the tones above it** — the mirror of the mosaic
 * designs, because a palette is ordered light-to-dark and this design is lit marks on a dark ground.
 * [RampTones.belowGround] is that mirror, with the same floor: a palette reduced to two stops still gets three tones
 * to comb with rather than one.
 */
object FlowFieldGenerator : Generator {

    /** What [DesignParams.density] resolves to — the trails grown *per tone*, and the slider's own range. */
    private val Amount = AmountKnob.Count("Strokes", 30..300)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        irregularity = "Curl",
        depth = "Moons",
        variant = VariantKnob("Style", Look.entries.map { it.label }),
    )

    /**
     * Which of the design's two looks is drawn — the reference's *Style*, and gart's two arts.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property frequency how many noise cycles span the frame's short side — the size of the swirls.
     * @property fullTurn whether the field sweeps a whole turn rather than the range [angleSpan] sets. Only *Pearls*
     *   does, and it is where its much tighter curls come from; gart's `perl` maps its noise over `360°` where
     *   `eclectic` maps it over three radians.
     */
    internal enum class Look(val label: String, val frequency: Float, val fullTurn: Boolean) {
        /** Flat strokes with round caps — gart's `eclectic`, and the reference's own default. */
        ECLECTIC("Eclectic", frequency = 3.4f, fullTurn = false),

        /** The same trails as chains of beads — gart's `perl`, over a field that sweeps a whole turn. */
        PEARLS("Pearls", frequency = 1.7f, fullTurn = true),
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val look = Look.entries[params.variant.coerceIn(0, Look.entries.lastIndex)]
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1))

        val tones = RampTones.belowGround(palette)
        // An all-ground palette has nothing to comb with, which is the honest picture rather than an error.
        if (tones.isEmpty()) return bitmap

        val shortSide = min(width, height).toFloat()
        val noise = PerlinNoise2d(seed)
        val span = if (look.fullTurn) TwoPi else angleSpan(params.irregularity)
        // Halved because the noise runs -1..1 and `span` is the *whole* range the field sweeps, not its amplitude.
        // gart's Perlin answers 0..1, so its `noise * 3` is three radians end to end; read that way here it would be
        // six, and the difference is the design curling twice as hard as the art it is taken from.
        val angleAt = { x: Float, y: Float ->
            noise.at(x / shortSide * look.frequency, y / shortSide * look.frequency) * span * 0.5f
        }

        val random = Random(seed)
        // The moons draw from a seed of their own, so moving their slider adds and removes moons instead of
        // re-rolling every trail behind them.
        val moonRandom = Random(seed xor MoonSeed)
        val trailCount = strokeCount(params.density)
        val thickness = thicknessScale(params.scale)
        val moons = moonCount(params.depth)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for ((index, tone) in tones.withIndex()) {
            val trails = growTrails(trailCount, width, height, shortSide, angleAt, random)
            drawMoonsBefore(canvas, index, tones.size, moons, tones, width, height, shortSide, paint, moonRandom)
            paint.color = tone
            for (trail in trails) {
                val strokeWidth = trail.width * thickness
                if (look == Look.PEARLS) drawBeads(canvas, trail.points, strokeWidth, paint) else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = strokeWidth
                    canvas.drawPath(Streamlines.pathOfPixels(trail.points), paint)
                }
            }
        }
        return bitmap
    }

    /** How many trails one tone's pass grows at [density] — a scatter of marks up to a packed comb. */
    internal fun strokeCount(density: Float): Int = Amount.at(density)

    /**
     * The **whole** angle range the field sweeps, in radians, at [irregularity] — a gentle drift when low, a tight
     * curl when high, and `0.5` landing on the three radians gart's `eclectic` uses end to end.
     */
    internal fun angleSpan(irregularity: Float): Float =
        MinAngleSpan + irregularity.coerceIn(0f, 1f) * (MaxAngleSpan - MinAngleSpan)

    /** How much [scale] widens or narrows a mark, `0.5` leaving gart's own widths untouched. */
    internal fun thicknessScale(scale: Float): Float =
        MinThickness + scale.coerceIn(0f, 1f) * (MaxThickness - MinThickness)

    /** How many moons [depth] asks for — none at all up to a sky full, `0.5` landing on the reference's four. */
    internal fun moonCount(depth: Float): Int = (depth.coerceIn(0f, 1f) * MaxMoons).roundToInt()

    /** One grown trail: its points in **pixels**, interleaved `x, y`, and the width it was drawn to be. */
    private class Trail(val points: FloatArray, val width: Float)

    /**
     * Grows [count] trails through [angleAt] until each runs out of room, and returns the ones long enough to draw.
     *
     * They are grown **in lockstep** rather than one at a time, which is not an optimization but the rule itself: a
     * trail may only stop against a neighbor that has already started, so growing one to completion before beginning
     * the next would let the first cross the whole frame unchallenged.
     */
    private fun growTrails(
        count: Int,
        width: Int,
        height: Int,
        shortSide: Float,
        angleAt: (Float, Float) -> Float,
        random: Random,
    ): List<Trail> {
        val separation = Separation * shortSide
        val step = StepLength * shortSide
        val grid = SeparationGrid(width, height, separation, count * TrailSteps)

        val xs = FloatArray(count) { random.nextFloat() * width }
        val ys = FloatArray(count) { random.nextFloat() * height }
        val widths = FloatArray(count) { (MinWidth + random.nextFloat() * (MaxWidth - MinWidth)) * shortSide }
        val alive = BooleanArray(count) { true }
        val points = Array(count) { ArrayList<Float>(TrailSteps * 2) }

        repeat(TrailSteps) {
            for (i in 0 until count) {
                if (!alive[i]) continue
                val x = xs[i]
                val y = ys[i]
                if (outside(x, y, width, height) || grid.crowded(x, y, separation, i)) {
                    alive[i] = false
                } else {
                    points[i].add(x)
                    points[i].add(y)
                    grid.insert(x, y, i)

                    val angle = angleAt(x, y)
                    xs[i] = x + cos(angle) * step
                    ys[i] = y + sin(angle) * step
                }
            }
        }

        return (0 until count)
            .filter { points[it].size >= MinTrailSteps * 2 }
            .map { Trail(points[it].toFloatArray(), widths[it]) }
    }

    /** Whether ([x], [y]) has left the frame, which ends a trail as surely as a neighbor does. */
    private fun outside(x: Float, y: Float, width: Int, height: Int): Boolean =
        x < 0f || y < 0f || x >= width || y >= height

    /** Draws each bead of [points] as a filled dot, spaced so the chain reads as beads rather than as a stroke. */
    private fun drawBeads(canvas: Canvas, points: FloatArray, width: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        val stride = ((width * BeadSpacing) / (StepLength * min(canvas.width, canvas.height))).toInt().coerceAtLeast(1)
        var i = 0
        while (i * 2 + 1 < points.size) {
            canvas.drawCircle(points[i * 2], points[i * 2 + 1], width / 2f, paint)
            i += stride
        }
    }

    /**
     * Draws whichever moons belong before the pass at [index], so the tones drawn after them lie on top.
     *
     * The moons are spread evenly over the passes, which is what interleaves them through the picture's depth rather
     * than stacking them all behind or all in front.
     */
    @Suppress("LongParameterList")
    private fun drawMoonsBefore(
        canvas: Canvas,
        index: Int,
        passes: Int,
        moons: Int,
        tones: IntArray,
        width: Int,
        height: Int,
        shortSide: Float,
        paint: Paint,
        random: Random,
    ) {
        paint.style = Paint.Style.FILL
        for (moon in 0 until moons) {
            if (moon * passes / moons != index) continue
            paint.color = tones[random.nextInt(tones.size)]
            canvas.drawCircle(
                random.nextFloat() * width,
                random.nextFloat() * height,
                (MinMoon + random.nextFloat() * (MaxMoon - MinMoon)) * shortSide,
                paint,
            )
        }
    }

    /**
     * A uniform grid over the frame answering "is any *other* trail's point within a distance of here" — the spatial
     * index the collision rule needs to be affordable.
     *
     * Testing a head against every point of every trail is what gart does, and it is `O(trails × points)` per step:
     * fine on a desktop at 1024², a freeze on a phone at 1080×2400 with hundreds of trails. Cells are one separation
     * across, so a query only ever reads the nine around it.
     */
    private class SeparationGrid(width: Int, height: Int, private val cell: Float, capacity: Int) {

        private val cols = (width / cell).toInt() + 1
        private val rows = (height / cell).toInt() + 1
        private val buckets = Array(cols * rows) { ArrayList<Int>() }
        private val xs = FloatArray(capacity)
        private val ys = FloatArray(capacity)
        private val owners = IntArray(capacity)
        private var count = 0

        fun insert(x: Float, y: Float, owner: Int) {
            if (count >= xs.size) return
            xs[count] = x
            ys[count] = y
            owners[count] = owner
            buckets[bucketOf(x, y)].add(count)
            count++
        }

        /** Whether a point of some trail other than [owner] lies within [distance] of ([x], [y]). */
        fun crowded(x: Float, y: Float, distance: Float, owner: Int): Boolean {
            val col = (x / cell).toInt()
            val row = (y / cell).toInt()
            val squared = distance * distance
            for (r in (row - 1)..(row + 1)) {
                for (c in (col - 1)..(col + 1)) {
                    val inside = r >= 0 && r < rows && c >= 0 && c < cols
                    if (inside && bucketCrowded(r * cols + c, x, y, squared, owner)) return true
                }
            }
            return false
        }

        /** Whether one bucket holds a point of some trail other than [owner] within `sqrt(squared)` of ([x], [y]). */
        private fun bucketCrowded(bucket: Int, x: Float, y: Float, squared: Float, owner: Int): Boolean {
            for (i in buckets[bucket]) {
                if (owners[i] != owner) {
                    val dx = xs[i] - x
                    val dy = ys[i] - y
                    if (dx * dx + dy * dy < squared) return true
                }
            }
            return false
        }

        private fun bucketOf(x: Float, y: Float): Int {
            val col = (x / cell).toInt().coerceIn(0, cols - 1)
            val row = (y / cell).toInt().coerceIn(0, rows - 1)
            return row * cols + col
        }
    }

    /** How near another trail a head may come before it stops, as a share of the short side — gart's, scaled. */
    private const val Separation = 0.049f

    /** How far one hop carries a trail, as a share of the short side — gart's ten pixels on a 1024 canvas. */
    private const val StepLength = 0.0098f

    /**
     * How many hops a trail may take, and the fewest it must have taken to be worth drawing.
     *
     * The ceiling is gart's. The floor is **not**: gart drops anything under a third of the maximum, which here
     * discards every mark on the frame — its trails run a whole canvas width before meeting a neighbor, and ours
     * meet in a tenth of that, because the reference's frame is a tall phone rather than a square. So the floor is
     * only what separates a dash from a dot, and it was read off a render with the filter effectively off.
     */
    private const val TrailSteps = 300
    private const val MinTrailSteps = 12

    /** A mark's width before [thicknessScale], as a share of the short side — gart's six-to-thirty on a 1024 canvas. */
    private const val MinWidth = 0.006f
    private const val MaxWidth = 0.029f

    /** What [DesignParams.scale] multiplies that width by, `0.5` leaving it alone. */
    private const val MinThickness = 0.25f
    private const val MaxThickness = 1.75f

    /** The whole angle range the field sweeps at the ends of *Curl* — `0.5` lands on gart's three radians. */
    private const val MinAngleSpan = 0.6f
    private const val MaxAngleSpan = 5.4f

    /** The most moons the sky may hold; `depth` `0.5` lands on the reference's own four. */
    private const val MaxMoons = 8

    /** A moon's radius, as a share of the short side. */
    private const val MinMoon = 0.05f
    private const val MaxMoon = 0.15f

    /** How far apart beads sit, as a multiple of their diameter — enough to read as a chain rather than a stroke. */
    private const val BeadSpacing = 1.6f

    /** Mixed into the seed so the moons are drawn from their own stream. See the note where it is used. */
    private const val MoonSeed = 0x4D4F_4F4E_5346_4C44L

    private val TwoPi = (2.0 * PI).toFloat()
}
