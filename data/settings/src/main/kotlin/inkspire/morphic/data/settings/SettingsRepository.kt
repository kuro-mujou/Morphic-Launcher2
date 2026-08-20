package inkspire.morphic.data.settings

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
 * - **Derived or cached state** — a dominant color, a "dirty" marker, the id of the wallpaper currently applied to
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

    /** How frosted surfaces render over the wallpaper — the one global choice, and the strengths tuning it. */
    val backdropEffect: Flow<BackdropEffect>

    /**
     * The **global default icon recipe**: the layer set every app's icon renders from until that app is edited.
     *
     * Emits [IconLayerSet.Base] when nothing is stored — the plain two-layer app-default set — so "no settings yet"
     * and "the user reset everything" are the same state and no consumer special-cases either.
     *
     * **Per-app overrides are deliberately not here.** They are rows in `data:icons`, because there is one of them
     * per customized app and this is a preference store, not an arrangement store — the same line `drawerOrder` and
     * `categories` fell on the wrong side of in L1. The two meet in the composition, where an icon resolves its
     * override or falls back to this.
     */
    val iconAppearance: Flow<IconAppearance>

    /**
     * The APPS surface's chrome — the search field's placement, and which edge the category tabs sit on.
     *
     * **Read by the settings editor today and by the surface when those features land.** Neither search nor the tab bar
     * is built in `feature:apps` yet, so this is the one setting whose only current consumer is a preview. That is a
     * deliberate exception to "no model in a vacuum", taken because the preview is a real consumer with a real
     * question — L1's editor draws both, and drawing them from invented constants is what the exception avoids.
     */
    val appsChrome: Flow<AppsChrome>

    /** Sets where the APPS search field sits. */
    suspend fun setSearchPlacement(placement: SearchPlacement)

    /** Sets which edge the category pager's tab bar sits on. */
    suspend fun setTabBarEdge(edge: VerticalEdge)

    /**
     * The user's saved icon recipes. Empty until one is saved; there are no built-ins.
     *
     * A whole list rather than a lookup by name, because that is how it is used — the library is *shown*, and a
     * preset is chosen from what is on screen rather than fetched by a name someone typed.
     */
    val iconPresets: Flow<List<IconPreset>>

    /** Saves [layerSet] under [name], replacing any preset already called that. */
    suspend fun saveIconPreset(name: String, appearance: IconAppearance)

    /** Removes the preset called [name]. A no-op if there is none. */
    suspend fun deleteIconPreset(name: String)

    /**
     * Renames the preset called [from] to [to], keeping its place in the library.
     *
     * Its own operation rather than a delete and a save, because the name *is* the identity: spelled that way the
     * preset would come back at the end of the list. See [IconPresets.renamed].
     */
    suspend fun renameIconPreset(from: String, to: String)

    /**
     * Applies [transform] to the stored effect **inside the write**, rather than replacing it with a value the caller
     * computed earlier.
     *
     * **A transform rather than a value, because a value is a lost update — and it was one.** A screen builds
     * its next value with `copy` off the effect it last *saw*, and seeing it means a flow emission — so a second edit
     * issued before the first has come back round carries the first's field values with it. On the effects section that
     * showed as tapping a tint swatch and then pressing a stepper: the stepper's `copy` still held the pre-tap effect,
     * so the wash it had just written was overwritten with the old one. The steppers made it easy to reach because they
     * are *fast* — a slider drag lasts long enough that the store has caught up, a press does not.
     *
     * The transform runs where the old value is genuinely current: inside the same `edit` that writes, which is where
     * this module already puts every other read-modify-write (see `SettingsRepositoryImpl.update`). So an edit says
     * *what to change* and never *what everything else was*.
     *
     * **There is deliberately no whole-value setter beside it.** A whole-value setter is the shape that causes this,
     * so leaving one would leave the trap loaded for the next screen — an effect chosen outright is
     * `updateBackdropEffect { chosen }`, which says the same thing and cannot carry a stale field with it.
     * The whole-value writes that remain ([setIconAppearance], [setIconStudioBackground]) are ones where the value really
     * is the whole setting and no part of it is ever patched.
     *
     * **There is still no field-level setter, and the sealed type is why** — the reasoning the setter this replaced was
     * written for. Which parameters apply depends on *which* variant is selected, so there is no field to update
     * independently of it: `updateIcon` and `updateGrid` take a transform over a sparse record where one field genuinely
     * can move alone, and this takes one over a value that has to be rebuilt whole. A setter per variant per parameter is
     * the alternative, and it is a dozen of them.
     *
     * **The consequence worth knowing: switching variants discards the previous one's parameters**, because they are not
     * stored anywhere else. Within a variant nothing is lost — a blur keeps its strength and amount across a change of
     * wash, which is the comparison a user actually makes, and that is *why* the wash became a parameter rather than
     * staying four variants. L1 kept all ten parameters alive at once in a flat bag; the sealed type trades that for
     * making an effect unable to hold another effect's parameters, and this is the bill.
     */
    suspend fun updateBackdropEffect(transform: (BackdropEffect) -> BackdropEffect)

    /**
     * Replaces the global default icon recipe outright.
     *
     * A whole-value write, one step further than [setIconStudioBackground]'s: a layer set is an **ordered list**, so
     * there is no sparse record to patch and no stable key to patch it by — insert a layer and every index below it
     * moves. The editor holds the whole set anyway (that is what it edits), so it writes the whole set.
     *
     * This is also what makes undo cheap: a set is an immutable value, so the editor's history is a list of these and
     * a step is an index. L1 could not do that, because its equivalent state was a bag of flat fields.
     */
    suspend fun setIconAppearance(appearance: IconAppearance)

    /**
     * What the **icon studio's canvas** is drawn on, so the studio reopens on the backdrop the user left it on.
     *
     * **A workspace preference, and the first one here that is not about what the launcher looks like.** It shapes no
     * surface and reaches no rendered icon — it is the paper, not the drawing — which is exactly why it is *not* part of
     * [iconAppearance]: a recipe stored with a backdrop in it would make an icon's identity depend on what someone
     * happened to be looking at while they made it. It is a preference all the same, by the only test that matters
     * here: the user chose it, and would be annoyed to choose it again.
     *
     * Emits [PreviewBackground.Default] until one is chosen.
     */
    val iconStudioBackground: Flow<PreviewBackground>

    /**
     * Remembers [background] as the icon studio's canvas.
     *
     * A whole-value write like [setIconAppearance], for the simplest version of its reason: the setting *is* one value,
     * so there is no field to patch.
     */
    suspend fun setIconStudioBackground(background: PreviewBackground)

    /**
     * How the icon studio's workspace is arranged — the preview's pan and zoom, and where the layer rail was put.
     *
     * [iconStudioBackground]'s sibling and its exact argument: the paper, not the drawing. See [IconStudioWorkspace]
     * for why every value in it is a fraction of the canvas rather than a dp, and for why it is a slice of its own
     * rather than a field beside the backdrop.
     *
     * Emits [IconStudioWorkspace.Default] until the user has arranged something, and **never emits a value carrying a
     * non-finite float** — see [IconStudioWorkspace.sanitized], which is applied on the way out so no consumer has to
     * remember to.
     */
    val iconStudioWorkspace: Flow<IconStudioWorkspace>

    /**
     * Remembers how the studio's workspace is arranged.
     *
     * A whole-value write like [setIconStudioBackground]: the studio owns this continuously while a gesture is in
     * flight and hands back the settled arrangement, so there is no field to patch and nothing to merge with.
     */
    suspend fun setIconStudioWorkspace(workspace: IconStudioWorkspace)

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
     * The **card chrome** to draw tile grid [slot] with on [device] — resolved from its blueprint and any override.
     *
     * The third kind of stored size, beside [extent] and [rowHeight], and the one that is not a size of the *grid* at
     * all: a title's scale, a corner radius and two paddings shape one card, where those two shape the area a grid is
     * laid out in. A slot whose blueprint declares no `card` throws, which is that pair's convention — only a grid of
     * tiles can be asked.
     */
    fun cardChrome(slot: GridSlot, device: DeviceConfiguration): Flow<CardChrome>

    /**
     * Overrides one or more card-chrome fields for [slot] on [device]; a null field in [transform] clears it back to
     * the blueprint, which is how a per-control reset works — [updateIcon]'s contract, for the same reason.
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
     * returning something nullable. The split mirrors `toGridConfig` / `colsFor` in `core:model`, for the same reason.
     */
    fun gridConfig(slot: GridSlot, device: DeviceConfiguration): Flow<GridConfig>

    /**
     * The **visual** column count for [slot] on [device] — the whole of a scrolling grid's size, and half the
     * dock's (whose rows come from [extent] instead).
     *
     * Not clamped to what actually fits: that needs a measured area, so a surface that cares does it where it
     * measures. Reading a column count the screen has outgrown is far better than storing the clamp, since the
     * user's choice survives whatever shrank the fit.
     */
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
     *
     * **A grid can be editable on one axis only, and the clamp is what enforces it**: a null `minRows` drops a row
     * override rather than storing one. Two grids rely on that for two different reasons — a scrolling grid's rows
     * come from its content, and a side zone's **come from [extent]** (see `DockGrid`). Neither can be given a row
     * count by writing one here.
     */
    suspend fun updateGrid(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: GridOverride.() -> GridOverride,
    )

    /**
     * How thick the fixed-extent strip [slot] is on [device], in dp — its blueprint's extent with any override.
     *
     * **A height where the zone is a strip and a width where it is a rail** (`SideZoneEdge`), which is why it is an
     * extent rather than a height: the stored number is the same thickness either way, and the posture decides which
     * dimension it names and which of the two counts it bounds.
     *
     * **Only HOME's two side zones can be asked** — the dock and the widget area. Every other grid is configured by
     * counts and takes the space it is given, so "how thick is the APPS pager" has no answer; a slot whose blueprint
     * declares no `extentDp` therefore throws rather than resolving to something invented. This used to be a
     * dock-only method for that reason; the widget area is the second grid that can answer, and naming each of them
     * would have been the same method twice.
     */
    fun extent(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s extent on [device] to [dp], or clears it (back to the blueprint) when null.
     *
     * **The caller owns the bounds**, unlike [updateGrid], which floors what it is given. Both of an extent's bounds
     * are runtime facts a store cannot check: the floor is the smallest cell that still renders its content at the
     * *current* sizing, and the cap is a fraction of the *current* screen — so the cap changes when the device
     * rotates, which no stored constant would. What is enforced here is only that an extent is positive, which is an
     * invariant rather than a preference.
     *
     * **A shrink can leave items with nowhere to sit**, since fewer cells fit. Re-homing them is `data:layout`'s
     * (`GridReflow`), triggered by whoever makes this write — this call persists a number and nothing else.
     */
    suspend fun setExtent(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * How tall one row of the one-lane list [slot] is on [device], in dp — its blueprint's height with any override.
     *
     * **The other kind of stored size**, beside [extent], and for the opposite reason. A strip's extent is a whole
     * zone's, which its row count then divides; a list's row height is what nothing else can determine — one lane
     * means no cell width to derive a height from and no extent to divide, so it is the user's outright, and the
     * icon in the row is a fraction of *it*. Every other grid's cell height falls out of a number already chosen
     * (`CellFit.cellHeight`, or an extent ÷ rows), which is why only the two lists can be asked and a slot with no
     * `rowHeightDp` in its blueprint throws.
     */
    fun rowHeight(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s row height on [device] to [dp], or clears it (back to the blueprint) when null.
     *
     * **The caller owns the bounds**, as with [setExtent]: what a row must be at least depends on the current icon
     * sizing, and what it may be at most is taste rather than fit — a list scrolls, so a tall row costs density and
     * nothing else. Only "positive" is enforced here, which is an invariant rather than a preference.
     *
     * Nothing is displaced by this write, unlike a strip's: rows flow, so a taller one shows fewer apps per screen
     * rather than leaving any without a place.
     */
    suspend fun setRowHeight(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * The blank margin at [slot]'s left and right edges on [device], in dp — its blueprint's with any override.
     *
     * **Width the grid does not get, not decoration applied over one.** Every cell dimension is divided out of what is
     * left, so a surface reads this *before* it fits its columns; one that padded itself afterwards would draw cells
     * narrower than the ones it sized its icons against. That is also why it is not folded into [gridConfig] or
     * [gridCols] — the list and the card grid have neither, and both have edges.
     *
     * Not clamped against what still fits, exactly as [gridCols] is not: `CellFit` sees the reduced width and reports
     * fewer columns, so narrowing the padding brings them back.
     */
    fun horizontalPadding(slot: GridSlot, device: DeviceConfiguration): Flow<Int>

    /**
     * Sets [slot]'s horizontal padding on [device] to [dp], or clears it (back to the blueprint) when null.
     *
     * **Unlike a grid resize, this needs no companion placement write.** Removing a *column* has to say which edge it
     * went from, because that decides where the displaced items land; padding removes no cell — it makes every cell
     * narrower. A grid whose columns no longer fit reports fewer on read and re-flows what it draws, and the stored
     * count is untouched, so widening the padding is reversible where a resize is not.
     */
    suspend fun setHorizontalPadding(slot: GridSlot, device: DeviceConfiguration, dp: Int?)

    /**
     * Whether each pager's pages **wrap around** at the ends — resolved, and keyed by the grid that pages.
     *
     * **One map rather than a per-slot flow**, which is the opposite shape to every read above it and for a reason
     * those do not have. Wrapping has no device dimension, and exactly three grids can answer, so the whole answer is
     * three booleans; meanwhile the shell needs several of them *at once* — HOME's, plus one per bound edge — and
     * which ones depends on settings it is reading in the same breath. A per-slot flow would make that a dynamic
     * number of subscriptions to serve a value smaller than the subscription. Surfaces that draw one pager index the
     * map by their own slot.
     *
     * Contains an entry for every wrappable grid, always — a slot with nothing stored resolves to its blueprint's
     * default rather than being absent, so a reader never has to know whether the user has been here.
     */
    val pagerWraps: Flow<Map<GridSlot, Boolean>>

    /**
     * Turns [slot]'s page wrapping on or off, or clears it (back to the blueprint) when [wraps] is null.
     *
     * **Throws for a slot that is not a configurable pager**, exactly as [setExtent] does for a grid with no extent:
     * `GridBlueprint.wraps` is where "does this grid page" is declared, and deferring to it beats a second list here
     * that could disagree. A folder pages too but is bounded by construction, so it is not askable either.
     *
     * **This write changes a gesture, not just an animation.** A wrapping pager has no edge to hand off from, so the
     * one-finger swipe on its axis becomes `OneFingerSwipe.NEVER` — turning wrapping on for HOME's pager means a
     * LEFT- or RIGHT-bound surface needs two fingers to open. That is why the control says so, and why the blueprint
     * defaults are off.
     */
    suspend fun setPagerWrap(slot: GridSlot, wraps: Boolean?)
}
