package inkspire.morphic.feature.settings.grid

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.grid.GridArea
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.icons.IconSizingControls
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * **Home**: how many rows and columns HOME's main area is divided into, and how its icons are sized.
 *
 * **Layout group, then icon group**, which is L1's structure for every surface detail and the order the dependency
 * runs in: the icon size sets the smallest usable cell, and that is what this screen's row and column limits are
 * computed from. The live icon **preview** sits between the two, as L1 put it — see [IconSizingPreview], including the
 * one piece of L1's version still missing (it punched through to the live wallpaper, which needs `data:wallpaper`).
 *
 * The counterpart of the dock section, and the difference between them is the whole shape of grid configuration in
 * this launcher: home is sized **by counts** — the user picks rows and columns and the surface divides its space into
 * that many cells — while the dock is sized **by extent**. So this screen offers both axes and no height, and that
 * one offers a height and only columns.
 *
 * **The area it measures against is the window minus the dock**, which is exactly what home gets. That subtraction is
 * this screen's arithmetic rather than part of measuring a window ([usableWindowArea]) — L1 folded it into the
 * measurement (`homeGridArea(..., dockVisible, dockThickness)`), so every caller had to supply dock facts even when
 * sizing something unrelated.
 *
 * **The editor shows the grid home will draw, not the one in storage** — so changing the icon size below recalculates
 * it. The two ends of that dependency are one formula: the icon guardrails set the smallest usable cell, `CellFit`
 * divides the area by it, and both the *bounds* the buttons offer and the *counts* the preview draws come out of that
 * same division. Raise the minimum icon dp far enough and a 4×5 home becomes 4×4 in front of you, which is what the
 * surface has been drawing all along.
 *
 * **A count invalidated that way is clamped, never overwritten.** Shrink the icons again and the row comes back,
 * because nothing wrote the clamp down. L1 did write it — a `LaunchedEffect` right here, firing on every cause — so an
 * icon tweak permanently destroyed a row count that had nothing to do with icons, and only while this screen happened
 * to be open. The one write that reduces home's rows belongs to the dock's height commit, which is a deliberate change
 * to the space home is left with.
 *
 * **Editing names an edge, not a number**, and each press is two writes — the count and the placements it displaces.
 * See [GridSizeViewModel]; the reason is that only the button press knows *which* side changed, and a left column
 * removed and a right column removed leave your apps in different places.
 *
 * @param onBack leaves the section. Wired to the navigator by the host, and to system back here so the two agree.
 */
@Composable
internal fun GridSizeDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GridSizeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()


    val colors = LocalMorphicColors.current
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Home grid", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "Rows and columns on the home pages. The − and + on each edge change that side.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        // Null only for the frame before the device is reported; there is no honest value to show until then.
        val cols = state.cols
        val rows = state.rows
        val icon = state.icon
        val dockHeightDp = state.dockHeightDp
        if (cols != null && rows != null && icon != null && dockHeightDp != null) {
            // **What the preview draws while a slider is held.** Keyed on the resolved sizing, so a commit clears it and
            // the preview falls back to what was stored — the same shape `SettingsCommitSlider` uses for its own label,
            // and the same reason: the write is asynchronous, so dropping the local value on release would flash the old
            // one. The *controls* still read `icon`, since a slider must show its committed position.
            var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
            val shownIcon = previewIcon ?: icon
            val metrics = icon.toIconMetrics()
            val window = usableWindowArea(safeInsets)

            // Home gets the window minus the dock, which is why the dock's height belongs in this screen's state
            // even though nothing here edits it: a taller dock genuinely means fewer home rows fit.
            val homeArea = GridArea(
                widthDp = window.widthDp,
                heightDp = (window.heightDp - dockHeightDp).coerceAtLeast(1f),
            )
            val range = HomePagerGrid.editableRangeIn(homeArea, metrics)

            // **The grid as home will actually draw it**, through the same `fitGridConfig` the surface reads its own
            // counts with — which is what makes the icon group below move this editor. A stored count outlives the
            // conditions it was chosen under: raise the minimum icon size and a 4×5 home is a 4×4 one, because a cell
            // has to stay tall enough to draw an icon in. Until this was fitted, the editor went on claiming the 5
            // rows in storage while the surface drew 4, and the buttons counted from the number nobody could see.
            //
            // The clamp is **not** written back — shrink the icons again and the fifth row returns. Only a press
            // writes, which is the same asymmetry the dock applies to its own columns.
            val fitted = HomePagerGrid.fitGridConfig(homeArea, cols = cols, rows = rows, metrics = metrics)

            if (range != null) {
                GridEditor(
                    cols = fitted.visualCols,
                    rows = fitted.visualRows,
                    colBounds = range.cols,
                    rowBounds = range.rows,
                    aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f),
                    // Counting from the drawn grid rather than the stored one, so − and + mean what the preview shows.
                    onEdit = { edge, add -> viewModel.edit(edge, add, fitted.visualCols, fitted.visualRows) },
                    // The dock, at its real share of the screen — so shrinking home's rows and growing the dock
                    // read as the same picture from either section.
                    companion = EditorCompanion(
                        fraction = (dockHeightDp / window.heightDp.coerceAtLeast(1f)),
                        atBottom = true,
                    ),
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }

            MorphicButton(
                onClick = viewModel::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text("Reset grid")
            }

            // The **live preview**, between the layout group and the icon group — L1's position, and it is the reason
            // the icon controls are legible at all: three numbers (a fraction and two dp bounds) do not tell you what
            // you get in *this* cell, so the cell is drawn at its real size with the guardrails outlined on it.
            IconSizingPreview(
                app = sampleApp,
                // A home cell's size comes from dividing an area, so the fraction is the cell's to apply and these
                // metrics go straight through — unlike the two APPS grids whose height is derived from it.
                metrics = shownIcon.toIconMetrics(),
                cellWidth = (homeArea.widthDp / fitted.visualCols).dp,
                cellHeight = (homeArea.heightDp / fitted.visualRows).dp,
                onReroll = viewModel.sample::reroll,
                modifier = Modifier.padding(top = RowGap * 2),
            )

            // The icon group, under the layout group — L1's order in every one of its surface details, and the right
            // one: the icon size is what the grid's limits are computed from, so it reads as the finer adjustment
            // *within* a grid you have already sized.
            IconSizingControls(
                slot = GridSlot.HOME_MAIN,
                sizing = icon,
                onChange = viewModel.icons::change,
                onToggle = { label, showIcon -> viewModel.icons.toggle(label, showIcon) },
                onDpRange = viewModel.icons::changeDpRange,
                onPreview = { previewIcon = it },
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
}
