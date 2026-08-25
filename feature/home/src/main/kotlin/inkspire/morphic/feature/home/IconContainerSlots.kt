package inkspire.morphic.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.container.ArrangementSlot
import inkspire.morphic.core.designsystem.container.slots
import inkspire.morphic.core.model.IconArrangement

/**
 * Where an icon container's icons sit inside a [widthPx] × [heightPx] box — the cell's own arithmetic, in the one
 * place both things that need it can reach.
 *
 * **It exists because a second consumer arrived**, which is the trigger for extracting at all: the cell draws from
 * these slots, and the surface hit-tests a press against them to decide whether a drag is lifting one icon out of
 * the container or lifting the whole container. Those two must agree about *exactly* where an icon is, and the way
 * they would have disagreed is the one this codebase keeps rediscovering — the gap is a bare dp, so a copy of this
 * call with a different literal would put the hit-test a few dp from the picture and read as "that icon is hard to
 * grab" rather than as a wrong number.
 */
internal fun iconContainerSlots(
    arrangement: IconArrangement,
    count: Int,
    widthPx: Float,
    heightPx: Float,
    density: Density,
    spacingScalePercent: Int = 100,
): List<ArrangementSlot> {
    // The gap between neighbouring icons, before the container's own scaling. A fixed dp rather than a fraction of
    // the tile: it is breathing room between two icons, which is a constant of how the eye separates them and not
    // of how big the container is — a proportional gap would grow into a gulf on a large container and vanish on a
    // small one. What one number cannot suit is every *icon* size, and the scaling is the answer to that: the user
    // adjusts it where they can see it, rather than a formula guessing.
    val gapPx = with(density) { 8.dp.toPx() } * spacingScalePercent / 100f
    return arrangement.slots(count, widthPx, heightPx, gapPx)
}

/**
 * The index of the slot [local] falls in, or `null` for a press that missed every icon.
 *
 * **Containment, not nearest.** A press in a container's empty middle — the hole in a ring, the slack around a
 * short arc — is aimed at the container itself, and answering it with whichever icon happens to be least far away
 * would make the gaps unusable for lifting, resizing or reaching the container's own menu. Nearest-slot is the
 * right question while a *drag is already in flight* and has to land somewhere; it is the wrong one for deciding
 * what the finger came down on.
 */
internal fun List<ArrangementSlot>.indexAt(local: Offset): Int? {
    val i = indexOfFirst {
        local.x >= it.x && local.x <= it.x + it.width && local.y >= it.y && local.y <= it.y + it.height
    }
    return i.takeIf { it >= 0 }
}

/**
 * The index of the slot nearest [local] by center, or `null` if there are none — the drop-time counterpart of
 * [indexAt].
 *
 * A drag released over the container has to resolve to *some* slot, because the alternative is a drop that
 * silently does nothing on a target the user was plainly aiming at. So this never misses, where [indexAt]
 * deliberately can: the question at drop time is "which of these did you mean", not "did you hit one".
 */
internal fun List<ArrangementSlot>.nearestIndexTo(local: Offset): Int? {
    if (isEmpty()) return null
    return indices.minBy { i ->
        val slot = this[i]
        val dx = local.x - (slot.x + slot.width / 2f)
        val dy = local.y - (slot.y + slot.height / 2f)
        dx * dx + dy * dy
    }
}
