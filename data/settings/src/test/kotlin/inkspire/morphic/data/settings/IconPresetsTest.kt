package inkspire.morphic.data.settings

import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The library's two operations, and the one judgment call in them: saving over a name replaces rather than
 * duplicating.
 *
 * Worth pinning because the alternative fails quietly — two presets with one name look identical in a list, and
 * a user deleting "the wrong one" would find the other still there.
 */
class IconPresetsTest {

    private fun set(fill: Int) = IconAppearance(
        IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.SolidFill(fill)),
                IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.AppDefault),
            ),
        ),
    )

    @Test
    fun `saving adds a preset`() {
        val library = IconPresets.Default.with(IconPreset("Mono", set(0)))

        assertEquals(listOf("Mono"), library.presets.map { it.name })
    }

    @Test
    fun `saving over a name replaces it rather than making a second`() {
        val library = IconPresets.Default
            .with(IconPreset("Mono", set(0xFF000000.toInt())))
            .with(IconPreset("Mono", set(0xFFFFFFFF.toInt())))

        assertEquals(1, library.presets.size)
        assertEquals(set(0xFFFFFFFF.toInt()), library.presets.single().appearance)
    }

    @Test
    fun `saving over a name keeps the rest, and moves the saved one to the end`() {
        // The order is the honest consequence of replace-then-append rather than a design goal, but it is the
        // order a list will show, so it is worth being deliberate about: the most recently saved is last.
        val library = IconPresets.Default
            .with(IconPreset("A", set(1)))
            .with(IconPreset("B", set(2)))
            .with(IconPreset("A", set(3)))

        assertEquals(listOf("B", "A"), library.presets.map { it.name })
    }

    @Test
    fun `deleting removes one and leaves the others`() {
        val library = IconPresets.Default
            .with(IconPreset("A", set(1)))
            .with(IconPreset("B", set(2)))
            .without("A")

        assertEquals(listOf("B"), library.presets.map { it.name })
    }

    @Test
    fun `deleting a name that is not there changes nothing`() {
        val library = IconPresets.Default.with(IconPreset("A", set(1)))

        assertEquals(library, library.without("nope"))
    }

    /**
     * The whole reason `renamed` exists rather than a `without` and a `with`: spelled that way the renamed preset
     * would come back at the *end*, and the test above is what proves it would.
     */
    @Test
    fun `renaming keeps the preset where it was`() {
        val library = IconPresets.Default
            .with(IconPreset("A", set(1)))
            .with(IconPreset("B", set(2)))
            .with(IconPreset("C", set(3)))
            .renamed("B", "Bee")

        assertEquals(listOf("A", "Bee", "C"), library.presets.map { it.name })
        assertEquals(set(2), library.presets[1].appearance)
    }

    /** Renaming onto a name already in use is an overwrite the user asked for — never two rows with one name. */
    @Test
    fun `renaming onto an existing name drops the one it replaces`() {
        val library = IconPresets.Default
            .with(IconPreset("A", set(1)))
            .with(IconPreset("B", set(2)))
            .renamed("A", "B")

        assertEquals(listOf("B"), library.presets.map { it.name })
        assertEquals(set(1), library.presets.single().appearance)
    }

    @Test
    fun `renaming something absent, or to nothing, changes nothing`() {
        val library = IconPresets.Default
            .with(IconPreset("A", set(1)))
            .with(IconPreset("B", set(2)))

        assertEquals(library, library.renamed("nope", "C"))
        assertEquals(library, library.renamed("A", "   "))
    }
}
