package inkspire.morphic.core.icon

import inkspire.morphic.core.icon.render.ColorMatrices
import inkspire.morphic.core.icon.render.ColorMatrices.then
import inkspire.morphic.core.model.icon.IconFilter

/**
 * The built-in colour looks, and the matrix each resolves to.
 *
 * **`IconShapes`' shape, for `IconShapes`' reason.** Adding a filter is: compose a matrix, give it an id and a
 * name, list it. The [IconFilter.id]s are the stable on-disk contract; the labels and the grouping are
 * presentation and can be reworded freely.
 *
 * **Names describe the look, never a person or a film.** A filter's name is shipped, stored and user-visible, so
 * borrowing one — the reference this was drawn from has a "Tarantino" — makes the launcher's vocabulary depend on
 * somebody else's trademark for no gain in clarity.
 *
 * **Authored by composing [ColorMatrices] rather than by writing twenty numbers.** `saturation(0.85).then(
 * contrast(1.1)).then(offset(...))` says what a look *is*; a raw `floatArrayOf` says only what it computes, and a
 * table of twenty of those is unreviewable. The composition order matters and reads left to right.
 *
 * **What a matrix can and cannot be, which is the bound on this whole file.** A 4×5 matrix is one linear map over
 * the channels, so a grade, a duotone, an inversion and a channel mix are all expressible and *quantization is
 * not*: a look that snaps colours to a fixed palette — a four-tone handheld screen, an eight-bit console — cannot
 * be one of these however it is written, and would have to be a `LayerEffect` with a per-pixel pass of its own.
 * Where the reference does that, the entry here is the **ramp between the palette's two ends**, which is what a
 * duotone is, rather than a stepped approximation pretending to be the same thing.
 */
object IconFilters {

    /** How the picker groups the list. Presentation only — the stored recipe holds an id and nothing else. */
    enum class Category(val label: String) {
        CINEMATIC("Cinematic"),
        VIVID("Vivid"),
        DUOTONE("Duotone"),
        MONOCHROME("Monochrome"),
        TONAL("Tonal"),
        RETRO("Retro"),
        INVERTED("Inverted"),
    }

    /** One entry: what it is called, where it is filed, and what it does. */
    data class Entry(
        val filter: IconFilter,
        val label: String,
        val category: Category,
        val matrix: FloatArray,
    ) {
        // FloatArray has identity equals, so a data class holding one needs both written out. Compared by id
        // alone, deliberately — two entries with the same id are the bug, whatever their matrices say.
        override fun equals(other: Any?): Boolean = this === other || (other is Entry && other.filter == filter)

        override fun hashCode(): Int = filter.hashCode()
    }

    private fun entry(id: String, label: String, category: Category, matrix: FloatArray) =
        Entry(IconFilter(id), label, category, matrix)

    /**
     * A two-colour ramp from two ARGB literals, which is how every [Category.DUOTONE] entry is authored.
     *
     * The unpacking lives here rather than in [ColorMatrices] for `LayerFilter.solidMatrixOf`'s reason inverted:
     * that module is the arithmetic and takes channels, and a table is far more readable written as the two colours
     * a designer would name. The alpha byte is ignored — a ramp has no opacity of its own, the layer's alpha
     * survives untouched.
     */
    private fun duotone(darkArgb: Int, lightArgb: Int): FloatArray = ColorMatrices.duotone(
        darkR = (darkArgb shr 16 and 0xFF).toFloat(),
        darkG = (darkArgb shr 8 and 0xFF).toFloat(),
        darkB = (darkArgb and 0xFF).toFloat(),
        lightR = (lightArgb shr 16 and 0xFF).toFloat(),
        lightG = (lightArgb shr 8 and 0xFF).toFloat(),
        lightB = (lightArgb and 0xFF).toFloat(),
    )

    /** A grayscale tinted toward one colour — the shape every tinted [Category.MONOCHROME] entry takes. */
    private fun tintedMono(r: Float, g: Float, b: Float, contrast: Float = 1f): FloatArray =
        ColorMatrices.saturation(0f)
            .then(ColorMatrices.contrast(contrast))
            .then(ColorMatrices.scale(r, g, b))

    /** Every built-in, in picker order. Grouped by [Category], which is the order the chips read in. */
    val All: List<Entry> = listOf(
        // --- Cinematic: graded looks, where the shadows and highlights pull different ways ---
        entry(
            "cinematic_warm", "Cinematic Warm", Category.CINEMATIC,
            ColorMatrices.saturation(0.9f)
                .then(ColorMatrices.contrast(1.12f))
                .then(ColorMatrices.scale(1.06f, 1.01f, 0.94f))
                .then(ColorMatrices.offset(6f, 2f, -4f)),
        ),
        entry(
            "cinematic_cool", "Cinematic Cool", Category.CINEMATIC,
            ColorMatrices.saturation(0.88f)
                .then(ColorMatrices.contrast(1.12f))
                .then(ColorMatrices.scale(0.94f, 1.0f, 1.08f))
                .then(ColorMatrices.offset(-4f, 0f, 8f)),
        ),
        entry(
            // The blockbuster grade: cool shadows, warm skin. The lift is negative on blue and positive on red
            // *after* a warm scale, which is what separates the two ends rather than tinting the whole frame.
            "teal_orange", "Teal and Orange", Category.CINEMATIC,
            ColorMatrices.saturation(1.1f)
                .then(ColorMatrices.contrast(1.15f))
                .then(ColorMatrices.scale(1.1f, 0.98f, 0.92f))
                .then(ColorMatrices.offset(-10f, 2f, 14f)),
        ),
        entry(
            "noir_contrast", "Noir Contrast", Category.CINEMATIC,
            ColorMatrices.saturation(0.15f).then(ColorMatrices.contrast(1.45f)),
        ),
        entry(
            // The silver-retention look: colour mostly drained, contrast way up. Half a grade rather than a
            // monochrome, which is the whole point of it — what colour survives reads as accidental.
            "bleach_bypass", "Bleach Bypass", Category.CINEMATIC,
            ColorMatrices.saturation(0.4f).then(ColorMatrices.contrast(1.38f)),
        ),
        entry(
            "golden_hour", "Golden Hour", Category.CINEMATIC,
            ColorMatrices.saturation(1.05f)
                .then(ColorMatrices.scale(1.12f, 1.02f, 0.86f))
                .then(ColorMatrices.offset(10f, 4f, -6f)),
        ),
        entry(
            "moonlight", "Moonlight", Category.CINEMATIC,
            ColorMatrices.saturation(0.78f)
                .then(ColorMatrices.contrast(1.1f))
                .then(ColorMatrices.scale(0.86f, 0.95f, 1.2f))
                .then(ColorMatrices.offset(-6f, -2f, 10f)),
        ),
        entry(
            "emerald_shade", "Emerald Shade", Category.CINEMATIC,
            ColorMatrices.saturation(0.95f)
                .then(ColorMatrices.contrast(1.12f))
                .then(ColorMatrices.scale(0.88f, 1.08f, 0.96f))
                .then(ColorMatrices.offset(-8f, 4f, -2f)),
        ),
        entry(
            "dusty_rose", "Dusty Rose", Category.CINEMATIC,
            ColorMatrices.saturation(0.8f)
                .then(ColorMatrices.contrast(0.95f))
                .then(ColorMatrices.scale(1.08f, 0.96f, 0.99f))
                .then(ColorMatrices.offset(14f, 6f, 9f)),
        ),

        // --- Vivid ---
        entry(
            "vivid_pop", "Vivid Pop", Category.VIVID,
            ColorMatrices.saturation(1.55f).then(ColorMatrices.contrast(1.12f)),
        ),
        entry(
            "candy_bright", "Candy Bright", Category.VIVID,
            ColorMatrices.saturation(1.4f)
                .then(ColorMatrices.scale(1.08f, 1.04f, 1.08f))
                .then(ColorMatrices.offset(12f, 8f, 14f)),
        ),
        entry(
            "neon_glow", "Neon Glow", Category.VIVID,
            ColorMatrices.saturation(1.8f)
                .then(ColorMatrices.contrast(1.2f))
                .then(ColorMatrices.scale(1.08f, 0.94f, 1.14f)),
        ),
        entry(
            "tropical", "Tropical", Category.VIVID,
            ColorMatrices.saturation(1.5f)
                .then(ColorMatrices.scale(1f, 1.08f, 1.05f))
                .then(ColorMatrices.offset(-4f, 6f, 4f)),
        ),
        entry(
            "electric", "Electric", Category.VIVID,
            ColorMatrices.saturation(1.6f)
                .then(ColorMatrices.contrast(1.15f))
                .then(ColorMatrices.scale(0.9f, 1f, 1.2f)),
        ),
        entry(
            "sunburst", "Sunburst", Category.VIVID,
            ColorMatrices.saturation(1.45f)
                .then(ColorMatrices.scale(1.15f, 1.05f, 0.8f))
                .then(ColorMatrices.offset(10f, 4f, -8f)),
        ),

        // --- Duotone: the hue is discarded and the *range* is recoloured, which is what makes a set of icons
        // drawn by different hands read as one set. See `ColorMatrices.duotone` for why this is not a tint ---
        entry("duo_indigo_peach", "Indigo Peach", Category.DUOTONE, duotone(0xFF1B1F5C.toInt(), 0xFFFFCBA4.toInt())),
        entry("duo_teal_sand", "Teal Sand", Category.DUOTONE, duotone(0xFF0C3B3C.toInt(), 0xFFF2E2C4.toInt())),
        entry("duo_plum_gold", "Plum Gold", Category.DUOTONE, duotone(0xFF2E1338.toInt(), 0xFFF6C445.toInt())),
        entry("duo_ink_mint", "Ink Mint", Category.DUOTONE, duotone(0xFF10261F.toInt(), 0xFFA8F0D0.toInt())),
        entry("duo_wine_blush", "Wine Blush", Category.DUOTONE, duotone(0xFF4A0E22.toInt(), 0xFFFFC2CE.toInt())),
        entry("duo_navy_ice", "Navy Ice", Category.DUOTONE, duotone(0xFF0A1B3D.toInt(), 0xFFDCEEFF.toInt())),
        entry("duo_char_amber", "Charcoal Amber", Category.DUOTONE, duotone(0xFF1A1A1A.toInt(), 0xFFFFB347.toInt())),
        entry("duo_forest_lime", "Forest Lime", Category.DUOTONE, duotone(0xFF10250D.toInt(), 0xFFC8F04A.toInt())),

        // --- Monochrome ---
        entry("classic_mono", "Classic Mono", Category.MONOCHROME, ColorMatrices.saturation(0f)),
        entry(
            // A true sepia mixes channels rather than tinting a grey, which is why `scale` cannot express it.
            "warm_sepia", "Warm Sepia", Category.MONOCHROME,
            ColorMatrices.mix(
                0.393f, 0.769f, 0.189f,
                0.349f, 0.686f, 0.168f,
                0.272f, 0.534f, 0.131f,
            ),
        ),
        entry(
            "cool_silver", "Cool Silver", Category.MONOCHROME,
            ColorMatrices.saturation(0f)
                .then(ColorMatrices.contrast(1.1f))
                .then(ColorMatrices.scale(0.95f, 0.99f, 1.08f)),
        ),
        // A grey pulled toward one colour, which is a different thing from a duotone above it: the dark end stays
        // black, so what is tinted is the *midtones*. Five of them, because which one suits an icon is not
        // something a rule can decide.
        entry("mono_mint", "Mint Grey", Category.MONOCHROME, tintedMono(0.86f, 1.06f, 0.98f)),
        entry("mono_ember", "Ember Grey", Category.MONOCHROME, tintedMono(1.16f, 0.86f, 0.84f)),
        entry("mono_rust", "Rust Grey", Category.MONOCHROME, tintedMono(1.12f, 0.92f, 0.74f, contrast = 1.1f)),
        entry("mono_gold", "Gold Grey", Category.MONOCHROME, tintedMono(1.14f, 1f, 0.66f)),
        entry(
            // The hardest reduction here: no midtones to speak of, which is what makes a glyph read as printed.
            "ink_press", "Ink Press", Category.MONOCHROME,
            ColorMatrices.saturation(0f).then(ColorMatrices.contrast(1.85f)),
        ),

        // --- Tonal: no hue opinion, only how the range is spent ---
        entry("high_contrast", "High Contrast", Category.TONAL, ColorMatrices.contrast(1.4f)),
        entry(
            // Lifted blacks with the range pulled in — the matte look, and the one thing contrast alone cannot do.
            "soft_lift", "Soft Lift", Category.TONAL,
            ColorMatrices.contrast(0.85f).then(ColorMatrices.offset(18f, 18f, 20f)),
        ),
        entry(
            "faded_matte", "Faded Matte", Category.TONAL,
            ColorMatrices.saturation(0.7f)
                .then(ColorMatrices.contrast(0.8f))
                .then(ColorMatrices.offset(22f, 20f, 16f)),
        ),
        entry(
            "crushed", "Crushed", Category.TONAL,
            ColorMatrices.contrast(1.7f).then(ColorMatrices.offset(-10f, -10f, -10f)),
        ),
        entry(
            "airy", "Airy", Category.TONAL,
            ColorMatrices.saturation(0.9f)
                .then(ColorMatrices.contrast(0.9f))
                .then(ColorMatrices.offset(26f, 26f, 26f)),
        ),
        entry(
            "deep_shadow", "Deep Shadow", Category.TONAL,
            ColorMatrices.contrast(1.25f).then(ColorMatrices.offset(-18f, -18f, -16f)),
        ),

        // --- Retro ---
        entry(
            "retro_tech", "Retro Tech", Category.RETRO,
            ColorMatrices.saturation(0.6f)
                .then(ColorMatrices.contrast(1.2f))
                .then(ColorMatrices.scale(0.85f, 1.1f, 0.9f))
                .then(ColorMatrices.offset(-6f, 10f, -6f)),
        ),
        entry(
            "sun_faded", "Sun Faded", Category.RETRO,
            ColorMatrices.saturation(0.75f)
                .then(ColorMatrices.contrast(0.9f))
                .then(ColorMatrices.scale(1.1f, 1.02f, 0.88f))
                .then(ColorMatrices.offset(16f, 10f, 4f)),
        ),
        // **The two screen looks are duotones, and that is as close as a matrix gets.** What they imitate is a
        // display with four colours in it, and quantizing to a palette is not a linear operation — no 4×5 matrix
        // can do it, so the honest version is the ramp between the two ends of that palette rather than a
        // stepped approximation that would have to be a whole new kind of effect. Named for what they look like:
        // a filter's name is shipped and stored, so it cannot be somebody's console.
        entry("handheld_green", "Handheld Green", Category.RETRO, duotone(0xFF0F380F.toInt(), 0xFF9BBC0F.toInt())),
        entry("amber_terminal", "Amber Terminal", Category.RETRO, duotone(0xFF1A0F00.toInt(), 0xFFFFB000.toInt())),
        entry(
            "washed_print", "Washed Print", Category.RETRO,
            ColorMatrices.saturation(0.6f)
                .then(ColorMatrices.contrast(0.85f))
                .then(ColorMatrices.scale(1.04f, 1f, 1.06f))
                .then(ColorMatrices.offset(20f, 16f, 22f)),
        ),

        // --- Inverted ---
        entry("negative", "Negative", Category.INVERTED, ColorMatrices.invert()),
        entry(
            "inverted_mono", "Inverted Mono", Category.INVERTED,
            ColorMatrices.saturation(0f).then(ColorMatrices.invert()),
        ),
        entry(
            // Inverted *after* the colour is drained and then re-tinted, so a dark glyph comes back light and
            // carrying a hue — which is what makes an inversion usable rather than merely startling.
            "negative_warm", "Warm Negative", Category.INVERTED,
            ColorMatrices.invert().then(ColorMatrices.scale(1.06f, 1f, 0.9f)),
        ),
        entry(
            "negative_cool", "Cool Negative", Category.INVERTED,
            ColorMatrices.invert().then(ColorMatrices.scale(0.9f, 1f, 1.08f)),
        ),
    )

    private val byId: Map<IconFilter, Entry> = All.associateBy { it.filter }

    /** Every filter in [category], in list order. */
    fun inCategory(category: Category): List<Entry> = All.filter { it.category == category }

    /** The entry for [filter], or null for an id this build does not know — stale or newer stored data. */
    fun entryOrNull(filter: IconFilter): Entry? = byId[filter]

    /**
     * The matrix [filter] resolves to, or null for an unknown id — in which case the effect draws nothing, the same
     * degrade `IconShapes.drawableResOrNull` makes for a shape.
     */
    fun matrixOrNull(filter: IconFilter): FloatArray? = byId[filter]?.matrix
}
