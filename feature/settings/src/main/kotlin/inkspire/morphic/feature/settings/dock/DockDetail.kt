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
import inkspire.morphic.core.designsystem.grid.MinCell
import inkspire.morphic.core.designsystem.grid.WidgetMinCell
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.grid.minCellFor
import inkspire.morphic.core.designsystem.grid.minCellHeightDp
import inkspire.morphic.core.designsystem.grid.minCellWidthDp
import inkspire.morphic.core.designsystem.grid.sideZoneFraction
import inkspire.morphic.core.designsystem.grid.splitForSideZone
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.feature.settings.component.CompanionSide
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.component.of
import inkspire.morphic.feature.settings.icons.IconSizingGroup
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val RowGap = 8.dp

/**
 * The dp window the extent slider spans, and one range for both postures: the slider is titled "Width" on a rail, but
 * what it bounds is the same physical thickness.
 *
 * An earlier cut derived the cap as a fraction of the screen instead. That was invented, and it was answering the
 * wrong question: what limits a useful dock is the cell size it has to divide into, which comes from the icon and
 * text settings — not the size of the display. The cell count is where that constraint actually bites, and it is
 * enforced there.
 */
private val DockExtentRange = 80f..320f

/**
 * The widget area's — far thicker than a dock's, which is the difference between the two zones stated as a number.
 *
 * Far wider and far higher than the dock's, which is the whole difference between the two zones stated as a number: a
 * dock is a row of icons you reach for, and a widget area is a panel you look at.
 */
private val WidgetAreaExtentRange = 120f..480f

/**
 * **HOME's side zone**: how thick it is, the grid inside it, and (when it holds icons) how those are sized — the
 * **dock** under `PAGER_WITH_DOCK` and the **widget area** under `LIST_WITH_WIDGET_AREA`.
 *
 * One section for both, which is the settings mirror of the two zones being the same *kind* of thing: a fixed-extent
 * strip whose counts divide that extent. Everything structural is shared — the extent slider, the grid editor, the
 * margin, the reset — and exactly two things differ, both of them properties of what the zone holds rather than
 * choices made here. **A widget area draws no icons**, so its blueprint declares no icon sizing and this screen shows
 * no icon group at all. And its cells are fitted by a **widget's** floor rather than an icon guardrail's
 * (`WidgetMinCell`).
 *
 * **Where the zone sits decides what this screen's one extent means.** A strip on three device configurations and a
 * rail on a phone in landscape (`SideZoneEdge`) — so the slider is a *height* on the first and a *width* on the
 * second, and the extent bounds the **rows** there and the **columns** here. One stored number per device serves
 * both, because a user configuring a phone in landscape is configuring the rail.
 *
 * **Layout group, then icon group**, which is also the way the dependency runs: the icons decide the smallest usable
 * cell, and that is what the extent above divides into cells. Within the layout group the **editor comes first and the
 * sliders under it**; [SurfaceDetail] owns the arrangement and pins
 * the icon heading and preview together, which earns its place here more than anywhere — a dock cell is the extent
 * *the user set* divided by a count, so the icon in it moves as the slider does.
 *
 * The dock is the one grid with an extent of its own as well as a row and column count, and the extent **bounds** the
 * count divided out of it: a cell is `extent ÷ count`, so past a point another line leaves cells too small to draw an
 * icon in. The slider spans the zone's own range ([DockExtentRange] or [WidgetAreaExtentRange]); the buttons on that
 * axis stop where the division does.
 *
 * **The extent previews while dragging and commits on release**, and the commit carries the cell cap the new extent
 * allows, so a shrink that invalidates the stored count reduces it in the same step.
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
    val paddingDp = state.paddingDp ?: return
    val slot = state.layout.sideSlot
    // **Null here means "this zone draws no icons", not "not yet"** — see `DockState.icon`. It is what selects the
    // whole icon half of this screen: the controls, the preview, and the floor the extent is divided by.
    val icon = state.icon
    val blueprint = slot.blueprint

    // What the preview draws while a slider is held — see the same pair in the Home section for why it is keyed
    // on the resolved value rather than cleared on release.
    var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
    val shownIcon = previewIcon ?: icon
    val metrics = icon?.toIconMetrics()

    // The same window the launcher measures, minus the same insets home applies — so the bounds offered
    // here describe the dock the user will actually get rather than a second idea of the screen.
    val window = usableWindowArea(uiInsets)

    // **Where the zone sits, which is what makes this section's one slider a height or a width.** A rail on a phone
    // in landscape, a strip everywhere else — at the end the layout puts it. Everything below reads the edge rather
    // than the orientation, so there is one place to look when the rule changes.
    val edge = device.sideZoneEdge(state.layout)
    val isRail = !edge.isStrip
    val extentRange = if (state.layout == HomeLayout.LIST_WITH_WIDGET_AREA) WidgetAreaExtentRange else DockExtentRange

    // **The floor a cell of this zone may not go below** — its icon guardrails, or a widget's own minimum for the
    // zone that draws no icons. One value, so every fit, bound and cap below reads the same number.
    val minCell: MinCell = if (metrics == null) WidgetMinCell else minCellFor(metrics)

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
    val split = window.splitForSideZone(shownExtent.toFloat(), edge)
    val zoneArea = split.side.copy(widthDp = (split.side.widthDp - paddingDp * 2).coerceAtLeast(1f))

    // The smallest cell along the axis the extent divides — a height on a strip, a width on a rail. Not a bound on
    // the slider ([extentRange] is) but the number the cell cap below is computed from.
    val minCellOnExtentAxis = if (isRail) minCell.widthDp else minCell.heightDp
    // And the main area's, because this extent decides how much is left for it. A side zone is subtracted from the
    // screen, so growing it can invalidate the *main area's* count exactly as it invalidates this one — the same
    // arithmetic on the other side of the subtraction, on whichever axis the subtraction happened. Null when the main
    // area is a list, which has no count to invalidate: it simply shows fewer rows.
    val homeMetrics = state.homeIcon?.toIconMetrics()
    val homeMinCell = homeMetrics?.let { if (isRail) minCellWidthDp(it) else minCellHeightDp(it) }

    // The zone at this extent, resolved by the **same function the surface uses** — so the preview cannot claim a
    // shape the real zone will not have.
    val zoneConfig = blueprint.fitGridConfig(zoneArea, cols = cols, rows = rows, min = minCell)
    val range = blueprint.editableRangeIn(zoneArea, minCell)

    SurfaceDetail(
        title = if (metrics == null) "Widget area" else "Dock",
        subtitle = if (metrics == null) {
            "Its size, and the grid widgets are placed on."
        } else {
            "Its height, and how many columns fit across it. Rows follow from the height."
        },
        onReroll = viewModel.sample::reroll,
        modifier = modifier,
        layout = {
            if (range != null) {
                // The same editor home's grid uses. The one difference is a property of the dock rather than a
                // choice: its companion zone is the pager, sitting above rather than below.
                //
                // The row buttons are bounded by `range.rows`, which is this height divided by the smallest
                // usable cell — so "+ a row" is offered only while another row would still leave cells tall
                // enough to draw an icon in.
                GridEditor(
                    cols = zoneConfig.visualCols,
                    rows = zoneConfig.visualRows,
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
                    onEdit = { e, add -> viewModel.edit(e, add, zoneConfig.visualCols, zoneConfig.visualRows) },
                    // The main area, on the side the zone is *not* — the opposite edge, whichever that is. So the
                    // mockup shows a rail as a rail rather than as a strip that happens to be thin, and a widget area
                    // above its list rather than below it.
                    companion = EditorCompanion(
                        fraction = 1f - window.sideZoneFraction(shownExtent.toFloat(), edge),
                        side = CompanionSide.of(edge.opposite),
                    ),
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }

            // The slider previews while dragging and writes on release ([SettingsCommitSlider]), so a drag issues
            // one transaction rather than one per frame. The cell cap goes with the commit because the extent that
            // lands may no longer carry the stored count, and the fit is a runtime question this screen owns.
            //
            // **A width on a rail and a height on a strip**, with the subtitle naming whichever count the extent
            // actually divides.
            SettingsCommitSlider(
                title = if (isRail) "Width" else "Height",
                subtitle = if (isRail) {
                    "Columns divide this: at ${shownExtent}dp it holds up to ${range?.cols?.last ?: cols}."
                } else {
                    "Rows divide this: at ${shownExtent}dp it holds up to ${range?.rows?.last ?: rows}."
                },
                value = extentDp.toFloat().coerceIn(extentRange),
                valueRange = extentRange,
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewExtent = it.roundToInt() },
                onCommit = { committed ->
                    val dp = committed.roundToInt()
                    // Re-split at the **committed** extent rather than reading the drag's: the two agree on release,
                    // and deriving the write from the value being written is one fewer thing to keep true.
                    val committedSplit = window.splitForSideZone(dp.toFloat(), edge)
                    viewModel.setExtent(
                        dp = dp,
                        edge = edge,
                        maxCells = maxCells(dp.toFloat(), minCellOnExtentAxis),
                        // What the main area is left with, measured the same way the Home section measures it — so
                        // the two sections cannot disagree about how many cells it can hold. Zero when the main area
                        // is a list, which the ViewModel then ignores along with the whole clamp.
                        homeMaxCells = homeMinCell?.let {
                            maxCells(
                                if (isRail) committedSplit.main.widthDp else committedSplit.main.heightDp,
                                it,
                            )
                        } ?: 0,
                    )
                },
            )

            SettingsCommitSlider(
                title = "Side margin",
                subtitle = "Blank space at the zone's left and right edges.",
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
        preview = if (shownIcon == null) null else { previewModifier ->
            // A side-zone cell divides the strip's own extent, which is the one cell on the launcher whose size a user
            // sets *directly* — so seeing the icon in it while dragging the extent slider is worth more here than
            // anywhere. Both dimensions come from `zoneArea`, so the rail case needs no branch: its width is the
            // extent and its height is the screen's, which is exactly the transpose of the strip.
            IconSizingPreview(
                app = sampleApp,
                metrics = shownIcon.toIconMetrics(),
                cellWidth = (zoneArea.widthDp / zoneConfig.visualCols).dp,
                cellHeight = (zoneArea.heightDp / zoneConfig.visualRows).dp,
                modifier = previewModifier,
            )
        },
        // **No icon group at all on the widget area**, which is `WidgetAreaGrid.icon` being null reaching the screen:
        // a widget is not an icon in a cell, so there is no fraction, guardrail or label to set.
        icons = if (icon == null) null else {
            {
                IconSizingGroup(
                    slot = slot,
                    sizing = icon,
                    edits = viewModel.icons,
                    onPreview = { previewIcon = it },
                )
            }
        },
    )
}
