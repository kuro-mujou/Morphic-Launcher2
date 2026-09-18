package inkspire.morphic.core.model.widget

import kotlinx.serialization.Serializable

/**
 * A widget's footprint in **visual** cells — the cells a user sees, each one app icon's slot. A grid that subdivides
 * its cells for finer placement multiplies these out when it places the widget, so a 4 × 2 design is 4 × 2 on every
 * grid.
 */
@Serializable
data class WidgetSpan(val cols: Int = 4, val rows: Int = 2)
