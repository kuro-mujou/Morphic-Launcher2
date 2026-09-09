package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Every HOME zone's resolved grid for one [configuration] — the three slots a placement can live in, keyed by the
 * zone that names it.
 *
 * **An extension rather than a repository method**, because it composes three answers the repository already gives
 * rather than reaching a store none of them reach.
 *
 * It lives here rather than at either call site because there are two, and they have to agree: the home surface
 * resolves these to draw and to re-lay, and the Orientation section resolves them for a posture that is *not* on
 * screen when it copies one arrangement onto another. A second, hand-rolled version of this map would be wrong in
 * the way that never surfaces — a missing zone drops that zone's items silently rather than failing.
 */
fun SettingsRepository.homeZoneGrids(configuration: DeviceConfiguration): Flow<Map<HomeZone, GridConfig>> =
    combine(
        gridConfig(GridSlot.HOME_MAIN, configuration),
        gridConfig(GridSlot.HOME_DOCK, configuration),
        gridConfig(GridSlot.HOME_WIDGET_AREA, configuration),
    ) { main, dock, widgetArea ->
        mapOf(HomeZone.MAIN to main, HomeZone.DOCK to dock, HomeZone.WIDGET_AREA to widgetArea)
    }
