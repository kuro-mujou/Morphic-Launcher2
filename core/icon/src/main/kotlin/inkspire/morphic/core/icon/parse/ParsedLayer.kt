package inkspire.morphic.core.icon.parse

import android.graphics.drawable.Drawable

/**
 * The content of one parsed icon layer — the only distinction that changes how a layer is drawn: a flat
 * [Color] fill, or an [Image] whose pixels come from a [Drawable].
 *
 * We deliberately collapse the platform's drawable zoo (bitmap / vector / adaptive sub-layer / anything else)
 * into a single [Image] case. They all render the same way — draw the drawable onto the canvas — so tagging
 * them apart would be a distinction without a behavioural difference. Only "is it a solid colour?" affects
 * compositing, so that is the only split we keep. (The reference splits `Bitmap`/`Vector`/`Other` apart but
 * then treats all three identically; we skip that dead granularity.)
 */
sealed interface ParsedLayer {

    /** A flat colour fill (e.g. an adaptive icon's solid background). [argb] is a packed ARGB colour. */
    data class Color(val argb: Int) : ParsedLayer

    /** Pixels drawn from [drawable] — a bitmap, vector, adaptive sub-layer, or any other drawable type. */
    data class Image(val drawable: Drawable) : ParsedLayer
}
