package inkspire.morphic.data.settings

import inkspire.morphic.core.model.icon.IconAppearance
import kotlinx.serialization.Serializable

/**
 * One saved look under a name the user chose.
 *
 * A preset is **exactly an [IconAppearance] plus a name** — no separate format, no subset of it. That is what the
 * persistence model was already shaped for: the plan noted a preset needs "no schema change", and this is that
 * promise being cashed rather than a coincidence.
 *
 * **It was the layer set alone until the plate existed**, and what forced the widening is one control: the finalize
 * screen offers "save as preset" underneath the plate switch, so saving the recipe alone would have kept half of
 * what the user was looking at. See [IconAppearance].
 */
@Serializable
data class IconPreset(
    val name: String,
    val appearance: IconAppearance,
)

/**
 * The user's library of saved icon recipes.
 *
 * **A settings slice rather than a Room table**, which is the opposite call from per-app overrides one module
 * over, and the line between them is whether the store grows *with use of the launcher*. Overrides get a row per
 * customized app and are read one at a time by every icon on screen; presets are a handful, chosen deliberately,
 * and read as a whole list whenever the library is shown. That is a document, and a document is a slice.
 *
 * **Built-in curated presets are deliberately absent.** L1 planned to ship some, and that is a content decision —
 * which looks are worth shipping — rather than an engineering one. The mechanism is here; a starter set can be
 * seeded into this list whenever someone designs one, with no code change beyond the seeding.
 */
@Serializable
data class IconPresets(
    val presets: List<IconPreset> = emptyList(),
) {

    /**
     * This library with [preset] saved, **replacing any preset of the same name**.
     *
     * Replacing rather than refusing or de-duplicating the name: "save as" with a name already in the library is
     * a user overwriting their own preset, which is what they asked for. Silently making a second entry with the
     * same name would leave two rows nothing could tell apart.
     */
    fun with(preset: IconPreset): IconPresets =
        copy(presets = presets.filterNot { it.name == preset.name } + preset)

    /** This library without the preset called [name]. A no-op when there is none. */
    fun without(name: String): IconPresets = copy(presets = presets.filterNot { it.name == name })

    /**
     * This library with the preset called [from] renamed to [to], **kept where it was**.
     *
     * **Position-preserving, which is the whole reason this is an operation rather than a `without` and a `with`.**
     * The name is a preset's identity, so a rename really is a delete and an insert — and [with] appends, so spelled
     * that way it would send a renamed tile to the end of the grid. A user correcting a typo has not asked for their
     * library to be reordered.
     *
     * Any *other* preset already called [to] is dropped, for [with]'s reason: two rows nothing can tell apart are
     * worse than an overwrite the user asked for by typing a name they already own.
     *
     * A no-op when [from] is not here, or when [to] is blank — a preset with no name is one nothing could pick out.
     */
    fun renamed(from: String, to: String): IconPresets {
        val name = to.trim()
        if (name.isEmpty()) return this
        val index = presets.indexOfFirst { it.name == from }
        if (index < 0) return this
        val renamed = presets[index].copy(name = name)
        return copy(
            presets = presets
                .mapIndexed { i, preset -> if (i == index) renamed else preset }
                .filter { it === renamed || it.name != name },
        )
    }

    companion object {
        val Default = IconPresets()
    }
}
