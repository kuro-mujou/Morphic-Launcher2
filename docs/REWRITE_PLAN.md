# Morphic Launcher 2 — Rewrite Plan (learning + cleanup build)

**Goal:** rebuild Morphic Launcher from scratch, bottom-up, with TWO intertwined aims: (1) the author
(a junior Android dev) understands every layer, and (2) the codebase comes out **clean**. Launcher 2 is a
**refactor**, not a re-type. The original at `../Morphic-Launcher` is the **reference / answer key** —
it *runs*, but it's fragile and full of code smells (duplication, poor separation, logic in the wrong layer).
It is never deleted; we compare against it and then do it better.

**Refactor mandate:** do NOT port Launcher 1 code verbatim. For each piece, first understand what the
reference does and *why*, then question the design before writing it: is this duplicated? is the name honest?
is this concern in the right module/layer? Fix the smell in Launcher 2. Examples already found this way:
`GridBlueprint` (centralised scattered grid config; dropped a wrong interface abstraction + dead `max` fields);
`DeviceConfiguration` (split the pure enum into `core:model`, detection stays in `core:designsystem`);
`GridPlacement` (merged the near-duplicate `GridRect` + `AppPosition` into one type — rejected a `Vector` name
because it carries spans, so it's a box not a vector).

**Workflow:** the author writes the code (with local-AI autocomplete); Claude observes, explains the *why*,
reviews, proposes the cleaner design, and unblocks — coaching, not code-dumping. Milestones are kept small so
each ends in a visible win. Dependencies are added to each module's `build.gradle.kts` *as the code that needs
them is written*.

**Docs convention (Launcher 2):** every class / interface / enum / top-level gets a **KDoc** stating what it
is and what it's for — this is a *learning + cleanup* build, so the intent should be readable from the source.
Document non-obvious members/functions too (params, edge cases, units). Keep it concise — explain *purpose*,
not a line-by-line narration. (This intentionally **differs** from Launcher 1's "docs on request only" rule.)

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
- [ ] **P8 — Settings** screen.
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

## `core:model` inventory (write each when its phase needs it — not all up front)

### How we work from here
The author drives — picks the next piece from this map. Claude's job: (1) teach the *concept* on the
⚠️ geometry/logic ones, (2) review on request. No more per-class prompts. Principle stands: don't write a
model in a vacuum — write it when the database/UI that consumes it is being built, so it has context.

## Bottom-layer build map (`core:*` + `data:*`)

Everything below `feature:*`, in rough **dependency order** (each stage may depend on the ones above it).
Class lists are taken from the Launcher 1 reference at `../launcher`. Legend: ⚠️ = real logic — understand
before porting; 🔧 = known smell — refactor, don't copy. Build a module only when a consumer needs it (the
"no model in a vacuum" principle); this map is the *shape*, not a "write it all now" list.

### B0 — `core:model` 
- copy/migrate all model from original launcher project

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
- cells/components: `AppCell`, `IconLabelCell`, `FolderCell`, `AppListColumn`, `IconMetrics`, `ItemInteraction`.
- blur: `Backdrop`, `IconSkinBackdrop`, `LiquidGlass`. theme: `LauncherTheme`, `Typography`, `WallpaperColorScheme`.
- misc: folder (`FolderGrid`/`FolderPager`/`FolderAppPickerSheet`), filter (`Alphabet*`), search/field, popup,
  indicator, dialog, settings rows, insets, `topaction/TopActionZone`.
- **Grid editors** (`HomeGridEditor` etc.) live in `feature:settings`, not here — when ported, drive them off
  `GridBlueprint` + a `core:designsystem` `resolveBounds(blueprint, area, iconRail)` (the resolver we scoped).

### B5 — `core:navigation` — `Navigator`, `Routes` (small; depends on `core:model` surfaces, G4).

### B6 — `data:apps` — installed-app source + categorisation (depends on model, common)
- `AppRepository`(+`Impl`), `LauncherAppsWrapper`, `AppShortcut`, `mapper/AppInfoMappers`, `applist/AppListPipeline`.
- category 🔧: `AppCategorizer`, `CategoryHeuristics`, `CategoryMapping`, `AssetCategoryMapping` — Launcher 1's
  `POST_FIX_CLEANUP_PLAN` already targeted this area; bake those fixes in from the start (VM/business logic out
  of UI, dedupe mappings).

### B7 — `data:settings` — preferences + presets (depends on model, common)
- `SettingsRepository`(+`internal/SettingsRepositoryImpl`), `LauncherSettings`, `internal/Preferences`,
  `IconStyleMapper`, `WallpaperRepository`(+`Impl`), preset (`Preset`/`PresetRepository`/`PresetTemplates`), `internal/Blur`.
- 🔧 `LauncherSettings` is a god-object and `GridConfigKind`/per-surface knobs live here — a lot of this is now
  expressed by `GridBlueprint` in `core:model`; move the static grid facts out and slim the settings blob.

### B8 — `data:layout` — placement engine + layout persistence (depends on model, database) ⚠️ **highest-logic module**
- Repository: `LayoutRepository`(+`Impl`), `di/LayoutModule`, mappers (`AppPlacement`/`Folder`/`IconContainer`/`Widget`).
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
