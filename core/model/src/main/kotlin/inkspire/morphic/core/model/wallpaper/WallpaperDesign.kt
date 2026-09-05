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
 *
 * **A value is removed the same way, and it costs nothing today only because nothing stores a recipe yet.** `rings`
 * and `rays` were dropped when the two radial designs were: both drew a target — a bullseye or a pinwheel with a
 * point of convergence sitting among the icons — and reworking the origin off the frame turned them into designs the
 * catalog already had. When a store does exist, dropping a value is the *reader's* unknown-design path run against
 * our own history rather than a newer build's, so it lands in the same place: catch, drop the recipe, keep the rest.
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
     * The frame cut into flat bands by long smooth crests, each band a palette color with the next one's shadow
     * falling across it. A crest is a height at the frame's left edge and a height at its right, joined by a
     * smoothstep, so the whole stack shares one sweep until *Distortion* gives each crest lobes of its own.
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
     * Discs strewn across the frame in palette colors — confetti. A square lattice turned off the frame and pushed
     * around, so it reads as an even sprinkle rather than a grid; the palette is spent unevenly, so the last stops are
     * rare accents. [DesignParams.variant] is a depth of field over the discs' own sizes.
     */
    @SerialName("confetti")
    CONFETTI,

    /**
     * A grid of quarter-arc tiles turned at random that join into a maze of flowing loops — the Truchet pattern.
     * Emergent from the tiling: arcs meet every edge at its midpoint, so neighbours always connect.
     */
    @SerialName("truchet")
    TRUCHET,

    /**
     * Seeded charges whose potential fields merge, cut into stacked layers of flat color — the paper-cut relief. A
     * potential field, not discs, which is why the blobs join; banded, unlike the smooth [MESH_GRADIENT]. Push the
     * charge count up and the cores stop being findable, so the layers read as long organic ridges rather than as
     * rings; [DesignParams.depth] casts the shadow of each layer onto the one below it.
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
     * One palette gradient seen through parallel strips, each showing it from a slightly different place — the
     * louvered blind. Shares [GRADIENT_COLUMNS]' columns and inverts what runs through them: the ramp goes *along*
     * each strip rather than stepping across the set, so one strip is a gradient rather than a flat panel and the
     * frame reads as a single soft wash that staircases as it crosses them. [DesignParams.scale] spreads the
     * palette's inner stops from one hard edge out to an even ramp, and [DesignParams.irregularity] is how far the
     * ramp slides from strip to strip — at `0` every strip is identical and the design is a plain gradient.
     */
    @SerialName("louvers")
    LOUVERS,

    /**
     * Big translucent discs, soft-edged, overlapping into a cloudy painterly field — the misty blend. Leans on palette
     * alpha: discs are laid at partial opacity so overlaps mix. [DesignParams.variant] picks the blend (normal / additive).
     */
    @SerialName("softOverlaps")
    SOFT_OVERLAPS,

    /**
     * Equal bands of flat palette color divided by one wave drawn again and again down the frame, every divider the
     * same shape at the same phase. Clean adjacent bands, unlike [WAVES]' crests, which each have their own shape and
     * lap over one another.
     */
    @SerialName("waveDividers")
    WAVE_DIVIDERS,

    /**
     * The frame cut and re-cut by edge-to-edge chords into leaded panes of tinted glass — the stained-glass window.
     * Cutting with *lines* rather than around points is what makes its panes long shards and slender wedges where
     * [VORONOI]'s are compact blobs; [DesignParams.depth] fills each with a gradient, which is what makes it glass.
     */
    @SerialName("vitrall")
    VITRALL,

    /**
     * A palette gradient seen through fluted glass — vertical ribs that refract a diagonal background and carry a
     * specular sheen. Depth from light, not from color; [DesignParams.irregularity] sets the refraction strength.
     */
    @SerialName("ribbedGlass")
    RIBBED_GLASS,

    /**
     * The frame subdivided into rounded tiles floating on a wide grout — the modern mosaic. [MONDRIAN]'s construction
     * and the opposite of its finish: where that rules an ink line between blocks that touch, this pulls every tile
     * back from its own edges so the ground shows between them, rounds the corners, and pushes the shared ones off
     * square. [DesignParams.variant] is how lopsided a cut may be, which is what makes the tile sizes a set rather
     * than a spread.
     */
    @SerialName("modernMosaic")
    MODERN_MOSAIC,

    /**
     * A fan of long rounded bars sweeping across the frame, each carrying a gradient along its own length.
     *
     * **[TRUCHET]'s neighbour and not its rival.** That one tiles the frame with quarter-arcs that *join* into a maze;
     * this lays a set of separate capsules side by side and gives each its own angle, so they radiate rather than
     * connect. [DesignParams.rotation] is that fan — at `0` the bars stay parallel and the picture is a stack of
     * stripes — and [DesignParams.roundness] is their length, which at `1` has shortened every one to its own cap and
     * left a column of circles.
     */
    @SerialName("roundedTiles")
    ROUNDED_TILES,

    /**
     * Ragged translucent dabs laid along a sweep across the frame, building into a painted wash — the impasto. The
     * catalog's first **painterly** design: not a field like [PLASMA] and not a shape like [SOFT_OVERLAPS], but a
     * heap of overlapping marks whose torn edges are the texture, so the picture is what the marks do together.
     * [DesignParams.depth] is how many dabs each mark is built from, which is literally how thick the paint is.
     */
    @SerialName("impasto")
    IMPASTO,

    /**
     * Particles carried through a wave field and left as a mist of translucent dots — the spray. The fifth design on
     * a flow field and the first that draws no line: [FLOW_FIELD] and its siblings render a particle's *path*, this
     * renders the particle, so what accumulates is a density rather than a stroke.
     * [DesignParams.irregularity] is how far the field turns, and at `0` the mist falls into parallel lanes.
     */
    @SerialName("spray")
    SPRAY,

    /**
     * A disc of stirred pigment on a dark ground, ringed and shadowed so it sits in the page — the planet. The
     * catalog's first design that is an **object** rather than a surface: everything else fills the frame, this draws
     * one thing with air around it. [SPRAY]'s particles under a clip, which is what turns a mist into weather on a
     * sphere; [DesignParams.variant] picks which field stirs it.
     */
    @SerialName("planet")
    PLANET,
}
