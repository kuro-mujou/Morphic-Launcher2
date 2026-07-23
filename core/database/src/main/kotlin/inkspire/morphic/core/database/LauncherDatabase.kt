package inkspire.morphic.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import inkspire.morphic.core.database.converter.ComponentKeyConverter
import inkspire.morphic.core.database.converter.HomeZoneConverter
import inkspire.morphic.core.database.converter.IconArrangementConverter
import inkspire.morphic.core.database.converter.OrientationConverter
import inkspire.morphic.core.database.converter.WidgetContainerAxisConverter
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.database.dao.AppPlacementDao
import inkspire.morphic.core.database.dao.AppsPagerItemDao
import inkspire.morphic.core.database.dao.CategoryDao
import inkspire.morphic.core.database.dao.CategoryItemDao
import inkspire.morphic.core.database.dao.FolderDao
import inkspire.morphic.core.database.dao.FolderItemDao
import inkspire.morphic.core.database.dao.FolderPlacementDao
import inkspire.morphic.core.database.dao.HomeListItemDao
import inkspire.morphic.core.database.dao.IconContainerDao
import inkspire.morphic.core.database.dao.IconContainerItemDao
import inkspire.morphic.core.database.dao.IconContainerPlacementDao
import inkspire.morphic.core.database.dao.IconOverrideDao
import inkspire.morphic.core.database.dao.WidgetContainerDao
import inkspire.morphic.core.database.dao.WidgetContainerItemDao
import inkspire.morphic.core.database.dao.WidgetContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetDao
import inkspire.morphic.core.database.dao.WidgetPlacementDao
import inkspire.morphic.core.database.entity.AppInfoEntity
import inkspire.morphic.core.database.entity.AppPlacementEntity
import inkspire.morphic.core.database.entity.AppsPagerItemEntity
import inkspire.morphic.core.database.entity.CategoryEntity
import inkspire.morphic.core.database.entity.CategoryItemEntity
import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.database.entity.FolderPlacementEntity
import inkspire.morphic.core.database.entity.HomeListItemEntity
import inkspire.morphic.core.database.entity.IconContainerEntity
import inkspire.morphic.core.database.entity.IconContainerItemEntity
import inkspire.morphic.core.database.entity.IconContainerPlacementEntity
import inkspire.morphic.core.database.entity.IconOverrideEntity
import inkspire.morphic.core.database.entity.WidgetContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerItemEntity
import inkspire.morphic.core.database.entity.WidgetContainerPlacementEntity
import inkspire.morphic.core.database.entity.WidgetEntity
import inkspire.morphic.core.database.entity.WidgetPlacementEntity

/**
 * The launcher's Room database: cached app metadata, home placements (per orientation), folders, widgets,
 * containers, per-app icon overrides, and the "order" arrangements (APPS pager, categories, home list).
 * Layout/grid/icon *config* for a surface lives in `data:settings`, not here.
 */
@Database(
    entities = [
        AppInfoEntity::class,
        AppPlacementEntity::class,
        FolderEntity::class,
        FolderItemEntity::class,
        FolderPlacementEntity::class,
        WidgetEntity::class,
        WidgetPlacementEntity::class,
        WidgetContainerEntity::class,
        WidgetContainerItemEntity::class,
        WidgetContainerPlacementEntity::class,
        IconContainerEntity::class,
        IconContainerItemEntity::class,
        IconContainerPlacementEntity::class,
        IconOverrideEntity::class,
        AppsPagerItemEntity::class,
        CategoryEntity::class,
        CategoryItemEntity::class,
        HomeListItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    ComponentKeyConverter::class,
    OrientationConverter::class,
    HomeZoneConverter::class,
    IconArrangementConverter::class,
    WidgetContainerAxisConverter::class
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appInfoDao(): AppInfoDao
    abstract fun appPlacementDao(): AppPlacementDao
    abstract fun folderDao(): FolderDao
    abstract fun folderItemDao(): FolderItemDao
    abstract fun folderPlacementDao(): FolderPlacementDao
    abstract fun widgetDao(): WidgetDao
    abstract fun widgetPlacementDao(): WidgetPlacementDao
    abstract fun widgetContainerDao(): WidgetContainerDao
    abstract fun widgetContainerItemDao(): WidgetContainerItemDao
    abstract fun widgetContainerPlacementDao(): WidgetContainerPlacementDao
    abstract fun iconContainerDao(): IconContainerDao
    abstract fun iconContainerItemDao(): IconContainerItemDao
    abstract fun iconContainerPlacementDao(): IconContainerPlacementDao
    abstract fun iconOverrideDao(): IconOverrideDao
    abstract fun appsPagerItemDao(): AppsPagerItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryItemDao(): CategoryItemDao
    abstract fun homeListItemDao(): HomeListItemDao

    companion object {
        const val DATABASE_NAME = "morphic-launcher.db"
    }
}
