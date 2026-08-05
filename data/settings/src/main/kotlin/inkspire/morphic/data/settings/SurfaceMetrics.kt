package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import kotlinx.serialization.SerialName
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
 * divided out of, and those counts are what its icons are sized against. The list's row height is a fourth, and the
 * same dependency runs the other way there: the row is what its icon is a fraction *of*.
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
    // The serialized key keeps the old name deliberately: the *meaning* did not change — it is the same strip
    // thickness, on whichever axis the dock is stacked — only the Kotlin name was wrong once the dock could be a rail.
    // A key rename is this slice's seam for a semantic break, and renaming one here would silently reset every stored
    // dock instead.
    @SerialName("dockHeightDp")
    val dockExtentDp: Map<DeviceConfiguration, Int> = emptyMap(),
    val listRowHeightDp: Map<DeviceConfiguration, Int> = emptyMap(),
    val horizontalPaddingDp: Map<GridSlot, Map<DeviceConfiguration, Int>> = emptyMap(),
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
     * The dock's extent on [device] in dp: [base] — its blueprint's — unless the user has set one here.
     *
     * A height on the three configurations where the dock is a bottom strip and a width on the one where it is a rail
     * — the store holds a thickness and `DockEdge` says which dimension that is. One value per device is what makes
     * that work without a second field: a user configuring a phone in landscape is configuring the rail.
     *
     * **Not keyed by [GridSlot], unlike its two neighbours.** A slot-keyed map would make seven of the eight keys
     * meaningless: every other grid either fills the space its parent gives it or scrolls, so "how thick is the APPS
     * pager" has no answer to store. Naming the one grid that *has* an extent keeps the unrepresentable
     * unrepresentable, which is worth more here than a third map that looks like the other two.
     */
    fun dockExtent(device: DeviceConfiguration, base: Int): Int = dockExtentDp[device] ?: base

    /**
     * A copy with the dock's extent on [device] set to [dp], or **cleared** when it is null — after which the dock
     * follows its blueprint again, exactly as a nulled field does in the two override maps.
     *
     * No clamp here, and none in the store either. The grid dimensions could be floored on write because
     * `GridEditRange` states a minimum as a static *count*; an extent's bounds are both runtime — its floor is one
     * cell's smallest usable size (which needs the resolved icon sizing) and its ceiling is a fraction of the
     * measured screen. Those belong to whatever measured the screen, so the only rule this layer can honestly
     * enforce is that an extent is positive, which `SettingsRepository.setDockExtent` requires.
     */
    fun withDockExtent(device: DeviceConfiguration, dp: Int?): SurfaceMetrics =
        copy(dockExtentDp = if (dp == null) dockExtentDp - device else dockExtentDp + (device to dp))

    /**
     * How tall one row of the APPS vertical list is on [device] in dp: [base] — its blueprint's — unless the user has
     * set one here.
     *
     * **A fourth map rather than a `slot → extent` one, for the reason [dockExtentDp] is not slot-keyed either**, and
     * the two are not the same measurement wearing different names: a dock's height is a whole strip's extent, which
     * its rows then divide; a list's is one row of a grid that has no total height at all, because it scrolls. Naming
     * each of the two grids that declares a height keeps the six that declare none unable to be asked.
     */
    fun listRowHeight(device: DeviceConfiguration, base: Int): Int = listRowHeightDp[device] ?: base

    /**
     * A copy with the list's row height on [device] set to [dp], or **cleared** when it is null — after which the list
     * follows its blueprint again.
     *
     * No clamp, as [withDockExtent] has none: a row's floor is whatever still renders an icon at the *current* icon
     * sizing, which this layer cannot know, and its ceiling is a matter of taste rather than of fit — a list scrolls,
     * so a tall row costs nothing but density.
     */
    fun withListRowHeight(device: DeviceConfiguration, dp: Int?): SurfaceMetrics =
        copy(listRowHeightDp = if (dp == null) listRowHeightDp - device else listRowHeightDp + (device to dp))

    /**
     * The blank margin at [slot]'s left and right edges on [device], in dp: [base] — its blueprint's — unless
     * overridden here.
     *
     * **Slot-keyed, unlike [dockExtentDp] and [listRowHeightDp], and the difference is how many grids can be asked.**
     * Those two name one grid each because only one grid *has* the measurement — a strip's extent, a list's row. Every
     * grid has edges, so this one is keyed like [icon] and [grid] are.
     */
    fun horizontalPadding(slot: GridSlot, device: DeviceConfiguration, base: Int): Int =
        horizontalPaddingDp[slot]?.get(device) ?: base

    /**
     * A copy with [slot]'s padding on [device] set to [dp], or **cleared** when it is null — after which that grid
     * follows its blueprint again.
     *
     * Removes the empty entry at both levels, as [withIconOverride] does and for the same reason: a settings screen
     * that has been visited and reset should leave the blob as it found it.
     *
     * No clamp against what still fits. That is deliberate and matches the column count: a padding too wide for the
     * columns stored beside it is resolved *on read* by `CellFit`, which reports fewer columns rather than rewriting
     * anything, so narrowing the padding again brings them back. L1 wrote its clamps back and destroyed the number.
     */
    fun withHorizontalPadding(slot: GridSlot, device: DeviceConfiguration, dp: Int?): SurfaceMetrics {
        val forSlot = horizontalPaddingDp[slot].orEmpty()
        val updated = if (dp == null) forSlot - device else forSlot + (device to dp)
        return copy(
            horizontalPaddingDp = if (updated.isEmpty()) {
                horizontalPaddingDp - slot
            } else {
                horizontalPaddingDp + (slot to updated)
            },
        )
    }

    companion object {
        /** Nothing overridden: every grid draws at its blueprint's defaults. */
        val Default = SurfaceMetrics()
    }
}
