package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A field of color seen through fluted glass — vertical ribs that bend the light behind them, *Ribbed Glass*.
 *
 * **What the glass is over is half the design, and it is not a gradient.** An earlier version put a diagonal palette
 * ramp behind the ribs and had no way to put anything else there. Driving the reference settles it: its *Complexity*
 * runs `1..5` over a field of soft color blobs, and only at `1` does that field collapse to the plain ramp — which is
 * the one thing ours could ever draw. So the background is a [ColorLattice] of palette stops, `2..6` nodes across and
 * shaped to the frame so the blobs come out round, and [DesignParams.irregularity] is how many. Its `0` is the exact
 * diagonal ramp, because a lattice holding a linear function reads back as that function at any size.
 *
 * **The rib is a cylindrical lens, and it magnifies.** Ours compressed: it fanned each rib across a window of the
 * background up to a quarter of the frame wide, so a rib 1/18 of the frame across showed four times its own width
 * squeezed down. The reference does the opposite — drive its *Refraction* up and the colors inside a rib get *larger*
 * and smear. So the sampling is gart's own glass arithmetic (`glass/glassBall.kt`), Snell's law across the rib rather
 * than around a sphere: the source point is pulled toward the rib's center by [lensFactor], hardest at the rib's edges
 * and least at its middle, which is what makes the flute read as glass rather than as a linear squeeze.
 *
 * **[DesignParams.depth] is the *Refraction*, and its `0` had to become reachable.** The old mapping floored it at a
 * twentieth of the frame — deliberately, arguing that a rib should always be visible glass rather than a plain
 * gradient. That threw away the reference's calmest setting: at *Refraction* `0` theirs is the field, undistorted,
 * with only a hairline seam per rib. `0` now means exactly that, which is also [DesignParams.depth]'s standing rule.
 * The response is **squared** so the panel's universal `0.5` lands near their own default of `50` in `0..200`.
 *
 * **The sheen is a bevel, and ours was an order of magnitude too strong.** Measured across the reference at
 * *Refraction* `0`: a bright edge of about `+7%` just inside each rib's leading side, decaying over a fifth of the
 * rib, and a hairline seam of about `−8%` at its trailing edge — everything between is flat. Ours darkened every rib
 * to *half* brightness at both edges with no highlight at all, which is why it read as corrugated metal.
 *
 * **Two of the reference's knobs are deliberately elsewhere.** Its *Vibrancy* is a whole-image grade, so it belongs in
 * the studio's *Filters* stage rather than in this one design's panel — see
 * [inkspire.morphic.core.model.wallpaper.WallpaperFilter]. And its *Real glass* toggle is not a sub-look at all: turn
 * it off and the tab row becomes six *different* knobs (Light angle, Contrast, Thickness, Softness) over a striped
 * satin drawn from a one-dimensional ramp, with only *Count* shared. That is a second design wearing this one's name
 * and it is its own slice.
 *
 * [ribCount], [latticeAcross] and [lensFactor] are pure and tested: a lens that bends the wrong way still looks like
 * glass, which is exactly why it needs checking without a bitmap.
 */
object RibbedGlassGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Ribs* slider's own range. */
    private val Amount = AmountKnob.Count("Ribs", 1..30)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Complexity",
        depth = "Refraction",
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val ribs = ribCount(params.density)
        val cols = latticeAcross(params.irregularity)
        // Shaped to the frame rather than square, so a blob is as tall as it is wide. ColorLattice takes both.
        val rows = max(2, (cols * height.toFloat() / width).roundToInt())
        val nodes = fieldNodes(cols, rows, params.irregularity, palette, seed)
        val thickness = MaxThickness * params.depth.coerceIn(0f, 1f) * params.depth.coerceIn(0f, 1f)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val v = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val u = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val scaled = u * ribs
                val rib = floor(scaled).toInt().coerceIn(0, ribs - 1)
                val local = scaled - rib // 0..1 across this rib
                val center = (rib + 0.5f) / ribs
                // The lens pulls the sample toward the rib's center; at thickness 0 the factor is 1 and this is `u`.
                val sourceU = center + (u - center) * lensFactor(abs(local - 0.5f) * 2f, thickness)
                val behind = ColorLattice.sample(nodes, cols, rows, sourceU, v)
                pixels[y * width + x] = Shades.scale(behind, sheenAt(local))
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many ribs [density] asks for — one (a bare field, the reference's own rigid end) up to a fine reeding. */
    internal fun ribCount(density: Float): Int = Amount.at(density)

    /**
     * How many lattice nodes across the frame the background field is built from, for a given [complexity] — the
     * reference's *Complexity* `1..5`, which is a count of blobs rather than an amount of noise.
     */
    internal fun latticeAcross(complexity: Float): Int =
        MinLattice + (complexity.coerceIn(0f, 1f) * (MaxLattice - MinLattice)).roundToInt()

    /**
     * How far a point at [nd] across a rib (`0` at its center, `1` at its edge) is pulled toward that center, for a
     * lens of [thickness] — Snell's law through a half-cylinder.
     *
     * `1` is no lens at all, and it is what [thickness] `0` gives at every [nd]: the rigid end is the undistorted
     * field. Below `1` the rib shows a *narrower* window of the background than it occupies, which is magnification —
     * strongest at the rib's edges, weakest down its middle. **Clamped at zero**, because past a thickness the
     * factor would go negative and the rib would sample the background mirrored through its own center, which reads
     * as a seam down every flute rather than as more glass.
     */
    internal fun lensFactor(nd: Float, thickness: Float): Float {
        val across = nd.coerceIn(0f, 1f)
        val z = sqrt(1f - across * across)
        val k = 1f - Eta * Eta * across * across
        val offset = Eta * z - sqrt(max(k, 0f))
        return (1f + offset * thickness).coerceAtLeast(0f)
    }

    /**
     * How the rib's own bevel lights it at [local] (`0` at its leading edge, `1` at its trailing one) — a multiplier
     * on whatever the glass shows there.
     *
     * Measured off the reference rather than chosen; see the class note for the numbers and for what the old
     * half-brightness valley did to the look.
     */
    private fun sheenAt(local: Float): Float {
        val highlight = HighlightPeak * (1f - local / HighlightWidth).coerceIn(0f, 1f)
        val seam = SeamDepth * ((local - (1f - SeamWidth)) / SeamWidth).coerceIn(0f, 1f)
        return 1f + highlight - seam
    }

    /**
     * The lattice behind the glass: every node takes the palette somewhere between its own place along the frame's
     * diagonal and a seeded one, leaning further toward the seeded one as [complexity] rises.
     *
     * **Blended toward the free position rather than jittered around the diagonal**, which is the difference between a
     * field with blobs in it and a field that merely looks noisy. A jitter is an *offset*, so as the lattice gets
     * finer its neighbours' diagonal positions crowd together and the contrast between them falls — the field grows
     * more nodes and less to see, which is exactly backwards from the knob's name. Blending lets a node reach any part
     * of the ramp however fine the lattice is, so a blob can sit against its opposite.
     *
     * **A blend of `0` is the rigid end.** Every node then sits at a *linear* function of its position, and a bilinear
     * read of a linear function is that function — so the field is an exact diagonal ramp at any lattice size, which
     * is the reference's *Complexity* `1`. Deterministic in [seed].
     */
    private fun fieldNodes(cols: Int, rows: Int, complexity: Float, palette: Palette, seed: Long): IntArray {
        val random = Random(seed)
        // Eased, so the reference's own restrained default already has blobs in it rather than a barely-bent ramp.
        val blend = MaxFieldBlend * complexity.coerceIn(0f, 1f).pow(FieldBlendEase)
        return IntArray(cols * rows) { i ->
            val c = i % cols
            val r = i / cols
            val diagonal = (c.toFloat() / (cols - 1) + r.toFloat() / (rows - 1)) * 0.5f
            val position = diagonal + (random.nextFloat() - diagonal) * blend
            LinearGradientGenerator.colorAt(position.coerceIn(0f, 1f), palette)
        }
    }

    /** The fewest and most lattice nodes across the frame — the reference's *Complexity* `1..5`. */
    private const val MinLattice = 2
    private const val MaxLattice = 6

    /** How far a node abandons the frame's diagonal for a color of its own at full *Complexity*. */
    private const val MaxFieldBlend = 0.9f

    /** Shapes that blend so the reference's restrained *Complexity* `2` already reads as blobs rather than a ramp. */
    private const val FieldBlendEase = 0.55f

    /** Air-to-glass refractive ratio, gart's own default — the thing that makes the flute bend light like glass. */
    private const val Eta = 1f / 1.5f

    /**
     * The lens strength at *Refraction* `1` — where a rib has collapsed onto the single column of field at its own
     * center, so the frame is vertical smears. Swept offline rather than reasoned about: gart's own default of `1.2`
     * is where the factor at a rib's *edge* first reaches zero, which looks like almost nothing, because the factor
     * at the rib's *middle* is still `0.55` there and it is the middle that carries the picture.
     */
    private const val MaxThickness = 5f

    /** How much brighter a rib is at its leading edge, and over how much of its width that decays — measured. */
    private const val HighlightPeak = 0.07f
    private const val HighlightWidth = 0.2f

    /** How much darker the seam at a rib's trailing edge is, and how wide it is — measured. */
    private const val SeamDepth = 0.08f
    private const val SeamWidth = 0.03f
}
