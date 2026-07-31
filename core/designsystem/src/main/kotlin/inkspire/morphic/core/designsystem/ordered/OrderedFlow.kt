package inkspire.morphic.core.designsystem.ordered

import androidx.compose.ui.geometry.Offset
import inkspire.morphic.core.designsystem.grid.GridGeometry
import kotlin.math.floor

/**
 * **MovingGap** — the reorder model every *ordered* surface shares: one list, a gap that migrates through it as
 * the finger moves, and a flow that densifies on drop.
 *
 * The counterpart of the free grid's push engine (`FreeGridPlanner`/`FreePush` in `data:layout`). Where a
 * coordinate surface answers "which cell, and who gets shoved aside?", an ordered one answers "which *index*
 * does this land at?" — and the whole preview is the list rendered with the dragged item lifted to that index.
 *
 * **Generic over the item, and that is the point.** Its first home was the folder, typed on `ComponentKey`
 * because a folder holds apps. The APPS pager holds `IconItem` (app **or** folder), so a `ComponentKey`-typed
 * copy would have been the second implementation of one idea — the near-duplicate smell this rewrite exists to
 * avoid. Items are compared by `equals`, so any value type works.
 *
 * Pure list and geometry maths: no Compose state, no `data:layout`, no persistence. The surface owns its order
 * and its gap; these functions only compute the next one.
 */

/** The finger's horizontal position within its hovered cell, in `0f..1f` (`< 0.5` → left half). */
fun GridGeometry.cellFractionX(fingerInRoot: Offset): Float {
    val local = (fingerInRoot.x - originInRoot.x) / cellW
    return local - floor(local)
}

/**
 * Which third of its cell the finger is in — the **3-zone** partition an ordered surface uses when it can hold
 * folders: [LEFT]/[RIGHT] insert the gap before/after the hovered item, [CENTER] means merge into it.
 *
 * A surface that holds no folders (a folder's own grid, the category pager) has nothing to merge into and reads
 * [cellFractionX] instead, splitting each cell into halves. That is not a lesser version of this: with no merge
 * target, a centre third would be dead space where the user's aim does nothing.
 */
enum class Third { LEFT, CENTER, RIGHT }

/** Which [Third] of its hovered cell [fingerInRoot] falls in. */
fun GridGeometry.thirdInCell(fingerInRoot: Offset): Third = when {
    cellFractionX(fingerInRoot) < 1f / 3f -> Third.LEFT
    cellFractionX(fingerInRoot) < 2f / 3f -> Third.CENTER
    else -> Third.RIGHT
}

/**
 * The flat index of [cell] within a paged ordered flow: the slot counted from the first item of page 0.
 *
 * A page holds `cols × rows` items, so page *p*'s first slot is `p × perPage` — which is what lets one list be
 * addressed by a per-page grid's `(row, col)` without the surface doing the arithmetic twice.
 */
fun flatSlotOf(row: Int, col: Int, cols: Int, page: Int, perPage: Int): Int =
    page * perPage + row * cols + col

/**
 * The new gap — an insertion index into [order] **minus the dragged item** — for a finger over display slot
 * [flatSlot]. [currentGap] is the live gap (`< 0` seeds it from the dragged item's own position); [insertBefore]
 * picks the side of the hovered item to insert on (from [cellFractionX] or [thirdInCell]).
 *
 * Indices are into the list *without* the dragged item on purpose: that is the list the user is actually looking
 * at rearranging, and it makes "drop where it already is" a no-op rather than an off-by-one.
 */
fun <T> movingGap(
    order: List<T>,
    dragged: T,
    currentGap: Int,
    flatSlot: Int,
    insertBefore: Boolean,
): Int {
    val others = order.filter { it != dragged }
    var gap = (if (currentGap < 0) order.indexOf(dragged) else currentGap).coerceIn(0, others.size)
    when {
        flatSlot > others.size -> gap = others.size // past the last item → append
        flatSlot == gap -> Unit // already over the gap → no change
        else -> {
            val hovered = if (flatSlot < gap) flatSlot else flatSlot - 1 // others-index under the finger
            if (hovered in others.indices) gap = if (insertBefore) hovered else hovered + 1
        }
    }
    return gap
}

/**
 * [order] rendered with [dragged] lifted to gap index [gap], the others densified around it.
 *
 * One function serves both the live preview (the dragged cell drawn invisible in its slot, everything else
 * animating around it) and the committed order on drop — so what the user saw and what gets written cannot
 * disagree. With no drag ([dragged] null) it is just [order].
 *
 * @param key how to tell one item from another, defaulting to the item itself (i.e. `equals`, which is what the
 *   identity lists this was built for want). A surface that renders **resolved** items needs to override it: the
 *   APPS pager's `AppsItem` and the category pager's `AppInfo` both carry a label and an icon, so structural
 *   equality would answer "is this the same *content*" where the only question here is "is this the same *item*".
 *   Both surfaces had grown a private copy of this function for exactly that reason before the parameter existed —
 *   which is the near-duplicate smell this file's own KDoc warns about, so the selector is the fix rather than a
 *   third copy. [movingGap] needs no equivalent: it is handed identity lists (`GridItem`, `ComponentKey`) by every
 *   caller, because a *plan* is computed over what the drag carries, not over what is drawn.
 */
fun <T> movingGapDisplayOrder(order: List<T>, dragged: T?, gap: Int, key: (T) -> Any? = { it }): List<T> {
    if (dragged == null) return order
    val draggedKey = key(dragged)
    val others = order.filter { key(it) != draggedKey }
    val at = gap.coerceIn(0, others.size)
    return others.take(at) + dragged + others.drop(at)
}

/** The item under display slot [flatSlot] of [order], or null past the end. */
fun <T> itemAtSlot(order: List<T>, flatSlot: Int): T? = order.getOrNull(flatSlot)
