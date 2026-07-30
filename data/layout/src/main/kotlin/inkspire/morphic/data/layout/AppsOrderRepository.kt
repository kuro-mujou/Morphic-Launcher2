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
 * It serves both APPS order stores: the pager (`apps_pager_item`) and the categories (`category` +
 * `category_item`, shared by the two category layouts). One repository rather than two because they are one
 * surface's arrangement; method names are prefixed by store for that reason.
 *
 * **Only the pager is per-orientation.** Its methods take an [Orientation] because it keeps two saved lists; the
 * category methods take none, because a category order is a single orientation-independent list (see the
 * arrangement model). That asymmetry is in the signatures on purpose — it is the difference between the stores, not
 * an omission.
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

    /**
     * Every category and the apps filed under it, categories in their stored order.
     *
     * Includes categories holding **nothing** — a row survives its last app leaving, so an emptied page stays on
     * screen and can be dragged back into.
     */
    fun categoryContents(): Flow<List<CategoryContents>>

    /**
     * Reconciles the stored arrangement with what is installed: appends apps that are new, drops ones that are
     * gone, seeds everything on first run, and creates a category row for any id that needs one.
     *
     * **An app already filed keeps its category, whatever [assignments] says.** The classifier runs every launch,
     * so treating its answer as authoritative would undo the user's drags each time — an assignment is a *first*
     * answer, for apps that have none yet.
     *
     * @param assignments every installed app and the category it would be filed under (from `AppCategorizer`).
     *   Iteration order decides the order new apps are appended in, so pass a map built in display order.
     */
    suspend fun syncCategories(assignments: Map<ComponentKey, String>)

    /** Applies [changes] in order, as one read-modify-write over the whole arrangement. */
    suspend fun applyCategory(changes: List<AppsCategoryChange>)
}
