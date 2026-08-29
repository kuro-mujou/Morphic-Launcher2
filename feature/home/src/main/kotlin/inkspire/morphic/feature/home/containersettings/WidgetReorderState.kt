package inkspire.morphic.feature.home.containersettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import inkspire.morphic.core.model.WidgetInfo

/**
 * The live state of a drag-reorder over a widget container's contents.
 *
 * **The list is edited under the finger and written once, on release** — the studio's rule for anything draggable,
 * and the one place this screen keeps an optimistic layer. Everywhere else it writes straight through, because a
 * switch or a chip is a single value and a store round-trip is imperceptible; a drag is not, because the row has to
 * stay under the finger that is holding it and the rows it passes have to move as it passes them.
 *
 * **Swaps happen at half a row**, which is the same threshold the eye uses: the dragged row has crossed its
 * neighbour when their centers pass. Each swap takes a row's height back out of [offset], so what is left is always
 * the distance from the row's *current* home — which is why the offset stays small however far the finger travels,
 * and why the clamp at the ends is a clamp on that remainder rather than on the whole gesture.
 *
 * **The row height is measured, never written down.** It is `ContentRow`'s own dp, and a second copy of that number
 * here would be the launcher's standing hazard: the drag would swap at a threshold the rows are not drawn at, which
 * does not look like a wrong constant — it looks like a list that grabs the wrong item.
 */
@Stable
internal class WidgetReorderState {

    /** The order being dragged, as widget ids. Null when no drag is in flight and none is waiting to be stored. */
    private var order by mutableStateOf<List<Int>?>(null)

    /** The widget under the finger, or null. */
    var dragged by mutableStateOf<Int?>(null)
        private set

    /** How far the dragged row sits from its current place, in px. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var rowHeight = 0f

    /** Reported by each row as it is measured; they are uniform, so the last one to answer is as good as any. */
    fun measureRow(heightPx: Int) {
        rowHeight = heightPx.toFloat()
    }

    /**
     * What the list should draw: the dragged order while one is in flight or waiting to land, [stored] otherwise.
     *
     * **A held order whose membership no longer matches is dropped rather than reconciled.** It can only differ
     * because something else changed the container while this screen held an order — a widget added or removed —
     * and at that point the stored list is the newer answer. Reconciling would be guessing where the newcomer goes.
     */
    fun shown(stored: List<WidgetInfo>): List<WidgetInfo> {
        val ids = order ?: return stored
        val byId = stored.associateBy { it.appWidgetId }
        if (byId.size != ids.size || !byId.keys.containsAll(ids)) {
            order = null
            dragged = null
            return stored
        }
        return ids.mapNotNull(byId::get)
    }

    /** Clears a committed order once the store has caught up with it, so the two never disagree silently. */
    fun settled(stored: List<WidgetInfo>) {
        if (dragged == null && order == stored.map { it.appWidgetId }) order = null
    }

    fun begin(appWidgetId: Int, stored: List<WidgetInfo>) {
        order = shown(stored).map { it.appWidgetId }
        dragged = appWidgetId
        offset = 0f
    }

    /** Takes [dy] px of travel, swapping past each neighbour the dragged row's center clears. */
    fun drag(dy: Float) {
        val ids = order ?: return
        val id = dragged ?: return
        if (rowHeight <= 0f) return
        offset += dy

        val moved = ids.toMutableList()
        var at = moved.indexOf(id)
        while (offset > rowHeight / 2f && at < moved.lastIndex) {
            moved[at] = moved[at + 1]
            moved[at + 1] = id
            at++
            offset -= rowHeight
        }
        while (offset < -rowHeight / 2f && at > 0) {
            moved[at] = moved[at - 1]
            moved[at - 1] = id
            at--
            offset += rowHeight
        }
        // Whatever is left is under half a row — except at the two ends, where there was no neighbour to swap with
        // and the loop stopped. Clamping here is what stops the first row being dragged up out of the list.
        offset = offset.coerceIn(-rowHeight / 2f, rowHeight / 2f)
        order = moved
    }

    /**
     * Ends the drag and returns the order to store, or null if the widget was put back where it started.
     *
     * The held order is *kept* on a real move — it is what the list draws until the store answers with the same
     * thing, which is [settled]'s job.
     */
    fun drop(stored: List<WidgetInfo>): List<Int>? {
        val ids = order
        dragged = null
        offset = 0f
        if (ids == null || ids == stored.map { it.appWidgetId }) {
            order = null
            return null
        }
        return ids
    }

    fun cancel() {
        order = null
        dragged = null
        offset = 0f
    }
}

@Composable
internal fun rememberWidgetReorder(): WidgetReorderState = remember { WidgetReorderState() }
