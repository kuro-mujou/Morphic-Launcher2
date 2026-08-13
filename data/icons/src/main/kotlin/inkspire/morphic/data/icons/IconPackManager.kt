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
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.data.icons.internal.PackFallback
import inkspire.morphic.data.icons.internal.composePackFallback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber

/**
 * One installed icon pack, as offered to the user.
 *
 * @property preview the pack's own launcher icon — packs are recognized by their artwork rather than their name,
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
    private val rawIcons: RawIconSource,
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
     * [component]'s artwork from [packPackage] — its mapped drawable, or the pack's own treatment for an app it does
     * not theme. `null` only when the pack has neither.
     *
     * **A miss is the ordinary case, not a failure**: no pack themes every app, and coverage of a few hundred is
     * typical. What a pack does about that is [PackFallback] — its plate, its stencil, its overlay and how far to
     * shrink the app's own icon — which is part of `appfilter.xml`'s own spec rather than a launcher's invention, and
     * is what every other launcher applies. See `composePackFallback`.
     *
     * Null therefore means "this pack has nothing to say about this app at all", and the resolver answers it by
     * leaving the app's own artwork alone.
     */
    suspend fun drawable(
        packPackage: String,
        component: ComponentKey,
        drawableName: String? = null,
    ): Drawable? = withContext(dispatchers.io) {
        val pack = load(packPackage) ?: return@withContext null
        // An explicit name wins outright: it is the user having browsed the pack and chosen, which is a stronger
        // statement than the pack author's own mapping and must not be second-guessed by it.
        val name = drawableName ?: pack.componentDrawable[component.appFilterKey()]
        name?.let(pack::resolve) ?: pack.fallbackFor(component)
    }

    /**
     * Every drawable [packPackage] offers, for browsing — **its own catalog first, then whatever else it maps.**
     *
     * Two sources, because between them they are the whole pack and neither is enough alone:
     * - **`drawable.xml`** is the author's browsable list, and the only place a drawable **mapped to no app** appears —
     *   generic shapes, alternates, spares. A pack's own "browse icons" screen is built from this file, which is what
     *   this browser is. Kept **in the author's order**, since it is curated (grouped by category, alternates beside
     *   what they vary).
     * - **`appfilter.xml`'s values** are the icons assigned to some app. Mostly a subset of the catalog, but a pack that
     *   ships no `drawable.xml` at all has nothing else to offer, and one whose catalog is out of date with its mapping
     *   would otherwise hide the icons it actually uses. Appended **sorted**, since a mapping is a hash with no order to
     *   keep.
     *
     * This used to be the appfilter projection alone, on the reasoning that its values *are* drawable names so no
     * separate lister was needed. True as far as it went, and it went one file short: it could only ever show icons the
     * author had already spoken for.
     *
     * **Filtered to what the pack really ships.** An authored list can name a drawable that is not in the APK, and a
     * browser cell for one is a permanent blank the user can tap; `getIdentifier` is a hash lookup, so checking every
     * name costs nothing next to decoding one.
     */
    suspend fun drawableNames(packPackage: String): List<String> = withContext(dispatchers.io) {
        val pack = load(packPackage) ?: return@withContext emptyList()
        val mappedOnly = (pack.componentDrawable.values.toSet() - pack.catalog.toSet()).sorted()
        (pack.catalog + mappedOnly).distinct().filter(pack::has)
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

        val appFilter = parseAppFilter(packPackage, resources)
        LoadedPack(
            packageName = packPackage,
            resources = resources,
            componentDrawable = appFilter.componentDrawable,
            // Resolved against what the APK really contains, once, here — see `PackFallback.retainingShipped` for the
            // half-themed drawer that comes of trusting the declaration.
            fallback = appFilter.fallback.retainingShipped {
                resources.getIdentifier(it, "drawable", packPackage) != 0
            },
            catalog = parseDrawableCatalog(packPackage, resources),
        ).also { loaded = it }
    }

    /** Everything `appfilter.xml` says: which drawable each app gets, and what to do about the apps it misses. */
    private class AppFilter(val componentDrawable: Map<String, String>, val fallback: PackFallback)

    /**
     * Reads `appfilter.xml` — **both halves of it**, in one pass.
     *
     * The `<item>` mapping is the half this always read. The other is the four fallback tags, which are declared once
     * near the top of the file and describe what to draw for an app the mapping does not name: `<iconback>` (one or
     * more plates, `img1`…`imgN`), `<iconmask>`, `<iconupon>` and `<scale factor>`. Ignoring them is why an unthemed
     * app came out as a bare plate — see [PackFallback].
     *
     * `<item>` is matched wherever it appears, including inside the `<old>` block packs use for retired component
     * names. That is deliberate: a later entry overwrites an earlier one, so a current mapping always wins, and an old
     * one is only consulted for an app nothing newer speaks for — which is what that block is for.
     */
    private fun parseAppFilter(packPackage: String, resources: Resources): AppFilter {
        val parser = openPackXml(packPackage, resources, "appfilter")
            ?: return AppFilter(emptyMap(), PackFallback(emptyList(), null, null, DefaultFallbackScale))

        val map = mutableMapOf<String, String>()
        val backs = mutableListOf<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = DefaultFallbackScale

        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) map[component] = drawable
                        }
                        // `img1`, `img2`, … — read by *shape* rather than by counting up to a fixed limit, since the
                        // count is the author's and LineX White alone ships 24.
                        "iconback" -> backs += parser.imgAttributes()
                        "iconmask" -> mask = parser.imgAttributes().firstOrNull()
                        "iconupon" -> upon = parser.imgAttributes().firstOrNull()
                        "scale" -> scale = parser.getAttributeValue(null, "factor")
                            ?.toFloatOrNull()
                            ?.takeIf { it > 0f }
                            ?: scale
                    }
                }
                event = parser.next()
            }
        }.onFailure { Timber.w(it, "appfilter parse failed for %s", packPackage) }

        return AppFilter(map, PackFallback(backs, mask, upon, scale))
    }

    /** Every `imgN` on the current tag, in the author's order. */
    private fun XmlPullParser.imgAttributes(): List<String> =
        (0 until attributeCount)
            .filter { getAttributeName(it).startsWith("img") }
            .mapNotNull { getAttributeValue(it)?.takeIf(String::isNotBlank) }

    /**
     * Every drawable the pack **offers for browsing**, in the author's own order.
     *
     * **This is the half `appfilter.xml` cannot answer.** That file maps components to drawables, so its values are
     * only the icons the author *assigned to an app* — a pack's generic shapes, alternates and spares are mapped to
     * nothing and appear nowhere in it. `drawable.xml` is the de-facto file that lists them: what a pack's own "browse
     * icons" screen is built from, which is exactly what this browser is.
     *
     * **Order is preserved rather than sorted**, unlike the appfilter projection, and the difference is real: a mapping
     * is a hash with no order to keep, where this list is authored — grouped by `<category>`, alternates next to the
     * icon they vary. Sorting it alphabetically would throw away the one piece of curation a pack ships.
     *
     * `icon_pack` is tried as a second name because packs disagree about which of the two they ship; the item shape is
     * identical, so one parser reads either.
     */
    private fun parseDrawableCatalog(packPackage: String, resources: Resources): List<String> {
        val parser = openPackXml(packPackage, resources, "drawable")
            ?: openPackXml(packPackage, resources, "icon_pack")
            ?: return emptyList()

        val names = mutableListOf<String>()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    // `<category title="…"/>` tags are skipped rather than read: grouping is a presentation decision the
                    // browser has not made yet, and a flat list is what it draws today. The categories stay available
                    // here whenever it wants them — this is the file that carries them.
                    parser.getAttributeValue(null, "drawable")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(names::add)
                }
                event = parser.next()
            }
        }.onFailure { Timber.w(it, "drawable list parse failed for %s", packPackage) }
        return names
    }

    /**
     * One of the pack's XML files by [name], as a resource if it has one and from its assets otherwise.
     *
     * Both, because packs disagree: some ship them compiled into `res/xml`, others as raw assets. L1 tried the
     * same two in the same order, for `appfilter` alone — the [name] parameter is what lets the drawable catalog
     * reuse the lookup instead of repeating it.
     */
    private fun openPackXml(packPackage: String, resources: Resources, name: String): XmlPullParser? {
        val xmlId = resources.getIdentifier(name, "xml", packPackage)
        if (xmlId != 0) return runCatching { resources.getXml(xmlId) }.getOrNull()

        return runCatching {
            val assets = context.createPackageContext(packPackage, 0).assets
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(assets.open("$name.xml"), "UTF-8")
            }
        }.getOrNull()
    }

    private inner class LoadedPack(
        val packageName: String,
        val resources: Resources,
        val componentDrawable: Map<String, String>,
        val fallback: PackFallback,
        val catalog: List<String>,
    ) {

        /**
         * The pack's treatment of an app it does not theme, or `null` when it ships none.
         *
         * **The app's own icon is read here rather than passed in**, which is what keeps this out of every caller's
         * signature: the seam `core:icon` declares for exactly this artwork is [RawIconSource], and asking it costs
         * one platform lookup on a path that only runs for apps the pack missed.
         */
        fun fallbackFor(component: ComponentKey): Drawable? {
            if (fallback.isEmpty) return null
            val base = rawIcons.loadIcon(component) ?: return null
            return composePackFallback(
                base = base,
                back = fallback.backFor(component.packageName)?.let(::resolve),
                mask = fallback.mask?.let(::resolve),
                upon = fallback.upon?.let(::resolve),
                scale = fallback.scale,
                resources = context.resources,
            )
        }

        /** Whether the pack really ships a drawable of this name — an authored list can name one that is not there. */
        fun has(drawableName: String): Boolean =
            resources.getIdentifier(drawableName, "drawable", packageName) != 0

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

        /**
         * How far a pack shrinks an app's own icon into its plate when it declares no `<scale>`.
         *
         * A pack that ships plates and forgets the factor still has to put the icon *inside* one, so 1.0 would be the
         * one value guaranteed wrong. LineX White declares 0.5; this is a little more generous, since a pack silent
         * about it is more likely to have a thin frame than a thick one.
         */
        const val DefaultFallbackScale = 0.7f
    }
}

/** The key an `appfilter.xml` uses — a stringified `ComponentName`, which is the format packs are authored in. */
private fun ComponentKey.appFilterKey(): String = "ComponentInfo{$packageName/$className}"
