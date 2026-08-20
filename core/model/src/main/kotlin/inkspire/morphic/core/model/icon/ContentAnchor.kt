package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * What something laid over a layer is positioned against — the icon's square box, or the layer's own artwork.
 *
 * **Three things ask this, which is why it is named for neither of them.** An [IconShape] asks where its silhouette
 * sits; [LayerEffect.Bloom] and [LayerEffect.Gloss] ask where their light is laid out. All three resolve it through
 * the same `ShapeMask.InkFit`, so a shape and a bloom both anchored to content on one layer land on the *same
 * square*. The entries carry no `@SerialName`, so `"BOX"` and `"CONTENT"` are the stored contract, and each holder's
 * own field still says what *it* anchors ([IconLayerSpec.shapeAnchor], `Bloom.anchor`, `Gloss.anchor`).
 *
 * A mask needs a frame, and so does a gradient. Until this existed there was exactly one: the box, fixed, so the
 * silhouette stayed put while the content moved under it. That is the right answer for a *plate* — a squircle you
 * push artwork around inside — and the wrong one for *trimming the artwork itself*, which is the other thing users
 * reach a shape for and which the box frame cannot express at all: artwork sitting small and off-center in its
 * canvas would be cropped by a shape it does not even touch.
 *
 * **The two are frames, not an on/off.** Anything anchored is anchored *somewhere*, so this is an enum rather than
 * the `shapeFollowsArtwork` boolean the feature was asked for — the same correction `SideZoneEdge` made to a pair of
 * booleans, and `TintMode` to a multiply that could not be turned off. It also leaves room for a third frame
 * (the icon's *visible* bound, once a backing plate exists) without a second boolean beside the first.
 *
 * **[BOX] is the default, which is what makes this additive.** `encodeDefaults = false` on both icon stores means
 * a stored recipe that never mentioned an anchor reads back as [BOX] — exactly what it rendered as before this
 * existed — so nothing migrates and the test pinning `IconLayerSet.Base`'s stored JSON still passes. Same deal the
 * sealed effect list gives a new effect.
 */
@Serializable
enum class ContentAnchor {

    /**
     * The frame fills the icon's square box and does not move: the layer's zoom, rotation and offset slide the
     * *content* under a silhouette — or under a light — that stays where it is.
     *
     * The plate reading, and the reason it is the default beyond compatibility — a mask that moved with the content
     * could never trim it, so a user dragging the transform sliders would have nothing happen at the edges.
     */
    BOX,

    /**
     * The frame is fitted to the layer's artwork and carried by the layer's transform: it lands on the ink, and
     * zooming, rotating or nudging the layer takes it along.
     *
     * The trim reading. Three things about it are worth knowing before using it:
     *
     * **It is fitted to the ink's bounding *square*, not its rectangle** — side = `ContentMetrics.longestSide`,
     * centered on the ink's center. Stretching a silhouette to a wide logo's rectangle would turn a circle into
     * an ellipse and a hexagon into something that is not a hexagon, which is the one thing a shape must not do;
     * every built-in is authored square and stays square here.
     *
     * **It rotates with the layer**, so a foreground turned 45° inside a rounded square comes out as a rotated
     * rounded square rather than a straight one. That is what "follows the transform" means and it is the whole
     * difference from [BOX] — someone who wants the crop to stay upright while the artwork spins is asking for
     * [BOX], which is why both exist rather than one winning. A bloom or a gloss follows an *in-plane* rotation the
     * same way; neither follows a tilt, `LayerGradient.Frame` carrying no perspective term.
     *
     * **Where the artwork was not measured, this degrades to the box carried by the transform** rather than to
     * nothing. Only the app's own artwork (its foreground and its themed layer) is measured — see
     * `IconLayerResolver`'s note on why measurement and normalization have the same scope — so a pack drawable, an
     * imported image or a flat fill has no ink bounds to fit, and the frame falls back to the full box. It still
     * follows the transform, so the control is never inert; it just cannot trim to something it has not measured.
     *
     * With `IconLayerSpec.normalize` on, the two anchors **coincide** at zoom 1, and that is a consequence rather
     * than a special case: normalizing is precisely rescaling the ink to fill the box, so the artwork's frame and
     * the box's *are* the same frame once it has run.
     */
    CONTENT,
}
