package inkspire.morphic.core.icon

/**
 * The catalog of built-in [IconShape]s and the vector drawable each resolves to. Adding a shape is: drop a
 * `shape_*.xml` vector into `res/drawable`, add an entry here, and (if it should be pickable) list it in
 * [All]. The renderer builds a clip mask from the resolved drawable's silhouette.
 *
 * Shape [IconShape.id]s are the stable on-disk contract; the `R.drawable` resource names are an internal
 * detail mapped here, so a drawable can be renamed without touching persisted data.
 */
object IconShapes {
    val Circle = IconShape("circle")
    val Square = IconShape("square")
    val RoundedSquare = IconShape("rounded_square")
    val Diamond = IconShape("diamond")
    val Hexagon = IconShape("hexagon")
    val Teardrop = IconShape("teardrop")
    val Triangle = IconShape("triangle")

    /** Fallback shape when none is chosen. */
    val Default = Circle

    /** Every built-in shape, in picker order. */
    val All = listOf(Circle, Square, RoundedSquare, Diamond, Hexagon, Teardrop, Triangle)

    /** The vector drawable backing [shape], or `null` for an unknown id (e.g. stale persisted data). */
    fun drawableResOrNull(shape: IconShape): Int? = when (shape) {
        Circle -> R.drawable.shape_circle
        Square -> R.drawable.shape_square
        RoundedSquare -> R.drawable.shape_rounded_square
        Diamond -> R.drawable.shape_diamond
        Hexagon -> R.drawable.shape_hexagon
        Teardrop -> R.drawable.shape_teardrop
        Triangle -> R.drawable.shape_triangle
        else -> null
    }
}
