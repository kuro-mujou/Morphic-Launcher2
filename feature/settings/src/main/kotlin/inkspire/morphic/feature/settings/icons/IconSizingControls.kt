package inkspire.morphic.feature.settings.icons

import androidx.compose.runtime.Composable
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.IconSizingRanges
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsSwitchRow

/**
 * Which icon field a control writes.
 *
 * Named fields rather than the `IconLayoutSettings.() -> IconLayoutSettings` transforms L1 passed around, because the
 * two clamps (`min ≤ max`) need the **resolved** values to be correct, and only the ViewModel has both those and the
 * sparse override. L1 repeated its clamp inline at each of the two call sites; here it lives in one place.
 */
internal enum class IconSizingField { IconPercent, LabelScale, MinIconDp, MaxIconDp }

/**
 * Icon + label sizing controls for **one grid**, shared by every surface that edits its own.
 *
 * The port of L1's `IconLayoutControls`, which its five surface details each embedded. It stays a reusable group for
 * the same reason: when per-surface settings sections arrive (Home, Apps, Dock, Folder), each embeds this instead of
 * growing its own copy — that duplication is part of why L1 ended up with two 700-line detail screens.
 *
 * **What the port changed.** L1 took a `listMode: Boolean` every caller had to remember to pass, which decided whether
 * icons could be hidden and how the size slider was worded. Here it is read off the [slot], because it is a property of
 * the grid rather than of the call site — so a caller cannot get it wrong, and a new grid answers for itself.
 *
 * L1 also dropped the dp guardrails entirely for a list. They are kept here because in L2 they genuinely apply: every
 * cell resolves its icon through `IconMetrics.resolveIconSize`, which clamps to them whatever the surface.
 *
 * **Every control writes a *sparse* override**, so nothing is stored for a field left alone and a later change to a
 * blueprint default still reaches it. That is also what makes "reset" a plain write of nulls rather than a special op.
 *
 * @param sizing the currently **resolved** sizing — blueprint default with any override merged in. What the controls
 *   should show, because it is what the user sees on screen.
 * @param onChange commits a numeric field. Fires on slider **release**, not per frame.
 * @param onToggle commits a boolean field.
 */
@Composable
internal fun IconSizingControls(
    slot: GridSlot,
    sizing: IconSizing,
    onChange: (IconSizingField, Float) -> Unit,
    onToggle: (showLabel: Boolean?, showIcon: Boolean?) -> Unit,
) {
    SettingsSectionHeader("Icon & text")

    // A pure-text list is the one arrangement where hiding icons makes sense; on a grid it would leave a page of
    // labels floating in empty cells. L1 gated this on the flag its callers passed; the slot already knows.
    if (slot == GridSlot.APPS_LIST) {
        SettingsSwitchRow(
            title = "Show app icons",
            subtitle = "Turn off for a pure-text list",
            checked = sizing.showIcon,
            onCheckedChange = { on -> onToggle(null, on) },
        )
    }

    if (sizing.showIcon) {
        SettingsCommitSlider(
            title = "Icon size",
            // A list row is a full-width strip whose icon is sized against its height, so the same number reads as a
            // scale rather than a portion of a cell. The only wording L1's `listMode` still buys.
            subtitle = if (slot == GridSlot.APPS_LIST) "Scale of the default size" else "Portion of each cell the icon fills",
            value = sizing.iconPercent,
            valueRange = IconSizingRanges.IconPercent,
            valueLabel = { "${(it * 100).toInt()}%" },
            onCommit = { onChange(IconSizingField.IconPercent, it) },
        )
    }

    SettingsSwitchRow(
        title = "Show labels",
        checked = sizing.showLabel,
        onCheckedChange = { on -> onToggle(on, null) },
    )
    if (sizing.showLabel) {
        SettingsCommitSlider(
            title = "Text size",
            value = sizing.labelScale,
            valueRange = IconSizingRanges.LabelScale,
            valueLabel = { "%.2fx".format(it) },
            onCommit = { onChange(IconSizingField.LabelScale, it) },
        )
    }

    SettingsSectionHeader("Icon size limits")
    SettingsCommitSlider(
        title = "Minimum",
        subtitle = "Guardrail on dense grids",
        value = sizing.minIconDp.toFloat(),
        valueRange = IconSizingRanges.MinIconDp.asSliderRange(),
        steps = IconSizingRanges.MinIconDp.sliderSteps(),
        valueLabel = { "${it.toInt()} dp" },
        onCommit = { onChange(IconSizingField.MinIconDp, it) },
    )
    SettingsCommitSlider(
        title = "Maximum",
        subtitle = "Guardrail on sparse grids",
        value = sizing.maxIconDp.toFloat(),
        valueRange = IconSizingRanges.MaxIconDp.asSliderRange(),
        steps = IconSizingRanges.MaxIconDp.sliderSteps(),
        valueLabel = { "${it.toInt()} dp" },
        onCommit = { onChange(IconSizingField.MaxIconDp, it) },
    )
}

/** A whole-number range as a slider's float bounds. */
private fun IntRange.asSliderRange(): ClosedFloatingPointRange<Float> = first.toFloat()..last.toFloat()

/**
 * How many discrete stops a slider needs to land on every whole value in this range.
 *
 * Compose counts the steps *between* the endpoints, so it is one fewer than the interior values — derived here rather
 * than written as the literal `47`/`91` L1 kept beside its ranges, where the two could drift apart.
 */
private fun IntRange.sliderSteps(): Int = (last - first - 1).coerceAtLeast(0)
