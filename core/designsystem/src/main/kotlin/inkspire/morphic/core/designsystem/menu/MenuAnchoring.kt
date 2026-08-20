package inkspire.morphic.core.designsystem.menu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * What a menu is anchored to — and the two cases are positioned by different rules because they *mean* different
 * things — named as one type, rather than two composables a caller has to choose between correctly.
 */
sealed interface MenuAnchor {

    /**
     * A **thing**: the item's bounds in root coordinates. The menu sits beside it and scales out of the edge nearest
     * it, because the menu describes *that icon* and has to be read as belonging to it.
     */
    data class Item(val bounds: Rect) : MenuAnchor

    /**
     * A **place**: where the finger was when a long-press landed on empty space, in root coordinates.
     *
     * The menu docks flush to whichever vertical screen edge that half of the screen is nearer and slides in from
     * it, vertically centered on the press so the rows land under the thumb.
     * **The reason it docks rather than sitting at the point** is that there is nothing there to point at: an item
     * menu must be beside its icon, but a surface menu describes the *surface*, so planting it on an arbitrary patch
     * of wallpaper would claim a relationship with whatever it happens to cover — and hugging the edge leaves the
     * wallpaper the user just pressed on visible beside it.
     */
    data class Press(val position: Offset) : MenuAnchor
}

/** Which vertical edge a [MenuAnchor.Press] menu hugs. */
enum class MenuDock { LEFT, RIGHT }

/**
 * The edge a menu pressed at [position] docks to: the side of [frame] that half of the screen is in, so the menu
 * opens on the same side as the thumb that asked for it rather than reaching across the screen.
 */
fun menuDockFor(position: IntOffset, frame: IntRect): MenuDock =
    if (position.x < frame.left + frame.width / 2) MenuDock.LEFT else MenuDock.RIGHT

/**
 * Where to place a menu of [menuSize] docked to [dock], vertically centered on the press at [position], inside
 * [frame] with [gapPx] clear of every edge.
 *
 * Horizontally it is pinned, not clamped: a docked menu is *meant* to touch its edge, so there is one x for each
 * side. Vertically it follows the finger and is clamped exactly as [menuOffsetFor] clamps, with the same
 * `maxOf(min, max)` guard so a menu taller than the frame pins to the top rather than throwing.
 */
fun dockedMenuOffsetFor(
    position: IntOffset,
    menuSize: IntSize,
    frame: IntRect,
    dock: MenuDock,
    gapPx: Int,
): IntOffset {
    val x = when (dock) {
        MenuDock.LEFT -> frame.left + gapPx
        MenuDock.RIGHT -> frame.right - menuSize.width - gapPx
    }
    val minY = frame.top + gapPx
    val maxY = frame.bottom - menuSize.height - gapPx
    return IntOffset(
        x.coerceAtLeast(frame.left + gapPx),
        (position.y - menuSize.height / 2).coerceIn(minY, maxOf(minY, maxY)),
    )
}

/**
 * Where a context menu sits relative to the item it was opened on.
 *
 * **Four values rather than two booleans** (`vertical`, `towardEnd`) — the same correction `SideZoneEdge` made to a
 * `landscape`/`dockAtStart` pair: two booleans encode four states while letting a reader
 * decode them at every use, and every consumer here — the offset, the transform origin — really does want to say
 * "which of the four", not "which axis, then which way".
 */
enum class MenuPlacement {
    /** Under the item, horizontally centered on it. */
    BELOW,

    /** Over the item, horizontally centered on it. */
    ABOVE,

    /** To the item's right, vertically centered on it. */
    RIGHT,

    /** To the item's left, vertically centered on it. */
    LEFT,
}

/**
 * Which side of [anchor] a menu should open on, inside the usable area [frame].
 *
 * **The axis follows the screen's shape and the direction follows the room left**, for a reason worth writing down:
 * a tall screen has spare height and little spare width, so stacking the
 * menu over or under the icon keeps it near the finger without shoving it sideways off the item it describes; a wide
 * one has the opposite, and a menu below an icon in landscape would cover the row beneath it and be clipped as often
 * as not. Then the direction flips toward whichever half of the frame has space, so an item near the top opens
 * downward and one near the bottom opens upward.
 *
 * @param anchor the item's own bounds, in root coordinates.
 * @param frame the area the menu may occupy — the window less [inkspire.morphic.core.designsystem.insets.uiInsets].
 *   Judged against the usable area rather than the whole window, so a tall notch or a gesture bar cannot tip the
 *   decision toward a half the menu is not allowed to be drawn in.
 */
fun menuPlacementFor(anchor: IntRect, frame: IntRect): MenuPlacement =
    if (frame.height >= frame.width) {
        val anchorCenterY = (anchor.top + anchor.bottom) / 2
        if (anchorCenterY < frame.top + frame.height / 2) MenuPlacement.BELOW else MenuPlacement.ABOVE
    } else {
        val anchorCenterX = (anchor.left + anchor.right) / 2
        if (anchorCenterX < frame.left + frame.width / 2) MenuPlacement.RIGHT else MenuPlacement.LEFT
    }

/**
 * Where to place a menu of [menuSize] beside [anchor], on the given [placement], inside [frame].
 *
 * [gapPx] does two jobs at once, deliberately: it separates the menu from the item (so the icon stays visible
 * beside its own menu) and it keeps the menu off the edges of [frame]. One number, because they are the same
 * breathing space seen from two sides.
 *
 * **Clamping is what makes the placement a preference rather than a promise.** A menu taller than the room below
 * its anchor is pushed back up into [frame] rather than being drawn off-screen; on a small enough frame it can end
 * up overlapping the item it belongs to, which is the honest outcome — there is nowhere else for it to be, and
 * `coerceIn` is given `maxOf(min, max)` so a menu larger than the frame pins to the top-left instead of throwing.
 */
fun menuOffsetFor(
    anchor: IntRect,
    menuSize: IntSize,
    frame: IntRect,
    placement: MenuPlacement,
    gapPx: Int,
): IntOffset {
    val minX = frame.left + gapPx
    val maxX = frame.right - menuSize.width - gapPx
    val minY = frame.top + gapPx
    val maxY = frame.bottom - menuSize.height - gapPx

    val rawX = when (placement) {
        MenuPlacement.BELOW, MenuPlacement.ABOVE -> (anchor.left + anchor.right) / 2 - menuSize.width / 2
        MenuPlacement.RIGHT -> anchor.right + gapPx
        MenuPlacement.LEFT -> anchor.left - gapPx - menuSize.width
    }
    val rawY = when (placement) {
        MenuPlacement.BELOW -> anchor.bottom + gapPx
        MenuPlacement.ABOVE -> anchor.top - gapPx - menuSize.height
        MenuPlacement.RIGHT, MenuPlacement.LEFT -> (anchor.top + anchor.bottom) / 2 - menuSize.height / 2
    }

    return IntOffset(
        rawX.coerceIn(minX, maxOf(minX, maxX)),
        rawY.coerceIn(minY, maxOf(minY, maxY)),
    )
}

/**
 * The corner or edge a menu on this [MenuPlacement] should grow *out of* — so the menu appears to spring from the
 * item rather than to inflate in place, which is the whole point of animating it at all.
 *
 * It is the edge nearest the anchor: a menu below its item scales up from its own top edge, one to the left from
 * its right edge.
 *
 * **Known limit, accepted**: the cross-axis is pinned to the center, so a menu that [menuOffsetFor] had to *clamp*
 * sideways (an item near the screen's edge) grows from a point a little off the item. Correcting it needs the
 * menu's measured size, which is not known until it has been laid out — a frame later than the animation starts.
 * Invisible in practice, the clamp being small compared to a menu's width.
 */
fun MenuPlacement.transformOrigin(): TransformOrigin = when (this) {
    MenuPlacement.BELOW -> TransformOrigin(0.5f, 0f)
    MenuPlacement.ABOVE -> TransformOrigin(0.5f, 1f)
    MenuPlacement.RIGHT -> TransformOrigin(0f, 0.5f)
    MenuPlacement.LEFT -> TransformOrigin(1f, 0.5f)
}
