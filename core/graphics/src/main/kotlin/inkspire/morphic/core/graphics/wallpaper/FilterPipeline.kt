package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Bitmap

/**
 * The post-process stage: a generator's bitmap in, a filtered bitmap out — the studio's *Filters* panel.
 *
 * **The seam is here; the passes are not yet.** The plan's whole thesis is that this stage reuses the icon studio's
 * per-pixel effect helpers (`LayerRipple`, `LayerGrain`, `LayerDither`, `LayerTritone`, `Oklab`, …), which are already
 * pure and bitmap-size-agnostic, run over a full-screen bitmap by one shared runner. Building that runner — and
 * deciding which effects a *non-silhouette* wallpaper may carry — is a later slice (see `docs/WALLPAPER_STUDIO_PLAN.md`,
 * W4). W0 declares the seam so the rest of the pipeline can name it, and ships only the identity below.
 */
interface FilterPipeline {

    /** [source] with the pipeline's passes applied, as a new bitmap the caller owns. */
    fun apply(source: Bitmap): Bitmap

    companion object {

        /** The empty pipeline — the base every recipe starts from, and W0's only implementation. */
        val None: FilterPipeline = object : FilterPipeline {
            override fun apply(source: Bitmap): Bitmap = source
        }
    }
}
