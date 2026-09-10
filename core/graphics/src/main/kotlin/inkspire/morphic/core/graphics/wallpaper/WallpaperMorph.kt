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
 * **The expensive half is [WallpaperMorphs.between]; this is the cheap half.** Building one plans both windows and
 * merges their cuts; asking for a moment re-cuts the frame. That split is why a scrub is prepared while nothing is
 * happening and only asked for moments once a finger is down — see docs/MORPH_ENGINE_PLAN.md.
 */
class WallpaperMorph internal constructor(
    private val morph: VitrallGenerator.Morph,
    private val palette: Palette,
) {

    /**
     * Paints the window [t] of the way across into [canvas], at `[width]` × `[height]` pixels.
     *
     * **Any canvas, and a hardware one is the point** — a Compose `DrawScope` hands out exactly this through
     * `drawIntoCanvas { it.nativeCanvas }`, so a scrub frame and the bake issue the same draw calls from the same
     * plan and only the rasterizer differs. Measured at a mean difference of `0.18` of `255`, which is why the
     * handoff from the last scrub frame to the settled bitmap is invisible.
     *
     * Cheap enough for a frame, and it must be: this is called from a draw pass.
     */
    fun draw(canvas: Canvas, t: Float, width: Int, height: Int) {
        VitrallGenerator.draw(canvas, morph.at(t), palette, width, height)
    }
}

/** Where the studio asks whether two recipes can be scrubbed between, and gets the scrub if they can. */
object WallpaperMorphs {

    /**
     * A scrub from [from] to [to] in a frame `[width]` × `[height]`, or **null where these two cannot be scrubbed**
     * and the caller should fall back to whatever it does for a discrete change.
     *
     * **Null rather than a degraded scrub**, because every way of failing here is a way of putting a *different*
     * picture on screen than the one that will be applied. The four refusals:
     *
     * - **A design with no plan seam.** Vitrall is the only one so far; M6 of the morph plan is the rest of the
     *   catalog, one generator at a time. Everything else has a `render` and nothing to interpolate.
     * - **Two different designs.** Two windows of one design share a *kind* of construction, which is what makes
     *   their cuts interpolable; a Vitrall and a mesh gradient share nothing to put into correspondence.
     * - **Two different palettes.** [draw] takes one palette, and a scrub that quietly used the starting one would
     *   land on a picture the recipe does not describe. Interpolating two palettes is a real thing to build — the
     *   reference does it, its ground going black to cream across one swipe — and it is not built.
     * - **Any filter on either side.** A scrub draws to a canvas and never becomes an `IntArray`, so the per-pixel
     *   passes have nothing to run on. Applying what the canvas can reach and resolving the rest on settle is the
     *   design in the morph plan; until the filters are split by whether the live path can reach them at all, a
     *   scrub past one would pop at both ends of the gesture.
     *
     * **Plans both windows and merges them, so it belongs off the main thread.** The cost is roughly two renders'
     * worth of geometry with none of the rasterizing.
     */
    fun between(from: WallpaperRecipe, to: WallpaperRecipe, width: Int, height: Int): WallpaperMorph? {
        if (from.design != to.design || from.design != WallpaperDesign.VITRALL) return null
        if (from.palette != to.palette) return null
        if (from.filters.isNotEmpty() || to.filters.isNotEmpty()) return null

        val palette = PaletteColorMode.resolve(from.palette, from.params.colorMode)
        return WallpaperMorph(
            VitrallGenerator.morph(
                VitrallGenerator.plan(width, height, from.params, from.seed),
                VitrallGenerator.plan(width, height, to.params, to.seed),
            ),
            palette,
        )
    }
}
