package inkspire.morphic.core.icon.parse

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import timber.log.Timber

/**
 * Splits a platform [Drawable] into a [ParsedIcon] (foreground / background? / monochrome?).
 *
 * Mostly pure analysis: no shaping, scaling, or compositing — those belong to the renderer. Kept a concrete class
 * (not an interface): the analysis is single-strategy, so an interface would be a speculative abstraction;
 * if the renderer ever needs to fake it, extract one then.
 *
 * An [AdaptiveIconDrawable] contributes its own foreground and background (and, on Android 13+, its optional
 * monochrome layer). Any other drawable is a **legacy** icon: the whole drawable becomes the foreground and there
 * is no background of its own — we never try to cut a glyph out of a flat legacy bitmap (unreliable). For those,
 * [LegacyBackground] may recover a background *color* from the artwork's own edge; see [legacyBackground].
 */
class DrawableParser(private val resources: Resources) {

    /**
     * @param label who this icon belongs to, for the measurement diagnostics only — never for a decision. Null is
     *   fine and simply logs as unknown; see [rasterized].
     */
    fun parse(drawable: Drawable, label: String? = null): ParsedIcon = when (drawable) {
        is AdaptiveIconDrawable -> ParsedIcon(
            // getForeground()/getBackground() are nullable in the SDK; fall back / stay legacy if absent.
            foreground = (drawable.foreground ?: drawable).overshot().toParsedLayer(resources, true, label, "foreground"),
            // **The background is deliberately not measured, and not normalized.** Its ink legitimately covers the
            // whole canvas — that is what a plate is — so a measurement would say "full" and mean nothing. Leaving
            // it untouched is also a decision rather than an omission: with no mask, rescaling a plate would crop it
            // to a square, which is a visible change nobody asked for. It is not rasterized either, for the same
            // reason: nothing about it is measured, so nothing depends on it being size-independent.
            //
            // It **is** overshot, though, and that is geometry rather than measurement: a plate cropped differently
            // from the foreground in front of it would put the two out of register wherever the plate has anything
            // on it but a flat color.
            background = drawable.background?.overshot()?.toParsedLayer(resources),
            monochrome = drawable.monochromeOrNull()?.overshot()?.toParsedLayer(resources, true, label, "monochrome"),
        )

        else -> ParsedIcon(
            foreground = drawable.toParsedLayer(resources, true, label, "legacy"),
            background = legacyBackground(drawable),
        )
    }

    /**
     * The flat color behind a legacy icon, recovered from the artwork's own border, or `null` when there is not
     * one to recover.
     *
     * Rasterizes the drawable small and hands its border ring to [LegacyBackground]. **Small on purpose**: this
     * runs on every parse, and the question — "is this edge one flat color?" — is answered as well by a
     * thumbnail as by the full icon, at a fraction of the allocation. A `ColorDrawable` is answered without
     * drawing anything, since its edge is its color by definition.
     */
    private fun legacyBackground(drawable: Drawable): ParsedLayer? {
        if (drawable is ColorDrawable) return null // already the foreground; a plate behind it would be the same.
        val ring = borderRing(drawable) ?: return null
        return LegacyBackground.detectFill(ring)?.let(ParsedLayer::Color)
    }

    /** The pixels within [RingWidth] of an edge of [drawable] rendered at [SampleSize], or null if it cannot draw. */
    private fun borderRing(drawable: Drawable): IntArray? {
        val bitmap = createBitmap(SampleSize, SampleSize)
        try {
            // The drawable's own bounds are whatever the last renderer left them as, so they are set here rather
            // than trusted — the same reason the compositor sets them before every draw.
            drawable.setBounds(0, 0, SampleSize, SampleSize)
            drawable.draw(Canvas(bitmap))

            val pixels = IntArray(SampleSize * SampleSize)
            bitmap.getPixels(pixels, 0, SampleSize, 0, 0, SampleSize, SampleSize)

            return buildList {
                for (y in 0 until SampleSize) {
                    for (x in 0 until SampleSize) {
                        val onBorder = x < RingWidth || y < RingWidth ||
                            x >= SampleSize - RingWidth || y >= SampleSize - RingWidth
                        if (onBorder) add(pixels[y * SampleSize + x])
                    }
                }
            }.toIntArray()
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        /** Enough resolution to tell a flat plate from a picture, and small enough to be free. */
        const val SampleSize = 32

        /** How deep the sampled border is — ~9% in from each edge, which is where a plate color is if there is one. */
        const val RingWidth = 3
    }
}

/** The adaptive monochrome (themed-icon) layer, or `null` below Android 13 or when the icon exposes none. */
private fun AdaptiveIconDrawable.monochromeOrNull(): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) monochrome else null

/**
 * An adaptive layer expanded so that the part the platform actually *shows* fills the box.
 *
 * **This is the "adaptive-layer overshoot" the renderer's KDoc has been deferring, and it was visible all along as
 * icons that looked slightly small.** An [AdaptiveIconDrawable]'s foreground and background are 108-unit canvases of
 * which only the central 72 is the masked, guaranteed-visible area; the ring around it is bleed the platform
 * reserves for parallax. So Android draws each layer at **1.5× the icon's size, centered**, and masks to the icon.
 * Drawing one into the box at 1× instead — which is what happened here — leaves the artwork the designer drew
 * covering about two thirds of the square, with transparent margin around it.
 *
 * That is a constant *fraction*, which is exactly why it hid for so long: at a 48dp cell it reads as an icon that
 * is a touch small, and only at the studio's zoom does it read as what it is — the icon's box plainly larger than
 * the icon in it.
 *
 * **A negative [InsetDrawable] rather than expanded bounds inside [rasterized]**, so there is one mechanism and not
 * two: everything downstream — the rasterizing, the ink measurement, normalization, both renderers — goes on setting
 * bounds to the box and needs to know nothing about this. What it wraps simply draws bigger than what it is given.
 *
 * The fraction is the platform's own ([AdaptiveIconDrawable.getExtraInsetFraction], a quarter), not a number chosen
 * here: it is the same constant Android masks by, so the result is what the system would have drawn.
 *
 * **A flat color is handed back untouched**, which is exactness rather than an optimization: expanding a color
 * changes nothing about what it draws, and wrapping it would hide the [ColorDrawable] from [toParsedLayer] — so an
 * adaptive icon's plain plate would stop being a [ParsedLayer.Color] and become a rasterised image of one.
 */
private fun Drawable.overshot(): Drawable =
    if (this is ColorDrawable) this else InsetDrawable(this, -AdaptiveIconDrawable.getExtraInsetFraction())

/** A [ColorDrawable] becomes a flat [ParsedLayer.Color]; every other drawable becomes an [ParsedLayer.Image]. */
private fun Drawable.toParsedLayer(
    resources: Resources,
    measured: Boolean = false,
    label: String? = null,
    role: String = "",
): ParsedLayer = when (this) {
    is ColorDrawable -> ParsedLayer.Color(color)
    else -> if (measured) rasterized(resources, label, role) else ParsedLayer.Image(this)
}

/**
 * The layer rendered **once, to a bitmap**, together with how much of the box that bitmap's ink covers.
 *
 * **A drawable is not obliged to look the same at two sizes, and that is what broke normalization twice.** The whole
 * measure-once-scale-everywhere design rests on "what fraction of the box is ink" being a property of the artwork.
 * It is not, for anything carrying *absolute* padding: an `InsetDrawable` with dp insets — the ordinary way an app
 * ships a themed layer with a margin — subtracts the same pixel count whatever bounds it is handed, so its artwork
 * covers proportionally *more* of a large box than a small one. Reddit's monochrome layer insets ~25px a side. In a
 * 64px sample that left 8px of artwork and normalization magnified the icon eight times; measuring at 192 fixed the
 * magnitude but not the shape of the problem, because the studio and a drawer cell still draw at different sizes and
 * so still disagreed — the same icon correct in the grid and overflowing in the editor.
 *
 * **Rasterizing settles it by construction.** A `BitmapDrawable` scales proportionally, so once the artwork is a
 * bitmap its ink fraction is the same at every size: the measurement matches what is drawn, and both renderers match
 * each other, whatever they are drawing into. It also generalizes — nine-patches and anything else with fixed padding
 * are fixed by the same move rather than each needing to be recognized.
 *
 * What that *chooses* is which appearance is canonical, and it is the one at [sampleBoxSize] — the drawable's own
 * intrinsic size, clamped, which is where its absolute padding means what its author meant. The choice mostly washes
 * out when normalization is on (the artwork is rescaled to fill the box anyway) and matters when it is off, which is
 * why the clamp lands near the size an icon is really displayed at.
 *
 * **Rasterized at [RasterOversample]× the resolution, at the same appearance**, via a canvas scale rather than larger
 * bounds — so the drawable still lays itself out for a [sampleBoxSize] box while the pixels are captured at twice
 * that. Without it the studio, which draws far larger than a drawer cell, would show a visibly soft preview; with it
 * the canonical appearance and the resolution stop being the same decision.
 *
 * Artwork painted outside the bounds is clipped here, which is no loss: both renderers draw a layer into a bitmap of
 * exactly the box, so they clip it too. A drawable that draws **nothing** keeps its original rather than freezing a
 * blank raster, since it may yet draw at some other size.
 */
private fun Drawable.rasterized(resources: Resources, label: String?, role: String): ParsedLayer {
    val boxSize = sampleBoxSize()
    val rasterSize = boxSize * RasterOversample
    val bitmap = createBitmap(rasterSize, rasterSize)

    val canvas = Canvas(bitmap)
    canvas.scale(RasterOversample.toFloat(), RasterOversample.toFloat())
    // Bounds are set rather than trusted: a drawable carries whatever the last renderer left on it.
    setBounds(0, 0, boxSize, boxSize)
    draw(canvas)

    val pixels = IntArray(rasterSize * rasterSize)
    bitmap.getPixels(pixels, 0, rasterSize, 0, 0, rasterSize, rasterSize)
    // Fractions of the raster are fractions of the box: the raster *is* the box, at a higher resolution.
    val metrics = ContentMetrics.of(pixels, rasterSize)
    logMeasurement(this, label, role, metrics, pixels, rasterSize, boxSize)

    if (metrics == null) {
        bitmap.recycle()
        return ParsedLayer.Image(this)
    }
    return ParsedLayer.Image(bitmap.toDrawable(resources), metrics)
}

/**
 * The square this drawable is rasterized in: its own intrinsic size, clamped to [MinSampleSize]..[MaxSampleSize].
 *
 * The intrinsic size is the one at which a drawable's *absolute* padding means what its author meant — for an
 * `InsetDrawable` it is literally child-plus-insets — so this is what makes a dp inset a margin rather than a
 * crusher. The larger of the two axes, because the box is square and the smaller axis must fit inside it.
 */
private fun Drawable.sampleBoxSize(): Int {
    val intrinsic = maxOf(intrinsicWidth, intrinsicHeight)
    return if (intrinsic <= 0) MaxSampleSize else intrinsic.coerceIn(MinSampleSize, MaxSampleSize)
}

/**
 * What the measurement saw, for a wrong size that cannot be diagnosed from its own output.
 *
 * **A wrong scale looks like a wrong icon, and every cause of one looks like every other** — bleed, a stray pixel, a
 * drawable that rendered nothing, artwork drawn inverted. They differ by a factor of twenty, but on screen they are
 * all just "that icon is the wrong size", which is how four attempts at this were each found late and by eye.
 *
 * **Coverage is the number that separates them**, and it is here rather than on [ContentMetrics] deliberately: that
 * type dropped coverage on purpose, because the *fit* is a bounds question and nothing should be tempted to make a
 * decision from this. What it answers is "how much of that measured box is actually ink" — a solid glyph is 30–60%,
 * while a handful of stray pixels, or artwork that came out as its own negative, is a few percent inside bounds that
 * claim to be much larger.
 *
 * Silent unless a tree is planted, which is debug builds only ([Timber.treeCount]) — checked before the strings are
 * built, so a release build does not pay to format a message nobody reads. The silhouette is dumped only for a
 * measurement small enough to be implausible, so an ordinary device logs one line per layer and the suspect ones
 * draw themselves.
 */
private fun logMeasurement(
    drawable: Drawable,
    label: String?,
    role: String,
    metrics: ContentMetrics?,
    pixels: IntArray,
    rasterSize: Int,
    boxSize: Int,
) {
    if (Timber.treeCount == 0) return

    val who = "${label ?: "?"} $role ${drawable.javaClass.simpleName}"
    if (metrics == null) {
        Timber.tag(MeasureTag).d("%s -> nothing drawn (no ink above alpha %d)", who, ContentMetrics.AlphaFloor)
        return
    }

    var ink = 0
    for (pixel in pixels) if ((pixel ushr 24) > ContentMetrics.AlphaFloor) ink++
    val coverage = ink.toFloat() / (rasterSize * rasterSize)

    // The sample size is logged because it is now per drawable, and it is the first thing to check when a
    // measurement looks wrong: a box far from the drawable's real size is how this went wrong before.
    Timber.tag(MeasureTag).d(
        "%s -> side=%.3f box=[%.2f,%.2f,%.2f,%.2f] coverage=%.1f%% scale=%.2f sample=%dpx intrinsic=%dpx",
        who, metrics.longestSide, metrics.left, metrics.top, metrics.right, metrics.bottom,
        coverage * 100f, 1f / metrics.longestSide, boxSize, maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight),
    )

    if (metrics.longestSide < ImplausibleSide) {
        Timber.tag(MeasureTag).d(
            "%s measured implausibly small — what was actually drawn:\n%s",
            who,
            silhouette(pixels, rasterSize),
        )
    }
}

/**
 * The rasterized layer as text: `#` is ink, `.` is empty. The raster is exactly the icon box, so the grid's edges
 * are the box's edges.
 *
 * Sampled down to something a terminal can hold, at half as many rows as columns, because text cells are about twice
 * as tall as they are wide and a silhouette squashed to half height is harder to recognize than one at the right
 * shape.
 */
private fun silhouette(pixels: IntArray, rasterSize: Int): String = buildString {
    for (row in 0 until SilhouetteRows) {
        val y = row * rasterSize / SilhouetteRows
        for (column in 0 until SilhouetteColumns) {
            val x = column * rasterSize / SilhouetteColumns
            append(if ((pixels[y * rasterSize + x] ushr 24) > ContentMetrics.AlphaFloor) '#' else '.')
        }
        append('\n')
    }
}

/** One tag for every measurement line, so `logcat -s` shows this and nothing else. */
private const val MeasureTag = "IconMeasure"

/** Below this fraction of the box, artwork is too small to be a real icon — so something went wrong, not small. */
private const val ImplausibleSide = 0.3f

private const val SilhouetteColumns = 64
private const val SilhouetteRows = 32

/**
 * The smallest box a drawable is measured in. Absolute (dp) padding is a fixed pixel count whatever the bounds, so
 * below some size it stops being a margin and starts being most of the box — which is the bug this floor exists for.
 */
private const val MinSampleSize = 96

/**
 * The largest box a drawable is measured in — about a launcher icon at the highest common density, so a drawable is
 * measured at roughly the size it is drawn at. Beyond this the scan grows with the square for precision no eye can
 * see, and the whole measurement is only ever a ratio.
 */
private const val MaxSampleSize = 192

/**
 * How many raster pixels per box pixel. Applied as a canvas scale, so it buys **resolution without changing the
 * appearance** — the drawable still lays out for a [MaxSampleSize]-ish box. The studio draws an icon far larger than
 * a drawer cell does, and 1× here would show there as a soft preview.
 */
private const val RasterOversample = 2
