package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * Anything that can occupy a position on a home grid (main area or dock). These five are the "same level"
 * peers: apps and widgets are referenced directly; folders and containers by their id (defined in [Folder] /
 * [IconContainer] / [WidgetContainer]). Pair a [GridItem] with a [GridPlacement] to record where it sits.
 */
@Serializable
sealed interface GridItem {
    @Serializable data class App(val component: ComponentKey) : GridItem
    @Serializable data class Widget(val appWidgetId: Int) : GridItem
    @Serializable data class Folder(val folderId: Long) : GridItem
    @Serializable data class IconContainer(val containerId: Long) : GridItem
    @Serializable data class WidgetContainer(val containerId: Long) : GridItem
}

/**
 * An app or a folder shown as a single tappable icon — the shared alphabet for the two holders of exactly
 * {app, folder}: the [Surface.APPS] pager and an [IconContainer]. Named for its role (a single-icon entry),
 * not its holder, since both use it.
 */
@Serializable
sealed interface IconItem {
    @Serializable data class App(val component: ComponentKey) : IconItem
    @Serializable data class Folder(val folderId: Long) : IconItem
}

// ── Containers (grid items that own an inner layout + inner items) ─────

/**
 * A named folder of apps. Contents are apps only — folders never nest. Referenced elsewhere by [id]
 * (see [GridItem.Folder] / [IconItem.Folder]); opening it shows [apps].
 */
@Serializable
data class Folder(val id: Long, val label: String, val apps: List<ComponentKey>)

/** How the icons inside an [IconContainer] are arranged. */
@Serializable
enum class IconArrangement {
    GRID, CIRCLE,
    FAN_TOP_LEFT, FAN_TOP_RIGHT, FAN_BOTTOM_LEFT, FAN_BOTTOM_RIGHT,
    BEEHIVE,
}

/**
 * A grid item that groups app/folder icons into one cell, laid out by [arrangement] (grid, circle, fan,
 * beehive). Holds [IconItem]s — apps or folders, never widgets or other containers.
 */
@Serializable
data class IconContainer(val id: Long, val arrangement: IconArrangement, val items: List<IconItem>)

/**
 * Which way a [WidgetContainer] is paged — **the direction the finger swipes** to reach the next widget, not a
 * direction the widgets stack in.
 */
@Serializable
enum class WidgetContainerAxis { HORIZONTAL, VERTICAL }

/**
 * A grid item that groups widgets into one cell, **one shown at a time**, swiped between along [axis]. Holds bound
 * widget ids only; per-widget metadata is in [WidgetInfo].
 *
 * **Paged, not stacked.** Dividing one cell's footprint between the contained widgets would shrink each of them,
 * which is the opposite of why a user groups widgets: each still wants the whole footprint, and what the container
 * buys back is the *cells*, by showing one widget at a time.
 *
 * @property autoRotate whether it pages itself on a timer, so a container of glanceable widgets cycles without being
 *   touched. Off by default: a launcher that animates on its own while nobody is looking at it is a choice, not a
 *   baseline.
 * @property resetOnReturn whether returning to home puts it back on its **first** page. Off by default, on the same
 *   terms — leaving a container where the user left it is the less surprising of the two, and this exists for the
 *   opposite taste: a container whose first page is the one that matters, with the rest kept behind it.
 */
@Serializable
data class WidgetContainer(
    val id: Long,
    val axis: WidgetContainerAxis,
    val widgetIds: List<Int>,
    val autoRotate: Boolean = false,
    val resetOnReturn: Boolean = false,
)
