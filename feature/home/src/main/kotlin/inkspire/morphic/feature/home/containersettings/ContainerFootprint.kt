package inkspire.morphic.feature.home.containersettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.feature.home.footprintOf
import inkspire.morphic.feature.home.homeZoneArea
import org.koin.compose.koinInject

/**
 * **How big a container really is on home, and with what icon sizing** — everything a preview needs to be a scale
 * model of it rather than a likeness.
 *
 * @property size the footprint in dp, at the same aspect ratio and the same absolute size the home surface draws it.
 * @property metrics the icon guardrails its zone resolves through, so the icons inside come out the same fraction of
 *   their slots. Carried alongside the size rather than read from `LocalIconMetrics` at the draw site: a settings
 *   screen's ambient metrics are not home's, and an icon capped at a *different* `maxIconDp` is exactly the way a
 *   preview looks nearly right and is not.
 */
@Immutable
internal data class ContainerFootprint(val size: DpSize, val metrics: IconMetrics)

/**
 * The real footprint of a container placed at [placement] in [zone], or null until the stores have answered.
 *
 * **Why a preview needs this at all.** Drawn at some pleasant size of its own, a container is not a smaller picture
 * of the real one — it is a *different* one. Two things inside it are absolute rather than proportional: the gap
 * between icons is a flat 8dp, and an icon is capped at the user's `maxIconDp`. So a 148dp square preview of a
 * 180×190dp container gives its icons a larger share of their slots (the cap stops binding) and a smaller share to
 * the gaps, and the shape a `CIRCLE` or a `GRID` makes changes outright, since `gridSlots` picks its column count
 * from the box's own aspect ratio. Drawing at the true size and then scaling the whole thing graphically is what
 * makes the difference a scale factor instead of a redesign.
 *
 * **It asks the same questions the surface asks, through the same functions** — [homeZoneArea] for the region, the
 * blueprint's own `fitGridConfig` for the counts, [footprintOf] for the span. Restating any of that here is how the
 * preview would agree today and quietly stop agreeing the first time one of them was retuned, and a preview that has
 * drifted is worse than none because it is believed.
 *
 * **`PAGER_WITH_DOCK` is not an assumption, it is the only pairing an icon container exists under.** The widget area
 * accepts widgets alone (`CONTAINERS_PLAN` §4), so an icon container is never in the side zone of the other pairing,
 * and HOME_MAIN's coordinate grid is the grid its placement lives in whichever main area happens to be on screen —
 * which is the same reason `HomeViewModel.pagerConfig` is deliberately not gated on the layout.
 *
 * Reading the four settings here rather than through [ContainerSettingsViewModel] is deliberate and is the narrower
 * choice: the **device** is a composition value, so a ViewModel would need it pushed in and held, which is the
 * `setDevice` plumbing home carries for state it genuinely owns. This owns nothing — it is derivation over four
 * repository reads, in the shape `rememberHomePagerLayout` already uses for the same job.
 */
@Composable
internal fun rememberContainerFootprint(placement: GridPlacement, zone: HomeZone): ContainerFootprint? {
    val settings = koinInject<SettingsRepository>()
    val device = currentDeviceConfiguration()
    val slot = if (zone == HomeZone.MAIN) GridSlot.HOME_MAIN else GridSlot.HOME_DOCK

    val stored by remember(settings, slot, device) { settings.gridConfig(slot, device) }
        .collectAsStateWithLifecycle(null)
    val dockExtentDp by remember(settings, device) { settings.extent(GridSlot.HOME_DOCK, device) }
        .collectAsStateWithLifecycle(null)
    val paddingDp by remember(settings, slot, device) { settings.horizontalPadding(slot, device) }
        .collectAsStateWithLifecycle(null)
    val sizing by remember(settings, slot, device) { settings.iconSizing(slot, device) }
        .collectAsStateWithLifecycle(null)

    // **Null rather than a blueprint fallback while the stores answer**, which is the opposite call from
    // `rememberHomePagerLayout`'s and right for the opposite reason: there a wrong first value is *drawn as home* for
    // a frame, and the blueprint is the closest honest guess; here nothing is drawn but a preview, and one frame of a
    // container at the wrong size is a flicker the caller can avoid entirely by holding the pane's shape instead.
    val config = stored ?: return null
    val extent = dockExtentDp ?: return null
    val metrics = (sizing ?: return null).toIconMetrics()

    val area = homeZoneArea(
        zone = zone,
        dockExtent = extent.dp,
        dockEdge = device.sideZoneEdge(HomeLayout.PAGER_WITH_DOCK),
        padding = (paddingDp ?: 0).dp,
    )
    // The counts are clamped to the area exactly as the surface clamps them, so a stored grid the current posture
    // cannot draw is previewed at the size it will actually be drawn at rather than the size it was stored as.
    val fitted = slot.blueprint.fitGridConfig(
        area = area,
        cols = config.visualCols,
        rows = config.visualRows,
        metrics = metrics,
    )
    return ContainerFootprint(size = area.footprintOf(placement, fitted), metrics = metrics)
}
