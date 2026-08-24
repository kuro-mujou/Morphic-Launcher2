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
    resolveIconSizeUnfloored(availWidth, availHeight)
        .coerceAtLeast(minOf(minIconDp, maxIconDp))

/**
 * The same **without the lower guardrail** — [iconPercent] of the smaller bound, capped above and floored by nothing.
 *
 * **For a surface where the floor is the available space itself**, which is the icon container: it packs many icons
 * into one grid cell, so small icons are what it is *for* rather than a state to be rescued from. `minIconDp` exists
 * so an icon on a **grid** never becomes unreadable, and a container is not a grid.
 *
 * The floor also flattens the size where it bites: between `slot × iconPercent` falling under it and the slot itself
 * falling under it, every slot resolves to the same `minIconDp` — so a stretch of a container's resize would move the
 * tile and not its contents. That is a property of clamping rather than a fault anyone reported, and it is what the
 * unfloored resolution is tested for.
 *
 * [resolveIconSize] is this plus the floor, rather than the two being written out separately: the upper guardrail and
 * the percentage are the same rule for both, and only the floor differs.
 */
fun IconMetrics.resolveIconSizeUnfloored(availWidth: Dp, availHeight: Dp): Dp =
    (minOf(availWidth, availHeight) * iconPercent)
        .coerceAtMost(maxOf(minIconDp, maxIconDp))

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
