package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/** Room row caching an installed app's metadata (mirrors `AppInfo`), keyed by [component]. */
@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey val component: ComponentKey,
    val label: String,
    val isWorkProfile: Boolean,
    val isSuspended: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val category: Int,
    val isSystem: Boolean = false,
)
