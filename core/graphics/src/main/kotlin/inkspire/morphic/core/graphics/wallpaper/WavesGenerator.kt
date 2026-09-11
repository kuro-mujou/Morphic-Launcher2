package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The frame cut into flat bands by long smooth crests — *Layered Waves*, the reference studio's design of that name.
 *
 * **A band boundary is a height at the frame's left edge and a height at its right edge, joined by a smoothstep.**
 * That one sentence is the whole design, and it was measured rather than guessed: with the reference's *Distortion*
 * at `0` and ten bands on screen, all seven visible boundaries normalize to the *same* curve to within `0.002`, and
 * what separates them is a single signed amplitude — the difference between two partitions of the frame. So the frame
 * carries **two** band layouts, one down each edge, and every crest is the interpolation between the pair. The design
 * this replaced summed three seeded sines per layer, independently, which cannot produce a frame whose crests share a
 * shape at any setting.
 *
 * **[DesignParams.scale] is *Variation*, and it moves the band heights and the crests' sweep at once — which is the
 * surprising part.** The two edge layouts are the same arithmetic ([Bands.boundaries]) drawn from two seeds, so
 * winding it to `0` makes both exactly even, which makes every left height equal its right height, which leaves every
 * crest with **nothing to sweep between**. That is the reference's rigid end exactly — its *Spacing* at `100` is a
 * stack of dead-straight stripes — and it is why a knob that reads as a spacing control turns off the waves. Wound
 * up, band heights run from slivers to a third of the frame and the crests sweep with them. It is *Distortion* that
 * still curves them at `0`, so the flat stack is the two knobs down together.
 *
 * **[DesignParams.irregularity] is *Distortion*, and it is lobes rather than amplitude.** The reference's knob adds
 * interior shape to a boundary without touching its ends: at `0` the crest is that single edge-to-edge sweep, and
 * wound up the boundaries grow lobes, cross each other and swallow their neighbors whole. Here that is a two-term
 * ripple under a `sin(πx)` envelope, so the crest still meets both edges at exactly its layout heights however far
 * the knob is pushed — the rigid end stays exact, which is the contract the field carries everywhere.
 *
 * **A crossed crest hides the band above it, and that is deliberate.** A pixel's band is the *number* of crests at or
 * above it, so a crest that dives past its neighbor simply stops being counted and the band between them pinches out.
 * Giving each band to a fixed pair of neighboring crests would keep every band alive as a sliver instead, and would
 * lose the swallowing that is most of what the reference's *Distortion* looks like at the top of its range.
 *
 * **[DesignParams.depth] is *Shadow*, and the reference has no knob for it — it is always on.** Measured down a
 * column of theirs: `×0.815` at the crest, recovering **linearly** to `×1` over about a sixteenth of the frame's
 * height, uniform across the channels and the same length whatever the band's own thickness. So the band *above* is
 * the nearer one, and the response here is linear rather than squared precisely so the `0.5` every design still opens
 * at reproduces that measurement. **Fifth design wanting per-design defaults.**
 *
 * **[DesignParams.variant] is *Fill*, the reference's *Palette gradients* toggle, and its transform is exact**: each
 * band ramps from its own color at the left edge to that color turned **±20° of hue and ±20 points of lightness, the
 * saturation untouched** — four sampled pairs, all four within a tenth. Their palette is a bank of flat swatches, so
 * this is derived rather than stored, which is what lets ours have it at all. The direction alternates band to band;
 * whether theirs takes it from the index's parity or from a seeded draw is not settled, and parity is the one that
 * reproduces every sample.
 *
 * **The palette *cycles* here, one band per stop — it is not read as a ramp.** Ten bands over a four-stop palette
 * paint every stop at full strength twice, where reading the ramp across the band index would spend the middle bands
 * on colors the user never picked. Stop `0` is the ground everywhere in this catalog and stays unpainted, so the
 * cycling is over [RampTones] — which is also what keeps the design alive in the default two-stop color mode.
 *
 * The one departure from theirs is the count: their *Count* `1` draws two crests, and ours draws the one the number
 * says.
 *
 * **The bands are painted as shapes, not counted a pixel at a time**, so the bake and a scrub issue the same canvas
 * calls ([plan] and [draw]) and a shuffle can be scrubbed — see docs/MORPH_ENGINE_PLAN.md's field survey for why.
 */
object WavesGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the crest count, and the slider's own range. */
    private val Amount = AmountKnob.Count("Layers", 1..10)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Variation",
        irregularity = "Distortion",
        depth = "Shadow",
        variant = VariantKnob("Fill", Fill.entries.map { it.label }),
    )

    /**
     * How a band is painted — the reference's *Palette gradients*, off and on.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     */
    internal enum class Fill(val label: String) {
        /** One flat color per band, the reference's own default. */
        FLAT("Flat"),

        /** Each band ramping across the frame to a turn of its own color — see the class note for the transform. */
        GRADIENT("Gradient"),
    }

    /**
     * A frame of crests planned but not painted — at no particular size and in no particular palette.
     *
     * **It takes no size**: every height is a share of the frame and every crest is a function of `0..1` across it.
     * A band's color is not here either — the `k`-th band down takes the `k`-th tone above the ground — so a shuffle
     * never recolors one.
     *
     * @property left each crest's height at the frame's left edge, `0` the top — [Bands.boundaries], sorted.
     * @property right the same down the right edge, from a layout of its own.
     * @property lobes each crest's interior shape, which [distortion] scales.
     * @property distortion the *Distortion* knob, `0..1`, before [crestAt] squares it.
     * @property shadow how far a crest darkens the band under it — [shadowDepth] of the knob.
     */
    internal class Plan(
        val left: FloatArray,
        val right: FloatArray,
        val lobes: List<Lobe>,
        val distortion: Float,
        val shadow: Float,
        val fill: Fill,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(params, seed), palette, width, height)
        return bitmap
    }

    /**
     * The crests [params] and [seed] describe.
     *
     * The two band layouts sit one down each edge; at *Variation* `0` both are even, which leaves every crest nothing
     * to sweep between.
     */
    internal fun plan(params: DesignParams, seed: Long): Plan {
        val crests = layerCount(params.density)
        val variation = params.scale.coerceIn(0f, 1f)
        val random = Random(seed xor LobeSeed)
        return Plan(
            left = Bands.boundaries(crests + 1, variation, seed),
            right = Bands.boundaries(crests + 1, variation, seed xor RightEdgeSeed),
            lobes = List(crests) { Lobe.random(random) },
            distortion = params.irregularity.coerceIn(0f, 1f),
            shadow = shadowDepth(params.depth),
            fill = Fill.entries[params.variant.coerceIn(0, Fill.entries.lastIndex)],
        )
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     *
     * **A pixel's band is how many crests sit at or above it, and that is painted rather than counted.** Where the
     * count reaches `k` is everywhere below the `k`-th highest crest of that column, so each column's crests are sorted
     * and the `k`-th of them traced across the frame is the edge band `k` is filled below — later bands over earlier,
     * so every shared edge is antialiased once. Sorting per column is what keeps the counting: a crest that dives past
     * its neighbor swaps places with it in the sort, and the band between them pinches out exactly as it did.
     *
     * **The shadow hangs from that same traced edge**, a darkening that fades linearly over [ShadowSpan] of the frame
     * below it, laid by [shade]; each band's fill covers the shadow of the edge above it, which is the "nearest crest
     * above" the counting chose.
     *
     * **Half a pixel in**, because a crest is read at pixel `y` and a canvas puts that pixel's center at `y + 0.5`.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        val tones = RampTones.aboveGround(palette)
        // A palette with nothing above its ground has only the ground to paint with — the same answer [RampTones]
        // gives, and the honest picture rather than an error.
        val stops = if (tones.isEmpty()) intArrayOf(palette.colorAt(0)) else tones
        val xs = columnsFor(width)
        val edges = edges(plan, xs, width, height)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val shadow = if (plan.shadow > 0f) ShadowStrip(plan.shadow, height * ShadowSpan) else null
        canvas.save()
        canvas.translate(PixelCenter, PixelCenter)
        for (band in 0..edges.size) {
            paintBand(fillPaint, band, stops, plan.fill, width)
            if (band == 0) {
                canvas.drawRect(-1f, -1f, width + 1f, height + 1f, fillPaint)
            } else {
                canvas.drawPath(below(xs, edges[band - 1], height), fillPaint)
                shadow?.shade(canvas, xs, edges[band - 1])
            }
        }
        canvas.restore()
    }

    /**
     * Where the crests are sampled across a frame [width] wide, in pixels, with a column past each edge so the traced
     * edges reach the frame's sides.
     *
     * **Every [ColumnStep] pixels**, which is fine enough to be exact: a crest turns at most once and a half between
     * the edges, so a chord that short departs from the curve by a small fraction of a pixel even at full distortion.
     */
    private fun columnsFor(width: Int): FloatArray {
        val steps = ((width - 1) / ColumnStep).toInt().coerceAtLeast(1)
        val inside = FloatArray(steps + 1) { (width - 1) * it.toFloat() / steps }
        return floatArrayOf(-1f) + inside + floatArrayOf(width.toFloat())
    }

    /**
     * The edge each band is filled below, in pixels: for every column in [xs], the crests' heights sorted top to
     * bottom — so `edges[k][j]` is the `k`-th highest crest at column `j`.
     */
    internal fun edges(plan: Plan, xs: FloatArray, width: Int, height: Int): Array<FloatArray> {
        val crests = plan.lobes.size
        val out = Array(crests) { FloatArray(xs.size) }
        val column = FloatArray(crests)
        for (j in xs.indices) {
            val nx = if (width <= 1) 0f else xs[j] / (width - 1)
            for (i in 0 until crests) {
                column[i] = crestAt(plan.left[i], plan.right[i], plan.lobes[i], plan.distortion, nx)
            }
            column.sort()
            for (k in 0 until crests) out[k][j] = column[k] * height
        }
        return out
    }

    /** The region below the traced [edge] across [xs], to past the bottom of a frame [height] tall. */
    private fun below(xs: FloatArray, edge: FloatArray, height: Int): Path = Path().apply {
        moveTo(xs[0], edge[0])
        for (j in 1 until xs.size) lineTo(xs[j], edge[j])
        lineTo(xs.last(), height + 1f)
        lineTo(xs[0], height + 1f)
        close()
    }

    /**
     * Sets [paint] to band [band]'s fill — its stop, flat, or ramping across the frame to a turn of that stop
     * ([turned]), from pixel `0` to pixel `width - 1`.
     */
    private fun paintBand(paint: Paint, band: Int, stops: IntArray, fill: Fill, width: Int) {
        val from = stops[band % stops.size]
        if (fill == Fill.FLAT) {
            paint.shader = null
            paint.color = from
        } else {
            val to = turned(from, up = band % 2 == 1)
            paint.shader = LinearGradient(0f, 0f, (width - 1).toFloat(), 0f, from, to, Shader.TileMode.CLAMP)
        }
    }

    /**
     * A crest's shadow: black at [depth] of opacity along the crest, fading linearly to nothing [span] pixels below
     * it — which over an opaque band is the band's own color scaled by `1 - depth` recovering to `1`.
     *
     * **Laid as a bitmap mesh along the crest**, because the fade follows a curve and a gradient shader can only
     * follow a line. `drawBitmapMesh` is drawn by the hardware canvas at every API level the launcher runs on, where
     * `drawVertices` — the other way to shade a curved strip — is only from 29, and an unsupported call draws nothing
     * rather than failing.
     */
    private class ShadowStrip(depth: Float, private val span: Float) {

        private val fade: Bitmap = createBitmap(1, FadeTexels).apply {
            val alpha = (depth * ChannelMax).roundToInt().coerceIn(0, ChannelMask)
            for (row in 0 until FadeTexels) {
                val left = 1f - row.toFloat() / (FadeTexels - 1)
                setPixel(0, row, ((alpha * left).roundToInt() shl AlphaShift))
            }
        }

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        /** Shades below the traced [edge] across [xs] into [canvas]. */
        fun shade(canvas: Canvas, xs: FloatArray, edge: FloatArray) {
            val verts = FloatArray(xs.size * 4)
            for (j in xs.indices) {
                verts[j * 2] = xs[j]
                verts[j * 2 + 1] = edge[j]
                verts[(xs.size + j) * 2] = xs[j]
                verts[(xs.size + j) * 2 + 1] = edge[j] + span
            }
            canvas.drawBitmapMesh(fade, xs.size - 1, 1, verts, 0, null, 0, paint)
        }
    }

    /**
     * A scrub between two frames of crests: the band layouts slide along both edges and every crest's lobes reshape,
     * and the bands are re-traced under them at every moment.
     *
     * **Counted bands need no pairing.** A band is not an object that could be matched — it is wherever a count
     * reaches a number — so moving the crests is the whole morph, and the count re-derives the bands from them.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(params, from), plan(params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two frames prepared to interpolate, crest for crest — or **null where they hold different numbers of crests**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.lobes.size == to.lobes.size) Morph(from, to) else null

    /** Two frames of crests and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /**
         * The crests [t] of the way across; the ends are the plans themselves.
         *
         * **A ripple's phase turns the short way**, since a phase is an angle: taken the long way it would spin a
         * crest's lobes through most of a cycle on the way to a partner that sits a few degrees off.
         */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                left = between(from.left, to.left, t),
                right = between(from.right, to.right, t),
                lobes = List(from.lobes.size) { i ->
                    Lobe(
                        from.lobes[i].terms.zip(to.lobes[i].terms) { a, b ->
                            Term(
                                amplitude = a.amplitude + (b.amplitude - a.amplitude) * t,
                                frequency = a.frequency + (b.frequency - a.frequency) * t,
                                phase = lerpAngle(a.phase, b.phase, t),
                            )
                        },
                    )
                },
                distortion = from.distortion + (to.distortion - from.distortion) * t,
                shadow = from.shadow + (to.shadow - from.shadow) * t,
                // A choice, and one a shuffle never changes.
                fill = if (t < 0.5f) from.fill else to.fill,
            )
        }

        private fun between(a: FloatArray, b: FloatArray, t: Float) = FloatArray(a.size) { a[it] + (b[it] - a[it]) * t }
    }

    /** How many crests [density] asks for — one long horizon up to a finely stratified frame. */
    internal fun layerCount(density: Float): Int = Amount.at(density)

    /**
     * Where the crest between two bands sits at horizontal position [nx] (`0..1`), as a fraction of the frame where
     * `0` is the top — [left] and [right] being its heights at the two edges.
     *
     * The lobes ride under a `sin(πx)` envelope, so whatever [distortion] is, the crest still arrives at exactly
     * [left] and [right]: the edges belong to the band layout and the knob only shapes what happens between them.
     *
     * **[distortion] is squared**, for [shadowDepth]'s inverse reason: the reference opens on a restrained *60* out of
     * 100 and its crests are still long sweeps there, where a linear response puts our own `0.5` deep into the
     * serpentine end. Third design to buy a per-design default with an exponent.
     */
    internal fun crestAt(left: Float, right: Float, lobe: Lobe, distortion: Float, nx: Float): Float {
        val t = nx.coerceIn(0f, 1f)
        val sweep = left + (right - left) * Easing.smoothstep(t)
        if (distortion <= 0f) return sweep

        var ripple = 0f
        for (term in lobe.terms) {
            ripple += term.amplitude * sin(t * term.frequency * TwoPi + term.phase)
        }
        return sweep + distortion * distortion * WarpSweep * sin(t * PiF) * ripple
    }

    /**
     * How far a crest darkens the band under it at [depth].
     *
     * **Linear, not squared** — unlike [LouversGenerator]'s seam, because the reference has no knob here at all and
     * shades every band the same amount, so `0.5` has to land on that measurement rather than somewhere restrained
     * below it.
     */
    internal fun shadowDepth(depth: Float): Float = MaxShadow * depth.coerceIn(0f, 1f)

    /**
     * [argb] turned one step around the reference's *Palette gradients* transform — [up] rotating the hue forward and
     * lifting the lightness, else both the other way. Saturation is untouched, which is theirs measured.
     *
     * **Its own HSL arithmetic rather than `ColorUtils`, which is the platform's and is right**: that class reads its
     * channels through `android.graphics.Color`, so a unit test of this turn throws *Method not mocked* instead of
     * checking it. The measured ±20°/±20pp is exactly the kind of number a later tidy-up rounds off, so it has to be
     * checkable without an emulator — which is the reason this module exists at all.
     */
    internal fun turned(argb: Int, up: Boolean): Int {
        val red = (argb shr RedShift and ChannelMask) / ChannelMax
        val green = (argb shr GreenShift and ChannelMask) / ChannelMax
        val blue = (argb and ChannelMask) / ChannelMax
        val high = max(red, max(green, blue))
        val low = min(red, min(green, blue))
        val chroma = high - low
        val lightness = (high + low) * Midpoint

        val hue = when {
            chroma == 0f -> 0f
            high == red -> Sextant * ((green - blue) / chroma)
            high == green -> Sextant * ((blue - red) / chroma + 2f)
            else -> Sextant * ((red - green) / chroma + 4f)
        }
        // Saturation as HSL states it: the chroma against the most a color of this lightness could carry.
        val saturation = if (chroma == 0f) 0f else chroma / (1f - abs(lightness - Midpoint) / Midpoint)

        val way = if (up) 1f else -1f
        val turnedHue = (hue + way * HueTurn + FullTurn + FullTurn) % FullTurn
        val lifted = (lightness + way * LightnessLift).coerceIn(0f, 1f)
        return argbOf(argb ushr AlphaShift and ChannelMask, turnedHue, saturation, lifted)
    }

    /** The opposite conversion — an HSL triple back to a packed color, carrying [alpha] through unchanged. */
    private fun argbOf(alpha: Int, hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(lightness - Midpoint) / Midpoint) * saturation
        val sector = hue / Sextant
        // The three levels a channel can take in any sixth of the circle; which channel takes which is the table's.
        val levels = floatArrayOf(chroma, chroma * (1f - abs(sector % 2f - 1f)), 0f)
        val base = lightness - chroma * Midpoint
        val order = HueSectors[sector.toInt().coerceIn(0, HueSectors.lastIndex)]
        return (alpha shl AlphaShift) or
            (byteOf(levels[order[0]] + base) shl RedShift) or
            (byteOf(levels[order[1]] + base) shl GreenShift) or
            byteOf(levels[order[2]] + base)
    }

    /** One `0..1` channel as a color byte. */
    private fun byteOf(value: Float): Int = (value * ChannelMax).roundToInt().coerceIn(0, ChannelMask)

    /** One ripple term of a crest's interior shape: how tall, how many cycles across the frame, and where it starts. */
    internal data class Term(val amplitude: Float, val frequency: Float, val phase: Float)

    /** The interior shape *Distortion* gives one crest, summed from [LobeWeights]' worth of ripple terms. */
    internal data class Lobe(val terms: List<Term>) {
        companion object {
            fun random(random: Random): Lobe = Lobe(
                LobeWeights.map { weight ->
                    Term(
                        amplitude = weight,
                        frequency = MinLobeCycles + random.nextFloat() * (MaxLobeCycles - MinLobeCycles),
                        phase = random.nextFloat() * TwoPi,
                    )
                },
            )
        }
    }

    /** How the ripple is split between its terms — a broad lobe and a smaller one on it, summing to `1`. */
    private val LobeWeights = listOf(0.65f, 0.35f)

    /**
     * How many cycles a ripple term spans across the frame — half a lobe up to one and a half.
     *
     * Low, because theirs is: even at *Distortion* `100` a boundary of theirs turns at most once between the edges.
     * Anything above about two cycles pinches the crests into a squiggle no setting of the reference produces.
     */
    private const val MinLobeCycles = 0.5f
    private const val MaxLobeCycles = 1.5f

    /** How far full distortion may push a crest off its sweep, as a fraction of the frame — far enough to cross. */
    private const val WarpSweep = 0.35f

    /** How often a crest is sampled across the frame, in pixels — see [columnsFor]. */
    private const val ColumnStep = 4f

    /** Where a pixel's center sits within it, in canvas units — see [draw]. */
    private const val PixelCenter = 0.5f

    /** How finely a shadow's fade is stored; the mesh stretches it over [ShadowSpan] of the frame. */
    private const val FadeTexels = 256

    /** How far a crest's shadow reaches down the band under it, as a fraction of the frame — theirs, measured. */
    private const val ShadowSpan = 0.0625f

    /** The darkest a crest shades the band under it; `0.5` lands on the reference's own `×0.815`. */
    private const val MaxShadow = 0.37f

    /** The reference's *Palette gradients* transform, measured: a fifth of a lightness, a twentieth of a turn. */
    private const val HueTurn = 20f
    private const val LightnessLift = 0.2f

    /** The hue circle, and the sixth of it each RGB sector spans. */
    private const val FullTurn = 360f
    private const val Sextant = 60f

    /**
     * Which of the three levels each channel takes, per sixth of the hue circle — index `0` the full chroma, `1` the
     * partial one, `2` nothing.
     *
     * A table rather than the usual six-armed `when` because those arms differ *only* by this permutation: written
     * out they are six near-identical lines, and a transposed pair in one of them tints a single sixth of the wheel,
     * which is a wallpaper nobody can point at.
     */
    private val HueSectors = arrayOf(
        intArrayOf(0, 1, 2), // red to yellow
        intArrayOf(1, 0, 2), // yellow to green
        intArrayOf(2, 0, 1), // green to cyan
        intArrayOf(2, 1, 0), // cyan to blue
        intArrayOf(1, 2, 0), // blue to magenta
        intArrayOf(0, 2, 1), // magenta to red
    )

    /** The largest a color channel goes, as a float so the conversions read in `0..1`, and as its packed mask. */
    private const val ChannelMax = 255f
    private const val ChannelMask = 0xFF

    /** Where each channel sits in a packed color. */
    private const val AlphaShift = 24
    private const val RedShift = 16
    private const val GreenShift = 8

    /** Half of anything — the middle of the lightness range, and of a band's chroma. */
    private const val Midpoint = 0.5f

    private val PiF = PI.toFloat()
    private val TwoPi = (2.0 * PI).toFloat()

    /** Mixed into [Generator.render]'s seed so the frame's two band layouts differ without needing a second seed. */
    private const val RightEdgeSeed = 0x5749_4454_4832_4C52L

    /** The same, for the ripple terms — so the crests' interiors do not track either edge's layout. */
    private const val LobeSeed = 0x4C4F_4245_5741_5645L
}
