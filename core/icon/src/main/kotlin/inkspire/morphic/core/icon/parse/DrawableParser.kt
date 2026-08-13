package inkspire.morphic.core.icon.parse

import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap

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
class DrawableParser {

    fun parse(drawable: Drawable): ParsedIcon = when (drawable) {
        is AdaptiveIconDrawable -> ParsedIcon(
            // getForeground()/getBackground() are nullable in the SDK; fall back / stay legacy if absent.
            foreground = (drawable.foreground ?: drawable).toParsedLayer(measured = true),
            // **The background is deliberately not measured, and not normalized.** Its ink legitimately covers the
            // whole canvas — that is what a plate is — so a measurement would say "full" and mean nothing. Leaving
            // it untouched is also a decision rather than an omission: with no mask, rescaling a plate would crop it
            // to a square, which is a visible change nobody asked for.
            background = drawable.background?.toParsedLayer(),
            monochrome = drawable.monochromeOrNull()?.toParsedLayer(measured = true),
        )

        else -> ParsedIcon(
            foreground = drawable.toParsedLayer(measured = true),
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

/** A [ColorDrawable] becomes a flat [ParsedLayer.Color]; every other drawable becomes an [ParsedLayer.Image]. */
private fun Drawable.toParsedLayer(measured: Boolean = false): ParsedLayer = when (this) {
    is ColorDrawable -> ParsedLayer.Color(color)
    else -> ParsedLayer.Image(this, metrics = if (measured) measureContent(this) else null)
}

/**
 * How much of its canvas [drawable] covers, or null if it is empty.
 *
 * Rasterized at [ContentSampleSize] rather than at the icon's real size: the question is "what fraction of this
 * canvas is ink", and a thumbnail answers it to well under a percent for a fraction of the allocation. L1 measured
 * at full size, scanning every pixel of a full bitmap on every bake; that accuracy is invisible and the cost is not.
 */
private fun measureContent(drawable: Drawable): ContentMetrics? {
    val bitmap = createBitmap(ContentSampleSize, ContentSampleSize)
    try {
        // Bounds are set rather than trusted: a drawable carries whatever the last renderer left on it.
        drawable.setBounds(0, 0, ContentSampleSize, ContentSampleSize)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(ContentSampleSize * ContentSampleSize)
        bitmap.getPixels(pixels, 0, ContentSampleSize, 0, 0, ContentSampleSize, ContentSampleSize)
        return ContentMetrics.of(pixels, ContentSampleSize)
    } finally {
        bitmap.recycle()
    }
}

/** Fine enough to place an edge and an area to well under a percent, small enough to be free on every parse. */
private const val ContentSampleSize = 64
