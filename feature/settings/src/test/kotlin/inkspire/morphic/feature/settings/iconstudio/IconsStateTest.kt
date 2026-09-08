package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.data.settings.IconPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [IconsState.appliedPreset] — which tile in the Icons hub's library wears the ring.
 *
 * **Worth pinning because getting it wrong is legible but not diagnosable.** The ring is the only feedback a tap on a
 * preset gives — applying is immediate and has no confirm — so a ring on the wrong tile reads as "the tap applied the
 * wrong look", which is a bug in a completely different place from the one that exists.
 *
 * What these tests really guard is that **the recipe never comes back into this decision**. Resolving by name looks
 * like it is throwing information away, and the value comparison it replaced is an easy thing to reintroduce as an
 * apparent improvement — so the duplicate case here is written to fail if it ever is.
 */
class IconsStateTest {

    private fun look(fill: Int) = IconAppearance(
        IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.SolidFill(fill)),
                IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.AppDefault),
            ),
        ),
    )

    private val black = look(0xFF000000.toInt())

    /**
     * Clones are the point: a look kept twice so one copy can be adjusted later. They differ only by name, which is
     * why the name is what the ring is resolved from — comparing recipes rang the first of them whatever was pressed.
     */
    @Test
    fun `a clone is marked as itself, not as the preset it was copied from`() {
        val library = listOf(IconPreset("First", black), IconPreset("Second", black))

        assertEquals("Second", IconsState(presets = library, applied = "Second").appliedPreset)
        assertEquals("First", IconsState(presets = library, applied = "First").appliedPreset)
    }

    /** Deleting the applied preset leaves its name stamped; nothing holds it any more, so nothing is marked. */
    @Test
    fun `a name for a preset that is gone marks nothing`() {
        val state = IconsState(presets = listOf(IconPreset("Ink", black)), applied = "Deleted")

        assertNull(state.appliedPreset)
    }

    /**
     * A recipe edited in the studio is stamped with no name, and marks nothing **even when it matches a saved
     * preset** — the deliberate cost of resolving by name. A look arrived at by editing did not come from a preset.
     */
    @Test
    fun `a look re-created by hand marks nothing`() {
        val state = IconsState(presets = listOf(IconPreset("Mono", black)), applied = null)

        assertNull(state.appliedPreset)
    }
}
