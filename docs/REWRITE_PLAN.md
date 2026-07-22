# Morphic Launcher 2 — Rewrite Plan (learning build)

**Goal:** rebuild Morphic Launcher from scratch, bottom-up, so the author (a junior Android dev)
actually understands every layer. The original at `../Morphic-Launcher` is the **reference / answer key** —
never deleted, always available to compare against.

**Workflow:** the author writes the code (with local-AI autocomplete); Claude observes, explains the *why*,
reviews, and unblocks — coaching, not code-dumping. Milestones are kept small so each ends in a visible win.
Dependencies are added to each module's `build.gradle.kts` *as the code that needs them is written*.

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
- [ ] `AppEvent` — a change signal (installed / removed / updated) the data layer reacts to
- [ ] `AppCategory` — enum of Android's system categories (game, social, …)
- [ ] `Category` — a user-defined group of apps
- [ ] `CategoryGroup` — a grouping of categories

**G2 — Grid & geometry** ⚠️ (P2 layout, P4+ UI)
- [ ] `Orientation` — enum (portrait / landscape)
- [ ] `GridConfig` — rows × columns of a grid
- [ ] `GridRect` ⚠️ — a rectangular region on the grid (incl. rotation math — has a unit test)
- [ ] `GridEdge` — grid edge descriptor
- [ ] `AppPosition` — where an app sits on the grid

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
