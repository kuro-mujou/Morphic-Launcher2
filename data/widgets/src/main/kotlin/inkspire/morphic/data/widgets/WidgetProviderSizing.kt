package inkspire.morphic.data.widgets

import android.appwidget.AppWidgetProviderInfo
import android.os.Build

/**
 * The default size a provider declares **in cells**, or 0 when it declares none.
 *
 * `targetCellWidth`/`targetCellHeight` arrived in Android 12 (`S`), and the fields do not exist on the framework
 * class before it — reading them on an older device throws, not returns 0 — so the version gate is required rather
 * than defensive. Zero is the honest "unspecified", which is what `WidgetSpan.forWidget` falls back from.
 *
 * The two consumers are the catalog (a widget that *could* be added) and a bound widget (one that exists); both
 * read the same field the same way, which is why this is one function rather than the number copied into each.
 */
internal fun AppWidgetProviderInfo.targetCols(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetCellWidth else 0

/** The declared height in cells; see [targetCols]. */
internal fun AppWidgetProviderInfo.targetRows(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetCellHeight else 0
