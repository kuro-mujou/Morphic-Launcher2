package inkspire.morphic.feature.home

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour spec for [FolderHostState] — the folder-interaction lifecycle a surface hosts.
 *
 * These pin the transitions *as they behave today*, which is the point: the state was previously inline in
 * `HomeScreen` and therefore untestable, so the extraction is the first chance to write them down. Anything that
 * reshapes this state machine (collapsing the flags into one sealed phase, fixing the inject flicker) has to keep
 * these green or explain itself.
 */
class FolderHostStateTest {

    private val app = ComponentKey("pkg", "Main")
    private val other = ComponentKey("pkg.other", "Main")
    private val folderId = 7L

    private fun host() = FolderHostState()

    @Test
    fun `starts with nothing open and nothing in flight`() {
        val host = host()
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.openFolderId)
        assertNull(host.incomingComponent)
    }

    @Test
    fun `open then close`() {
        val host = host()
        host.open(folderId)
        assertEquals(FolderPhase.Open(folderId), host.phase)
        assertEquals(folderId, host.openFolderId)
        host.close()
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.openFolderId)
    }

    // ── Extract: an app on its way out of the folder, onto the surface ──

    @Test
    fun `beginExtract records the app and leaves the folder open`() {
        val host = host()
        host.open(folderId)
        host.beginExtract(folderId, app)
        assertEquals(FolderPhase.Extracting(folderId, app), host.phase)
        assertEquals(folderId, host.openFolderId) // the overlay hides itself, but the folder is still "open"
    }

    @Test
    fun `a committed extract closes the folder`() {
        val host = host()
        host.open(folderId)
        host.beginExtract(folderId, app)
        host.close() // what the drop handler calls once it has committed (or declined) the landing
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.openFolderId)
    }

    @Test
    fun `a cancelled extract drag leaves the folder open with the app still in it`() {
        // The drop handler closes the folder itself; onDragEnd is the safety net for a *cancelled* gesture, where
        // returning to the open folder (nothing committed, nothing removed) is the right resting state.
        val host = host()
        host.open(folderId)
        host.beginExtract(folderId, app)
        host.onDragEnd()
        assertEquals(FolderPhase.Open(folderId), host.phase)
    }

    // ── Inject: an app on its way in, from the surface ──

    @Test
    fun `beginInject opens the folder and carries the app in`() {
        val host = host()
        host.beginInject(folderId, app)
        assertEquals(FolderPhase.Injecting(folderId, app), host.phase)
        assertEquals(folderId, host.openFolderId)
        assertEquals(app, host.incomingComponent)
    }

    @Test
    fun `injectCommitted clears the incoming app but keeps the folder open`() {
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        assertEquals(FolderPhase.Open(folderId), host.phase) // so the user sees the app land
        assertNull(host.incomingComponent)
    }

    @Test
    fun `injectCommitted does nothing when no inject is in flight`() {
        val host = host()
        host.open(folderId)
        host.injectCommitted()
        assertEquals(FolderPhase.Open(folderId), host.phase)
    }

    @Test
    fun `an inject dropped elsewhere closes the folder with the drag`() {
        // The folder was opened *by the gesture*, so if the gesture ends without committing, it goes away again.
        val host = host()
        host.beginInject(folderId, app)
        host.onDragEnd()
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.incomingComponent)
    }

    @Test
    fun `a committed inject survives the end of the drag`() {
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        host.onDragEnd()
        assertEquals(folderId, host.openFolderId)
    }

    @Test
    fun `a folder opened by tapping is unaffected by an unrelated drag ending`() {
        val host = host()
        host.open(folderId)
        host.onDragEnd()
        assertEquals(folderId, host.openFolderId)
    }

    @Test
    fun `beginInject after another folder is open retargets to the new folder`() {
        // The phase carries its own folder id, so an incoming app can never be left attached to a folder that is
        // no longer the open one.
        val host = host()
        host.open(folderId)
        host.beginInject(99L, other)
        assertEquals(FolderPhase.Injecting(99L, other), host.phase)
    }

    // ── The two hand-offs are mutually exclusive: one finger, one direction ──

    @Test
    fun `beginExtract replaces a pending inject rather than coexisting with it`() {
        val host = host()
        host.beginInject(folderId, other)
        host.beginExtract(folderId, app)
        assertEquals(FolderPhase.Extracting(folderId, app), host.phase)
        assertNull(host.incomingComponent) // no stale incoming app left behind
    }

    @Test
    fun `beginInject replaces a pending extract rather than coexisting with it`() {
        val host = host()
        host.open(folderId)
        host.beginExtract(folderId, app)
        host.beginInject(folderId, other)
        assertEquals(FolderPhase.Injecting(folderId, other), host.phase)
    }
}
