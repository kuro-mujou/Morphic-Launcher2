package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.TintMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What [LayerSource.AppDefaultMonochrome] resolves to, which is the one source whose meaning depends on what the app
 * shipped — and therefore the one that can be wrong for a *subset* of the device's apps while looking perfect on the
 * app the studio happens to be previewing.
 *
 * **These are the tests that would have caught the bug this file was written for.** The resolver drained the
 * foreground's color on apps with no themed layer and handed the app's *own* themed artwork through untouched, on the
 * reasoning that the themed slot holds a silhouette. It holds whatever the app put there — so an app shipping color
 * came out in color while every other icon went grey, i.e. the feature inverted itself on exactly the apps that had
 * bothered to support it. Nothing failed; the icons were simply wrong, on a device, for some apps.
 *
 * Runs on the plain JVM with no emulator: every layer here is a [ParsedLayer.Color], so no `Drawable` is ever
 * constructed. That is the same reason [LegacyBackgroundTest] and [LayerFilterTest] are plain unit tests — the
 * decisions are arithmetic and data, and only the rasterising is Android.
 */
class IconLayerResolverTest {

    private val resolver = IconLayerResolver()

    /** Distinct so a test can tell *which* artwork came back, not merely that something did. */
    private val foregroundArgb = 0xFFFF0000.toInt()
    private val themedArgb = 0xFF00FF00.toInt()

    private fun icon(monochrome: ParsedLayer? = null) = ParsedIcon(
        foreground = ParsedLayer.Color(foregroundArgb),
        background = ParsedLayer.Color(0xFF0000FF.toInt()),
        monochrome = monochrome,
    )

    /** A set whose foreground is monochrome, carrying [color] as that layer's own color effect. */
    private fun monochromeSet(color: LayerEffect.Color? = null) = IconLayerSet(
        listOf(
            IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.Empty),
            IconLayerSpec(
                role = LayerRole.FOREGROUND,
                source = LayerSource.AppDefaultMonochrome,
                effects = listOfNotNull(color),
            ),
        ),
    )

    private fun resolveForeground(set: IconLayerSet, icon: ParsedIcon): ResolvedLayer =
        resolver.resolve(set, icon, customImage = { null })
            .single { it.spec.role == LayerRole.FOREGROUND }

    @Test
    fun `an app that ships themed artwork has it drained, whatever color the app put in that slot`() {
        val resolved = resolveForeground(monochromeSet(), icon(monochrome = ParsedLayer.Color(themedArgb)))

        // The app's own artwork is what is drawn — the source swaps the content, which was never the broken half.
        assertEquals(ParsedLayer.Color(themedArgb), resolved.content)
        // ...and it is drained. This is the assertion that was false: the spec came back with no color effect at all,
        // so a themed layer holding a full-color picture stayed a full-color picture.
        assertEquals(0f, resolved.spec.color?.saturation)
    }

    @Test
    fun `an app with no themed artwork has its foreground drained instead`() {
        val resolved = resolveForeground(monochromeSet(), icon(monochrome = null))

        assertEquals(ParsedLayer.Color(foregroundArgb), resolved.content)
        assertEquals(0f, resolved.spec.color?.saturation)
    }

    /**
     * The property the two arms above exist to guarantee, stated as one thing: whichever artwork answers the source,
     * the result is grey. A user selecting "monochrome" is asking for one outcome, and a setting that delivers it on
     * some apps and not others is worse than one that does not exist.
     */
    @Test
    fun `monochrome means grey on both branches, which is the whole of what the word promises`() {
        val withThemed = resolveForeground(monochromeSet(), icon(monochrome = ParsedLayer.Color(themedArgb)))
        val withoutThemed = resolveForeground(monochromeSet(), icon(monochrome = null))

        assertEquals(withoutThemed.spec.color?.saturation, withThemed.spec.color?.saturation)
        assertEquals(0f, withThemed.spec.color?.saturation)
    }

    @Test
    fun `a tint the user set survives the drain, because silhouette plus tint is the themed-icon recipe`() {
        val tint = LayerEffect.Color(tintArgb = 0xFF3366CC.toInt(), tintMode = TintMode.SOLID)
        val resolved = resolveForeground(monochromeSet(tint), icon(monochrome = ParsedLayer.Color(themedArgb)))

        assertEquals(0xFF3366CC.toInt(), resolved.spec.color?.tintArgb)
        assertEquals(0f, resolved.spec.color?.saturation)
        // SOLID is *kept* over real themed artwork: flattening a silhouette to one flat color is what that mode is
        // for, and lifting a black glyph to white is the case it was added for.
        assertEquals(TintMode.SOLID, resolved.spec.color?.tintMode)
    }

    /**
     * The one place this resolver overrides the user, and it only applies to the fallback. A solid tint keeps alpha
     * alone, which is right over a silhouette and disastrous over an adaptive foreground — that alpha is usually a
     * large blob, so the icon would come out as a featureless colored splodge rather than a glyph.
     */
    @Test
    fun `a solid tint is downgraded to multiply on the fallback, so a foreground does not flatten to a blob`() {
        val tint = LayerEffect.Color(tintArgb = 0xFF3366CC.toInt(), tintMode = TintMode.SOLID)
        val resolved = resolveForeground(monochromeSet(tint), icon(monochrome = null))

        assertEquals(TintMode.MULTIPLY, resolved.spec.color?.tintMode)
        // The tint itself is kept — only its mode changes, so the color the user picked still shows through as a
        // multiply over greyscale.
        assertEquals(0xFF3366CC.toInt(), resolved.spec.color?.tintArgb)
    }

    /**
     * The drain must not eat the layer's other effects. `withColor` replaces only the color effect, and a gradient
     * silently disappearing when a source changed would be the kind of loss nobody attributes to this arm.
     */
    @Test
    fun `draining replaces only the color effect and leaves the rest of the stack in place`() {
        val gradient = LayerEffect.Gradient(startArgb = 0xFF000000.toInt(), endArgb = 0xFFFFFFFF.toInt())
        val set = IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.Empty),
                IconLayerSpec(
                    role = LayerRole.FOREGROUND,
                    source = LayerSource.AppDefaultMonochrome,
                    effects = listOf(gradient),
                ),
            ),
        )

        val resolved = resolveForeground(set, icon(monochrome = ParsedLayer.Color(themedArgb)))

        assertTrue(gradient in resolved.spec.effects)
        assertEquals(0f, resolved.spec.color?.saturation)
    }

    /**
     * Not about monochrome, but it is the invariant the arm above sits inside: a layer that resolves to nothing is
     * dropped rather than drawn empty. Pinned here because this test class is the only one that exercises `resolve`
     * end to end.
     */
    @Test
    fun `an empty layer contributes nothing to the composite`() {
        val resolved = resolver.resolve(monochromeSet(), icon(), customImage = { null })

        assertNull(resolved.firstOrNull { it.spec.role == LayerRole.BACKGROUND })
    }
}
