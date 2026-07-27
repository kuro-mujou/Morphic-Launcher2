package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.AppPlacementEntity
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.layout.PlacedItem

/**
 * Entity ⇄ domain mapping for app placements — the app slice of the layout stores.
 *
 * (L1 repeated a near-identical `toAppPosition()` on every `*PlacementEntity`. When the folder / widget /
 * container placement tables are wired in Part 4, factor out the shared shape rather than copying this four
 * times — the `@Embedded GridPlacement` + `zone` columns are identical across them, only the id column differs.)
 */

/** An [AppPlacementEntity] as the `(item, placed)` entry the repository exposes. */
internal fun AppPlacementEntity.toEntry(): Pair<GridItem, PlacedItem> =
    GridItem.App(component) to PlacedItem(placement, zone)

/** A placed [GridItem.App] as its row for [orientation]. */
internal fun GridItem.App.toEntity(
    orientation: Orientation,
    zone: HomeZone,
    placement: GridPlacement,
): AppPlacementEntity = AppPlacementEntity(
    component = component,
    orientation = orientation,
    zone = zone,
    placement = placement,
)
