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
 */
object HalftoneGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 8..26)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Jitter",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val cols = gridColumns(params.density)
        val cellPx = width.toFloat() / cols
        val rows = (height / cellPx).roundToInt().coerceAtLeast(1)
        val noise = PerlinNoise2d(seed)
        val jitter = params.irregularity.coerceIn(0f, 1f) * MaxJitter
        val random = Random(seed xor JitterSalt)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0)) // lightest stop — the paper the screen is printed on
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val maxRadius = min(cellPx, height.toFloat() / rows) / 2f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // Each dot nudged off its cell centre by up to half a cell at full irregularity — a loosened screen.
                val nx = ((c + 0.5f) / cols + (random.nextFloat() * 2f - 1f) * jitter / cols).coerceIn(0f, 1f)
                val ny = ((r + 0.5f) / rows + (random.nextFloat() * 2f - 1f) * jitter / rows).coerceIn(0f, 1f)
                val field = fieldAt(nx, ny, noise) // 0..1
                val radius = radiusAt(field) * maxRadius
                if (radius <= 0f) continue
                // Color from the darker half of the ramp so a dot reads on the pale paper, deeper where the field is strong.
                paint.color = LinearGradientGenerator.colorAt(ColorFloor + (1f - ColorFloor) * field, palette)
                canvas.drawCircle(nx * width, ny * height, radius, paint)
            }
        }
        return bitmap
    }

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

    /** The field at ([nx], [ny]) in `0..1` — one octave of noise mapped from `-1..1`. */
    private fun fieldAt(nx: Float, ny: Float, noise: PerlinNoise2d): Float =
        ((noise.at(nx * Frequency, ny * Frequency) + 1f) / 2f).coerceIn(0f, 1f)

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
