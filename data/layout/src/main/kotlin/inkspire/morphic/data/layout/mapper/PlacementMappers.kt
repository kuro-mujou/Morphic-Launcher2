package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.AppPlacementEntity
import inkspire.morphic.core.database.entity.FolderPlacementEntity
import inkspire.morphic.core.database.entity.IconContainerPlacementEntity
import inkspire.morphic.core.database.entity.WidgetContainerPlacementEntity
import inkspire.morphic.core.database.entity.WidgetPlacementEntity
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.layout.PlacedItem

/**
 * Entity ⇄ domain mapping for the five `*_placement` tables. Each stores the same shape — an id + `orientation`
 * + `zone` + an `@Embedded` [GridPlacement] — differing only in which id column identifies the [GridItem]. So
 * the read side factors through [entry] (the identical `(placement, zone)` → [PlacedItem] step), and the write
 * side is five thin constructors, one per placement table. This is the "find the shared pattern, don't copy it
 * four times" cleanup L1's parallel `toAppPosition()` mappers never did.
 */

/** Folds the shared `(placement, zone)` half; each `toEntry` only supplies the item id. */
private fun entry(item: GridItem, placement: GridPlacement, zone: HomeZone): Pair<GridItem, PlacedItem> =
    item to PlacedItem(placement, zone)

internal fun AppPlacementEntity.toEntry() = entry(GridItem.App(component), placement, zone)
internal fun FolderPlacementEntity.toEntry() = entry(GridItem.Folder(folderId), placement, zone)
internal fun WidgetPlacementEntity.toEntry() = entry(GridItem.Widget(appWidgetId), placement, zone)
internal fun IconContainerPlacementEntity.toEntry() = entry(GridItem.IconContainer(containerId), placement, zone)
internal fun WidgetContainerPlacementEntity.toEntry() = entry(GridItem.WidgetContainer(containerId), placement, zone)

internal fun GridItem.App.toEntity(orientation: Orientation, zone: HomeZone, placement: GridPlacement) =
    AppPlacementEntity(component, orientation, zone, placement)

internal fun GridItem.Folder.toEntity(orientation: Orientation, zone: HomeZone, placement: GridPlacement) =
    FolderPlacementEntity(folderId, orientation, zone, placement)

internal fun GridItem.Widget.toEntity(orientation: Orientation, zone: HomeZone, placement: GridPlacement) =
    WidgetPlacementEntity(appWidgetId, orientation, zone, placement)

internal fun GridItem.IconContainer.toEntity(orientation: Orientation, zone: HomeZone, placement: GridPlacement) =
    IconContainerPlacementEntity(containerId, orientation, zone, placement)

internal fun GridItem.WidgetContainer.toEntity(orientation: Orientation, zone: HomeZone, placement: GridPlacement) =
    WidgetContainerPlacementEntity(containerId, orientation, zone, placement)
