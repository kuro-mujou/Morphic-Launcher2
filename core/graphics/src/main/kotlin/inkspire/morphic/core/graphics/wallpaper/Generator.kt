package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperDesign

/**
 * Draws one design to a bitmap — the wallpaper studio's engine seam.
 *
 * **Deterministic in [seed], and that is a contract the whole studio leans on.** The same arguments must always
 * produce the same pixels: it is what lets a recipe be *stored as a seed* rather than a bitmap, what makes the
 * studio's **shuffle** just a new seed, and what lets a design be re-rendered at any size for any screen. A generator
 * that reached for an unseeded `Random`, the clock, or any ambient state would break all three silently — the
 * picture would drift between a preview and the applied wallpaper, and a shared recipe would not reproduce.
 *
 * **Static, not animated — no `phase`.** The studio's swipe-to-mutate is a *discrete re-seed with an animated
 * transition* between two static renders, not a continuous parameter threaded through the generator (see the plan's
 * Motion section for the evidence). So a generator's only notion of "which variation" is [seed]; the motion lives in
 * a transition layer above this, and the generator stays a pure function of its inputs.
 *
 * **Returns its own new [Bitmap].** The caller owns and eventually recycles it; a generator must not cache or reuse
 * one across calls, since two renders at two sizes (a preview and a full bake) are routinely live at once.
 */
interface Generator {

    /**
     * Which of [DesignParams]' knobs this generator actually reads, and what it calls them — what the studio's Style
     * panel offers for this design.
     *
     * **Abstract rather than defaulted**, for the reason [Generators]' `when` is total: a generator that forgot to
     * declare its knobs would silently offer none, and "this design has no parameters" is a claim that should have to
     * be typed. [DesignStyle] carries the argument for why it is declared here at all.
     */
    val style: DesignStyle

    /**
     * The knobs this generator reads *at [params]* — the same declaration as [style] for a design whose choices all
     * answer to the same set, which is most of them.
     *
     * **Overridden only where a choice genuinely takes away a knob or gives one**, because "absent, not disabled" is
     * a standing rule and a knob that changes nothing is the silent failure [DesignStyle] exists to prevent — worse
     * here than usual, since the panel would offer it beside knobs that do work. Flow Field is the original case: its
     * *Pearls* beads a share of its lines and its *Eclectic* has no beads to control.
     *
     * **It takes the whole [DesignParams] rather than a variant index**, because the variant is not the only choice a
     * knob set can hang off. The Polygon Cascade's shadow belongs to its *filled* finish and is meaningless on an
     * outline, which the narrower signature could not say at all — it would have had to offer a dead knob under
     * Stroke, or drop the shadow. A generator still reads only the fields it branches on; passing the rest costs
     * nothing and means the next design that gates on a different one needs no second widening.
     *
     * **Only the *presence* and *naming* of knobs may depend on [params], never a value.** The panel asks this to
     * decide which tabs to draw, so a `DesignStyle` that changed with, say, `density` would rebuild the tab row under
     * a moving finger. Gate on the choices — [DesignParams.variant] and [DesignParams.finish] — not on the sliders.
     *
     * Defaulted rather than replacing [style] so the designs whose knobs do not vary say nothing, and so the sweeps
     * that enumerate a design's choices still have one declaration to ask for the option lists themselves.
     */
    fun styleFor(params: DesignParams): DesignStyle = style

    /**
     * A `[width] × [height]` bitmap of this design, painted from [palette], tuned by [params], varied by [seed].
     *
     * [width] and [height] are pixels and may be any positive size and aspect — the generator frames itself to fit
     * rather than assuming a square, which is the difference from the icon bake that always works on a square.
     */
    fun render(width: Int, height: Int, palette: Palette, params: DesignParams, seed: Long): Bitmap
}

/**
 * The one place a [WallpaperDesign] is resolved to the [Generator] that draws it.
 *
 * **A total `when`, on purpose.** Every design in the enum has a generator here, and the compiler enforces it — which
 * is exactly why [WallpaperDesign] grows one value per built generator rather than listing the whole planned catalog
 * up front. A partial registry with a null fallback was the alternative and is worse: it turns "this design is not
 * built yet" from a compile error into a blank wallpaper nobody asked for.
 */
object Generators {

    /**
     * The generator for [design].
     *
     * Its complexity *is* the catalog's length — one arm per design, and adding a design is meant to add an arm —
     * so the branch-count check is suppressed rather than worked around. Splitting the table into helpers to score
     * better would break the one property it exists for: that the compiler refuses a design with no generator.
     */
    @Suppress("CyclomaticComplexMethod")
    fun forDesign(design: WallpaperDesign): Generator = when (design) {
        WallpaperDesign.LINEAR_GRADIENT -> LinearGradientGenerator
        WallpaperDesign.MESH_GRADIENT -> MeshGradientGenerator
        WallpaperDesign.FLOW_FIELD -> FlowFieldGenerator
        WallpaperDesign.TRIANGULAR_FACETS -> TriangularFacetsGenerator
        WallpaperDesign.VORONOI -> VoronoiGenerator
        WallpaperDesign.PLASMA -> PlasmaGenerator
        WallpaperDesign.CONTOUR -> ContourGenerator
        WallpaperDesign.WAVES -> WavesGenerator
        WallpaperDesign.BAUHAUS -> BauhausGenerator
        WallpaperDesign.MONDRIAN -> MondrianGenerator
        WallpaperDesign.CONFETTI -> ConfettiGenerator
        WallpaperDesign.RINGS -> RingsGenerator
        WallpaperDesign.TRUCHET -> TruchetGenerator
        WallpaperDesign.METABALLS -> MetaballsGenerator
        WallpaperDesign.RIBBONS -> RibbonsGenerator
        WallpaperDesign.RAYS -> RaysGenerator
        WallpaperDesign.DOT_GRID -> DotGridGenerator
        WallpaperDesign.HALFTONE -> HalftoneGenerator
        WallpaperDesign.FLOW_LINES -> FlowLinesGenerator
        WallpaperDesign.RIBBON_FLOW -> RibbonFlowGenerator
        WallpaperDesign.POLYGON_CASCADE -> PolygonCascadeGenerator
        WallpaperDesign.DIAGONAL_BANDS -> DiagonalBandsGenerator
        WallpaperDesign.GRADIENT_COLUMNS -> GradientColumnsGenerator
        WallpaperDesign.LOUVERS -> LouversGenerator
        WallpaperDesign.SOFT_OVERLAPS -> SoftOverlapsGenerator
        WallpaperDesign.WAVE_DIVIDERS -> WaveDividersGenerator
        WallpaperDesign.RIBBED_GLASS -> RibbedGlassGenerator
        WallpaperDesign.VITRALL -> VitrallGenerator
        WallpaperDesign.MODERN_MOSAIC -> ModernMosaicGenerator
        WallpaperDesign.ROUNDED_TILES -> RoundedTilesGenerator
    }
}
