package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconItem

/**
 * The write-command vocabulary for the **APPS pager** arrangement — one value per intended change to where its
 * entries sit and which folder holds what.
 *
 * **Why not [LayoutChange].** That vocabulary is coordinate-shaped: its `Move` carries a `GridPlacement` + a
 * `HomeZone`, and its `CreateFolder` needs a cell to put the folder in. An ordered surface has neither — an entry
 * has a page and a slot, and nothing else. Forcing both surfaces through one command set would mean a `Move` whose
 * placement fields are meaningless for half its callers, which is exactly the kind of conflation L1's 19 ops were
 * refactored out of.
 *
 * **Orientation- and capacity-free by design.** A change says *what* to do; the caller scopes *which* list and
 * *how big a page is* via [AppsOrderRepository.applyPager], mirroring how [LayoutChange] leaves orientation to
 * [LayoutRepository.apply]. That is what lets the same command replay into either saved list.
 *
 * **Two ops name a neighbour instead of a slot**, and deliberately: [CreateFolder] and [DissolveFolder] both mean
 * "this thing takes that thing's place". Expressing them as a slot number would make the caller compute an index
 * that shifts underneath it as the folded apps leave the list — a bug that is invisible until two apps happen to
 * share a page. Naming the entry instead is immune to it.
 */
sealed interface AppsPagerChange {

    /**
     * Move [item] to [toSlot] of [toPage]. Reorder within a page and a move across pages are the same op — the
     * only difference is whether the page changes.
     *
     * Pages are hard boundaries: this compacts **only** the source page (nothing is pulled back from later pages
     * to fill the hole), and if the destination page overflows, the surplus cascades to the front of the next
     * one. [toPage] past the end appends a new page, which is how an app is carried onto a fresh one.
     */
    data class Move(val item: IconItem, val toPage: Int, val toSlot: Int) : AppsPagerChange

    /**
     * Fold [dragged] onto [target] into a new folder labelled [label] — the merge-ring drop.
     *
     * The folder holds `[target, dragged]` in that order (the app that was already there first, as home's
     * `mergeChanges` does) and **inherits [target]'s slot**; both apps leave the pager, since an app lives in one
     * place. Mints the folder id, so the caller cannot pre-compute one.
     */
    data class CreateFolder(val label: String, val target: ComponentKey, val dragged: ComponentKey) : AppsPagerChange

    /** Drop [app] onto folder [folderId]: it joins the folder's contents at the end and leaves the pager. */
    data class AddToFolder(val folderId: Long, val app: ComponentKey) : AppsPagerChange

    /**
     * Take [app] out of folder [folderId]'s membership. **Where it goes is a separate op** — pair this with a
     * [Move] to land it on the pager, or with an [AddToFolder] to send it straight into another folder.
     *
     * Deliberately not "remove and place at page/slot": that shape can only express one of the two landings, and
     * a folder→folder drag would have had to invent a placement it then immediately undoes. Two small ops compose
     * into both, and a batch is applied against one view of the pages, so they still commit together.
     */
    data class RemoveFromFolder(val folderId: Long, val app: ComponentKey) : AppsPagerChange

    /** Set folder [folderId]'s contents to exactly [apps], in that order (the in-folder reorder). */
    data class ReorderFolder(val folderId: Long, val apps: List<ComponentKey>) : AppsPagerChange

    /**
     * Delete folder [folderId], with [lastApp] taking over its slot on the pager (null when the folder is empty).
     *
     * This is the auto-dissolve a folder reaches when its second-last app leaves: a folder of one is not a folder.
     * One op rather than a delete plus a placement, because the two must agree about *where* — the app inherits
     * the folder's slot, and asking the caller for that slot would hand it an index that the deletion invalidates.
     */
    data class DissolveFolder(val folderId: Long, val lastApp: ComponentKey?) : AppsPagerChange
}
