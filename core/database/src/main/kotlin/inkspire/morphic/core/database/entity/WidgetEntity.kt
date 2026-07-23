package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a bound app widget, keyed by its host [appWidgetId]. */
@Entity(tableName = "widget")
data class WidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val label: String,
)
