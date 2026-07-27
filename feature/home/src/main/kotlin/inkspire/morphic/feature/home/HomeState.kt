package inkspire.morphic.feature.home

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.GridPlacement

/** An app placed on the home grid: its [info] (label + identity for the icon) and where it sits ([placement]). */
data class PlacedApp(val info: AppInfo, val placement: GridPlacement)

/**
 * The home surface's render state for the current orientation — just the placed apps for now. Folders, widgets,
 * and containers join this once their cells exist; the repository already streams them.
 */
data class HomeState(val apps: List<PlacedApp>)
