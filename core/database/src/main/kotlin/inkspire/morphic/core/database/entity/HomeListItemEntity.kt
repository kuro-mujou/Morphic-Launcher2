package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/**
 * One app in the home vertical-list layout (apps only, no folders). [sortOrder] is the dense top-to-bottom
 * order. One list shared by every arrangement — only rendering reflows on rotate, not the order.
 */
@Entity(tableName = "home_list_item")
data class HomeListItemEntity(
    @PrimaryKey val component: ComponentKey,
    val sortOrder: Int,
)
