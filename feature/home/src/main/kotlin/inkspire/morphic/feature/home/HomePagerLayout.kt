package inkspire.morphic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.grid.GridArea
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.splitForSideZone
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.DockGrid
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.SideZoneEdge
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.core.model.toGridConfig

/**
 * How `PAGER_WITH_DOCK` divides the window between its two zones, and the size each grid is drawn at.
 *
 * Every field is derived — from the device, the stored settings in [HomeState], and the usable window — and nothing
 * here is state. That is what makes it separable from [HomePagerSurface], which owns the drag, the gestures and the
 * measured geometry that genuinely are.
 *
 * @property config the main pager's grid, fitted to what the dock leaves it.
 * @property dockConfig the dock's, fitted to its own extent.
 * @property mainFromStore whether [config] came from the store rather than from the blueprint fallback — the guard on
 *   re-settling, and load-bearing rather than tidy. See [rememberHomePagerLayout].
 * @property dockFromStore the same for [dockConfig].
 */
@Immutable
internal data class HomePagerLayout(
    val config: GridConfig,
    val dockConfig: GridConfig,
    val mainMetrics: IconMetrics,
    val dockMetrics: IconMetrics,
    val dockEdge: SideZoneEdge,
    val dockExtent: Dp,
    val mainPadding: Dp,
    val dockPadding: Dp,
    val mainFromStore: Boolean,
    val dockFromStore: Boolean,
)

/**
 * Works out both grids' sizes for the current device and settings.
 *
 * **Extracted as a value-returning composable, which is why it is a safe thing to have moved.** A `@Composable` that
 * returns a value is not a restart scope of its own — Compose cannot skip it — so this behaves exactly as the same
 * lines did inline: the same `remember` keys, the same snapshot reads of [state], the same recompositions. Pulling
 * *UI* out of a surface is the move that changes scoping; pulling derivation out is not.
 *
 * The order below is the dependency order, and each step is why the next one can be taken:
 *
 * 1. **Where the dock sits** ([SideZoneEdge]) and how thick it is, which decides everything after it: a bottom strip
 *    on three configurations and a trailing rail in phone landscape, the one posture with no height to spare.
 * 2. **Each zone's area**, from [homeZoneArea] — the window, less the other zone, less this zone's own margin.
 * 3. **Each grid is fitted to the area it actually gets**, through the same `fitGridConfig` the settings sections
 *    bound their editors with. The section says how many rows may be *chosen* and this says how many are *drawn*;
 *    one formula is what keeps those two answers the same.
 *
 * **The blueprint is a fallback for the first frame, not the source.** The store answers a frame or two later, and
 * until it does the blueprint's default is the same number it would resolve to for a user who has changed nothing.
 * Drawing the blueprint *as* the source is what makes a settings screen write a size nothing reads.
 *
 * **Clamped on read, never written back**: the count the user chose survives, so shortening the dock brings home's
 * rows straight back. Only the *items* a smaller grid cannot hold are written, by the caller's settle effects — and
 * [mainFromStore] / [dockFromStore] are what stop those running against a blueprint fallback, which is a *smaller*
 * grid than one the user has grown. Settling against it would re-home items to fit a size nobody chose, and the write
 * would outlive the frame that caused it.
 */
@Composable
internal fun rememberHomePagerLayout(state: HomeState, device: DeviceConfiguration): HomePagerLayout {
    val dockEdge = device.sideZoneEdge(HomeLayout.PAGER_WITH_DOCK)
    val dockSizing = state.side
    val dockExtent = (dockSizing?.extentDp ?: checkNotNull(DockGrid.extentDp)).dp

    val mainPadding = state.paddingFor(GridSlot.HOME_MAIN).dp
    val dockPadding = state.paddingFor(GridSlot.HOME_DOCK).dp
    val mainArea = homeZoneArea(HomeZone.MAIN, dockExtent, dockEdge, mainPadding)
    val dockArea = homeZoneArea(HomeZone.DOCK, dockExtent, dockEdge, dockPadding)

    // The dock is the one grid with an extent of its own: the user sets how thick the strip is *and* how many rows
    // and columns divide it, and `fitGridConfig` clamps those counts to what the extent and the icon size allow. On
    // a rail that bound falls on the columns rather than the rows, which needs no branch here — the extent is in the
    // area's width, and `CellFit` fits each axis to the dimension it is given.
    val dockMetrics = state.metricsFor(GridSlot.HOME_DOCK)
    val dockBlueprintConfig = remember(device) { DockGrid.toGridConfig(device) }
    val dockConfig = if (dockSizing == null) {
        dockBlueprintConfig
    } else {
        DockGrid.fitGridConfig(
            area = dockArea,
            cols = dockSizing.cols,
            rows = dockSizing.rows,
            metrics = dockMetrics,
        )
    }

    // The pager is fitted to what the dock leaves it. Drawn at its stored size regardless of the space available,
    // raising the dock's height would squeeze home's rows into whatever was left instead of reducing them — and past
    // a point the cells are shorter than the icon they hold.
    val mainMetrics = state.metricsFor(GridSlot.HOME_MAIN)
    val storedMain = (state.main as? HomeMainSizing.Pager)?.config
    val blueprintConfig = remember(device) { HomePagerGrid.toGridConfig(device) }
    val config = if (storedMain == null) {
        blueprintConfig
    } else {
        HomePagerGrid.fitGridConfig(
            area = mainArea,
            cols = storedMain.visualCols,
            rows = storedMain.visualRows,
            metrics = mainMetrics,
        )
    }

    return HomePagerLayout(
        config = config,
        dockConfig = dockConfig,
        mainMetrics = mainMetrics,
        dockMetrics = dockMetrics,
        dockEdge = dockEdge,
        dockExtent = dockExtent,
        mainPadding = mainPadding,
        dockPadding = dockPadding,
        mainFromStore = storedMain != null,
        dockFromStore = dockSizing != null,
    )
}

/**
 * **The dp area [zone]'s grid is actually drawn in** — the window, less the other zone, less this zone's own margin.
 *
 * Shared rather than inlined because a second caller arrived that has to agree with the surface *exactly*: an icon
 * container's settings screen draws the container at the size home draws it, and the only way that stays true is for
 * both to ask one question. A settings screen deriving its own idea of the area is how a preview becomes a
 * confident lie — it agrees on the day it is written and drifts the first time the dock's extent changes meaning.
 *
 * Three things it settles, in dependency order:
 *
 * 1. **The window**, from `usableWindowArea(uiInsets)` — the same expression the settings sections and the APPS
 *    surface read, so a bound computed for this screen somewhere else describes the same screen.
 * 2. **The split**, which the edge decides the *axis* of and the extent the *amount* of (see `splitForSideZone`).
 * 3. **The margin comes off before anything is fitted**, and horizontally whichever edge the dock is on: a rail's
 *    margin insets *within* its width, which is what a "side margin" means on a vertical strip too. Fitting against
 *    the full width would size cells the grid then has no room to draw.
 *
 * [HomeZone.WIDGET_AREA] answers with the side zone's area exactly as [HomeZone.DOCK] does — the two are the same
 * region under the two pairings, and which one a window has is [HomeLayout]'s business rather than this arithmetic's.
 *
 * @param dockExtent the side zone's thickness — its height on a strip, its width on a rail.
 * @param padding [zone]'s own horizontal margin, taken off **both** edges.
 */
@Composable
internal fun homeZoneArea(zone: HomeZone, dockExtent: Dp, dockEdge: SideZoneEdge, padding: Dp): GridArea {
    val split = usableWindowArea(uiInsets).splitForSideZone(dockExtent.value, dockEdge)
    val area = if (zone == HomeZone.MAIN) split.main else split.side
    return area.copy(widthDp = (area.widthDp - padding.value * 2).coerceAtLeast(1f))
}

/**
 * **How big [placement]'s footprint is in dp**, on a grid of [config] drawn in this area.
 *
 * Spans are in *logical* cells and the area divides into logical cells, so this needs no `cellMultiplier` of its own
 * — which is the point of expressing it as a fraction of the area rather than as a cell size times a visual span.
 * The multiplier is the one number in this system that is easy to apply twice.
 */
internal fun GridArea.footprintOf(placement: GridPlacement, config: GridConfig): DpSize = DpSize(
    width = (widthDp * placement.colSpan / config.cols).dp,
    height = (heightDp * placement.rowSpan / config.rows).dp,
)
