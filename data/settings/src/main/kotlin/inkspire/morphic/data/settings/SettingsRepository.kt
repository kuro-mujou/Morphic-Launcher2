package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.PreviewBackground
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the launcher's **user preferences**.
 *
 * **One flow per slice, not one flow for everything.** A consumer subscribes to the slice it actually reads, so an
 * unrelated change wakes nobody and a slice's shape stays free to move. New slices are new properties, never new
 * fields on one object.
 *
 * **What this repository is not for** — anything whose lifetime differs from a preference:
 * - **Arrangement** (which app sits where) is `data:layout`'s, in Room.
 * - **Derived or cached state** — a dominant color, a dirty marker, the id of the wallpaper currently applied.
 *   Recompute or cache it; do not persist it as though the user chose it.
 * - **Wallpaper bitmaps and files** are `data:wallpaper`'s. It stores its *pointers* here, which is not the same as
 *   living here.
 *
 * Writes are `suspend` and each is atomic over its own slice: the implementation reads, transforms and writes inside
 * one DataStore transaction, so two concurrent edits cannot lose one another.
 */
interface SettingsRepository {

    /**
     * HOME's layout, its per-edge bindings, and the crossing transition.
     *
     * Emits [SurfaceRegister.Default] when nothing is stored, so no consumer handles "no settings".
     */
    val surfaceRegister: Flow<SurfaceRegister>

    /** How frosted surfaces render over the wallpaper — the one global choice, and the strengths tuning it. */
    val backdropEffect: Flow<BackdropEffect>

    /**
     * The **global default icon appearance**: what every app's icon renders from until that app is edited.
     *
     * Emits [IconAppearance.Base] when nothing is stored, so "no settings yet" and "the user reset everything" are
     * one state that nobody special-cases.
     *
     * **Per-app overrides are deliberately not here**: there is one per customized app, which makes them rows in
     * `data:icons` rather than a preference. The two meet at composition, where an icon resolves its override or
     * falls back to this.
     */
    val iconAppearance: Flow<IconAppearance>

    /**
     * The APPS surface's chrome — the search field's placement, and which edge the category tabs sit on.
     *
     * Neither search nor the tab bar is built in `feature:apps` yet, so this setting's only current consumer is the
     * settings preview. A deliberate exception to "no model in a vacuum": a preview is a real consumer with a real
     * question, and the alternative is drawing it from invented constants.
     */
    val appsChrome: Flow<AppsChrome>

    /** Sets where the APPS search field sits on [layout]; the other arrangements keep theirs. */
    suspend fun setSearchPlacement(layout: AppsLayout, placement: SearchPlacement)

    /** Sets which edge the category pager's tab bar sits on. */
    suspend fun setTabBarEdge(edge: VerticalEdge)

    /**
     * The user's saved icon recipes. Empty until one is saved; there are no built-ins.
     *
     * A whole list rather than a lookup by name, because that is how it is used: the library is *shown*, and a preset
     * is chosen from what is on screen rather than fetched by a name someone typed.
     */
    val iconPresets: Flow<List<IconPreset>>

    /** Saves [appearance] under [name], replacing any preset already called that. */
    suspend fun saveIconPreset(name: String, appearance: IconAppearance)

    /** Removes the preset called [name]. A no-op if there is none. */
    suspend fun deleteIconPreset(name: String)

    /**
     * Renames the preset called [from] to [to], keeping its place in the library.
     *
     * Its own operation rather than a delete and a save, because the name *is* the identity: spelled that way, the
     * preset would come back at the end of the list.
     */
    suspend fun renameIconPreset(from: String, to: String)

    /**
     * Applies [transform] to the stored effect **inside the write**, never replacing it with a value the caller
     * computed earlier.
     *
     * **A value would be a lost update, and was one.** A screen builds its next value with `copy` off the effect it
     * last *saw* — and seeing it means a flow emission, so a second edit issued before the first has come back round
     * carries the first's fields with it. It showed as a tint swatch being overwritten by the stepper pressed after
     * it. Steppers reach it easily because they are fast; a slider drag lasts long enough for the store to catch up.
     *
     * **There is deliberately no whole-value setter beside it**, because a whole-value setter is the shape that
     * causes this. An effect chosen outright is `updateBackdropEffect { chosen }`.
     *
     * **No field-level setter either, and the sealed type is why**: which parameters apply depends on which variant
     * is selected, so no field can move independently of it. The consequence worth knowing is that **switching
     * variants discards the previous one's parameters** — they are stored nowhere else. Within a variant nothing is
     * lost, which is why the wash became a parameter rather than staying four variants.
     */
    suspend fun updateBackdropEffect(transform: (BackdropEffect) -> BackdropEffect)

    /**
     * Replaces the global default icon appearance outright.
     *
     * A whole-value write because a layer set is an **ordered list**: there is no sparse record to patch and no
     * stable key to patch it by, since inserting a layer moves every index below it. It is also what makes undo
     * cheap — an appearance is an immutable value, so history is a list of them and a step is an index.
     */
    suspend fun setIconAppearance(appearance: IconAppearance)

    /**
     * What the **icon studio's canvas** is drawn on, so the studio reopens on the backdrop it was left on.
     *
     * A workspace preference: it shapes no surface and reaches no rendered icon — the paper, not the drawing. Which
     * is exactly why it is not part of [iconAppearance], where it would make an icon's identity depend on what
     * someone happened to be looking at while making it.
     *
     * Emits [PreviewBackground.Default] until one is chosen.
     */
    val iconStudioBackground: Flow<PreviewBackground>

    /** Remembers [background] as the icon studio's canvas. */
    suspend fun setIconStudioBackground(background: PreviewBackground)

    /**
     * How the icon studio's workspace is arranged — the preview's pan and zoom, and where the layer rail was put.
     *
     * [iconStudioBackground]'s sibling and its exact argument: the paper, not the drawing. See [IconStudioWorkspace]
     * for why every value is a fraction of the canvas rather than a dp.
     *
     * Emits [IconStudioWorkspace.Default] until something is arranged, and **never emits a value carrying a
     * non-finite float** — [IconStudioWorkspace.sanitized] is applied on the way out so no consumer has to remember.
     */
    val iconStudioWorkspace: Flow<IconStudioWorkspace>

    /** Remembers how the studio's workspace is arranged. */
    suspend fun setIconStudioWorkspace(workspace: IconStudioWorkspace)

    /** Sets HOME's main-area + side-zone pairing. */
    suspend fun setHomeLayout(layout: HomeLayout)

    /**
     * Binds [binding] to [edge], or **unbinds** the edge when it is null — after which that edge is not swipeable.
     *
     * One method taking the edge, because the edge is data. Four methods would be the same method four times.
     */
    suspend fun setSide(edge: HomeEdge, binding: SideBinding?)

    /** Sets how HOME and a side surface animate past each other. */
    suspend fun setSurfaceTransition(transition: SurfaceTransition)

    /**
     * The icon sizing to draw [slot]'s cells with on [device] — **already resolved**: the blueprint's default with
     * any user override applied over it.
     *
     * **Consumers never see the keying, and that is the design.** A caller asks for the sizing of the grid it is
     * drawing and gets a value; the slot × device map, the sparse overrides and the merge stay inside this module,
     * which is what keeps the combinatorial fan-out a storage detail rather than something every surface learns.
     */
    fun iconSizing(slot: GridSlot, device: DeviceConfiguration): Flow<IconSizing>

    /**
     * Overrides one or more icon fields for [slot] on [device]. Setting a field to null in [transform] clears it,
     * after which that field follows the blueprint again — which is how a per-control reset works with no separate
     * operation.
     *
     * Scoped to one device configuration on purpose: the user is configuring the posture they are holding, and
     * nothing writes the other three.
     */
    suspend fun updateIcon(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: IconOverride.() -> IconOverride,
    )

    /**
     * The **card chrome** to draw tile grid [slot] with on [device] — resolved from its blueprint and any override.
     *
     * The third kind of stored size, beside [extent] and [rowHeight], and the one that is not a size of the *grid*:
     * a title's scale, a corner radius and two paddings shape one card, where those two shape the area a grid is laid
     * out in. A slot whose blueprint declares no `card` throws — only a grid of tiles can be asked.
     */
    fun cardChrome(slot: GridSlot, device: DeviceConfiguration): Flow<CardChrome>

    /**
     * Overrides one or more card-chrome fields for [slot] on [device]; a null field in [transform] clears it back to
     * the blueprint — [updateIcon]'s contract, for its reason.
     */
    suspend fun updateCard(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: CardOverride.() -> CardOverride,
    )

    /**
     * The dimensions to lay [slot] out with on [device] — **already resolved** from its blueprint and any override.
     *
     * Only meaningful for a grid with a fixed row count (`GridSizing.FIXED_PAGER`); a scrolling grid derives its rows
     * from content and has no `GridConfig` to give, which is why [gridCols] exists beside this rather than one method
     * returning something nullable.
     */
    fun gridConfig(slot: GridSlot, device: DeviceConfiguration): Flow<GridConfig>

    /**
     * The **visual** column count for [slot] on [device] — the whole of a scrolling grid's size, and half the dock's,
     * whose rows come from [extent] instead.
     *
     * Not clamped to what actually fits: that needs a measured area, so a surface that cares clamps where it
     * measures. Reading a count the screen has outgrown beats storing the clamp, since the user's choice then
     * survives whatever shrank the fit.
     */
    fun gridCols(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Overrides [slot]'s dimensions for [device]. Null in the transform clears an axis, which then follows the
     * blueprint again.
     *
     * **Clamped to the blueprint's `editRange` on write**, so storage can never hold a grid the editor would refuse.
     * Only the *minima* are enforced: how many rows or columns actually fit depends on screen area and icon size,
     * which is a runtime question `core:model` deliberately does not answer.
     *
     * A grid with no `editRange` is not user-editable at all (a folder's, a list's), so writing to one throws rather
     * than silently doing nothing.
     *
     * **A grid can be editable on one axis only, and the clamp enforces it**: a null `minRows` drops a row override
     * rather than storing one. Two grids rely on that for different reasons — a scrolling grid's rows come from its
     * content, and a side zone's come from [extent].
     */
    suspend fun updateGrid(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: GridOverride.() -> GridOverride,
    )

    /**
     * How thick the fixed-extent strip [slot] is on [device], **in dp** — its blueprint's extent with any override.
     *
     * **A height where the zone is a strip and a width where it is a rail** (`SideZoneEdge`), which is why it is an
     * extent: the stored number is the same thickness either way, and the posture decides which dimension it names
     * and which of the two counts it bounds.
     *
     * **Only HOME's two side zones can be asked** — the dock and the widget area. Every other grid is configured by
     * counts and takes the space it is given, so a slot whose blueprint declares no `extentDp` throws rather than
     * resolving to something invented.
     */
    fun extent(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s extent on [device] to [dp], or clears it back to the blueprint when null.
     *
     * **The caller owns the bounds**, unlike [updateGrid]. Both of an extent's bounds are runtime facts a store
     * cannot check: the floor is the smallest cell that still renders its content at the *current* sizing, and the
     * cap is a fraction of the *current* screen, which changes when the device rotates. Only "positive" is enforced
     * here, being an invariant rather than a preference.
     *
     * **A shrink can leave items with nowhere to sit**, since fewer cells fit. Re-homing them is `data:layout`'s
     * (`GridReflow`), triggered by whoever makes this write — this call persists a number and nothing else.
     */
    suspend fun setExtent(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * How tall one row of the one-lane list [slot] is on [device], **in dp** — its blueprint's height with any
     * override.
     *
     * **The other kind of stored size**, beside [extent], and for the opposite reason: a strip's extent is a whole
     * zone's, which its row count divides, where a list's row height is what nothing else can determine — one lane
     * means no cell width to derive from and no extent to divide, so it is the user's outright and the icon is a
     * fraction of *it*. Every other grid's cell height falls out of a number already chosen, which is why only the
     * two lists can be asked and a slot with no `rowHeightDp` throws.
     */
    fun rowHeight(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s row height on [device] to [dp], or clears it back to the blueprint when null.
     *
     * **The caller owns the bounds**, as with [setExtent]: the minimum depends on the current icon sizing, and the
     * maximum is taste rather than fit, since a list scrolls and a tall row costs density and nothing else.
     *
     * Nothing is displaced by this write, unlike a strip's: rows flow, so a taller one shows fewer apps per screen
     * rather than leaving any without a place.
     */
    suspend fun setRowHeight(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * The blank margin at [slot]'s left and right edges on [device], **in dp** — its blueprint's with any override.
     *
     * **Width the grid does not get, not decoration applied over one.** Every cell dimension is divided out of what
     * is left, so a surface reads this *before* it fits its columns; one that padded itself afterwards would draw
     * cells narrower than the ones it sized its icons against. That is also why it is not folded into [gridConfig]
     * or [gridCols] — the list and the card grid have neither, and both have edges.
     *
     * Not clamped against what still fits, exactly as [gridCols] is not.
     */
    fun horizontalPadding(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s horizontal padding on [device] to [dp], or clears it back to the blueprint when null.
     *
     * **Unlike a grid resize, this needs no companion placement write.** Removing a *column* has to say which edge it
     * went from, because that decides where displaced items land; padding removes no cell, it makes every cell
     * narrower. The stored count is untouched, so widening the padding is reversible where a resize is not.
     */
    suspend fun setHorizontalPadding(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * Whether each pager's pages **wrap around** at the ends — resolved, and keyed by the grid that pages.
     *
     * **One map rather than a per-slot flow**, which is the opposite shape to every read above and for a reason they
     * do not have: wrapping has no device dimension and exactly three grids can answer, so the whole answer is three
     * booleans — while the shell needs several at once (HOME's, plus one per bound edge) and which ones depends on
     * settings it is reading in the same breath. A per-slot flow would be a dynamic number of subscriptions serving
     * a value smaller than the subscription.
     *
     * Contains an entry for every wrappable grid, always: a slot with nothing stored resolves to its blueprint's
     * default rather than being absent, so a reader never has to know whether the user has been here.
     */
    val pagerWraps: Flow<Map<GridSlot, Boolean>>

    /**
     * Turns [slot]'s page wrapping on or off, or clears it back to the blueprint when [wraps] is null.
     *
     * **Throws for a slot that is not a configurable pager**, as [setExtent] does for a grid with no extent:
     * `GridBlueprint.wraps` is where "does this grid page" is declared, and deferring to it beats a second list here
     * that could disagree. A folder pages too but is bounded by construction, so it is not askable.
     *
     * **This write changes a gesture, not just an animation.** A wrapping pager has no edge to hand off from, so the
     * one-finger swipe on its axis becomes `OneFingerSwipe.NEVER` — turning wrapping on for HOME's pager means a
     * LEFT- or RIGHT-bound surface needs two fingers to open. Which is why the control says so, and why the
     * blueprint defaults are off.
     */
    suspend fun setPagerWrap(slot: GridSlot, wraps: Boolean?)
}
