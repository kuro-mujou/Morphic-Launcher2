# Settings Port Plan — L1 `data:settings` + `feature:settings` → L2

**Goal:** give L2 a settings layer that the surfaces already built can *read from*, and a settings UI to change
it with — porting L1's knobs while dropping the god object, the 693-line hand-written codec, and the
content-masquerading-as-preferences that come with them.

> Domain concepts and the working model live in [CLAUDE.md](../CLAUDE.md); phase order lives in
> [REWRITE_PLAN.md](REWRITE_PLAN.md) (this is **B7** + **P8**). This file is the source of truth for **what the
> settings layer holds and in what order it lands**. Read it before touching `data:settings` or `feature:settings`.
>
> L1 reference: `../launcher` — `data/settings` (15 files, ~1,960 LOC) and `feature/settings` (35 files, 6,479 LOC).

---

## Why this is a refactor and not a port

Three findings from reading L1 decide most of the plan.

**1. A third of L1's settings blob is not settings.** `LauncherSettings` fuses four things with four different
change cadences behind one flow:

| what | L1 field(s) | where it belongs in L2 |
|---|---|---|
| user preferences | most of it | `data:settings` — the actual port |
| **layout content** | `drawerOrder`, `drawerPages`, `categories`, `categoryAssignments` | **already in Room** — `data:layout`'s `apps_pager_item` / `category` / `category_item` |
| onboarding flag | `setupComplete` | not a setting; L2 has no setup wizard — defer |
| device/system state | `wallpaper.appliedSystemWallpaperId`, `appliedSingle`, `singleDirty` | cache/dirty bookkeeping, not preferences |

The content row is the big one: L1 hand-encodes lists of apps and categories into single DataStore strings with
control-char separators (`U+001F`, `U+001E`). L2 decided that question long ago — arrangement lives in Room, and
`AppsOrderRepository` already implements it. **So the port begins by subtracting, not adding.**

**2. `feature:settings` has no presentation layer to port.** Its screens call `koinInject<SettingsRepository>()`
**directly in composables** — 25 `SettingsRepository` references across the module and exactly one ViewModel (icon
studio). L2's plain-MVVM rule requires a ViewModel per screen with logic out of the composable, so those screens
don't get ported, they get *given* a layer they never had. Budget for that: it is most of the work in each slice.

**3. Half the knobs already have homes in `core:model` / `core:designsystem`.** The port is largely wiring existing
L2 types to storage, not designing new ones:

| L1 type | L2 equivalent | note |
|---|---|---|
| `IconLayoutSettings` (6 fields) | `IconMetrics` (6 fields) | **exact match**, and `IconMetrics.of(...)` already exists "for the settings/layout layer" |
| `GridDimensions` / `GridProfile` / `DockAreaProfile` | `GridBlueprint` + `GridConfig` | L2's has `require()` invariants; L1's has none (nothing stops `columns = 0`) |
| `HomeSurfaceKind` {PAGER_GRID, VERTICAL_LIST} | `HomeLayout` {PAGER_WITH_DOCK, LIST_WITH_WIDGET_AREA} | |
| `AppDrawerLayout` (4) + `AppLibraryLayout` (2) | `AppsLayout` (5) | the drawer/library collapse the surface taxonomy already made |
| `SideSurfaceKind` (5) | `Surface` (2) | L1's SEARCH/RECENTS/CUSTOM_PANEL never shipped |
| `SearchPosition` | `SearchPlacement` | exists, **no consumer yet** |
| `SurfaceTransition` | `SurfaceTransition` | exists, **no consumer yet** |
| `WallpaperEffect` + params | `BackdropEffect` | exists, **no consumer yet** |
| `GridConfigKind`, `gridConfigKind()`, `GridDefaults` | — | superseded by `GridBlueprint`; **do not port** |

Six `core:model` types have **zero consumers** outside `core:model` today (`SurfaceTransition`, `BackdropEffect`,
`SearchPlacement`, `VerticalEdge`, `GridEditRange`, `GridEditorEdge`). Settings will be the first thing to use them,
which means their shapes have never been checked against a caller — **expect to reshape them**, and treat that as
the port doing its job rather than as scope creep.

---

## What L2 is waiting for (the consumers that drive the order)

The port is ordered by "no model in a vacuum": build the slice some already-written placeholder is waiting on.
**Status column added as each was retired** — the remaining `todo` rows are what S4/S5 still owe.

| waiting consumer | file | what it needs | |
|---|---|---|---|
| `LauncherShell(sideSurfaces = emptyMap())` | `feature/shell/LauncherShell.kt` | per-edge binding → **no edge is swipeable today** | ✅ S2 |
| `LauncherShell` `darkTheme = true` | same | wallpaper-brightness signal | todo S5 |
| `AppsScreen(layout = VERTICAL_LIST)` | `feature/apps/AppsScreen.kt` | which APPS layout, per binding | ✅ S2 |
| `DockHeight = 96.dp` | `feature/home/HomeScreen.kt` | dock extent (rows derive **from** it, not the reverse) | ✅ S4c |
| home padding | — (deliberately absent) | horizontal padding, added *with* the setting | todo S4f |
| `RowHeight = 56.dp` | `AppsVerticalList.kt` | list row height | todo S4e |
| `CellHeight = 96.dp` ×2 | `AppsVerticalGrid.kt`, `AppsCategoryPager.kt` | grid cell height | todo S4e |
| `CardColumns = 2` + spacing/padding | `AppsCategoryCard.kt` | card grid geometry (device-blind today) | ✅ S3 (`AppsCardGrid`) |
| 5 × per-surface `IconMetrics` | apps list/grid/pager/category/card, home | icon size, label scale, show label/icon | ✅ S3 |
| one-finger swipe policies | `LauncherShell.bindingFor` | derived from HOME's + the surface's layout | todo |
| home orientation | not built | landscape support | todo |

---

## Target shape

### Storage: one `@Serializable` blob per slice, not 265 flat keys

L1 uses DataStore Preferences with **79 static keys plus ~186 synthesized by string-prefix concatenation**
(`"drawer.profiles.${layout.name}.portrait.iconLayout.iconPercent"`) — ~265 effective keys behind a 693-line codec,
with enum *names* as the wire format and a typo silently falling back to a default.

**L2 stores each slice as one kotlinx-serialization JSON string under one Preferences key.** This is not a new idea
here — it is the same call CLAUDE.md already records for icons, where L1 burned *four destructive DB bumps* before
concluding "one serialized `IconLayerSet` blob, **NOT** flat columns". Same reasoning, same answer.

What it buys: the codec disappears; per-slice reads and writes instead of decode-everything; a slice can carry a
`version` field (L1 has **no** schema version and no `DataMigration` at all); and `@SerialName` decouples storage
from enum spelling, so a rename stops being a silent data-loss migration.

Rejected: **Proto DataStore** — the strongest argument against L1's key soup, but it adds protobuf codegen and a
second schema language to a codebase that already serializes with kotlinx everywhere. Revisit only if blob merging
becomes painful.

### Repository: per-slice flows, no god flow

L1 exposes exactly one `Flow<LauncherSettings>`, so a full ~265-key decode runs on **every** emission and every
consumer wakes for every unrelated change. Worse, every mutator calls `prefs.toLauncherSettings()` *inside* the
edit transaction — deserializing the whole store to change one field, then rewriting the group (`updateDrawerProfile`
rewrites 18 keys to move one slider).

L2: one narrow flow per slice, and writes scoped to a slice. Also fold L1's four-way duplication
(`setSideTop`/`setSideRight`/`setSideBottom`/`setSideLeft` → one `setSide(edge, surface)`; three near-identical
`update*Profile(layout, orientation, transform)` → one).

### Defaults in exactly one place

L1 has the same numbers in **five** places: data-class defaults, `object GridDefaults`, inline literals in the read
path, `profile()` fallbacks, and `Preset` defaults. In L2 the static per-device grid facts are already
`GridBlueprint`'s job, so: **blueprint = default, settings = optional override**, nothing else holds a number.

### Grid + icon config: how a per-surface knob is keyed *(settled)*

Both need the same identity — **which grid, on which device configuration** — so they share one key and one slice.

**The surface × layout axis already exists as a name, not a key.** There are six blueprints (`HomePagerGrid`,
`DockGrid`, `AppsPagerGrid`, `AppsScrollGrid`, `AppsCategoryGrid`, `FolderGrid`) and *that* is the axis. So the id
goes **on the blueprint**, which is what stops the key set and the blueprint set from drifting apart:

```kotlin
// core:model
enum class GridSlot { HOME_MAIN, HOME_DOCK, APPS_PAGER, APPS_SCROLL, APPS_CATEGORY, FOLDER }
data class GridBlueprint(val slot: GridSlot, /* …existing fields… */)

// data:settings — one slice, one DataStore key, the fan-out inside the value
@Serializable
data class SurfaceMetrics(
    val version: Int = 1,
    val grid: Map<GridSlot, Map<DeviceConfiguration, GridOverride>> = emptyMap(),
    val icon: Map<GridSlot, Map<DeviceConfiguration, IconOverride>> = emptyMap(),
)

@Serializable data class GridOverride(val cols: Int? = null, val rows: Int? = null)
@Serializable data class IconOverride(val iconPercent: Float? = null, val labelScale: Float? = null, /* … */)
```

**Keyed by `DeviceConfiguration` (4), not `Orientation` (2).** `GridBlueprint.defaults` is already a
`Map<DeviceConfiguration, GridDefault>` — form factor **crossed with** orientation. An `Orientation`-keyed override
would therefore be *coarser than the default it replaces*: one value would override both phone-landscape and
tablet-landscape even though the blueprint gives them different numbers. Matching the blueprint's granularity keeps
`override ?: default` a like-for-like lookup and gives foldables per-posture config for free. `Orientation` stays what
it is today — the **arrangement** key (only HOME placements and the APPS pager use it), not a config key.

**Sparse, and nullable per field.** A fresh install stores `{}`, not 265 defaults; touching one slider stores one
field. This is what keeps the section above literally true — if settings stored *resolved* values, the blueprint's
number would be copied into storage the moment a user moved anything, and "defaults in five places" would be back.
It also means changing a default later still reaches every field the user never touched. (No tension with the
icon-layer decision to "snapshot the default and detach, no field-merge" — that was forced by variable-length **list**
diffing; merging a fixed-arity record of scalars is a one-liner.)

**Consumers never see the keying.** The repository hands back already-resolved values, so the combinatorial fan-out
stays inside `data:settings`:

```kotlin
fun iconMetrics(slot: GridSlot, device: DeviceConfiguration): Flow<IconMetrics>
fun gridConfig(slot: GridSlot, device: DeviceConfiguration): Flow<GridConfig>   // FIXED_PAGER only
fun gridCols(slot: GridSlot, device: DeviceConfiguration): Flow<Int>            // SCROLL_GRID only
```

`AppsVerticalList` asks for its icon metrics and gets an `IconMetrics` — exactly the type `LocalIconMetrics` already
wants and `IconMetrics.of(...)` already builds. The `gridConfig`/`gridCols` split is not new: it mirrors the existing
`toGridConfig` vs `colsFor`, because a `GridConfig` requires rows and a scrolling grid has none to give.

**`editRange` is the write-side clamp.** An override is coerced into `GridBlueprint.editRange` when *written*, not
when read — so storage can never hold a grid the editor wouldn't allow, and L1's ad-hoc `coerceAtMost` at each call
site disappears. This is the first real consumer for `GridEditRange`, which has sat unused in `core:model`.

**One level of icon config, not two.** L1 stored `iconLayout` at group level (`drawer.iconLayout`) *and* inside every
`GridDimensions` — two places to set the same thing, and 30 of its ~186 generated keys. One level only.

### Settings UI: one destination, two panes, a section per surface *(revised)*

**Structure ported from L1**: a section list that highlights its selection beside a detail on a tablet, and slides
between list and detail on a phone (`SettingsScreen` → `SettingsList` + one `SettingsDetail` `when`). Rows carry an
icon, a title and a subtitle, grouped under headers.

**A section belongs to a *surface*, and holds everything about it** — its layout controls *and* its icon sizing,
which is how L1 had it: every one of its five details embedded `IconLayoutControls` under its layout section. L2 moved
the same way one section at a time and is now **there**: Home, Dock, Apps and Folders each own their icon sizing, and the
standalone icon-sizing screen has been **deleted** (S4k) — it was a waiting room, and it is empty. The name `Icons`
returns with the **icon studio** (B9), which is what L1's `Icons` section actually holds: shape, background and layers, a
per-app concern rather than a per-grid one.

Not yet ported from L1's details: the live **icon preview** between the layout and icon groups, which renders over the
wallpaper through a `BlendMode.Src` punch and so waits on `data:wallpaper` (S5/B7b).

#### Why not one `NavKey` per section *(reversed)*

This plan originally settled on **one `NavKey` per settings section**, on the reasoning below. It was right about
L1's bug and wrong about the layout: **a section is a pane, and on a tablet two of them are on screen at once.** A
destination that is sometimes half a screen is not a destination. `SettingsSection` is now ordinary state inside
`SettingsScreen`, and back is one `BackHandler` that closes an open detail before leaving the surface.

What L1 actually got wrong is a *different* thing, and it is still avoided: it declared `SettingsSection` in the
**navigation module** because a route carried it, so every consumer of navigation could see the whole settings
taxonomy — which is how `feature:home` came to import `SettingsSection.WALLPAPER`. Ours stays inside
`feature:settings`. The original argument follows, kept because the failure modes it names are real ones to watch:

L1 made its 11 sections *panes inside* one destination, and paid for it three times over: back had to be two
mechanisms stitched together by hand (`if (selected != null) closeDetail() else navigator.goBack()`); section state
survived rotation through a hand-written `Saver<SettingsSection?, String>` instead of through the back stack that
already does that; and its two-pane tablet mode silently dropped the "close detail" concept entirely
(`selected ?: SettingsSection.THEME`). All three are symptoms of one cause — the thing the user navigated to was not
a navigation destination.

Nav3 makes a key cheap, so the back stack does all of it for free. Consequences worth stating:

- **The keys live in `feature:settings`, not `core:navigation`.** That boundary is already deliberate and documented
  — L1's navigation module exported an 11-value `SettingsSection` to every consumer, which is how `feature:home`
  ended up importing `SettingsSection.WALLPAPER`. `app` maps the keys, as it already does for the dev harness.
- **Cross-feature deep links wait for a caller.** L1 had exactly one real one (home long-press → wallpaper). When it
  comes back it needs a way to name a destination across a feature boundary without re-creating the dumping ground;
  that is a decision for the commit that needs it, not now.
- **An adaptive two-pane layout stays possible.** It becomes a scene/list-detail strategy over the same keys rather
  than a second, parallel notion of "where am I".

### Not `data:settings` at all *(settled)*

- **`WallpaperRepository` (+Impl, ~380 LOC)** — a bitmap/file/`WallpaperManager` service that merely *borrows*
  settings to persist path pointers. Gets its own **`data:wallpaper`**, depending on `data:settings` rather than
  living in it.
- **`internal/Blur.kt` (112 LOC)** — raw `IntArray` box-blur and dominant-colour extraction. Pure image processing;
  belongs beside the graphics/icon code, in neither repository's module.

[REWRITE_PLAN.md](REWRITE_PLAN.md) listed both under B7; it has been corrected — wallpaper is now **B7b**. This is a
real dependency rather than tidying: wallpaper is what the shell's hardcoded `darkTheme = true` is waiting on, since
launcher chrome takes its dark/light signal from wallpaper brightness.

---

## Phases

Each slice is a full vertical: storage → repository → ViewModel → screen → the placeholder it retires. That way
every phase ends with something visibly working on device, and no slice is written before it has a consumer.

- [x] **S0 — Subtract.** Decide-and-record what does *not* come across: layout content (already Room), `setupComplete`,
      `GridConfigKind`/`gridConfigKind()`/`GridDefaults`, the legacy flat twins (`DockSettings.visualCols/visualRows/heightDp`,
      `WidgetAreaSettings.*`, `DrawerSettings.columns`, `GridSettings.horizontalPaddingDp`), and the two empty sections
      (THEME and GESTURE are 12-LOC "Coming soon" placeholders occupying enum values and list slots — don't port empty
      destinations). No code; this is the shopping list the rest of the plan is scoped against.
- [x] **S1 — `data:settings` foundation.** DataStore + slice-blob codec + `SettingsRepository` with per-slice flows +
      defaults-from-blueprint. Built *with* S2's slice as its first consumer, not before it. Unit-testable without
      Android, like `AppCategorizer` and the `data:layout` arithmetic.
- [x] **S2 — Surface register** (7 knobs: `HomeLayout`, per-edge `Surface`, `SurfaceTransition`). **Lead with this**:
      smallest schema, model fully exists, and it retires the two placeholders with the biggest visible payoff —
      binding APPS to an edge is what makes it swipeable at all, and it gives `AppsLayout` a real owner. Also the first
      settings ViewModel, so it sets the pattern.
- [x] **S3 — Icon metrics** (6 knobs × slot × device configuration). `IconLayoutSettings` ≡ `IconMetrics`, and
      `IconMetrics.of()` was written for this. Retires five per-surface `IconMetrics` placeholders. Keying is settled
      (see "Grid + icon config" above), so the work here is the `GridSlot` id on `GridBlueprint`, the sparse-override
      merge, and the resolved `iconMetrics(slot, device)` flow — the same three pieces S4 then reuses for grids.
      **The controls have since moved into each surface's section** (Home, Dock so far), with `IconSizingEdits`
      sharing the write commands the way `IconSizingControls` already shared the UI.
- [ ] **S4 — Surface geometry** — *in progress.* Sub-steps, because they turned out to have different shapes:
  - [x] **S4a — grid dimension store.** `GridOverride` (sparse per axis, scrolling grids ignore a stored row count) and
        the `editRange` **write-side clamp** — the first consumer of `GridEditRange`. Minima only: maxima are a runtime
        question. 8 tests.
  - [x] **S4b — `resolveBounds`.** `core:designsystem/grid/CellFit.kt`, ported from L1's `CellFit`, which had *two*
        definitions of "smallest usable cell" and its own copy of the cell padding. Now one formula, reading
        `IconLabelCell`'s real constants. 13 tests.
    - **Corrected in S4h: neither of L1's two was right, and the port had adopted the worse one.** The floor is
      `minIconDp + cellPadding` — `resolveIconSize` clamps the percent-derived size **up** to the guardrail, so an icon
      is never drawn smaller than it and a cell overflows exactly when the guardrail exceeds its inner width.
      `iconPercent` scales *within* the guardrails and cannot make a cell unusable, so it has no business in the floor.
      L1's `scrollingMaxColumns` divided by it (which is a different question — "how wide must a cell be for the percent
      to be honoured un-clamped" — and answers this one backwards: at 30% a 28dp guardrail demands a 101dp column, so
      *shrinking* the icons reports fewer columns); L1's `gridMaxima`, the one behind its home editor, left the percent
      out correctly but used the raw guardrail as the whole cell, forgetting the inset. L2 is now L1's home formula plus
      the padding it was missing, with a test pinning that the fraction moves no bound.
  - [x] **S4c — the dock.** Specified below and built, in five parts: `GridReflow.Overflow.EVICT` + `admit` (the two
        halves of a shrink, 6 tests); the extent store (`GridBlueprint.heightDp`, `SurfaceMetrics.dockHeightDp`,
        `dockHeight`/`setDockHeight`, 6 tests); `CellFit.fitGridConfig` (rows from the extent, columns clamped,
        6 tests); home consuming all of it (`DockHeight = 96.dp` retired, `HomeState.dock`, and
        `HomeViewModel.fitDockTo` running the spill when the grid changes); and the **Dock section** —
        `DockRoute`/`DockScreen`/`DockViewModel`, a height slider bounded by one usable cell up to a third of the
        usable window, and a column stepper bounded by `editableRangeIn`. No row control, by design.
  - [x] **S4d — the rows/cols editor.** `GridReflow.edit(edge, add)` (the op, 6 tests), `settleDock` (the two-zone
        rule both dock callers share, 5 tests), one `GridEditor` composable — a screen-shaped preview with a − / +
        pair on each editable edge — and the **Home grid** section. The dock moved onto the same editor, so L1's
        `HomeGridEditor` + `DockGridEditor` (two ~220-line near-copies) are one component parameterised by which half
        of the preview holds the lattice. `usableWindowArea` is the single measurement of the screen, replacing
        L1's `homeGridArea(window, insets, dockVisible, dockThickness)`: subtracting the dock is one caller's
        arithmetic on the result, not part of measuring a window. (It began as settings-only and moved to
        `core:designsystem/grid` in S4i, once a surface and a ViewModel needed the same number.)
  - [x] **S4e — the two derived cell heights** (`AppsVerticalGrid`'s and the category pager's `CellHeight`). **No
        setting, and that is the finding**: a scrolling grid's columns fix its cell *width*, so the height that is left
        is exactly what the icon and label need — `IconLabelCell`'s arithmetic run forwards. `cellHeight` in `CellFit`,
        the forward twin of `minCellHeightDp` and a port of L1's `gridCellHeightDp` (same formula, reading the cell's
        own padding constants instead of a copy). Storing a height *as well* would let two settings disagree; deriving
        it is what makes S3's icon sliders move the grid, as they do in L1. 3 tests.
  - [x] **S4f — the list's row height, and the APPS section it needed.** The one of the three heights that genuinely
        *is* stored, for the reason the other two are not: a list has no cell width to derive from, and `AppsListGrid`
        already said its icon *fills its row*, so the row is the primary quantity and the icon a fraction of it. L1
        hardcodes both (56dp row, 40dp icon, its list ignoring its own icon settings entirely). Built in three parts:
    - **The APPS surface reads its grids from the store** — all five layouts resolved their own size from a blueprint,
      so an override would have gone unread. Fixed *before* a section could write one, which is the bug home had just
      paid for. The pager's is load-bearing: `rows × cols` is the page capacity the store paginates against.
    - **The store** — `GridBlueprint.rowHeightDp` (the third way a cell gets a height: divided out of an extent,
      derived from a width, or declared), `SurfaceMetrics.listRowHeightDp` keyed per configuration like the dock's
      height, `listRowHeight`/`setListRowHeight`. 5 tests.
    - **The section** — one `SettingsSection.APPS` with a chip per configurable layout, editing that layout's grid
      (or, for the list, its row height) plus its icon sizing. A resize here is **one write**, unlike home's two: every
      APPS grid is ordered or derived, so the flow re-densifies and there is nothing to displace. The row-height
      slider's range is derived from the icon guardrails (`rowHeightRangeDp`) rather than stated: **its bounds are the
      guardrail range shifted by the row's own inset**, so the icon range slider governs it — a row shorter than
      `minIconDp` + padding cannot honour the smallest icon allowed, and one taller than `maxIconDp` + padding is height
      the largest cannot fill. `iconPercent` is out of it for the reason S4h took it out of the grid formulas (dividing
      by it inverted the control), and a stored height outside the range is clamped on read (`fitRowHeightDp`, in the
      list and in the slider alike) rather than written down. **With `showIcon = false` neither guardrail applies**, so
      the floor becomes the label's own height (`rowLabelHeight`) and the ceiling opens up to `IconSizingRanges.IconDp`'s
      — bounding a pure-text row by an absent icon forbade a compact list to anyone who had set chunky icons first. 9
      tests. Four APPS slots left the `ICONS` waiting room, which then held only the folder grid (S4k empties it).
      `AppRowCell` also gained the two metrics it had been ignoring (`showIcon`, `labelScale`), since the section
      offers both.
    - **Left open: the category card's lane count.** Its blueprint declares an `editRange`, but a card is a *tile* —
      how narrow one may get is not an icon guardrail, and its blueprint declares no icon sizing at all. Nothing yet
      answers it, and a bound picked by hand is what this port keeps refusing. L1 gave its library layout no grid
      knobs either.
  - [x] **S4h — an editor edits the grid that is *drawn*.** The bug S3 and S4 left between them: every section computed
        its **bounds** from the live icon sizing (so raising the minimum icon dp narrowed the range) but drew and counted
        from the **stored** number, which the surface had already clamped past. So home's editor claimed 4×5 while home
        drew 4×4, `−` wrote 4 instead of 3, and the icon group underneath appeared to do nothing. Fixed by passing the
        *fitted* size to `GridEditor` and to the edit command — `CellFit.fitGridConfig` for home and the dock, and a new
        **`fitCols`** (the scrolling twin: one axis, no label height, 5 tests) for the APPS grids. Three consequences
        worth knowing:
    - **Still clamp-on-read, not L1's write-back.** L1 reconciled this from a `LaunchedEffect` inside its home detail
      that wrote the clamped counts into storage, so an icon tweak destroyed a row count permanently — and only while
      that screen was open. Here the count returns when the icons shrink; only a **press** writes. The one stored
      reduction stays the dock's height commit, for the reason it always was: that height was itself a deliberate change.
    - **The APPS *scrolling* grids needed the same clamp in the surface**, which they never had (`AppsVerticalGrid`,
      `CategoryPage`, and the category pager's drag proxy, all through `fitCols` on their own measured width) — otherwise
      the section would have shown a column count the grid was ignoring, with the icons overflowing their cells.
    - **The APPS *pager* was left out**, as the one grid whose stored count is not a display number — done next, in S4i.
  - [x] **S4i — the pager's capacity is fitted before the store paginates against it.** The APPS pager's rows × cols is
        the page **capacity** `AppsViewModel` paginates the store against — `apps_pager_item` rows carry an explicit page
        and in-page slot — so unlike every other grid it could not be clamped where it is drawn: items past the fitted
        capacity would sit on pages that do not exist, and a drop would compute its slot against a capacity the store
        never applied. The clamp therefore goes *upstream* of pagination — `AppsScreen` measures, fits the stored grid,
        and reports it (`AppsViewModel.setPagerFit`), and that fit is what `pagerPages`, `syncPager` and `applyPager` read.
        Four things worth knowing:
    - **The report is gated on the store having answered.** Pagination *writes*, so a capacity guessed from the blueprint
      for one frame and corrected on the next would write rows twice and visibly reshuffle the pages. Every reader already
      treated null as "not yet", which is the same guard home states for its own settle effects — so the gate cost
      nothing to add and is the load-bearing part.
    - **It is not `setPagerGrid` returning.** That pushed the *blueprint's* size down, which put a default the store owns
      in the UI. This pushes a runtime **bound** the store cannot know, exactly as `DockViewModel.setHeight` is told its
      row cap; `setDevice` stays the input it was made, and `AppsState.pagerConfig` is now explicitly the *stored* size
      (the fit's input) rather than the capacity.
    - **Reported from `AppsScreen`, not from the pager's arm**, so it does not depend on which layout is showing: the
      pager's arrangement stays in step with what is installed whatever the user is looking at, which is the invariant
      that makes switching layout reload nothing.
    - **The APPS section stopped excluding the pager**, so every grid in that editor now shows what its surface draws.
  - [x] **S4j — one measurement of the window.** The pager fit needs the screen, and `feature:settings` held the only
        copy of that arithmetic. `usableWindowArea` moved to `core:designsystem/grid`, beside the `GridArea` it returns,
        and home's own inline copy went with it — so the three kinds of caller (a surface laying out, a section bounding
        what may be chosen, a ViewModel being told a capacity) cannot disagree about how big the phone is. That was L1's
        real bug here (`homeGridArea` in settings vs `pagerBoundsInWindow` on the surface), and it had been a third of the
        way back.
  - [x] **S4k — the folder section, and the end of the icon-sizing screen.** The last grid in the waiting room gets its
        own section, so every surface now holds its own icon sizing and the room is deleted rather than left as a heading
        with nothing under it. **It is L1's shape exactly**: `FolderSettingsDetail`'s layout group is literally `{}` and
        its `IconLayoutControls` are the whole screen, because `FolderGrid` declares no `editRange` — a folder's card is
        sized to the screen, so its rows and columns follow rather than being chosen. Three things the section does with
        that:
    - **States the page size instead of offering it** (`FolderGrid.defaults` for the current configuration), which
      pre-empts "where are the − / + buttons?" without inventing an answer to it.
    - **Says it also governs the category card's expansion**, since that is the same `FolderOverlay` on the same grid —
      a user who changed one and saw the other move would otherwise read it as a bug.
    - **Takes L1's row wording** ("Folders" / "Icon and text size" / `Icons.Outlined.Folder`) and L1's position: last in
      the surface group, a folder being drawn *over* a surface rather than being one.
    The `Personalization` header went with the screen — every section left belongs to a surface, and a heading over one
    row does no work. **The name `Icons` returns with the icon studio** (B9), which is what L1's `Icons` section actually
    holds: shape, background and layers, never grid sizing.
  - [x] **S4l — one set of icon defaults, and a floor that is really a cell floor.** Every blueprint declared its own
        `iconPercent` (home 88%, the app grids 75%, the list 100%) and inherited `28..72dp` guardrails from
        `IconSizing`. All of it collapses to **one** default — `iconPercent = 1f`, `24..48dp` — which every blueprint
        now takes unmodified (`icon = IconSizing()`), so "a default lives in exactly one place" is literally true of
        icon sizing too. Three reasons, in the order they matter:
    - **At 100%, `maxIconDp` *is* the icon size** on any cell bigger than it, so "how large are my icons" becomes one
      number in dp instead of a percentage of a cell size the user has to picture. The per-grid fractions were the
      *fraction* doing the upper guardrail's job — and double-counting density at that, since a narrower cell already
      yields a smaller icon at 100% (the fraction is *of the cell*). The fraction keeps the job it is good at:
      shrinking an icon inside a cell that is already small.
    - **The lower guardrail is a cell floor**, because `CellFit` inverts it. At the old bound of 16dp that meant a 24dp
      cell — thirteen rows in a 320dp dock, fifteen columns across a phone — legal arithmetic nothing could be tapped
      in. `IconSizingRanges.IconDp` now starts at **24**, which is the number L1 wrote down for exactly this
      (`MIN_CELL_DP`, "press-area floor") and then never used.
    - **The ceiling is 120dp and is a judgement**, stated as one on `IconDp`: far enough for a tablet cell to be filled
      at the default fraction, near enough to keep 24–48 legible on the track, and low enough that even the *lower*
      thumb at maximum leaves a 360dp phone two columns rather than none.
    Visible consequence to expect on device: home icons were drawing at 72dp (88% of an 82dp inner bound, capped by the
    old 72dp guardrail) and now draw at 48dp. `IconMetrics`' Compose-side defaults moved in step — they are the same
    record and a difference between them would show as a change at the moment the store answered.
  - [ ] **S4g — horizontal padding** for every layout, home's included. Deferred by decision (see the dock, rule 5).
- [ ] **S5 — `data:wallpaper` + effects.** Wallpaper source/rotate/crop and the `BackdropEffect` params (11 knobs).
      Unblocks the shell's wallpaper-brightness theme input. Blur/dominant-colour move out of settings on the way.
- [ ] **S6 — Folder + the long tail.** Folder metrics (1 knob), search placement (needs the alphabet-strip/search
      feature to exist first), presets.
- [ ] **P8 exit criteria** — every placeholder constant in the table above is either settings-driven or has a written
      reason not to be.

### Deferred, with reasons

| item | L1 size | why not now |
|---|---|---|
| Icon studio | `IconStudioScreen` 247 + `iconstudio/` 373 + `IconLayoutPreview` 404 | blocked on B3's deferred half (per-layer effects + the live editor) |
| Home grid editor | `HomeGridEditor.kt` 685 | wants a `resolveBounds(blueprint, area, iconRail)` resolver that doesn't exist yet |
| THEME, GESTURE sections | 12 LOC each | zero knobs; add the section when the feature exists |
| Presets | ~150 LOC | needs enough slices to be worth a persona; and L1's `Preset` is a 16-field flattened *shadow* of a `LauncherSettings` subset — port it as a partial settings object, applied in one transaction, not seven |

---

## Decisions on record

Nothing is open. Each is argued where it applies rather than restated here; this is the index.

| decision | settled as | argued in |
|---|---|---|
| Storage mechanism | one `@Serializable` blob per slice; Proto DataStore rejected | *Storage* |
| Read/write surface | per-slice flows, no god flow | *Repository* |
| Where defaults live | `GridBlueprint` only; settings stores overrides | *Defaults in exactly one place* |
| Per-surface knob key | `GridSlot` (on the blueprint) × `DeviceConfiguration`, sparse per field | *Grid + icon config* |
| Override precedence | clamped into `editRange` **on write** | *Grid + icon config* |
| Settings sections | **panes** inside one `SettingsScreen` (two-pane on tablet), declared in `feature:settings` | *Settings UI* |
| Where icon sizing lives | in each **surface's** section, beside its layout controls — as L1 has it | *Settings UI* |
| Wallpaper + blur | out of `data:settings` → `data:wallpaper` (B7b) | *Not `data:settings` at all* |
| Dock rows | **stored**; the height caps them, and a height commit writes them down when they no longer fit | *The dock (S4c)* |
| Dock columns | **stored**, with the fit as a ceiling and the clamp applied on *read* | *The dock (S4c)* |
| Dock height cap | a fraction of the current screen height, so it changes with orientation | *The dock (S4c)* |
| Grid resizing | names an **edge**, not a count — and commits the count *and* the placements it displaces | *S4d* |
| What an editor shows | the **fitted** count (what the surface draws), and a press counts from that — never the stored one | *S4h* |
| Where a fit is applied | in the surface that draws it — **except** the APPS pager's, which is a store capacity and so is reported to its ViewModel | *S4i* |

The last one edited [REWRITE_PLAN.md](REWRITE_PLAN.md)'s build map; the rest are local to this plan.

**Still genuinely undecided, but not blocking** — deferred because no caller needs them yet, not because they are
hard: how a cross-feature deep link names a settings section (L1's one case was home long-press → wallpaper), and
whether the tablet two-pane layout returns as a scene strategy over the section keys.

---

---

## The dock (S4c) — settled, not yet built

The dock is the one surface whose geometry is **entirely derived**, and it is where S4 stopped. Recorded here in full
because it resolves the contradiction the earlier plan left open: `CLAUDE.md` said the dock's row count derives from its
extent, while `DockGrid`'s blueprint declares `rows = 1` per device. **The derivation wins; the blueprint's rows become a
fallback, not a setting.**

1. **Height is the setting, and it is adjustable** — the dock can be shrunk or expanded. Capped at a fraction of the
   *current* screen height (a third or a quarter, to be picked), so the cap **changes with orientation** rather than
   being one dp constant. (L1 used a fixed `80f..320f` dp slider, which cannot do that.)
2. **Cell size is calculated from the icon and the text**, so the icon/text min-max rail (S3's `IconSizing`) feeds the
   dock's cell size directly. This is the same inverse `resolveBounds` already implements — `minCellWidthDp` /
   `minCellHeightDp` in `core:designsystem/grid/CellFit.kt`.
3. **Both counts are stored and edited; the height *bounds* the rows.** *(Revised twice: it first said both axes were
   derived from the extent, then that rows alone were. Neither survived contact with the behaviour.)* A cell is
   `height ÷ rows`, so the height caps how many rows are usable rather than replacing them. `DockGrid.editRange`
   gives both axes a minimum, the editor offers both, and **+ a row is enabled only while another row would still
   leave cells at least the smallest usable height**. Deriving rows read as an editor missing half its buttons;
   deriving *columns* was worse, since at the default icon guardrail (`minIconDp = 24`) a 360dp phone dock
   derives **eleven** columns against the four it has today — and the dock's width is the screen's rather than anything
   a user chose. This is L1's model, and it is the right one.
4. **The height commits on release, then reduces the rows if they no longer fit.** Dragging the slider previews only
   (`SettingsCommitSlider`), so one gesture is one transaction; on release the height is written, and if the stored
   row count now describes cells shorter than the minimum, the rows are **written down** to what the new height
   carries. This is the one clamp that is stored rather than applied on read, and the asymmetry is deliberate: the
   height that invalidated the rows was itself a committed change, where a column count invalidated by an icon-size
   change is left alone and returns when the icons shrink. L1 reconciled *everything* this way from a
   `LaunchedEffect` *inside `DockSettingsDetail`*, which also only ran while that screen was open ("Neither surface
   reflows reactively"); reducing at the write instead means the dock and its own settings cannot disagree whether or
   not anyone is looking.
5. **Shrinking swallows the bottom row.** When a new height no longer fits the current row count, the dock drops its
   bottom row and the remaining rows grow to fill the new extent — rather than squashing every cell. Expanding is the
   same rule in reverse.
6. Horizontal padding for every layout is a **separate, later** decision (it is also home's missing `padding`
   placeholder). Deliberately not folded in here. (L1 has one on the dock: `0..32` dp.)

**Consequence worth planning for:** rule 5 removes cells that may hold items. A swallowed row's occupants need somewhere
to go, which is `data:layout`'s reflow (`GridReflow`) rather than a settings concern — but it is the settings write that
triggers it, so the two have to meet. That is the first piece of S4c to design, not the slider.

**Where those occupants go is L2's own answer.** L1 **deletes them** (`DockGridEdit` reports `droppedApps` /
`droppedFolders` / `droppedWidgets` for the caller to delete, with widget ids unbound from the host) — an app in a
swallowed row is simply gone. L2 spills them onto HOME's main area instead: `GridReflow.reflow(…, Overflow.EVICT)`
hands back what the single-page strip cannot keep, and `GridReflow.admit(…)` finds each a cell on the pager. Two other
things worth taking from L1's version: it checks `findFreeRect(...).page != 0` — computing a placement on a page the
dock never draws and then discarding it, where `findFreeRectOnPage` never invents it — and its edit ordering is
**grow-first for adds, place-first for removes**, since items shifted into a larger grid always fit.

## Smells not to reproduce

Recorded so the port is checked against them rather than trusting memory. All from `../launcher/data/settings`.

1. **God object across four lifetimes** — see the table at the top.
2. **One flow → global invalidation**, with a full-store decode per emission.
3. **Read-all-to-write-one** — every mutator deserializes ~265 keys inside its own edit transaction.
4. **Legacy flat fields kept alive as migration seeds**, with three tests dedicated to the hack and no schema
   version anywhere — the migration is *permanently* embedded in the read path. L2 has no installed settings base,
   so it simply drops them.
5. **Defaults in five places.**
6. **Grid/UI policy in the data layer** (`GridConfigKind`, `GridDefaults`) while the real grid model lives in
   `core:model` — and L1's parallel `GridDimensions` has no invariants, so callers `coerceAtMost` ad hoc in at least
   two places.
7. **Derived state persisted as settings** — `appliedSingle`, `singleDirty`, and a landscape default that is literally
   the portrait values with rows/columns swapped, frozen into storage.
8. **Non-atomic multi-writes** — preset apply = 7 transactions and 7 observable intermediate states;
   `WallpaperRepositoryImpl` does a read-modify-write *outside* any transaction (lost-update race).
9. **Four-way method duplication** in the repository interface.
10. **Silent failure in the codec** — JSON decode errors swallowed to `null`; `enumOrNull` does a linear scan per
    read and falls back to a default with no diagnostic when a constant is renamed.
11. **Prefix-string key construction** at four nesting levels — untypable, unlistable, unmigratable.
