package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A category definition (built-in or user-created), keyed by [id]; [sortOrder] is its order among categories. */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)
