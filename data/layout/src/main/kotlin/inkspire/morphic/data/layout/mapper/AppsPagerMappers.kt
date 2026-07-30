package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.AppsPagerItemEntity
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Orientation

/**
 * The entry this row holds — the "exactly one of" column pair read back as the [IconItem] it stands for. Null
 * only if a row somehow violates the entity's own `require`, which the type system can't rule out at read time.
 */
internal fun AppsPagerItemEntity.toIconItem(): IconItem? {
    // Read into locals first: the columns are `val`s of another module, so Kotlin won't smart-cast them.
    val app = component
    val folder = folderId
    return when {
        app != null -> IconItem.App(app)
        folder != null -> IconItem.Folder(folder)
        else -> null
    }
}

/** These rows as pages: grouped by `page`, ordered by `positionInPage`, gaps closed. */
internal fun List<AppsPagerItemEntity>.toPages(): List<List<IconItem>> =
    sortedWith(compareBy({ it.page }, { it.positionInPage }))
        .groupBy { it.page }
        .toSortedMap()
        .values
        .map { rows -> rows.mapNotNull { it.toIconItem() } }

/**
 * [pages] as rows to persist for [orientation], reusing each entry's existing row id from [ids].
 *
 * **Reusing the id is what makes this an update rather than a duplicate.** `@Upsert` matches on the primary key,
 * and the key here is a surrogate `id` — a moved entry written with id 0 would insert a second row for the same
 * app, which the per-orientation unique indices then reject. So a re-slot must carry the id it was read with.
 */
internal fun rowsForPages(
    pages: List<List<IconItem>>,
    orientation: Orientation,
    ids: Map<IconItem, Long>,
): List<AppsPagerItemEntity> = pages.flatMapIndexed { page, entries ->
    entries.mapIndexed { slot, item ->
        AppsPagerItemEntity(
            id = ids[item] ?: 0L,
            orientation = orientation,
            component = (item as? IconItem.App)?.component,
            folderId = (item as? IconItem.Folder)?.folderId,
            page = page,
            positionInPage = slot,
        )
    }
}

/** Each entry's stored row id, for [rowsForPages] to write back. */
internal fun List<AppsPagerItemEntity>.idsByItem(): Map<IconItem, Long> =
    mapNotNull { row -> row.toIconItem()?.let { it to row.id } }.toMap()
