package inkspire.morphic.core.icon.layer

import kotlinx.serialization.Serializable

/**
 * The ordered stack of layers that composites into one app icon, from bottom (index 0) to top. Every set has
 * exactly one [foreground] and one [background] (both permanent), plus any number of custom layers. The sole
 * ordering invariant is that the foreground sits above the background; custom layers may go anywhere — below
 * the background, between, or above the foreground.
 *
 * The invariant is enforced in code, not the type: construction validates it, and the reorder helpers
 * ([moveUp]/[moveDown]) refuse a move that would break it (returning the set unchanged) rather than throwing.
 */
@Serializable
data class IconLayerSet(val layers: List<IconLayerSpec>) {

    init {
        val fg = layers.count { it.role == LayerRole.FOREGROUND }
        val bg = layers.count { it.role == LayerRole.BACKGROUND }
        require(fg == 1) { "an icon layer set needs exactly one foreground layer, had $fg" }
        require(bg == 1) { "an icon layer set needs exactly one background layer, had $bg" }
        require(foregroundAboveBackground(layers)) { "the foreground layer must sit above the background" }
    }

    /** The single, always-present background layer. */
    val background: IconLayerSpec get() = layers.first { it.role == LayerRole.BACKGROUND }

    /** The single, always-present foreground layer. */
    val foreground: IconLayerSpec get() = layers.first { it.role == LayerRole.FOREGROUND }

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
        return if (foregroundAboveBackground(reordered)) IconLayerSet(reordered) else this
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
