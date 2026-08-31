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
     * A noise field read as a contour map — inked iso-lines on bare paper by default, or filled height bands as a
     * variant. The topographic look, from the field rather than from traced polylines.
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
     * An even lattice of square tiles, each carrying one flat arc — quarter disc, half disc, whole disc or nothing —
     * the Bauhaus poster. Shapes are drawn at cell scale, so neighbours facing each other join into larger circles;
     * [DesignParams.variant] chooses whether each tile keeps its own colored ground or the shapes float on one.
     */
    @SerialName("bauhaus")
    BAUHAUS,

    /**
     * The frame cut into blocks by recursive splitting, filled from the palette and ruled off in the darkest stop —
     * the Mondrian. The orthogonal, ruled counterpart to [BAUHAUS]'s arcs; Mondrian's three primaries generalized
     * onto whatever palette is chosen.
     */
    @SerialName("mondrian")
    MONDRIAN,

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
     * A contained block of evenly-spaced rounded tiles, stepping through the palette in bands down its rows — the dot
     * grid. Every tile is the same size and only the color moves; [DesignParams.scale] is the margin around the block,
     * which is what decides between a texture and a motif.
     */
    @SerialName("dotGrid")
    DOT_GRID,

    /**
     * A regular grid of dots whose *size* is driven by a noise field — the halftone screen. Shares [DOT_GRID]'s
     * lattice and inverts what varies over it: one color, sizes swelling and dwindling to bare paper, filling the
     * frame rather than sitting in it.
     */
    @SerialName("halftone")
    HALFTONE,

    /**
     * A dense combing of fine, even-seeded hairlines through the flow field — the brushed thin-line texture. The same
     * field as [FLOW_FIELD], but seeded on a lattice and stroked uniformly fine, so it combs the whole frame rather than
     * scattering loose streaks; the first of the thin-line family the studio was missing.
     */
    @SerialName("flowLines")
    FLOW_LINES,

    /**
     * Broad rounded ribbons flowing through the field, each sweeping through the palette down its length — the liquid
     * band look. The same field as [RIBBONS], but the color runs *along* each ribbon rather than being flat, and there
     * is no outline, so the bands melt past each other instead of reading as ruled strokes.
     */
    @SerialName("ribbonFlow")
    RIBBON_FLOW,

    /**
     * A rosette of rotating, shrinking polygon outlines cascading into the frame's center — the spirograph. Not a field
     * like the rest: one shape drawn many times, each copy turned and scaled from the last, so the overlapping edges
     * weave a moiré. [DesignParams.variant] picks the polygon's sides.
     */
    @SerialName("polygonCascade")
    POLYGON_CASCADE,

    /**
     * Parallel bands of flat palette color marching across the frame — the calmest staple, a stripe pattern.
     * [DesignParams.variant] sets the direction (diagonal, vertical, horizontal); no noise, no geometry, just the bands.
     */
    @SerialName("diagonalBands")
    DIAGONAL_BANDS,

    /**
     * Vertical columns stepping once through the palette, each shaded on its right edge for depth — one coarse gradient
     * rendered in panels. Progresses through the palette where [DIAGONAL_BANDS] cycles it, and carries a soft seam shadow.
     */
    @SerialName("gradientColumns")
    GRADIENT_COLUMNS,

    /**
     * Big translucent discs, soft-edged, overlapping into a cloudy painterly field — the misty blend. Leans on palette
     * alpha: discs are laid at partial opacity so overlaps mix. [DesignParams.variant] picks the blend (normal / additive).
     */
    @SerialName("softOverlaps")
    SOFT_OVERLAPS,

    /**
     * Horizontal bands of flat palette color separated by wavy seams that undulate in unison — [DIAGONAL_BANDS] with a
     * wave under it. Clean adjacent bands, unlike [WAVES]' overlapping dunes; [DesignParams.irregularity] sets the wave depth.
     */
    @SerialName("waveDividers")
    WAVE_DIVIDERS,

    /**
     * A palette gradient seen through fluted glass — vertical ribs that refract a diagonal background and carry a
     * specular sheen. Depth from light, not from color; [DesignParams.irregularity] sets the refraction strength.
     */
    @SerialName("ribbedGlass")
    RIBBED_GLASS,
}
