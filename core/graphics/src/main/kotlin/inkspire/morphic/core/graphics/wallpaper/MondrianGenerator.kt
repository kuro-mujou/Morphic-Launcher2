package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.graphics.wallpaper.GuillotineTree.Node
import inkspire.morphic.core.graphics.wallpaper.GuillotineTree.Rect
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.random.Random

/**
 * The frame cut into blocks by recursive splitting and filled from the palette, each ruled off in the darkest stop —
 * the *Mondrian* look (gart's `arts/rects/mondrian`).
 *
 * **The orthogonal, ruled sibling of [BauhausGenerator]'s arc tiles.** Both cut the frame into flat colored pieces;
 * this one splits it unevenly and draws the ink line between the pieces, where the other lays an even lattice and
 * curves inside each cell. They are told apart at a glance by the ruling — it is the whole of this look and absent
 * from that one.
 *
 * **Recursive subdivision, generalized off Mondrian's three primaries onto the palette.** gart's Mondrian hard-codes
 * white/red/blue/yellow; a launcher whose whole point is that the palette carries the color cannot. So the blocks are
 * filled from the palette instead — most take the lightest stop as the ground, a seeded few take a vivid middle stop
 * as an accent, and the ink between them is the darkest stop. Same composition, any palette.
 *
 * **The split is a partition, not a scatter — every block tiles, none overlaps.** Starting from the whole frame, each
 * pass either leaves a block, halves it one way, or halves it both ways, stopping once a block is too small to split
 * again. That the pieces still exactly cover the frame is the property [subdivide] is tested for — a gap or an overlap
 * is a silently-wrong tiling. [DesignParams.density] sets how many passes, so how fine the blocks get. Deterministic in
 * [seed].
 *
 * **It plans and then paints, and the plan keeps the cuts.** [subdivide] records each halving as a [GuillotineTree]
 * rather than keeping only the pieces, because a scrub between two Mondrians is a matter of *cuts* sliding. See
 * docs/MORPH_ENGINE_PLAN.md.
 */
object MondrianGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Divisions* slider's own range. */
    private val Amount = AmountKnob.Count("Divisions", 3..7)

    override val style = DesignStyle(amount = Amount)

    /**
     * A Mondrian planned but not painted — at no particular size and in no particular palette.
     *
     * @property root the cuts, [subdivide]'s tree, whose pieces are indexed in painting order.
     * @property blocks the tree's pieces in painting order.
     * @property tones each block's paint, by its index — [Ground], or an index into the palette's [accents].
     * @property accents how many accent tones [tones] index into — [accents] of the palette it was planned for.
     */
    internal class Plan(val root: Node, val blocks: List<Node>, val tones: IntArray, val accents: Int)

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(params, accents(palette).size, seed), palette, width, height)
        return bitmap
    }

    /**
     * The Mondrian [params] and [seed] describe, for a palette with [accents] accent tones.
     *
     * **It takes the accent count and not the colors**, since a tone is drawn as an index among however many there
     * are — so a recolor to a palette with as many accents is a redraw of this plan.
     */
    internal fun plan(params: DesignParams, accents: Int, seed: Long): Plan {
        val random = Random(seed)
        val root = subdivide(passes(params.density), random)
        val blocks = GuillotineTree.pieces(root)
        blocks.forEachIndexed { i, block -> block.index = i }
        // Toned after the cutting and in painting order, from the same stream — the order a stored recipe's colors
        // were always drawn in.
        val tones = IntArray(blocks.size) { tone(random, accents) }
        return Plan(root, blocks, tones, accents)
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake. A scrub's moment is painted by
     * the same [paint] from the blocks [Morph] cuts for it.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        val accents = accents(palette)
        val colors = plan.tones.map { colorOf(it, palette, accents) }
        paint(canvas, plan.blocks.map { it.rect }, colors, palette, width, height)
    }

    /** [blocks] filled with [colors] and ruled off in the ink, in order — every Mondrian frame, baked or scrubbed. */
    private fun paint(
        canvas: Canvas,
        blocks: List<Rect>,
        colors: List<Int>,
        palette: Palette,
        width: Int,
        height: Int,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            // The ruled grid, a fraction of the short side so it reads the same at any resolution.
            strokeWidth = minOf(width, height) * 0.006f
            color = palette.colorAt(palette.size - 1)
        }
        for (i in blocks.indices) {
            val block = blocks[i]
            fill.color = colors[i]
            canvas.drawRect(block.left * width, block.top * height, block.right * width, block.bottom * height, fill)
            canvas.drawRect(block.left * width, block.top * height, block.right * width, block.bottom * height, ink)
        }
    }

    /** How many subdivision passes [density] asks for — a few bold blocks up to a fine grid. */
    internal fun passes(density: Float): Int = Amount.at(density)

    /**
     * The whole frame split [passes] times — each pass walks the current blocks and, per block, leaves it, halves it
     * vertically or halves it horizontally, stopping a block once it is below [MinCell] a side.
     *
     * The result **partitions the unit square**: the pieces cover it with no gap and no overlap, which is what lets the
     * fill just paint each one. A block halved in place keeps its halves where it stood in the pass's list, so the
     * tree's walk of its pieces is the order they always came out in.
     */
    internal fun subdivide(passes: Int, random: Random): Node {
        val root = Node(Rect(0f, 0f, 1f, 1f))
        var blocks = listOf(root)
        repeat(passes) {
            val next = ArrayList<Node>(blocks.size * 2)
            for (block in blocks) {
                val rect = block.rect
                // A block too small either way draws nothing from the stream; every other block rolls its move.
                val vertical = if (rect.width < MinCell * 2 && rect.height < MinCell * 2) {
                    null
                } else {
                    when (random.nextInt(MoveFaces) / 2) {
                        0 -> null // leave it whole this pass
                        1 -> true.takeIf { rect.width >= MinCell * 2 }
                        else -> false.takeIf { rect.height >= MinCell * 2 }
                    }
                }
                if (vertical == null) {
                    next.add(block)
                } else {
                    val halves = block.cut(vertical, Half)
                    next.add(halves.first)
                    next.add(halves.second)
                }
            }
            blocks = next
        }
        return root
    }

    /**
     * A scrub between two Mondrians: the rulings slide along their own axes, and none ever turns — [GuillotineTree]'s
     * merge, every cut of which here stands at a half until it leaves.
     *
     * **Any two Mondrians of one palette merge** — two trees of halvings always do — so the only refusal is a plan
     * made for a different number of accents, whose tones would index into a different set.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val accents = accents(palette)
        val morph = morph(plan(params, accents.size, from), plan(params, accents.size, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> morph.draw(canvas, t, palette, w, h) }
    }

    /** Two Mondrians merged to interpolate — or **null where their tones do not index into the same accents**. */
    internal fun morph(from: Plan, to: Plan): Morph? =
        if (from.accents == to.accents) Morph(from, to, GuillotineTree.merge(from.root, to.root)) else null

    /** Two Mondrians and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan, private val tree: GuillotineTree.Blend) {

        /** Paints the moment [t]; the ends are the plans themselves, each drawn as its own bake. */
        fun draw(canvas: Canvas, t: Float, palette: Palette, width: Int, height: Int) {
            when {
                t <= 0f -> MondrianGenerator.draw(canvas, from, palette, width, height)
                t >= 1f -> MondrianGenerator.draw(canvas, to, palette, width, height)
                else -> {
                    val accents = accents(palette)
                    val blocks = ArrayList<Rect>()
                    val colors = ArrayList<Int>()
                    at(t) { rect, fromIndex, toIndex ->
                        // A block only arriving or only leaving is its own tone the whole way.
                        val start = if (fromIndex >= 0) from.tones[fromIndex] else to.tones[toIndex]
                        val end = if (toIndex >= 0) to.tones[toIndex] else start
                        blocks.add(rect)
                        colors.add(mix(colorOf(start, palette, accents), colorOf(end, palette, accents), t))
                    }
                    paint(canvas, blocks, colors, palette, width, height)
                }
            }
        }

        /** Every block of the moment [t], in painting order, with its index in each plan — `-1` where it is not in one. */
        fun at(t: Float, onBlock: (rect: Rect, fromIndex: Int, toIndex: Int) -> Unit) =
            GuillotineTree.pieces(tree, Rect(0f, 0f, 1f, 1f), t, onBlock)
    }

    /**
     * Color [a] mixed [t] of the way to [b] — [a] itself, exactly, where the two are one.
     *
     * **A block's color blends rather than switching**, because a shuffle re-tones a large share of the blocks and a
     * switch would flip them all in the one frame at the midpoint — Confetti's reason, which Dot Grid escapes and this
     * does not: nothing here carries each block across at its own moment.
     */
    private fun mix(a: Int, b: Int, t: Float): Int = if (a == b) a else LinearGradientGenerator.lerpArgb(a, b, t)

    /**
     * The tones a block may be accented with — the ramp between the ground and the ink, neither end included.
     *
     * **Read as a ramp rather than indexed into the stops, which is what killed the design at its own default.** The
     * previous rule was "a stop between the first and the last", and it opened with `palette.size <= 2` returning the
     * ground: the default color mode reduces every palette to **two** stops, so at the setting most people first see
     * this design it had no accent at all and drew 96% bare ground under a ruling — a sheet of graph paper. That is
     * the failure [RampTones] was extracted for, named there on Dot Grid and Flowing Blobs; the Mondrian was split out
     * of Bauhaus in W11a and never picked it up. Reading the ramp costs nothing where there are stops to land on — a
     * six-stop palette still yields exactly its four middle stops — and gives a two-stop palette two real tones of its
     * own. The final tone is dropped because it *is* the ink, and a block the color of the ruling has no edges.
     */
    internal fun accents(palette: Palette): List<Int> = RampTones.aboveGround(palette).dropLast(1)

    /**
     * A block's tone, of [accents] accent tones — [Ground] most of the time, an accent [AccentChance] of the time.
     * Never the darkest stop, which is reserved for the ink between blocks.
     *
     * A palette with no room for an accent at all (a single stop) draws every block on the ground, which is the honest
     * picture rather than a fallback.
     */
    internal fun tone(random: Random, accents: Int): Int {
        if (accents == 0 || random.nextFloat() > AccentChance) return Ground
        return random.nextInt(accents)
    }

    /** [tone]'s color in [palette], whose [accents] are handed in so a frame's worth of blocks reads them once. */
    private fun colorOf(tone: Int, palette: Palette, accents: List<Int>): Int =
        if (tone == Ground) palette.colorAt(0) else accents[tone]

    /** A block's tone when it takes the lightest stop, the ground, rather than an accent. */
    internal const val Ground = -1

    /** Where a halving cut stands, as a share of the region it halves. */
    private const val Half = 0.5f

    /**
     * The faces of the die a block's move is drawn from — **two per move**, which is why it is six rather than three.
     *
     * The three moves are equally likely either way, so the pairing is not what the six buys: a `nextInt` of a
     * different bound takes a different number out of the seeded stream, and every stored recipe's wallpaper would
     * re-roll into a different picture. That is the silent part.
     */
    private const val MoveFaces = 6

    /** The smallest a block may be, a side, as a fraction of the frame — below this it stops splitting. */
    private const val MinCell = 0.12f

    /** How often a block takes a vivid accent instead of the light ground — a few splashes, not a checkerboard. */
    private const val AccentChance = 0.28f
}
