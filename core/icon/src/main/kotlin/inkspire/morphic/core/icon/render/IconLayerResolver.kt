package inkspire.morphic.core.icon.render

import android.graphics.drawable.Drawable
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns an [IconLayerSet] + the [ParsedIcon] for one app into the ordered, concrete layers the compositor
 * draws. Pure and testable: the one impure step — decoding a custom-image path — is injected as [customImage]
 * rather than done here, so this needs no `Context` or file I/O.
 */
class IconLayerResolver {

    /**
     * Resolves the visible layers of [layerSet] bottom→top against [icon]. A layer that resolves to no
     * content is dropped from the result (an empty background, or a custom image whose file is gone) — it
     * still exists in the set for the editor, it just contributes nothing to the composite.
     *
     * Both image lookups are **injected and pre-bound**: `packImage` already knows which app it is resolving
     * for, because this class deliberately does not. What it keeps is the `when` over [LayerSource] — one place
     * that decides what each source *means*, rather than a copy of that decision in each renderer.
     *
     * @param customImage decodes a [LayerSource.CustomImage] path to a drawable; returns `null` if missing.
     * @param packImage draws this app from an installed icon pack — its mapped drawable, or the pack's own fallback
     *   treatment for an app it does not theme. `null` only when the pack has neither, and the layer then resolves to
     *   the app's own artwork rather than to nothing.
     */
    fun resolve(
        layerSet: IconLayerSet,
        icon: ParsedIcon,
        customImage: (path: String) -> Drawable?,
        packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
    ): List<ResolvedLayer> {
        val drawn = layerSet.layers
            .filter { it.visible }
            .mapNotNull { spec -> spec.resolveLayer(icon, customImage, packImage) }
            // Each render draws its own drawables, never the cache's — see [owned].
            .map { it.owned() }

        // **Every app's artwork ends up the same size, and nothing about the icon changes that.** The fit reads each
        // layer's own opaque bounds against the box — it does not consult the background, is not affected by turning
        // one off, and never resizes one.
        return drawn.map { it.normalized() }
    }
}


/**
 * The layer holding a drawable **this render owns**, rather than the one the parse cache holds.
 *
 * ## A `Drawable` is mutable state, and both renderers write to it
 *
 * Drawing one means `setBounds(0, 0, sizePx, sizePx)` and then `draw`, in the bake and in the live path alike — and
 * the bounds live on the *instance*. A [ParsedIcon] is cached per app and handed to every consumer, so before this
 * every renderer on screen was writing its own size into the same object: the studio canvas, each tile in the layer
 * rail, and any surface icon baking at the same moment. Four writers, four sizes, three of them on background threads.
 *
 * **The failure is a picture, not a crash, which is what made it look like a rendering bug.** A bake that sets 768 and
 * is then overwritten by a tile's 128 draws the artwork at 128 — and a drawable draws from its bounds' origin, so what
 * lands is the icon at a sixth of its size in the **top-left corner** of the box, with the rest of the square empty. A
 * whole-icon shape then masks the box it was told about rather than the artwork, clipping the small icon on whichever
 * side the silhouette crosses it, and a blur spreads out of it into the space where the icon should have been.
 *
 * **It was always latent and a real blur is what surfaced it.** The window is however long the artwork takes to draw,
 * so while a blur was two `Bitmap.scale` calls the odds of another renderer landing inside it were slim; three box
 * passes over half a million pixels holds it open long enough to hit every time. Which is the honest description of a
 * data race — the bug was there, and something merely made it likely.
 *
 * `newDrawable` shares the constant state, so the artwork itself is not copied — a `BitmapDrawable` copy points at the
 * same bitmap. `mutate` is what makes the state private as well, which matters for the drawables that cache *inside*
 * it: a `VectorDrawable` keeps a rendered bitmap in its constant state, so two copies at two sizes would go on
 * fighting over that instead of over the bounds. A drawable with no constant state cannot be copied and is passed
 * through as it is, which is no worse than before.
 *
 * **Here rather than in each renderer**, because this is the one seam both paths already go through to learn what a
 * layer is made of — and a fix applied in one renderer would have left the other still writing to the shared object,
 * which is the same race with one fewer participant.
 */
private fun ResolvedLayer.owned(): ResolvedLayer {
    val image = content as? ParsedLayer.Image ?: return this
    val own = image.drawable.constantState?.newDrawable()?.mutate() ?: return this
    return copy(content = image.copy(drawable = own))
}

/**
 * The layer with its artwork scaled to the size every app's artwork is drawn at, or unchanged when there is nothing
 * to do.
 *
 * **The measurement comes from the content this layer actually resolved to, which is the correction that made the
 * themed layer right.** An earlier cut measured `ParsedIcon.foreground` and applied the result to whichever layer
 * held the foreground role — fine until an app ships a themed icon, where the artwork drawn is its *monochrome*
 * silhouette and that has bounds of its own. It was scaled by a factor measured from a different picture, silently
 * and only on the apps that support theming.
 *
 * **The artwork is its opaque pixels, and nothing else.** An app's foreground is mostly transparency: the canvas it
 * is drawn on says nothing about how big the picture on it is, and two apps drawing the same logo at half the scale
 * of each other are indistinguishable until the transparent margin is thrown away. So the ink's bounds are scanned
 * ([ContentMetrics]) and *those* are scaled to the box, whoever made the app and whatever convention they followed.
 *
 * **What is measured is exactly what is normalized**, and that is what keeps the scope right without a list of
 * special cases: the parser measures the app's **own** artwork — its foreground and its themed layer — and nothing
 * else, so a pack drawable, an imported image and a flat fill arrive with no metrics and are left alone by
 * construction. Somebody chose those deliberately; there is nothing to correct.
 *
 * **The background is not consulted, and that is the rule rather than an oversight.** Whether a plate is drawn, or
 * turned off in the studio, changes what sits *behind* the artwork and not how large the artwork should be — so the
 * same app resolves to the same size either way, and a user toggling the background sees the picture stay put. The
 * role check is kept even though a background carries no metrics to begin with: "the background is never resized" is
 * too load-bearing to rest on a parser detail one file away.
 *
 * There is **no tuning value here**. Matching sizes is a statement about geometry, so the scale falls out of the
 * measurement, and where the artwork sits *within* the box is the per-layer zoom's job — one global edit moves every
 * app's artwork together, which is exactly the control a shared size makes possible.
 *
 * Two properties fall out rather than being enforced: it can only ever **grow** artwork, since the raster it is
 * measured from *is* the box and ink cannot exceed it, and it can never **overflow**, since filling the box exactly
 * is what it computes. Earlier versions needed an explicit cap against overflow this shape cannot produce.
 *
 * **And the measurement is of a raster, which is what makes one scale correct at every size.** A drawable is free to
 * look different at different sizes — absolute padding does exactly that — so a fraction measured once could not be
 * right for both a drawer cell and the studio's much larger preview, and the same icon came out correct in one and
 * overflowing in the other. `DrawableParser` renders each measured layer to a bitmap first; a bitmap scales
 * proportionally, so its ink fraction is a property of the artwork again rather than of the box it lands in.
 *
 * **Centered against the transform that is actually applied, which is the whole subtlety here.** Everything scales
 * and rotates about the *box's* middle, so artwork whose ink sits off-center is thrown further off the more it is
 * enlarged or turned — and the translate that corrects that has to be computed from the **final** zoom and rotation,
 * not from the normalization's own factor.
 *
 * Getting that wrong is what made the zoom slider misbehave: the correction was `-(inkCenter - 0.5) * fit`, which
 * cancels the displacement only while the total scale *is* `fit`. Multiply the user's zoom in and a residual
 * `(inkCenter - 0.5) * fit * (zoom - 1)` is left over, so the artwork slid off center as the slider moved, in the
 * direction it had been off-center in the source — which reads exactly like scaling about the artwork instead of
 * about the box. It was invisible at zoom 1, where the residual is zero, i.e. everywhere until someone dragged it.
 *
 * So the fold is not "a fit composed with the user's transform" but one transform resolved once: the user's zoom
 * multiplies the fit, and the recentring is derived from that product and then **rotated the way the renderer will
 * rotate it** (`LayerTransform` orders scale → rotate → translate about the box center, so a translate computed
 * before the rotation must be turned to match). The user's own offset is added on top and so still means "relative
 * to where this icon normally sits".
 */
private fun ResolvedLayer.normalized(): ResolvedLayer {
    if (!spec.normalize || spec.role != LayerRole.FOREGROUND) return this
    val metrics = (content as? ParsedLayer.Image)?.metrics ?: return this
    if (metrics.longestSide <= 0f) return this

    // The zoom the renderer will really apply — the user's, on top of the fit.
    val zoom = spec.zoom / metrics.longestSide

    // What that zoom does to the ink's center, negated: the distance the translate has to make up.
    val correctionX = -(metrics.centerX - 0.5f) * zoom
    val correctionY = -(metrics.centerY - 0.5f) * zoom

    // Turned into the rotated frame, because the rotation happens between the scale and the translate.
    val radians = spec.rotation * PI.toFloat() / 180f
    val cos = cos(radians)
    val sin = sin(radians)

    return copy(
        spec = spec.copy(
            zoom = zoom,
            offsetX = spec.offsetX + correctionX * cos - correctionY * sin,
            offsetY = spec.offsetY + correctionX * sin + correctionY * cos,
        ),
    )
}

/**
 * One spec against one app: the content its [LayerSource] points at, paired with the spec to draw it by — or `null`
 * when there is nothing to draw. `AppDefault` is meaningless on a custom layer, so that combination resolves to
 * nothing, as does an [LayerSource.Empty] layer.
 *
 * **It returns a spec as well as content because one source rewrites it** — see the monochrome arm. Every other arm
 * hands back the spec it was given.
 */
private fun IconLayerSpec.resolveLayer(
    icon: ParsedIcon,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
): ResolvedLayer? = when (val src = source) {
    // An unfilled layer, which draws nothing — the same answer as a pack that does not cover this app, and reached the
    // same way, so both render paths already handle it.
    LayerSource.Empty -> null

    LayerSource.AppDefault -> appArtwork(icon)?.let { ResolvedLayer(it, this) }

    // **The one source whose meaning depends on what the app shipped, and deciding it here is the point.** Whether an
    // app carries a themed-icon layer is not something the user knows, and in the *global* studio it is not even one
    // answer — a single recipe covers apps that differ. So the branch belongs where per-app differences already live,
    // exactly as `AppDefault` resolves to different artwork per app.
    LayerSource.AppDefaultMonochrome -> when (val mono = icon.monochrome) {
        // No themed layer to swap in, so the foreground is drained of color instead — the best available
        // approximation, and the whole reason this used to be a visible no-op: it fell back to the *unfiltered*
        // foreground, so choosing monochrome on such an app changed nothing at all.
        //
        // Folded into the spec rather than baked into the content, which keeps it composable with the layer's own
        // recoloring: a tint the user set survives, and silhouette-plus-tint is the themed-icon recipe.
        null -> ResolvedLayer(icon.foreground, withEffect(monochromeFallbackColor()))

        // The app ships one — and it is drained too, which is the arm this used to get wrong by handing the artwork
        // straight through. The themed-icon slot is *meant* to hold a silhouette, but it is only a convention and a
        // fair number of apps put full-color artwork in it. Those came out colored while every other icon in the set
        // had gone gray, so the one app that shipped the slot correctly-looking was the one that stood out.
        else -> ResolvedLayer(mono, withEffect(monochromeColor()))
    }

    is LayerSource.SolidFill -> ResolvedLayer(ParsedLayer.Color(src.argb), this)

    is LayerSource.CustomImage -> customImage(src.path)?.let { ResolvedLayer(ParsedLayer.Image(it), this) }

    // **A pack that does not cover this app falls back to the app's own artwork, not to nothing.**
    //
    // Dropping the layer looks like the conservative answer and is the destructive one: on the *foreground* it
    // deletes the glyph, so an unthemed app under a pack came out as a bare background plate with no icon on it —
    // worse than having applied no pack at all. Coverage of a few hundred apps is typical, so this is the common
    // case rather than the edge.
    //
    // **Most packs never reach this**, because a pack shipping `iconback` art has its own answer for an app it does
    // not theme, composed one layer down in `IconPackManager` — the treatment every other launcher applies. This is
    // what happens when a pack ships none: the app keeps its own icon, untouched.
    is LayerSource.IconPack -> packImage(src.packPackage, src.drawableName)
        ?.let { ResolvedLayer(ParsedLayer.Image(it), this) }
        ?: appArtwork(icon)?.let { ResolvedLayer(it, this) }
}

/**
 * The app's own parsed content for this layer's role, or null where there is none to have.
 *
 * Shared by [LayerSource.AppDefault] and by a pack layer that misses, which is what makes "a pack falls back to the
 * app's own artwork" literally the same resolution rather than a second one written to match.
 */
private fun IconLayerSpec.appArtwork(icon: ParsedIcon): ParsedLayer? = when (role) {
    LayerRole.FOREGROUND -> icon.foreground
    LayerRole.BACKGROUND -> icon.background
    LayerRole.CUSTOM -> null
}


/**
 * The recoloring **every** [LayerSource.AppDefaultMonochrome] layer takes: the layer's own color effect with the
 * saturation drained out of it.
 *
 * **The word is the specification, and it is one word.** "Monochrome" means the icon goes gray — that is what a user
 * selecting it is asking for, and it has to be true whichever of the two artworks answered the source, or the setting
 * means one thing on some apps and another on the rest. Draining here rather than at either call site is what makes
 * that structural: the branch above chooses *content*, and neither arm gets to choose whether the result is gray.
 *
 * **Which corrects the arm that shipped the app's own themed layer untouched.** The reasoning was that the themed slot
 * holds a solid-alpha silhouette meant to be tinted, so nothing needed applying — true of the slot's *intent* and not
 * of what is in it. It is a convention with no enforcement, and enough apps ship full-color artwork there that the
 * result was the reverse of the feature: switch the whole device to monochrome and those apps stayed in color, so they
 * became the only icons standing out.
 *
 * Drained rather than flattened with [TintMode.SOLID] deliberately — a solid fill would make every app's glyph agree
 * exactly, but it is also what turns a non-silhouette into a featureless blob, and it is available as a tint whenever
 * that is what someone wants. Grayscale is the reading of the word that is safe on artwork we cannot inspect. What it
 * does not equalize is *lightness*: a desaturated color icon lands in mid-gray where a proper silhouette is flat white
 * or black. Equalizing *lightness* as well is deliberately still open.
 */
private fun IconLayerSpec.monochromeColor(): LayerEffect.Color =
    (effects.effectOrNull<LayerEffect.Color>() ?: LayerEffect.Color()).copy(saturation = 0f)

/**
 * [monochromeColor] plus the one thing the **fallback** needs on top of it: [TintMode.SOLID] downgraded to
 * [TintMode.MULTIPLY]. The only place this resolver overrides the user.
 *
 * A solid tint keeps only alpha, which is exactly right over a themed silhouette and disastrous over an ordinary
 * foreground: an adaptive foreground's alpha is usually a large blob, so the icon would come out as a featureless
 * colored splodge. The two are the same setting reaching two different kinds of artwork, and only this layer knows
 * which one it just handed back.
 *
 * It matters most in the **global** studio, which is the whole point of that setting — "make every icon a flat white
 * glyph" is one edit there, and without this it would silently produce blobs for every app without a themed layer.
 * Downgrading gives those apps a grayscale icon instead, which is the same answer they got before a tint was involved.
 *
 * The tint is *kept*, only its mode changes: a multiply over grayscale is the tinted-grayscale recipe, so the color
 * the user picked still shows.
 */
private fun IconLayerSpec.monochromeFallbackColor(): LayerEffect.Color =
    monochromeColor().copy(tintMode = TintMode.MULTIPLY)
