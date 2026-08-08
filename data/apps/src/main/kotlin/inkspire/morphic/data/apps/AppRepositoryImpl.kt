package inkspire.morphic.data.apps

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.data.apps.mapper.toAppInfo
import inkspire.morphic.data.apps.mapper.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Default [AppRepository]: the Room cache ([AppInfoDao]) is the source of truth for reads, mirrored from
 * [LauncherAppsWrapper] by [refresh]. `internal` so only Koin (see `di/AppsModule`) constructs it — consumers
 * depend on the [AppRepository] interface.
 *
 * **It keeps itself in step with the platform**, which is the half that was deferred with `AppEvent` and is what
 * makes an uninstall visible: every surface in the launcher resolves its items through this cache — home's
 * placements and its list through `HomeState.appInfo`, the APPS order stores through their sync, the derived APPS
 * layouts directly — so all of them are correct exactly when this is, and none of them needs to know a package
 * event happened. The APPS pager and category stores prune themselves against the installed set they are handed,
 * so they follow from here too.
 *
 * **On [scope], deliberately, and this is the exception the ViewModel rule allows for.** Screens run their work on
 * `viewModelScope` so it cancels with them; a cache that must mirror the device cannot, or the launcher would show
 * stale icons whenever the screen watching it happened to be gone. That is precisely what `ApplicationScope`
 * exists for — "work that must outlive any single screen".
 */
internal class AppRepositoryImpl(
    private val launcherApps: LauncherAppsWrapper,
    private val appInfoDao: AppInfoDao,
    private val dispatchers: AppDispatchers,
    scope: CoroutineScope,
) : AppRepository {

    init {
        // Started here rather than by a caller because there is exactly one right answer to "when should the cache
        // be re-read?", and it is this type's. Handing the flow out instead would have both ViewModels collecting
        // it and each re-deciding the rule — which is how L1 ended up refreshing from several places on different
        // triggers. The collector lives as long as the process; the *listener* under it is registered only while
        // this flow is collected, so it is not a leak.
        //
        // The payload is ignored on purpose — "what is installed now?" is answered by re-reading, and that is the
        // whole point of the cache being a mirror. `conflate` is applied here rather than on the flow because it
        // is *this* collector that re-reads everything: a burst of events (a restore installing dozens of apps)
        // needs one re-read at the end, not one per package, while `BakedIconInvalidator` needs every event and
        // the names in it. Each consumer states its own need.
        scope.launch { launcherApps.packageChanges().conflate().collect { refresh() } }
    }

    override fun observeApps(): Flow<List<AppInfo>> =
        appInfoDao.observeAll().map { entities -> entities.map { it.toAppInfo() } }

    override suspend fun refresh() {
        // LauncherApps queries are blocking binder calls → do the query + mapping off the main thread.
        val entities = withContext(dispatchers.io) {
            launcherApps.queryActivities().map { activity ->
                activity.toAppInfo(userSerial = launcherApps.serialForUser(activity.user)).toEntity()
            }
        }
        // **An empty answer is a failed read, not an empty device.** Every Android device has at least this
        // launcher installed, so nothing back means the query did not really run — a profile mid-unlock, a binder
        // hiccup, external storage remounting. Replacing with it would empty the cache, and because every surface
        // resolves through this cache that means a blank home screen and a blank drawer. Upserting could never do
        // this damage, which is the cost of the mirror being authoritative.
        if (entities.isEmpty()) {
            Timber.w("LauncherApps reported no apps at all; keeping the cached set rather than emptying it")
            return
        }
        // **Replace, not upsert** — see [AppRepository.refresh]. One transaction, so no reader ever sees the
        // moment between the old contents and the new (`AppInfoDao.replaceAll`).
        appInfoDao.replaceAll(entities)
    }
}
