package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one of the launcher's own widgets: its recipe, serialized whole.
 *
 * **One blob, not columns** — a recipe is a tree, and the icon store already paid in destructive migrations for
 * spreading one across a table. Its placements are [WidgetPlacementEntity] rows, one per arrangement.
 *
 * @property recipe a `WidgetRecipe` as JSON; `data:layout` owns the encoding.
 */
@Entity(tableName = "widget")
data class WidgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipe: String,
)
