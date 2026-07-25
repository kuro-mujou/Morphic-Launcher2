package inkspire.morphic.data.layout

/**
 * A cardinal direction an occupant can be shoved when a dragged item's footprint lands on top of it.
 *
 * This replaces L1's raw `Pair<Int, Int>` (`dr` to `dc`) deltas that [FreePush] threaded around. Those pairs
 * were a footgun the rewrite mandate calls out: every call site had to remember that `.first` was the row
 * delta and `.second` the column delta, and nothing stopped a caller passing `(2, 0)` — a "direction" two
 * cells long. Naming the four legal directions makes the set exhaustive and the deltas self-documenting; the
 * raw [deltaRow]/[deltaCol] stay exposed only for the geometry math inside [FreePush].
 *
 * @property deltaRow -1 = up, +1 = down, 0 = no vertical movement.
 * @property deltaCol -1 = left, +1 = right, 0 = no horizontal movement.
 */
enum class PushDirection(val deltaRow: Int, val deltaCol: Int) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1),
}
