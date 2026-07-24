package inkspire.morphic.core.icon.render

import android.graphics.Bitmap
import android.util.LruCache
import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.icon.parse.DrawableParser
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.core.model.ComponentKey

/**
 * The display side of the hybrid render: bakes an icon once and caches the [Bitmap] by [IconId], so a surface
 * showing hundreds of icons draws cached bitmaps instead of re-compositing each one.
 *
 * [get] is get-or-bake and **blocking** (it may load, parse, and composite), so callers run it off the main
 * thread. The editor does *not* go through this cache — it uses the live [IconRenderer] path for instant
 * feedback — and calls [invalidate] on commit so surfaces re-bake with the new layer set.
 */
class IconRenderManager(
    private val rawIconSource: RawIconSource,
    private val parser: DrawableParser,
    private val renderer: IconRenderer,
    maxCacheKb: Int = defaultCacheKb(),
) {
    private val cache = object : LruCache<IconId, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: IconId, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    /**
     * The baked icon for [component] rendered from [layerSet] at [sizePx], cached by [IconId]. Returns `null`
     * when the app has no resolvable icon (e.g. uninstalled between listing and baking).
     */
    fun get(component: ComponentKey, layerSet: IconLayerSet, sizePx: Int): Bitmap? {
        val id = IconId(component, layerSet, sizePx)
        cache.get(id)?.let { return it }

        val drawable = rawIconSource.loadIcon(component) ?: return null
        val baked = renderer.render(parser.parse(drawable), layerSet, sizePx)
        cache.put(id, baked)
        return baked
    }

    /**
     * The already-baked bitmap for these inputs, or `null` if not cached yet — a cache peek that never bakes,
     * so it is safe to call on the main thread (lets the UI show a cached icon with no flicker, then bake the
     * rest off-thread via [get]).
     */
    fun peek(component: ComponentKey, layerSet: IconLayerSet, sizePx: Int): Bitmap? =
        cache.get(IconId(component, layerSet, sizePx))

    /** Drops every cached size/layer-set variant of [component] — e.g. after an app update or an icon edit. */
    fun invalidate(component: ComponentKey) {
        cache.snapshot().keys
            .filter { it.component == component }
            .forEach { cache.remove(it) }
    }

    /** Evicts the entire cache. */
    fun clear() = cache.evictAll()

    private companion object {
        /** ~1/8 of the heap, floored at 4 MB, in KB (the LruCache is sized in KB via [LruCache.sizeOf]). */
        fun defaultCacheKb(): Int {
            val maxKb = Runtime.getRuntime().maxMemory() / 1024
            return (maxKb / 8).toInt().coerceAtLeast(4 * 1024)
        }
    }
}
