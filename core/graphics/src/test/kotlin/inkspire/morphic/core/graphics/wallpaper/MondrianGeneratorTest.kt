package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.graphics.wallpaper.MondrianGenerator.Rect
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The subdivision — the blocks must *partition* the frame (cover it, no overlap), because the fill trusts that and a
 * gap or overlap is a silently-wrong tiling no green build would catch. And the scrub, which must keep that partition
 * at every moment and land on each end's own blocks.
 */
class MondrianGeneratorTest {

    /**
     * The failure that killed this design at its own default: the color mode reduces every palette to **two** stops,
     * and the old rule handed a two-stop palette the ground for every block — 96% bare paper under a ruling.
     */
    @Test
    fun `a two-stop palette still has accents, and they are neither the ground nor the ink`() {
        val ground = 0xFFF2E2C4.toInt()
        val ink = 0xFF121E2B.toInt()
        val accents = MondrianGenerator.accents(Palette(listOf(ground, ink)))
        assertTrue("a two-stop palette must still accent", accents.isNotEmpty())
        accents.forEach {
            assertNotEquals("an accent must not be the ground", ground, it)
            assertNotEquals("an accent must not be the ink", ink, it)
        }
    }

    @Test
    fun `a full palette accents with exactly its middle stops`() {
        val stops = listOf(0xFFF2E2C4, 0xFFE6A15C, 0xFFC9603E, 0xFF2C6E6B, 0xFF1F3A4D, 0xFF121E2B).map { it.toInt() }
        assertEquals(stops.subList(1, stops.size - 1), MondrianGenerator.accents(Palette(stops)))
    }

    @Test
    fun `a single-stop palette has nothing to accent with, and every block is ground`() {
        val accents = MondrianGenerator.accents(Palette(listOf(0xFF808080.toInt())))
        assertTrue(accents.isEmpty())
        assertEquals(MondrianGenerator.Ground, MondrianGenerator.tone(Random(1), accents.size))
    }

    @Test
    fun `density maps to the pass count range`() {
        assertEquals(3, MondrianGenerator.passes(0f))
        assertEquals(7, MondrianGenerator.passes(1f))
        assertEquals(3, MondrianGenerator.passes(-1f)) // clamped
    }

    @Test
    fun `the blocks cover the whole frame — their areas sum to one`() {
        assertEquals(1.0, area(blocks(passes = 6, seed = 11L)), 1e-4)
    }

    @Test
    fun `no two blocks overlap`() {
        assertNoOverlap(blocks(passes = 5, seed = 3L))
    }

    @Test
    fun `the same seed yields the same blocks, so a recipe reproduces`() {
        assertEquals(blocks(5, 42L), blocks(5, 42L))
    }

    /**
     * **The frame stays whole at every moment of a scrub** — over shuffles whose trees disagree at the root, where a
     * cut leaves one way while the other tree's structure arrives across it. The cuts slide, so this cannot fail by
     * construction unless the slide and the split stop agreeing on a region; this is the check that they do.
     */
    @Test
    fun `the frame stays whole at every moment of a scrub`() {
        for (seed in 1L..6L) {
            val morph = morph(seed, seed + 50)
            for (step in 0..20) {
                val blocks = ArrayList<Rect>()
                morph.at(step / 20f) { rect, _, _ -> blocks.add(rect) }
                assertEquals("seed $seed at ${step / 20f}", 1.0, area(blocks), 1e-4)
                assertNoOverlap(blocks)
            }
        }
    }

    /**
     * **A scrub's ends are the two Mondrians it is between**, block for block and tone for tone, once the blocks that
     * are only arriving or already gone — which stand at no width at all there — are left out. The morph hands over to
     * the bake at both ends, so a difference here would be a jump the moment the finger lifts.
     */
    @Test
    fun `a scrub starts on one Mondrian's blocks and ends on the other's`() {
        for (seed in 1L..6L) {
            val from = MondrianGenerator.plan(DesignParams(), accents = 4, seed = seed)
            val to = MondrianGenerator.plan(DesignParams(), accents = 4, seed = seed + 50)
            val morph = requireNotNull(MondrianGenerator.morph(from, to))
            for ((t, plan) in listOf(0f to from, 1f to to)) {
                val seen = ArrayList<Pair<Rect, Int>>()
                morph.at(t) { rect, fromTone, toTone ->
                    if (rect.width > 0f && rect.height > 0f) seen.add(rect to if (t == 0f) fromTone else toTone)
                }
                assertEquals("seed $seed at $t", plan.blocks.map { it.rect to it.tone }, seen)
            }
        }
    }

    @Test
    fun `two Mondrians toned for different accents are refused rather than paired`() {
        val four = MondrianGenerator.plan(DesignParams(), accents = 4, seed = 1L)
        assertNull(MondrianGenerator.morph(four, MondrianGenerator.plan(DesignParams(), accents = 2, seed = 2L)))
        assertNotNull(MondrianGenerator.morph(four, MondrianGenerator.plan(DesignParams(), accents = 4, seed = 2L)))
    }

    private fun blocks(passes: Int, seed: Long) =
        MondrianGenerator.blocks(MondrianGenerator.subdivide(passes, Random(seed))).map { it.rect }

    private fun morph(from: Long, to: Long) = requireNotNull(
        MondrianGenerator.morph(
            MondrianGenerator.plan(DesignParams(density = 1f), accents = 4, seed = from),
            MondrianGenerator.plan(DesignParams(density = 1f), accents = 4, seed = to),
        ),
    )

    private fun area(blocks: List<Rect>) = blocks.sumOf { (it.width * it.height).toDouble() }

    private fun assertNoOverlap(blocks: List<Rect>) {
        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                val a = blocks[i]
                val b = blocks[j]
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue("blocks $i and $j overlap", overlapX <= 1e-4f || overlapY <= 1e-4f)
            }
        }
    }
}
