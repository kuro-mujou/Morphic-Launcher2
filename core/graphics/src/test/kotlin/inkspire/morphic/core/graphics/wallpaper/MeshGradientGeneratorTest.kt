package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lattice: which node takes which color, and which nodes may move.
 *
 * Both fail *quietly*. Bands that run diagonally instead of across the frame read as a design choice, not as a
 * row/column transposition. And a boundary node left free to move drags the frame's own corners off the distribution,
 * so a *Corners* wash quietly stops having the palette's ends at its corners. Neither needs a bitmap to catch.
 */
class MeshGradientGeneratorTest {

    private val palette = Palette(listOf(0xFF241B4E.toInt(), 0xFFB65A78.toInt(), 0xFFFFD9A0.toInt()))

    private fun mesh(side: Int = 3, warp: Float = 0.5f, softness: Float = 0f, variant: Int = 0, seed: Long = 99L) =
        MeshGradientGenerator.mesh(side, warp, softness, variant, palette, seed)

    @Test
    fun `the side counts patches, so the lattice has one more node per axis`() {
        assertEquals(4, mesh(side = 3).span)
        assertEquals(16, mesh(side = 3).colors.size)
        assertEquals(81, mesh(side = 8).colors.size)
    }

    @Test
    fun `the same seed yields the same lattice, so a recipe reproduces`() {
        assertTrue(mesh(seed = 7L).dx.contentEquals(mesh(seed = 7L).dx))
        assertTrue(mesh(seed = 7L).colors.contentEquals(mesh(seed = 7L).colors))
    }

    @Test
    fun `a different seed warps it differently`() {
        assertNotEquals(mesh(seed = 1L).dx.toList(), mesh(seed = 2L).dx.toList())
    }

    @Test
    fun `no warp leaves the lattice true, whatever the seed`() {
        val rigid = mesh(side = 4, warp = 0f, seed = 1L)
        assertTrue("a rigid mesh must not move at all", rigid.dx.all { it == 0f } && rigid.dy.all { it == 0f })
    }

    @Test
    fun `the warp lattice is its own, and coarser than the colors at any density`() {
        // Tying the displacements to the color lattice makes each tongue one colour-cell wide, which reads as a drip.
        for (side in listOf(2, 5, 8)) {
            val m = mesh(side = side, warp = 1f)
            assertEquals("the warp lattice must not follow the density knob", 16, m.dx.size)
            assertTrue("the colors must still follow it", m.colors.size == m.span * m.span)
        }
    }

    @Test
    fun `boundary nodes never move, so the frame keeps its own corners`() {
        val m = mesh(side = 4, warp = 1f)
        val span = kotlin.math.sqrt(m.dx.size.toDouble()).toInt()
        for (r in 0 until span) {
            for (c in 0 until span) {
                val onEdge = r == 0 || r == span - 1 || c == 0 || c == span - 1
                if (!onEdge) continue
                val i = r * span + c
                assertEquals("node ($r, $c) is on the boundary and moved", 0f, m.dx[i], 0f)
                assertEquals("node ($r, $c) is on the boundary and moved", 0f, m.dy[i], 0f)
            }
        }
        assertTrue("nothing moved at all", m.dx.any { it != 0f })
    }

    @Test
    fun `vertical reads the ramp down the rows and ignores the columns`() {
        val m = mesh(side = 3, variant = 0)
        val span = m.span
        for (r in 0 until span) {
            val inRow = (0 until span).map { m.colors[r * span + it] }.toSet()
            assertEquals("row $r must be one color, or the bands run diagonally", 1, inRow.size)
        }
        assertEquals("the top row is the ramp's start", palette.colorAt(0), m.colors.first())
        assertEquals("the bottom row is the ramp's end", palette.colorAt(palette.size - 1), m.colors.last())
        assertEquals("every row must be its own step", span, m.colors.toSet().size)
    }

    @Test
    fun `corners puts the ramp's ends at opposite corners`() {
        val m = mesh(side = 3, variant = 1)
        val span = m.span
        assertEquals("the top-left corner is the ramp's start", palette.colorAt(0), m.colors[0])
        assertEquals("the bottom-right corner is the ramp's end", palette.colorAt(palette.size - 1), m.colors.last())
        assertEquals("every node is its own blend", span * span, m.colors.toSet().size)
    }

    @Test
    fun `scattered cycles the palette, so every stop appears`() {
        assertEquals(palette.colors.toSet(), mesh(side = 3, variant = 2).colors.toSet())
    }

    @Test
    fun `no softness leaves the colors alone, and full softness pulls the middle in`() {
        val crisp = mesh(side = 4, softness = 0f).colors
        val washed = mesh(side = 4, softness = 1f).colors
        assertNotEquals(crisp.toList(), washed.toList())

        // Softening is a low pass, so the spread between the darkest and lightest node can only shrink.
        fun spread(colors: IntArray) = colors.maxOf { it and 0xFF } - colors.minOf { it and 0xFF }
        assertTrue("softness must reduce contrast", spread(washed) < spread(crisp))
    }

    @Test
    fun `softening is symmetric, so a uniform lattice stays uniform`() {
        val flat = IntArray(16) { 0xFF808080.toInt() }
        MeshGradientGenerator.soften(flat, span = 4, strength = 1f)
        assertTrue("a low pass must not invent contrast", flat.all { it == 0xFF808080.toInt() })
    }

    @Test
    fun `a node's own color is read exactly at its own position`() {
        val m = mesh(side = 2, warp = 0f, variant = 0)
        // Node (1, 1) of a 3×3 lattice sits at the centre — the bilinear sample there is that node, not a blend.
        assertEquals(m.colors[1 * m.span + 1], MeshGradientGenerator.sampleColor(m, 0.5f, 0.5f))
        assertEquals(m.colors[0], MeshGradientGenerator.sampleColor(m, 0f, 0f))
        assertEquals(m.colors.last(), MeshGradientGenerator.sampleColor(m, 1f, 1f))
    }

    @Test
    fun `a sample off the edge reads the edge rather than wrapping`() {
        val m = mesh(side = 2, warp = 0f)
        assertEquals(MeshGradientGenerator.sampleColor(m, 0f, 0f), MeshGradientGenerator.sampleColor(m, -0.5f, -2f))
        assertEquals(MeshGradientGenerator.sampleColor(m, 1f, 1f), MeshGradientGenerator.sampleColor(m, 3f, 1.2f))
    }
    /**
     * **A field morph has nothing to pair**, which is what separates this bucket from the primitive one.
     *
     * Two lattices of the same shape put every node opposite its counterpart by construction, so a moment between
     * them is a linear pass and no correspondence has to be found, resampled or aligned. The whole of `GlassTree`
     * exists because a subdivision cannot say that.
     */
    @Test
    fun `a moment of a field morph is its two lattices, node for node`() {
        val palette = Palette(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        val from = MeshGradientGenerator.mesh(3, warp = 0.5f, softness = 0f, variant = 0, palette = palette, seed = 1L)
        val to = MeshGradientGenerator.mesh(3, warp = 0.5f, softness = 0f, variant = 0, palette = palette, seed = 2L)
        val morph = requireNotNull(MeshGradientGenerator.morph(from, to))

        val middle = morph.at(0.5f)
        for (i in from.dx.indices) {
            assertEquals((from.dx[i] + to.dx[i]) / 2f, middle.dx[i], 1e-5f)
            assertEquals((from.dy[i] + to.dy[i]) / 2f, middle.dy[i], 1e-5f)
        }
        assertEquals(from.colors.size, middle.colors.size)
        assertEquals(from.side, middle.side)
    }

    /** Both ends of a scrub are the plans themselves, as the primitive bucket's are. */
    @Test
    fun `a field morph begins and ends on the lattices themselves`() {
        val palette = Palette(listOf(0xFF102030.toInt(), 0xFFA0B0C0.toInt()))
        val from = MeshGradientGenerator.mesh(4, warp = 0.3f, softness = 0.2f, variant = 0, palette = palette, seed = 7L)
        val to = MeshGradientGenerator.mesh(4, warp = 0.3f, softness = 0.2f, variant = 0, palette = palette, seed = 8L)
        val morph = requireNotNull(MeshGradientGenerator.morph(from, to))

        assertSame(from, morph.at(0f))
        assertSame(to, morph.at(1f))
        assertSame(from, morph.at(-1f))
        assertSame(to, morph.at(2f))
    }

    /**
     * Two lattices of different sizes have no node-for-node correspondence, so there is no morph to give.
     *
     * **Refused rather than resampled**, since nothing scrubs between two densities: a shuffle re-seeds and leaves
     * every knob alone. Resampling one lattice onto the other is what that caller would need, and it does not exist.
     */
    @Test
    fun `two lattices of different densities have no morph between them`() {
        val palette = Palette(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        val small = MeshGradientGenerator.mesh(2, 0.4f, 0f, 0, palette, seed = 1L)
        val large = MeshGradientGenerator.mesh(5, 0.4f, 0f, 0, palette, seed = 1L)

        assertNull(MeshGradientGenerator.morph(small, large))
    }

}
