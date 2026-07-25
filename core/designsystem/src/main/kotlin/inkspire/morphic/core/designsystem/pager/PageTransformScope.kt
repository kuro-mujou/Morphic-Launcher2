package inkspire.morphic.core.designsystem.pager

import androidx.compose.runtime.Immutable

/**
 * Passed to a [LauncherPager]'s `pageTransform` so it can apply a per-page `graphicsLayer` effect (parallax,
 * scale, depth, fade — the raw material for the surface transitions).
 *
 * @property pageOffset the page's signed distance from the viewport centre, in page widths: 0 = centred,
 *   -1 = one page to the left, +1 to the right (already wrap-adjusted for an infinite pager).
 */
@Immutable
data class PageTransformScope(val pageOffset: Float)
