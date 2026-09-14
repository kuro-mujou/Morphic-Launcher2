package inkspire.morphic.data.wallpaper.internal

import inkspire.morphic.core.model.wallpaper.LuminanceMap
import kotlin.math.pow
import kotlin.math.roundToInt

/** Rec. 709 luminance weights — the ones WCAG's relative luminance, and Compose's `Color.luminance`, use. */
private const val RedWeight = 0.2126f
private const val GreenWeight = 0.7152f
private const val BlueWeight = 0.0722f

/** Where each channel sits in a packed ARGB `Int`. */
private const val RedShift = 16
private const val GreenShift = 8
private const val ChannelMask = 0xFF

/** Each 8-bit sRGB channel value linearized — a table, because the curve is a `pow` and a wallpaper is 10⁵ pixels. */
private val LinearChannel = FloatArray(256) { value ->
    val c = value / 255.0
    (if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)).toFloat()
}

/**
 * [argb], a [width] × [height] image stored row-major, reduced to a [LuminanceMap] [columns] cells wide.
 *
 * Rows follow the image's proportions. Both counts are capped at the image's own size, so every cell covers at least
 * one pixel and no mean divides by zero. Alpha is ignored: a wallpaper is opaque.
 */
internal fun luminanceMapOf(argb: IntArray, width: Int, height: Int, columns: Int): LuminanceMap {
    val cols = columns.coerceIn(1, width)
    val rows = (cols.toFloat() * height / width).roundToInt().coerceIn(1, height)
    val sums = FloatArray(cols * rows)
    val counts = IntArray(cols * rows)
    for (y in 0 until height) {
        val rowBase = (y * rows / height) * cols
        val pixelBase = y * width
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = argb[pixelBase + x]
            sums[cell] += RedWeight * LinearChannel[pixel shr RedShift and ChannelMask] +
                GreenWeight * LinearChannel[pixel shr GreenShift and ChannelMask] +
                BlueWeight * LinearChannel[pixel and ChannelMask]
            counts[cell]++
        }
    }
    return LuminanceMap(cols, rows, FloatArray(sums.size) { sums[it] / counts[it] })
}
