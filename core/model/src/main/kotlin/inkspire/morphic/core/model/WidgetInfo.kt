package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * Metadata for one bound app widget: the host [appWidgetId], its provider component
 * ([providerPackage] / [providerClass]), and a display [label].
 */
@Serializable
data class WidgetInfo(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val label: String,
)