package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe

/**
 * Two recipes prepared to scrub between — resolved once when a swipe begins, asked for a moment on every frame of it.
 *
 * **This is the studio's seam onto the morph engine, and it is deliberately narrower than the engine.** Planning,
 * merging and interpolating are `core:graphics`' business and stay internal; what a screen needs is "can these two be
 * scrubbed between, and if so paint me `t`". Widening it to the whole seam would put a `Plan` in a composable, which
 * is the shape the icon subsystem's two-renderer hazard grew out of.
 *
 * **The expensive half is [WallpaperMorphs.between]; this is the cheap half.** Building one plans both pictures and,
 * for a subdivision, merges their cuts; asking for a moment re-cuts or re-evaluates. That split is why a scrub is
 * prepared while nothing is happening and only asked for moments once a finger is down — see
 * docs/MORPH_ENGINE_PLAN.md.
 *
 * **An interface with one implementation per bucket, because the two buckets do genuinely different work.** A
 * subdivision re-cuts the frame into polygons and issues draw calls; a field re-evaluates a small buffer of pixels and
 * lets the canvas blow it up. What they share is this signature, and that is the extent of what they can share.
 */
interface WallpaperMorph {

    /**
     * Paints the picture [t] of the way across into [canvas], at `[width]` × `[height]` pixels.
     *
     * Cheap enough for a frame, and it must be: this is called from a draw pass.
     */
    fun draw(canvas: Canvas, t: Float, width: Int, height: Int)
}

/**
 * A subdivision scrub: the cuts interpolate and the cells are re-derived, at full resolution.
 *
 * **Full resolution is not a choice here.** The picture is made of edges, and an edge is exactly the thing a
 * downscale destroys — the inversion that separates the two buckets. It is affordable because the cost is per
 * *element*, and there are a few hundred of them.
 */
private class SubdivisionMorph(
    private val morph: VitrallGenerator.Morph,
    private val palette: Palette,
) : WallpaperMorph {

    override fun draw(canvas: Canvas, t: Float, width: Int, height: Int) {
        VitrallGenerator.draw(canvas, morph.at(t), palette, width, height)
    }
}

/**
 * A field scrub: the lattice interpolates and the field is re-evaluated on a small buffer, blown up by the canvas.
 *
 * **The downscale is where this bucket's affordability comes from**, and its resolution is the design's own measured
 * floor rather than one number for everything — see [MeshGradientGenerator.ScrubShortSide].
 */
private class FieldMorph(private val morph: MeshGradientGenerator.Morph) : WallpaperMorph {

    override fun draw(canvas: Canvas, t: Float, width: Int, height: Int) {
        MeshGradientGenerator.draw(canvas, morph.at(t), width, height, MeshGradientGenerator.ScrubShortSide)
    }
}

/** Where the studio asks whether two recipes can be scrubbed between, and gets the scrub if they can. */
object WallpaperMorphs {

    /**
     * A scrub from [from] to [to] in a frame `[width]` × `[height]`, or **null where these two cannot be scrubbed**
     * and the caller should fall back to whatever it does for a discrete change.
     *
     * **Null rather than a degraded scrub**, because every way of failing here is a way of putting a *different*
     * picture on screen than the one that will be applied. The refusals:
     *
     * - **A design with no plan seam.** Vitrall and the mesh gradient are the two so far, one per bucket; M6 of the
     *   morph plan is the rest of the catalog, one generator at a time. Everything else has a `render` and nothing to
     *   interpolate.
     * - **Two different designs.** Two pictures of one design share a *kind* of construction, which is what makes
     *   them interpolable at all; a Vitrall and a mesh gradient share nothing to put into correspondence.
     * - **Two different palettes.** Interpolating two palettes is a real thing to build — the reference does it, its
     *   ground going black to cream across one swipe — and it is not built. Worse for the field bucket, whose plan
     *   has the colours resolved into it, so there is no palette left at draw time to swap.
     * - **Any filter on either side.** A scrub draws to a canvas and never becomes an `IntArray`, so the per-pixel
     *   passes have nothing to run on. Applying what the canvas can reach and resolving the rest on settle is the
     *   design in the morph plan; until the filters are split by whether the live path can reach them at all, a
     *   scrub past one would pop at both ends of the gesture.
     * - **Two field lattices of different sizes**, which is the mesh gradient's own refusal and only reachable if a
     *   scrub ever spans two densities. Nothing does: a shuffle re-seeds and leaves every knob alone.
     *
     * **Plans both pictures, so it belongs off the main thread.** For a subdivision it also merges two trees, which
     * is the larger half; for a field it is two structs.
     */
    fun between(from: WallpaperRecipe, to: WallpaperRecipe, width: Int, height: Int): WallpaperMorph? {
        if (from.design != to.design) return null
        if (from.palette != to.palette) return null
        if (from.filters.isNotEmpty() || to.filters.isNotEmpty()) return null

        val palette = PaletteColorMode.resolve(from.palette, from.params.colorMode)
        return when (from.design) {
            WallpaperDesign.VITRALL -> SubdivisionMorph(
                VitrallGenerator.morph(
                    VitrallGenerator.plan(width, height, from.params, from.seed),
                    VitrallGenerator.plan(width, height, to.params, to.seed),
                ),
                palette,
            )

            WallpaperDesign.MESH_GRADIENT -> MeshGradientGenerator.morph(
                MeshGradientGenerator.plan(from.params, palette, from.seed),
                MeshGradientGenerator.plan(to.params, PaletteColorMode.resolve(to.palette, to.params.colorMode), to.seed),
            )?.let(::FieldMorph)

            else -> null
        }
    }
}
