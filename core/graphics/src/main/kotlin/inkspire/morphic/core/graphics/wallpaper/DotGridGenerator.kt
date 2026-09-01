package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.floor
import kotlin.math.min

/**
 * A contained block of evenly-spaced rounded tiles, stepping through the palette in bands down its rows — the *Dot
 * Grid*.
 *
 * **Every element is the same size, and the color is the only thing that moves.** That is the whole design and it is
 * what makes it calm: the eye reads a single motif of uniform marks, and the palette walking down it in flat bands is
 * the picture. [HalftoneGenerator] is the sibling that does the opposite — one color, size driven by a field — and the
 * two share only their lattice.
 *
 * **The block is contained, not full-bleed, and the air around it is the design.** [DesignParams.scale] is the
 * *margin*: at `0` the tiles reach the frame's edges, at the default `0.5` the block occupies the middle half, and
 * above that it shrinks to a small mark on a wide ground. This is the knob that decides whether the wallpaper is a
 * texture or a motif, and it is why the default is a quiet block with room around it rather than a filled frame.
 *
 * **[DesignParams.irregularity] dithers the bands into each other.** At `0` the bands are ruled and every row is one
 * flat color; climbing it lets a coherent noise field push tiles across a band boundary, so the seams break up into a
 * scatter of the neighbouring tone rather than into per-tile static — the noise is 2D and smooth, which is what makes
 * the intrusions read as drifts rather than as speckle. Where the drift pushes a tile off the *light* end of the ramp
 * it is dropped instead, so the block's top edge erodes — which is what keeps the knob alive on a two-stop palette,
 * where there is a single band and no neighbouring tone to trade with.
 *
 * **[DesignParams.variant] is the tile's whole look — its corner, how much of its cell it fills, and its proportion.**
 * These are three numbers in the reference studio and one named choice here, because they are not independently useful:
 * a square that fills half its cell and a circle that fills all of it are two looks, not four knobs' worth. *Tiles* is
 * the one that fills its cell completely, which turns the same lattice into a solid banner of flat bands.
 *
 * **The ground is the palette's lightest stop and the bands are rungs on the ramp above it**, so the topmost band
 * sits one rung from the ground and is *deliberately* faint — the fade the design opens with. That is the departure
 * from [StopContrast]'s rule, and it holds only because this is a whole band of marks rather than a lone shape: a
 * hundred near-ground tiles read as a soft edge, where one would read as a missing shape.
 *
 * **The rungs come from [RampTones], floor and all.** One rung per stop above the ground lands on the palette's own
 * colors exactly, but the *default* color mode reduces the palette to two stops, which leaves a single rung and no
 * ramp: a flat block, with the dither having nothing to trade between. That failure is not this design's alone — it
 * killed Flowing Blobs the same way — so the arithmetic and its floor live in one place.
 *
 * [gridOf] is pure and tested — the fit is arithmetic that fails silently when it is wrong (a block that overflows the
 * frame, or one that leaves a sliver of unused box), and it needs no canvas to check.
 */
object DotGridGenerator : Generator {

    /** What [DesignParams.density] resolves to for this design — the count, and the *Columns* slider's own range. */
    private val Amount = AmountKnob.Count("Columns", 3..15)

    override val style = DesignStyle(
        amount = Amount,
        scale = "Margin",
        irregularity = "Dither",
        variant = VariantKnob("Look", Look.entries.map { it.label }),
    )

    /**
     * One of the tile shapes the design draws, as the three numbers that together make a look.
     *
     * @property label the option's name in the Style panel, positionally the [DesignParams.variant] index.
     * @property corner how round the tile is, `0..1` of half its short side — `1` is a circle (or a pill, once
     *   [aspect] is not square).
     * @property fill how much of its cell the tile takes, `0..1` — `1` makes neighbours touch, so the lattice becomes
     *   a solid field.
     * @property aspect the tile's width over its height. Above `1` the cell squashes vertically with it, so the rows
     *   crowd together and the block reads as stacked bars rather than as a grid.
     */
    internal enum class Look(val label: String, val corner: Float, val fill: Float, val aspect: Float) {
        DOTS("Dots", corner = 1f, fill = 0.5f, aspect = 1f),
        ROUNDED("Rounded", corner = 0.3f, fill = 0.62f, aspect = 1f),
        SQUARES("Squares", corner = 0f, fill = 0.62f, aspect = 1f),
        BARS("Bars", corner = 1f, fill = 0.8f, aspect = 3f),
        TILES("Tiles", corner = 0.12f, fill = 1f, aspect = 1f),
    }

    /**
     * The lattice the tiles are drawn on, in pixels — everything the fit decides.
     *
     * @property columns tiles across; exactly what [DesignParams.density] asked for.
     * @property rows tiles down; however many *square* cells fill the box. Counting against the square cell rather
     *   than the drawn one is what lets a wide look come out as a short banner instead of restacking to refill the box.
     * @property cellWidth the horizontal pitch. The painted width is `cellWidth * (columns - 1 + fill)` and that
     *   equals the box exactly — the *painted* extent is what is fitted, not the cell count, or the block would sit
     *   half a tile short of its own margin.
     * @property cellHeight the vertical pitch, `cellWidth / aspect` — equal to [cellWidth] for every square look.
     * @property left the left edge of the leftmost tile.
     * @property top the top edge of the topmost tile.
     * @property tileWidth one tile's drawn width.
     * @property tileHeight one tile's drawn height.
     */
    internal data class Grid(
        val columns: Int,
        val rows: Int,
        val cellWidth: Float,
        val cellHeight: Float,
        val left: Float,
        val top: Float,
        val tileWidth: Float,
        val tileHeight: Float,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val look = Look.entries[params.variant.coerceIn(0, Look.entries.lastIndex)]
        val grid = gridOf(width, height, Amount.at(params.density), params.scale, look)
        // The ground takes stop 0 and the bands are the rungs above it. A one-stop palette has no ramp and draws
        // nothing but its ground, which is the honest answer rather than a field of invisible tiles.
        val tones = RampTones.aboveGround(palette)
        val bands = tones.size
        val noise = PerlinNoise2d(seed)
        // A fraction of the *whole ramp*, not of one band — so the knob means the same thing however many stops the
        // color mode left behind. In band units it would erode a one-band palette's entire block at the setting that
        // merely roughens a five-band one's seams.
        val dither = params.irregularity.coerceIn(0f, 1f) * MaxDither

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.colorAt(0))
        if (bands < 1) return bitmap
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val radius = look.corner * min(grid.tileWidth, grid.tileHeight) / 2f
        val tile = RectF()

        for (row in 0 until grid.rows) {
            val down = if (grid.rows > 1) row.toFloat() / (grid.rows - 1) else 0f
            for (col in 0 until grid.columns) {
                // The field is read in frame-relative coordinates so its swells stay the same size on the picture
                // however fine the lattice is — a denser grid samples the same drift more finely, it does not get noisier.
                val drift = noise.at(
                    (grid.left + col * grid.cellWidth) / width * Frequency,
                    (grid.top + row * grid.cellHeight) / height * Frequency,
                )
                val band = floor((down + drift * dither) * bands).toInt()
                // Pushed off the light end of the ramp, a tile is simply not drawn — which is what keeps the dither a
                // live knob on a palette with a single band, where there is no neighbouring tone to trade with. It
                // erodes the block's top edge, the end the ramp starts from, so the motif fades in rather than ruling.
                if (band < 0) continue
                paint.color = tones[band.coerceAtMost(bands - 1)]
                tile.set(
                    grid.left + col * grid.cellWidth,
                    grid.top + row * grid.cellHeight,
                    grid.left + col * grid.cellWidth + grid.tileWidth,
                    grid.top + row * grid.cellHeight + grid.tileHeight,
                )
                canvas.drawRoundRect(tile, radius, radius, paint)
            }
        }
        return bitmap
    }

    /**
     * The lattice for a `[width] × [height]` frame at [columns] across, inside the box [margin] leaves.
     *
     * **[margin] is halved before it is applied**, so the default `0.5` insets a quarter of the frame on each side and
     * leaves the block the middle half — the proportion the design is styled around. It is capped short of `1` so the
     * block never vanishes entirely: a knob whose top end renders an empty frame is a knob with a broken half.
     *
     * The block is then centered on the frame rather than on the box, which are the same point — stated because the
     * *rows* deliberately do not fill their box exactly (there is only ever a whole number of them), and the leftover
     * has to be split across both edges or the block hangs off its own margin at the bottom.
     */
    internal fun gridOf(width: Int, height: Int, columns: Int, margin: Float, look: Look): Grid {
        val inset = (margin.coerceIn(0f, 1f) * 0.5f).coerceAtMost(MaxMargin)
        val boxWidth = width * (1f - 2f * inset)
        val boxHeight = height * (1f - 2f * inset)

        val cols = columns.coerceAtLeast(1)
        val cellWidth = boxWidth / (cols - 1 + look.fill)
        val cellHeight = cellWidth / look.aspect
        val tileWidth = cellWidth * look.fill
        val tileHeight = cellHeight * look.fill
        // Rows are counted against a *square* cell, then drawn at the squashed one — so a wide look keeps the row
        // count its square sibling has and the block comes out short, rather than stacking three times as many rows to
        // refill the box. Filling the box on both axes is what turns bars into vertical stripes: the vertical gap
        // shrinks with the cell while the horizontal one does not, until only the columns read.
        val rows = (floor((boxHeight - cellWidth * look.fill) / cellWidth).toInt() + 1).coerceAtLeast(1)

        val paintedWidth = cellWidth * (cols - 1) + tileWidth
        val paintedHeight = cellHeight * (rows - 1) + tileHeight
        return Grid(
            columns = cols,
            rows = rows,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            left = (width - paintedWidth) / 2f,
            top = (height - paintedHeight) / 2f,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
        )
    }

    /** How far along the ramp a tile can be pushed at full dither, as a fraction of the ramp's whole length. */
    private const val MaxDither = 0.32f

    /** How many noise cycles span the frame — the size of the drifts that break the seams up. */
    private const val Frequency = 2.5f

    /** The most of each side the margin may take, so the top of the knob still leaves a block to look at. */
    private const val MaxMargin = 0.45f
}
