package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a bound app widget, keyed by its host [appWidgetId]. */
@Entity(tableName = "app_widget")
data class AppWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val label: String,
)
