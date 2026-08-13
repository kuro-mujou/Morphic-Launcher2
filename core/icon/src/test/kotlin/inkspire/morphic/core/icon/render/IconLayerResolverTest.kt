package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.parse.ContentMetrics
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

    // --- normalize: every icon fills its box ---

    private fun metrics(side: Float, center: Float = 0.5f) = ContentMetrics(
        left = center - side / 2f,
        top = center - side / 2f,
        right = center + side / 2f,
        bottom = center + side / 2f,
    )

    /** Artwork with nothing behind it — the artwork *is* the icon. */
    private fun bareIcon(m: ContentMetrics?) =
        ParsedIcon(foreground = ParsedLayer.Image(StubDrawable(), metrics = m), background = null)

    /** Artwork on a plate — the plate is the icon, and it already fills the box. */
    private fun platedIcon(m: ContentMetrics?) = ParsedIcon(
        foreground = ParsedLayer.Image(StubDrawable(), metrics = m),
        background = ParsedLayer.Color(0xFF0000FF.toInt()),
    )

    private fun appDefaultSet(normalize: Boolean = true, zoom: Float = 1f) = IconLayerSet(
        listOf(
            IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.AppDefault),
            IconLayerSpec(
                role = LayerRole.FOREGROUND,
                source = LayerSource.AppDefault,
                normalize = normalize,
                zoom = zoom,
            ),
        ),
    )

    /**
     * **The point of the whole thing:** artwork is grown until its *opaque bounds* fill the box, so a small logo and
     * a large one end up the same size rather than each keeping whatever size its author happened to draw it at.
     * Transparent margin is discounted, which is what makes apps from different sources comparable at all.
     */
    @Test
    fun `artwork is grown until it fills the box, whatever size it was drawn`() {
        val small = resolveForeground(appDefaultSet(), bareIcon(metrics(0.25f))).spec.zoom
        val large = resolveForeground(appDefaultSet(), bareIcon(metrics(0.8f))).spec.zoom

        assertEquals(4f, small, 0.001f)
        assertEquals(1.25f, large, 0.001f)
        // Both end up spanning the box exactly, which is what "the same size" means here.
        assertEquals(1f, 0.25f * small, 0.001f)
        assertEquals(1f, 0.8f * large, 0.001f)
    }

    /**
     * Artwork is rarely centered in its own canvas, so the recentring has to carry the same factor that displaced
     * it, or a grown icon fills the box while sitting off in a corner.
     */
    @Test
    fun `off-center artwork is recentered as it grows`() {
        val resolved = resolveForeground(appDefaultSet(), bareIcon(metrics(0.5f, center = 0.7f)))

        val scale = resolved.spec.zoom
        // The ink's center at 0.7 is displaced to 0.5 + 0.2 * scale by scaling about the box's middle; the offset
        // has to be exactly what returns it, so the two cancel.
        assertEquals(0.5f, 0.5f + 0.2f * scale + resolved.spec.offsetX, 0.001f)
    }

    /**
     * **The background makes no difference to the mechanism**, which is the rule rather than a detail: a plate
     * changes what sits behind the artwork, not how large the artwork should be. So the same app resolves to the
     * same size whether its background is drawn or switched off, and toggling one leaves the picture where it is.
     */
    @Test
    fun `a plate does not change the size the artwork is scaled to`() {
        val plated = resolveForeground(appDefaultSet(), platedIcon(metrics(0.25f))).spec.zoom
        val bare = resolveForeground(appDefaultSet(), bareIcon(metrics(0.25f))).spec.zoom

        assertEquals(bare, plated, 0.001f)
        assertEquals(4f, plated, 0.001f)
    }

    /** ...and the plate itself is still never resized. */
    @Test
    fun `the background keeps its own size`() {
        val resolved = resolver.resolve(appDefaultSet(), platedIcon(metrics(0.25f)), customImage = { null })

        assertEquals(1f, resolved.single { it.spec.role == LayerRole.BACKGROUND }.spec.zoom, 0.001f)
    }

    /**
     * **A themed layer is measured by its own bounds, not the normal foreground's.** This was wrong: the fit came
     * from `ParsedIcon.foreground` and was applied to whichever layer held the foreground role, so an app shipping a
     * themed icon had its silhouette scaled by a factor measured from a completely different picture. Silent, and
     * only on the apps that bothered to support theming.
     */
    @Test
    fun `a themed layer is scaled by its own bounds, not the foreground's`() {
        val icon = ParsedIcon(
            foreground = ParsedLayer.Image(StubDrawable(), metrics = metrics(0.8f)),
            background = null,
            // The silhouette is drawn much smaller than the full-color artwork, which is ordinary.
            monochrome = ParsedLayer.Image(StubDrawable(), metrics = metrics(0.2f)),
        )

        val resolved = resolveForeground(monochromeSet(), icon)

        // 1 / 0.2 — its own bounds. The foreground's would have given 1.25 and left it a fifth of the size.
        assertEquals(5f, resolved.spec.zoom, 0.001f)
    }

    /** The monochrome *fallback* draws the ordinary foreground, so it is measured by that instead. */
    @Test
    fun `the monochrome fallback is scaled by the foreground it falls back to`() {
        val icon = ParsedIcon(
            foreground = ParsedLayer.Image(StubDrawable(), metrics = metrics(0.5f)),
            background = null,
            monochrome = null,
        )

        assertEquals(2f, resolveForeground(monochromeSet(), icon).spec.zoom, 0.001f)
    }

    /**
     * Artwork somebody chose deliberately is left alone, and it falls out of what the parser measures rather than
     * from a list of sources checked here: a pack drawable and an imported image arrive with no metrics at all.
     */
    @Test
    fun `a pack or a custom image is never resized`() {
        val set = IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.Empty),
                IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.CustomImage("some/path")),
            ),
        )

        val resolved = resolver.resolve(set, bareIcon(metrics(0.25f)), customImage = { StubDrawable() })
            .single { it.spec.role == LayerRole.FOREGROUND }

        assertEquals(1f, resolved.spec.zoom, 0.001f)
    }

    /** It can only ever grow artwork, and only ever to the box: bounds cannot exceed the canvas. */
    @Test
    fun `artwork already filling its canvas is not resized`() {
        assertEquals(1f, resolveForeground(appDefaultSet(), bareIcon(metrics(1f))).spec.zoom, 0.001f)
    }

    /** Off is the raw artwork, at exactly the size its author drew. */
    @Test
    fun `normalize off leaves the artwork alone`() {
        val resolved = resolveForeground(appDefaultSet(normalize = false), bareIcon(metrics(0.25f)))

        assertEquals(1f, resolved.spec.zoom, 0.001f)
        assertEquals(0f, resolved.spec.offsetX, 0.001f)
    }

    /** The user's own zoom composes on top rather than being replaced. */
    @Test
    fun `the user's own zoom composes with it`() {
        assertEquals(8f, resolveForeground(appDefaultSet(zoom = 2f), bareIcon(metrics(0.25f))).spec.zoom, 0.001f)
    }

    /** Off-center artwork is brought back to the middle, by the scale that displaced it. */
    @Test
    fun `off-center artwork is centered, by the scale that displaced it`() {
        val resolved = resolveForeground(appDefaultSet(), bareIcon(metrics(0.5f, center = 0.3f)))

        assertEquals(2f, resolved.spec.zoom, 0.001f)
        assertEquals(0.4f, resolved.spec.offsetX, 0.001f)
    }

    /** Artwork that could not be measured is left alone rather than guessed at. */
    @Test
    fun `unmeasured artwork is left alone`() {
        assertEquals(1f, resolveForeground(appDefaultSet(), bareIcon(null)).spec.zoom, 0.001f)
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

/**
 * A drawable that draws nothing, so a resolver test can hold [ParsedLayer.Image] without an emulator. Nothing here
 * calls a method on it — the resolver only passes it through — so the stubs never run.
 */
private class StubDrawable : android.graphics.drawable.Drawable() {
    override fun draw(canvas: android.graphics.Canvas) = Unit
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Java", ReplaceWith("android.graphics.PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
