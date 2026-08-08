package inkspire.morphic.core.icon.render

import android.graphics.Bitmap
import android.util.LruCache
import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.icon.parse.DrawableParser
import inkspire.morphic.core.icon.source.RawIconSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The display side of the hybrid render: bakes an icon once and caches the [Bitmap] by [IconId], so a surface
 * showing hundreds of icons draws cached bitmaps instead of re-compositing each one.
 *
 * [get] is get-or-bake and **suspending** — it may load, parse and composite, and it moves that work onto a bounded
 * dispatcher of its own. The editor does *not* go through this cache — it uses the live [IconRenderer] path for
 * instant feedback — and calls [invalidate] on commit so surfaces re-bake with the new layer set.
 *
 * ## Two properties that are not optional on a weak device
 *
 * Both were learned from an ANR: four APPS surfaces composed at once (one per bound HOME edge) asked for the same
 * icons at the same moment, and the profile showed eight `Dispatchers.Default` threads pegged, `HeapTaskDaemon` at
 * 57%, and the main thread unable to service input for five seconds.
 *
 * - **Concurrent requests for one [IconId] are coalesced.** A plain `cache.get() ?: bake()` is a thundering herd:
 *   every caller that arrives before the first bake finishes does the whole load-parse-composite again and allocates
 *   a full bitmap that the next `put` immediately makes garbage. That allocation *is* the GC load in that trace.
 *   Now the first caller bakes and the rest await its result — suspended, so they hold no thread while they wait.
 * - **Baking is capped well below the core count** ([bakeParallelism]). Even deduplicated, a screenful of cells
 *   launches a coroutine each, and `Dispatchers.Default` is sized to the number of cores — so icon baking will
 *   happily occupy every one of them and leave nothing for the main thread. Leaving cores idle here is the point.
 */
class IconRenderManager(
    private val rawIconSource: RawIconSource,
    private val parser: DrawableParser,
    private val renderer: IconRenderer,
    maxCacheKb: Int = defaultCacheKb(),
    bakeDispatcher: CoroutineDispatcher = defaultBakeDispatcher(),
) {
    private val cache = object : LruCache<IconId, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: IconId, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val bakeContext = bakeDispatcher

    /**
     * Bakes currently running, by id, so a second request for the same icon waits on the first instead of repeating
     * it. Guarded by [lock] rather than being a concurrent map: the check-cache-then-claim step has to be atomic
     * against another caller doing the same, and a `ConcurrentHashMap` alone would not make it so.
     */
    private val inFlight = mutableMapOf<IconId, CompletableDeferred<Bitmap?>>()
    private val lock = Mutex()

    /**
     * The baked icon for [component] rendered from [layerSet] at [sizePx], cached by [IconId]. Returns `null`
     * when the app has no resolvable icon (e.g. uninstalled between listing and baking).
     *
     * Safe to call from anywhere: the expensive part is moved to [bakeContext], and a caller that arrives while the
     * same icon is already baking simply suspends until it is ready.
     */
    suspend fun get(component: ComponentKey, layerSet: IconLayerSet, sizePx: Int): Bitmap? {
        val id = IconId(component, layerSet, sizePx)
        cache.get(id)?.let { return it }

        // Claim the bake, or find the claim someone else already made. The cache is re-checked inside the lock
        // because it may have been filled between the read above and getting here.
        var owned: CompletableDeferred<Bitmap?>? = null
        val pending = lock.withLock {
            cache.get(id)?.let { return it }
            inFlight[id] ?: CompletableDeferred<Bitmap?>().also {
                inFlight[id] = it
                owned = it
            }
        }
        val claim = owned ?: return pending.await()

        // We own it. `finally` rather than a plain completion: a throw here would otherwise leave every waiter
        // suspended forever on a deferred nobody will ever complete.
        var baked: Bitmap? = null
        try {
            baked = withContext(bakeContext) {
                rawIconSource.loadIcon(component)?.let { renderer.render(parser.parse(it), layerSet, sizePx) }
            }
            if (baked != null) cache.put(id, baked)
        } finally {
            lock.withLock { inFlight.remove(id) }
            claim.complete(baked)
        }
        return baked
    }

    /**
     * The already-baked bitmap for these inputs, or `null` if not cached yet — a cache peek that never bakes,
     * so it is safe to call on the main thread (lets the UI show a cached icon with no flicker, then bake the
     * rest off-thread via [get]).
     */
    fun peek(component: ComponentKey, layerSet: IconLayerSet, sizePx: Int): Bitmap? =
        cache.get(IconId(component, layerSet, sizePx))

    /**
     * Bumped every time something is evicted — **the one input [IconId] cannot capture**, and the reason it exists.
     *
     * That key is built to make invalidation automatic: it carries the component, the resolved layer set and the
     * bake size, so any change *we* make produces a different key and therefore a different bitmap, for free. The
     * exception is the app's **own artwork**, which an update replaces without a single one of those values moving
     * — same component, same layer set, same size, different icon. Nothing in the key can see that, so it needs a
     * signal, and this is it.
     *
     * Compose state rather than a flow because every icon on screen reads it: a state read subscribes that
     * composition with no coroutine, where a `StateFlow` would mean one collector per icon and there are hundreds.
     * [inkspire.morphic.core.icon.compose.LauncherIcon] folds it into its bake keys, so a bump re-peeks every icon
     * — a cache hit for all but the ones just dropped, which re-bake.
     */
    var generation: Int by mutableIntStateOf(0)
        private set

    /** Drops every cached size/layer-set variant of [component] — e.g. after an icon edit. */
    fun invalidate(component: ComponentKey) {
        evict { it.component == component }
    }

    /**
     * Drops every baked icon belonging to any of [packageNames] — what an install, update or removal invalidates.
     *
     * By package rather than by component because that is what the platform reports changed, and one package can
     * publish several launcher activities. **A change that evicted nothing does not bump [generation]**: an app
     * being installed for the first time has no stale bakes, and recomposing every icon on screen to discover that
     * would be work for nothing.
     */
    fun invalidatePackages(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        evict { it.component.packageName in packageNames }
    }

    /** Evicts the entire cache. */
    fun clear() {
        cache.evictAll()
        generation++
    }

    /** Removes every entry matching [stale], and reports it once if anything went. */
    private fun evict(stale: (IconId) -> Boolean) {
        val doomed = cache.snapshot().keys.filter(stale)
        if (doomed.isEmpty()) return
        doomed.forEach { cache.remove(it) }
        generation++
    }

    private companion object {
        /** ~1/8 of the heap, floored at 4 MB, in KB (the LruCache is sized in KB via [LruCache.sizeOf]). */
        fun defaultCacheKb(): Int {
            val maxKb = Runtime.getRuntime().maxMemory() / 1024
            return (maxKb / 8).toInt().coerceAtLeast(4 * 1024)
        }

        /**
         * How many icons may be composited at once: **at most half the cores, and never more than three.**
         *
         * The cap is the point, not the number. Icon baking is pure CPU with a large allocation attached, and
         * `Dispatchers.Default` is sized to the core count — so left alone it takes every core, and the main thread
         * competes with it *and* with the GC the allocations provoke. Three is enough to keep a scrolling drawer
         * ahead of the eye on a fast device; half the cores is what keeps a four-core device usable at all.
         */
        fun bakeParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 3)

        @OptIn(ExperimentalCoroutinesApi::class)
        fun defaultBakeDispatcher(): CoroutineDispatcher =
            Dispatchers.Default.limitedParallelism(bakeParallelism())
    }
}
