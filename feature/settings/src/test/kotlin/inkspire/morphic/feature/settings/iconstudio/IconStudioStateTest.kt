package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IconStudioState.canUseFixedSource] — which layers may take a source that is the same pixels for every app.
 *
 * **Worth pinning because both ways of being wrong are silent.** Too strict and a control is simply absent, which this
 * studio keeps being mistaken for broken over; too loose and one global edit replaces every icon on the device with the
 * same picture. Neither throws, and neither shows up anywhere but on a real home screen.
 *
 * Two readers must agree with this property — `SourceControls` omits the rows and `IconStudioViewModel.pickImage`
 * refuses behind it — which is why it is a property rather than a test at each site, and why it is worth a test of its
 * own rather than being inferred from the UI.
 */
class IconStudioStateTest {

    private val component = ComponentKey(packageName = "com.example", className = "Main")

    /** A three-layer stack — custom, background, foreground — so a test can select any role by index. */
    private val stack = IconLayerSet(
        listOf(
            IconLayerSpec(role = LayerRole.CUSTOM, source = LayerSource.Empty),
            IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.AppDefault),
            IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.AppDefault),
        ),
    )

    private val customIndex = 0
    private val backgroundIndex = 1
    private val foregroundIndex = 2

    private fun global(selected: Int) =
        IconStudioState(subject = StudioSubject.Global(component), editing = stack, selected = selected)

    private fun individual(selected: Int) =
        IconStudioState(subject = StudioSubject.App(component), editing = stack, selected = selected)

    /**
     * The rule's whole purpose: a flat color or one photo on the global foreground makes every app's icon the same
     * picture, and an icon that no longer identifies its app has stopped being an icon.
     */
    @Test
    fun `the global foreground refuses a fixed source, because that is the layer identifying the app`() {
        assertFalse(global(foregroundIndex).canUseFixedSource)
    }

    /**
     * The reversal this test class was added for. A shared plate under a per-app glyph leaves every icon still telling
     * you what it is — it is Android's own themed-icon look — so refusing it forbade the commonest global look there
     * is, and made a device-wide monochrome recipe a three-step workaround.
     */
    @Test
    fun `the global background allows one, because the glyph above it still identifies the app`() {
        assertTrue(global(backgroundIndex).canUseFixedSource)
    }

    /** Unchanged by the reversal: a decoration layer is *added* to every icon rather than standing in for one. */
    @Test
    fun `a custom layer allows one in either studio`() {
        assertTrue(global(customIndex).canUseFixedSource)
        assertTrue(individual(customIndex).canUseFixedSource)
    }

    /**
     * Editing one app, every layer is fair game — replacing that app's artwork is the entire point of the individual
     * studio, and there is no other icon for it to affect.
     */
    @Test
    fun `editing a single app allows a fixed source on every layer`() {
        assertTrue(individual(foregroundIndex).canUseFixedSource)
        assertTrue(individual(backgroundIndex).canUseFixedSource)
    }

    /**
     * Nothing selected is the state before anything has loaded. It refuses, rather than defaulting to permission — the
     * safe direction, since the reader behind this one writes a source.
     */
    @Test
    fun `no selected layer refuses`() {
        val empty = IconStudioState(subject = StudioSubject.Global(component), selected = 99)

        assertFalse(empty.canUseFixedSource)
    }
}
