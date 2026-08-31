package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
 * Persisted inside the recipe, so the names are an on-disk contract. An unknown one is dropped, but *not* by
 * `ignoreUnknownKeys` — see [WallpaperFilterStrengths], which is what actually drops it.
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

/**
 * The recipe's `filters` map, read so that a filter this build has never heard of is **dropped instead of throwing**.
 *
 * **`ignoreUnknownKeys` does not reach in here, which is the whole reason this exists.** That flag skips an unknown
 * *property* of a class; the entries of this map are not properties, they are values of [WallpaperFilter], and an
 * unrecognized enum value throws unconditionally. So a recipe written by a newer build with one extra filter turned
 * on used to fail the entire read — losing a wallpaper the reader could otherwise have drawn correctly in every
 * respect but one pass. Decoding through `Map<String, Float>` and discarding the names that do not resolve is what
 * makes the recipe degrade to the rest of itself, as its KDoc promises.
 *
 * Encoding is unchanged — every key is a real enum value on the way out, so this only ever *narrows* on the way in.
 *
 * Deliberately **not** applied to [WallpaperRecipe.design]: dropping an unknown design would leave a recipe claiming
 * to be some other picture entirely, so that one still throws and the store drops the whole row.
 */
object WallpaperFilterStrengths : KSerializer<Map<WallpaperFilter, Float>> {

    private val delegate = MapSerializer(String.serializer(), Float.serializer())

    // Read off the descriptor rather than re-typed here, so the @SerialName annotations above stay the single source
    // of the on-disk names — a rename there cannot silently miss this map.
    private val names: List<String> =
        WallpaperFilter.entries.map { WallpaperFilter.serializer().descriptor.getElementName(it.ordinal) }

    private val byName: Map<String, WallpaperFilter> = WallpaperFilter.entries.associateBy { names[it.ordinal] }

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<WallpaperFilter, Float>) {
        delegate.serialize(encoder, value.mapKeys { (filter, _) -> names[filter.ordinal] })
    }

    override fun deserialize(decoder: Decoder): Map<WallpaperFilter, Float> =
        buildMap {
            delegate.deserialize(decoder).forEach { (name, strength) ->
                byName[name]?.let { put(it, strength) }
            }
        }
}
