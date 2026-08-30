package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.Serializable

/**
 * An ordered set of opaque-or-translucent colors a generator paints from — the wallpaper studio's unit of color.
 *
 * **A plain ordered list, not a sampler.** How a color is *read* — nearest stop, perceptual interpolation between
 * stops, an assignment of stops to shapes — is a rendering choice that differs per generator and belongs in
 * `core:graphics`, not here. This layer only holds the colors and their order, so it stays pure Kotlin and
 * serializable, the same split `core:model` keeps everywhere else (data here, the arithmetic that can be silently
 * wrong one module out).
 *
 * **Colors are packed ARGB `Int`s and carry their alpha.** Unlike an icon tint — where `MorphicColorPicker` drops
 * alpha because the layer already has an opacity — a wallpaper palette legitimately holds translucent colors, since
 * a generator like *Soft Overlaps* blends discs by their own alpha. So the alpha byte is meaningful here and is not
 * thrown away.
 *
 * @property colors the stops, in order, light-to-dark by convention (the order a gradient climbs and the order the
 *   picker's strip shows). May be any length of one or more; a generator decides what to do with however many it is
 *   given.
 */
@Serializable
data class Palette(val colors: List<Int>) {

    /** How many stops this palette has. */
    val size: Int get() = colors.size

    /**
     * The stop at [index], **clamped** into range — so an out-of-range index reads the nearest end rather than
     * throwing. A generator indexing shapes into a palette must never crash on a palette shorter than it expected,
     * and a stored recipe is not obliged to have a sensible count.
     */
    fun colorAt(index: Int): Int = colors[index.coerceIn(0, size - 1)]

    companion object {

        /**
         * A safe fallback for a recipe that somehow has no colors — one mid gray, so nothing downstream reads an
         * empty list.
         */
        val Fallback = Palette(listOf(0xFF808080.toInt()))
    }
}
