package inkspire.morphic.feature.settings.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.component.slider.MorphicRangeSliderRow
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.IconSizingRanges
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.feature.settings.component.SettingsRowPadding
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsSwitchRow
import kotlin.math.roundToInt

/**
 * Which icon field a control writes.
 *
 * Named fields rather than a transform over the whole settings object, so the ViewModel — not a composable — decides
 * how a field is stored.
 *
 * Only the two continuous scales are here. The guardrails are set together by a range slider and commit as a pair,
 * because one two-thumb control is the honest shape for two bounds that must not cross.
 */
internal enum class IconSizingField { IconPercent, LabelScale }

/**
 * Icon + label sizing controls for **one grid**, shared by every surface that edits its own.
 *
 * **Every section embeds this group under its own layout controls**, and there is no separate icon-sizing screen left
 * (the folder section took the last grid out of it). Sharing one group is what stops the sections drifting apart, and
 * [IconSizingEdits] shares the *commands* for the same reason.
 *
 * **Whether icons can be hidden, and which defaults a reset lands on, are read off the [slot]** rather than taken as
 * flags every caller has to remember to pass — both are properties of the grid rather than of the call site — so a
 * caller cannot get either wrong, and a new grid answers for itself.
 *
 * The dp guardrails are shown for a list too, because they genuinely apply: every cell resolves its icon through
 * `IconMetrics.resolveIconSize`, which clamps to them whatever the surface. They are **one range slider** rather than
 * two independent ones, which makes their ordering structural instead of a clamp anyone could forget.
 *
 * **Every control writes a *sparse* override**, so nothing is stored for a field left alone and a later change to a
 * blueprint default still reaches it. That is also what makes "reset" a plain write of nulls rather than a special op.
 *
 * It emits no heading of its own: the group's heading is pinned above the preview by `SurfaceDetail`, so stating it
 * again here would repeat it a scroll apart.
 *
 * @param sizing the currently **resolved** sizing — blueprint default with any override merged in. What the controls
 *   should show, because it is what the user sees on screen.
 * @param onChange commits a numeric field. Fires on slider **release**, not per frame.
 * @param onToggle commits a boolean field.
 * @param onDpRange commits both icon-size guardrails at once, in whole dp.
 * @param onPreview the sizing a slider is *currently* dragging towards, per frame, written nowhere — for the live
 *   preview above these controls. A whole [IconSizing] rather than a field and a value, because that is what a preview
 *   needs and this group is the one place that can assemble it: it holds the resolved sizing every other field keeps.
 *   Handing over the result rather than a transform keeps the caller from having to know how a field is applied,
 *   which is the same
 *   reason [onChange] takes a named field rather than a lambda.
 */
@Composable
internal fun IconSizingControls(
    slot: GridSlot,
    sizing: IconSizing,
    onChange: (IconSizingField, Float) -> Unit,
    onToggle: (showLabel: Boolean?, showIcon: Boolean?) -> Unit,
    onDpRange: (IntRange) -> Unit,
    onPreview: (IconSizing) -> Unit = {},
) {
    // **Where each reset goes: this grid's blueprint, not a number typed here.** The blueprint is the one place a
    // default lives — `data:settings` resolves every override against it — so reading it is what keeps a reset landing
    // on the value an untouched launcher actually shows. A grid of *tiles* has no icon of its own to size (the card's
    // is null), and the launcher's own defaults are the honest fallback there, since that is what `IconSizing()` is.
    val defaults = slot.blueprint.icon ?: IconSizing()

    // A pure-text list is the one arrangement where hiding icons makes sense; on a grid it would leave a page of
    // labels floating in empty cells. The slot already knows, so no caller passes a flag.
    if (slot == GridSlot.APPS_LIST) {
        SettingsSwitchRow(
            title = "Show app icons",
            subtitle = "Turn off for a pure-text list",
            checked = sizing.showIcon,
            onCheckedChange = { on -> onToggle(null, on) },
        )
    }

    if (sizing.showIcon) {
        MorphicSliderRow(
            label = "Icon size",
            what = "icon size",
            value = sizing.iconPercent,
            valueRange = IconSizingRanges.IconPercent,
            default = defaults.iconPercent,
            // Rounded rather than truncated: a hundredth is not representable in binary, so flooring reads 0.29f back
            // as 28% — a stepper moving the value by exactly one hundredth then appears to land at random.
            valueLabel = { "${(it * 100).roundToInt()}%" },
            onPreview = { onPreview(sizing.copy(iconPercent = it)) },
            onCommit = { onChange(IconSizingField.IconPercent, it) },
            modifier = SettingsRowPadding,
        )
    }

    // **Neither text control is offered where the cells carry no text**, which the slot already knows: a list row *is*
    // its label, so hiding it would leave nothing (the mirror of the switch above rather than an inconsistency), and a
    // category card's preview slots have no label to hide — the blueprint says so with `showLabel = false`, because
    // four ellipsized words at thumbnail size would eat the room the icons need. `AppRowCell` and `CategoryCardFace`
    // each say the same from their own side, which is why neither has to be told.
    //
    // A card *does* have text: its title. That is card chrome rather than cell sizing, and its control lives with the
    // rest of the card's in the APPS section — a title is drawn once per tile, not once per icon.
    if (slot != GridSlot.APPS_LIST && slot != GridSlot.APPS_CARD) {
        SettingsSwitchRow(
            title = "Show labels",
            checked = sizing.showLabel,
            onCheckedChange = { on -> onToggle(on, null) },
        )
    }
    if (sizing.showLabel) {
        MorphicSliderRow(
            label = "Text size",
            what = "text size",
            value = sizing.labelScale,
            valueRange = IconSizingRanges.LabelScale,
            default = defaults.labelScale,
            valueLabel = { "%.2fx".format(it) },
            onPreview = { onPreview(sizing.copy(labelScale = it)) },
            onCommit = { onChange(IconSizingField.LabelScale, it) },
            modifier = SettingsRowPadding,
        )
    }

    SettingsSectionHeader("Icon size limits")
    MorphicRangeSliderRow(
        label = "Icon size range",
        what = "icon size range",
        value = sizing.minIconDp..sizing.maxIconDp,
        bounds = IconSizingRanges.IconDp,
        default = defaults.minIconDp..defaults.maxIconDp,
        valueLabel = { "${it.first}–${it.last} dp" },
        onPreview = { onPreview(sizing.copy(minIconDp = it.first, maxIconDp = it.last)) },
        onCommit = onDpRange,
        modifier = SettingsRowPadding,
    )
}

/**
 * A settings section's whole icon group: the [IconSizingControls] sliders plus the reset beneath them.
 *
 * Every section that sizes icons shows exactly this, so it is one composable rather than the same two statements in
 * each — four sections had copied it, which is four places for a control to be added to and three for it to be
 * forgotten. What varies is only which grid is being sized ([slot], [sizing]); the commands are the same
 * [IconSizingEdits] in every section, which is what made the block identical in the first place.
 *
 * @param onPreview reports the sizing under the finger, per frame, so the section's live preview tracks a drag rather
 *   than waiting for its release. The section owns that state because it also owns the cell the preview is drawn in.
 */
@Composable
internal fun IconSizingGroup(
    slot: GridSlot,
    sizing: IconSizing,
    edits: IconSizingEdits,
    onPreview: (IconSizing) -> Unit,
) {
    IconSizingControls(
        slot = slot,
        sizing = sizing,
        onChange = edits::change,
        onToggle = { label, showIcon -> edits.toggle(label, showIcon) },
        onDpRange = edits::changeDpRange,
        onPreview = onPreview,
    )
    MorphicButton(
        onClick = edits::reset,
        style = MorphicButtonStyle.Text,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text("Reset icons")
    }
}
