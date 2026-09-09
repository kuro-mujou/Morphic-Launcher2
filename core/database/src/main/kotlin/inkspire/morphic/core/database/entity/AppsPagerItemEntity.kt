package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey

/**
 * One entry's slot in the APPS pager for a given [arrangement]: **exactly one** of an app [component] or a
 * [folderId], at [page] + [positionInPage].
 *
 * The pager keeps one independent list per [ArrangementKey]. [page] is an explicit hard boundary and
 * [positionInPage] is the dense top-to-bottom order within that page, so trailing empty slots are a gap at that
 * page's end and nowhere else.
 *
 * **Why app-or-folder rather than a `component` key.** The APPS pager hosts folders, and this row *is* a folder's
 * position — there is no `apps_pager_placement` table and there should not be one, because an ordered surface
 * stores a slot, not a coordinate. That makes this the same "exactly one of" shape as [IconContainerItemEntity],
 * which is no coincidence: [inkspire.morphic.core.model.IconItem] is the model's shared alphabet for the two
 * holders of exactly {app, folder}, and those two holders are this table and an icon container.
 *
 * **One difference from [IconContainerItemEntity], and it matters:** the unique indices here are scoped **per
 * arrangement**. An app appears once in *each* saved list, not once overall, so a globally-unique index would
 * make every list after the first unwritable. (SQLite ignores NULLs in unique indices, which is what lets the two
 * nullable columns coexist.)
 *
 * Deleting a folder cascades this row away, so a dissolved folder leaves no orphan slot behind.
 */
@Entity(
    tableName = "apps_pager_item",
    indices = [
        Index(value = ["arrangement", "page"]),
        Index(value = ["arrangement", "component"], unique = true),
        Index(value = ["arrangement", "folderId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppsPagerItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val arrangement: ArrangementKey,
    val component: ComponentKey? = null,
    val folderId: Long? = null,
    val page: Int,
    val positionInPage: Int,
) {
    init {
        require((component == null) != (folderId == null)) {
            "apps_pager_item needs exactly one of component/folderId"
        }
    }
}
