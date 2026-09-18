package inkspire.morphic.core.model.widget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a widget layer draws. A group is a source too — [Overlap] and [Stack] hold layers — so one type covers leaves
 * and containers, and a recipe is a tree of [WidgetLayerSpec]s.
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
     * @property colorGlobal a [WidgetGlobal.Color] that decides [color] instead, when it names one.
     * @property sizeGlobal a [WidgetGlobal.Number] that decides [size] instead.
     * @property fontGlobal a [WidgetGlobal.Font] that decides [font] instead.
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
        val colorGlobal: String? = null,
        val sizeGlobal: String? = null,
        val fontGlobal: String? = null,
    ) : WidgetSource {

        /** The family, as a choice among the platform's own — there is no font import yet. */
        @Serializable
        enum class Font { SANS, SERIF, MONO }

        /** Where the lines sit inside the layer, which only matters once the layer is wider than its text. */
        @Serializable
        enum class Align { LEFT, CENTER, RIGHT }
    }

    /**
     * A filled shape, stretched to the layer's box — and, with a [picture], a picture cropped to that shape. A
     * widget's background card is one, which is why a picture is a fill here rather than a layer of its own: the
     * card's corners, and the setting that rounds them, clip it for free.
     *
     * With a picture, [color] is drawn **over** it at the picture's tint rather than at its own alpha, so the one
     * "Background" color stays meaningful either way: a card color without a picture, a wash over one with.
     *
     * @property cornerRadius in dp, for [Kind.RECTANGLE]; an [Kind.OVAL] has no corners.
     * @property colorGlobal a [WidgetGlobal.Color] that decides [color] instead, when it names one.
     * @property cornerRadiusGlobal a [WidgetGlobal.Number] that decides [cornerRadius] instead.
     * @property pictureGlobal a [WidgetGlobal.Picture] that decides [picture] instead — its having none included.
     */
    @Serializable
    @SerialName("shape")
    data class Shape(
        val kind: Kind = Kind.RECTANGLE,
        val color: Int = White,
        val cornerRadius: Float = 0f,
        val colorGlobal: String? = null,
        val cornerRadiusGlobal: String? = null,
        val picture: WidgetPicture? = null,
        val pictureGlobal: String? = null,
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
     * How far a formula's number sits between [min] and [max], drawn as a filled share of a track — a battery ring, a
     * day's progress bar. The value is a formula like a [Text]'s, so what is measured is the design's choice, not a
     * list of presets.
     *
     * A [value] that does not evaluate to a number draws the track alone, as does one at or below [min].
     *
     * @property value literal text with `$…$` formulas, read as a number once evaluated — `$bi(level)$`.
     * @property thickness in dp, the [Kind.ARC]'s stroke. A [Kind.BAR] is its layer's whole box.
     * @property startAngle degrees clockwise from twelve o'clock, where an arc's fill begins.
     * @property sweep degrees clockwise the full track covers: 360 is a ring, 270 a gauge.
     * @property rounded round caps on an arc; pill ends on a bar, with the fill clipped to them.
     * @property colorGlobal a [WidgetGlobal.Color] that decides [color] instead, when it names one.
     * @property trackColorGlobal a [WidgetGlobal.Color] that decides [trackColor] instead.
     */
    @Serializable
    @SerialName("progress")
    data class Progress(
        val value: String,
        val kind: Kind = Kind.BAR,
        val min: Float = 0f,
        val max: Float = 100f,
        val color: Int = White,
        val trackColor: Int = FaintWhite,
        val thickness: Float = 6f,
        val startAngle: Float = 0f,
        val sweep: Float = 360f,
        val rounded: Boolean = true,
        val colorGlobal: String? = null,
        val trackColorGlobal: String? = null,
    ) : WidgetSource {

        /** A bar fills left to right across its box; an arc fills clockwise around the largest circle that fits. */
        @Serializable
        enum class Kind { BAR, ARC }
    }

    /**
     * A group whose layers are placed freely inside it, each by its own anchor and offset, later layers drawn over
     * earlier ones. A recipe's own layers are this, one level up.
     *
     * With a content extent, a group is exactly as big as what its layers cover — offsets included — so a block's
     * box is what it draws, which is what selecting it on the canvas hits.
     *
     * @property globals settings this group owns, in a scope of their own: bindings and `gv` inside it read these
     *   before the recipe's, so two copies of one block keep separate settings under the same names. A block's
     *   parameters live here.
     */
    @Serializable
    @SerialName("overlap")
    data class Overlap(
        val layers: List<WidgetLayerSpec> = emptyList(),
        val globals: List<WidgetGlobal> = emptyList(),
    ) : WidgetSource

    /**
     * A group whose layers follow one another along an [axis] — "icon, label, value", a column of lines — which
     * overlapping cannot express: a second line cannot sit under a first whose height depends on its text.
     *
     * **The stack places its layers, so their own anchors and offsets are not used.** A layer's size still comes from
     * its extents, measured against the stack; [align] decides where each sits across the axis.
     *
     * @property spacing in dp, between one layer and the next.
     */
    @Serializable
    @SerialName("stack")
    data class Stack(
        val layers: List<WidgetLayerSpec> = emptyList(),
        val axis: Axis = Axis.VERTICAL,
        val spacing: Float = 0f,
        val align: Align = Align.START,
    ) : WidgetSource {

        /** Down the page, or across it. */
        @Serializable
        enum class Axis { VERTICAL, HORIZONTAL }

        /** Where a layer sits across the axis: its top or left edge, the middle, or its bottom or right edge. */
        @Serializable
        enum class Align { START, CENTER, END }
    }
}

/**
 * An imported picture filling a [WidgetSource.Shape], with the share of the shape's color washed over it.
 *
 * @property path the stored copy, as `WidgetImageStore` wrote it.
 * @property tint 0–1: how much of the shape's color covers the picture — what keeps white text readable over a bright
 *   photo. The color's own alpha is not used, since the picker that sets it always hands back an opaque one.
 */
@Serializable
data class WidgetPicture(val path: String, val tint: Float = DefaultTint) {
    companion object {
        /** Enough to read white text over most photos while still showing them. */
        const val DefaultTint = 0.35f
    }
}

/** The layers this source holds, when it is a group of either kind; null for a leaf. */
val WidgetSource.children: List<WidgetLayerSpec>?
    get() = when (this) {
        is WidgetSource.Overlap -> layers
        is WidgetSource.Stack -> layers
        is WidgetSource.Text, is WidgetSource.Shape, is WidgetSource.Image, is WidgetSource.Progress -> null
    }

/** This group holding [layers] instead; a leaf, which holds none, is returned unchanged. */
fun WidgetSource.withChildren(layers: List<WidgetLayerSpec>): WidgetSource = when (this) {
    is WidgetSource.Overlap -> copy(layers = layers)
    is WidgetSource.Stack -> copy(layers = layers)
    is WidgetSource.Text, is WidgetSource.Shape, is WidgetSource.Image, is WidgetSource.Progress -> this
}

private const val White = 0xFFFFFFFF.toInt()
private const val FaintWhite = 0x33FFFFFF
