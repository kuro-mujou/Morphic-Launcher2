package inkspire.morphic.feature.home

import android.appwidget.AppWidgetHostView
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.surface.EmbeddedViewTouchFrame
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.data.widgets.AppWidgetHostController
import org.koin.compose.koinInject

/** The widget's corner rounding, and the shape its host view is clipped to. */
private val WidgetShape = RoundedCornerShape(12.dp)

/**
 * One placed widget — **another app's views, hosted inside a cell of ours**.
 *
 * An `AndroidView` around an [AppWidgetHostView], which is the only way to draw a widget: its content is
 * `RemoteViews` inflated from another process, so there is no Compose equivalent and nothing to port. L1's
 * `WidgetCell` is the same three calls; the differences are below.
 *
 * **It tells the widget how big it is, and that is not optional.** A provider lays itself out from the size the
 * host reports, not from the space the view happens to occupy — a clock renders at its default size in a cell
 * twice as wide until `updateAppWidgetSize` says otherwise. The size comes from `onSizeChanged`, so a widget
 * re-lays itself out when the grid is resized or the device rotates.
 *
 * **The cell handles no gestures of its own, and passes [itemGestures] to its root rather than to its content.**
 * That is the exception `LauncherDragCell` names: an icon narrows its touch target to the icon and its label
 * because a cell is mostly slack around them, while a widget genuinely *fills* its cell — every pixel of it is the
 * item. The widget's own taps still reach it, because `launcherItemGestures` never consumes a down.
 *
 * **Its *swipes* reach it through [EmbeddedViewTouchFrame], which is a whole mechanism rather than a modifier.** The
 * surface-switching pan runs on `PointerEventPass.Initial` and so outranks the hosted view on every event, which made
 * a scrolling list widget unscrollable — the pan claimed the finger before the view was even handed the movement. The
 * frame is what lets the widget claim first; see its KDoc for why the platform's own
 * `requestDisallowInterceptTouchEvent` could not do the job on its own.
 *
 * **A widget that will not resolve draws its [label] instead** — its provider uninstalled, or an id the platform no
 * longer knows. Naming it rather than drawing nothing is deliberate: an empty cell that cannot be removed reads as
 * a rendering fault, where a labeled one invites the long-press that removes it.
 */
@Composable
internal fun WidgetCell(
    appWidgetId: Int,
    label: String,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
) {
    val host = koinInject<AppWidgetHostController>()
    val density = LocalDensity.current
    val colors = LocalMorphicColors.current

    // Reset with the id, so a cell reused for a different widget re-measures rather than reporting the previous
    // one's size to the new provider.
    var size by remember(appWidgetId) { mutableStateOf(IntSize.Zero) }
    var resolved by remember(appWidgetId) { mutableStateOf(true) }

    // **The launcher taking the gesture has to be *told* to the hosted view.**
    //
    // A widget's tap is not ours to suppress: the `AppWidgetHostView` receives the same touches we do and fires
    // its own click on `ACTION_UP`, which is why long-pressing a widget and releasing used to trigger it. The
    // gesture machine cannot help — it only decides whether *we* open the item — and consuming the release does
    // not either, because the interop layer has already handed the up to the view by the time a Compose node on
    // the Main pass could consume it.
    //
    // So the view is sent a synthetic `ACTION_CANCEL`, which is the platform's own way of saying "that gesture is
    // no longer yours". AOSP's Launcher3 does the same thing from a `CheckLongPressHelper`; this reads the two
    // launcher-wide signals instead, so there is no second long-press timer to keep in step with ours.
    //
    // Canceling on *any* open menu or *any* drag is deliberately broad: if this widget is not the one being
    // touched, a cancel is a no-op on it, and the alternative is plumbing "is it me?" through a modifier that is
    // opaque by design.
    val menuHost = LocalMenuHost.current
    val gestureLock = LocalSurfaceGestureLock.current
    val coordinator = requireDragCoordinator()
    val launcherOwnsFinger = menuHost?.request != null || coordinator.isDragging
    var hostView by remember(appWidgetId) { mutableStateOf<AppWidgetHostView?>(null) }
    LaunchedEffect(launcherOwnsFinger, hostView) {
        if (launcherOwnsFinger) hostView?.cancelPendingTouch()
    }

    if (!resolved) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(WidgetShape)
                .then(itemGestures),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        }
        return
    }

    AndroidView(
        factory = { context ->
            // The widget's view is always wrapped, never handed to `AndroidView` directly: the frame is what hears it
            // claim a scroll, and it is exactly the size of its child so the measurement below is unaffected. A bare
            // `FrameLayout` stands in when the widget will not resolve, because the factory has to return a view; the
            // flag then swaps to the placeholder on the next composition.
            val widget = host.createView(context, appWidgetId)
            if (widget == null) {
                resolved = false
                FrameLayout(context)
            } else {
                hostView = widget
                host.registerView(appWidgetId, widget)
                EmbeddedViewTouchFrame(context, widget, gestureLock)
            }
        },
        onRelease = { view ->
            view.hostedWidget()?.let { host.unregisterView(appWidgetId, it) }
        },
        update = { view ->
            val widget = view.hostedWidget()
            if (widget != null && size.width > 0 && size.height > 0) {
                val widthDp = with(density) { size.width.toDp().value.toInt() }
                val heightDp = with(density) { size.height.toDp().value.toInt() }
                // The deprecated four-argument form on purpose: its replacement takes a list of *candidate* sizes
                // for a widget the host may show at several, and ours has exactly one — the cell it was placed in.
                @Suppress("DEPRECATION")
                widget.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clip(WidgetShape)
            .onSizeChanged { size = it }
            .then(itemGestures),
    )
}

/**
 * The [AppWidgetHostView] inside an [EmbeddedViewTouchFrame], or null for the placeholder a widget that would not
 * resolve is drawn with.
 *
 * Reached through the frame rather than remembered separately so the size update and the unregister cannot disagree
 * about which view they are talking about — `unregisterView` matches by identity.
 */
private fun View.hostedWidget(): AppWidgetHostView? =
    (this as? EmbeddedViewTouchFrame)?.embedded as? AppWidgetHostView

/**
 * Tells this view that the touch gesture it is in the middle of has been taken away.
 *
 * A synthetic `ACTION_CANCEL` is the platform's own vocabulary for it: a `View` that receives one abandons the
 * gesture without firing a click, which is exactly what is wanted once the launcher's long-press has claimed the
 * finger. Coordinates are irrelevant to a cancel, so they are zero.
 */
private fun AppWidgetHostView.cancelPendingTouch() {
    val now = SystemClock.uptimeMillis()
    val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    try {
        dispatchTouchEvent(cancel)
    } finally {
        cancel.recycle()
    }
}
