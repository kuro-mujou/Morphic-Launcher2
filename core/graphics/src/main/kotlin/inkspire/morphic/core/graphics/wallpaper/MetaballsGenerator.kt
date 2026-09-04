package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.pow
import kotlin.random.Random

/**
 * A few merged charges seen through a warp and cut into stacked layers — the paper-cut *Flowing Blobs* (gart's
 * `arts/blob`, `arts/plasma/plasmeander`).
 *
 * **A metaball is a potential field, not a circle, which is why the blobs merge.** Each charge adds
 * `radius² / distance²` to every pixel, so two charges near each other sum to a field that bulges and joins between
 * them into one shape with a pinched waist. The field is then **banded**, and the bands are what is drawn.
 *
 * **There are only [Blobs] charges, and the reference's *Complexity* is not a count of them.** That is the finding
 * this design was rebuilt on, and the render is what proves it: drive theirs from one end of *Complexity* to the
 * other and the *same two or three systems stay in the same corners of the frame*, growing from smooth concentric
 * rings into convoluted sinuous ridges. Nothing is being added — the shapes are being **distorted**. So the field is
 * read through a domain warp, and Complexity is that warp's **frequency**: too low to see at the bottom of the slider
 * (a low-frequency warp is a translation, and a translated field looks the same), fine enough at the top to fold the
 * contours into islands. Reading it as a charge count instead — which is what this generator did, with a count that
 * topped out at nine — can only ever draw the bottom of their range.
 *
 * **[DesignParams.irregularity] is the warp's amplitude, and it is the knob that has a true rigid end.** At `0` there
 * is no warp at all and the design is exactly what the charges say: smooth nested ovals, the calmest thing in the
 * catalog. The reference has no separate knob for this — its amplitude is fixed — but splitting frequency from
 * amplitude costs nothing and buys the rigid end.
 *
 * **[DesignParams.scale] is the band thickness, and it must not be the palette's length.** Reading one band per stop
 * — what this did — means the *default* color mode, which reduces the palette to two, draws two bands: a ground with
 * lumps on it, and no design left. So the count is its own knob and the bands are **rungs on the ramp** rather than
 * the palette's own stops, exactly as [ContourGenerator] reads its levels.
 *
 * **[DesignParams.variant] is how far down the field the bands spread** — the reference's *Contrast*. *Broad* opens
 * them out so the layers are wide and soft; *Tight* crowds them against the peaks, so most of the frame is bare ground
 * and what is left reads as thin filaments hugging the ridges.
 *
 * **[DesignParams.depth] is the paper-cut shadow, and it is that field's second consumer.** Each band is darkened
 * along its boundary with the band *above* it — the one nearer a peak — and recovers across itself, so the layers read
 * as sheets of paper stacked on each other. It is deliberately **not** a directional cast: measured off the reference,
 * the dark rim hugs the upper layer's edge on *every* side of a blob, which is what a stack does and a light source
 * does not. The depth at the edge and the easing are measured too — see [shadowAt].
 *
 * [field], [level], [bandCount] and [shadowAt] are pure and tested — a summed potential is arithmetic that is silently
 * wrong (blobs that never merge, a field that saturates flat, a shadow on the wrong side of a band) with no bitmap
 * needed to see it.
 */
object MetaballsGenerator : Generator {

    /**
     * What [DesignParams.density] resolves to — the warp's frequency.
     *
     * A [AmountKnob.Fraction] rather than a count, for the plasma's reason: this design draws three charges whatever
     * the slider says, and what the slider actually moves is a continuous property of a field. The reference shows a
     * number here (4..40) but it counts nothing either.
     */
    private val Amount = AmountKnob.Fraction("Complexity")

    override val style = DesignStyle(
        amount = Amount,
        scale = "Thickness",
        irregularity = "Distortion",
        depth = "Shadow",
        variant = VariantKnob("Spread", Spread.entries.map { it.label }),
    )

    /**
     * How far down the field the bands are spread — the reference's *Contrast*, at the three points that are
     * different pictures.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property gamma the exponent the rolled field is raised to before banding. Above `1` the bands crowd toward the
     *   peaks and more of the frame is ground; below `1` they open out and the layers fill it.
     */
    internal enum class Spread(val label: String, val gamma: Float) {
        BROAD("Broad", BroadGamma),
        BALANCED("Balanced", BalancedGamma),
        TIGHT("Tight", TightGamma),
    }

    /** One charge: where it sits in the unit square, and its radius (its pull). */
    internal data class Charge(val x: Float, val y: Float, val radius: Float)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val charges = charges(seed)
        val bands = bandCount(params.scale)
        val gamma = Spread.entries[params.variant.coerceIn(0, Spread.entries.lastIndex)].gamma
        val shadow = params.depth.coerceIn(0f, 1f) * MaxShadow
        val frequency = MinFrequency + params.density.coerceIn(0f, 1f) * (MaxFrequency - MinFrequency)
        // Measured as a share of the frame, which is [DomainWarp]'s note: these contours are frame-sized things.
        val warp = DomainWarp(seed, params.irregularity.coerceIn(0f, 1f) * MaxDistortion, frequency)
        // One color per band, resolved once: the ramp read at `bands` rungs, so a two-stop palette still has a ramp
        // and every rung of a long one lands on a stop of its own.
        val ink = IntArray(bands) {
            LinearGradientGenerator.colorAt(it.toFloat() / (bands - 1).coerceAtLeast(1), palette)
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
            for (x in 0 until width) {
                val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
                val warpedX = warp.x(nx, ny)
                val warpedY = warp.y(nx, ny)
                val step = (level(field(warpedX, warpedY, charges), gamma) * bands)
                    .coerceIn(0f, bands - Epsilon)
                pixels[y * width + x] = shade(ink[step.toInt()], shadowAt(step - step.toInt(), shadow))
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** How many bands [scale] asks for — **fewer as it climbs**, since the knob is the band's thickness. */
    internal fun bandCount(scale: Float): Int {
        val thickness = scale.coerceIn(0f, 1f)
        return MaxBands - ((MaxBands - MinBands) * thickness).toInt()
    }

    /**
     * The design's [Blobs] charges for [seed] — positions in the unit square, radii over [MinRadius]..[MaxRadius].
     *
     * The count is fixed on purpose; see the class note. Radii are a large fraction of the frame, which is what makes
     * three charges a *field* rather than three discs — at this scale their potentials overlap everywhere and the
     * low contours are one shape.
     */
    internal fun charges(seed: Long): List<Charge> {
        val random = Random(seed)
        return List(Blobs) {
            Charge(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = MinRadius + random.nextFloat() * (MaxRadius - MinRadius),
            )
        }
    }

    /**
     * The summed metaball potential at ([nx], [ny]) — `Σ radius² / (distance² + ε)`. The `ε` keeps a pixel sitting on a
     * charge finite; the sum is unbounded above, so the caller maps it, not this.
     */
    internal fun field(nx: Float, ny: Float, charges: List<Charge>): Float {
        var sum = 0f
        for (charge in charges) {
            val dx = nx - charge.x
            val dy = ny - charge.y
            sum += (charge.radius * charge.radius) / (dx * dx + dy * dy + Softness)
        }
        return sum
    }

    /**
     * A potential in `0..∞` rolled to a height in `0..1`, spread by [gamma].
     *
     * `field / (field + 1)` is the roll — a smooth curve reaching `1` only at a charge's center and staying near `0`
     * in empty space. It is what makes the *contours* readable at all: a `1/d²` sum spans orders of magnitude, and any
     * linear cut through it either saturates the whole frame or leaves everything on the ground. Raising the result to
     * [gamma] is the *Spread* knob: above `1` the height is pulled down everywhere except at the peaks, so the bands
     * crowd there; below `1` it is pushed up and they open out.
     */
    internal fun level(field: Float, gamma: Float): Float {
        val rolled = field / (field + 1f)
        return rolled.toDouble().pow(gamma.toDouble()).toFloat()
    }

    /**
     * The factor a band's color is scaled by, [into] of the way from its low edge to the boundary with the band above
     * it, for a shadow of [strength].
     *
     * **Measured off the reference rather than chosen.** Its bands darken to `0.57` of their flat color right at that
     * boundary and recover across the band, and the recovery fits `f^2.5` closely (a square is visibly too wide, a
     * cube too narrow). Both numbers are the whole of the paper-cut look: shallower and the layers stop reading as
     * stacked, deeper and the picture goes muddy at every seam.
     */
    internal fun shadowAt(into: Float, strength: Float): Float =
        1f - strength * into.coerceIn(0f, 1f).toDouble().pow(ShadowEase.toDouble()).toFloat()

    /** [color] with every channel scaled by [factor], alpha untouched. */
    internal fun shade(color: Int, factor: Float): Int {
        if (factor >= 1f) return color
        val a = color ushr AlphaShift and ChannelMask
        val r = ((color shr RedShift and ChannelMask) * factor).toInt().coerceIn(0, ChannelMask)
        val g = ((color shr GreenShift and ChannelMask) * factor).toInt().coerceIn(0, ChannelMask)
        val b = ((color and ChannelMask) * factor).toInt().coerceIn(0, ChannelMask)
        val packed = (a shl AlphaShift) or (r shl RedShift) or (g shl GreenShift) or b
        return packed
    }

    /** Where each channel sits in a packed ARGB int, and the byte that reads it. */
    private const val AlphaShift = 24
    private const val RedShift = 16
    private const val GreenShift = 8
    private const val ChannelMask = 0xFF

    /**
     * The exponents the three spreads raise the rolled field to. Around `2` the bands land in the proportions the
     * reference's own default does; either side of it opens them out over the frame or crowds them onto the peaks.
     */
    private const val BroadGamma = 1.2f
    private const val BalancedGamma = 2f
    private const val TightGamma = 3.5f

    /** How many charges the field is made of — fixed; the reference's Complexity distorts them, it does not add any. */
    private const val Blobs = 3

    /** A charge's radius, as a fraction of the frame. Big enough that three of them overlap into one field. */
    private const val MinRadius = 0.20f
    private const val MaxRadius = 0.45f

    /** The `ε` that keeps a pixel on a charge's center finite rather than dividing by zero. */
    private const val Softness = 0.0008f

    /**
     * Warp cells across the frame at each end of the complexity knob. The low end is under one cell, which is a
     * translation rather than a distortion — which is exactly why the reference's own low end looks unwarped.
     */
    private const val MinFrequency = 0.5f
    private const val MaxFrequency = 5f

    /** How far the warp may push a coordinate, at full distortion — past this the field folds through itself. */
    private const val MaxDistortion = 0.7f

    /** The band count at each end of the thickness knob — thin layers up to a few fat ones. */
    private const val MinBands = 2
    private const val MaxBands = 12

    /** Keeps the top of the field inside the last band rather than one past it. */
    private const val Epsilon = 0.0001f

    /** How dark a band goes at the boundary with the band above it, at full shadow — measured off the reference. */
    private const val MaxShadow = 0.43f

    /** The easing the shadow recovers across a band with — measured, not chosen. See [shadowAt]. */
    private const val ShadowEase = 2.5f

}
