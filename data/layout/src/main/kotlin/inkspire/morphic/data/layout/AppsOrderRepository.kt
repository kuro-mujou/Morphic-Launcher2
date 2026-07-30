package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the APPS surface's **order** stores — the arrangements the user makes on APPS, as opposed
 * to the coordinate placements [LayoutRepository] owns for HOME.
 *
 * **Why it is a separate repository.** The two stores answer different questions with different shapes: HOME asks
 * "which cell is this item in?" and APPS asks "which page and slot is it in?". Folding both into [LayoutRepository]
 * would rebuild the god-interface that repository was carved out of — its KDoc records L1's ~30-method original —
 * and would give every HOME caller methods that can only ever return nothing.
 *
 * Today it serves the pager (`AppsLayout.PAGER`); the category store (`category` + `category_item`, shared by the
 * two category layouts) joins it here rather than in a third repository, since it is the same surface's
 * arrangement. Method names are prefixed by store for that reason.
 *
 * **Folder writes live here too**, not only on [LayoutRepository]. A merge on the pager mints a folder, moves two
 * apps into it and re-slots the result; splitting that across two repositories would make one user action two
 * independent writes, with a window where the app is in neither place. Folder *definitions* are still read from
 * [LayoutRepository.folders] — one folder store, two surfaces that host from it.
 */
interface AppsOrderRepository {

    /**
     * The pager's pages for [orientation]: entries in reading order, each page dense from its first slot, re-fitted
     * to [perPage] on the way out (see `normalizePages`).
     *
     * [perPage] is a parameter rather than repository state because the capacity is the UI's to know — it comes
     * from `AppsPagerGrid` resolved against the detected device, exactly as home pushes its `GridConfig` down. A
     * changed capacity re-collects with the new value and re-fits the saved list.
     */
    fun pagerPages(orientation: Orientation, perPage: Int): Flow<List<List<IconItem>>>

    /**
     * Reconciles the stored arrangement with [installed] — appending apps that are new, dropping ones that are
     * gone, and seeding the whole list on first run (an empty store makes every app "new").
     *
     * @param installed every installed app in the order new ones should be appended in — the caller's display
     *   order, so the locale-aware A–Z is decided once in the ViewModel rather than re-derived down here.
     */
    suspend fun syncPager(orientation: Orientation, perPage: Int, installed: List<ComponentKey>)

    /**
     * Applies [changes] in order to [orientation]'s list, at page capacity [perPage].
     *
     * The whole batch is one read-modify-write, so a drop that spans stores (a merge is a folder insert, two
     * membership rows and a re-slot) resolves against one consistent view of the pages.
     */
    suspend fun applyPager(orientation: Orientation, perPage: Int, changes: List<AppsPagerChange>)
}
