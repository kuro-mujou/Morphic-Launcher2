package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a folder — a named group of apps. */
@Entity(tableName = "folder")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
)
