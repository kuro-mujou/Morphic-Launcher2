package inkspire.morphic.core.model.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each backdrop *means*, pinned per value.
 *
 * The three predicates here are read by three different drawing paths — the canvas's backdrop, the cycle button's
 * swatch, and the system bars' icon color — and every one of them is a judgment about the same five values that
 * nothing else can check. A wrong entry is invisible in code and obvious only on the one backdrop it affects, which is
 * exactly the kind of thing worth a table.
 *
 * [PreviewBackground.darkSystemBarIcons] is the one to be most careful with, because the platform field it feeds
 * (`isAppearanceLightStatusBars`) is named for the *background* rather than the icons — so the two read as opposites and
 * a mistake looks correct.
 */
class PreviewBackgroundTest {

    @Test
    fun `the checkerboard is what the studio opens on`() {
        assertEquals(PreviewBackground.CHECKERBOARD, PreviewBackground.Default)
    }

    @Test
    fun `only the bare checkerboard fills the whole canvas with checkers`() {
        assertEquals(
            setOf(PreviewBackground.CHECKERBOARD),
            PreviewBackground.entries.filter { it.checkersOutsideBound }.toSet(),
        )
    }

    @Test
    fun `the icon's bound shows checkers in the bare checkerboard and both mixes`() {
        assertEquals(
            setOf(
                PreviewBackground.CHECKERBOARD,
                PreviewBackground.BLACK_WITH_CHECKER,
                PreviewBackground.WHITE_WITH_CHECKER,
            ),
            PreviewBackground.entries.filter { it.checkersInsideBound }.toSet(),
        )
    }

    /**
     * The bars sit over the *surround*, so a mix follows the color it is named for — and the bare checkerboard counts
     * as light, since a white glyph is lost on both of its grays.
     */
    @Test
    fun `the bars go dark over every backdrop that is light where they sit`() {
        assertEquals(
            setOf(
                PreviewBackground.WHITE,
                PreviewBackground.WHITE_WITH_CHECKER,
                PreviewBackground.CHECKERBOARD,
            ),
            PreviewBackground.entries.filter { it.darkSystemBarIcons }.toSet(),
        )
    }

    @Test
    fun `cycling reaches every backdrop and comes back`() {
        val start = PreviewBackground.Default
        val visited = mutableListOf(start)
        var current = start
        repeat(PreviewBackground.entries.size - 1) {
            current = current.next()
            visited += current
        }

        assertEquals(PreviewBackground.entries.toSet(), visited.toSet())
        assertEquals(start, current.next())
    }

    /**
     * A backdrop cannot claim checkers *outside* the icon's bound without having them inside it — that would draw a flat
     * hole in the middle of a checkerboard, which is not one of the five looks and would read as a rendering fault.
     */
    @Test
    fun `checkers outside the bound imply checkers within it`() {
        PreviewBackground.entries.filter { it.checkersOutsideBound }.forEach {
            assertTrue("$it checkers outside its bound but not inside", it.checkersInsideBound)
        }
    }
}
