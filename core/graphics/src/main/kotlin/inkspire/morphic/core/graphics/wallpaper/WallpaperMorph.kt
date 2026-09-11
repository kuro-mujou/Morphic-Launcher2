package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Canvas
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe

/**
 * Two recipes prepared to scrub between — resolved once when a swipe begins, asked for a moment on every frame of it.
 *
 * **This is the studio's seam onto the morph engine, and it is deliberately narrower than the engine.** Planning,
 * merging and interpolating are `core:graphics`' business and stay internal; what a screen needs is "can these two be
 * scrubbed between, and if so paint me `t`". Widening it to the whole seam would put a `Plan` in a composable, which
 * is the shape the icon subsystem's two-renderer hazard grew out of.
 *
 * **The expensive half is [Generator.scrub]; this is the cheap half.** Building one plans both pictures and, for a
 * subdivision, merges their cuts; asking for a moment re-cuts, moves or re-evaluates. That split is why a scrub is
 * prepared while nothing is happening and only asked for moments once a finger is down — see
 * docs/MORPH_ENGINE_PLAN.md.
 *
 * **A `fun interface`, because each design's scrub is one line over its own plan.** The kinds of design do genuinely
 * different work behind it — a subdivision re-cuts polygons, a scatter moves independent shapes, a field re-evaluates
 * a small buffer and lets the canvas blow it up — and what they share is this signature and nothing else.
 */
fun interface WallpaperMorph {

    /**
     * Paints the picture [t] of the way across into [canvas], at `[width]` × `[height]` pixels.
     *
     * Cheap enough for a frame, and it must be: this is called from a draw pass.
     */
    fun draw(canvas: Canvas, t: Float, width: Int, height: Int)
}

/** Where the studio asks whether two recipes can be scrubbed between, and gets the scrub if they can. */
object WallpaperMorphs {

    /**
     * A scrub from [from] to [to] in a frame `[width]` × `[height]`, or **null where these two cannot be scrubbed**
     * and the caller should fall back to whatever it does for a discrete change.
     *
     * **Null rather than a degraded scrub**, because every way of failing here is a way of putting a *different*
     * picture on screen than the one that will be applied. The refusals made here, before any design is asked:
     *
     * - **Two different designs.** Two pictures of one design share a *kind* of construction, which is what makes
     *   them interpolable at all; a Vitrall and a mesh gradient share nothing to put into correspondence.
     * - **Two different knob settings.** A scrub is a shuffle — see [Generator.scrub].
     * - **Two different palettes**, compared *resolved*, since the color mode is a knob that reduces the palette.
     *   Interpolating two palettes is a real thing to build — the reference does it, its ground going black to cream
     *   across one swipe — and it is not built.
     * - **Any filter on either side.** A scrub draws to a canvas and never becomes an `IntArray`, so the per-pixel
     *   passes have nothing to run on. Applying what the canvas can reach and resolving the rest on settle is the
     *   design in the morph plan; until the filters are split by whether the live path can reach them at all, a
     *   scrub past one would pop at both ends of the gesture.
     *
     * The design then refuses for its own reasons, the first of which is having no plan seam yet — M6 of the morph
     * plan is the rest of the catalog, one generator at a time.
     *
     * **Plans both pictures, so it belongs off the main thread.**
     */
    fun between(from: WallpaperRecipe, to: WallpaperRecipe, width: Int, height: Int): WallpaperMorph? {
        val palette = PaletteColorMode.resolve(from.palette, from.params.colorMode)
        val scrubbable = from.design == to.design &&
            from.params == to.params &&
            from.filters.isEmpty() && to.filters.isEmpty() &&
            palette == PaletteColorMode.resolve(to.palette, to.params.colorMode)
        if (!scrubbable) return null

        return Generators.forDesign(from.design).scrub(width, height, palette, from.params, from.seed, to.seed)
    }
}
