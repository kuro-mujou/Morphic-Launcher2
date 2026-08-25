package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.IconContainerEntity
import inkspire.morphic.core.database.entity.IconContainerItemEntity
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.IconItem

/**
 * Assembles [IconContainer]s from the container + membership rows (items in `sortOrder`). Each membership row
 * stores an [IconItem] as exactly one of `component` (an app) or `folderId` (a nested folder).
 */
internal fun iconContainersOf(
    containers: List<IconContainerEntity>,
    items: List<IconContainerItemEntity>,
): List<IconContainer> {
    val itemsByContainer = items.sortedBy { it.sortOrder }.groupBy { it.containerId }
    return containers.map { container ->
        IconContainer(
            id = container.id,
            arrangement = container.arrangement,
            items = itemsByContainer[container.id].orEmpty().map { it.toIconItem() },
            iconScalePercent = container.iconScalePercent,
            spacingScalePercent = container.spacingScalePercent,
        )
    }
}

/** The stored row as its [IconItem] — the non-null one of `component` / `folderId`. */
internal fun IconContainerItemEntity.toIconItem(): IconItem =
    component?.let { IconItem.App(it) } ?: IconItem.Folder(folderId!!)

/** An [IconItem] as a membership row of [containerId] at [sortOrder]. */
internal fun IconItem.toRow(containerId: Long, sortOrder: Int): IconContainerItemEntity = when (this) {
    is IconItem.App -> IconContainerItemEntity(containerId = containerId, component = component, sortOrder = sortOrder)
    is IconItem.Folder -> IconContainerItemEntity(containerId = containerId, folderId = folderId, sortOrder = sortOrder)
}
