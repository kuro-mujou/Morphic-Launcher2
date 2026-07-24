package inkspire.morphic.data.apps

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.data.apps.mapper.toAppInfo
import inkspire.morphic.data.apps.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Default [AppRepository]: the Room cache ([AppInfoDao]) is the source of truth for reads, populated from
 * [LauncherAppsWrapper] on [refresh]. `internal` so only Koin (see `di/AppsModule`) constructs it — consumers
 * depend on the [AppRepository] interface.
 */
internal class AppRepositoryImpl(
    private val launcherApps: LauncherAppsWrapper,
    private val appInfoDao: AppInfoDao,
    private val dispatchers: AppDispatchers,
) : AppRepository {

    override fun observeApps(): Flow<List<AppInfo>> =
        appInfoDao.observeAll().map { entities -> entities.map { it.toAppInfo() } }

    override suspend fun refresh() {
        // LauncherApps queries are blocking binder calls → do the query + mapping off the main thread.
        val entities = withContext(dispatchers.io) {
            launcherApps.queryActivities().map { activity ->
                activity.toAppInfo(userSerial = launcherApps.serialForUser(activity.user)).toEntity()
            }
        }
        // Room's suspend upsert is main-safe (it dispatches to its own executor).
        appInfoDao.upsert(entities)
    }
}
