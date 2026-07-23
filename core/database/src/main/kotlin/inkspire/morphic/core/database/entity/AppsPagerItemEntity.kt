package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Orientation

/**
 * One app's slot in the APPS pager for a given [orientation]. The pager keeps two independent lists (portrait
 * and landscape); [page] is an explicit hard boundary and [positionInPage] is the dense top-to-bottom order
 * within that page (trailing empty slots = a gap at the page's end).
 */
@Entity(
    tableName = "apps_pager_item",
    primaryKeys = ["component", "orientation"],
    indices = [Index(value = ["orientation", "page"])],
)
data class AppsPagerItemEntity(
    val component: ComponentKey,
    val orientation: Orientation,
    val page: Int,
    val positionInPage: Int,
)
