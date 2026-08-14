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
        // `copy` rather than the constructor, and that is not a style choice: the constructor takes [effects] too, so
        // rebuilding positionally would silently drop the whole icon's effects every time a layer moved. Anything
        // else assembling a new layer list must do the same.
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
