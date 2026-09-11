package inkspire.morphic.core.graphics.wallpaper

/**
 * A rectangle divided by guillotine cuts — each straight across whatever it divides, parallel to one of its sides —
 * kept as the tree of cuts rather than only the pieces, and how two such trees interpolate into one another.
 *
 * **Shared by the designs built this way**, [MondrianGenerator] (every cut a halving) and [ModernMosaicGenerator] (cuts
 * at a seeded share), because the part that goes invisibly wrong is the merge, and two of them would disagree only in
 * a scrub nobody slowed down to look at.
 *
 * **Two trees merge along axes, never across them.** A cut pairs only with one the other tree makes along the same
 * axis, and slides from its share to the other's. Anywhere else — a cut only one tree makes, or two made across each
 * other — the cut is one-sided: it slides from where it stands to the edge of its region, taking the side with fewer
 * pieces away with it, while the other side is merged against the whole of the other tree's region. [GlassTree] does
 * the same for Vitrall's cuts but pairs any two by turning one into the other, and a guillotine design whose cuts tilt
 * mid-scrub is no longer one.
 *
 * **A cut stands at a share of its own region, not at a line on the frame**, so a side on its way out carries its
 * whole subdivision down in proportion, and every moment is still a partition of rectangles.
 *
 * Nothing here knows what is being cut: a piece carries an **index**, and what it is painted with is the caller's.
 */
internal object GuillotineTree {

    /** A rectangle — left/top/right/bottom, in whatever frame the design cuts. */
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * One region: the cut that divides it, or a piece.
     *
     * **Filled in while the frame is being cut, and read-only after**, like [GlassTree.Branch]: a design decides a
     * piece's cut long after it made the piece, in whatever order its own construction visits them.
     *
     * @property vertical which way the cut runs — `true` a vertical cut, parting left from right; null on a piece.
     * @property at where the cut stands, as a share of the region's width where [vertical] and its height otherwise.
     * @property first the left or top side, null on a piece.
     * @property second the right or bottom side.
     * @property index which piece this is, into whatever list the design keeps; `-1` until the design says.
     */
    class Node(val rect: Rect) {
        var vertical: Boolean? = null
        var at = 0f
        var first: Node? = null
        var second: Node? = null
        var index = -1

        /** This piece cut [at] of the way across — see [split] — and its two new pieces, first side first. */
        fun cut(vertical: Boolean, at: Float): Pair<Node, Node> {
            val sides = split(rect, vertical, at)
            this.vertical = vertical
            this.at = at
            val made = Node(sides.first) to Node(sides.second)
            first = made.first
            second = made.second
            return made
        }
    }

    /**
     * [rect] cut [at] of the way across — along its width where [vertical], its height otherwise.
     *
     * **The one place a cut becomes two rectangles**, for a design's construction and for every moment of a scrub, so
     * a cut that holds still through a scrub lands on exactly the pixels the bake put it on.
     */
    fun split(rect: Rect, vertical: Boolean, at: Float): Pair<Rect, Rect> = if (vertical) {
        val x = rect.left + rect.width * at
        rect.copy(right = x) to rect.copy(left = x)
    } else {
        val y = rect.top + rect.height * at
        rect.copy(bottom = y) to rect.copy(top = y)
    }

    /** [root]'s pieces, first sides before second. */
    fun pieces(root: Node): List<Node> {
        val out = ArrayList<Node>()
        fun walk(node: Node) {
            val first = node.first
            if (first == null) {
                out.add(node)
            } else {
                walk(first)
                walk(node.second!!)
            }
        }
        walk(root)
        return out
    }

    /**
     * Two trees as one: each node carries its cut's axis and where the cut stands at either end of the scrub.
     *
     * @property vertical the cut's axis, null on a piece.
     * @property fromAt the cut's share at `t = 0` — `0` or `1` for a cut not made yet.
     * @property toAt the same at `t = 1` — `0` or `1` for a cut that has gone.
     * @property fromIndex which piece of the starting tree this is, or `-1` for one that is only arriving.
     * @property toIndex the same for the finishing tree, `-1` for one that is leaving.
     */
    class Blend(
        val vertical: Boolean?,
        val fromAt: Float,
        val toAt: Float,
        val first: Blend?,
        val second: Blend?,
        val fromIndex: Int,
        val toIndex: Int,
    )

    /** [from] and [to] merged into one [Blend] — see the class note for which cuts pair. */
    fun merge(from: Node, to: Node): Blend = merged(from, to)

    private fun merged(from: Node?, to: Node?): Blend {
        if (from == null) return frozen(to!!, arriving = true)
        if (to == null) return frozen(from, arriving = false)
        val a = from.vertical
        val b = to.vertical
        return when {
            a != null && a == b -> Blend(
                a, from.at, to.at,
                merged(from.first, to.first), merged(from.second, to.second),
                -1, -1,
            )
            a != null -> leaving(from, a, to, forward = true)
            b != null -> leaving(to, b, from, forward = false)
            else -> Blend(null, 0f, 0f, null, null, from.index, to.index)
        }
    }

    /**
     * [deep]'s cut, which the other tree has no partner for, sliding to the edge of its region — leaving through the
     * scrub if [forward], arriving through it otherwise.
     *
     * **The side with fewer pieces is the one that goes**, so that as little as possible is taken away or made out of
     * nothing; the second side goes where the two are equal. The side that stays is merged against [other], which
     * sends it down this same path again until it finds cuts to pair.
     */
    private fun leaving(deep: Node, vertical: Boolean, other: Node, forward: Boolean): Blend {
        val first = deep.first!!
        val second = deep.second!!
        val firstGoes = pieces(first).size < pieces(second).size
        // The share the cut stands at once the leaving side is gone: none of the region left to the first side, or
        // all of it.
        val gone = if (firstGoes) 0f else 1f
        val kept = if (firstGoes) second else first
        val stay = if (forward) merged(kept, other) else merged(other, kept)
        val leave = frozen(if (firstGoes) first else second, arriving = !forward)
        return Blend(
            vertical,
            if (forward) deep.at else gone,
            if (forward) gone else deep.at,
            if (firstGoes) leave else stay,
            if (firstGoes) stay else leave,
            -1,
            -1,
        )
    }

    /** A subtree with no counterpart: its cuts hold still, and the cut above it is what takes it away. */
    private fun frozen(node: Node, arriving: Boolean): Blend {
        val vertical = node.vertical ?: return if (arriving) {
            Blend(null, 0f, 0f, null, null, -1, node.index)
        } else {
            Blend(null, 0f, 0f, null, null, node.index, -1)
        }
        return Blend(
            vertical, node.at, node.at,
            frozen(node.first!!, arriving), frozen(node.second!!, arriving),
            -1, -1,
        )
    }

    /**
     * [blend] cut [t] of the way across over [frame], reporting every piece in painting order with the two pieces it
     * lies between — `-1` for an end it is not in.
     */
    fun pieces(blend: Blend, frame: Rect, t: Float, onPiece: (rect: Rect, fromIndex: Int, toIndex: Int) -> Unit) {
        val vertical = blend.vertical
        if (vertical == null) {
            onPiece(frame, blend.fromIndex, blend.toIndex)
            return
        }
        val sides = split(frame, vertical, blend.fromAt + (blend.toAt - blend.fromAt) * t)
        pieces(blend.first!!, sides.first, t, onPiece)
        pieces(blend.second!!, sides.second, t, onPiece)
    }
}
