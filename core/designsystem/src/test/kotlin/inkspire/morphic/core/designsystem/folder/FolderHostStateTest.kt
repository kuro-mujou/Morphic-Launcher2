package inkspire.morphic.core.designsystem.folder

import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour spec for [FolderHostState] — the folder-interaction lifecycle a surface hosts.
 *
 * The cases below are written from the gesture they belong to, because that is what the state machine is for: one
 * uninterrupted drag can open a folder, leave it, and open another (or the same one again), and the thing that has to
 * stay true across all of it is *which folder the drag started in*. Anything that reshapes this has to keep these
 * green or explain itself.
 */
class FolderHostStateTest {

    private val app = ComponentKey("pkg", "Main")
    private val other = ComponentKey("pkg.other", "Main")
    private val folderId = 7L
    private val otherFolderId = 99L

    private fun host() = FolderHostState()

    @Test
    fun `starts with nothing open and nothing in flight`() {
        val host = host()
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.openFolderId)
        assertNull(host.incomingComponent)
        assertNull(host.dragSourceFolderId)
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

    // ── Where the drag came from: fixed on lift, and the only thing that outlives the folders it visits ──

    @Test
    fun `a drag lifted inside a folder is owed to it`() {
        val host = host()
        host.open(folderId)
        host.onDragStart()
        assertEquals(folderId, host.dragSourceFolderId)
    }

    @Test
    fun `a drag lifted off the surface owes no folder`() {
        val host = host()
        host.onDragStart()
        assertNull(host.dragSourceFolderId)
    }

    @Test
    fun `an app carried into a folder from the surface does not make it the source`() {
        // The distinction that makes re-entry work: this folder holds no pointer and is owed no removal, so nothing
        // pins it and nothing stops the drag opening it again later.
        val host = host()
        host.onDragStart()
        host.beginInject(folderId, app)
        assertNull(host.dragSourceFolderId)
    }

    @Test
    fun `the source survives every folder the drag then visits`() {
        val host = host()
        host.open(folderId)
        host.onDragStart()
        host.leaveFolder()
        host.beginInject(otherFolderId, app) // carried into a second folder…
        host.leaveFolder() // …and back out of it
        host.beginInject(otherFolderId, app)
        assertEquals(folderId, host.dragSourceFolderId) // still owed to the folder it was lifted from
    }

    @Test
    fun `the source is scoped to one drag`() {
        val host = host()
        host.open(folderId)
        host.onDragStart()
        host.onDragEnd()
        assertNull(host.dragSourceFolderId)
    }

    // ── Leaving: the folder closes and the same drag carries on beneath it ──

    @Test
    fun `leaving closes the folder`() {
        val host = host()
        host.open(folderId)
        host.onDragStart()
        host.leaveFolder()
        assertEquals(FolderPhase.Closed, host.phase)
        assertNull(host.openFolderId) // genuinely closed, not hidden — this is what allows re-entry
    }

    @Test
    fun `a folder left mid-drag can be opened again by the same drag`() {
        val host = host()
        host.open(folderId)
        host.onDragStart()
        host.leaveFolder()
        host.beginInject(folderId, app)
        assertEquals(FolderPhase.Injecting(folderId, app), host.phase)
        assertEquals(folderId, host.openFolderId)
    }

    @Test
    fun `a drag cancelled after leaving does not re-open the folder`() {
        // Leaving was deliberate (a full dwell), so an abandoned gesture rests where the user put it: nothing was
        // written, the app is still in the folder, and the folder is shut.
        val host = host()
        host.open(folderId)
        host.onDragStart()
        host.leaveFolder()
        host.onDragEnd()
        assertEquals(FolderPhase.Closed, host.phase)
    }

    // ── Entering: an app on its way in, from the surface or from another folder ──

    @Test
    fun `beginInject opens the folder and carries the app in`() {
        val host = host()
        host.beginInject(folderId, app)
        assertEquals(FolderPhase.Injecting(folderId, app), host.phase)
        assertEquals(folderId, host.openFolderId)
        assertEquals(app, host.incomingComponent)
    }

    @Test
    fun `injectCommitted keeps carrying the app until the store catches up`() {
        // The app is off the surface (dropped optimistically) and not yet in the folder's persisted contents, so
        // releasing it here is what made it blink out of existence for a frame or two.
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        assertEquals(FolderPhase.Injected(folderId, app), host.phase)
        assertEquals(app, host.incomingComponent) // still rendered by the folder, from the outside
        assertEquals(folderId, host.openFolderId) // and the folder stays open, so the user sees it land
    }

    @Test
    fun `the hand-off ends once the folder's members include the injected app`() {
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        host.onMembersChanged(listOf(other, app))
        assertEquals(FolderPhase.Open(folderId), host.phase)
        assertNull(host.incomingComponent) // the folder's own data can render it now
    }

    @Test
    fun `membership without the injected app keeps carrying it`() {
        // The pre-write membership arriving must not be mistaken for confirmation.
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        host.onMembersChanged(listOf(other))
        assertEquals(FolderPhase.Injected(folderId, app), host.phase)
    }

    @Test
    fun `injectCommitted does nothing when no inject is in flight`() {
        val host = host()
        host.open(folderId)
        host.injectCommitted()
        assertEquals(FolderPhase.Open(folderId), host.phase)
    }

    @Test
    fun `onMembersChanged does nothing when no inject is settling`() {
        val host = host()
        host.open(folderId)
        host.onMembersChanged(listOf(app))
        assertEquals(FolderPhase.Open(folderId), host.phase)
    }

    @Test
    fun `beginInject after another folder is open retargets to the new folder`() {
        // The phase carries its own folder id, so an incoming app can never be left attached to a folder that is
        // no longer the open one.
        val host = host()
        host.open(folderId)
        host.beginInject(otherFolderId, other)
        assertEquals(FolderPhase.Injecting(otherFolderId, other), host.phase)
    }

    // ── The end of the drag ──

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
    fun `a committed inject survives the end of the drag, still carrying the app`() {
        // The drop that commits the inject also ends the drag, so this fires immediately afterwards — it must not
        // undo the commit or drop the app that is still mid-hand-off.
        val host = host()
        host.beginInject(folderId, app)
        host.injectCommitted()
        host.onDragEnd()
        assertEquals(FolderPhase.Injected(folderId, app), host.phase)
        assertEquals(folderId, host.openFolderId)
    }

    @Test
    fun `a folder opened by tapping is unaffected by an unrelated drag ending`() {
        val host = host()
        host.open(folderId)
        host.onDragEnd()
        assertEquals(folderId, host.openFolderId)
    }
}
