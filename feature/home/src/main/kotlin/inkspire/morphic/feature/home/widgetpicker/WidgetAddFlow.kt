package inkspire.morphic.feature.home.widgetpicker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.data.widgets.BoundWidget
import timber.log.Timber

/** No widget is being added. `AppWidgetManager` uses the same value for "not a real id". */
private const val NoPendingId = AppWidgetManager.INVALID_APPWIDGET_ID

/**
 * Drives **adding a widget**: allocate an id, get it bound to the provider, run the provider's configuration
 * screen if it has one, and hand the result to whoever will place it.
 *
 * Two of those four steps may bounce off another activity, which is the whole reason this exists as a state
 * holder rather than a function — the flow is suspended across a result and resumed in a callback.
 *
 * ```
 *   start(provider) ─ bindAllowed? ─ yes ─┐
 *          │                              ├─ configure? ─ yes ─→ [configure activity] ─→ onResult ─┐
 *          └─ no ─→ [system bind dialog] ─┘                                                        │
 *                          └─ onResult ─ ok ─┘                    no ──────────────────────────────┴─→ onBound
 * ```
 *
 * **Every path that does not end at [onBound] gives the id back.** An allocated `appWidgetId` outlives this
 * process, so a declined bind, a cancelled configuration screen, or a placement with nowhere to go would each
 * otherwise leak a widget the user can neither see nor remove. That is what makes [onBound]'s return value part
 * of the contract rather than a convenience.
 *
 * **What it does *not* decide is where the widget goes.** L1's equivalent held the home state, both grid configs,
 * four cell sizes and the surface kind so it could place the widget itself — fourteen mutable fields reassigned on
 * every composition. Here the caller receives a [BoundWidget] and answers whether it kept it, so the flow needs to
 * know nothing about grids at all.
 *
 * @param onBound called with the bound, configured widget. **Return true if it was placed** (the caller now owns
 *   the id) and false if it could not be — this releases the id in that case.
 */
@Stable
class WidgetAddFlow internal constructor(
    private val host: AppWidgetHostController,
    private val onBound: (BoundWidget) -> Boolean,
) {

    /** The id being added, or [NoPendingId]. Only one add is in flight at a time — the picker is modal. */
    private var pendingId by mutableIntStateOf(NoPendingId)

    internal var launchBind: (Intent) -> Unit = {}
    internal var launchConfigure: (Intent) -> Unit = {}

    /**
     * Begins adding the widget published by [provider].
     *
     * The silent bind is tried first: the *active* home app is usually allowed to bind without asking, so most
     * adds never show the system dialog at all. When it is refused — we are installed but not the current home
     * app, or the platform simply says no — the dialog is the fallback rather than an error.
     */
    fun start(provider: ComponentName) {
        val id = host.allocateId()
        pendingId = id
        if (host.bindAllowed(id, provider)) {
            configureOrFinish(id)
        } else {
            launchBind(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                },
            )
        }
    }

    /** The system bind dialog closed. */
    internal fun onBindResult(resultCode: Int) {
        val id = pendingId
        if (id == NoPendingId) return
        if (resultCode == Activity.RESULT_OK) configureOrFinish(id) else abandon(id)
    }

    /** The provider's configuration screen closed. */
    internal fun onConfigureResult(resultCode: Int) {
        val id = pendingId
        if (id == NoPendingId) return
        if (resultCode == Activity.RESULT_OK) finish(id) else abandon(id)
    }

    /**
     * Bound — now run the provider's configuration screen, or place it if it has none.
     *
     * **A widget with a configuration screen must show it before being placed**, not after: the provider expects
     * to be configured while it is still invisible, and several write their initial state only when that screen
     * returns OK. Placing first would put a half-set-up widget on the grid.
     */
    private fun configureOrFinish(id: Int) {
        val configure = host.boundWidget(id)?.configure
        if (configure == null) {
            finish(id)
        } else {
            launchConfigure(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                },
            )
        }
    }

    /** Hands the finished widget to the caller, and releases the id if it could not take it. */
    private fun finish(id: Int) {
        pendingId = NoPendingId
        val bound = host.boundWidget(id)
        if (bound == null) {
            // Bound a moment ago and gone now — the provider was uninstalled mid-flow, or the profile went away.
            Timber.w("Widget %d bound but no longer resolves; releasing it", id)
            host.deleteId(id)
            return
        }
        if (!onBound(bound)) {
            Timber.w("Nowhere to place widget %d; releasing it", id)
            host.deleteId(id)
        }
    }

    /** The user backed out of a step. The id was never used, so it goes straight back. */
    private fun abandon(id: Int) {
        pendingId = NoPendingId
        host.deleteId(id)
    }
}

/**
 * A [WidgetAddFlow] wired to this composition's activity-result launchers.
 *
 * [onBound] is kept current through [rememberUpdatedState], so the flow can be remembered once while still
 * calling back into whatever the surface's *latest* placement logic is — which matters because that logic closes
 * over the grid and its measured geometry, both of which change under it while a configuration screen is open.
 */
@Composable
internal fun rememberWidgetAddFlow(
    host: AppWidgetHostController,
    onBound: (BoundWidget) -> Boolean,
): WidgetAddFlow {
    val latestOnBound by rememberUpdatedState(onBound)
    val flow = remember(host) { WidgetAddFlow(host) { latestOnBound(it) } }

    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.onBindResult(it.resultCode)
    }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.onConfigureResult(it.resultCode)
    }
    flow.launchBind = { bind.launch(it) }
    flow.launchConfigure = { configure.launch(it) }

    return flow
}
