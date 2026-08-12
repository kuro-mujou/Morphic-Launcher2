package inkspire.morphic.data.icons

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import kotlinx.coroutines.flow.Flow

/**
 * The apps whose icons have their **own** recipe, and the reads and writes that detach and re-attach them.
 *
 * The model is **full-snapshot detach**, not a field-level merge: an app either inherits the global default
 * (`SettingsRepository.iconLayerSet`) or owns a complete [IconLayerSet] of its own. Editing an app snapshots the
 * current default into its row, after which later global changes pass it by; [clear] deletes the row and the app
 * follows the default again.
 *
 * **The alternative — a sparse per-property override merged at render time — cannot be made to work here**, and it
 * is worth knowing why rather than rediscovering it. A layer set is a variable-length, *ordered* list: there is no
 * stable key to merge two of them by, and "the third layer" means nothing across sets of different lengths. L1 spent
 * three model revisions and four destructive schema bumps arriving at this same answer. The tradeoff it accepts is
 * real and is the one every launcher makes: an individually-edited app does **not** inherit later global changes.
 *
 * **Why this is not a `data:settings` slice**, when the global default is one: there is a row per customized app, so
 * this is a keyed store that grows with use, and a preference blob is neither. It is the same line `drawerOrder` and
 * `categories` fell on the wrong side of in L1.
 */
interface IconOverrideRepository {

    /**
     * Every detached app, with the recipe it renders from. Emits an empty map when nothing has been customized.
     *
     * A map rather than a per-app query because that is how it is consumed: one composition-local for the whole
     * launcher, read by every icon on screen. A row whose stored recipe cannot be decoded is **omitted**, so the app
     * falls back to the global default rather than the surface failing to draw — see the implementation.
     */
    val overrides: Flow<Map<ComponentKey, IconLayerSet>>

    /** Gives [component] its own [layerSet], detaching it from the global default (or replacing what it had). */
    suspend fun set(component: ComponentKey, layerSet: IconLayerSet)

    /** Drops [component]'s own recipe, re-attaching it to the global default. A no-op if it had none. */
    suspend fun clear(component: ComponentKey)
}
