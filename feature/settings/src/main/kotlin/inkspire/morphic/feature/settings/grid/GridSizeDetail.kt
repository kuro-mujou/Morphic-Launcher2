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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.grid.GridArea
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.usableWindowArea
import inkspire.morphic.feature.settings.icons.IconSizingControls
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * **Home**: how many rows and columns HOME's main area is divided into, and how its icons are sized.
 *
 * **Layout group, then icon group**, which is L1's structure for every surface detail and the order the dependency
 * runs in: the icon size sets the smallest usable cell, and that is what this screen's row and column limits are
 * computed from. L1 also put a live icon **preview** between the two; it draws over the wallpaper through a
 * `BlendMode.Src` punch, and that subsystem is deferred, so the controls arrive without it.
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
            val metrics = icon.toIconMetrics()
            val window = usableWindowArea(safeInsets)

            // Home gets the window minus the dock, which is why the dock's height belongs in this screen's state
            // even though nothing here edits it: a taller dock genuinely means fewer home rows fit.
            val homeArea = GridArea(
                widthDp = window.widthDp,
                heightDp = (window.heightDp - dockHeightDp).coerceAtLeast(1f),
            )
            val range = HomePagerGrid.editableRangeIn(homeArea, metrics)

            if (range != null) {
                GridEditor(
                    cols = cols,
                    rows = rows,
                    colBounds = range.cols,
                    rowBounds = range.rows,
                    aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f),
                    onEdit = viewModel::edit,
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

            // The icon group, under the layout group — L1's order in every one of its surface details, and the right
            // one: the icon size is what the grid's limits are computed from, so it reads as the finer adjustment
            // *within* a grid you have already sized.
            IconSizingControls(
                slot = GridSlot.HOME_MAIN,
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
}
