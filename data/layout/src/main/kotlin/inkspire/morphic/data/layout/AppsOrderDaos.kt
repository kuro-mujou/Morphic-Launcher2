package inkspire.morphic.data.layout

import inkspire.morphic.core.database.dao.AppsPagerItemDao
import inkspire.morphic.core.database.dao.CategoryDao
import inkspire.morphic.core.database.dao.CategoryItemDao
import inkspire.morphic.core.database.dao.FolderDao
import inkspire.morphic.core.database.dao.FolderItemDao

/**
 * The Room DAOs [AppsOrderRepositoryImpl] reads and writes, bundled so the repository takes one dependency —
 * the same shape as [LayoutDaos], for the same reason.
 *
 * It holds the **folder** DAOs as well as the arrangement tables', which is the whole point of the bundle: a merge
 * on the pager writes a folder, its membership and the pager slot together, and a repository that could only reach
 * the pager table would have to hand half of that back to its caller.
 */
class AppsOrderDaos(
    val pagerItem: AppsPagerItemDao,
    val folder: FolderDao,
    val folderItem: FolderItemDao,
    val category: CategoryDao,
    val categoryItem: CategoryItemDao,
)
