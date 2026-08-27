package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anything that can occupy a position on a home grid (main area or dock). These five are the "same level"
 * peers: apps and widgets are referenced directly; folders and containers by their id (defined in [Folder] /
 * [IconContainer] / [WidgetContainer]). Pair a [GridItem] with a [GridPlacement] to record where it sits.
 *
 * **Short [SerialName]s, because this now reaches a user's stored blob** — a per-item gesture assignment is keyed by
 * one of these. Without them the discriminator is the fully-qualified class name, so moving or renaming a subtype
 * would quietly orphan every assignment referring to it. `SearchPlacement` carries them for the same reason.
 */
@Serializable
sealed interface GridItem {
    @Serializable
    @SerialName("app")
    data class App(val component: ComponentKey) : GridItem
    @Serializable
    @SerialName("widget")
    data class Widget(val appWidgetId: Int) : GridItem
    @Serializable
    @SerialName("folder")
    data class Folder(val folderId: Long) : GridItem
    @Serializable
    @SerialName("icon_container")
    data class IconContainer(val containerId: Long) : GridItem
    @Serializable
    @SerialName("widget_container")
    data class WidgetContainer(val containerId: Long) : GridItem
}

/**
 * An app or a folder shown as a single tappable icon — the shared alphabet for the two holders of exactly
 * {app, folder}: the [Surface.APPS] pager and an [IconContainer]. Named for its role (a single-icon entry),
 * not its holder, since both use it.
 */
@Serializable
sealed interface IconItem {
    @Serializable
    data class App(val component: ComponentKey) : IconItem
    @Serializable
    data class Folder(val folderId: Long) : IconItem
}

// ── Containers (grid items that own an inner layout + inner items) ─────

/**
 * A named folder of apps. Contents are apps only — folders never nest. Referenced elsewhere by [id]
 * (see [GridItem.Folder] / [IconItem.Folder]); opening it shows [apps].
 */
@Serializable
data class Folder(val id: Long, val label: String, val apps: List<ComponentKey>)

/**
 * How the icons inside an [IconContainer] are arranged — **a shape, and the parameters that shape has**.
 *
 * Sealed rather than one flat value per combination, because those are two different questions and only the first
 * is one a user can answer cold: a grid, a ring, a honeycomb or a fan is picked from a picture, while *which
 * corner* a fan opens from is adjusted afterwards on a container that exists. Flattened, the second question
 * multiplies the first — four corners are four values, eight anchors would be eight — so anything offering "the
 * shapes" has to filter the vocabulary back down to what it meant, and a shape whose parameter is not an enum
 * value at all (a column count) cannot be expressed here however it is named.
 *
 * A shape's parameters travel with the shape, which is what makes a corner on a circle unrepresentable rather than
 * a field that is merely ignored there — [HomeLayout]'s reason for being one type.
 *
 * **Stored as a serialized blob**, so a shape can grow a parameter without a column. The [SerialName]s are short
 * and explicit for [GridItem]'s reason: this reaches a user's stored data, so the discriminator must not be a class
 * name that a refactor could move.
 */
@Serializable
sealed interface IconArrangement {
    /** Rows and columns of square cells, filled in reading order and wrapped where [fill] says to. */
    @Serializable
    @SerialName("grid")
    data class Grid(val fill: GridFill = GridFill.Auto) : IconArrangement

    /** A single ring, the icons spaced evenly around it. */
    @Serializable
    @SerialName("circle")
    data object Circle : IconArrangement

    /** A honeycomb — one icon in the middle, then complete hexagonal rings outward. */
    @Serializable
    @SerialName("beehive")
    data object Beehive : IconArrangement

    /** Concentric arcs sweeping out of [anchor]. */
    @Serializable
    @SerialName("fan")
    data class Fan(val anchor: FanAnchor = FanAnchor.TOP_LEFT) : IconArrangement
}

/**
 * Where an [IconArrangement.Grid] wraps — **one axis pinned, or neither**.
 *
 * The icons are laid out in reading order whichever this is; all that changes is where the wrapping count comes
 * from, and therefore **which way the block grows** as icons are added: pin the columns and it extends downward,
 * pin the rows and it extends to the right (`Rows(1)` is a dock). Reading order is not negotiable — a container is
 * reordered by dragging onto a *position*, and an order the eye cannot follow is one the finger cannot aim at.
 *
 * **A one-of rather than a `rows` and a `columns` together**, because there is no R × C frame here and no capacity
 * to fill: the container's own bounds are the list's bounds and the icons scale until everything fits. A pair would
 * invite exactly the fixed frame this is not, and leave "both pinned" to mean something nobody can define.
 *
 * [Auto] is the default, so a container nobody has configured is laid out as it always was.
 */
@Serializable
sealed interface GridFill {
    /** The column count follows the container's proportions, so the block stays roughly the box's shape. */
    @Serializable
    @SerialName("auto")
    data object Auto : GridFill

    /** Exactly [count] columns; rows appear underneath as the list grows. */
    @Serializable
    @SerialName("columns")
    data class Columns(val count: Int) : GridFill

    /** Exactly [count] rows; columns appear to the right as the list grows. */
    @Serializable
    @SerialName("rows")
    data class Rows(val count: Int) : GridFill
}

/**
 * The point an [IconArrangement.Fan] pivots on — where its arcs sweep *out of*, rather than where its first icon
 * sits. The four corners of the container and the four edge midpoints.
 *
 * **The kind of anchor is also the size of the sweep**: a corner has a quarter circle in front of it and an edge
 * has a half, so the half-circle fan is reached by choosing where it pivots and nothing else. There is deliberately
 * no second setting for the angle — "a corner with a 180 degree sweep" is not a shape this makes, and a pair of
 * settings would be a pair to keep in step.
 *
 * **There is no center**, though it would complete the table, because that is the circle — and not the same circle:
 * [IconArrangement.Circle] is a *single* ring solving its radius from the chord so neighbours sit one pitch apart,
 * where a fan is *nested* arcs at a fixed radial pitch. They agree at small counts and diverge completely at large
 * ones, so folding them together would mean one of the two laws quietly losing.
 */
@Serializable
enum class FanAnchor {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT,
}

/**
 * A grid item that groups app/folder icons into one cell, laid out by [arrangement] (grid, circle, fan,
 * beehive). Holds [IconItem]s — apps or folders, never widgets or other containers.
 *
 * @property iconScalePercent how big its icons are as a percentage of what the surface's own sizing would give
 *   them; 100 is that size exactly. A **multiplier over the resolved size**, not a size, so a container answers to
 *   the same settings as everything around it and then departs from them by a stated amount. Bounded by the slot
 *   the arrangement gave the icon, since past that neighbours overlap.
 * @property spacingScalePercent the same for the gap between icons, against the container's own base gap.
 *   Lowering it buys room the icons can then grow into, which is why the two sliders are worth having together.
 */
@Serializable
data class IconContainer(
    val id: Long,
    val arrangement: IconArrangement,
    val items: List<IconItem>,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
)

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
