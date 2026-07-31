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

| waiting consumer | file | what it needs |
|---|---|---|
| `LauncherShell(sideSurfaces = emptyMap())` | `feature/shell/LauncherShell.kt` | per-edge binding → **no edge is swipeable today** |
| `LauncherShell` `darkTheme = true` | same | wallpaper-brightness signal |
| `AppsScreen(layout = VERTICAL_LIST)` | `feature/apps/AppsScreen.kt` | which APPS layout, per binding |
| `DockHeight = 96.dp` | `feature/home/HomeScreen.kt` | dock extent (rows derive **from** it, not the reverse) |
| home padding | — (deliberately absent) | horizontal padding, added *with* the setting |
| `RowHeight = 56.dp` | `AppsVerticalList.kt` | list row height |
| `CellHeight = 96.dp` ×2 | `AppsVerticalGrid.kt`, `AppsCategoryPager.kt` | grid cell height |
| `CardColumns = 2` + spacing/padding | `AppsCategoryCard.kt` | card grid geometry (device-blind today) |
| 5 × per-surface `IconMetrics` | apps list/grid/pager/category/card, home | icon size, label scale, show label/icon |
| one-finger swipe policies | `LauncherShell.bindingFor` | derived from HOME's + the surface's layout |
| home orientation | not built | landscape support |

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

### Not `data:settings` at all

- **`WallpaperRepository` (+Impl, ~380 LOC)** — a bitmap/file/`WallpaperManager` service that merely *borrows*
  settings to persist path pointers. Belongs in its own `data:wallpaper`.
- **`internal/Blur.kt` (112 LOC)** — raw `IntArray` box-blur and dominant-colour extraction. Pure image processing;
  belongs beside the icon/graphics code.

Both are listed under B7 in [REWRITE_PLAN.md](REWRITE_PLAN.md) — **that listing is wrong and this plan supersedes
it.** Wallpaper is also what the shell's `darkTheme` placeholder is waiting on, so it is a real dependency, not a
tidy-up.

---

## Phases

Each slice is a full vertical: storage → repository → ViewModel → screen → the placeholder it retires. That way
every phase ends with something visibly working on device, and no slice is written before it has a consumer.

- [ ] **S0 — Subtract.** Decide-and-record what does *not* come across: layout content (already Room), `setupComplete`,
      `GridConfigKind`/`gridConfigKind()`/`GridDefaults`, the legacy flat twins (`DockSettings.visualCols/visualRows/heightDp`,
      `WidgetAreaSettings.*`, `DrawerSettings.columns`, `GridSettings.horizontalPaddingDp`), and the two empty sections
      (THEME and GESTURE are 12-LOC "Coming soon" placeholders occupying enum values and list slots — don't port empty
      destinations). No code; this is the shopping list the rest of the plan is scoped against.
- [ ] **S1 — `data:settings` foundation.** DataStore + slice-blob codec + `SettingsRepository` with per-slice flows +
      defaults-from-blueprint. Built *with* S2's slice as its first consumer, not before it. Unit-testable without
      Android, like `AppCategorizer` and the `data:layout` arithmetic.
- [ ] **S2 — Surface register** (7 knobs: `HomeLayout`, per-edge `Surface`, `SurfaceTransition`). **Lead with this**:
      smallest schema, model fully exists, and it retires the two placeholders with the biggest visible payoff —
      binding APPS to an edge is what makes it swipeable at all, and it gives `AppsLayout` a real owner. Also the first
      settings ViewModel, so it sets the pattern.
- [ ] **S3 — Icon metrics** (6 knobs × slot × device configuration). `IconLayoutSettings` ≡ `IconMetrics`, and
      `IconMetrics.of()` was written for this. Retires five per-surface `IconMetrics` placeholders. Keying is settled
      (see "Grid + icon config" above), so the work here is the `GridSlot` id on `GridBlueprint`, the sparse-override
      merge, and the resolved `iconMetrics(slot, device)` flow — the same three pieces S4 then reuses for grids.
- [ ] **S4 — Surface geometry** (dock extent, home padding, grid cols/rows, list row height, grid cell heights, card
      columns). Retires the remaining placeholder constants. Reuses S3's override machinery for `GridOverride`, adds the
      `editRange` write-side clamp (the first consumer for `GridEditRange`), and must respect the dock's stated
      dependency direction: **extent is the setting, row count derives from it**.
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

## Open decisions

Worth settling before S1, because each changes the shape rather than the amount of work.

1. **Are settings sections navigation destinations?** *Recommendation: yes, one `NavKey` per section.* L1's sections
   were *not* back-stack entries, which cost it two incompatible back mechanisms stitched together by hand
   (`if (selected != null) closeDetail() else navigator.goBack()`), section state preserved through a hand-written
   `Saver`, and a two-pane mode that silently dropped the "close detail" concept. Nav3 makes a key cheap. Keys would
   live in `feature:settings` (not `core:navigation` — that boundary is deliberate and already documented), with `app`
   mapping them; cross-feature deep-links wait until something needs one.
2. **Where does wallpaper live?** `data:wallpaper` (recommended) vs staying inside `data:settings` as B7 says. This
   plan assumes the former; it needs a nod because it edits the rewrite plan's module map.

---

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
