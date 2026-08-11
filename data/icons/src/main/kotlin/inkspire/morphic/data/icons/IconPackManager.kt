package inkspire.morphic.data.icons

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber

/**
 * One installed icon pack, as offered to the user.
 *
 * @property preview the pack's own launcher icon — packs are recognised by their artwork rather than their name,
 *   so a list of labels alone would be nearly unusable.
 */
data class InstalledIconPack(
    val packageName: String,
    val label: String,
    val preview: Bitmap?,
)

/**
 * Finds installed icon packs and resolves an app's icon from one.
 *
 * Ported from L1's manager, which is the one part of its icon work that landed cleanly. Icon packs have no
 * official API: a pack is an ordinary app that declares one of a handful of **de-facto theme intents**, and its
 * mapping lives in an `appfilter.xml` keyed by `ComponentInfo{package/class}` strings. Both conventions are
 * copied from L1 unchanged, because they are conventions rather than choices — a pack built for Nova is what it
 * is, and inventing our own reading of it would just mean supporting fewer packs.
 *
 * ## The one thing L1 got away with and we cannot
 *
 * `queryIntentActivities` is subject to **package visibility filtering** on API 30+: without a declaration, it
 * answers only about this app's own activities, so pack detection returns an empty list on every modern device.
 * L1 never hit it because it held `QUERY_ALL_PACKAGES`, which this launcher deliberately does not request. The
 * fix is the narrow `<queries>` block in this module's manifest, one `<intent>` per action below — the same
 * correction the wallpaper section's live-wallpaper shelf needed, and the same silent failure mode: not an error,
 * just an empty list.
 *
 * ## One pack loaded at a time
 *
 * An `appfilter.xml` is thousands of entries, so the parsed map is worth keeping — but only for the pack in use.
 * Guarded by a [Mutex] rather than `@Synchronized` (L1's choice) because the work happens in a coroutine, and a
 * blocking monitor inside one is how a dispatcher thread ends up parked.
 */
class IconPackManager(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {

    private val packageManager = context.packageManager
    private val loadLock = Mutex()
    private var loaded: LoadedPack? = null

    /** Browser thumbnails, by `pack/name`. Bounded — a browsable pack has thousands of drawables. */
    private val previews = LruCache<String, Bitmap>(PreviewCacheEntries)

    /** Every installed icon pack, by label. Empty when none is installed — or when `<queries>` is missing. */
    suspend fun installedPacks(): List<InstalledIconPack> = withContext(dispatchers.io) {
        ThemeActions
            .flatMap { action ->
                runCatching { packageManager.queryIntentActivities(Intent(action), 0) }
                    .getOrElse { emptyList() }
                    .map { it.activityInfo.packageName }
            }
            .distinct()
            .mapNotNull { pkg ->
                runCatching {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    InstalledIconPack(
                        packageName = pkg,
                        label = packageManager.getApplicationLabel(info).toString(),
                        preview = packageManager.getApplicationIcon(info).toBitmap(PreviewPx, PreviewPx),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * [component]'s artwork from [packPackage], or `null` when the pack does not cover that app.
     *
     * **Null is the ordinary case, not a failure**: no pack themes every app, and coverage of a few hundred is
     * typical. The caller draws nothing for that layer, which leaves the app's own icon showing through from
     * whatever is beneath — the same outcome L1 reached by falling back to the default icon.
     */
    suspend fun drawable(
        packPackage: String,
        component: ComponentKey,
        drawableName: String? = null,
    ): Drawable? = withContext(dispatchers.io) {
        val pack = load(packPackage) ?: return@withContext null
        // An explicit name wins outright: it is the user having browsed the pack and chosen, which is a stronger
        // statement than the pack author's own mapping and must not be second-guessed by it.
        val name = drawableName ?: pack.componentDrawable[component.appFilterKey()] ?: return@withContext null
        pack.resolve(name)
    }

    /**
     * Every drawable [packPackage] maps to some app, for browsing.
     *
     * **Taken from the `appfilter.xml` this pack has already parsed**, whose values *are* drawable names — so the
     * "drawable lister" a browser was thought to need turns out to be a projection of data the pack loads anyway.
     * What that misses is drawables the author shipped but mapped to nothing (generic shapes, alternates) and the
     * category grouping a `drawable.xml` carries; both are additive later and neither blocks browsing.
     *
     * Sorted by name because there is no other order available — a pack's mapping is a hash, and the names are
     * what search works on.
     */
    suspend fun drawableNames(packPackage: String): List<String> = withContext(dispatchers.io) {
        load(packPackage)?.componentDrawable?.values?.distinct()?.sorted().orEmpty()
    }

    /**
     * A small preview of one named drawable, for a browser cell.
     *
     * Cached, because a grid re-asks for the same cell every time it scrolls back and re-decoding a vector each
     * time is what makes such a grid stutter. The cache is bounded and holds thumbnails, so a few hundred of them
     * is a fraction of one baked icon's worth of memory.
     */
    suspend fun preview(packPackage: String, drawableName: String): Bitmap? = withContext(dispatchers.io) {
        val key = "$packPackage/$drawableName"
        previews.get(key)?.let { return@withContext it }

        val pack = load(packPackage) ?: return@withContext null
        val bitmap = runCatching { pack.resolve(drawableName)?.toBitmap(PreviewPx, PreviewPx) }.getOrNull()
        bitmap?.also { previews.put(key, it) }
    }

    private suspend fun load(packPackage: String): LoadedPack? = loadLock.withLock {
        loaded?.takeIf { it.packageName == packPackage }?.let { return it }

        val resources = runCatching { packageManager.getResourcesForApplication(packPackage) }
            .onFailure { Timber.w(it, "Icon pack %s could not be read", packPackage) }
            .getOrNull() ?: return null

        LoadedPack(packPackage, resources, parseAppFilter(packPackage, resources)).also { loaded = it }
    }

    /** The `component → drawable name` mapping, or an empty map when the pack has none we can read. */
    private fun parseAppFilter(packPackage: String, resources: Resources): Map<String, String> {
        val parser = openAppFilter(packPackage, resources) ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) map[component] = drawable
                }
                event = parser.next()
            }
        }.onFailure { Timber.w(it, "appfilter parse failed for %s", packPackage) }
        return map
    }

    /**
     * The pack's `appfilter`, as a resource if it has one and from its assets otherwise.
     *
     * Both, because packs disagree: some ship it compiled into `res/xml`, others as a raw asset. L1 tried the
     * same two in the same order.
     */
    private fun openAppFilter(packPackage: String, resources: Resources): XmlPullParser? {
        val xmlId = resources.getIdentifier("appfilter", "xml", packPackage)
        if (xmlId != 0) return runCatching { resources.getXml(xmlId) }.getOrNull()

        return runCatching {
            val assets = context.createPackageContext(packPackage, 0).assets
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(assets.open("appfilter.xml"), "UTF-8")
            }
        }.getOrNull()
    }

    private class LoadedPack(
        val packageName: String,
        val resources: Resources,
        val componentDrawable: Map<String, String>,
    ) {
        /** The pack's drawable of this name, or `null` when it does not have one under it. */
        fun resolve(drawableName: String): Drawable? {
            val id = resources.getIdentifier(drawableName, "drawable", packageName)
            if (id == 0) return null
            return runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
        }
    }

    private companion object {
        /**
         * The de-facto theme intents a pack declares. No official API exists, so this list *is* the definition of
         * "an icon pack" — L1's, unchanged, since a pack built for one of these launchers already declares it.
         * **Must stay in step with the `<queries>` block in this module's manifest**, or an action listed here is
         * one this app cannot see.
         */
        val ThemeActions = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.sonyericsson.home.ICON_PACK",
        )

        /** Big enough for a chooser tile, small enough that listing every installed pack costs nothing. */
        const val PreviewPx = 96

        /** Roughly a few screens of a browser grid at [PreviewPx] — enough that scrolling back is free. */
        const val PreviewCacheEntries = 300
    }
}

/** The key an `appfilter.xml` uses — a stringified `ComponentName`, which is the format packs are authored in. */
private fun ComponentKey.appFilterKey(): String = "ComponentInfo{$packageName/$className}"
