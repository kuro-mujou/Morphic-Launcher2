package inkspire.morphic.data.settings

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.IconSizing
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

    /**
     * The icon sizing to draw [slot]'s cells with on [device] — **already resolved**: the grid's blueprint default
     * with any user override applied on top.
     *
     * **Consumers never see the keying, and that is the design.** A caller asks for the sizing of the grid it is
     * drawing and gets a value; the slot × device map, the sparse overrides and the merge all stay inside this
     * module. That is what makes the combinatorial fan-out a storage detail rather than something every surface has
     * to understand — L1 pushed it outward instead, and ended up with ~186 machine-generated preference keys and
     * call sites that clamped values ad hoc because nothing had resolved them.
     */
    fun iconSizing(slot: GridSlot, device: DeviceConfiguration): Flow<IconSizing>

    /**
     * Overrides one or more icon fields for [slot] on [device]. Setting a field to null in [transform] clears it,
     * after which that field follows the blueprint again — which is how a per-control "reset" works without a
     * separate operation.
     *
     * Scoped to one device configuration on purpose: the user is configuring the posture they are holding. Nothing
     * writes the other three.
     */
    suspend fun updateIcon(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: IconOverride.() -> IconOverride,
    )

    /**
     * The dimensions to lay [slot] out with on [device] — **already resolved** from its blueprint and any override.
     *
     * Only meaningful for a grid with a fixed row count (`GridSizing.FIXED_PAGER`); a scrolling grid derives its rows
     * from content and has no `GridConfig` to give, which is why [gridCols] exists beside this rather than one method
     * returning something nullable. The split mirrors `toGridConfig` / `colsFor` in `core:model`, for the same reason.
     */
    fun gridConfig(slot: GridSlot, device: DeviceConfiguration): Flow<GridConfig>

    /** The **visual** column count for [slot] on [device] — the whole of a scrolling grid's size. */
    fun gridCols(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Overrides [slot]'s dimensions for [device]. Null in the transform clears an axis, which then follows the
     * blueprint again.
     *
     * **Clamped to the blueprint's `editRange` on write**, so storage can never hold a grid the editor would refuse —
     * L1 instead let call sites `coerceAtMost` ad hoc, in at least two places that could disagree. Only the *minima*
     * are enforced: how many rows or columns actually *fit* depends on screen area and icon size, which is a runtime
     * question `core:model` deliberately does not answer.
     *
     * A grid with no `editRange` is not user-editable at all (a folder's, a list's) and writing to one is a coding
     * mistake rather than a no-op, so it throws.
     */
    suspend fun updateGrid(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: GridOverride.() -> GridOverride,
    )
}
