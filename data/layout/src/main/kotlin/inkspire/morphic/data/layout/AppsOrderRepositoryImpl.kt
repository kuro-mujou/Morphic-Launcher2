package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.model.CategoryGroup
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.layout.mapper.categoryRowFor
import inkspire.morphic.data.layout.mapper.idsByItem
import inkspire.morphic.data.layout.mapper.rowsForPages
import inkspire.morphic.data.layout.mapper.rowsForCategoryItems
import inkspire.morphic.data.layout.mapper.toCategory
import inkspire.morphic.data.layout.mapper.toCategoryItems
import inkspire.morphic.data.layout.mapper.toPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [AppsOrderRepository]. All the arrangement thinking is in `AppsPagerPaging.kt` (pure list maths,
 * unit-tested); this class is the part that cannot be pure — reading rows, minting folder ids, writing back.
 *
 * **Every write is one read-modify-write.** Load the rows once, fold the changes over the in-memory pages, persist
 * the result. Two things fall out of that: a batch resolves against a single consistent view (a merge is a folder
 * insert, two membership rows and a re-slot, all seeing the same pages), and slot arithmetic never runs against a
 * half-applied list.
 *
 * **Not wrapped in a Room transaction**, matching [LayoutRepositoryImpl], which is the honest statement of where
 * this codebase is rather than an argument that it is right: both repositories should take the database and use
 * `withTransaction`, and doing it in one and not the other would be worse than doing it in neither. Worth fixing
 * for both at once.
 *
 * Writes hop to [AppDispatchers.io]; the DAO's `Flow` stays on Room's own executor.
 */
internal class AppsOrderRepositoryImpl(
    private val daos: AppsOrderDaos,
    private val dispatchers: AppDispatchers,
) : AppsOrderRepository {

    override fun pagerPages(orientation: Orientation, perPage: Int): Flow<List<List<IconItem>>> =
        daos.pagerItem.observe(orientation).map { rows -> normalizePages(rows.toPages(), perPage) }

    override suspend fun syncPager(orientation: Orientation, perPage: Int, installed: List<ComponentKey>) {
        withContext(dispatchers.io) {
            val rows = daos.pagerItem.get(orientation)
            val current = normalizePages(rows.toPages(), perPage)
            val synced = syncPagerPages(current, installed, perPage)
            // Nothing to do on the overwhelmingly common launch where nothing installed or vanished — and skipping
            // the write matters, since persisting would re-emit the flow and re-render the whole surface.
            if (synced != current) persist(orientation, synced, rows)
        }
    }

    override suspend fun applyPager(orientation: Orientation, perPage: Int, changes: List<AppsPagerChange>) {
        if (changes.isEmpty()) return
        withContext(dispatchers.io) {
            val rows = daos.pagerItem.get(orientation)
            var pages = normalizePages(rows.toPages(), perPage)
            changes.forEach { pages = applyChange(it, pages, perPage) }
            persist(orientation, normalizePages(pages, perPage), rows)
        }
    }

    /**
     * Folds one change into [pages], performing its folder-store side effects as it goes.
     *
     * The folder writes have to happen here rather than after the fold, because [AppsPagerChange.CreateFolder]
     * mints an id that the very next line needs in order to put the folder in the list.
     */
    private suspend fun applyChange(
        change: AppsPagerChange,
        pages: List<List<IconItem>>,
        perPage: Int,
    ): List<List<IconItem>> = when (change) {
        is AppsPagerChange.Move ->
            movePagerItem(pages, change.item, change.toPage, change.toSlot, perPage)

        is AppsPagerChange.CreateFolder -> {
            val folderId = daos.folder.insert(FolderEntity(label = change.label))
            daos.folderItem.upsert(
                listOf(
                    FolderItemEntity(folderId, change.target, 0),
                    FolderItemEntity(folderId, change.dragged, 1),
                ),
            )
            // The folder takes the target's place, then the dragged app leaves — in that order, so the slot is
            // resolved before any removal can shift it.
            val withFolder = replacePagerItem(pages, IconItem.App(change.target), IconItem.Folder(folderId))
            removePagerItem(withFolder, IconItem.App(change.dragged))
        }

        is AppsPagerChange.AddToFolder -> {
            // Membership only, mirroring RemoveFromFolder: the caller pairs it with RemoveFromPager when the app
            // was loose on a page. Bundling the removal here is what hid the case where it was needed and absent.
            val next = (daos.folderItem.maxSortOrder(change.folderId) ?: -1) + 1
            daos.folderItem.upsert(listOf(FolderItemEntity(change.folderId, change.app, next)))
            pages
        }

        is AppsPagerChange.RemoveFromPager -> removePagerItem(pages, change.item)

        is AppsPagerChange.RemoveFromFolder -> {
            // Membership only: the app is now in neither place, and the op paired with this one — a Move onto the
            // pager, or an AddToFolder into another folder — decides where it lands. Both are in the same batch,
            // so the gap never reaches the store.
            daos.folderItem.remove(change.folderId, change.app)
            pages
        }

        is AppsPagerChange.ReorderFolder -> {
            // Replaces membership wholesale, exactly as `LayoutChange.ReorderFolder` does — and with the same
            // warning attached: the caller must reconcile a UI-reported order against real membership first, or
            // members it could not render are deleted rather than reordered.
            daos.folderItem.clearFolder(change.folderId)
            daos.folderItem.upsert(change.apps.mapIndexed { i, app -> FolderItemEntity(change.folderId, app, i) })
            pages
        }

        is AppsPagerChange.DissolveFolder -> {
            val folder = IconItem.Folder(change.folderId)
            // Put the survivor in the folder's slot *before* deleting, so it inherits the position rather than
            // being appended to the end of the list.
            val next = change.lastApp
                ?.let { replacePagerItem(pages, folder, IconItem.App(it)) }
                ?: removePagerItem(pages, folder)
            // Cascades folder_item and the pager row (the entity's foreign key), so nothing else to clean up.
            daos.folder.delete(change.folderId)
            next
        }
    }

    /**
     * Writes [pages] as this orientation's rows: entries that left are deleted, the rest are upserted carrying the
     * row id they were read with (see `rowsForPages` — an id of 0 would insert a duplicate).
     */
    private suspend fun persist(
        orientation: Orientation,
        pages: List<List<IconItem>>,
        existing: List<inkspire.morphic.core.database.entity.AppsPagerItemEntity>,
    ) {
        val ids = existing.idsByItem()
        val kept = pages.flatItems().toSet()
        (ids.keys - kept).forEach { gone ->
            when (gone) {
                is IconItem.App -> daos.pagerItem.deleteApp(orientation, gone.component)
                is IconItem.Folder -> daos.pagerItem.deleteFolder(orientation, gone.folderId)
            }
        }
        daos.pagerItem.upsert(rowsForPages(pages, orientation, ids))
    }

    // ── Categories ─────────────────────────────────────────────────────────────────────────────────────────────
    //
    // Simpler than the pager throughout, and all of it follows from one difference: a category is a single dense
    // list with no capacity. Nothing cascades, nothing compacts, and there is no orientation — a category order is
    // one list, not two.

    override fun categoryContents(): Flow<List<CategoryContents>> =
        combine(daos.category.observeAll(), daos.categoryItem.observeAll()) { categories, items ->
            val byCategory = items.toCategoryItems()
            // Driven by the *definitions*, so a category holding nothing still appears — an emptied page has to
            // stay on screen to be dragged back into. An item whose category has no row would be invisible here,
            // which is why both writes below make sure the row exists first.
            categories.map { CategoryContents(it.toCategory(), byCategory[it.id].orEmpty()) }
        }

    override suspend fun syncCategories(assignments: Map<ComponentKey, String>) {
        withContext(dispatchers.io) {
            val rows = daos.categoryItem.getAll()
            val current = rows.toCategoryItems()
            // Anything filed under an id that is no longer a category is unfiled first, so the classifier can place
            // it again. Today that means the built-in groups only, since categories cannot yet be user-created —
            // when they can, `known` grows to include them and this keeps working unchanged.
            val known = CategoryGroup.entries.mapTo(mutableSetOf()) { it.name }
            val synced = syncCategoryItems(dropUnknownCategories(current, known), assignments)
            // Their definition rows go too, or the surface keeps showing an empty page for a category that is gone.
            daos.category.getAll().map { it.id }.filterNot { it in known }.forEach { daos.category.delete(it) }
            // Definitions first: an item row pointing at an undefined category would not render.
            ensureCategoryRows(synced.keys + assignments.values)
            // Skipping the write when nothing moved matters — persisting would re-emit the flow and re-render the
            // whole surface on every launch.
            if (synced != current) persistCategoryItems(synced, rows)
        }
    }

    override suspend fun applyCategory(changes: List<AppsCategoryChange>) {
        if (changes.isEmpty()) return
        withContext(dispatchers.io) {
            val rows = daos.categoryItem.getAll()
            var items = rows.toCategoryItems()
            changes.forEach { change ->
                items = when (change) {
                    is AppsCategoryChange.Move ->
                        moveCategoryItem(items, change.app, change.toCategory, change.toSlot)
                }
            }
            ensureCategoryRows(items.keys)
            persistCategoryItems(items, rows)
        }
    }

    /**
     * Creates a definition row for any of [referenced] that has none, so no app can end up filed under a category
     * the read cannot resolve — which would make it vanish from the UI while still sitting in the table.
     */
    private suspend fun ensureCategoryRows(referenced: Set<String>) {
        val defined = daos.category.getAll().map { it.id }.toSet()
        val missing = referenced - defined
        if (missing.isEmpty()) return
        daos.category.upsert(missing.mapIndexed { index, id -> categoryRowFor(id, defined.size + index) })
    }

    /** Writes [items] as membership rows; apps no longer filed anywhere are deleted. */
    private suspend fun persistCategoryItems(
        items: Map<String, List<ComponentKey>>,
        existing: List<inkspire.morphic.core.database.entity.CategoryItemEntity>,
    ) {
        val kept = items.values.flatMapTo(mutableSetOf()) { it }
        existing.map { it.component }.filterNot { it in kept }.forEach { daos.categoryItem.removeByComponent(it) }
        // The row is keyed on the component alone, so an upsert re-files in place — no surrogate id to carry, which
        // is the one thing the pager's writes have to be careful about.
        daos.categoryItem.upsert(rowsForCategoryItems(items))
    }
}
