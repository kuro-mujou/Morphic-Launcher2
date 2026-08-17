package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * The ordered stack of layers that composites into one app icon, from bottom (index 0) to top. Every set has
 * exactly one [foreground] and one [background] (both permanent), plus any number of custom layers. The sole
 * ordering invariant is that the foreground sits above the background; custom layers may go anywhere — below
 * the background, between, or above the foreground.
 *
 * The invariant is enforced in code, not the type: construction validates it, and the reorder helpers
 * ([moveUp]/[moveDown]) refuse a move that would break it (returning the set unchanged) rather than throwing.
 *
 * ## [effects] — the set's own, applied to the finished icon
 *
 * The layers composite into one picture, and *then* these run over it. **That is a capability rather than a
 * convenience**, which is the whole reason it is not "the same effect copied onto every layer": a glow derives from
 * the finished silhouette, so a per-layer one glows around the foreground *inside* the background plate, where it
 * cannot be seen; a displacement applied per layer produces two independent distortion fields that visibly shear
 * apart at the edge of the glyph; and even a colour matrix is not the same thing applied before compositing as
 * after, the moment opacity or a blend mode is in play.
 *
 * **The same [LayerEffect] type, deliberately.** The composite is a thing with pixels and nothing beneath it — which
 * is a layer's shape minus the two properties that describe *joining a stack* — so both renderers reuse their whole
 * per-layer pipeline on it rather than growing a second one. What it does not get is [IconLayerSpec.opacity] and
 * [IconLayerSpec.blend], and the studio's Effects grid drops exactly those two entries for it, which falls out of
 * the same rule that decides which entries carry a switch.
 *
 * Defaulted empty with `encodeDefaults = false`, so no stored recipe moved to gain it.
 *
 * ## [rotation]/[tiltX]/[tiltY] — which way the finished icon faces
 *
 * The same three angles a layer carries, applied to the finished stack: the layers are drawn **through** these in
 * both renderers, so what turns is the assembled picture rather than each layer separately.
 *
 * **The composite gets the values that say which way it faces, and not the two that say where it is and how big.**
 * That is the whole line, and each half of it holds for its own reason:
 * - **rotation and tilt** are the same kind of statement — one turns the icon in the plane, the others lean it out
 *   of it — so splitting them across two scopes would make one rotation two different kinds of thing, which is the
 *   argument [IconLayerSpec.tiltX] already makes for keeping them together one scope down.
 * - **zoom** is the icon-size setting (`IconSizing`), which is per surface and lives in `data:settings`; a second
 *   scale here would be applied *on top of* it, so one recipe would come out a different size on every grid.
 * - **offset** can only ever slide the icon *out* of the one square there is — nothing exists beyond the box to
 *   bring in — and under a [shape] it is worse, since the mask stays put and what appears is a crescent of missing
 *   icon.
 *
 * **Per-layer angles are not a substitute for either, and the two fail differently.** A tilt cannot be composed at
 * all: layers at different depths of one recipe each get their own vanishing point, so a foreground slides off its
 * background as the angle grows. A rotation *can* — it is affine, so turning every layer by one angle about one
 * center is turning the composite — right up until a layer has an offset, because a layer rotates about the box
 * center and *then* translates, so an arranged layer's position does not travel with the angle. It stops being
 * equivalent exactly when the recipe is interesting.
 *
 * A steep angle does push the picture past the box, where the output crops it — the same bound a turned or leaned
 * *layer* already has, visible while it is being set rather than a surprise later, and absent entirely under a
 * [shape] that keeps the icon clear of the corners.
 *
 * Additive on the same terms as everything else here: defaulted to zero, `encodeDefaults = false`.
 *
 * ## [shape] — the stack-level mask, and why it has no anchor
 *
 * The same [IconShape] a layer can carry, cut against the finished composite. It is what makes "put every icon in a
 * squircle" one control rather than the same shape set on each layer in turn — and it is *not* the same picture as
 * doing that, since a per-layer mask trims each layer before it joins the stack, so a blend or a bloom that reaches
 * past a layer's own silhouette escapes the shape.
 *
 * **This one escapes nothing, which is what makes it a boundary rather than one more mask** — so it is applied
 * *twice*, and the second pass is [effectTrimShape]. A layer's shape sits before that layer's effects deliberately;
 * the stack's sits before **and** after its own.
 *
 * **No [IconLayerSpec.shapeAnchor] here, and that is a property of the composite rather than a control left out.**
 * An anchor chooses between the box and the layer's own *artwork carried by its transform* — the composite has no
 * measured ink to fit to, and [tiltX] is not a frame anything can be laid out in (a lean has no in-plane position or
 * rotation, which is all a frame is made of). That is exactly why [effects] anchored to content already fall back to
 * the box in both renderers. Offering the switch would be offering a choice with one reachable answer.
 *
 * Additive on the same terms as [effects]: defaulted null, `encodeDefaults = false`.
 *
 * ## Why the recipe lives in `core:model` and not beside the renderer
 *
 * This whole package is an icon's **recipe** — pure data describing what an icon should look like — while
 * turning it into pixels is `core:icon`'s job. That split is this codebase's third of the same kind, after
 * `BackdropEffect` (model here, rendering in `core:designsystem`) and `DeviceConfiguration` (pure enum here,
 * detection in `core:designsystem`), and it is what lets the two modules that **store** a set — `data:settings`
 * for the global default, `data:icons` for per-app overrides — persist it without either taking a dependency on
 * a module that allocates bitmaps.
 *
 * It also means serialization belongs here rather than there: a set is written as one JSON blob, so every type
 * in this package is `@Serializable` and their `@SerialName`s are a stable on-disk contract.
 */
@Serializable
data class IconLayerSet(
    val layers: List<IconLayerSpec>,
    val rotation: Float = 0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val shape: IconShape? = null,
    val effects: List<LayerEffect> = emptyList(),
) {

    init {
        val fg = layers.count { it.role == LayerRole.FOREGROUND }
        val bg = layers.count { it.role == LayerRole.BACKGROUND }
        require(fg == 1) { "an icon layer set needs exactly one foreground layer, had $fg" }
        require(bg == 1) { "an icon layer set needs exactly one background layer, had $bg" }
        require(foregroundAboveBackground(layers)) { "the foreground layer must sit above the background" }
    }

    /** The whole-icon effects that actually draw, in the order they are applied. @see activeEffects */
    val activeEffects: List<LayerEffect> get() = effects.activeEffects

    /**
     * The shape the **finished** icon is trimmed back to, or `null` when nothing could have escaped [shape].
     *
     * **The stack mask catches everything, and that only happens if it is applied after the effects as well as
     * before.** Before, because everything derived from a silhouette — an outline, a bevel, an inner shadow — has to
     * read the *shaped* icon rather than the square it was cut from, or it traces a boundary the icon does not have.
     * After, because a good half of the effect list grows alpha outward: a blur spreads it, a glow and an extrusion
     * put copies beside it, a chromatic split displaces it. Applied only before, all of that lands outside the shape
     * and is stopped instead by the one edge left — the **box** — so a rounded icon comes out ringed by a squared-off
     * fringe of haze with corners the shape was chosen to remove.
     *
     * **Shared rather than re-decided in each renderer**, for the reason every derivation in `core:icon`'s `render`
     * package is: a path that forgot the trim would draw a perfectly plausible icon, and the effects that need it most
     * are the ones the live path cannot draw at all — the studio previews those from the bake, so the editor could not
     * show you the difference.
     *
     * Null with no effects, which is every unedited icon: one mask rather than two, and the icon's antialiased edge is
     * not multiplied by the silhouette twice.
     */
    val effectTrimShape: IconShape? get() = shape?.takeIf { activeEffects.isNotEmpty() }

    /**
     * Whether the **live** render path can draw this whole icon, or whether the studio must preview it from the bake.
     *
     * **Asked of the whole set rather than layer by layer, and that is the decision rather than a convenience.** A
     * per-layer fallback would mean a *hybrid* preview — one layer from a bitmap, the rest drawn live around it — so
     * the two halves would have to agree about geometry at a seam **inside a single icon**. That is the two-renderer
     * hazard in its worst form: no longer two whole pictures that can be held up against each other, but one picture
     * assembled from both, where a drift shows as a misalignment with nothing to compare it to. It also costs
     * nothing, since the bake renders a whole set either way and its expense is the *effect* rather than the number
     * of layers.
     *
     * [IconLayerSpec.drawsLive] keeps its own job despite this, which is worth saying because it looks redundant
     * next to it: a *layer tile* in the studio's rail draws one layer alone, so it falls back on that layer's own
     * effects. One question, two scopes, each asking about what it actually draws.
     */
    val drawsLive: Boolean get() = layers.all { it.drawsLive } && effects.drawLive

    /** The single, always-present background layer. */
    val background: IconLayerSpec get() = layers.first { it.role == LayerRole.BACKGROUND }

    /** The single, always-present foreground layer. */
    val foreground: IconLayerSpec get() = layers.first { it.role == LayerRole.FOREGROUND }

    /**
     * Where the foreground sits in the stack.
     *
     * Beside [foreground] because an editor needs the *index* — it is what a selection is — and finding it by role at
     * each call site is the kind of small duplication that ends up disagreeing. Never `-1`: the `init` above requires
     * exactly one.
     */
    val foregroundIndex: Int get() = layers.indexOfFirst { it.role == LayerRole.FOREGROUND }

    /** Moves the layer at [index] one step toward the top; a no-op (returns `this`) when the move is illegal. */
    fun moveUp(index: Int): IconLayerSet = swap(index, index + 1)

    /** Moves the layer at [index] one step toward the bottom; a no-op (returns `this`) when the move is illegal. */
    fun moveDown(index: Int): IconLayerSet = swap(index, index - 1)

    private fun swap(i: Int, j: Int): IconLayerSet {
        if (i !in layers.indices || j !in layers.indices) return this
        val reordered = layers.toMutableList()
        reordered[i] = layers[j]
        reordered[j] = layers[i]
        // A swap never changes the role counts, so only the fg-above-bg order can be violated.
        //
        // `copy` rather than the constructor, and that is not a style choice: the constructor takes the whole icon's
        // angles, mask and effects too, so rebuilding positionally would silently drop all of them every time a
        // layer moved. Anything else assembling a new layer list must do the same.
        return if (foregroundAboveBackground(reordered)) copy(layers = reordered) else this
    }

    companion object {
        /**
         * The default two-layer set every app starts from: an app-default background beneath an app-default
         * foreground. The starting point for both the global default and any per-app customization.
         */
        val Base: IconLayerSet = IconLayerSet(
            listOf(
                IconLayerSpec(role = LayerRole.BACKGROUND, source = LayerSource.AppDefault),
                IconLayerSpec(role = LayerRole.FOREGROUND, source = LayerSource.AppDefault),
            ),
        )
    }
}

/** True when the foreground layer sits above (a higher index than) the background layer. */
private fun foregroundAboveBackground(layers: List<IconLayerSpec>): Boolean {
    val fg = layers.indexOfFirst { it.role == LayerRole.FOREGROUND }
    val bg = layers.indexOfFirst { it.role == LayerRole.BACKGROUND }
    return fg > bg
}
