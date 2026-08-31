package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.min
import kotlin.random.Random

/**
 * Big translucent discs, soft-edged, overlapping and blending into a cloudy color field — *Soft Overlaps* (gart's
 * `arts/monet`).
 *
 * **Soft edges come from a radial gradient per disc, not a blur pass.** Each disc is drawn with a [RadialGradient] from
 * its color at the center fading to fully transparent at the rim, so where two discs overlap their translucent falloffs
 * add up and the colors melt — the misty, painterly blend with no hard edge anywhere, and none of the cost of blurring
 * the whole frame. This is the one design that leans on the palette carrying **alpha**: the discs are laid at partial
 * opacity so the overlaps mix rather than paint over.
 *
 * **[DesignParams.variant] picks the blend:** `0` normal translucency (colors average), `1` additive (overlaps
 * brighten toward light — a glow). [DesignParams.density] sets how many discs, [DesignParams.irregularity] how far they
 * scatter off an even lattice — a regular polka of blobs at `0`, a loose drift at `1` (Smart Launcher's *Position
 * jitter*). Deterministic in [seed].
 *
 * [discCount] is the pure mapping; the placement is [PointScatter]'s and the blending is judged in the render harness.
 */
object SoftOverlapsGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Discs* slider's own range. */
    private val Amount = AmountKnob.Count("Discs", 8..26)

    override val style = DesignStyle(
        amount = Amount,
        irregularity = "Jitter",
        variant = VariantKnob("Blend", listOf("Normal", "Additive")),
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(palette.size - 1)) // darkest stop — the ground the discs glow on
        val discColors = if (palette.size > 1) palette.colors.dropLast(1) else palette.colors

        val count = discCount(params.density)
        val centers = PointScatter.gridJitter(count, params.irregularity, seed)
        val shortSide = min(width, height)
        val sizeRandom = Random(seed xor SizeSalt) // salted, so the irregularity knob moves centers without resizing
        val additive = params.variant == VariantAdditive
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (additive) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)

        for (i in 0 until count) {
            val cx = centers[i * 2] * width
            val cy = centers[i * 2 + 1] * height
            val radius = (MinRadius + sizeRandom.nextFloat() * (MaxRadius - MinRadius)) * shortSide
            val core = discColors[i % discColors.size] and RgbMask or (DiscAlpha shl 24)
            val transparent = core and RgbMask // same color, zero alpha — the rim
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(core, transparent), // color at center, fully transparent at the rim
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
        return bitmap
    }

    /** How many discs [density] asks for — a sparse few up to a dense wash. */
    internal fun discCount(density: Float): Int = Amount.at(density)

    /** [DesignParams.variant] selecting additive blending (overlaps brighten) over the default normal translucency. */
    private const val VariantAdditive = 1

    /** A disc's radius range, as a fraction of the short side — large, so discs heavily overlap into one field. */
    private const val MinRadius = 0.18f
    private const val MaxRadius = 0.36f

    /** The opacity a disc's center is laid at, so overlaps blend rather than paint over. */
    private const val DiscAlpha = 0x9C

    /** The low 24 bits of an ARGB color — its RGB, alpha masked off. */
    private const val RgbMask = 0x00FFFFFF

    /** Keeps the size stream independent of placement, so irregularity moves discs without resizing them. */
    private const val SizeSalt = 0x85EBCA6BL
}
