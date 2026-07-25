package inkspire.morphic.core.designsystem.drag

import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.model.GridItem

/**
 * A registered drop target living in the shared **root/window** coordinate space. Every drag-participating
 * surface contributes one; the coordinator hit-tests the finger against all of them at once. That single
 * shared space is what makes cross-surface drops and dragging out of a folder "just work" with no per-surface
 * handoff (docs/DRAG_AND_DROP_DESIGN.md §4).
 *
 * This is intentionally minimal for now — id, bounds, stacking, and an accept rule are all the coordinator
 * needs to route a drag. The per-zone **geometry** (finger↔cell) and **behaviour** (partition + reflow, §6)
 * are consumed by the placement planner, not the coordinator, so they'll be added here when the real
 * planner replaces the fake one.
 *
 * @property id stable identity used to key the registry and report the active target.
 * @property bounds the zone's rectangle in root/window coordinates, reported by the surface as it measures or
 *   moves; hit-testing against the measured bounds is what stops hit geometry drifting from what's drawn (the
 *   L1 smell of a hardcoded tap radius).
 * @property z stacking order: when zones overlap (an open folder above home), the highest [z] wins the finger.
 * @property accepts whether this zone will take the given dragged item — an A–Z drawer refuses a home item, a
 *   widget zone refuses an app. Zones that reject the item are skipped, so the finger falls through to
 *   whatever sits beneath them.
 */
data class DropZone(
    val id: ZoneId,
    val bounds: Rect,
    val z: Int,
    val accepts: (GridItem) -> Boolean,
)
