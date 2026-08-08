package inkspire.morphic.data.widgets

import android.content.ComponentName
import android.graphics.Bitmap

/**
 * One installed app widget, as the picker needs to show it.
 *
 * **A read of the platform, not a stored thing.** Nothing here is persisted: what a launcher keeps once a widget is
 * placed is `WidgetInfo` in `core:model` — an allocated `appWidgetId` and the provider that answers for it — where
 * this describes a widget that *could* be added and has no id yet. Keeping them apart is what stops a picker row
 * looking like a placed widget with a missing field.
 *
 * @property component the provider's own component, which is what a later slice binds an allocated id to. Carried
 *   now rather than added later because it is the entry's identity — two widgets from one app differ by it and by
 *   nothing else that is guaranteed unique.
 * @property preview the artwork the app publishes for this widget, already rasterised at the device density, or
 *   null when it publishes neither a preview nor an icon. A [Bitmap] in a data model for `AppShortcut`'s reason:
 *   it is someone else's artwork, read fresh for a sheet that is about to be shown and thrown away when it closes.
 * @property minWidthPx the smallest size the provider says it can be drawn at, in pixels — what the picker turns
 *   into a "4 × 2" label against whichever grid the widget would land on, and what the placement slice will size
 *   its footprint from. Left in the platform's own units because only the grid knows what a cell is.
 */
data class WidgetProvider(
    val component: ComponentName,
    val label: String,
    val preview: Bitmap?,
    val minWidthPx: Int,
    val minHeightPx: Int,
)

/**
 * The widgets one app publishes, under that app's name.
 *
 * **Grouped here rather than in the picker**, which is where L1 did it (`buildPickerApps`). The grouping needs the
 * *application's* label, which is a `PackageManager` read — a second platform lookup per row — and this module is
 * already the boundary that owns those. Doing it in the UI is how a composable ends up holding a `PackageManager`.
 *
 * It is also a fact about the catalogue rather than a choice about presentation: a widget belongs to the app that
 * ships it whether or not anything draws it in sections.
 */
data class WidgetProviderGroup(
    val packageName: String,
    val appLabel: String,
    val providers: List<WidgetProvider>,
)
