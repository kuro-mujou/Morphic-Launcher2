package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A frame and the recorded *cuts* that divide it — the recipe a subdivision was made by, kept instead of thrown away
 * — and how two such recipes interpolate into one another.
 *
 * **A subdivision's cells are not independent objects, and that is the whole reason this exists.** Adjacent panes
 * share an edge because one cut made both of them, so a morph that pairs cells off and interpolates each one
 * separately breaks the partition the moment two neighbours are handed different partners: they part company along
 * the edge they used to share, and the ground opens between them. Measured on Vitrall, the lead went from 18% of the
 * frame to 40% at the middle of a scrub, and the retained bones — which are only meaningful *as* pane boundaries —
 * came loose and swept across open ground as free-floating strokes.
 *
 * Interpolating the cuts instead makes the partition an invariant rather than an aspiration: whatever the cuts are at
 * a given moment, applying them in order divides the frame exactly, because that is what cutting *is*. There is no
 * bound to tune, no pairing to refuse, and no way for two panes to drift apart.
 *
 * **Two trees are merged into one shared structure ([merge]) and the cells re-derived per frame ([cells]).** The
 * structure is fixed when the gesture begins; a frame is a walk that clips polygons. Where one tree cuts and the
 * other does not, the missing cut is supplied as a *flattened* one set just outside the region it divides
 * ([flattened]), so a pane that has no counterpart grows out of an edge or shrinks back into one instead of
 * appearing — and takes the whole gesture to do it rather than being gone by the first fifth of it.
 *
 * Nothing here knows what is being cut: a leaf carries an **index**, and what glass hangs on it is the caller's.
 */
// Cuts, trees and the blend of two trees are one subject, and each step of it — recording, applying, merging,
// deriving — is a named thing rather than a fragment of a longer one.
@Suppress("TooManyFunctions")
internal object GlassTree {

    /**
     * The two pieces a cut leaves, in the cut's **own** sides rather than in the order the geometry happened to
     * produce them.
     *
     * @property plus the piece on the side the cut's normal points to; null where the cut left nothing there.
     * @property minus the other piece, likewise nullable.
     * @property arc the boundary between them, for a caller drawing it as a bone. Null where the cut did not
     *   actually divide anything.
     */
    class Sides(val plus: FloatArray?, val minus: FloatArray?, val arc: FloatArray?)

    /**
     * One cut, as the recipe rather than as its result — enough to divide *any* region, not only the one it was
     * first drawn against.
     *
     * **[curve] is what makes a straight cut and a bowed one interpolable, and it is a curvature rather than a
     * radius for exactly that reason.** Flattening a bow means its radius running off to infinity, which linear
     * interpolation of a radius cannot express: lerping a radius toward zero *tightens* the arc into a circle the
     * size of a pane, which is the opposite of the intended motion and looks like the window curling up. Curvature
     * goes to zero where a cut goes straight, so the two ends of the family are an ordinary interval.
     *
     * The exact circle is carried alongside rather than rebuilt from [curve], because `1f / (1f / r)` is not `r` and
     * a plan re-derived through that round trip would not be the window that was baked.
     */
    sealed interface Cutting {
        /** Which way the cut runs, in radians. Its normal — the direction of [Sides.plus] — is a quarter turn on. */
        val angle: Float

        /** A point the cut passes through. */
        val px: Float
        val py: Float

        /** Signed curvature: `0` for a straight cut, `1/reach` for a bow, the sign being which side it bows to. */
        val curve: Float

        /** [region] divided, or handed whole to whichever side it fell on where this cut did not reach it. */
        fun cut(region: FloatArray): Sides

        /** The same cut with its sides named the other way round — the same geometry, [Sides.plus] swapped. */
        fun flipped(): Cutting
    }

    /** A cut along a line. */
    private class Straight(
        override val angle: Float,
        override val px: Float,
        override val py: Float,
    ) : Cutting {
        override val curve = 0f

        override fun cut(region: FloatArray): Sides {
            val dx = cos(angle)
            val dy = sin(angle)
            val pieces = GlassCut.split(region, px, py, dx, dy)
            if (pieces.size == 2) return Sides(pieces[0], pieces[1], GlassCut.chord(region, px, py, dx, dy))
            val piece = pieces[0]
            val side = (GlassCut.centroidX(piece) - px) * -dy + (GlassCut.centroidY(piece) - py) * dx
            return if (side >= 0f) Sides(piece, null, null) else Sides(null, piece, null)
        }

        override fun flipped(): Cutting = Straight(angle + Half, px, py)
    }

    /**
     * A cut along a circle.
     *
     * [GlassCut.bowAbout] hands back the inside of the circle first; the plus side is the inside only while the
     * centre is on the plus side, which is what [curve]'s sign says. Getting that backwards is silent — the two
     * subtrees simply swap, so a window re-cuts itself inside out partway through a scrub.
     */
    private class Bowed(
        override val angle: Float,
        override val px: Float,
        override val py: Float,
        override val curve: Float,
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
    ) : Cutting {
        override fun cut(region: FloatArray): Sides {
            val made = GlassCut.bowAbout(region, cx, cy, radius)
            if (made.panes.size == 2) {
                val inside = made.panes[0]
                val outside = made.panes[1]
                return if (curve >= 0f) Sides(inside, outside, made.arc) else Sides(outside, inside, made.arc)
            }
            val piece = made.panes[0]
            val within = hypot(GlassCut.centroidX(piece) - cx, GlassCut.centroidY(piece) - cy) <= radius
            return if (within == (curve >= 0f)) Sides(piece, null, null) else Sides(null, piece, null)
        }

        override fun flipped(): Cutting = Bowed(angle + Half, px, py, -curve, cx, cy, radius)
    }

    /** A cut along the line through (`px`, `py`) at [angle]. */
    fun straight(angle: Float, px: Float, py: Float): Cutting = Straight(angle, px, py)

    /**
     * A cut bowed off that line on signed radius [reach] — [GlassCut.bow]'s circle, struck the same way so a plan
     * derived through this is the one that function would have cut.
     */
    fun bowed(angle: Float, px: Float, py: Float, reach: Float): Cutting {
        if (abs(reach) < GlassCut.Tiny) return Straight(angle, px, py)
        val side = if (reach >= 0f) 1f else -1f
        val r = abs(reach)
        return Bowed(
            angle, px, py, 1f / reach,
            px + cos(angle + GlassCut.Quarter) * r * side,
            py + sin(angle + GlassCut.Quarter) * r * side,
            r,
        )
    }

    /**
     * A cut along the circle of radius [r] about (`cx`, `cy`) — the form a run of concentric courses is struck in.
     *
     * The line form it also has to carry is anchored at the point of that circle nearest [region]'s middle, since
     * that is the part of it doing the cutting; the normal points back at the centre, so the inside of the circle is
     * the plus side and a course keeps the side it was cut off.
     */
    fun about(cx: Float, cy: Float, r: Float, region: FloatArray): Cutting {
        val ux = GlassCut.centroidX(region) - cx
        val uy = GlassCut.centroidY(region) - cy
        val reach = hypot(ux, uy).coerceAtLeast(GlassCut.Tiny)
        return Bowed(
            atan2(-uy / reach, -ux / reach) - GlassCut.Quarter,
            cx + ux / reach * r,
            cy + uy / reach * r,
            1f / r,
            cx, cy, r,
        )
    }

    /**
     * One region of a subdivision: the cut that divides it, or the pane it is.
     *
     * **Mutable while a window is being cut and read-only ever after.** The subdivision discovers a region's children
     * long after it has decided to cut it — it works off a stack rather than by recursion, so that a fine window
     * cannot run the frame out of stack — and the glazing pass turns settled panes into short chains of courses
     * later still. A node that can be filled in is what lets both happen without a second structure to reconcile
     * against the first.
     *
     * @property cut null on a leaf; a leaf is where the subdivision stopped.
     * @property bone whether this cut is one of the first few, and so drawn in heavier lead.
     * @property index which pane this leaf is, into whatever list the caller keeps. `-1` until it is glazed.
     */
    class Branch {
        var cut: Cutting? = null
        var bone = false
        var plus: Branch? = null
        var minus: Branch? = null
        var index = -1
    }

    /**
     * [root] applied to [region], filling [panes] by leaf index and collecting the bones.
     *
     * [panes] is written by index rather than appended to, so the caller's pane order is the order it assigned when
     * it glazed — not the order this walk happens to reach them in.
     */
    fun cells(root: Branch, region: FloatArray, panes: Array<FloatArray?>, bones: MutableList<FloatArray>) {
        val cut = root.cut
        if (cut == null) {
            if (root.index >= 0) panes[root.index] = region
            return
        }
        val sides = cut.cut(region)
        if (root.bone) sides.arc?.let(bones::add)
        sides.plus?.let { cells(root.plus!!, it, panes, bones) }
        sides.minus?.let { cells(root.minus!!, it, panes, bones) }
    }

    /**
     * Two trees as one, each node carrying the cut it makes at either end of the scrub.
     *
     * @property from the cut at `t = 0`, null on a pane.
     * @property to the cut at `t = 1`, likewise.
     * @property fromIndex which pane of the starting window this is, or `-1` for one that is only arriving.
     * @property toIndex the same for the finishing window, `-1` for one that is leaving.
     */
    class Blend private constructor(
        val from: Cutting?,
        val to: Cutting?,
        val bone: Boolean,
        val plus: Blend?,
        val minus: Blend?,
        val fromIndex: Int,
        val toIndex: Int,
    ) {
        companion object {
            fun pane(fromIndex: Int, toIndex: Int) = Blend(null, null, false, null, null, fromIndex, toIndex)

            fun split(from: Cutting, to: Cutting, bone: Boolean, plus: Blend, minus: Blend) =
                Blend(from, to, bone, plus, minus, -1, -1)
        }
    }

    /**
     * [from] and [to] merged into one structure that can be cut at any moment between them, over [region].
     *
     * **Both trees are re-cut as this descends, and the regions are what it is for.** A cut that one window makes and
     * the other does not has to be flattened out of *the region it divides* rather than off the frame, and the only
     * way to know that region is to have cut down to it. It costs one frame's worth of clipping, once per gesture.
     */
    fun merge(from: Branch, to: Branch, region: FloatArray): Blend = merged(from, region, to, region)

    private fun merged(from: Branch?, fromRegion: FloatArray?, to: Branch?, toRegion: FloatArray?): Blend {
        if (from == null) return frozen(to!!, arriving = true)
        if (to == null) return frozen(from, arriving = false)
        val a = from.cut
        val b = to.cut
        return when {
            a != null && b != null -> paired(from, a, fromRegion!!, to, b, toRegion!!)
            a != null -> opening(from, a, fromRegion!!, to, toRegion!!, forward = true)
            b != null -> opening(to, b, toRegion!!, from, fromRegion!!, forward = false)
            else -> Blend.pane(from.index, to.index)
        }
    }

    /**
     * Two cuts of the same region, put into correspondence.
     *
     * **A cut has no direction of its own, and choosing the wrong one of its two is a fold across the whole
     * subtree.** The same line is `angle` or `angle + π` with its sides named the other way about; taken as written,
     * two windows whose cuts happen to be recorded pointing opposite ways interpolate through a half turn, sweeping
     * the boundary across the region and taking everything under it along. Naming the second cut the way that lies
     * nearest the first costs one comparison and removes the whole failure.
     */
    @Suppress("LongParameterList") // Two branches, the cut each makes and the region each makes it over.
    private fun paired(
        from: Branch,
        a: Cutting,
        fromRegion: FloatArray,
        to: Branch,
        b: Cutting,
        toRegion: FloatArray,
    ): Blend {
        val flip = gap(a.angle, b.angle) > gap(a.angle, b.angle + Half)
        val cut = if (flip) b.flipped() else b
        val plus = if (flip) to.minus!! else to.plus!!
        val minus = if (flip) to.plus!! else to.minus!!
        val here = a.cut(fromRegion)
        val there = cut.cut(toRegion)
        return Blend.split(
            a, cut, from.bone || to.bone,
            merged(from.plus!!, here.plus ?: fromRegion, plus, there.plus ?: toRegion),
            merged(from.minus!!, here.minus ?: fromRegion, minus, there.minus ?: toRegion),
        )
    }

    /**
     * A cut on one side facing a pane on the other: the pane gains that cut, flattened out of the region so that at
     * its own end of the scrub one side of it is empty.
     *
     * **The smaller piece is the one that goes**, so that the part of the picture created out of nothing is the
     * lesser part — a large pane shrinking away is noticed, a sliver is not. The survivor keeps the whole region and
     * is merged against the pane, which sends it down this same path again until it is a pane too; that is what
     * makes a whole subtree collapse into one piece progressively rather than by vanishing at the end.
     *
     * @param forward whether [deep] is the starting window; it decides which end of the blend the flattened cut is.
     */
    @Suppress("LongParameterList") // The deep branch, its cut, and a region on each side of the blend.
    private fun opening(
        deep: Branch,
        cut: Cutting,
        deepRegion: FloatArray,
        pane: Branch,
        paneRegion: FloatArray,
        forward: Boolean,
    ): Blend {
        val facing = facing(deep, cut.cut(deepRegion), deepRegion)
        // Cleared out of the region the *pane* fills, since that is the region this cut has to leave whole at the end
        // it does not belong to.
        val flat = flattened(cut, emptyPlus = facing.plusGoes, paneRegion)
        val kept = if (forward) {
            merged(facing.survivor, facing.survivorRegion, pane, paneRegion)
        } else {
            merged(pane, paneRegion, facing.survivor, facing.survivorRegion)
        }
        val gone = if (forward) {
            merged(facing.goner, facing.gonerRegion, null, null)
        } else {
            merged(null, null, facing.goner, facing.gonerRegion)
        }
        return Blend.split(
            if (forward) cut else flat,
            if (forward) flat else cut,
            deep.bone,
            if (facing.plusGoes) gone else kept,
            if (facing.plusGoes) kept else gone,
        )
    }

    /**
     * Which of a cut's two sides is leaving and which is staying, with the region each covers.
     *
     * @property plusGoes whether it is the cut's plus side that vanishes — which [opening] still needs, because it
     *   decides which way round the two blends hang off the node.
     */
    private class Facing(
        val survivor: Branch,
        val survivorRegion: FloatArray,
        val goner: Branch,
        val gonerRegion: FloatArray,
        val plusGoes: Boolean,
    )

    /** [deep]'s two sides sorted into the one that stays and the one that leaves — the smaller piece leaves. */
    private fun facing(deep: Branch, sides: Sides, deepRegion: FloatArray): Facing {
        val plus = sides.plus ?: deepRegion
        val minus = sides.minus ?: deepRegion
        return if (GlassCut.area(plus) <= GlassCut.area(minus)) {
            Facing(deep.minus!!, minus, deep.plus!!, plus, plusGoes = true)
        } else {
            Facing(deep.plus!!, plus, deep.minus!!, minus, plusGoes = false)
        }
    }

    /** A subtree with no counterpart: it holds still, and the flattened cut above it is what takes it away. */
    private fun frozen(branch: Branch, arriving: Boolean): Blend {
        val cut = branch.cut
            ?: return if (arriving) Blend.pane(-1, branch.index) else Blend.pane(branch.index, -1)
        return Blend.split(
            cut, cut, branch.bone,
            frozen(branch.plus!!, arriving),
            frozen(branch.minus!!, arriving),
        )
    }

    /**
     * [cut] straightened and moved just clear of [region], so that everything falls on one side of it.
     *
     * **Just clear of that region, not clear of the frame, and the difference is the whole feel of a scrub.** Pushed
     * by the frame's own diagonal — which is the obvious way to guarantee one side is empty — a cut leaves the pane
     * it divides about a fifth of the way through the gesture, and the subtree under it is then gone for the rest of
     * it. Measured on Vitrall, the lead fell from 18% of the frame to 7% in the middle and the window passed through
     * a state that read as the same design at a much lower density. Moved just far enough, the same cut spends the
     * whole scrub crossing its own region, so what is leaving shrinks smoothly and the window keeps its density.
     *
     * Straightened as well as moved, because a bow that is merely moved away is still a circle, and a region lying
     * wholly outside one is not cut by it at all — which is a refusal rather than a side, and a refusal cannot say
     * which side to keep.
     */
    private fun flattened(cut: Cutting, emptyPlus: Boolean, region: FloatArray): Cutting {
        val nx = cos(cut.angle + GlassCut.Quarter)
        val ny = sin(cut.angle + GlassCut.Quarter)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in region.indices step 2) {
            val at = region[i] * nx + region[i + 1] * ny
            lo = min(lo, at)
            hi = max(hi, at)
        }
        val clearance = (hi - lo) * Clearance
        val target = if (emptyPlus) hi + clearance else lo - clearance
        val push = target - (cut.px * nx + cut.py * ny)
        return Straight(cut.angle, cut.px + nx * push, cut.py + ny * push)
    }

    /**
     * [blend] cut [t] of the way across, over [region], reporting each pane as the two panes it lies between.
     *
     * A pane arriving reports `-1` for where it came from, one leaving `-1` for where it goes; the caller owns what
     * that means for the glass.
     */
    fun cells(
        blend: Blend,
        t: Float,
        region: FloatArray,
        bones: MutableList<FloatArray>,
        onPane: (from: Int, to: Int, outline: FloatArray) -> Unit,
    ) {
        val from = blend.from
        if (from == null) {
            onPane(blend.fromIndex, blend.toIndex, region)
            return
        }
        val sides = between(from, blend.to!!, t).cut(region)
        if (blend.bone) sides.arc?.let(bones::add)
        sides.plus?.let { cells(blend.plus!!, t, it, bones, onPane) }
        sides.minus?.let { cells(blend.minus!!, t, it, bones, onPane) }
    }

    /**
     * The cut [t] of the way from [a] to [b].
     *
     * Below [FlatCurve] the arc is drawn as a line: a circle that large deviates from its own chord by well under a
     * pixel across a whole frame, and striking it means a centre thousands of frames away, where the arc sampling
     * loses its precision to the subtraction of two large numbers.
     */
    fun between(a: Cutting, b: Cutting, t: Float): Cutting {
        val angle = lerpAngle(a.angle, b.angle, t)
        val px = lerp(a.px, b.px, t)
        val py = lerp(a.py, b.py, t)
        val curve = lerp(a.curve, b.curve, t)
        return if (abs(curve) < FlatCurve) Straight(angle, px, py) else bowed(angle, px, py, 1f / curve)
    }

    /** How far apart two directions are, the short way round. */
    private fun gap(from: Float, to: Float): Float = abs(shortestTurn(to - from))

    /** Plain linear interpolation, named so a call site reads as interpolation rather than as arithmetic. */
    fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /** The curvature of a circle so large that its arc and its chord are the same line at any size we draw at. */
    private const val FlatCurve = 1f / 40f

    /**
     * How far past a region a flattened cut is set down, as a share of that region's own extent.
     *
     * Small on purpose, and the two ways of being wrong are not symmetric: overshooting takes the cut out of the
     * region before the scrub ends, which is the coarse middle [flattened] exists to avoid, while undershooting
     * leaves a sliver at a moment nobody looks at — the ends of a scrub hand back the plans themselves.
     */
    private const val Clearance = 0.02f

    private const val Half = PI.toFloat()
}
