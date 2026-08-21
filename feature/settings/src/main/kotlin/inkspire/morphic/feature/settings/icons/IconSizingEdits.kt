package inkspire.morphic.feature.settings.icons

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.data.settings.IconOverride
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The **write half of [IconSizingControls]** for one grid — held by every section that offers those controls.
 *
 * Icon sizing is edited from more than one place by design: each surface's own section owns its grid's icons, exactly
 * as L1's home, drawer, dock and folder details each embedded `IconLayoutControls`. What L1 then repeated at every one
 * of those call sites was the *writing* — `scope.launch { repository.updateDock { copy(iconLayout = …) } }` and four
 * near-identical siblings. Sharing the controls without sharing their commands is half a job, so this is the other
 * half: one place that knows an icon edit is a sparse override on (grid, device).
 *
 * **[slot] and [device] are suppliers, not values.** The APPS section switches grids from its chip row — one section for
 * five layouts — and every section resolves its device a frame after composing, so both move underneath this object. A
 * section with one fixed grid (the folder's) simply supplies a constant.
 *
 * Kept as a plain class a ViewModel holds rather than a base class it extends: the sections have nothing else in
 * common, and inheritance for three methods is how a hierarchy starts.
 */
internal class IconSizingEdits(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val slot: () -> GridSlot,
    private val device: () -> DeviceConfiguration?,
) {

    /** Commits a numeric field. */
    fun change(field: IconSizingField, value: Float) {
        edit {
            when (field) {
                IconSizingField.IconPercent -> copy(iconPercent = value)
                IconSizingField.LabelScale -> copy(labelScale = value)
            }
        }
    }

    /**
     * Commits a boolean field; pass null for the one not being changed.
     *
     * Parameters are named for the thing rather than the field (`label`, not `showLabel`) so the receiver's own
     * properties are readable inside the transform without `this.` disambiguation.
     */
    fun toggle(label: Boolean? = null, icon: Boolean? = null) {
        edit { copy(showLabel = label ?: showLabel, showIcon = icon ?: showIcon) }
    }

    /**
     * Commits both icon-size guardrails together, in whole dp.
     *
     * **No clamp, and that is the point of using a range slider.** The two bounds were once independent sliders, which
     * meant this had to stop them crossing — against the *resolved* values, since a sparse override may hold null for
     * the other half — and had to avoid pinning a field still following the blueprint. A two-thumb control cannot
     * produce a crossed pair at all, so the invariant is structural rather than enforced here.
     */
    fun changeDpRange(range: IntRange) {
        edit { copy(minIconDp = range.first, maxIconDp = range.last) }
    }

    /**
     * Clears one numeric field, returning it to its blueprint's default.
     *
     * **What a reset writes is `null`, not the default value** — the payoff of sparse overrides, and the reason each
     * control's reset comes through here rather than committing the number it shows. Writing the number would pin it:
     * storage would keep an entry saying what the blueprint already says, and a later change to that default would
     * never reach anyone who had pressed reset. The store drops an override that has become empty, so a field reset is
     * a field that was never touched.
     */
    fun clear(field: IconSizingField) {
        edit {
            when (field) {
                IconSizingField.IconPercent -> copy(iconPercent = null)
                IconSizingField.LabelScale -> copy(labelScale = null)
            }
        }
    }

    /** [clear] for the guardrail pair, which commits as one field for [changeDpRange]'s reason. */
    fun clearDpRange() {
        edit { copy(minIconDp = null, maxIconDp = null) }
    }

    private fun edit(transform: IconOverride.() -> IconOverride) {
        val configuration = device() ?: return
        val grid = slot()
        scope.launch { settings.updateIcon(grid, configuration, transform) }
    }
}
