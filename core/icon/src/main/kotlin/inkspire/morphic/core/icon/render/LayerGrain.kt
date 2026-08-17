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
 * ## Four things make this grain rather than static, and each was a defect before
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
 *
 * **Pixel centres, not pixel corners** — [latticeAt]. The three above are about the field; this one is about where it
 * is read. Since the field is zero *at* the lattice, sampling corners drops every `cellPx`-th sample onto nothing,
 * which costs nothing visible at coarse cells and is the whole effect at fine ones.
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
     * The line a directional grain runs along, plus how much of the scatter is forced onto it — everything [displace]
     * needs that is the same for every pixel of a bake.
     *
     * **It exists because the trig was inside the per-pixel loop.** `displace` used to take the effect and resolve the
     * angle itself, so a bake spent two transcendental calls per output pixel on a value that cannot change within it —
     * over a million of them on a studio canvas. That is the same mistake, one function along, that [dot]'s KDoc
     * records as "the whole of why a preview took seconds to arrive": the angle is a property of the *recipe*, so it
     * belongs where the recipe is read once.
     *
     * @property directionality already coerced, so the loop does not repeat that either.
     */
    data class Drift(val directionality: Float, val axisX: Float, val axisY: Float)

    /** [Drift] for this grain — resolved **once per bake**, never per pixel. */
    fun driftOf(grain: LayerEffect.Grain): Drift {
        val directionality = grain.directionality.coerceIn(0f, 1f)
        // The studio's own convention: straight down at 0°, which puts 90° along +x.
        val radians = grain.angleDegrees * Math.PI.toFloat() / 180f
        return Drift(directionality, axisX = sin(radians), axisY = cos(radians))
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
     *
     * It takes a [Drift] rather than the effect for the same reason: what it needs from the recipe is two floats, and
     * deriving them here meant deriving them a million times. See [driftOf].
     */
    fun displace(drift: Drift, fieldX: Float, fieldY: Float, into: FloatArray) {
        val directionality = drift.directionality
        if (directionality <= 0f) {
            into[0] = fieldX
            into[1] = fieldY
            return
        }

        val axisX = drift.axisX
        val axisY = drift.axisY

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
     * Where pixel [pixel] sits on the lattice, in cells — **its centre, not its corner**.
     *
     * A pixel is an area and its centre is where it should be sampled, which is textbook and at coarse cells makes no
     * visible difference at all. At fine ones it decides whether the effect exists: gradient noise reads **zero at
     * every lattice point**, and sampling corners puts every `cellPx`-th sample exactly on one. At a four-pixel cell
     * that is a quarter of them landing on nothing; at two, half; at one, all of them, which is why the floor below
     * used to have to be four.
     *
     * Offset by half a *pixel* rather than half a cell, deliberately: the correction is about where a pixel is, so it
     * cannot depend on how large the cells happen to be. At any cell size the samples then straddle the lattice
     * instead of landing on it.
     *
     * Shared with the test rather than written into the renderer's loop, because it is one line whose being wrong is
     * invisible — the picture still looks like noise, it is merely weaker than it should be.
     */
    fun latticeAt(pixel: Int, cellPx: Float): Float = (pixel + 0.5f) / cellPx

    /**
     * How far apart the field's lattice points sit, in pixels — the size of the pieces the artwork tears into.
     *
     * **Exponential in the control's position, which is the fix for a slider whose useful half was unreachable.**
     * `grainSize` used to be the fraction itself, and the sizes worth having are bunched near the bottom of it: on
     * a linear travel from a five-hundredth of the box to a half, everything below a twentieth — dust through small
     * clusters, which is most of what anyone wants — lived in the first four percent of the slider. Geometric
     * spacing makes equal movements of the finger equal *ratios*, so the fine end gets as much travel as the coarse.
     *
     * **The floor is the *reason* for the fine end rather than a clamp on it.** [FinestCell] is derived from
     * [MinCellPx] and [GrainFidelityPx], so at any size from [GrainFidelityPx] up the coercion below never binds and
     * every position on the control is a different picture. It used to bind across the first third of the travel, and
     * [FinestCell] records what that cost.
     *
     * **The coercion stays, because [sizePx] is not the control's to know.** A bake *below* [GrainFidelityPx] — a
     * 128px layer tile, a thumbnail — is still asked for cells finer than it can carry, and there the clamp is what
     * keeps the field a field; [MinCellPx] says what it would otherwise degenerate into. So the promise this file is
     * built on, that one recipe grains the same at every bake size, holds as a *fraction of the box* everywhere above
     * that size and gives out below it, on the bottom sliver of the control only.
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
     * **Two, and it is [latticeAt] that made two possible.** The field is zero at each lattice point, so while the
     * renderer sampled pixel *corners* a cell of one pixel put every sample on a zero and displaced nothing, and two
     * put half of them there — four was the smallest cell at which every sample landed somewhere the field was doing
     * something, whatever the phase. Sampling pixel centres removes the coincidence entirely: at a two-pixel cell the
     * samples fall on quarter and three-quarter phases, both well away from the zeros.
     *
     * **Two rather than one, because one is not a lattice.** At a single-pixel cell every sample sits at the centre of
     * its own cell, so neighbouring pixels share nothing and the field degenerates into per-pixel confetti — which is
     * the look this whole construction exists to avoid. Two is the finest structure a bitmap can actually carry.
     *
     * It is one of [FinestCell]'s two inputs, so halving it would halve the finest grain the slider can ask for at
     * every bake size at once — see [GrainFidelityPx] for why that is not the knob that was turned.
     */
    private const val MinCellPx = 2f

    /**
     * The bake size at which the finest setting is guaranteed to render exactly as asked.
     *
     * **Three things coincide at 144 and the control is honest only while they do**: it is roughly the smallest
     * bitmap the launcher bakes an icon into (a home cell's icon, 48dp at 3× density), it is the size the studio
     * *drafts* at (`IconPreview.DraftPx`), and it is this. So the finest grain the slider can ask for is the finest
     * grain the smallest icon can draw — and also the finest the picture being dragged against can show.
     *
     * **Raising it to 288 was tried and reverted, and the failure is worth keeping.** It bought a genuinely finer
     * grain — 5.3px cells on the studio canvas rather than 10.7 — and the author noticed within a minute that the
     * preview had stopped responding below a grain size of about 0.15. Not speed: the draft is 144px, so the bottom
     * sixth of the ramp clamped *there* and every draft in that range came back identical. Which is precisely the
     * defect this file spent an evening removing, reintroduced by the same mistake in a new place — reasoning about
     * the cost to *home icons* while forgetting that the picture the user actually drags against is the same size.
     *
     * So the rule is not "144" but **the largest of the three**: anything finer than the draft can show is a control
     * that does nothing under the finger, whatever it does on release. Finer grain is available, and its real price is
     * a larger [inkspire.morphic.core.icon.compose.IconPreview] draft — four times the pixels at 288, against a
     * measured 19ms at 144 — which is a decision about drag latency rather than about noise.
     *
     * An estimate rather than a plumbed value, and it only has to be the right *order*.
     */
    private const val GrainFidelityPx = 144f

    /**
     * The two ends of [cellPx]'s ramp, as fractions of the box.
     *
     * **The fine end is derived from [MinCellPx] and [GrainFidelityPx] rather than chosen**, which is the fix for a
     * slider whose bottom third was redundant. It used to be `0.006` — a cell of *four tenths of a pixel* on a home
     * icon and nine tenths on the studio's own draft, both far under the floor — so every grain size below ≈0.35
     * clamped to the same cell and drew the *same picture*: on a device the control did nothing across a third of its
     * travel, and in the studio the preview stopped responding entirely down there, which reads as the preview having
     * frozen rather than as a slider with nothing left to say. (Its own KDoc claimed "a few pixels at 256"; at 256 it
     * was one and a half. That claim is the reason nobody checked.)
     *
     * Derived, the fine end is *by construction* the finest grain that renders as asked at [GrainFidelityPx] — so
     * above that size every position on the slider is a different picture, and one recipe grains the same at every
     * such size, which is the promise this file is built on. Below it the bottom of the control clamps; see
     * [GrainFidelityPx] for how much of it and why that is the accepted trade.
     *
     * What is given up is grain finer than a seventy-second of the box, which is beyond what a small icon can carry
     * anyway. **The stored value's meaning changes** whenever these two move, since a saved `grainSize` is a position
     * on this ramp rather than a size. Affordable only because nothing has shipped.
     */
    private const val FinestCell = MinCellPx / GrainFidelityPx
    private const val CoarsestCell = 0.5f

    private const val Root2 = 1.4142135f
    private const val TwoPi = 6.2831855f
}
