package inkspire.morphic.core.designsystem.surface

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * The frame an **embedded Android View** sits in, which arbitrates that view's touches against the launcher's
 * surface swipe.
 *
 * A widget is the case it was written for: its content is another app's `RemoteViews`, so a scrolling list widget
 * handles the same finger the launcher does, and the two have to be told apart. Without this the pan won every
 * gesture and a widget could not be scrolled at all.
 *
 * **Why the platform's own signal is not enough.** A scrolling `View` claims a gesture from its ancestors by calling
 * `ViewParent.requestDisallowInterceptTouchEvent(true)` — an `AbsListView`/`ScrollView`/`RecyclerView` does so as it
 * passes its own touch slop. Compose's `AndroidView` does wire that through: `AndroidViewHolder` forwards it to the
 * `PointerInteropFilter` in its modifier chain. But all it changes there is *when* the filter dispatches to the view;
 * it cannot outrank an ancestor, because [surfacePagerGesture] runs on `PointerEventPass.Initial` and Initial runs
 * parents **before** children. And while the view can still be intercepted the filter dispatches moves on the
 * **`Final`** pass — two passes after the pan has already decided on that same event. So the pan reaches its
 * threshold first, every time, and consumption on a later pass arrives too late to matter. The signal is real; it is
 * just structurally invisible to the gesture that needs it.
 *
 * **So the claim is made in reverse.** This frame takes a [SurfaceGestureLock] claim at the **down** — assuming the
 * embedded view might want the gesture — and hands it back once the view has had *its own* slop's worth of movement
 * without asking to keep it. The pan, for its part, **waits** while anything is claiming rather than handing the
 * swipe back for the whole gesture (see [surfacePagerGesture]), so a declined claim still becomes a pan. The two
 * halves together cost a pan that starts on an embedded view **one event of latency** and nothing else.
 *
 * Hearing the request needs a frame of our own: `AndroidViewHolder` keeps the forwarded lambda internal, but
 * `ViewGroup.requestDisallowInterceptTouchEvent` propagates up the *view* hierarchy, so a parent of the embedded view
 * hears it before Compose does.
 *
 * @param embedded the view to host. Added at `MATCH_PARENT` on both axes, so this frame is exactly its size and a
 *   `Modifier.onSizeChanged` on the `AndroidView` still measures the embedded view.
 * @param lock the claim to take, or null outside the launcher shell — in which case this is a plain `FrameLayout` and
 *   the embedded view behaves exactly as it did before.
 */
class EmbeddedViewTouchFrame(
    context: Context,
    val embedded: View,
    private val lock: SurfaceGestureLock?,
) : FrameLayout(context) {

    /** The embedded view's own threshold — the platform value every scrolling `View` claims at. */
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f

    /** True once [embedded] (or a descendant) has asked not to be intercepted, i.e. it keeps this gesture. */
    private var embeddedClaimed = false

    /** Whether *this* frame currently holds a claim, so the release paths below are idempotent. */
    private var holdsLock = false

    init {
        addView(
            embedded,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Only ever latched true. The view system clears its own flag at the next down without telling the parent,
        // so the reset below is the down's job, not this call's.
        if (disallowIntercept) embeddedClaimed = true
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
            embeddedClaimed = false
            acquire()
        }
        // Dispatched **before** the verdict below, because a child claims from inside its own touch handling: a list
        // widget calls `requestDisallowInterceptTouchEvent(true)` as it starts scrolling, and that arrives on this
        // very event. Reading the flag first would release the claim a moment before the view took it.
        val handled = super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            // A view that did not handle the down receives nothing else — Compose's interop filter records the
            // down's return value and stops dispatching on false — so it can never claim, and answering for it here
            // is the difference between a released claim and one that outlives the gesture.
            MotionEvent.ACTION_DOWN -> if (!handled) release()
            // Past the view's own slop with no claim: it does not want this gesture. Hand the swipe back.
            MotionEvent.ACTION_MOVE -> if (!embeddedClaimed && hypot(ev.x - downX, ev.y - downY) > slop) release()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
        }
        return handled
    }

    /**
     * Backstop for a claim taken by a frame that then leaves the window — a cell disposed mid-touch. Every ordinary
     * path releases above; a leaked claim would lock the surface swipe for the rest of the session.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    private fun acquire() {
        if (!holdsLock) {
            lock?.acquire()
            holdsLock = true
        }
    }

    private fun release() {
        if (holdsLock) {
            lock?.release()
            holdsLock = false
        }
    }
}
