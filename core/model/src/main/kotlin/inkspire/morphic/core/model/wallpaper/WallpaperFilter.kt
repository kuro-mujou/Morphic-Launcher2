package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A post-process a generated wallpaper can carry — the studio's *Filters*, applied to the finished bitmap after the
 * generator has drawn it.
 *
 * **A fixed set of strength-knobs, not the icon studio's sealed effect list.** A wallpaper filter is a whole-image
 * pass with one intensity, so the recipe stores a `Map<WallpaperFilter, Float>` — the strength each is turned to,
 * absent meaning off. That is simpler than the layered, ordered `LayerEffect` pipeline an icon needs, and it is all a
 * flat picture with no silhouette wants. The order they apply in is the renderer's (`FilterPipeline`), not the map's.
 *
 * **Only non-silhouette passes belong here.** The icon effects that read a layer's *alpha* as a shape — glow, shadow,
 * outline, bevel — have nothing to read on an opaque wallpaper, which is why this list is the blurs, grades and
 * grains rather than a mirror of `LayerEffect`. Sharing the icon studio's pure per-pixel math is a later refactor —
 * `core:graphics` cannot yet reach `core:icon` — so these are drawn fresh for now.
 *
 * Persisted inside the recipe, so the names are an on-disk contract; an unknown one is dropped by `ignoreUnknownKeys`.
 */
@Serializable
enum class WallpaperFilter {

    /** A real gaussian-ish softening of the whole image. */
    @SerialName("blur")
    BLUR,

    /** The corners weighted down, so the picture reads as lit from its middle. */
    @SerialName("vignette")
    VIGNETTE,

    /** Fine per-pixel noise, the film grain that keeps a flat gradient from banding. */
    @SerialName("grain")
    GRAIN,

    /** Faint horizontal lines — the CRT / retro-screen texture. */
    @SerialName("scanlines")
    SCANLINES,
}
