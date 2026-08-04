package inkspire.morphic.feature.settings.dock

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
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.grid.minCellHeightDp
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.DockGrid
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.icons.IconSizingControls
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * The dp window the height slider spans — **L1's, verbatim** (`valueRange = 80f..320f` in its `DockSettingsDetail`).
 *
 * An earlier cut derived the cap as a fraction of the screen instead. That was invented, and it was answering the
 * wrong question: what limits a useful dock is the cell height it has to divide into, which comes from the icon and
 * text settings — not the size of the display. The row count is where that constraint actually bites, and it is
 * enforced there.
 */
private val HeightRange = 80f..320f

/**
 * **Dock**: how tall the strip at the bottom of HOME is, the grid inside it, and how its icons are sized.
 *
 * **Layout group, then icon group** — L1's structure for every surface detail, and the dependency runs that way too:
 * the icons decide the smallest usable cell, which is what the height and width above divide into rows and columns.
 * What is missing beside L1's is the live icon **preview** between the two groups; it renders over the wallpaper
 * through a `BlendMode.Src` punch, and that whole subsystem (`data:wallpaper`, transparent launcher surfaces) is
 * deferred. The controls are the half that works without it.
 *
 * The dock is the one grid with a height of its own as well as a row and column count, and the height **bounds** the
 * rows: a cell is `height ÷ rows`, so past a point another row leaves cells too short to draw an icon in. The slider
 * spans [HeightRange]; the row buttons stop where that division does.
 *
 * **The height previews while dragging and commits on release**, and the commit carries the row cap the new height
 * allows, so a shrink that invalidates the stored rows reduces them in the same step. That is L1's sequence.
 *
 * **A column count too large for the current icon size is *not* written down** — it is clamped where the grid is
 * drawn and returns when the icons shrink. Only rows are reduced in storage, because only they are invalidated by a
 * change the user just made to this screen.
 *
 * **It edits the device configuration you are holding**, like the icon-sizing section, because that is how overrides
 * are keyed — a dp height that suits portrait is most of a landscape screen.
 */
@Composable
internal fun DockDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<DockViewModel>()
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
        Text("Dock", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "Its height, and how many columns fit across it. Rows follow from the height.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

        // Null only for the frame before the device is reported; there is no honest value to show until then,
        // and a placeholder would be a second source of truth for numbers the blueprint owns.
        val heightDp = state.heightDp
        val cols = state.cols
        val rows = state.rows
        val icon = state.icon
        val homeIcon = state.homeIcon
        if (heightDp != null && cols != null && rows != null && icon != null && homeIcon != null) {
            val metrics = icon.toIconMetrics()

            // The same window the launcher measures, minus the same insets home applies — so the bounds offered
            // here describe the dock the user will actually get rather than a second idea of the screen.
            val usable = usableWindowArea(safeInsets)

            // The smallest cell this dock's icons need, which is what a height has to divide into whole rows.
            // Not a bound on the slider — [HeightRange] is — but the number the row cap is computed from below.
            val minCellHeight = minCellHeightDp(metrics)
            // And home's, because this height decides how much is left for the pager. A dock is subtracted from the
            // screen, so growing it can invalidate *home's* row count exactly as it invalidates this one — the same
            // arithmetic on the other side of the subtraction.
            val homeMinCellHeight = minCellHeightDp(homeIcon.toIconMetrics())

            // The dock at this height, resolved by the **same function the surface uses** — so the preview cannot
            // claim a shape the real dock will not have.
            val dockArea = usable.copy(heightDp = heightDp.toFloat())
            val dockConfig = DockGrid.fitGridConfig(dockArea, cols = cols, rows = rows, metrics = metrics)
            val range = DockGrid.editableRangeIn(dockArea, metrics)

            // The slider previews while dragging and writes on release ([SettingsCommitSlider]), so a drag issues
            // one transaction rather than one per frame. The row cap goes with the commit because the height that
            // lands may no longer carry the stored rows, and the fit is a runtime question this screen owns.
            SettingsCommitSlider(
                title = "Height",
                subtitle = "Rows divide this: at ${heightDp}dp it holds up to ${range?.rows?.last ?: rows}.",
                value = heightDp.toFloat().coerceIn(HeightRange),
                valueRange = HeightRange,
                valueLabel = { "${it.roundToInt()} dp" },
                onCommit = { committed ->
                    val dp = committed.roundToInt()
                    viewModel.setHeight(
                        dp = dp,
                        maxRows = maxCells(dp.toFloat(), minCellHeight),
                        // What the pager is left with, measured the same way the Home section measures it — so the
                        // two sections cannot disagree about how many rows home can hold.
                        homeMaxRows = maxCells(usable.heightDp - dp, homeMinCellHeight),
                    )
                },
            )

            if (range != null) {
                // The same editor home's grid uses. The one difference is a property of the dock rather than a
                // choice: its companion zone is the pager, sitting above rather than below. L1 had a second
                // ~220-line editor for this.
                //
                // The row buttons are bounded by `range.rows`, which is this height divided by the smallest
                // usable cell — so "+ a row" is offered only while another row would still leave cells tall
                // enough to draw an icon in.
                GridEditor(
                    cols = dockConfig.visualCols,
                    rows = dockConfig.visualRows,
                    colBounds = range.cols,
                    rowBounds = range.rows,
                    aspectRatio = usable.widthDp / usable.heightDp.coerceAtLeast(1f),
                    // Counting from the fitted grid above rather than from the stored counts, so a press changes the
                    // number the preview is showing. Storage can legitimately hold more columns than fit — that is the
                    // clamp-on-read rule — and an edit that ignored the fit would move a count nobody can see.
                    onEdit = { edge, add -> viewModel.edit(edge, add, dockConfig.visualCols, dockConfig.visualRows) },
                    companion = EditorCompanion(
                        fraction = 1f - (heightDp / usable.heightDp.coerceAtLeast(1f)),
                        atBottom = false,
                    ),
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }

            MorphicButton(
                onClick = viewModel::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text("Reset height and grid")
            }

            // The icon group, under the layout group — L1's order in every surface detail. Here the dependency is
            // especially direct: these controls set the smallest usable cell, which is what the height above divides
            // into rows, so raising the icon size can take a row away.
            IconSizingControls(
                slot = GridSlot.HOME_DOCK,
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
