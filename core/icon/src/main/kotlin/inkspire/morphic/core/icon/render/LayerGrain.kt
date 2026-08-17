package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * The noise field a grain effect pushes its pixels through.
 *
 * [LayerRipple]'s counterpart, and here for the same reason: only the bake draws this, so nothing is competing with
 * the arithmetic — it is separated because a noise field that is subtly wrong looks exactly like a noise field that
 * is right, and because pulled out of `IconRenderer` it can be checked without an emulator.
 *
 * **Deterministic, and defined in fractions of the box.** The same recipe has to grain identically every time it is
 * baked — otherwise the icon would shimmer as the studio re-bakes, and a draft would not predict the full-size
 * result — and identically at 96px and 288px, which is why the field is sampled in normalised coordinates rather
 * than in pixels. That is also why there is no seed: a hash of position *is* the randomness, and a seed would be a
 * second control offering nothing the grain size does not.
 *
 * ## Three things make this grain rather than static, and each was a defect before
 *
 * **Gradient noise, not value noise.** Value noise picks a number *at* each lattice point and interpolates between
 * them, so its peaks and troughs land exactly on the lattice — which puts a visible square grid through the field
 * at every scale, and the artwork tears into axis-aligned chunks whatever size is asked for. Gradient noise puts a
 * random *direction* at each lattice point and reads the field as zero there, so the structure sits between the
 * points and has no grid to see.
 *
 * **Several octaves, not one.** One octave is one size of detail, which is what made the old field read as blobs:
 * a real grain has fine dust *and* larger clumps at once. [Octaves] samples at doubling frequencies and halving
 * amplitudes, which is the cheapest way to have both.
 *
 * **A quintic fade, not a smoothstep.** `6t⁵ − 15t⁴ + 10t³` has zero *second* derivative at the lattice as well as
 * zero first, so the field's rate of change is continuous too. With a smoothstep the second derivative jumps, which
 * a displacement makes visible as a faint crease along every lattice line — the artefact that is easy to mistake
 * for the grain itself.
 */
object LayerGrain {

    /**
     * How many octaves the field is built from, and how much quieter each is than the one before.
     *
     * Three is where the return stops being worth the cost: the fourth is an eighth as loud and at a frequency
     * that is already at the pixel on any bake the launcher asks for, so it adds arithmetic rather than detail.
     */
    private const val Octaves = 3
    private const val Persistence = 0.5f

    /**
     * The field's value at ([x], [y]) in lattice units, in −1..1 — [Octaves] of [noise] summed.
     *
     * [salt] picks *which* field: a displacement needs two independent ones to push in two dimensions, and sampling
     * the same field twice would push every pixel along the diagonal. Each octave is salted again from it, so the
     * fine detail of one axis is not the coarse structure of the other.
     */
    fun field(x: Float, y: Float, salt: Int): Float {
        var sum = 0f
        var total = 0f
        var frequency = 1f
        var amplitude = 1f

        for (octave in 0 until Octaves) {
            sum += noise(x * frequency, y * frequency, salt * SaltStride + octave) * amplitude
            total += amplitude
            frequency *= 2f
            amplitude *= Persistence
        }
        // Divided by what was actually summed rather than by a constant, so the range holds if [Octaves] changes.
        return (sum / total).coerceIn(-1f, 1f)
    }

    /**
     * One octave of gradient noise at ([x], [y]) in lattice units, in −1..1.
     *
     * The classic construction: a unit vector per lattice corner, each dotted with the offset from that corner to
     * the sample, then blended. Scaled by √2 because the dot products of unit gradients over a unit cell reach
     * ±1/√2 and the callers here want a field that spans its stated range.
     */
    fun noise(x: Float, y: Float, salt: Int): Float {
        val cellX = floor(x).toInt()
        val cellY = floor(y).toInt()
        val fracX = x - cellX
        val fracY = y - cellY
        val fadeX = fade(fracX)
        val fadeY = fade(fracY)

        val topLeft = dot(cellX, cellY, salt, fracX, fracY)
        val topRight = dot(cellX + 1, cellY, salt, fracX - 1f, fracY)
        val bottomLeft = dot(cellX, cellY + 1, salt, fracX, fracY - 1f)
        val bottomRight = dot(cellX + 1, cellY + 1, salt, fracX - 1f, fracY - 1f)

        val top = lerp(topLeft, topRight, fadeX)
        val bottom = lerp(bottomLeft, bottomRight, fadeX)
        return (lerp(top, bottom, fadeY) * Root2).coerceIn(-1f, 1f)
    }

    /**
     * The displacement a field pair produces, in **field units** (−1..1 per axis), written into [into].
     *
     * **This is where `directionality` is spent, and the whole of it is one decomposition.** The raw vector
     * (`fieldX`, `fieldY`) scatters every way at once. Split it into the part running *along* the effect's angle
     * and the part running across, scale the second by `1 − directionality`, and put them back together: at 0
     * nothing is scaled and the scatter is untouched, at 1 the across-part is gone entirely and every pixel moves
     * on one line. Every value between is a real look — the reference's wind-blown sand — which is what the
     * two-valued `GrainDrift` this replaced could not express.
     *
     * An out-parameter rather than a returned pair, because this runs once per pixel of a bake and a `Pair` there
     * is an allocation per pixel. Same shape `IconRenderer.resample` takes for its own sample position.
     */
    fun displace(grain: LayerEffect.Grain, fieldX: Float, fieldY: Float, into: FloatArray) {
        val directionality = grain.directionality.coerceIn(0f, 1f)
        if (directionality <= 0f) {
            into[0] = fieldX
            into[1] = fieldY
            return
        }

        // The studio's own convention: straight down at 0°, which puts 90° along +x.
        val radians = grain.angleDegrees * Math.PI.toFloat() / 180f
        val axisX = sin(radians)
        val axisY = cos(radians)

        val along = fieldX * axisX + fieldY * axisY
        val acrossX = fieldX - along * axisX
        val acrossY = fieldY - along * axisY
        val kept = 1f - directionality

        into[0] = along * axisX + acrossX * kept
        into[1] = along * axisY + acrossY * kept
    }

    /** How far the field pushes at its extreme, in pixels. */
    fun amplitudePx(grain: LayerEffect.Grain, sizePx: Int): Float = grain.amplitude * sizePx

    /**
     * How far apart the field's lattice points sit, in pixels — the size of the pieces the artwork tears into.
     *
     * **Exponential in the control's position, which is the fix for a slider whose useful half was unreachable.**
     * `grainSize` used to be the fraction itself, and the sizes worth having are bunched near the bottom of it: on
     * a linear travel from a five-hundredth of the box to a half, everything below a twentieth — dust through small
     * clusters, which is most of what anyone wants — lived in the first four percent of the slider. Geometric
     * spacing makes equal movements of the finger equal *ratios*, so the fine end gets as much travel as the coarse.
     *
     * **The floor is now the *reason* for the fine end rather than a clamp on it.** [FinestCell] is derived from
     * [MinCellPx] and the smallest size an icon is baked at, so the clamp below binds only at the very bottom of the
     * control instead of across its first third — see [FinestCell] for what that cost on a device and in the studio.
     *
     * **Floored at [MinCellPx], and that floor is load-bearing in a way a one-pixel one was not.** Gradient noise
     * reads *zero at every lattice point* — which is what removes the grid a value field puts through the picture,
     * and what makes a cell of about a pixel catastrophic: the samples land on the integers, every one of them
     * reads a zero, and the effect disappears entirely. That is exactly what happened to a home-screen icon, which
     * bakes at a size where the finest setting always reached the floor, while the studio's much larger canvas
     * escaped it and showed the grain the surface would not.
     *
     * **So the fine end is bounded by the bake, and that bound is real rather than a policy.** Structure finer than
     * a few pixels cannot exist on a small bitmap, so a recipe at the finest setting is *not* identical at 144px and
     * at 750px — the one place this file's "same at every bake size" promise cannot hold, because what it promises
     * is unrepresentable down there.
     */
    fun cellPx(grain: LayerEffect.Grain, sizePx: Int): Float {
        val position = grain.grainSize.coerceIn(0f, 1f)
        val fraction = FinestCell * (CoarsestCell / FinestCell).pow(position)
        return (fraction * sizePx).coerceAtLeast(MinCellPx)
    }

    /**
     * One lattice point's gradient, dotted with the offset to the sample — the randomness itself.
     *
     * **A table of directions, not an angle through `cos`/`sin`, and the difference is seconds.** The first version
     * turned the hash into an angle and called both trig functions on it, which is the textbook-prettiest form and
     * costs *forty-eight transcendental calls per output pixel* — four corners, three octaves, two fields, two calls
     * each. On a studio canvas that is tens of millions of them per bake, and it is the whole of why a preview took
     * seconds to arrive. The table is built once and read by three bits of the hash.
     *
     * The KDoc that argued against this said a small table leaves the field with a handful of preferred directions.
     * True of a table this size on a *single* octave; not true of what is drawn, because three octaves at different
     * frequencies and salts sum their directions, and the displacement is two independent fields besides.
     */
    private fun dot(x: Int, y: Int, salt: Int, offsetX: Float, offsetY: Float): Float {
        val index = (hash(x, y, salt) and (Directions - 1)) shl 1
        return GradientTable[index] * offsetX + GradientTable[index + 1] * offsetY
    }

    /** The hash behind every lattice point. Its low bits pick a gradient; nothing else reads it. */
    private fun hash(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return h xor (h shr 16)
    }

    /**
     * How many directions a gradient may take. A power of two, so the hash picks one with a mask rather than a
     * division — and sixteen is fine enough that no direction is visible in a field made of three octaves.
     */
    private const val Directions = 16

    /** The unit vectors themselves, as `[x0, y0, x1, y1, …]` — one flat array, so a lookup allocates nothing. */
    private val GradientTable = FloatArray(Directions * 2).also { table ->
        for (i in 0 until Directions) {
            val angle = i.toFloat() / Directions * TwoPi
            table[i * 2] = cos(angle)
            table[i * 2 + 1] = sin(angle)
        }
    }

    /** `6t⁵ − 15t⁴ + 10t³` — zero slope *and* zero curvature at both ends. See the class note. */
    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    /** Keeps one axis's octaves clear of the other's — see [field]. */
    private const val SaltStride = 977

    /**
     * The smallest lattice worth sampling, in pixels.
     *
     * **Four rather than one, and the difference is the effect existing.** The field is zero at each lattice point,
     * so a cell of one pixel puts every sample on a zero and displaces nothing; two puts half of them there. Four is
     * where every sample lands somewhere the field is actually doing something, whatever the phase.
     */
    private const val MinCellPx = 4f

    /**
     * The smallest bitmap the launcher ever bakes an icon into — roughly a home cell's icon, 48dp at 3× density.
     *
     * An estimate rather than a plumbed value, and it only has to be the right *order*: what it decides is where
     * [FinestCell] sits, and being out by a few pixels there moves the finest grain by a few percent. Plumbing the
     * real number would mean the noise field taking a dependency on the icon-sizing settings of every surface, to
     * answer a question about what a slider's bottom end should mean.
     */
    private const val SmallestBakePx = 144f

    /**
     * The two ends of [cellPx]'s ramp, as fractions of the box.
     *
     * **The fine end is derived from [MinCellPx] rather than chosen, and that is the fix for a slider whose bottom
     * third was redundant.** It used to be `0.006` — a cell of *four tenths of a pixel* on a home icon and nine
     * tenths on the studio's own draft, both far under the floor. So every grain size below ≈0.35 clamped to the same
     * 4px cell and rendered the *same picture*: on a device the control did nothing across a third of its travel, and
     * in the studio the draft preview stopped responding entirely down there — which reads as the preview having
     * frozen rather than as a slider with nothing left to say. (Its own KDoc claimed "a few pixels at 256"; at 256 it
     * was one and a half.)
     *
     * Anchoring it to the smallest bake makes the floor bind nowhere but the very bottom, so every position on the
     * slider is a different picture — **and it restores the promise this file is built on**, that one recipe grains
     * the same at every bake size. That promise was already broken down here and the class note admitted it: a
     * clamped cell is a constant number of *pixels*, so the same recipe came out as coarse dust on a 144px icon and
     * as an invisible shimmer on a 768px canvas. Nothing above the floor has that problem, because a fraction of the
     * box is a fraction of the box.
     *
     * What is given up is grain finer than about a thirtieth of the box — which was never drawable, so it is an
     * unreachable setting being removed rather than a look. **The stored value's meaning changes**: a saved
     * `grainSize` now maps to a coarser cell than it did. Affordable only because nothing has shipped.
     */
    private const val FinestCell = MinCellPx / SmallestBakePx
    private const val CoarsestCell = 0.5f

    private const val Root2 = 1.4142135f
    private const val TwoPi = 6.2831855f
}
