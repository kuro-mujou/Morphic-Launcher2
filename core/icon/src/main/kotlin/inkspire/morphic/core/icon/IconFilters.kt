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
 */
object IconFilters {

    /** How the picker groups the list. Presentation only — the stored recipe holds an id and nothing else. */
    enum class Category(val label: String) {
        CINEMATIC("Cinematic"),
        VIVID("Vivid"),
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

        // --- Inverted ---
        entry("negative", "Negative", Category.INVERTED, ColorMatrices.invert()),
        entry(
            "inverted_mono", "Inverted Mono", Category.INVERTED,
            ColorMatrices.saturation(0f).then(ColorMatrices.invert()),
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
