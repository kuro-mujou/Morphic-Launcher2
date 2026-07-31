package inkspire.morphic.data.settings

import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.SurfaceTransition
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the launcher's **user preferences**.
 *
 * **One flow per slice, not one flow for everything.** L1 exposed a single `Flow<LauncherSettings>` over ~102 fields,
 * so every consumer woke for every unrelated change and a full ~265-key decode ran on each emission. Here a consumer
 * subscribes to the slice it actually reads, which is also what keeps a slice's shape free to change without touching
 * anyone else. New slices are new properties, not new fields on one object.
 *
 * **What this repository is not for.** Anything with a different lifetime than a preference stays out, which is the
 * distinction L1's god object lost:
 * - **Arrangement** — which app sits where — is `data:layout`'s (Room). L1 kept `drawerOrder`, `drawerPages`,
 *   `categories` and `categoryAssignments` in the settings blob, hand-encoded into strings with control-char
 *   separators, and paid for it in every read.
 * - **Derived or cached state** — a dominant colour, a "dirty" marker, the id of the wallpaper currently applied to
 *   the system. Recompute or cache it; do not persist it as if the user chose it.
 * - **Wallpaper bitmaps and files** — `data:wallpaper`'s (B7b). It depends on this repository to persist its
 *   *pointers*, which is not the same as living here.
 *
 * Writes are `suspend` and each is atomic over its own slice: the implementation reads, transforms and writes inside
 * one DataStore transaction, so two concurrent edits cannot lose one another.
 */
interface SettingsRepository {

    /**
     * HOME's layout, its per-edge bindings, and the crossing transition. Emits [SurfaceRegister.Default] when nothing
     * has been stored yet, so a consumer never has to handle "no settings".
     */
    val surfaceRegister: Flow<SurfaceRegister>

    /** Sets HOME's main-area + side-zone pairing. */
    suspend fun setHomeLayout(layout: HomeLayout)

    /**
     * Binds [binding] to [edge], or **unbinds** the edge when it is null — after which that edge is not swipeable.
     *
     * One method taking the edge, rather than L1's four (`setSideTop`/`setSideRight`/`setSideBottom`/`setSideLeft`,
     * with four matching writers in its codec). The edge is data; four copies of one method is not an API, it is the
     * same method four times.
     */
    suspend fun setSide(edge: HomeEdge, binding: SideBinding?)

    /** Sets how HOME and a side surface animate past each other. */
    suspend fun setSurfaceTransition(transition: SurfaceTransition)
}
