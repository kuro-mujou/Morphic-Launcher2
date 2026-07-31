package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import kotlinx.serialization.Serializable

/**
 * A user's change to one grid's icon sizing — **every field nullable, meaning "not overridden"**.
 *
 * Sparse rather than a whole [IconSizing], and that is what keeps "a default lives in exactly one place" literally
 * true instead of aspirational. If a change stored the resolved value, the blueprint's number would be copied into
 * storage the moment a user moved one slider — and a later change to that default would then reach everybody except
 * the users who had touched anything nearby. Storing only the difference means a default keeps propagating to every
 * field nobody has an opinion about.
 *
 * It also makes "reset this one control" expressible without a separate op: write null.
 *
 * (No tension with the icon-layer decision to snapshot-and-detach rather than field-merge. That one is forced by
 * variable-length *list* diffing; merging a fixed-arity record of scalars is [resolveAgainst].)
 */
@Serializable
data class IconOverride(
    val iconPercent: Float? = null,
    val labelScale: Float? = null,
    val showLabel: Boolean? = null,
    val minIconDp: Int? = null,
    val maxIconDp: Int? = null,
    val showIcon: Boolean? = null,
) {
    /** True when nothing is overridden — the state in which this entry may as well not be stored. */
    val isEmpty: Boolean
        get() = iconPercent == null && labelScale == null && showLabel == null &&
            minIconDp == null && maxIconDp == null && showIcon == null

    /** [base] with each overridden field replaced. Field-by-field, so an untouched field keeps following [base]. */
    fun resolveAgainst(base: IconSizing): IconSizing = IconSizing(
        iconPercent = iconPercent ?: base.iconPercent,
        labelScale = labelScale ?: base.labelScale,
        showLabel = showLabel ?: base.showLabel,
        minIconDp = minIconDp ?: base.minIconDp,
        maxIconDp = maxIconDp ?: base.maxIconDp,
        showIcon = showIcon ?: base.showIcon,
    )
}

/**
 * **Per-grid metric overrides** — what the user changed about a grid, for one device configuration.
 *
 * The second settings slice, and the one that retires the per-surface icon constants scattered through the feature
 * modules. Only icon sizing is here so far; grid dimensions (S4) key the same way and join as a second map rather
 * than a second slice, because the two are edited on one screen and every consumer of one needs the other — a cell's
 * icon size is a fraction of a cell whose size comes from the grid.
 *
 * **Keyed by [GridSlot] × [DeviceConfiguration], and the second half matters.** `GridBlueprint.defaults` is already
 * per-[DeviceConfiguration] — form factor *crossed with* orientation, four values — so keying an override by mere
 * orientation would make it **coarser than the default it replaces**: one value would override both phone-landscape
 * and tablet-landscape even though the blueprint gives them different numbers. Matching the blueprint's granularity
 * keeps resolution a like-for-like lookup, and gives a foldable separate config per posture for free.
 *
 * `Orientation` stays what it already is elsewhere — the key for *arrangement* (which app sits where), not for
 * configuration.
 *
 * **Doubly sparse.** An absent slot means "nothing overridden anywhere for that grid"; an absent device inside a slot
 * means "nothing overridden for that posture". A fresh install stores `{}`, and a phone user never writes the two
 * tablet configurations at all.
 */
@Serializable
data class SurfaceMetrics(
    val icon: Map<GridSlot, Map<DeviceConfiguration, IconOverride>> = emptyMap(),
) {
    /**
     * The icon sizing to draw with for [slot] on [device]: the blueprint's default with any override applied.
     *
     * This is the whole read path, and consumers never see it — they ask [SettingsRepository.iconSizing] for a
     * resolved value. The keying stays inside this module.
     */
    fun iconSizing(slot: GridSlot, device: DeviceConfiguration, base: IconSizing): IconSizing =
        icon[slot]?.get(device)?.resolveAgainst(base) ?: base

    /**
     * A copy with [transform] applied to [slot]'s override for [device].
     *
     * **An override that ends up empty is removed rather than stored**, at both levels — so resetting everything
     * leaves the blob as it started rather than accumulating `{"HOME_MAIN":{"PHONE_PORTRAIT":{}}}`. Storage that
     * grows monotonically with every visit to a settings screen is how L1's ~265 keys stayed permanently populated.
     */
    fun withIconOverride(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: IconOverride.() -> IconOverride,
    ): SurfaceMetrics {
        val forSlot = icon[slot].orEmpty()
        val updated = forSlot.getOrElse(device) { IconOverride() }.transform()
        val nextForSlot = if (updated.isEmpty) forSlot - device else forSlot + (device to updated)
        return copy(icon = if (nextForSlot.isEmpty()) icon - slot else icon + (slot to nextForSlot))
    }

    companion object {
        /** Nothing overridden: every grid draws at its blueprint's defaults. */
        val Default = SurfaceMetrics()
    }
}
