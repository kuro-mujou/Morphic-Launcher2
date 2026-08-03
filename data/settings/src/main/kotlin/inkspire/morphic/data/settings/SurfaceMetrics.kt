package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridDefault
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
 * A user's change to one grid's **dimensions** — nullable per axis, meaning "not overridden".
 *
 * Sparse for the same reason [IconOverride] is: a stored value would copy the blueprint's number into storage the first
 * time a user touched the other axis, and a later change to that default would then stop reaching them.
 *
 * **A row override is ignored on a scrolling grid**, whatever is stored. Rows there are however many the content
 * reaches, so a fixed count is not a preference the surface could honour — see [resolveAgainst], which keeps that
 * decision in one place rather than trusting every reader to check the blueprint's sizing.
 */
@Serializable
data class GridOverride(
    val cols: Int? = null,
    val rows: Int? = null,
) {
    /** True when nothing is overridden — the state in which this entry may as well not be stored. */
    val isEmpty: Boolean get() = cols == null && rows == null

    /**
     * [base] with each overridden axis replaced.
     *
     * A null `base.rows` marks a scrolling grid, and stays null however many rows are stored: the blueprint decides
     * whether an axis *exists*, and only the size of one that does is the user's to change.
     */
    fun resolveAgainst(base: GridDefault): GridDefault = GridDefault(
        cols = cols ?: base.cols,
        rows = if (base.rows == null) null else rows ?: base.rows,
    )
}

/**
 * **Per-grid metric overrides** — what the user changed about a grid, for one device configuration.
 *
 * The second settings slice, and the one that retired the per-surface icon constants scattered through the feature
 * modules. Icon sizing and grid dimensions are two maps in **one** slice rather than two slices, because every
 * consumer of one needs the other: a cell's icon size is a fraction of a cell whose size comes from the grid. The
 * dock's height is a third, and belongs here for the same reason twice over — it is what its cell counts are
 * divided out of, and those counts are what its icons are sized against.
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
    val grid: Map<GridSlot, Map<DeviceConfiguration, GridOverride>> = emptyMap(),
    val dockHeightDp: Map<DeviceConfiguration, Int> = emptyMap(),
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


    /**
     * The dimensions to lay [slot] out with on [device]: the blueprint's default with any override applied.
     *
     * As with icon sizing, consumers never see this — they ask [SettingsRepository] for a resolved `GridConfig` (or a
     * column count, for a scrolling grid) and the keying stays in this module.
     */
    fun gridSize(slot: GridSlot, device: DeviceConfiguration, base: GridDefault): GridDefault =
        grid[slot]?.get(device)?.resolveAgainst(base) ?: base

    /**
     * A copy with [transform] applied to [slot]'s grid override for [device].
     *
     * Empty overrides are removed at both levels, exactly as [withIconOverride] does — the two are deliberately the
     * same shape, since the sparseness rule is the slice's rather than either map's.
     */
    fun withGridOverride(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: GridOverride.() -> GridOverride,
    ): SurfaceMetrics {
        val forSlot = grid[slot].orEmpty()
        val updated = forSlot.getOrElse(device) { GridOverride() }.transform()
        val nextForSlot = if (updated.isEmpty) forSlot - device else forSlot + (device to updated)
        return copy(grid = if (nextForSlot.isEmpty()) grid - slot else grid + (slot to nextForSlot))
    }

    /**
     * The dock's height on [device] in dp: [base] — its blueprint's — unless the user has set one here.
     *
     * **Not keyed by [GridSlot], unlike its two neighbours.** A slot-keyed map would make seven of the eight keys
     * meaningless: every other grid either fills the space its parent gives it or scrolls, so "how tall is the APPS
     * pager" has no answer to store. Naming the one grid that *has* an extent keeps the unrepresentable
     * unrepresentable, which is worth more here than a third map that looks like the other two.
     */
    fun dockHeight(device: DeviceConfiguration, base: Int): Int = dockHeightDp[device] ?: base

    /**
     * A copy with the dock's height on [device] set to [dp], or **cleared** when it is null — after which the dock
     * follows its blueprint again, exactly as a nulled field does in the two override maps.
     *
     * No clamp here, and none in the store either. The grid dimensions could be floored on write because
     * `GridEditRange` states a minimum as a static *count*; a height's bounds are both runtime — its floor is one
     * row's smallest usable cell (which needs the resolved icon sizing) and its ceiling is a fraction of the
     * measured screen. Those belong to whatever measured the screen, so the only rule this layer can honestly
     * enforce is that a height is positive, which `SettingsRepository.setDockHeight` requires.
     */
    fun withDockHeight(device: DeviceConfiguration, dp: Int?): SurfaceMetrics =
        copy(dockHeightDp = if (dp == null) dockHeightDp - device else dockHeightDp + (device to dp))

    companion object {
        /** Nothing overridden: every grid draws at its blueprint's defaults. */
        val Default = SurfaceMetrics()
    }
}
