package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.CategoryEntity
import inkspire.morphic.core.database.entity.CategoryItemEntity
import inkspire.morphic.core.model.Category
import inkspire.morphic.core.model.CategoryGroup
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.toCategory

/** The definition this row holds. */
internal fun CategoryEntity.toCategory(): Category = Category(id = id, name = name, order = sortOrder)

/** These membership rows as `category id → apps in order`, which is the shape the ordering maths works on. */
internal fun List<CategoryItemEntity>.toCategoryItems(): Map<String, List<ComponentKey>> =
    groupBy { it.categoryId }
        .mapValues { (_, rows) -> rows.sortedBy { it.sortOrder }.map { it.component } }

/** [items] flattened back to rows, each app's index in its category becoming its `sortOrder`. */
internal fun rowsForCategoryItems(items: Map<String, List<ComponentKey>>): List<CategoryItemEntity> =
    items.flatMap { (categoryId, apps) ->
        apps.mapIndexed { index, app -> CategoryItemEntity(app, categoryId, index) }
    }

/**
 * A definition row for [id], for a category that is referenced but not yet defined.
 *
 * A **built-in** id (a [CategoryGroup] name) gets that group's display name and its declared order, which is what
 * makes the seeded pages read as "Communication", "Media", … in a sensible sequence rather than as raw enum names.
 * Anything else is a user-created category the management UI will have named, so its id stands in as the name and it
 * sorts after everything defined so far.
 */
internal fun categoryRowFor(id: String, fallbackOrder: Int): CategoryEntity =
    CategoryGroup.entries.firstOrNull { it.name == id }?.toCategory()
        ?.let { CategoryEntity(id = it.id, name = it.name, sortOrder = it.order) }
        ?: CategoryEntity(id = id, name = id, sortOrder = fallbackOrder)
