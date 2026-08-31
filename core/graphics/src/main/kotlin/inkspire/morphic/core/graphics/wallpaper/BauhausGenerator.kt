package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The frame cut into blocks by recursive splitting and filled from the palette, each ruled off in the darkest stop —
 * the *Bauhaus / Mondrian* look (gart's `arts/rects/mondrian`).
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
 */
object BauhausGenerator : Generator {

    /** A block in the unit square — left/top/right/bottom, `0..1`. */
    internal data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val random = Random(seed)
        val blocks = subdivide(passes(params.density), random)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            // The ruled grid, a fraction of the short side so it reads the same at any resolution.
            strokeWidth = minOf(width, height) * 0.006f
            color = palette.colorAt(palette.size - 1)
        }

        for (block in blocks) {
            fill.color = blockColor(random, palette)
            canvas.drawRect(block.left * width, block.top * height, block.right * width, block.bottom * height, fill)
            canvas.drawRect(block.left * width, block.top * height, block.right * width, block.bottom * height, ink)
        }
        return bitmap
    }

    /** How many subdivision passes [density] asks for — [MinPasses] a few bold blocks up to [MaxPasses] a fine grid. */
    internal fun passes(density: Float): Int =
        MinPasses + (density.coerceIn(0f, 1f) * (MaxPasses - MinPasses)).roundToInt()

    /**
     * The whole frame split [passes] times — each pass walks the current blocks and, per block, leaves it, halves it
     * horizontally, halves it vertically, or quarters it, stopping a block once it is below [MinCell] a side.
     *
     * The result **partitions the unit square**: the pieces cover it with no gap and no overlap, which is what lets the
     * fill just paint each one.
     */
    internal fun subdivide(passes: Int, random: Random): List<Rect> {
        var blocks = listOf(Rect(0f, 0f, 1f, 1f))
        repeat(passes) {
            val next = ArrayList<Rect>(blocks.size * 2)
            for (block in blocks) {
                if (block.width < MinCell * 2 && block.height < MinCell * 2) {
                    next.add(block)
                    continue
                }
                when (random.nextInt(6)) {
                    0, 1 -> next.add(block) // leave it whole this pass
                    2, 3 -> if (block.width >= MinCell * 2) splitVertical(block, next) else next.add(block)
                    else -> if (block.height >= MinCell * 2) splitHorizontal(block, next) else next.add(block)
                }
            }
            blocks = next
        }
        return blocks
    }

    private fun splitVertical(block: Rect, into: MutableList<Rect>) {
        val mid = block.left + block.width / 2
        into.add(block.copy(right = mid))
        into.add(block.copy(left = mid))
    }

    private fun splitHorizontal(block: Rect, into: MutableList<Rect>) {
        val mid = block.top + block.height / 2
        into.add(block.copy(bottom = mid))
        into.add(block.copy(top = mid))
    }

    /**
     * A block's fill — the lightest stop most of the time (the ground), a vivid middle stop [AccentChance] of the time
     * (the accent). Never the darkest stop, which is reserved for the ink between blocks.
     */
    private fun blockColor(random: Random, palette: Palette): Int {
        if (palette.size <= 2 || random.nextFloat() > AccentChance) return palette.colorAt(0)
        return palette.colorAt(1 + random.nextInt(palette.size - 2)) // a middle stop, excluding ground and ink
    }

    private const val MinPasses = 3
    private const val MaxPasses = 7

    /** The smallest a block may be, a side, as a fraction of the frame — below this it stops splitting. */
    private const val MinCell = 0.12f

    /** How often a block takes a vivid accent instead of the light ground — a few splashes, not a checkerboard. */
    private const val AccentChance = 0.28f
}
