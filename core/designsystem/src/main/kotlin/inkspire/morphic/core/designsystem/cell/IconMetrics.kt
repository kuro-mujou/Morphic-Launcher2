package inkspire.morphic.core.designsystem.cell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.IconSizing

/**
 * Per-surface icon + label sizing. [iconPercent] is the icon's edge length as a fraction of the cell's *smaller*
 * available bound (see [IconLabelCell]), and [minIconDp]/[maxIconDp] clamp the result. [labelScale] multiplies the base
 * label text size.
 *
 * **The defaults mirror [IconSizing]'s exactly, and must keep doing so** — this is the Compose-typed twin of that
 * persistable record, resolved through `IconMetrics.of`, so a difference between the two would mean a surface drew one
 * thing before the store answered and another after. They are: fill the cell, capped at 48dp, never below 24dp. With the
 * fraction at 1f, [maxIconDp] is what actually decides the drawn size on any cell larger than it — the guardrail is the
 * size control, and the fraction is for shrinking an icon inside a cell that is already small.
 *
 * Each surface (home pager, dock, drawer, …) provides its own [IconMetrics] via [LocalIconMetrics].
 */
data class IconMetrics(
    val iconPercent: Float = 1f,
    val labelScale: Float = 1f,
    val showLabel: Boolean = true,
    val minIconDp: Dp = 24.dp,
    val maxIconDp: Dp = 48.dp,
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
