# Morphic Launcher 2 — Rewrite Plan (learning + cleanup build)

**Goal:** rebuild Morphic Launcher from scratch, bottom-up, as a clean **refactor** (not a re-type) of the
original at `../launcher` — the reference / answer key, never deleted.

> Working model, refactor mandate, docs/KDoc convention, and the domain concepts (surface taxonomy, layout
> persistence) live in [CLAUDE.md](../CLAUDE.md). This file holds **only the plan**: phases, the build map,
> and build order.

## Phases

- [x] **P0 — Scaffold** multi-module structure + `build-logic` + version catalog (Claude set this up).
      Blank `app` boots as a normal app (`MAIN`/`LAUNCHER`), showing a hello screen. appId `inkspire.morphic.launcher`.
- [ ] **P1 — Core**
  - `core:model` — plain Kotlin data classes (the shapes: app, icon, layout, …)
  - `core:database` — Room entities + DAOs + database
  - `core:common` — shared utilities / coroutine + DI plumbing
- [ ] **P2 — Data / repository layer** (`data:*`) — query installed apps (PackageManager), icon parsing,
      persistence; expose repositories the UI will consume.
- [ ] **P3 — UI walking skeleton** — wire all nav routes to **dummy screens**; navigable end-to-end.
- [ ] **P4 — Real surface frames** — home surface + side surfaces (still without live data).
- [ ] **P5 — Gestures** to navigate between home ↔ side surfaces.
- [ ] **P6 — App list** brought into home + surfaces (connect P2 data to P4 UI).
- [ ] **P7 — Wire gestures** with apps / surfaces (launch, drag, etc.).
- [ ] **P8 — Settings** screen — planned in [SETTINGS_PORT_PLAN.md](SETTINGS_PORT_PLAN.md) (with B7).
- [ ] **P9 — Become a launcher** — add the `HOME` intent category to the manifest (the final flip).

## Module → convention plugin (reference)

| Module | Plugin(s) |
|---|---|
| `core:model` | `jvm.library` + serialization |
| `core:common` | `android.library` |
| `core:database` | `android.library` + `android.room` |
| `core:designsystem` / `core:icon` / `core:navigation` | `android.library` + `library.compose` (+ serialization for icon/nav) |
| `data:*` | `android.library` (+ serialization for `icons`) |
| `feature:*` | `android.feature` (auto-adds core:model/common/designsystem + Compose/Koin/coroutines/lifecycle) |
| `app` | `android.application` + `application.compose` |

## Bottom-layer build map (`core:*` + `data:*`)

Everything below `feature:*`, in rough **dependency order** (each stage may depend on the ones above it).
Class lists are taken from the Launcher 1 reference at `../launcher`. Legend: ⚠️ = real logic — understand
before porting; 🔧 = known smell — refactor, don't copy. Build a module only when a consumer needs it (the
"no model in a vacuum" principle); this map is the *shape*, not a "write it all now" list.

### B0 — `core:model` ✅ (LayoutChange deferred to B8)
Migrated + refactored all L1 model. Key cleanups (don't-port-verbatim outcomes):
- **Surface family** — one suffix per concept (`Surface`/`HomeZone`/`…Layout`/…). `SideSurfaceKind`→`Surface.APPS`;
  `AppDrawerLayout`+`AppLibraryLayout`→one `AppsLayout`; `HomeSurfaceKind`→`HomeLayout` (the pager+dock /
  list+widget combos, illegal pairings unrepresentable); old `Surface`→`HomeZone`; new `HomeEdge` (4 edges).
- **Placement** — `GridRect`+`AppPosition`→`GridPlacement`; `GridEdge`→`GridEditorEdge`; `HomeGrid`/`DockGrid`
  objects→`GridBlueprint` values.
- **Containers** — unified item alphabets: `GridItem` (on-grid peers) + `IconItem` (app|folder, replaces the
  duplicate `DrawerSlot`/`IconContainerItem`); `Folder`/`IconContainer`/`WidgetContainer`/`WidgetInfo` all `@Serializable`.
- **Apps chrome** — `TabBarPosition`+`SearchPosition` (both were wrong `{TOP,BOTTOM,HIDDEN}`) → `VerticalEdge`
  (tab bar, no HIDDEN) + sealed `SearchPlacement` (Pinned/InHeader/Hidden).
- **Backdrop** — `WallpaperEffect`+`WallpaperEffectParams` → one sealed `BackdropEffect` (+`BlurTone`); renamed
  off "wallpaper" (it's the frosted backdrop) and folded the flat params bag into per-variant tunables.
- **Dropped**: `LayoutCombination` (obsolete after the merges). **Deferred**: `LayoutChange` → `data:layout` (B8).

### B1 — `core:common` — DI + coroutine plumbing (needed early, by almost everything)
- `dispatcher/AppDispatchers` — injectable IO/Default/Main dispatcher set (makes coroutines testable).
- `scope/ApplicationScope` — app-lifetime `CoroutineScope`.
- `di/CommonModule` — Koin module wiring the above.

### B2 — `core:database` — Room persistence (depends on `core:model`)
- `LauncherDatabase` (the `@Database`) + `di/DatabaseModule`.
- Converters (3): `ComponentKeyConverter`, `OrientationConverter`, `SurfaceConverter`.
- Entities (13) + DAOs (14): App, Folder(+Item), IconContainer(+Item), Widget, WidgetContainer(+Item),
  IconOverride — each with an `*Entity`, a `*PlacementEntity`, and matching DAOs.
- 🔧 The **item + placement + container** shape repeats across app/folder/icon/widget — find the shared
  pattern before copying four near-identical placement entities/DAOs.
- 🔧 With `GridRect`+`AppPosition` → `GridPlacement` merged, every `*PlacementEntity` should map to that one
  placement shape (page/row/col/spans) — reconcile columns to it, don't reproduce two coordinate types.

### B3 — `core:icon` — icon model + rendering (depends on `core:model`; part Compose + serialization)
- Types: `IconId`, `IconShape`(+`IconShapes`), `IconStyle`, `IconSkin`, `IconOverride`, `IconSurface`.
- Render ⚠️: `render/IconRenderer`, `render/IconRenderManager`, `render/IconCache`; `parse/DrawableParser`,
  `parse/ParsedIcon`; `layer/IconLayerSet`, `layer/IconStyleLayers`; `source/RawIconSource`.
- Compose: `compose/LauncherIcon`, `compose/IconSkinLayer`, `compose/SkinImageCache`.
- 🔧 Caching appears in three places (`IconCache`, `SkinImageCache`, `IconRenderManager`) — check for one
  cache abstraction before porting all three.

### B4 — `core:designsystem` — Compose components (depends on `core:model`, `core:icon`)
Large; port per-component as feature screens need them, not up front. Groups:
- adaptive: `DeviceConfiguration` (✅ already ported here as the detection half).
- grid ⚠️: `grid/CellFit`, `grid/GridSpan`, `grid/LauncherGrid` — the render-time cell math. 🔧 `GridSpan`
  overlaps the `GridPlacement`/`GridConfig` concepts now in `core:model`; decide what stays render-only.
- pager ⚠️: `InfiniteLauncherPager`, `LauncherPagerSwipe`, `LauncherState`, `PageTransformScope`.
- drag ⚠️: `DragLift`, `DropFootprint`, `FloatingDragIcon`, `SurfaceDragState`, `SurfaceExtractEngagement`.
- cells/components: `AppCell`, `IconLabelCell`, `FolderCell`, `IconMetrics`, `ItemInteraction`. ✅ `AppListColumn`
  landed as **`AppRowCell`** — the row only, since L1's version was a list *and* a row *and* the alphabet strip
  *and* four letter-indexing helpers in one file; the list is `feature:apps`, the strip is still to come.
- blur: `Backdrop`, `IconSkinBackdrop`, `LiquidGlass`. theme: `LauncherTheme`, `Typography`, `WallpaperColorScheme`.
- misc: folder (`FolderGrid`/`FolderPager`/`FolderAppPickerSheet`), filter (`Alphabet*`), search/field, popup,
  indicator, dialog, settings rows, insets, `topaction/TopActionZone`.
- **Grid editors** (`HomeGridEditor` etc.) live in `feature:settings`, not here — when ported, drive them off
  `GridBlueprint` + a `core:designsystem` `resolveBounds(blueprint, area, iconRail)` (the resolver we scoped).

### B5 — `core:navigation` — `Navigator`, `Routes` (small; depends on `core:model` surfaces, G4).

### B6 — `data:apps` — installed-app source + categorisation (depends on model, common)
- `AppRepository`(+`Impl`), `LauncherAppsWrapper`, `AppShortcut`, `mapper/AppInfoMappers`, `applist/AppListPipeline`.
- category ✅: `AppCategorizer`, `CategoryHeuristics`, `CategoryMapping`, `AssetCategoryMapping` (+ the curated
  `assets/app_categories.json`, 188 packages) — done, with the two fixes L1's `POST_FIX_CLEANUP_PLAN` targeted
  baked in: the classifier is **one app in, one category id out** (L1's `categorize` also did grouping, ordering
  and display resolution, which here belong to the category store), and the curated table is an **injected**
  `CategoryMapping` rather than a `Context` read inside the classifier — which is what makes the priority chain
  unit-testable without Android (11 tests).

### B7 — `data:settings` — preferences + presets (depends on model, common)
**Scoped in detail by [SETTINGS_PORT_PLAN.md](SETTINGS_PORT_PLAN.md)** — read that before starting; it supersedes
this entry, which is only the summary.
- `SettingsRepository`(+`internal/SettingsRepositoryImpl`), `LauncherSettings`, `internal/Preferences`,
  `IconStyleMapper`, preset (`Preset`/`PresetRepository`/`PresetTemplates`).
- 🔧 `LauncherSettings` is a god-object and `GridConfigKind`/per-surface knobs live here — a lot of this is now
  expressed by `GridBlueprint` in `core:model`; move the static grid facts out and slim the settings blob.
- 🔧 A third of L1's blob is not settings at all: `drawerOrder`/`drawerPages`/`categories`/`categoryAssignments`
  are **layout content**, which L2 already keeps in Room (B8's `apps_pager_item` / `category` / `category_item`).
  Do not port them.
- **`WallpaperRepository`(+`Impl`) and `internal/Blur` moved out of this module — see B7b.** L1 filed them here
  because they *borrow* settings to persist path pointers; neither is a preference.

### B7b — `data:wallpaper` — wallpaper source, crop, and system apply (depends on model, common, settings)
- `WallpaperRepository`(+`Impl`) ⚠️ — a bitmap/file/`WallpaperManager` service: decode + crop + scale from a `Uri`,
  own JPEGs under `filesDir/wallpaper/`, set the system wallpaper on HOME/LOCK/BOTH, load the backdrop blur and the
  dominant colour. It reads/writes `WallpaperState` through `data:settings`, which is a dependency, not a home.
- 🔧 L1's `internal/Blur.kt` (raw `IntArray` box-blur + saturation-weighted dominant colour) is image processing, not
  wallpaper *or* settings — land it beside the graphics/icon code, not in either repository's module.
- Blocks the launcher's **wallpaper-brightness** theme input, which `feature:shell` currently hardcodes to dark.

### B8 — `data:layout` — placement engine + layout persistence (depends on model, database) ⚠️ **highest-logic module** — ✅ first cut done

> **Status:** engine (`FreeGridPlanner`/`GridReflow`/`GridOccupancy`/`FreePush`) + `LayoutChange` (19→13) +
> slim `LayoutRepository` (~30→6) + complete Room-backed `LayoutRepositoryImpl` (all 5 placement tables + all
> definitions; `apply` exhaustive over 13 ops; DAOs bundled in `LayoutDaos`) are done. Folder ops now also
> take the folded apps off the grid, and folder delete cascades its rows; **empty-folder auto-dissolve** is done
> (in the home layer). **Not** done: the APPS pager/category/list **order** stores (their *own* repository, per
> the arrangement model); `GridEdit`/`DockGridEdit` unify; the FLOW-engine decision; cross-orientation
> rotate-seeding.
- Repository: `LayoutRepository`(+`Impl`), `di/LayoutModule`, mappers (`AppPlacement`/`Folder`/`IconContainer`/`Widget`).
- 🔧 `LayoutChange` (the layout write-command vocabulary) **lives here, NOT in `core:model`** — it is the
  repository's command set, not a persisted shape. Refactor while porting (L1 has 19 near-duplicate ops):
  - Collapse the 5 `Move*` → one `Move(item: GridItem, to: GridPlacement, zone: HomeZone)`, using the
    `core:model` `GridItem`/`GridPlacement`/`HomeZone` (drop `AppPosition`/`Surface`).
  - Collapse `AddAppToIconContainer` + `AddFolderToIconContainer` → one `AddToIconContainer(id, item: IconItem)`.
  - **Removal is NOT one command** (the naive single `Remove(GridItem)` is wrong). Two distinct intents:
    - **remove** = detach from a placement; the app stays installed. Context-specific, mirroring the Add ops:
      `RemoveFromGrid(GridItem)`, `RemoveFromFolder(folderId, app)`, `RemoveFromIconContainer(id, IconItem)`,
      `RemoveFromWidgetContainer(id, appWidgetId)`. Widget remove = unbind; container remove = destroy container.
    - **uninstall** = destroy the package — a **system action, not a `LayoutChange`**. Belongs in `data:apps`;
      the layout reacts to the resulting `AppEvent` removal and prunes placements. Uninstall is available
      wherever an app shows — including the APPS drawer, where it is the *only* removal (you can't "remove"
      an app from the full installed-app list).
- Geometry ⚠️: `GridOccupancy`, `PlacementResolver`, `GridReflow`, `GridEdit`, `DockGridEdit`, `WidgetSpan`.
- 🔧 FLOW engine (`FlowReflow`, `SpreadPush`, `PushPath`): Launcher 1's `FLOW_TO_DRAWER_PLAN` **drops FLOW from
  home**. Decide up front whether L2 even ports these, or moves the packed-grid behaviour straight to the drawer.
- 🔧 `GridEdit` vs `DockGridEdit` are near-duplicate edge-edit passes — unify into one edge-edit operation
  parameterised by `GridBlueprint` (this is the very code that started this whole cleanup).

### B9 — `data:icons` — icon packs + overrides (depends on `core:icon`, database)
- `IconOverrideRepository`(+`IconOverrideMapper`), `CustomIconStore`, `SkinImageStore`, `IconInspector`,
  `IconPackManager`, `InstalledIconPack`, sources (`LauncherAppsIconSource`, `PackAwareIconSource`), `di/IconsModule`.

### B10 — `data:widgets` — `AppWidgetHostController` + `di/WidgetsModule` (depends on database; MIUI quirks apply).

### Suggested build order
`core:model` (finish G3–G6 as needed) → `core:common` → `core:database` → `core:icon` → `data:settings` →
`data:apps` → `data:layout` → `data:icons` → `data:widgets` → `core:designsystem`/`core:navigation` (as
`feature:*` screens demand them).

### Arrangement persistence model (locked 2026-07-23)

How each surface/layout stores *where its items go*. Two primitives cover everything:

- **Coordinate** — item → `GridPlacement` (page/row/col/spans), **per orientation**, gaps allowed. Used
  ONLY by HOME (pager main, dock, widget area) + home folders/containers. → Room `*_placement` tables keyed
  by owner + orientation, each row carrying a **`zone: HomeZone`** (MAIN/DOCK/WIDGET_AREA). The zone is a
  *column, not part of the key*, which is what lets a drag between zones re-stamp the same row rather than
  needing a new op or a migration. *(L1's conflated `surface` column became `zone`; L1
  `Surface{HOME,DOCK,WIDGET_AREA}` split into L2 `Surface{HOME,APPS}` + `HomeZone`.)*
- **Order** — item → an ordinal within a bucket (1-D flow); the render re-paginates it. Everything else.

| Surface / layout | Kind | Store | Per-orientation |
|---|---|---|---|
| HOME pager / dock / widget area | coordinate | `*_placement` + `zone` | yes |
| HOME vertical list | order | `home_list_item` | no |
| APPS vertical list / grid | derived (A–Z) | none | — |
| APPS pager | paged order (explicit page + in-page slot; gaps per page) | `apps_pager_item` | yes — two lists |
| APPS pager-w/-category + category card | order within category | `category` + `category_item` (shared) | no |
| Folder contents | order (dense) | `folder_item.sortOrder` (exists) | no |

Decisions: APPS pager (Samsung-style) stores an explicit **page + in-page slot** per app — pages are hard
boundaries, so a move compacts only the source page (gap parks at that page's end) and overflow cascades to
the next page. It keeps **two saved lists** (portrait + landscape): the second is seeded on first rotate by
re-paginating the first to the new capacity (if the new capacity is smaller, the per-page overflow cascades
forward), then saved independently; rotating back restores the other verbatim. That re-pagination is
repository logic — the DB just holds both lists. The two category layouts **share** one category+order store;
the home vertical list, category, and folder orders are single **orientation-independent** lists (scrollable,
no page capacity). Only HOME **coordinate** placements (pager/dock/widget area) and the APPS pager are
**per-orientation**. Categories (defs + membership) live in **Room** (users will
create custom ones), not the L1 settings blob.

Stores — existing: `*_placement` (coordinate), `folder_item.sortOrder`. Built in B2:
`apps_pager_item(component, orientation, page, positionInPage)`; `category(id, name, sortOrder)` +
`category_item(component, categoryId, sortOrder)`; `home_list_item(component, sortOrder)` (apps only, single shared list across orientations).
Derived/none: APPS vertical list & grid. Layout choice, grid/icon config, sort mode → `data:settings` (B7).
