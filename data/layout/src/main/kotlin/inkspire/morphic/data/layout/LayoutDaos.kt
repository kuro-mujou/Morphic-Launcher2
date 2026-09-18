package inkspire.morphic.data.layout

import inkspire.morphic.core.database.dao.AppPlacementDao
import inkspire.morphic.core.database.dao.AppWidgetDao
import inkspire.morphic.core.database.dao.AppWidgetPlacementDao
import inkspire.morphic.core.database.dao.FolderDao
import inkspire.morphic.core.database.dao.FolderItemDao
import inkspire.morphic.core.database.dao.FolderPlacementDao
import inkspire.morphic.core.database.dao.IconContainerDao
import inkspire.morphic.core.database.dao.IconContainerItemDao
import inkspire.morphic.core.database.dao.IconContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetContainerDao
import inkspire.morphic.core.database.dao.WidgetContainerItemDao
import inkspire.morphic.core.database.dao.WidgetContainerPlacementDao

/**
 * The Room DAOs [LayoutRepositoryImpl] reads and writes, bundled so the repository takes one dependency instead
 * of a twelve-parameter constructor. Grouped: the five coordinate `*_placement` tables, then the folder /
 * icon-container / widget-container / widget definition tables. Provided as one Koin singleton.
 */
class LayoutDaos(
    val appPlacement: AppPlacementDao,
    val folderPlacement: FolderPlacementDao,
    val widgetPlacement: AppWidgetPlacementDao,
    val iconContainerPlacement: IconContainerPlacementDao,
    val widgetContainerPlacement: WidgetContainerPlacementDao,
    val folder: FolderDao,
    val folderItem: FolderItemDao,
    val iconContainer: IconContainerDao,
    val iconContainerItem: IconContainerItemDao,
    val widgetContainer: WidgetContainerDao,
    val widgetContainerItem: WidgetContainerItemDao,
    val widget: AppWidgetDao,
)
