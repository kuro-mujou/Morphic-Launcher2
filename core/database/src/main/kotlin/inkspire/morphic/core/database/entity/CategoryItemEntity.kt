package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ComponentKey

/**
 * An app's category membership and order within it — one row per app ([component] is the key, so an app
 * belongs to exactly one category). Shared by the APPS pager-with-category and category-card layouts.
 */
@Entity(
    tableName = "category_item",
    primaryKeys = ["component"],
    indices = [Index(value = ["categoryId"])],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryItemEntity(
    val component: ComponentKey,
    val categoryId: String,
    val sortOrder: Int,
)
