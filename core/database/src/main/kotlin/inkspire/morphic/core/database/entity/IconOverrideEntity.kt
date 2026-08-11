package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/**
 * One app's own icon recipe — the whole `IconLayerSet`, serialized, keyed by [component].
 *
 * **A row means "this app has been detached from the global default"**, and its absence means the app inherits.
 * That is the full-snapshot detach model: opening an app in the icon studio snapshots the current global default
 * into this row, after which the app renders from its own set and ignores later global changes; Reset deletes the
 * row and re-attaches it. So there is no "inherit this field" state to represent, and nothing here is nullable.
 *
 * ## Why one blob and not a column per property
 *
 * This shape is the single most expensive lesson in L1's icon work, so it is worth stating rather than discovering
 * again. That launcher tried, in order: a flat nullable column per property merged field-by-field at render time;
 * then eight transform columns; then those plus a separate JSON list of user-inserted layers; and finally one blob.
 * Each attempt was a **destructive schema bump** (its DB went v20 → v24 on this table alone), and the reason none of
 * the column shapes could work is structural rather than a matter of getting the columns right:
 *
 * - **A layer set is variable-length and ordered.** Two sets of different length cannot be merged field-by-field,
 *   and a layer's meaning comes from its *position*, which no per-property column can express.
 * - **Effects are open-ended.** A column per effect makes every new effect a migration; a serialized set makes it
 *   an additive variant of a sealed type and no schema change at all.
 *
 * The bill this shape accepts in exchange is that the set is **opaque to SQL** — no query can ask "which apps use a
 * circle foreground?". Nothing wants to: overrides are read as a whole map and written one app at a time.
 *
 * @property layerSet the app's `IconLayerSet` as JSON. Encoding lives in `data:icons`, not here, so `core:database`
 *   stays free of the icon model — the same reason every other entity stores what it is handed. A blob that cannot
 *   be decoded is dropped on read rather than failing the query; see the repository.
 */
@Entity(tableName = "icon_override")
data class IconOverrideEntity(
    @PrimaryKey val component: ComponentKey,
    val layerSet: String,
)
