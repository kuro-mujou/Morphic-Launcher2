package inkspire.morphic.core.icon

import inkspire.morphic.core.model.icon.IconPattern

/**
 * The catalog of built-in [IconPattern]s and the vector drawable each resolves to. Adding one is: drop a
 * `pattern_*.xml` vector into `res/drawable`, add an entry here, and list it in [All].
 *
 * **`IconShapes`' pipeline, and deliberately not its list** — see [IconPattern] for why the two libraries stay
 * apart. Pattern ids are the stable on-disk contract; the `R.drawable` names are an internal detail mapped here, so
 * a drawable can be renamed without touching persisted data.
 *
 * **Every tile is authored to repeat.** A mark crossing an edge is drawn again on the opposite one — or, where it
 * is easier, drawn whole and centred on the edge so the drawable clips it and the neighbour completes it. Getting
 * that wrong does not fail: it produces a visible seam every tile, which is the sort of thing that reads as a
 * rendering fault rather than as a bad asset.
 *
 * **The marks are white and the ground transparent**, always. The renderer tints the alpha, so a tile carrying its
 * own colour would come out wrong the moment a user picked one.
 */
object IconPatterns {
    val Dots = IconPattern("dots")
    val Grid = IconPattern("grid")
    val Stripes = IconPattern("stripes")
    val Checks = IconPattern("checks")
    val Waves = IconPattern("waves")
    val Crosses = IconPattern("crosses")

    /** Every built-in pattern, in picker order. */
    val All = listOf(Dots, Grid, Stripes, Checks, Waves, Crosses)

    /** The vector drawable backing [pattern], or `null` for an unknown id (e.g. a recipe from a later build). */
    fun drawableResOrNull(pattern: IconPattern): Int? = when (pattern) {
        Dots -> R.drawable.pattern_dots
        Grid -> R.drawable.pattern_grid
        Stripes -> R.drawable.pattern_stripes
        Checks -> R.drawable.pattern_checks
        Waves -> R.drawable.pattern_waves
        Crosses -> R.drawable.pattern_crosses
        else -> null
    }
}
