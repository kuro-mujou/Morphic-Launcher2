package inkspire.morphic.core.icon.parse

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Splits a platform [Drawable] into a [ParsedIcon] (foreground / background? / monochrome?).
 *
 * Pure analysis: no shaping, scaling, or compositing — those belong to the renderer. Kept a concrete class
 * (not an interface): the analysis is single-strategy, so an interface would be a speculative abstraction;
 * if the renderer ever needs to fake it, extract one then.
 *
 * An [AdaptiveIconDrawable] contributes its own foreground and background (and, on Android 13+, its optional
 * monochrome layer). Any other drawable is a **legacy** icon: the whole drawable becomes the foreground and
 * there is no background (`null`) — we never try to cut a glyph out of a flat legacy bitmap (unreliable).
 * Deriving a solid background colour for legacy icons (edge-pixel sampling) is a separate, later slice.
 */
class DrawableParser {

    fun parse(drawable: Drawable): ParsedIcon = when (drawable) {
        is AdaptiveIconDrawable -> ParsedIcon(
            // getForeground()/getBackground() are nullable in the SDK; fall back / stay legacy if absent.
            foreground = (drawable.foreground ?: drawable).toParsedLayer(),
            background = drawable.background?.toParsedLayer(),
            monochrome = drawable.monochromeOrNull()?.toParsedLayer(),
        )

        else -> ParsedIcon(foreground = drawable.toParsedLayer())
    }
}

/** The adaptive monochrome (themed-icon) layer, or `null` below Android 13 or when the icon exposes none. */
private fun AdaptiveIconDrawable.monochromeOrNull(): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) monochrome else null

/** A [ColorDrawable] becomes a flat [ParsedLayer.Color]; every other drawable becomes an [ParsedLayer.Image]. */
private fun Drawable.toParsedLayer(): ParsedLayer = when (this) {
    is ColorDrawable -> ParsedLayer.Color(color)
    else -> ParsedLayer.Image(this)
}
