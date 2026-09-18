package inkspire.morphic.core.model.widget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a widget layer draws. A group is a source too — [Overlap] holds layers — so one type covers leaves and
 * containers, and a recipe is a tree of [WidgetLayerSpec]s.
 *
 * The [SerialName]s are the stored contract. A kind this build has never heard of **throws** on decode rather than
 * being skipped, so whatever reads a stored recipe must catch and drop that one recipe, as the icon and wallpaper
 * stores already do for their own sealed types.
 *
 * Colors are ARGB, as `Palette`'s are.
 */
@Serializable
sealed interface WidgetSource {

    /**
     * A line of text, live when it holds formulas.
     *
     * @property text what `core:widgetscript` parses — literal text with `$…$` formulas in it. Stored as typed, never
     *   as a parse tree, so the grammar can change without a migration.
     * @property size in dp, not sp: a widget is a composition someone laid out, and the system font scale enlarging
     *   one line of it would push that line out of the design.
     * @property weight 100–900, the CSS scale Compose's `FontWeight` takes.
     * @property maxLines past this, the text is cut with an ellipsis.
     */
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val size: Float = 16f,
        val color: Int = White,
        val font: Font = Font.SANS,
        val weight: Int = 400,
        val align: Align = Align.LEFT,
        val maxLines: Int = 1,
    ) : WidgetSource {

        /** The family, as a choice among the platform's own — there is no font import yet. */
        @Serializable
        enum class Font { SANS, SERIF, MONO }

        /** Where the lines sit inside the layer, which only matters once the layer is wider than its text. */
        @Serializable
        enum class Align { LEFT, CENTER, RIGHT }
    }

    /**
     * A filled shape, stretched to the layer's box.
     *
     * @property cornerRadius in dp, for [Kind.RECTANGLE]; an [Kind.OVAL] has no corners.
     */
    @Serializable
    @SerialName("shape")
    data class Shape(
        val kind: Kind = Kind.RECTANGLE,
        val color: Int = White,
        val cornerRadius: Float = 0f,
    ) : WidgetSource {

        @Serializable
        enum class Kind { RECTANGLE, OVAL }
    }

    /** A picture the user imported, stored at [path]. */
    @Serializable
    @SerialName("image")
    data class Image(val path: String, val fit: Fit = Fit.CROP) : WidgetSource {

        /** How the picture meets a box of another shape: fill it and crop, or fit inside it and leave margins. */
        @Serializable
        enum class Fit { CROP, FIT }
    }

    /**
     * A group whose layers are placed freely inside it, each by its own anchor and offset, later layers drawn over
     * earlier ones. A recipe's own layers are this, one level up.
     */
    @Serializable
    @SerialName("overlap")
    data class Overlap(val layers: List<WidgetLayerSpec> = emptyList()) : WidgetSource
}

private const val White = 0xFFFFFFFF.toInt()
