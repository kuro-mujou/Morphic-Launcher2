package inkspire.morphic.core.common.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/**
 * A picture the user picked, decoded no larger than it needs to be — the one step the launcher's two image imports
 * (icon layers and widget pictures) share, and the one that is expensive to get wrong: decoding a 12-megapixel photo
 * whole makes a 48 MB bitmap on its way to being a few hundred pixels.
 *
 * Sampled **during** the decode, by the largest power of two that still leaves at least [atLeast] pixels on the
 * longest edge; the caller scales the rest of the way to its exact size.
 *
 * @return null when [uri] cannot be read or is not an image. Blocks on I/O, so call it off the main thread.
 */
fun decodeSampled(context: Context, uri: Uri, atLeast: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longest = max(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longest, atLeast) }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** The largest power-of-two reduction that still leaves at least [atLeast] on a [longestEdge]. */
internal fun sampleSizeFor(longestEdge: Int, atLeast: Int): Int {
    var sample = 1
    while (longestEdge / (sample * 2) >= atLeast) sample *= 2
    return sample
}
