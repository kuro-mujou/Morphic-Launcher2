package inkspire.morphic.core.model.wallpaper

/**
 * How bright the displayed wallpaper is **at each place on it** — mean relative luminance per cell, `0f..1f`.
 *
 * **A grid because one number cannot describe a picture that text sits on.** A bright sky over dark water averages to a
 * mid-gray, and whichever way that is read, the labels over the other half are unreadable. Chrome asks this map about
 * the rectangle it actually occupies instead.
 *
 * Cells run left to right and top to bottom in the **picture's own proportions**, so a screen rectangle is found in it
 * by the same center-crop the frosted backdrop finds its crop with — which is what keeps text and frost reading one
 * picture. Each cell is the mean of its pixels' luminances, not the luminance of their mean color: luminance is
 * gamma-expanded, and the two readings disagree exactly where it matters, on a cell that is half black and half white.
 *
 * Equality is by content, so measuring an unchanged picture again is not a change.
 */
class LuminanceMap(val columns: Int, val rows: Int, private val values: FloatArray) {

    init {
        require(columns > 0 && rows > 0 && values.size == columns * rows) {
            "A $columns x $rows map needs ${columns * rows} values, got ${values.size}"
        }
    }

    /** The luminance of the cell at [column], [row]. */
    operator fun get(column: Int, row: Int): Float = values[row * columns + column]

    /** The whole picture's mean — what a surface blurred across all of it (the film) has to contrast. */
    val mean: Float = values.average().toFloat()

    /** Every cell, row-major, as a copy the caller may reorder. */
    fun toFloatArray(): FloatArray = values.copyOf()

    override fun equals(other: Any?): Boolean =
        other is LuminanceMap && columns == other.columns && rows == other.rows && values.contentEquals(other.values)

    override fun hashCode(): Int = 31 * (31 * columns + rows) + values.contentHashCode()
}

/**
 * What the launcher can know about the brightness of the wallpaper its chrome sits on.
 *
 * **Two kinds of answer, and they are not two precisions of one.** When the launcher can prove the picture on screen is
 * a file it holds, it measures that file, spot by spot. When it cannot — a wallpaper another app set, a live wallpaper
 * that is not ours — the only thing on offer is the system's whole-picture verdict, which cannot say *where* anything is
 * and so can only answer for the screen at once.
 */
sealed interface WallpaperBrightness {

    /** Measured from the displayed picture itself. */
    data class Measured(val map: LuminanceMap) : WallpaperBrightness

    /**
     * The system's verdict on a picture the launcher cannot read.
     *
     * @property supportsDarkText `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`: bright on average **and** almost no dark
     *   area. Deliberately that and not a color's luminance — a picture that is half dark never earns it, which is the
     *   property that makes it safe to theme a whole screen by.
     */
    data class Reported(val supportsDarkText: Boolean) : WallpaperBrightness
}
