package inkspire.morphic.data.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import timber.log.Timber

/**
 * One bound widget — an `appWidgetId` the host has allocated and the platform has attached to a provider.
 *
 * The counterpart of [WidgetProvider], and the distinction is the whole shape of the add flow: a provider is
 * something that *could* be added and has no id, while this exists on the device and will keep existing until the
 * host is told to delete the id. Everything after binding deals in this.
 *
 * @property configure the provider's configuration activity, or null when it has none — which is the branch the
 *   add flow turns on, since a widget with a configuration screen must show it before it can be placed.
 * @property minWidthPx the size the provider asks for, in pixels, for `WidgetSpan` to turn into a footprint.
 * @property minResizeWidthPx the smallest width the provider says it can still *draw* at — see
 *   [WidgetResizeRules].
 */
data class BoundWidget(
    val appWidgetId: Int,
    val provider: ComponentName,
    val label: String,
    val configure: ComponentName?,
    val minWidthPx: Int,
    val minHeightPx: Int,
    val resize: WidgetResizeRules,
)

/**
 * How small a provider says it can still be drawn — the **floor** on a resize.
 *
 * **`resizeMode` is deliberately not here, and that is a product decision.** A provider also declares which axes
 * it will allow to be resized, and this launcher does not honour it: providers under-declare constantly (a great
 * many ship `resizeMode="none"` and resize perfectly well), so gating on it mostly denies the user a size the
 * widget could have drawn. Every widget is resizable here.
 *
 * The minimums *are* honoured, because they are a different claim: an axis declaration is about permission, where
 * a minimum is about whether the layout can render at all below that size. Letting a widget be squeezed to
 * nothing would break the thing the user is trying to keep.
 *
 * @property minWidthPx the smallest width the provider will accept, in pixels — its `minResizeWidth`, falling
 *   back to `minWidth` when it declares none. A minimum larger than the size it asks to be *added* at would be a
 *   contradiction; taking the smaller of the two is the reading that can always be satisfied.
 */
data class WidgetResizeRules(
    val minWidthPx: Int,
    val minHeightPx: Int,
)

/**
 * The launcher's [AppWidgetHost] — what lets this app *display* another app's widget rather than merely list it.
 *
 * [WidgetCatalog] answers "what could be added?" with no host at all; everything here is about a widget that will
 * actually exist: allocating an id, getting the platform to bind it to a provider, building the view that draws
 * it, and giving the id back when the widget is removed.
 *
 * **An allocated id is a resource, not a value.** It survives this process, and a widget whose id is allocated but
 * never placed is a leak the user cannot see or clear. Every path that abandons the add flow therefore calls
 * [deleteId] — a declined bind, a cancelled configuration screen, a grid with no room. That is why the flow that
 * drives this reads as a chain of "or give the id back".
 *
 * **[startListening] is bound to the launcher being on screen.** The host only receives provider updates while it
 * is listening, so a clock stops ticking without it; listening while the launcher is not visible is work for
 * nobody. `LauncherShell` drives it, since "the launcher is on screen" is precisely what that composable means.
 */
interface AppWidgetHostController {

    /** Starts receiving provider updates. Idempotent, and safe to call when already listening. */
    fun startListening()

    /** Stops receiving updates. Pair with [startListening]. */
    fun stopListening()

    /**
     * Reserves an id for a widget about to be bound. **The caller owns it** until it is either placed or handed
     * back with [deleteId]; nothing else reclaims it.
     */
    fun allocateId(): Int

    /**
     * Releases [appWidgetId] — the platform forgets the binding and the provider is told the widget is gone.
     *
     * Called both when an add is abandoned and when a placed widget is removed from the layout. It is safe on an
     * id that was never bound, which is what makes the abandon paths simple.
     */
    fun deleteId(appWidgetId: Int)

    /**
     * Binds [appWidgetId] to [provider] without asking the user, returning whether the platform allowed it.
     *
     * **False is the normal answer, not an error.** Binding without consent is permitted only to the *active* home
     * app (and only then on some versions); anything else has to send the user through the system's own bind
     * dialog. The caller falls back to that intent, which is why this returns a boolean rather than throwing.
     */
    fun bindAllowed(appWidgetId: Int, provider: ComponentName): Boolean

    /** What [appWidgetId] is bound to, or null when it is unbound or the provider has gone. */
    fun boundWidget(appWidgetId: Int): BoundWidget?

    /**
     * Remembers the live view drawing [appWidgetId], so [snapshot] can find it. Called by the cell that hosts it.
     */
    fun registerView(appWidgetId: Int, view: AppWidgetHostView)

    /** Forgets [view], if it is still the one registered for [appWidgetId]. */
    fun unregisterView(appWidgetId: Int, view: AppWidgetHostView)

    /**
     * A bitmap of the widget as it is drawn right now, or null when it is not on screen or has no size yet.
     *
     * **This is what a dragged widget's floating proxy is.** Everything else the launcher drags is a cell it can
     * simply re-draw — an app icon, a folder tile — but a widget's content is another app's `RemoteViews`, and a
     * second `AppWidgetHostView` for the same id to follow the finger would be a second live instance of the
     * widget. A snapshot is both cheaper and more honest: the user is dragging the thing they were looking at.
     * L1 kept this method for the same purpose.
     */
    fun snapshot(appWidgetId: Int): Bitmap?

    /**
     * Builds the view that draws [appWidgetId], or null when it no longer resolves.
     *
     * Takes the *view* [context] rather than using the application one: an [AppWidgetHostView] inflates another
     * app's `RemoteViews` into this window, and it needs the context that window is themed by.
     */
    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView?
}

/**
 * Default [AppWidgetHostController] over the real [AppWidgetHost].
 *
 * `internal` so only Koin constructs it — consumers depend on the interface.
 */
internal class DefaultAppWidgetHostController(
    context: Context,
) : AppWidgetHostController {

    private val appContext: Context = context.applicationContext
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    /**
     * Whether we have called `startListening` — so the pair stays balanced across the lifecycle events that drive
     * it. `AppWidgetHost` tolerates an unbalanced `stopListening`, but tracking it keeps the log below honest
     * about which call actually failed.
     */
    private var listening = false

    override fun startListening() {
        if (listening) return
        // The platform can throw here when the widget service is temporarily unavailable (a user switch, a
        // package manager still starting). A launcher that crashed on that would be unusable in exactly the
        // moments it is most needed, and the cost of failing is that widgets do not tick until the next resume.
        runCatching { host.startListening() }
            .onSuccess { listening = true }
            .onFailure { Timber.w(it, "AppWidgetHost could not start listening") }
    }

    override fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { host.stopListening() }.onFailure { Timber.w(it, "AppWidgetHost could not stop listening") }
    }

    /**
     * The live views by id, so a drag can snapshot one. Not a leak: a cell unregisters as it leaves composition,
     * and there is one entry per *placed* widget at most.
     */
    private val views = HashMap<Int, AppWidgetHostView>()

    override fun registerView(appWidgetId: Int, view: AppWidgetHostView) {
        views[appWidgetId] = view
    }

    override fun unregisterView(appWidgetId: Int, view: AppWidgetHostView) {
        // Identity, not equality: two cells can briefly overlap while one is being replaced, and the *outgoing*
        // one must not remove the incoming one's registration.
        if (views[appWidgetId] === view) views.remove(appWidgetId)
    }

    override fun snapshot(appWidgetId: Int): Bitmap? {
        val view = views[appWidgetId] ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return runCatching {
            createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
        }.onFailure { Timber.w(it, "Could not snapshot widget %d", appWidgetId) }.getOrNull()
    }

    override fun allocateId(): Int = host.allocateAppWidgetId()

    override fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
            .onFailure { Timber.w(it, "Could not release widget id %d", appWidgetId) }
    }

    override fun bindAllowed(appWidgetId: Int, provider: ComponentName): Boolean =
        runCatching { appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider) }.getOrDefault(false)

    override fun boundWidget(appWidgetId: Int): BoundWidget? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return BoundWidget(
            appWidgetId = appWidgetId,
            provider = info.provider,
            label = info.loadLabel(appContext.packageManager).orEmpty(),
            configure = info.configure,
            minWidthPx = info.minWidth,
            minHeightPx = info.minHeight,
            resize = WidgetResizeRules(
                // `minResizeWidth` is optional and defaults to 0, which would mean "no minimum at all" — so the
                // add-time minimum stands in, and the smaller of the two wins where a provider declares both.
                minWidthPx = info.minResizeWidth.takeIf { it > 0 }?.coerceAtMost(info.minWidth) ?: info.minWidth,
                minHeightPx = info.minResizeHeight.takeIf { it > 0 }?.coerceAtMost(info.minHeight)
                    ?: info.minHeight,
            ),
        )
    }

    override fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }
            .onFailure { Timber.w(it, "Could not create the view for widget %d", appWidgetId) }
            .getOrNull()
    }

    private companion object {
        /**
         * This launcher's host id. Arbitrary but **permanent**: the platform keys a process's allocated widget
         * ids to it, so changing it orphans every widget the user has placed. L1's value, kept for that reason.
         */
        const val HOST_ID = 0x4D52
    }
}
