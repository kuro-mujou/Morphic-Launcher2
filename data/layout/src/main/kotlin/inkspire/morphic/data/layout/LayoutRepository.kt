package inkspire.morphic.data.layout

import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the **HOME coordinate layout** — which grid items are placed where, plus the definitions of
 * the containers/folders they may be. The one owner of the `*_placement` + folder/container Room stores; the UI
 * observes its flows and pushes [LayoutChange]s through [apply].
 *
 * **Refactor: L1's ~30-method god-interface → this.** L1 exploded reads across *type* (a separate
 * `placements` / `folderPlacements` / `widgetPlacements` / … per item kind) **and** *zone* (again for
 * `dock*` and `widgetArea*`), plus a matching wall of `putAll*` writers. Both explosions vanish because L2's
 * model already unified them:
 * - **Type** — one [placements] flow keyed by the sealed [GridItem] replaces the ~5 per-type placement flows.
 * - **Zone** — [PlacedItem] carries the [HomeZone], so a *single* flow spans MAIN/DOCK/WIDGET_AREA instead of a
 *   flow per zone. The consumer partitions by `zone` to fill each grid.
 * - **Writes** — every mutation goes through the one [apply] sink over the [LayoutChange] vocabulary, replacing
 *   L1's dozen `putAll*` / `place` / `remove` / `createFolder` / … methods.
 *
 * Only HOME is coordinate-placed (per the arrangement model); the APPS pager / category / list surfaces are
 * *order*-based and will get their own repository — this one is deliberately not a dumping ground for them.
 */
interface LayoutRepository {

    /**
     * Every item placed on HOME for [orientation], across all zones, keyed by the item. One subscription gives
     * the whole home arrangement; the caller groups by [PlacedItem.zone] to render the main area, dock, and
     * widget area. Re-emits on any change applied through [apply].
     */
    fun placements(orientation: Orientation): Flow<Map<GridItem, PlacedItem>>

    /** The folder definitions (label + contained apps), independent of where each folder is placed. */
    fun folders(): Flow<List<Folder>>

    /** The icon-container definitions (arrangement + contained [inkspire.morphic.core.model.IconItem]s). */
    fun iconContainers(): Flow<List<IconContainer>>

    /** The widget-container definitions (axis + contained widget ids). */
    fun widgetContainers(): Flow<List<WidgetContainer>>

    /** Metadata for the bound widgets referenced by placements / widget containers. */
    fun widgets(): Flow<List<WidgetInfo>>

    /**
     * Applies [changes] to [orientation]'s layout as one unit — the single write path. [orientation] scopes
     * *which* per-orientation tables are touched, so the same command list can be replayed into either
     * orientation. Ordering is honored: earlier changes are visible to later ones in the batch.
     */
    suspend fun apply(orientation: Orientation, changes: List<LayoutChange>)
}
