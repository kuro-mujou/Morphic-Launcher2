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
     * Re-queries `LauncherApps` across all profiles and **replaces** the cache with the result.
     *
     * A replace, not an upsert: the cache is a mirror of what is installed, so an app that has been uninstalled
     * has to leave it. That it did not used to is why uninstalling an app left its icon on every surface — each
     * one resolves its items *through* this cache, so a row that never disappears is an icon that never does.
     *
     * Callers still need this at start-up, for changes that happened while the process was dead; while it is
     * alive the repository keeps itself in step (see the implementation) and nobody has to poll.
     */
    suspend fun refresh()
}
