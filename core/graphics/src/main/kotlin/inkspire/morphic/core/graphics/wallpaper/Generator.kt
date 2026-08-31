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

    /** The generator for [design]. */
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
        WallpaperDesign.CONFETTI -> ConfettiGenerator
        WallpaperDesign.RINGS -> RingsGenerator
        WallpaperDesign.TRUCHET -> TruchetGenerator
        WallpaperDesign.METABALLS -> MetaballsGenerator
        WallpaperDesign.RIBBONS -> RibbonsGenerator
        WallpaperDesign.RAYS -> RaysGenerator
        WallpaperDesign.DOT_GRID -> DotGridGenerator
        WallpaperDesign.FLOW_LINES -> FlowLinesGenerator
        WallpaperDesign.RIBBON_FLOW -> RibbonFlowGenerator
        WallpaperDesign.POLYGON_CASCADE -> PolygonCascadeGenerator
        WallpaperDesign.DIAGONAL_BANDS -> DiagonalBandsGenerator
        WallpaperDesign.GRADIENT_COLUMNS -> GradientColumnsGenerator
        WallpaperDesign.SOFT_OVERLAPS -> SoftOverlapsGenerator
        WallpaperDesign.WAVE_DIVIDERS -> WaveDividersGenerator
        WallpaperDesign.RIBBED_GLASS -> RibbedGlassGenerator
    }
}
