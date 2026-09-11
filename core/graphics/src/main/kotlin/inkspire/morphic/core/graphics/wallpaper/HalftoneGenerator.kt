package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A regular grid of dots whose size and color are driven by a noise field — the halftone screen (gart's
 * `arts/palecircles`, `arts/circledots`).
 *
 * **The field decides how big each dot is, and that is the whole design.** A newsprint screen renders a picture by
 * varying dot size against a fixed lattice, and here the picture is one octave of [PerlinNoise2d]: big dots where the
 * field is strong, dwindling to bare paper where it is weak. Each dot is also colored from that same field off the
 * palette ramp, so size and tone move together and the swell of ink wanders organically rather than in a plain
 * gradient.
 *
 * **The sibling to tell it apart from is [DotGridGenerator], which shares the lattice and nothing else.** Dot Grid
 * draws every element at *one* size inside a contained block and varies only which palette band it falls in; this
 * fills the frame and varies only the size. The lattice is the thing they have in common, not the design — and
 * [ConfettiGenerator] is the third of the family, which scatters rather than pinning to a lattice at all.
 *
 * **Dots on the lightest stop as paper** — the classic newsprint read. [DesignParams.density] sets the grid
 * resolution, and [DesignParams.irregularity] loosens the lattice — a crisp halftone screen at `0`, dots wandering off
 * their cells at `1` (Smart Launcher's *Irregularity*). Deterministic in [seed] (the field and the position jitter are).
 *
 * [radiusAt] is pure and tested — a dot's radius as a function of the field is the arithmetic that decides whether the
 * screen fades to paper or floods solid, and it needs no canvas.
 *
 * **It plans and then paints**: [plan] is where every dot sits and the field under it, [draw] turns that into circles,
 * and a scrub is a [Morph] between two plans. See docs/MORPH_ENGINE_PLAN.md.
 */
object HalftoneGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 8..26)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Jitter",
    )

    /**
     * A screen planned but not painted — where every dot sits and how strong the field is under it, at no particular
     * size and in no particular palette.
     *
     * **In shares of the frame**, which is where the render always placed its dots, so a plan is one picture at every
     * size of one shape and the bake draws exactly the circles it drew.
     *
     * @property x every dot's center across the frame, `0..1`, row-major — a dot the field leaves bare included, since
     *   a scrub can grow it.
     * @property y every dot's center down the frame, `0..1`.
     * @property field the field under every dot, `0..1` — its size ([radiusAt]) and its tone both.
     */
    internal class Plan(val cols: Int, val rows: Int, val x: FloatArray, val y: FloatArray, val field: FloatArray)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(width, height, params, seed), palette, width, height)
        return bitmap
    }

    /** The screen [params] and [seed] describe, in a frame shaped like `[width]` × `[height]`. */
    internal fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan {
        val cols = gridColumns(params.density)
        val cellPx = width.toFloat() / cols
        val rows = (height / cellPx).roundToInt().coerceAtLeast(1)
        val noise = PerlinNoise2d(seed)
        val jitter = params.irregularity.coerceIn(0f, 1f) * MaxJitter
        val random = Random(seed xor JitterSalt)
        // The lattice is already frame-shaped; the field it reads has to be too — see [fieldAt].
        val heightOverWidth = if (width <= 0) 1f else height.toFloat() / width

        val x = FloatArray(cols * rows)
        val y = FloatArray(cols * rows)
        val field = FloatArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // Each dot nudged off its cell centre by up to half a cell at full irregularity — a loosened screen.
                val nx = ((c + 0.5f) / cols + (random.nextFloat() * 2f - 1f) * jitter / cols).coerceIn(0f, 1f)
                val ny = ((r + 0.5f) / rows + (random.nextFloat() * 2f - 1f) * jitter / rows).coerceIn(0f, 1f)
                x[r * cols + c] = nx
                y[r * cols + c] = ny
                field[r * cols + c] = fieldAt(nx, ny * heightOverWidth, noise) // 0..1
            }
        }
        return Plan(cols, rows, x, y, field)
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        canvas.drawColor(palette.colorAt(0)) // lightest stop — the paper the screen is printed on
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val maxRadius = min(width.toFloat() / plan.cols, height.toFloat() / plan.rows) / 2f

        for (i in plan.field.indices) {
            val field = plan.field[i]
            val radius = radiusAt(field) * maxRadius
            if (radius <= 0f) continue
            // Color from the darker half of the ramp so a dot reads on the pale paper, deeper where the field is strong.
            paint.color = LinearGradientGenerator.colorAt(ColorFloor + (1f - ColorFloor) * field, palette)
            canvas.drawCircle(plan.x[i] * width, plan.y[i] * height, radius, paint)
        }
    }

    /**
     * A scatter scrub: every dot drifts from its place in one screen to its place in the other, and swells, shrinks and
     * re-tones as the field under it turns from one seed's to the other's.
     *
     * **Nothing to pair**: the lattice is the knobs' and the frame's, so a dot's partner is the dot in its own cell, and
     * neither end has pushed it more than half a cell off it. A dot the field leaves bare at one end is still in the
     * plan, and grows out of nothing — [radiusAt] is continuous at its floor.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(width, height, params, from), plan(width, height, params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two screens prepared to interpolate, dot for dot — or **null where they are not the same lattice**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.cols == to.cols && from.rows == to.rows) Morph(from, to) else null

    /** Two screens, dot for dot, and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /** The screen [t] of the way across; the ends are the plans themselves, as every design's are. */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                cols = from.cols,
                rows = from.rows,
                x = FloatArray(from.x.size) { from.x[it] + (to.x[it] - from.x[it]) * t },
                y = FloatArray(from.y.size) { from.y[it] + (to.y[it] - from.y[it]) * t },
                field = FloatArray(from.field.size) { turnField(from.field[it], to.field[it], t) },
            )
        }
    }

    /**
     * The field [t] of the way from one seed's [a] to another's [b] — [turnNoise] about the field's middle, so the
     * screen keeps its contrast through a scrub rather than flattening at the midpoint. Clamped, since the two swings
     * can add past either end of the field.
     */
    internal fun turnField(a: Float, b: Float, t: Float): Float =
        (Mid + turnNoise(a - Mid, b - Mid, t)).coerceIn(0f, 1f)

    /** How many columns of dots [density] asks for — a coarse screen up to a fine one. */
    internal fun gridColumns(density: Float): Int = Amount.at(density)

    /**
     * A dot's radius as a fraction of its cell, `0..1`, from the field strength at its center. Below [DotFloor] the dot
     * vanishes entirely (bare paper), so the weak areas read as empty rather than as a haze of specks; above it the
     * radius climbs to fill the cell.
     */
    internal fun radiusAt(field: Float): Float {
        if (field < DotFloor) return 0f
        return ((field - DotFloor) / (1f - DotFloor)).coerceIn(0f, 1f)
    }

    /**
     * The field at ([nx], [ny]) in `0..1` — one octave of noise mapped from `-1..1`.
     *
     * **Both coordinates are shares of the frame's *width*, matching the lattice above.** The cells were already sized
     * off the width and the rows counted to fit, so the dots sit on a square screen grid — but the field they read was
     * sampled in the unit square, which stretched every cluster by the frame's proportions. The dots were round and
     * the *picture they drew* was not, which is a mismatch between two derivations of the same geometry and exactly
     * the divergence that hides.
     */
    private fun fieldAt(nx: Float, ny: Float, noise: PerlinNoise2d): Float =
        ((noise.at(nx * Frequency, ny * Frequency) + 1f) / 2f).coerceIn(0f, 1f)

    /** The middle of the field, about which a scrub turns it. */
    private const val Mid = 0.5f

    /** Below this field strength a dot is not drawn at all, so weak regions are clean paper, not a speckle. */
    private const val DotFloor = 0.25f

    /** The lowest point on the ramp a dot is colored from, so even a small dot sits in the legible, darker half. */
    private const val ColorFloor = 0.4f

    /** How many noise cycles span the frame — the size of the clusters of large dots. */
    private const val Frequency = 3f

    /** Half a cell of travel at full irregularity, so a loosened dot reaches its neighbour's cell but the screen holds. */
    private const val MaxJitter = 0.5f

    /** Keeps the position-jitter stream independent of the noise field, so irregularity moves dots without resizing them. */
    private const val JitterSalt = 0x27D4EB2FL
}
