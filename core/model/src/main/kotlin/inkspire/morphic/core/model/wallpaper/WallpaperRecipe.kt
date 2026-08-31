package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.Serializable

/**
 * Everything needed to reproduce a wallpaper — the stored unit of the studio, and the thing that gets shared.
 *
 * **A recipe is a seed, not a bitmap, and that is the whole design.** Generation is deterministic in [seed]
 * (see `Generator`), so the picture is fully described by *how to draw it* rather than by its pixels. Three things
 * fall out of that: the stored unit is tiny; it re-renders at **any** resolution or aspect, so one recipe serves a
 * phone and a tablet; and the studio's **shuffle** is nothing but a new [seed]. It is also what will make community
 * sharing cheap — a shared recipe is a few bytes re-rendered locally, not a multi-megabyte image.
 *
 * **[filters] are the post-process the generator's bitmap is put through** — a strength per [WallpaperFilter], absent
 * meaning off. Defaulted empty, so a recipe from before filters existed reads back unchanged, and
 * `encodeDefaults = false` writes nothing for an unfiltered wallpaper. A filter this build has *not* heard of is
 * dropped and the rest of the recipe still reads — that is [WallpaperFilterStrengths]' doing, not
 * `ignoreUnknownKeys`', which does not reach inside a map.
 *
 * **Persisted per orientation.** A recipe is composed for a shape ([aspect]) and, like the home grid's coordinate
 * placements, a portrait and a landscape framing are stored separately — the render layer's business, not this
 * type's; this holds one framing.
 *
 * **An unknown [design] is the one thing a reader must survive.** [WallpaperDesign] grows one value per built
 * generator, so a recipe written by a newer build can name a design an older one has never heard of. Serialization of
 * an unknown enum *throws*, so a store that reads these must catch and drop the single recipe rather than fail — the
 * same contract `IconAppearanceCodec` keeps for an unknown effect discriminator.
 *
 * @property design which generator draws it.
 * @property seed the deterministic seed — the same recipe with the same seed renders identically every time, and a
 *   different seed is a different variation of the same design. A `Long` so the space is large enough that a shuffle
 *   never collides in practice.
 * @property params the design's Style knobs. Defaulted, so a recipe that never left the defaults stores nothing for
 *   it under `encodeDefaults = false`.
 * @property palette the colors the generator paints from.
 * @property aspect the shape it is composed for.
 * @property filters the post-process passes and their strengths, applied to the finished bitmap.
 */
@Serializable
data class WallpaperRecipe(
    val design: WallpaperDesign,
    val seed: Long,
    val params: DesignParams = DesignParams(),
    val palette: Palette = Palette.Fallback,
    val aspect: WallpaperAspect = WallpaperAspect.VERTICAL,
    @Serializable(with = WallpaperFilterStrengths::class)
    val filters: Map<WallpaperFilter, Float> = emptyMap(),
)
