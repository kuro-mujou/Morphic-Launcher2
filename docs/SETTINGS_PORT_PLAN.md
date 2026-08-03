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
| `DockHeight = 96.dp` | `feature/home/HomeScreen.kt` | dock extent (rows derive **from** it, not the reverse) | todo S4c |
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

### Settings UI: every section is its own destination *(settled)*

**One `NavKey` per settings section**, not one `SettingsRoute` carrying a section enum.

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
- [ ] **S4 — Surface geometry** — *in progress.* Sub-steps, because they turned out to have different shapes:
  - [x] **S4a — grid dimension store.** `GridOverride` (sparse per axis, scrolling grids ignore a stored row count) and
        the `editRange` **write-side clamp** — the first consumer of `GridEditRange`. Minima only: maxima are a runtime
        question. 8 tests.
  - [x] **S4b — `resolveBounds`.** `core:designsystem/grid/CellFit.kt`, ported from L1's `CellFit`, which had *two*
        definitions of "smallest usable cell" (one wrong) and its own copy of the cell padding. Now one formula, reading
        `IconLabelCell`'s real constants. 13 tests.
  - [ ] **S4c — the dock.** Fully specified below; nothing built.
  - [ ] **S4d — the rows/cols editor.** Unblocked by S4b *except* for one input: what **area** a settings screen
        measures against. It is not the surface, so it cannot measure home directly — L1 derived it
        (`homeGridArea(window, insets, dockVisible, dockThickness)`), and that derivation needs the dock extent, hence
        S4c first. For the APPS grids the window minus insets is already exact.
  - [ ] **S4e — cell and row heights** (`RowHeight`, the two `CellHeight`s). Needs a new blueprint field; `core:model`
        is a JVM library, so `Int` dp rather than `Dp`.
  - [ ] **S4f — horizontal padding** for every layout, home's included. Deferred by decision (see the dock, rule 5).
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
| Settings sections | one `NavKey` each, declared in `feature:settings` | *Settings UI* |
| Wallpaper + blur | out of `data:settings` → `data:wallpaper` (B7b) | *Not `data:settings` at all* |
| Dock rows/cols | **derived** from extent and cell size, never stored | *The dock (S4c)* |
| Dock height cap | a fraction of the current screen height, so it changes with orientation | *The dock (S4c)* |

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
   being one dp constant.
2. **Cell size is calculated from the icon and the text**, so the icon/text min-max rail (S3's `IconSizing`) feeds the
   dock's cell size directly. This is the same inverse `resolveBounds` already implements — `minCellWidthDp` /
   `minCellHeightDp` in `core:designsystem/grid/CellFit.kt`.
3. **Rows and columns are both derived** — dock width ÷ minimum cell width, dock height ÷ minimum cell height. Neither
   is a stored number, which is why `updateGrid` should never be offered for `GridSlot.HOME_DOCK`.
4. **Shrinking swallows the bottom row.** When a new height no longer fits the current row count, the dock drops its
   bottom row and the remaining rows grow to fill the new extent — rather than squashing every cell. Expanding is the
   same rule in reverse.
5. Horizontal padding for every layout is a **separate, later** decision (it is also home's missing `padding`
   placeholder). Deliberately not folded in here.

**Consequence worth planning for:** rule 4 removes cells that may hold items. A swallowed row's occupants need somewhere
to go, which is `data:layout`'s reflow (`GridReflow`) rather than a settings concern — but it is the settings write that
triggers it, so the two have to meet. That is the first piece of S4c to design, not the slider.

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
