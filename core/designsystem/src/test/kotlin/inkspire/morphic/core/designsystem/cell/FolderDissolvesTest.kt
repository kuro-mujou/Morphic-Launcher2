package inkspire.morphic.core.designsystem.cell

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [FolderDissolves]: a folder gone and one of its members standing exactly where it stood is a dissolve. */
class FolderDissolvesTest {

    private val a = ComponentKey("pkg", "a")
    private val b = ComponentKey("pkg", "b")

    /** A surface state: its folders and its apps, positioned by a plain slot number. */
    private class State(val folders: List<FolderSpot<Int>>, val apps: List<Pair<ComponentKey, Int>>)

    private fun FolderDissolves<Int>.see(state: State) = observe(state, { state.folders }, { state.apps })

    private val folderAt3 = FolderSpot(id = 7, label = "Games", position = 3, members = setOf(a, b))

    @Test
    fun `the last app standing where its folder stood is the folder dissolving`() {
        val dissolves = FolderDissolves<Int>()
        dissolves.see(State(listOf(folderAt3), emptyList()))
        dissolves.see(State(emptyList(), listOf(a to 3, b to 5)))

        assertEquals("Games", dissolves.from(a))
        assertNull(dissolves.from(b))

        dissolves.finished(a)
        assertNull(dissolves.from(a))
    }

    @Test
    fun `a folder removed with nothing in its place is not a dissolve`() {
        val dissolves = FolderDissolves<Int>()
        dissolves.see(State(listOf(folderAt3), emptyList()))
        dissolves.see(State(emptyList(), listOf(a to 4)))

        assertNull(dissolves.from(a))
    }

    @Test
    fun `an app that was never a member does not inherit the folder`() {
        val stranger = ComponentKey("pkg", "stranger")
        val dissolves = FolderDissolves<Int>()
        dissolves.see(State(listOf(folderAt3), emptyList()))
        dissolves.see(State(emptyList(), listOf(stranger to 3)))

        assertNull(dissolves.from(stranger))
    }

    @Test
    fun `the same state seen twice is not recomputed`() {
        val dissolves = FolderDissolves<Int>()
        val before = State(listOf(folderAt3), emptyList())
        dissolves.see(before)
        var asked = 0
        dissolves.observe(before, { asked++; before.folders }, { asked++; before.apps })

        assertEquals(0, asked)
    }
}
