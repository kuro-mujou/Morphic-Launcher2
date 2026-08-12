package inkspire.morphic.data.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Collator

/**
 * Every app widget installed on the device, grouped under the app that publishes it — the picker's whole input.
 *
 * **A live read, deliberately not a cache.** The app *list* is mirrored into Room because every surface resolves
 * items through it and it has to survive process death; a widget catalogue is read once when a sheet opens and
 * discarded when it closes, so caching it would mean keeping preview bitmaps for widgets nobody is looking at and
 * inventing an invalidation rule for a question that is cheap to re-ask. Same reasoning as `AppShortcuts` in
 * `data:apps`, and the same shape: a suspending read with no flow behind it.
 *
 * **This module is only the catalogue today.** Allocating an `appWidgetId`, binding it to a provider and hosting
 * the resulting view are an `AppWidgetHost`'s job and arrive with the slice that can actually draw a widget; this
 * one needs no host at all, which is why there isn't one yet. `AppWidgetManager` answers "what could be added?" on
 * its own.
 */
interface WidgetCatalog {

    /**
     * The installed widgets, grouped by app and sorted for display — apps by name, and each app's widgets by name
     * within it.
     *
     * Sorted with a locale-aware [Collator] rather than by `lowercase()`, which is the same correction the APPS
     * ordering and the live-wallpaper shelf make: raw UTF-16 comparison puts every accented label after `Z`, so a
     * Vietnamese or French device gets a list that breaks into two alphabets.
     *
     * Blocking work (a `PackageManager` lookup and a rasterised preview per widget) is moved off the caller's
     * thread here, since there is exactly one caller shape — a sheet about to be shown.
     */
    suspend fun installed(): List<WidgetProviderGroup>
}

/**
 * Default [WidgetCatalog], over [AppWidgetManager].
 *
 * **It does retain a [Context], unlike `DefaultLauncherAppsWrapper`, and it has to**: loading another app's
 * preview drawable is a resource inflation and takes one. The *application* context, so this is safe to hold as an
 * application-scoped singleton — the rule that class states is about never holding a view or activity context, and
 * it already keeps `Resources` from the same place for the same reason.
 *
 * `internal` so only Koin constructs it — consumers depend on the [WidgetCatalog] interface.
 */
internal class DefaultWidgetCatalog(
    context: Context,
    private val dispatchers: AppDispatchers,
) : WidgetCatalog {

    private val appContext: Context = context.applicationContext
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val packageManager: PackageManager = appContext.packageManager

    /**
     * The density previews are rasterised at, read per call rather than frozen at construction — the same reason
     * the launcher-apps wrapper keeps `Resources` for its shortcut icons.
     */
    private val resources = appContext.resources

    override suspend fun installed(): List<WidgetProviderGroup> = withContext(dispatchers.io) {
        val densityDpi = resources.displayMetrics.densityDpi
        val collator = Collator.getInstance()

        appWidgetManager.installedProviders
            .groupBy { it.provider.packageName }
            .mapNotNull { (packageName, infos) -> group(packageName, infos, densityDpi, collator) }
            .sortedWith { a, b -> collator.compare(a.appLabel, b.appLabel) }
    }

    /**
     * One app's entry, or null when it has nothing showable.
     *
     * **A blank app label drops the whole group**, which is L1's rule: the row is the app's name, so a group with
     * no name is a row the user cannot read or search for. The application's own label is preferred over the
     * widget's because two widgets from one app would otherwise name the group differently depending on which was
     * listed first.
     */
    private fun group(
        packageName: String,
        infos: List<AppWidgetProviderInfo>,
        densityDpi: Int,
        collator: Collator,
    ): WidgetProviderGroup? {
        val appLabel = runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrNull() ?: infos.firstOrNull()?.loadLabel(packageManager).orEmpty()
        if (appLabel.isBlank()) return null

        val providers = infos
            .map { info ->
                WidgetProvider(
                    component = info.provider,
                    label = info.loadLabel(packageManager).orEmpty(),
                    preview = info.preview(densityDpi),
                    minWidthPx = info.minWidth,
                    minHeightPx = info.minHeight,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }

        return WidgetProviderGroup(packageName, appLabel, providers)
    }

    /**
     * The provider's published preview, falling back to its icon — L1's pair, and the fallback matters: plenty of
     * apps ship an icon and no preview, and a blank tile in the picker is indistinguishable from a broken one.
     *
     * Sized from the drawable's intrinsic dimensions where it has them and from [PreviewFallbackPx] where it does
     * not (a plain color has none), because `toBitmap()` throws rather than guessing. Every step is guarded: this
     * is another app's drawable being inflated in our process, and one bad provider must not empty the picker.
     */
    private fun AppWidgetProviderInfo.preview(densityDpi: Int): Bitmap? = runCatching {
        val drawable = loadPreviewImage(appContext, densityDpi)
            ?: loadIcon(appContext, densityDpi)
            ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: PreviewFallbackPx
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: PreviewFallbackPx
        drawable.toBitmap(width = width, height = height)
    }.onFailure { Timber.w(it, "No preview for widget %s", provider.flattenToShortString()) }.getOrNull()

    private companion object {
        /** What a drawable with no intrinsic size is rasterised at. L1's number. */
        const val PreviewFallbackPx = 256
    }
}
