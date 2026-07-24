package inkspire.morphic.data.apps

import inkspire.morphic.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * Read/refresh access to the set of installed, launchable apps.
 *
 * Reads are **offline-first**: [observeApps] streams from the Room cache, so it survives process death and is
 * stable to collect; [refresh] re-queries `LauncherApps` and updates that cache, which in turn re-emits.
 * Raw icon drawables are not served here — those come straight from [LauncherAppsWrapper.loadIcon], since a
 * `Drawable` is a heavy platform object, not repository-shaped data.
 */
interface AppRepository {

    /** Streams the cached apps, re-emitting whenever the cache changes (e.g. after a [refresh]). */
    fun observeApps(): Flow<List<AppInfo>>

    /**
     * Re-queries `LauncherApps` across all profiles and upserts the result into the cache.
     *
     * This is an additive refresh — it does not yet prune apps that were uninstalled since the last run.
     * Live install/uninstall updates (and that pruning) arrive with the `AppEvent` listener in a later part.
     */
    suspend fun refresh()
}
