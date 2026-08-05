package inkspire.morphic.feature.settings.dock

import androidx.compose.foundation.layout.padding
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
import inkspire.morphic.core.designsystem.grid.dockFraction
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.grid.minCellHeightDp
import inkspire.morphic.core.designsystem.grid.minCellWidthDp
import inkspire.morphic.core.designsystem.grid.splitForDock
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.DockEdge
import inkspire.morphic.core.model.DockGrid
import inkspire.morphic.core.model.dockEdge
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.feature.settings.component.CompanionSide
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.icons.IconSizingControls
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val RowGap = 8.dp

/**
 * The dp window the extent slider spans — **L1's, verbatim** (`valueRange = 80f..320f` in its `DockSettingsDetail`),
 * and one range for both postures, as L1 also had it: the slider is titled "Width" on a rail, but what it bounds is the
 * same physical thickness.
 *
 * An earlier cut derived the cap as a fraction of the screen instead. That was invented, and it was answering the
 * wrong question: what limits a useful dock is the cell size it has to divide into, which comes from the icon and
 * text settings — not the size of the display. The cell count is where that constraint actually bites, and it is
 * enforced there.
 */
private val ExtentRange = 80f..320f

/**
 * **Dock**: how thick HOME's dock is, the grid inside it, and how its icons are sized.
 *
 * **Where the dock sits decides what this screen's one extent means.** A bottom strip on three device configurations
 * and a rail down the trailing edge on a phone in landscape (`DockEdge`) — so the slider is a *height* on the first
 * and a *width* on the second, and the extent bounds the **rows** there and the **columns** here. One stored number
 * per device serves both, because a user configuring a phone in landscape is configuring the rail. L1 called the same
 * value `extentDp` and titled the same slider the same way.
 *
 * **Layout group, then icon group** — L1's structure for every surface detail, and the dependency runs that way too:
 * the icons decide the smallest usable cell, which is what the extent above divides into cells. Within the layout group
 * the **editor comes first and the sliders under it**, again L1's order; [SurfaceDetail] owns the arrangement and pins
 * the icon heading and preview together, which earns its place here more than anywhere — a dock cell is the extent
 * *the user set* divided by a count, so the icon in it moves as the slider does.
 *
 * The dock is the one grid with an extent of its own as well as a row and column count, and the extent **bounds** the
 * count divided out of it: a cell is `extent ÷ count`, so past a point another line leaves cells too small to draw an
 * icon in. The slider spans [ExtentRange]; the buttons on that axis stop where the division does.
 *
 * **The extent previews while dragging and commits on release**, and the commit carries the cell cap the new extent
 * allows, so a shrink that invalidates the stored count reduces it in the same step. That is L1's sequence.
 *
 * **The count on the *other* axis is never written down** when it outgrows the icons — it is clamped where the grid is
 * drawn and returns when the icons shrink. Only the axis the extent divides is reduced in storage, because only it is
 * invalidated by a change the user just made to this screen.
 *
 * **It edits the device configuration you are holding**, like the icon-sizing section, because that is how overrides
 * are keyed — and here it does double duty: a phone in landscape is where the dock is a rail, so its extent is stored
 * against that posture and never seen by the other three.
 */
@Composable
internal fun DockDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<DockViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()


    // Null only for the frame before the device is reported; there is no honest value to show until then,
    // and a placeholder would be a second source of truth for numbers the blueprint owns.
    val extentDp = state.extentDp ?: return
    val cols = state.cols ?: return
    val rows = state.rows ?: return
    val icon = state.icon ?: return
    val homeIcon = state.homeIcon ?: return
    val paddingDp = state.paddingDp ?: return

    // What the preview draws while a slider is held — see the same pair in the Home section for why it is keyed
    // on the resolved value rather than cleared on release.
    var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
    val shownIcon = previewIcon ?: icon
    val metrics = icon.toIconMetrics()

    // The same window the launcher measures, minus the same insets home applies — so the bounds offered
    // here describe the dock the user will actually get rather than a second idea of the screen.
    val window = usableWindowArea(uiInsets)

    // **Where the dock sits, which is what makes this section's one slider a height or a width.** A rail on a phone
    // in landscape, a bottom strip everywhere else; everything below reads the edge rather than the orientation, so
    // there is one place to look when the rule changes.
    val dockEdge = device.dockEdge
    val isRail = dockEdge == DockEdge.END

    // **Previewed while the slider is held**, so the editor above re-splits and re-fits under the finger rather than
    // jumping on release. Everything derived from the extent reads the *shown* one: the dock's fitted grid, its
    // editable range, the companion split, and the caption. A preview that showed the new split but the old count
    // would be worse than one that showed neither, because the count is the thing the extent decides.
    var previewExtent by remember(extentDp) { mutableStateOf<Int?>(null) }
    val shownExtent = previewExtent ?: extentDp
    var previewPadding by remember(paddingDp) { mutableStateOf<Int?>(null) }
    val shownPadding = previewPadding ?: paddingDp

    // The split the surface itself draws with, so this section cannot describe a dock home will not give. Then
    // narrowed by the margin, because everything below divides *this* width: the fitted grid, the editable column
    // range, and the preview's aspect.
    val split = window.splitForDock(shownExtent.toFloat(), dockEdge)
    val dockArea = split.dock.copy(widthDp = (split.dock.widthDp - paddingDp * 2).coerceAtLeast(1f))

    // The smallest cell this dock's icons need along the axis the extent divides — a height on a strip, a width on a
    // rail. Not a bound on the slider ([ExtentRange] is) but the number the cell cap below is computed from.
    val minCell = if (isRail) minCellWidthDp(metrics) else minCellHeightDp(metrics)
    // And home's, because this extent decides how much is left for the pager. A dock is subtracted from the
    // screen, so growing it can invalidate *home's* count exactly as it invalidates this one — the same
    // arithmetic on the other side of the subtraction, on whichever axis the subtraction happened.
    val homeMetrics = homeIcon.toIconMetrics()
    val homeMinCell = if (isRail) minCellWidthDp(homeMetrics) else minCellHeightDp(homeMetrics)

    // The dock at this extent, resolved by the **same function the surface uses** — so the preview cannot claim a
    // shape the real dock will not have.
    val dockConfig = DockGrid.fitGridConfig(dockArea, cols = cols, rows = rows, metrics = metrics)
    val range = DockGrid.editableRangeIn(dockArea, metrics)

    SurfaceDetail(
        title = "Dock",
        subtitle = "Its height, and how many columns fit across it. Rows follow from the height.",
        onReroll = viewModel.sample::reroll,
        modifier = modifier,
        layout = {
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
                    // **The whole screen's ratio, not the narrowed one.** The preview is a picture of the device; the
                    // margin is drawn *inside* it below. Feeding the reduced width here instead made the box taller
                    // for every dp of margin, since it is `fillMaxWidth().aspectRatio(ratio)`.
                    aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f),
                    horizontalInsetFraction = shownPadding / window.widthDp,
                    // Counting from the fitted grid above rather than from the stored counts, so a press changes the
                    // number the preview is showing. Storage can legitimately hold more columns than fit — that is the
                    // clamp-on-read rule — and an edit that ignored the fit would move a count nobody can see.
                    onEdit = { edge, add -> viewModel.edit(edge, add, dockConfig.visualCols, dockConfig.visualRows) },
                    // The pager, on the side the dock is *not* — above a bottom strip, before a rail. So the mockup
                    // shows a rail as a rail rather than as a strip that happens to be thin.
                    companion = EditorCompanion(
                        fraction = 1f - window.dockFraction(shownExtent.toFloat(), dockEdge),
                        side = if (isRail) CompanionSide.START else CompanionSide.TOP,
                    ),
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }

            // The slider previews while dragging and writes on release ([SettingsCommitSlider]), so a drag issues
            // one transaction rather than one per frame. The cell cap goes with the commit because the extent that
            // lands may no longer carry the stored count, and the fit is a runtime question this screen owns.
            //
            // **A width on a rail and a height on a strip** — L1 titled the same slider the same way, and the subtitle
            // names whichever count the extent actually divides.
            SettingsCommitSlider(
                title = if (isRail) "Width" else "Height",
                subtitle = if (isRail) {
                    "Columns divide this: at ${shownExtent}dp it holds up to ${range?.cols?.last ?: cols}."
                } else {
                    "Rows divide this: at ${shownExtent}dp it holds up to ${range?.rows?.last ?: rows}."
                },
                value = extentDp.toFloat().coerceIn(ExtentRange),
                valueRange = ExtentRange,
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewExtent = it.roundToInt() },
                onCommit = { committed ->
                    val dp = committed.roundToInt()
                    // Re-split at the **committed** extent rather than reading the drag's: the two agree on release,
                    // and deriving the write from the value being written is one fewer thing to keep true.
                    val committedSplit = window.splitForDock(dp.toFloat(), dockEdge)
                    viewModel.setExtent(
                        dp = dp,
                        edge = dockEdge,
                        maxCells = maxCells(dp.toFloat(), minCell),
                        // What the pager is left with, measured the same way the Home section measures it — so the
                        // two sections cannot disagree about how many cells home can hold.
                        homeMaxCells = maxCells(
                            if (isRail) committedSplit.main.widthDp else committedSplit.main.heightDp,
                            homeMinCell,
                        ),
                    )
                },
            )

            SettingsCommitSlider(
                title = "Side margin",
                subtitle = "Blank space at the dock's left and right edges.",
                value = paddingDp.toFloat(),
                valueRange = HorizontalPaddingRange.first.toFloat()..HorizontalPaddingRange.last.toFloat(),
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewPadding = it.roundToInt() },
                onCommit = { viewModel.setPadding(it.roundToInt()) },
            )

            MorphicButton(
                onClick = viewModel::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text("Reset size and grid")
            }
        },
        preview = { previewModifier ->
            // A dock cell divides the strip's own extent, which is the one cell on the launcher whose size a user sets
            // *directly* — so seeing the icon in it while dragging the extent slider is worth more here than anywhere.
            // Both dimensions come from `dockArea`, so the rail case needs no branch: its width is the extent and its
            // height is the screen's, which is exactly the transpose of the strip.
            IconSizingPreview(
                app = sampleApp,
                metrics = shownIcon.toIconMetrics(),
                cellWidth = (dockArea.widthDp / dockConfig.visualCols).dp,
                cellHeight = (dockArea.heightDp / dockConfig.visualRows).dp,
                modifier = previewModifier,
            )
        },
        icons = {
            IconSizingControls(
                slot = GridSlot.HOME_DOCK,
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
        },
    )
}
