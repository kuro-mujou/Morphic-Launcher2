package inkspire.morphic.data.settings

import inkspire.morphic.core.model.icon.IconLayerSet
import kotlinx.serialization.Serializable

/**
 * One saved icon recipe under a name the user chose.
 *
 * A preset is **exactly an [IconLayerSet] plus a name** — no separate format, no subset of the recipe. That is
 * what the persistence model was already shaped for: the plan noted a preset needs "no schema change", and this
 * is that promise being cashed rather than a coincidence.
 */
@Serializable
data class IconPreset(
    val name: String,
    val layerSet: IconLayerSet,
)

/**
 * The user's library of saved icon recipes.
 *
 * **A settings slice rather than a Room table**, which is the opposite call from per-app overrides one module
 * over, and the line between them is whether the store grows *with use of the launcher*. Overrides get a row per
 * customised app and are read one at a time by every icon on screen; presets are a handful, chosen deliberately,
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

    companion object {
        val Default = IconPresets()
    }
}
