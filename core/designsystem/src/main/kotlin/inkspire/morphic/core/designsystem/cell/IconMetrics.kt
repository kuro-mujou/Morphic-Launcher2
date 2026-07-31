package inkspire.morphic.core.designsystem.cell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.IconSizing

/**
 * Per-surface icon + label sizing. [iconPercent] is the primary control — the icon's edge length is that
 * fraction of the cell's *smaller* available bound (see [IconLabelCell]); [minIconDp]/[maxIconDp] are wide
 * guardrails, not the primary limit. [labelScale] multiplies the base label text size.
 *
 * Each surface (home pager, dock, drawer, …) provides its own [IconMetrics] via [LocalIconMetrics].
 */
data class IconMetrics(
    val iconPercent: Float = 0.88f,
    val labelScale: Float = 1f,
    val showLabel: Boolean = true,
    val minIconDp: Dp = 28.dp,
    val maxIconDp: Dp = 72.dp,
    val showIcon: Boolean = true,
) {
    companion object {
        /** Build from persisted primitives (guardrails as raw dp ints), for the settings/layout layer. */
        fun of(
            iconPercent: Float,
            labelScale: Float,
            showLabel: Boolean,
            minIconDp: Int,
            maxIconDp: Int,
            showIcon: Boolean = true,
        ): IconMetrics = IconMetrics(iconPercent, labelScale, showLabel, minIconDp.dp, maxIconDp.dp, showIcon)
    }
}

/**
 * The icon edge length: [iconPercent] of the smaller available bound, clamped to the guardrails (order-safe,
 * so inverted guardrails never crash).
 */
fun IconMetrics.resolveIconSize(availWidth: Dp, availHeight: Dp): Dp =
    (minOf(availWidth, availHeight) * iconPercent)
        .coerceIn(minOf(minIconDp, maxIconDp), maxOf(minIconDp, maxIconDp))

/** The current surface's [IconMetrics]; defaults to a sensible grid metric. */
val LocalIconMetrics = staticCompositionLocalOf { IconMetrics() }

/**
 * This persisted sizing as Compose-facing [IconMetrics].
 *
 * The bridge `IconMetrics.of` was written for, and the reason `core:model`'s [IconSizing] can hold the same six facts
 * without depending on Compose: it keeps guardrails as raw dp `Int`s because a plain JVM module has no `Dp`. One
 * conversion, here, so no surface invents its own.
 */
fun IconSizing.toIconMetrics(): IconMetrics = IconMetrics.of(
    iconPercent = iconPercent,
    labelScale = labelScale,
    showLabel = showLabel,
    minIconDp = minIconDp,
    maxIconDp = maxIconDp,
    showIcon = showIcon,
)
