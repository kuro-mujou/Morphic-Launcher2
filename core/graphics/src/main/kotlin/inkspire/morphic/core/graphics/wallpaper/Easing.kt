package inkspire.morphic.core.graphics.wallpaper

/**
 * `3t² − 2t³` — the S between two values, flat at both ends.
 *
 * **Named here because two designs want the same curve for different reasons, and one of them measured it.**
 * [MeshGradientGenerator] eases a cell parameter so a warped lattice joins without a crease; [WavesGenerator]'s band
 * boundary *is* this curve — drive the reference's *Distortion* to `0` and every boundary in the frame normalizes to
 * one shape, which fits smoothstep to within `0.002` where a half-cosine is out by `0.015`. Keeping the two as
 * separate literals is the duplication the codebase keeps rediscovering; keeping them as one *without saying why*
 * would be worse, because a warp that later wants a quintic (`6t⁵ − 15t⁴ + 10t³`, as `LayerGrain` uses) is a change to
 * the **easing** and must not drag the **measurement** with it. Whoever makes that change adds a second function here.
 */
internal object Easing {

    /** [t] eased, clamped to `0..1` — zero slope at both ends. */
    fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val eased = clamped * clamped * (3f - 2f * clamped)
        return eased
    }
}
