package inkspire.morphic.core.graphics.wallpaper

import kotlin.random.Random

/**
 * Smooth 2D gradient noise — the field a flow generator reads its directions from, and the reusable noise the rest
 * of the generative engine will want.
 *
 * **Gradient noise, not value noise, for `LayerGrain`'s reason one subsystem over.** Value noise picks a number *at*
 * each lattice point and interpolates, so its structure lands on the integer grid and a flow field built on it swirls
 * in visible squares. Gradient (Perlin) noise puts a random *direction* at each lattice point and reads the field as
 * zero there, so the structure sits between the points and there is no grid to see.
 *
 * **Deterministic in [seed]:** the permutation table is a seeded shuffle, so the same seed is the same field forever —
 * which is what lets a wallpaper recipe reproduce and a flow field be traced identically every time.
 *
 * Pure `Float` arithmetic, checkable without a bitmap — a noise that is subtly wrong looks like a *different*
 * plausible field rather than an error, so it earns its own tests.
 */
class PerlinNoise2d(seed: Long) {

    // The classic doubled permutation table: 0..255 shuffled by the seed, then repeated so an index plus a lattice
    // offset never runs off the end without a modulo.
    private val perm: IntArray = run {
        val table = (0..TableMask).shuffled(Random(seed)).toIntArray()
        IntArray(table.size * 2) { table[it % table.size] }
    }

    /**
     * The noise at ([x], [y]) — roughly `-1..1`, zero on the integer lattice, smooth everywhere between.
     *
     * A caller scales its input to choose the feature size: small coordinates give broad swells, larger ones finer
     * detail. A flow field maps the result to an angle.
     */
    fun at(x: Float, y: Float): Float {
        val xi = fastFloor(x) and TableMask
        val yi = fastFloor(y) and TableMask
        val xf = x - fastFloor(x)
        val yf = y - fastFloor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = perm[perm[xi] + yi]
        val ba = perm[perm[xi + 1] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val bb = perm[perm[xi + 1] + yi + 1]

        val lower = lerp(grad(aa, xf, yf), grad(ba, xf - 1f, yf), u)
        val upper = lerp(grad(ab, xf, yf - 1f), grad(bb, xf - 1f, yf - 1f), u)
        return lerp(lower, upper, v)
    }

    private fun fastFloor(v: Float): Int = if (v >= 0f) v.toInt() else v.toInt() - 1

    /** The quintic fade `6t⁵ − 15t⁴ + 10t³` — zero first *and* second derivative at the lattice, so no crease shows. */
    private fun fade(t: Float): Float = t * t * t * (t * (t * FadeA - FadeB) + FadeC)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)

    /** One of four diagonal gradients chosen by the hash's low bits — the dot product of that gradient with (x, y). */
    private fun grad(hash: Int, x: Float, y: Float): Float = when (hash and GradMask) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        else -> -x - y
    }

    private companion object {
        const val TableMask = 255
        const val GradMask = 3
        const val FadeA = 6f
        const val FadeB = 15f
        const val FadeC = 10f
    }
}
