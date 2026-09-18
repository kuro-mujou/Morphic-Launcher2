package inkspire.morphic.feature.settings.widgetstudio

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The widget studio's destination: which placed widget to restyle, and the size HOME draws it at.
 *
 * **The size travels with the route** because a widget re-lays rather than scales — a preview at any other size would
 * show a different layout from the one on the grid — and only the surface it was long-pressed on measured it.
 *
 * @property widthDp the widget as drawn on HOME, which the preview reproduces.
 */
@Serializable
@SerialName("widget_studio")
data class WidgetStudioRoute(val widgetId: Long, val widthDp: Float, val heightDp: Float) : NavKey
