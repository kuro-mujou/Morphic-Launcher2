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

Grouped by concern. `[x]` = done. ⚠️ = has real logic/geometry (pair with Claude for the concept).

**G1 — App identity & grouping** (needed now → P1/P2)
- [x] `ComponentKey` — unique app identity (package + class + user) + `flatten`/`parse`
- [x] `AppInfo` — display info; *contains* a `ComponentKey`
- [x] `AppEvent` — a change signal (installed / removed / updated) the data layer reacts to
- [x] `AppCategory` — enum of Android's system categories (game, social, …)
- [x] `Category` — a user-defined group of apps
- [x] `CategoryGroup` — a grouping of categories

**G2 — Grid & geometry** ⚠️ (P2 layout, P4+ UI)
- [x] `Orientation` — enum (portrait / landscape)
- [x] `GridConfig` — rows × columns of a grid
- [x] `GridPlacement` ⚠️ — paged-grid rect (page + row/col + spans) with overlap/contains/fit/rotation math.
      **Merged `GridRect` + `AppPosition`** into one honest type (they were near-duplicates). Named a *placement*
      not a `Vector` — it carries spans (extent), so it's a box, not a vector. ⚠️ needs the rotation unit test.
- [x] `DeviceConfiguration` — device/orientation enum (phone/tablet × portrait/landscape). Pure enum in
      `core:model`; window-size detection (`fromWindowSizeClass`, `currentDeviceConfiguration`) in `core:designsystem`.
- [x] `GridBlueprint` — **was `GridEdge`**: centralises per-(surface × layout) grid config — sizing, cell
      multiplier, free-placement, per-`DeviceConfiguration` defaults, optional edit range; holds `GridEditorEdge`.

**G3 — Layout containers & arrangement** (P2 layout, P4–P7)
- [ ] `IconContainer`, `IconArrangement`, `DrawerSlot`, `Folder`,
      `LayoutChange`, `LayoutCombination`, `AppDrawerLayout`, `AppLibraryLayout`

**G4 — Surfaces & navigation** (P3–P5)
- [ ] `Surface`, `HomeSurfaceKind`, `SideSurfaceKind`, `SurfaceTransition`,
      `TabBarPosition`, `SearchPosition`

**G5 — Widgets** (later, ~P6) — `WidgetInfo`, `WidgetContainer`
**G6 — Wallpaper & effects** (late, ~P8) — `WallpaperEffect`, `WallpaperEffectParams`

### How we work from here
The author drives — picks the next piece from this map. Claude's job: (1) teach the *concept* on the
⚠️ geometry/logic ones, (2) review on request. No more per-class prompts. Principle stands: don't write a
model in a vacuum — write it when the database/UI that consumes it is being built, so it has context.
