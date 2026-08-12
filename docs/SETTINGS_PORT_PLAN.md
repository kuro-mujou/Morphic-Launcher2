# Settings Port Plan — L1 `data:settings` + `feature:settings` → L2

**Goal:** give L2 a settings layer that the surfaces already built can *read from*, and a settings UI to change
it with — porting L1's knobs while dropping the god object, the 693-line hand-written codec, and the
content-masquerading-as-preferences that come with them.

> Domain concepts and the working model live in [CLAUDE.md](../CLAUDE.md); phase order lives in
> [REWRITE_PLAN.md](REWRITE_PLAN.md) (this is **B7** + **P8**). This file is the source of truth for **what the
> settings layer holds and in what order it lands**. Read it before touching `data:settings` or `feature:settings`.
>
> L1 reference (`../Morphic-Launcher` or `../launcher`, per machine) — `data/settings` (15 files, ~1,960 LOC) and `feature/settings` (35 files, 6,479 LOC).

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
| `WallpaperEffect` + params | `BackdropEffect` | ✅ S5f-2 — a settings slice, read by the shell, drawn by `wallpaperBackdrop` |
| `GridConfigKind`, `gridConfigKind()`, `GridDefaults` | — | superseded by `GridBlueprint`; **do not port** |

Six `core:model` types had **zero consumers** outside `core:model` when this was written (`SurfaceTransition`,
`BackdropEffect`, `SearchPlacement`, `VerticalEdge`, `GridEditRange`, `GridEditorEdge`). Settings would be the first
thing to use them, which meant their shapes had never been checked against a caller — **expect to reshape them**, and
treat that as the port doing its job rather than as scope creep.

**`BackdropEffect` is the worked example, and it survived first contact almost intact** (S5f-2). Its sealed shape held
up where a flat `WallpaperEffect` + `WallpaperEffectParams` pair would not have, and it needed exactly two additions:
`@Serializable`/`@SerialName` so it can be a settings slice, and a `blurStrength` property so the one thing every
consumer asks it is not a `when` re-written per caller. What did *not* survive is a variant — `MaterialYou` asks for a
wallpaper hue, which reads as a conflict with the monochrome palette rule until you notice the rule is about chrome
and this is an effect the user picks — a distinction only a caller could have surfaced, and one that took a reversal
mid-slice to settle.

---

## What L2 is waiting for (the consumers that drive the order)

The port is ordered by "no model in a vacuum": build the slice some already-written placeholder is waiting on.
**Status column added as each was retired** — the remaining `todo` rows are what S4/S5 still owe.

| waiting consumer | file | what it needs | |
|---|---|---|---|
| `LauncherShell(sideSurfaces = emptyMap())` | `feature/shell/LauncherShell.kt` | per-edge binding → **no edge is swipeable today** | ✅ S2 |
| `LauncherShell` `darkTheme = true` | same | wallpaper-brightness signal — **L2's own idea, not a port** | ✅ S5f-1 |
| `AppsScreen(layout = VERTICAL_LIST)` | `feature/apps/AppsScreen.kt` | which APPS layout, per binding | ✅ S2 |
| `DockHeight = 96.dp` | `feature/home/HomeScreen.kt` | dock extent (rows derive **from** it, not the reverse) | ✅ S4c |
| home padding | — (deliberately absent) | horizontal padding, added *with* the setting | ✅ S4g |
| `RowHeight = 56.dp` | `AppsVerticalList.kt` | list row height | ✅ S4f |
| `CellHeight = 96.dp` ×2 | `AppsVerticalGrid.kt`, `AppsCategoryPager.kt` | grid cell height | ✅ S4e (derived, not stored) |
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

The live **icon preview** between the layout and icon groups is ported (S4m). What is *not* is the wallpaper behind
it — L1 punched through to the live wallpaper with `BlendMode.Src`. The window it punches *to* now exists (the launcher
theme shows the wallpaper); what is left is the punch itself, with the effects (S5f).

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
  settings to persist path pointers. Gets its own **`data:wallpaper`**.
  - **Revised when built (S5a): it does not depend on `data:settings` either.** The sentence here used to end
    "depending on `data:settings` rather than living in it", on the assumption that the path pointers would stay in the
    settings blob. They did not: S0 had already ruled that L1's applied-snapshot and dirty flags are bookkeeping rather
    than preferences, and a module that owns the files may as well own the pointers to them — so `data:wallpaper` has its
    own one-key DataStore and no settings dependency at all. The **effect params** are the part that is genuinely a
    preference, and they stay in `data:settings` (S5f).
- **`internal/Blur.kt` (112 LOC)** — raw `IntArray` box-blur and dominant-color extraction. Pure image processing;
  belongs beside the graphics/icon code, in neither repository's module.
  - **Revised again at S5f-2: both halves live in `data:wallpaper`.** "Beside the graphics code, in neither
    repository's module" was written when the wallpaper lived *inside* `data:settings` — image processing genuinely has
    no business in a preferences store. `data:wallpaper` is the opposite case: it exists because it decodes bitmaps,
    and `cropAndScale` plus the sampled decode are in the file next door, so moving these somewhere abstract would
    separate them from their only caller to honor a sentence about a module that no longer holds it. `dominantColor`
    ships too, as `accentColor`'s API-26 fallback — see S5f-2 for why the hue is kept.
  - **Revised at S5f-1: the brightness signal does not need it, and neither half is ported yet.** The line below said
    the shell's `darkTheme` was waiting on the dominant-color half. It was not.
    `WallpaperManager.getWallpaperColors` answers the question over the wallpaper *actually displayed* — no permission,
    no decode, and on API 31+ `HINT_SUPPORTS_DARK_TEXT` is the verdict itself — while `dominantColor` is a
    **saturation-weighted** average, built so a vivid accent beats washed-out gray. That is what an accent wants and
    the opposite of what brightness wants, so reusing it would have been a wrong answer wearing a reused name. Both
    halves of `Blur.kt` now wait on their real consumer, the frosted backdrop (S5f-2).

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
      to be honored un-clamped" — and answers this one backwards: at 30% a 28dp guardrail demands a 101dp column, so
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
        `HomeGridEditor` + `DockGridEditor` (two ~220-line near-copies) are one component parameterized by which half
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
      `minIconDp` + padding cannot honor the smallest icon allowed, and one taller than `maxIconDp` + padding is height
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
    - **The ceiling is 120dp and is a judgment**, stated as one on `IconDp`: far enough for a tablet cell to be filled
      at the default fraction, near enough to keep 24–48 legible on the track, and low enough that even the *lower*
      thumb at maximum leaves a 360dp phone two columns rather than none.
    Visible consequence to expect on device: home icons were drawing at 72dp (88% of an 82dp inner bound, capped by the
    old 72dp guardrail) and now draw at 48dp. `IconMetrics`' Compose-side defaults moved in step — they are the same
    record and a difference between them would show as a change at the moment the store answered.
  - [x] **S4m — the live icon preview.** L1's `IconPreviewBox` + `ClassicCellIconPreview`, in every section: a real
        `AppCell` (or `AppRowCell`) at the **real cell size** the section computed, with the cell and the two icon
        guardrails outlined over it, tracking the sliders **live** rather than on release. It is what makes the icon
        controls legible — a fraction and two dp bounds do not tell you what you get in *this* cell, and which of the
        three is binding is the only question a user has while dragging. Five parts:
    - **`cellIconLayout`** (`core:designsystem/cell`) — where the icon lands in a cell of a given size, published so the
      guides can align with the cell without copying its constants. L1 restated the padding and label gap as
      `PREVIEW_CELL_PAD_DP` / `PREVIEW_LABEL_GAP_DP` under a "keep in sync" comment; this is the same correction `CellFit`
      made in the other direction. 5 tests, including the asymmetry worth pinning: the no-label branch does **not** clamp
      the icon to the cell, so an oversized guardrail is reported as the overflow it is rather than hidden.
    - **`onPreview` on both commit sliders**, forwarded by `IconSizingControls` as a whole previewed `IconSizing`. The
      dragged value was already tracked for the slider's own label, so handing it out cost nothing — this is L1's
      `onPreview`/`onChange` pair, with the *result* passed instead of a transform, for the reason `onChange` names a
      field rather than taking a lambda.
    - **`SamplePreviewApp`** — one real installed app to draw, plus L1's dice to change it (`data:apps` is a new
      dependency for this module, and an honest one: the preview's whole point is a real icon at a real size). Indexed
      rather than `shuffled()` in composition, which is what L1 did — its preview could change app mid-drag.
    - **Every section supplies its own cell size**, which is the part that could not be shared: home divides its area by
      the fitted grid, the dock divides its *height setting* by its rows, APPS branches on layout (a row for the list),
      and the folder asks `folderInnerSize` — the same sizer the overlay lays out with, since a folder's cell comes from
      a card, not a division.
    - **Two deliberate departures.** The guardrails are **grayscale** (solid = cell, dashed = upper, dotted = lower, with
      the caption naming them), because L1 colored them green and red and this palette reserves red for `error` — the
      same rule that put the grid editor's buttons on the edge they affect. And there is **no wallpaper behind it**: L1
      punched through to the live wallpaper (`BlendMode.Src` over a transparent window, the whole pane composited into an
      offscreen layer, overscroll disabled because a stretch breaks the punch), which needs `data:wallpaper` — the one
      piece of the preview genuinely blocked rather than reshaped, and the reason L1's sticky-header scaffolds
      (`IconDetailPortrait`/`Landscape`) are not ported either. The preview sits above the icon group in the section's
      ordinary scrolling column instead.
  - [x] **S4n — the fraction is spent once.** Found by using the preview S4m just built: dragging *icon size* on the two
        scrolling APPS grids shrank the cell but stopped moving the icon, which pinned itself to the lower guardrail
        below ~58%. Cause: `iconPercent` was applied **twice** on any grid whose height is derived — once to derive the
        height (`chrome + iconPercent × innerWidth`), then again by the cell, whose `iconArea` *is* that product, giving
        `iconPercent²` of the width. At 50% on a 4-column phone that is a 24dp icon in a row built for 41dp. L1 has the
        identical pairing (`gridCellHeightDp` × `resolveIconSize`) and the port inherited it; it was invisible until the
        defaults reached 100%, where both agree.
    - **`cellHeight` became `derivedCell`**, returning the height *and* the metrics to draw with (`iconPercent = 1f`), so
      the two cannot be separated — which is the actual fix: the fraction is spent on the height, and the icon then fills
      exactly what that bought. Its four callers (`AppsVerticalGrid`, `CategoryPage`, the category pager's drag proxy, and
      the APPS section's preview) all pass those metrics to their `AppCell`.
    - **The alternative was offered and declined**: dropping `iconPercent` from the derived height instead, which would
      have made the fraction never resize a cell anywhere — at the cost of up to 24dp of dead space per row, a slider
      useful only over 50–100%, no densifying, and reversing S4e's own rule.
    - **Where the preview needed it too**: `IconSizingPreview` takes `IconMetrics` rather than `IconSizing` precisely so a
      section can hand it the derived cell's metrics; otherwise the preview would draw an icon its surface does not.
    - A test pins the fixed point at four fractions: *the cell draws the icon its height was derived for*.
  - [x] **S4g — horizontal padding**, for all seven drawn grids: home's pager and dock, and the five APPS layouts.
        `GridBlueprint.horizontalPaddingDp` (0 by default, as L1's `GridDimensions` had it) with a per-slot × device
        override — a **fifth** `SurfaceMetrics` map, slot-keyed unlike `dockHeightDp`/`listRowHeightDp` because those
        name the one grid that *has* the measurement and every grid has edges. Three sliders reach seven grids: Home,
        Dock, and APPS following its layout chip. Two things worth keeping straight, both about where it is applied:
    - **Subtracted before anything is fitted.** Cells are divided out of the remaining width, so `CellFit` sees the
      reduced area on the surface *and* in the section — otherwise the editor offers columns the grid cannot draw. The
      APPS pager is where that would be more than cosmetic: its fit is the page capacity the store is paginated
      against, so the two would disagree about how many entries a page holds.
    - **Applied above the geometry publisher**, which is what makes drag and drop correct without a single adjustment:
      the drag surfaces measure *after* the caller's modifier, so the published `GridGeometry`, the registered drop
      zone, the edge-flip band and the drag proxy all describe the padded box. The margin is consequently in no drop
      zone, so a release there cancels — consistent with the free slack a surface long-press needs.
    - No companion placement write, unlike a resize: a margin removes no cell, so nothing is displaced. The columns it
      costs are re-reported on read and return when it narrows, which is the same clamp-never-write rule the counts
      already live by.
    - **Two editor bugs the margin exposed, both fixed in the same slice.** The preview is
      `fillMaxWidth().aspectRatio(r)`, so height is *derived* from the ratio — feeding it the narrowed width made the
      box taller for every dp of margin. And the inset was applied to the whole mockup, shrinking the companion zone
      too, so home's dock appeared to narrow because the pager's slider moved. L1 has neither: it keeps the screen's
      ratio and passes `insetFraction` to its `GridPreview` and none to its `NonGridPreview`.
  - [x] **S4g' — the grid editor, brought up to L1's.** Filed under S4g because the margin is what surfaced it. Four
        pieces, all ports:
    - **A fixed mockup size per posture** (240 / 140 / 360 / 280 dp, L1's numbers) with the width from the screen
        ratio, replacing `fillMaxWidth(0.62f)` — a settings pane is half a tablet and all of a phone, so a fraction
        gave a different preview in each.
    - **L1's three button arrangements**, including the columns-only rails and the no-button frame. The earlier cut
        centered a −/+ pair per edge, on the grounds that a grayscale palette cannot tell add from remove by color;
        the premise was right and the conclusion wrong, since in L1 the *position* encodes the action and the color
        was reinforcement.
    - **A `preview` slot with a mockup per APPS layout** — `ReflectivePreview` (cells at their derived aspect,
        clipped at the fold) for the scrolling grids, the even lattice for the pagers and the card grid, header + tabs
        for the category pager, and `LanePreview` for the list.
    - **`AppsChrome`, a third slice**: `SearchPlacement` (built in B0, unconsumed until now) plus the tab bar's
        `VerticalEdge`, whose KDoc named this consumer. **The one setting whose only consumer is a preview** — search
        and the tab bar are unbuilt on the surface — which is a deliberate exception to "no model in a vacuum", taken
        because a preview is a real consumer with a real question and L1's editor draws both. Its search default is
        `Hidden` where L1's is `TOP`, since a preview must not draw a feature the launcher has not got.
- [x] **S5 — `data:wallpaper` + effects** — *done.* L1 spends ~1,730 LOC here (a 381-LOC repository, `Blur.kt`, a
      561-LOC `WallpaperTab`, a crop screen, a capture screen, a live-wallpaper service and an effects tab), which is
      more than one slice can carry, so it is broken up by **what a user can do when it lands**:
  - [x] **S5a — the module, and the static image it owns.** `data:wallpaper`: its own DataStore blob, a JPEG under
        `filesDir/wallpaper`, decode-and-sample from a `Uri`, center-crop and scale to the screen, and
        `WallpaperManager.setBitmap` on HOME / LOCK / BOTH. Three decisions worth keeping straight:
    - **It keeps its own bookkeeping**, rather than "borrowing settings to persist path pointers" as the section below
      originally said it would. S0 had already ruled that L1's `appliedSingle` / `singleDirty` /
      `appliedSystemWallpaperId` are *not* preferences, and a module that owns files may as well own the pointers to
      them; nothing else has to be running for a wallpaper to be read. The **effect params still go to `data:settings`**
      when they land, because those genuinely are preferences.
    - **Two state fields where L1 had six.** L1's `WallpaperState` juggled two image sets (`single` + `rotate`) and kept
      a **snapshot copy** of whichever was applied, so a frosted backdrop could go on sampling the real system
      wallpaper. Neither exists yet, so this holds the chosen image and `appliedSystemId` — not a boolean, because the
      id is also how a wallpaper set *outside* the launcher gets spotted, which is what L1 stored it for.
    - **One structural fix**: L1's repository did its read-modify-write **outside** any transaction (the lost-update
      race this plan's smell list already named). `updateState` does it inside `edit`.
  - [x] **S5b — the wallpaper section.** Preview, "Choose image" (`PickVisualMedia`), and Apply / Re-apply with L1's
        home/lock/both menu — the vertical that makes S5a visible, and the first row of the **Personalization** group,
        which is why that heading is back and the sections now sit in two named groups. Four things worth keeping
        straight:
    - **The preview is screen-shaped**, because the stored file already is: `setImage` crops and scales to this screen,
      so a preview at any other ratio would show a crop the device will never display. Same argument `GridEditor`'s
      preview makes for taking the window's ratio rather than a square. It measures the **whole** window, insets
      included — every other section measures the *usable* area, and the difference is that those size things a user
      reaches while this sizes something they only look at.
    - **One button and a menu, where L1 drew a `SplitButtonLayout`.** Both halves of L1's ran `expanded = true`, so the
      split was decoration over a single action: applying always asks *where*, and there is no plain apply to run
      without the menu.
    - **A `busy` flag is L2's own**, not a port. L1 never needed one because its picker went to the crop screen and the
      work happened behind that; here the picker returns straight to the section, so a decode-and-scale of a large
      photo would otherwise be a second of nothing happening.
    - **`decodePreview` stays unused until S5c.** It exists to show a picked image *before* anything is written, which
      is the crop screen's job; with no crop screen a pick writes immediately and the preview comes from `loadImage`.
      L1's three browse rows ("My wallpapers", "Backdrops (By Unsplash)", installed live wallpapers) are also absent —
      the first two are empty-state hints for a source that does not exist, and the third is S5e.
    - **Reversed after S5e, at the author's call: the whole of L1's layout is now ported.** The section is a two-page
      mode pager over the three shelves, which is what S5b's vertical had flattened. See S5g below.
  - [x] **S5c — the crop screen.** L1's pan/zoom over the decoded bitmap, passing a `NormalizedCropRect` so `setImage`
        stops center-cropping — the stand-in that slice's KDoc called a stand-in is now gone, and nothing in the module
        invents a rectangle. Separate because L1 keeps it a separate screen too, and it is the **first destination a
        feature module declares** (`WallpaperCropRoute` in `feature:settings`, mapped by `app`) — the pattern
        `LauncherRoute`'s KDoc blesses and L1 got wrong by putting every route in its navigation module.
    - **A destination, where a section is a pane.** Full-screen, transient, and back out of it means "not that image"
        rather than "close this detail" — which is what a back-stack entry is for, and what the sections are not.
    - **The viewport is the output.** The screen frames against the whole window (a wallpaper sits under the bars) and
        passes that same size as the size to store at, so the rectangle and the result share one coordinate space.
    - L1's arithmetic is kept exactly: the cover scale as both the starting scale and the pinch floor, the
        centroid-anchored zoom, and the offset clamp. Together they make the image impossible to frame badly — no gap,
        and no crop outside the picture — which is worth more than any chrome a crop screen could grow.
    - `decodePreview` is finally read, which is what it was built for; `cropAndScale` replaces `centerCropTo` and
        clamps each edge against the opposite one, so a rectangle a rounding error out of range yields a small crop
        rather than an exception out of `Bitmap.createBitmap`.
    - **Not carried:** L1's `forRotate`/`landscape` pair, which pinned the activity's orientation while framing the
        second image of a rotating wallpaper. That is S5e's, and it arrives with the feature that needs a second image.
  > **Reordered after S5c: every *source* lands before the effects that read them.** The three slices below were
  > sequenced effects-first, on the grounds that capture has no other consumer. That reads the dependency the wrong way
  > round for the work that matters: an effect has to answer "which image do I sample?", and L1 answers it with an
  > `effectRef` that switches on *which source is applied*. Designing that against one source and then adding two more
  > means re-answering it twice, in the slice with the most surface area. Sources first means the effect params are
  > shaped once, against the set they will actually serve.
  >
  > The cost is stated rather than hidden: **capture lands before anything reads it**, which is the one place this plan
  > builds a producer ahead of its consumer. That is a deliberate exception to "no model in a vacuum", taken because
  > the alternative is worse, and it is bounded — a capture is visible in the section's preview the moment it is taken.

  - [x] **S5d — capture.** L1's effect-only source: a screenshot taken with the launcher's own UI hidden, which never
        becomes the system wallpaper. (Was S5e.) The flow is L1's because there is no other — **no API takes a
        screenshot for an app** — so the launcher hides itself, asks, and watches `MediaStore` for what arrives.
    - **The watch moved into `data:wallpaper`** (`newGalleryImages()`, a `ContentObserver` + query as a `callbackFlow`).
      L1 kept both in the screen; a system read is not a composable's to hold. It cannot tell a screenshot from any
      other new image — neither could L1's — which is why the screen asks for one *now* and takes the first emission.
    - **`WallpaperSource` is the model half**: `PICKED` or `CAPTURED` on the stored image, with `apply` declining a
      capture. That is L1's own branch (`applySingle` checks the source), and it is the reason the section replaces the
      Apply button with the reason rather than leaving it dead.
    - **One write path for both sources.** A capture is already the size and shape of the screen, so it passes
      `NormalizedCropRect.Full` — the caller that value was declared for — and `setImage` takes the source as an
      argument instead of growing a near-duplicate method.
    - **The window has to show the wallpaper, and L2's did not.** L1's launcher window already did; L2's theme was
      opaque, so a screenshot taken here would have been a picture of this app's own background. The screen first did
      it at runtime (`FLAG_SHOW_WALLPAPER` + a transparent background, reverted on dispose); **the launcher theme now
      carries it instead** — the platform's `Theme.Wallpaper` recipe — so the screen only hides the system bars. That
      is the right place for it: capture was one of three things waiting on a window that shows the wallpaper, the
      others being the icon preview's `BlendMode.Src` punch-through and the frosted backdrop.
  - [x] **S5e — rotate, and the live wallpaper.** L1's per-orientation pair, rendered by the launcher's own
        `RotatingWallpaperService`, and the one that makes "the wallpaper" stop being a single image — which is why the
        effects wanted it first. Five things worth keeping straight:
    - **A service is the only way.** Android has no per-orientation static wallpaper: `setBitmap` takes one image and
      the system crops it whichever way the phone is held, so showing a different picture in landscape means being the
      thing that draws. L1 reached the same conclusion and wrote the same engine.
    - **It lives in `data:wallpaper`, not the settings feature.** L1 put it in `feature:settings`, which left its data
      layer unable to name its own service — `applyRotateWallpaper` built the `ComponentName` in the UI. The module that
      owns the files owns the renderer; the section only launches the system chooser at it.
    - **Which wallpaper is active is asked, never stored.** L1 latched it (`appliedMode` via `markRotateApplied`) and
      then needed `reconcileLiveWallpaper` on every resume to repair the latch. `WallpaperManager.wallpaperInfo` answers
      it directly, so `isRotatingActive()` reads it: no cache, no latch, no reconciler — smell 7 ("derived state
      persisted") declined rather than ported. The section still refreshes on resume, because the confirmation happens
      while it is stopped, but it refreshes a *read*.
    - **`CropTarget` replaces L1's `forRotate` + `landscape` booleans**, which between them could express a state that
      does not exist. One value names the three slots and answers what the crop screen cannot otherwise know: the shape
      to frame against, and the size to store at.
    - **The landscape half is framed letterboxed, not by pinning the activity** (the piece S5c deferred here). The frame
      decides the *shape* and the target screen decides the *resolution*, so a landscape image framed on an upright
      phone is still stored full size — which is what makes turning the device unnecessary. `outWidth`/`outHeight` are
      therefore no longer the measured viewport, and S5c's "the viewport is the output" is now "its shape".
    - **~~Not carried: L1's browser of *installed* live wallpapers.~~ Reversed in S5g** — it is a duplicate of a system
      screen, but it is also the shelf the future *sources* will live on, and the author took it. Our own service is
      filtered out of it, which L1 had no reason to think about.
    - Also improved on the way: the engine keeps **one** decoded bitmap rather than L1's two (only one can be drawn, and
      the wallpaper process is kept alive behind the home screen), and the merge is explicit — setting one half leaves
      the other alone, since a pair is assembled one orientation at a time.
  - [x] **S5g — the section takes L1's layout.** Out of order, because it is a rework of S5b rather than new ground:
        the flat vertical became L1's **two-page mode pager** (Single / Rotate — one shared `WallpaperModePage`
        anatomy, where L1 hand-wrote both) over the **three browse shelves**, installed live wallpapers included.
        Paging the modes is the part that carries meaning: only one of them is ever the wallpaper, and two stacked
        headings do not say that. Three things it still does not copy — the `SplitButtonLayout` (both halves ran
        `expanded = true`, so one button and a chevron), the band-shaped preview (the stored file is cropped to *this*
        screen, so the picture keeps the screen's ratio inside L1's band), and **our own rotating service in the
        live-wallpaper shelf** — it genuinely is one, but the rotate page owns it and a card would be the one route
        with no "has an orientation yet" guard.
  > **S5f split into three when it was costed.** As one slice it is `BackdropEffect` + a settings slice + `Blur.kt` + a
  > 350-line `Modifier.Node` + an AGSL shader + a settings tab + three consumers — well past what one review can carry,
  > and the three pieces have genuinely different blockers.
  - [x] **S5f-1 — the brightness signal, and the shell's `darkTheme`.** L1 has none of this: it themes the launcher
        from the system's dark mode, so the luminance analysis is L2's own idea. `WallpaperRepository.brightness` is a
        `Flow<WallpaperBrightness>`; `LauncherShell`'s hardcoded `darkTheme = true` is gone.
    - **It needed neither half of `Blur.kt`, which this plan had assumed it would.** See the revised `Blur.kt` note
      above: `getWallpaperColors` already answers it over the wallpaper *actually displayed*, and `dominantColor` is a
      saturation-weighted statistic that would have answered a different question. Taking this piece first is what
      surfaced that — the alternative was porting 112 LOC of image processing to be used wrongly by its first caller.
    - **Ask the system; read our own file only with proof.** The fallback (API 26, or a live wallpaper publishing no
      colors) is gated on `appliedSystemId` still equaling the live wallpaper id — the second job `WallpaperState`
      reserved that field for, now doing it. Without the gate, "we have an image stored" would be treated as evidence
      about a wallpaper another app set. Otherwise `DARK`: the old hardcoded value, and the safer miss.
    - **The cut is at relative luminance 0.179**, where the WCAG contrast ratios against black and white cross — a
      derivation rather than a taste value.
    - **`RotatingWallpaperService` publishes its colors** (`onComputeColors` + `notifyColorsChanged`). A live
      wallpaper is the one kind the system cannot analyze for itself, so a silent service starves every consumer of
      `getWallpaperColors`, status-bar icon contrast included. Answering it means our own pair takes the same path as
      every other wallpaper instead of a special case reading our files behind the system's back. L1's published
      nothing, and had no caller that noticed.
  - [x] **S5f-2 — the frosted backdrop.** `BackdropEffect` as a `data:settings` slice, `Blur.kt`'s box blur, L1's
        `BackdropState` / `LocalBackdrop` / `wallpaperBackdrop` node in a new `core:designsystem/backdrop`, and the
        folder overlay as its first frosted surface. Six things worth keeping straight:
    - **The model was already there and mostly right.** B0's sealed `BackdropEffect` needed `@Serializable` +
      `@SerialName` (short names, so the discriminator in a user's blob survives a class rename) and a `blurStrength`
      property, so the one question every consumer asks it is not a `when` re-written per caller. See the note in
      "Target shape" above — this is the worked example of a `core:model` type meeting its first caller.
    - **All four effects carry the wallpaper's hue — the deliberate exception to the monochrome palette rule.** That
      rule makes *chrome* grayscale so the wallpaper and the icons carry the color; an effect the user selects, whose
      subject is the wallpaper, is not chrome. L1's two-stage blend is ported exactly: a wallpaper tone of
      `lerp(surfaceVariant, accent, 0.30)`, then `lerp(White|Black, tone, 0.35)` for the blurs and the tone outright
      for Material You. The 35% nudge is not decoration — a neutral film over a blurred photograph reads as dirty, and
      that is what it fixes. **Recorded as a reversal**: the first cut of this slice dropped the hue everywhere and
      left `MaterialYou` unrenderable on palette grounds, and the author reversed it mid-slice.
    - **Both halves of `Blur.kt` crossed, and the accent does not come from the OS palette.** L1 read
      `colorScheme.primary` above API 31, which worked because its launcher ran a normal M3 dynamic scheme; L2 bridges
      a **monochrome** scheme, so that expression returns gray and the dynamic-color route is closed by a decision
      made long before this. `accentColor` reads the wallpaper instead — `WallpaperColors.primaryColor` on API 27+,
      `dominantColor` over our own file below it. Note the trap S5f-1 nearly walked into: `dominantColor` is
      saturation-weighted, so it is the right statistic for "what color?" and the wrong one for "how bright?".
    - **`Blur.kt` went into `data:wallpaper`, not "beside the graphics code".** That instruction was written when the
      wallpaper lived inside `data:settings`, where image processing had no business; `data:wallpaper` exists *because*
      it decodes bitmaps, and `cropAndScale` is in the file next door. See the revised note above.
    - **One answer to "which image does an effect sample?"**, for all three sources at once — which is what the
      sources-before-effects reordering was for. Rotating active → that orientation's half; a capture → always, since
      it *is* a picture of what is displayed; a picked image → only while `appliedSystemId` matches the live wallpaper
      id; otherwise nothing. **That third test replaces L1's `appliedSingle` snapshot** — a whole second copy of the
      file, kept so an unapplied pick could not desynchronize the backdrop — and it answers one more question the
      snapshot could not: a wallpaper set outside the launcher makes the ids differ, where the snapshot went on
      claiming to match. Same gate `brightness` uses, so the two readings cannot drift.
    - **A flow, where L1 re-read on recomposition.** Two of those four answers change with no action from us (the
      system's own wallpaper settings, the live-wallpaper chooser), so a one-shot read can show a blur of a wallpaper
      that is no longer there. It shares `brightness`' change signal.
    - **Provided at the shell**, the theme's own boundary — L1 provided it inside `HomeScreen`, which is why its
      settings feature needed a second provider. `LocalLockedBackdrop` is **not** carried: L1's second backdrop exists
      for a popup menu and a widget picker that L2 does not have.
  - [x] **S5f-3 — liquid glass, and the effects section.** The AGSL shader and the seventh section, which is also the
        slice's first writer. Four things worth keeping straight:
    - **The shader is a port with an attribution that must not be dropped.** Its refraction maths — rounded-rect SDF,
      analytic gradient, circular rim falloff, chromatic split — is adapted from Kyant's `AndroidLiquidGlass`
      (Apache-2.0), as L1's own comment records. It samples the same crop rectangle the blur path does, so switching
      effects does not shift the picture, and the compiled shader plus its bound bitmap live on the draw node because
      a drag re-sends uniforms every frame while only the uniforms change.
    - **API 33+, and the chip is hidden rather than disabled below it.** An effect that silently degrades to a plain
      blur is worse than one not offered; the renderer still checks, because a stored preference outlives the device
      it was chosen on.
    - **A write is a whole-value write**, which is the sealed type's bill finally coming due: the parameters that
      apply depend on the variant, so switching between variants discards the previous one's. Within a variant
      nothing is lost. Stated on `setBackdropEffect` rather than worked around — an in-memory stash would survive a
      chip tap and not survive leaving the screen, which is worse than a rule.
    - **`BackdropOption` is the chooser's vocabulary and never reaches storage.** Light and dark blur are two chips
      and one variant with a `tone`; putting that split in the section is what keeps B0's enum-plus-bag collapse from
      being quietly undone by a chip list.
    - **No live preview**, unlike every surface section: an effect previews a frosted surface over the wallpaper, and
      the settings pane has no backdrop by design. L1 had none here either.
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
| Wallpaper + blur | out of `data:settings` → `data:wallpaper` (B7b), with its **own** store rather than settings' | *Not `data:settings` at all* |
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
   derived from the extent, then that rows alone were. Neither survived contact with the behavior.)* A cell is
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

Recorded so the port is checked against them rather than trusting memory. All from L1's `data/settings`.

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
