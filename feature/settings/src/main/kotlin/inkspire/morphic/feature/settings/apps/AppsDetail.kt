package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.rowHeightRangeDp
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.grid.cellHeight
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsChip
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.usableWindowArea
import inkspire.morphic.feature.settings.icons.IconSizingControls
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp
private val ChipGap = 8.dp

/**
 * **Apps**: how each arrangement of the app list is sized, and how its icons are drawn.
 *
 * **One section, a chip per layout** — the settings mirror of `feature:apps` being one module for five layouts. The
 * layouts differ only in arrangement, so what a user configures is "the paged one" or "the list", and every control
 * below addresses whichever is selected. L1 reached the same shape from the other direction: its drawer detail edited
 * `drawer.profile(layout)` — the profile of the selected layout — but it needed *two* details to do it, because the
 * drawer and the library were separate modules.
 *
 * **Selecting a chip changes nothing.** Which layout a user actually gets is a property of the home edge they swipe
 * from, and lives in the surface register beside that binding; this row only says which one you are configuring. That
 * is the same distinction the icons section's chips draw, and the reason neither writes on selection.
 *
 * **Layout group, then icon group**, as in every other section here and in every one of L1's, because the dependency
 * runs that way: the icon size decides the smallest usable cell, which is what the column and row limits above are
 * computed from — and, for the list, what the row-height slider's range is computed from.
 *
 * The one control that has no counterpart elsewhere is the list's **row height**, and the one grid missing from the
 * chips is the **category card** — see [ConfigurableLayouts] for why that gap is left open rather than filled with a
 * guessed bound.
 */
@Composable
internal fun AppsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<AppsSectionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    val colors = LocalMorphicColors.current
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Apps", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "Per arrangement, for this screen orientation.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalArrangement = Arrangement.spacedBy(ChipGap),
            modifier = Modifier.padding(top = ChipGap * 2),
        ) {
            ConfigurableLayouts.forEach { layout ->
                SettingsChip(
                    label = layout.label,
                    selected = layout == state.layout,
                    onClick = { viewModel.selectLayout(layout) },
                )
            }
        }

        // Null only for the frame before the device is reported; there is no honest value to show until then, and a
        // placeholder would be a second source of truth for numbers the blueprint owns.
        val size = state.size
        val icon = state.icon
        val rowHeightDp = state.rowHeightDp
        if (size == null || icon == null || rowHeightDp == null) return@Column
        val slot = state.layout.slot
        val metrics = icon.toIconMetrics()
        // The whole window, unlike home's: the APPS surface takes all of it, with no dock to subtract.
        val window = usableWindowArea(safeInsets)

        if (state.layout == AppsLayout.VERTICAL_LIST) {
            // A list is one lane, so it has no grid to edit — its size *is* the row height. The range comes from the
            // icon guardrails below rather than from stated bounds: outside it the height stops changing the icon.
            val range = rowHeightRangeDp(metrics)
            SettingsCommitSlider(
                title = "Row height",
                subtitle = "How tall each row is. The icon fills it.",
                value = rowHeightDp.toFloat().coerceIn(range),
                valueRange = range,
                valueLabel = { "${it.roundToInt()} dp" },
                onCommit = { committed -> viewModel.setRowHeight(committed.roundToInt()) },
            )
        } else {
            // The same editor home and the dock use. No companion zone: an APPS layout fills the screen, so there is
            // no second area to draw at its share of it.
            val range = slot.blueprint.editableRangeIn(window, metrics)
            // A scrolling grid stores no rows, so the preview draws **how many fit**: the cell height this column
            // count implies (the same derivation the surface lays out with) divided into the screen. That is what
            // makes adding a column visibly gain rows as the cells narrow — the actual consequence of the press,
            // which a fixed preview number would hide.
            val cellHeight = cellHeight(cellWidth = window.widthDp.dp / size.cols, metrics = metrics)
            if (range != null) {
                GridEditor(
                    cols = size.cols,
                    rows = size.rows ?: maxCells(window.heightDp, cellHeight.value),
                    colBounds = range.cols,
                    // Null for a scrolling grid, which hides the top and bottom pairs — its rows are however many its
                    // content reaches, so there is nothing there to offer.
                    rowBounds = range.rows,
                    aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f),
                    onEdit = viewModel::edit,
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }
        }

        MorphicButton(
            onClick = viewModel::resetSize,
            style = MorphicButtonStyle.Text,
            modifier = Modifier.padding(top = RowGap * 2),
        ) {
            Text(if (state.layout == AppsLayout.VERTICAL_LIST) "Reset row height" else "Reset grid")
        }

        IconSizingControls(
            slot = slot,
            sizing = icon,
            onChange = viewModel.icons::change,
            onToggle = { label, showIcon -> viewModel.icons.toggle(label, showIcon) },
            onDpRange = viewModel.icons::changeDpRange,
        )
        MorphicButton(
            onClick = viewModel.icons::reset,
            style = MorphicButtonStyle.Text,
            modifier = Modifier.padding(top = RowGap * 2),
        ) {
            Text("Reset icons")
        }
    }
}

/**
 * A human label for a layout.
 *
 * Local to this screen rather than on the enum, for the reason the icons section keeps its own: `core:model` stays
 * free of display strings and of localisation. The names describe the *arrangement* a user sees rather than the enum
 * constant — L1 named the same four "Minimalist", "Classic", "Paged" and "Grouped", which named its own history more
 * than the layouts.
 */
private val AppsLayout.label: String
    get() = when (this) {
        AppsLayout.VERTICAL_LIST -> "List"
        AppsLayout.VERTICAL_GRID -> "Grid"
        AppsLayout.PAGER -> "Pages"
        AppsLayout.PAGER_WITH_CATEGORY -> "Category pages"
        AppsLayout.CATEGORY_CARD -> "Category cards"
    }
