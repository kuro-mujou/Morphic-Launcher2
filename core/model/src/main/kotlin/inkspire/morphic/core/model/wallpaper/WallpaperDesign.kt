package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which generator a wallpaper recipe is drawn by — the studio's *design*.
 *
 * **This enum grows one value per built generator, and no faster.** The full catalog the plan sets out is
 * twenty-two designs (see `docs/WALLPAPER_STUDIO_PLAN.md`), but a design id with no generator behind it is a value
 * `core:graphics` cannot render and a recipe cannot honor — a model in a vacuum. So the id is added *together with*
 * its generator, which is what keeps `Generators` a **total** `when` over this enum (the compiler then refuses to let
 * a generator be forgotten when a value is added). The catalog lives in the plan; this holds only what is real.
 *
 * Persisted inside the recipe, so the names are an on-disk contract. A recipe naming a design an older build does not
 * have is the one case a reader has to handle — see [WallpaperRecipe].
 */
@Serializable
enum class WallpaperDesign {

    /**
     * A gradient climbing the frame through the palette's stops — the simplest real design, and the one that proves
     * the whole pipeline (recipe → generator → bitmap) end to end without needing any of the gart engine.
     */
    @SerialName("linearGradient")
    LINEAR_GRADIENT,

    /**
     * A soft field where seeded points each pull the picture toward a palette color — the lava-lamp blend. The first
     * of the gart-harvest designs, and the gentlest: pure inverse-distance weighting, no geometry.
     */
    @SerialName("meshGradient")
    MESH_GRADIENT,

    /**
     * Streamlines traced through a noise flow field and drawn as strokes — the swirl. The first design built on the
     * real generative engine: a particle dropped in the field follows it, and hundreds of them trace the streaks.
     */
    @SerialName("flowField")
    FLOW_FIELD,

    /**
     * The frame tiled into flat-shaded triangles over a palette gradient — the low-poly look. A jittered grid rather
     * than a Delaunay of random points, so the facets are even and there are no slivers.
     */
    @SerialName("triangularFacets")
    TRIANGULAR_FACETS,

    /**
     * The frame broken into flat cells around scattered seeds, each edged in the palette's darkest tone — the
     * stained-glass mosaic. A nearest-seed diagram rather than a polygon Voronoi, so there is no fragile geometry;
     * the irregular cells are what set it apart from the even triangles of [TRIANGULAR_FACETS].
     */
    @SerialName("voronoi")
    VORONOI,

    /**
     * Overlapping sine waves summed into a rippling interference field, read through the palette — the demoscene
     * plasma. A still frame of gart's animated plasma, its phases fixed by the seed.
     */
    @SerialName("plasma")
    PLASMA,

    /**
     * A noise field read as a contour map — quantized into height bands, each a palette color, inked along every band
     * boundary. The topographic look, from the field rather than from traced polylines.
     */
    @SerialName("contour")
    CONTOUR,

    /**
     * Overlapping wave bands rising up the frame, each a flat palette color lapping over the one behind it — the
     * layered-dune / ridgeline look. Sine crests rather than noise, for a rolling, near-periodic swell.
     */
    @SerialName("waves")
    WAVES,

    /**
     * The frame cut into blocks by recursive splitting, filled from the palette and ruled off in the darkest stop —
     * the Bauhaus / Mondrian look. Mondrian's three primaries generalized onto whatever palette is chosen.
     */
    @SerialName("bauhaus")
    BAUHAUS,

    /**
     * Evenly-strewn discs in palette colors on a dark ground — confetti. Poisson-disk sampling, so the discs are spaced
     * rather than clumped like a uniform scatter.
     */
    @SerialName("confetti")
    CONFETTI,

    /**
     * Concentric rings of palette color rippling out from an off-center point — the echo / sonar op-art. Distance
     * banded through the looped palette, the radial sibling of [PLASMA].
     */
    @SerialName("rings")
    RINGS,

    /**
     * A grid of quarter-arc tiles turned at random that join into a maze of flowing loops — the Truchet pattern.
     * Emergent from the tiling: arcs meet every edge at its midpoint, so neighbours always connect.
     */
    @SerialName("truchet")
    TRUCHET,

    /**
     * Seeded charges whose potential fields merge into gooey blobs, banded through the palette — the lava-lamp look.
     * A potential field, not discs, which is why the blobs join; banded, unlike the smooth [MESH_GRADIENT].
     */
    @SerialName("metaballs")
    METABALLS,

    /**
     * A few thick ribbons traced through a flow field, outlined against a dark ground — Neon Ribbons. The same field
     * as [FLOW_FIELD], drawn as broad outlined strokes instead of fine streaks.
     */
    @SerialName("ribbons")
    RIBBONS,

    /**
     * Hard-edged wedges of palette color fanning from an off-center point — the sunburst. The angular sibling of
     * [RINGS], which bands distance; this bands bearing.
     */
    @SerialName("rays")
    RAYS,

    /**
     * A regular grid of dots whose size and color are driven by a noise field — the halftone screen. A lattice, the
     * opposite of [CONFETTI]'s scatter; dots dwindle to bare paper where the field is weak.
     */
    @SerialName("dotGrid")
    DOT_GRID,
}
