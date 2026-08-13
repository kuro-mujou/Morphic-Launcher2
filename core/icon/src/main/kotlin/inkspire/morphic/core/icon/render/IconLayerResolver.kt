package inkspire.morphic.core.icon.render

import android.graphics.drawable.Drawable
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.parse.ParsedLayer

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
     * @param packImage draws this app from an installed icon pack; `null` when the pack does not cover it, which
     *   is ordinary rather than exceptional.
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

        // **Every app's artwork ends up the same size, and nothing about the icon changes that.** The fit reads each
        // layer's own opaque bounds against the box — it does not consult the background, is not affected by turning
        // one off, and never resizes one.
        return drawn.map { it.normalized() }
    }
}

/** One layer's normalization: a scale about the box's center, and the translate that recenters what it moved. */
private data class IconFit(val scale: Float, val offsetX: Float, val offsetY: Float)

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
 * Two properties fall out rather than being enforced: it can only ever **grow** artwork, since bounds cannot exceed
 * the canvas they were measured in, and it can never **overflow**, since filling the box exactly is what it computes.
 * Earlier versions needed an explicit cap against overflow this shape cannot produce.
 *
 * **Centered**, because scaling happens about the box's middle: artwork sitting off-center is displaced further the
 * more it is enlarged, so the correcting translate carries the same factor that displaced it.
 */
private fun ResolvedLayer.normalized(): ResolvedLayer {
    if (!spec.normalize || spec.role != LayerRole.FOREGROUND) return this
    val metrics = (content as? ParsedLayer.Image)?.metrics ?: return this
    if (metrics.longestSide <= 0f) return this

    val scale = 1f / metrics.longestSide
    return copy(
        spec = spec.scaledBy(
            IconFit(
                scale = scale,
                offsetX = -(metrics.centerX - 0.5f) * scale,
                offsetY = -(metrics.centerY - 0.5f) * scale,
            ),
        ),
    )
}

/**
 * [fit] applied on top of whatever the user set — **multiplying** their zoom and **adding** to their offset, so the
 * Transform controls still read 1.0 and 0 on an untouched layer and still mean "relative to how this icon normally
 * sits". That is also what lets the target be a neutral midpoint rather than a guess that has to be right.
 */
private fun IconLayerSpec.scaledBy(fit: IconFit): IconLayerSpec = copy(
    zoom = zoom * fit.scale,
    offsetX = offsetX + fit.offsetX,
    offsetY = offsetY + fit.offsetY,
)

/**
 * One spec against one app: the content its [LayerSource] points at, paired with the spec to draw it by — or `null`
 * when there is nothing to draw. `AppDefault` is meaningless on a custom layer, so that combination resolves to
 * nothing, as does an [LayerSource.Empty] layer and a pack that does not cover this app.
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

    LayerSource.AppDefault -> when (role) {
        LayerRole.FOREGROUND -> icon.foreground
        LayerRole.BACKGROUND -> icon.background
        LayerRole.CUSTOM -> null
    }?.let { ResolvedLayer(it, this) }

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
        null -> ResolvedLayer(icon.foreground, withColor(monochromeFallbackColor()))

        // The app ships one — and it is drained too, which is the arm this used to get wrong by handing the artwork
        // straight through. The themed-icon slot is *meant* to hold a silhouette, but it is only a convention and a
        // fair number of apps put full-color artwork in it. Those came out colored while every other icon in the set
        // had gone gray, so the one app that shipped the slot correctly-looking was the one that stood out.
        else -> ResolvedLayer(mono, withColor(monochromeColor()))
    }

    is LayerSource.SolidFill -> ResolvedLayer(ParsedLayer.Color(src.argb), this)

    is LayerSource.CustomImage -> customImage(src.path)?.let { ResolvedLayer(ParsedLayer.Image(it), this) }

    is LayerSource.IconPack ->
        packImage(src.packPackage, src.drawableName)?.let { ResolvedLayer(ParsedLayer.Image(it), this) }
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
 * or black. That is L1's `foregroundUniform`/`normalize` question and is deliberately still open.
 */
private fun IconLayerSpec.monochromeColor(): LayerEffect.Color =
    (color ?: LayerEffect.Color()).copy(saturation = 0f)

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
