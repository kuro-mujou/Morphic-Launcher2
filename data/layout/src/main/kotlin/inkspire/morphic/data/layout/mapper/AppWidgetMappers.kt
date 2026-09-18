package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.AppWidgetEntity
import inkspire.morphic.core.database.entity.WidgetContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerItemEntity
import inkspire.morphic.core.model.AppWidgetInfo
import inkspire.morphic.core.model.WidgetContainer

/** Assembles [WidgetContainer]s from the container + membership rows (widget ids in `sortOrder`). */
internal fun widgetContainersOf(
    containers: List<WidgetContainerEntity>,
    items: List<WidgetContainerItemEntity>,
): List<WidgetContainer> {
    val idsByContainer = items.sortedBy { it.sortOrder }.groupBy { it.containerId }
    return containers.map { container ->
        WidgetContainer(
            id = container.id,
            axis = container.axis,
            widgetIds = idsByContainer[container.id].orEmpty().map { it.appWidgetId },
            autoRotate = container.autoRotate,
            resetOnReturn = container.resetOnReturn,
        )
    }
}

/** A bound-widget row as its [AppWidgetInfo] metadata. */
internal fun AppWidgetEntity.toAppWidgetInfo(): AppWidgetInfo =
    AppWidgetInfo(appWidgetId = appWidgetId, providerPackage = providerPackage, providerClass = providerClass, label = label)

/** The row for a newly bound widget — the definition half of `LayoutChange.PlaceAppWidget`. */
internal fun AppWidgetInfo.toEntity(): AppWidgetEntity =
    AppWidgetEntity(
        appWidgetId = appWidgetId,
        providerPackage = providerPackage,
        providerClass = providerClass,
        label = label,
    )
