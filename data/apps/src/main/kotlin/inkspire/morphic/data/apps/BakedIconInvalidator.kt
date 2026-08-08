package inkspire.morphic.data.apps

import inkspire.morphic.core.icon.render.IconRenderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drops baked icons for packages the platform says have changed — **the other half of keeping an uninstall or an
 * update visible**, beside [AppRepository]'s cache.
 *
 * The two are separate because they go stale for different reasons and neither implies the other:
 * - the app *cache* goes stale when the set of installed apps changes, and a re-read fixes it;
 * - a *baked icon* goes stale when an app replaces its own artwork, which changes nothing the cache can see — same
 *   component, same label, same row. [IconRenderManager.invalidatePackages] is the only thing that can fix it.
 *
 * **Why this lives in `data:apps`.** `core:icon` bakes icons and knows nothing about packages arriving and leaving;
 * it must not learn, or a rendering module ends up depending on the platform's package events. This module is
 * already the boundary to `LauncherApps` — it supplies `core:icon` its `RawIconSource` — so "the raw icon behind
 * that component has changed" is exactly the fact it owns. It is deliberately **not** in the Activity either: L1
 * kept icon-cache invalidation inside a 204-line `setContent`, which is the thing `MainActivity`'s KDoc names as
 * the mistake it exists to avoid.
 *
 * **It is a subscription, not a service with an API**, which is why it has no methods: constructing it starts it
 * and it runs for the life of the process. Koin builds it eagerly (`createdAtStart`), since nothing injects it.
 *
 * @param scope application-lifetime, like [AppRepository]'s own collector: a stale icon does not stop being stale
 *   because the screen that would have shown it went away.
 */
internal class BakedIconInvalidator(
    launcherApps: LauncherAppsWrapper,
    iconRenderManager: IconRenderManager,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            // Every event, unconflated — see `LauncherAppsWrapper.packageChanges`. Collapsing two would lose the
            // first one's package names, and this is the consumer that cannot recover them by re-reading.
            launcherApps.packageChanges().collect { packageNames ->
                iconRenderManager.invalidatePackages(packageNames)
            }
        }
    }
}
