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
 *
 * **It plans and then paints**: [plan] is the lattice and the drift under every tile, [draw] sorts the tiles into
 * bands, and a scrub is a [Morph] between two plans. See docs/MORPH_ENGINE_PLAN.md.
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

    /**
     * A block planned but not painted — the lattice the knobs fit and the drift under every tile, in a `[width]` ×
     * `[height]` frame and in no particular palette.
     *
     * **In pixels, like Confetti's plan**, since the lattice is fitted to the frame's pixels and the bake is worth more
     * than the symmetry of planning in shares; [draw] reaches another size by scaling the canvas.
     *
     * @property radius the tiles' corner radius, in pixels — the [Look]'s corner resolved against the tile.
     * @property dither how far along the ramp a drift of `1` pushes a tile — the dither knob, resolved.
     * @property drift the noise under every tile, row-major and roughly `-1..1` — the whole of what the seed decides,
     *   and nothing at all while [dither] is `0`.
     */
    internal class Plan(
        val grid: Grid,
        val radius: Float,
        val dither: Float,
        val drift: FloatArray,
        val width: Int,
        val height: Int,
    )

    override fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), plan(width, height, params, seed), palette, width, height)
        return bitmap
    }

    /** The block [params] and [seed] describe, in a `[width]` × `[height]` frame. */
    internal fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan {
        val look = Look.entries[params.variant.coerceIn(0, Look.entries.lastIndex)]
        val grid = gridOf(width, height, Amount.at(params.density), params.scale, look)
        val noise = PerlinNoise2d(seed)
        val drift = FloatArray(grid.rows * grid.columns)
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.columns) {
                // The field is read in frame-relative coordinates so its swells stay the same size on the picture
                // however fine the lattice is — a denser grid samples the same drift more finely, it does not get noisier.
                drift[row * grid.columns + col] = noise.at(
                    (grid.left + col * grid.cellWidth) / width * Frequency,
                    (grid.top + row * grid.cellHeight) / height * Frequency,
                )
            }
        }
        return Plan(
            grid = grid,
            radius = look.corner * min(grid.tileWidth, grid.tileHeight) / 2f,
            // A fraction of the *whole ramp*, not of one band — so the knob means the same thing however many stops the
            // color mode left behind. In band units it would erode a one-band palette's entire block at the setting
            // that merely roughens a five-band one's seams.
            dither = params.irregularity.coerceIn(0f, 1f) * MaxDither,
            drift = drift,
            width = width,
            height = height,
        )
    }

    /**
     * Paints [plan] into [canvas] at `[width]` × `[height]`, in [palette] — the bake into a software bitmap and a
     * scrub into a hardware canvas alike.
     *
     * **A tile never blends, in a scrub or out of one**: every moment's drift sorts every tile into a band exactly as
     * the bake does, so each frame of a scrub is a block this design could have baked. A tile switches band at the
     * moment the drift under it crosses a seam, which the turn in [Morph] makes happen tile by tile, not all at once.
     */
    internal fun draw(canvas: Canvas, plan: Plan, palette: Palette, width: Int, height: Int) {
        canvas.drawColor(palette.colorAt(0))
        // The ground takes stop 0 and the bands are the rungs above it. A one-stop palette has no ramp and draws
        // nothing but its ground, which is the honest answer rather than a field of invisible tiles.
        val tones = RampTones.aboveGround(palette)
        val bands = tones.size
        if (bands < 1) return
        val grid = plan.grid
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val tile = RectF()

        val resized = width != plan.width || height != plan.height
        if (resized) {
            canvas.save()
            canvas.scale(width.toFloat() / plan.width, height.toFloat() / plan.height)
        }
        for (row in 0 until grid.rows) {
            val down = if (grid.rows > 1) row.toFloat() / (grid.rows - 1) else 0f
            for (col in 0 until grid.columns) {
                val band = bandAt(down, plan.drift[row * grid.columns + col], plan.dither, bands)
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
                canvas.drawRoundRect(tile, plan.radius, plan.radius, paint)
            }
        }
        if (resized) canvas.restore()
    }

    /**
     * The band a tile [down] of the way down the block falls in, of [bands], once [drift] has pushed it [dither] of the
     * ramp's length — below `0` where it has been pushed off the light end, and past the last band where it has been
     * pushed off the dark one, which the caller clamps.
     */
    internal fun bandAt(down: Float, drift: Float, dither: Float, bands: Int): Int =
        floor((down + drift * dither) * bands).toInt()

    /**
     * A scrub between two blocks: the drifts under the tiles turn from one seed's field to the other's, and the seams
     * they break up move with them — see [draw] for why a tile switches rather than blends.
     *
     * **Nothing to pair**: the lattice is the knobs' and the frame's, so a tile's partner is the tile in its own place.
     * At no dither the seed decides nothing and the scrub is a still one, which is faithful rather than broken.
     */
    override fun scrub(
        width: Int,
        height: Int,
        palette: Palette,
        params: DesignParams,
        from: Long,
        to: Long,
    ): WallpaperMorph? {
        val morph = morph(plan(width, height, params, from), plan(width, height, params, to)) ?: return null
        return WallpaperMorph { canvas, t, w, h -> draw(canvas, morph.at(t), palette, w, h) }
    }

    /** Two blocks prepared to interpolate, tile for tile — or **null where they are not the same lattice**. */
    internal fun morph(from: Plan, to: Plan): Morph? {
        val same = from.grid == to.grid && from.width == to.width && from.height == to.height &&
            from.radius == to.radius && from.dither == to.dither
        return if (same) Morph(from, to) else null
    }

    /** Two blocks, tile for tile, and every moment between them. */
    internal class Morph(private val from: Plan, private val to: Plan) {

        /**
         * The block [t] of the way across; the ends are the plans themselves, as every design's are.
         *
         * **The drift turns ([turnNoise]) rather than blending straight**, and here the straight blend's failure is
         * one anybody would see: it cuts the drift's swing by nearly a third at the midpoint, so the seams would rule
         * straighter through the middle of a scrub and the top edge erode less — the dither knob appearing to move.
         * And because a quarter turn has at most one peak, the drift under a tile crosses a seam at most twice in a
         * scrub: a tile can switch band and switch back, but it cannot flicker.
         */
        fun at(t: Float): Plan = when {
            t <= 0f -> from
            t >= 1f -> to
            else -> Plan(
                grid = from.grid,
                radius = from.radius,
                dither = from.dither,
                drift = FloatArray(from.drift.size) { turnNoise(from.drift[it], to.drift[it], t) },
                width = from.width,
                height = from.height,
            )
        }
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
