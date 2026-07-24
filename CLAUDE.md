# CLAUDE.md

Guidance for Claude when working in **Morphic Launcher 2** — an Android launcher, mid-rewrite.

## What this project is

A **ground-up, bottom-up rewrite** of Morphic Launcher, with two intertwined aims:
1. the author (a **junior Android dev**) understands every layer, and
2. the codebase comes out **clean**.

Launcher 2 is a **refactor, not a re-type**. The full plan, phase checklist, and per-module
build map live in **[docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md)** — read it before doing
anything structural; it is the source of truth for *what* to build and *in what order*.

## How to work here (read this first)

This is the part that isn't derivable from the code:

- **Claude writes the code; the author reviews to learn.** The work is deliberately split into small,
  self-contained parts. For each part: Claude implements it, then the author joins, reads, and gives
  improvements before moving on. Keep each part small enough that the author can fully read and
  understand it — the learning aim still stands (the author must understand every layer), it's just
  reached via review rather than by typing. So: explain the *why* of each change (in KDoc and in the
  summary), call out the design decisions you made and any alternatives you rejected, and prefer several
  small reviewable commits over one large drop.
- **Never port Launcher 1 verbatim.** The original at `../Morphic-Launcher` (aka "L1") is the
  **reference / answer key**: it runs, but it's fragile and smell-ridden (duplication, poor
  separation, logic in the wrong layer). Never delete it; compare against it, then do it *better*.
  For each piece: understand what L1 does and *why* → question the design (duplicated? honest name?
  right module/layer?) → fix the smell in L2. Worked examples of this mandate:
  `GridBlueprint` (centralized scattered grid config; dropped a wrong interface abstraction + dead
  `max` fields); `DeviceConfiguration` (split the pure enum into `core:model`, detection stays in
  `core:designsystem`); `GridPlacement` (merged near-duplicate `GridRect` + `AppPosition` into one
  type — rejected a `Vector` name because it carries spans, so it's a box, not a vector).
- **KDoc is mandatory.** Every class / interface / enum / top-level gets a KDoc stating what it is
  and what it's for. Document non-obvious members too (params, edge cases, units). Explain *purpose*,
  not line-by-line narration. (This deliberately differs from L1's "docs on request only" rule.)
- **No model in a vacuum.** Build a module/type only when a consumer needs it, so it has context.
- **Add dependencies as needed.** Each module's `build.gradle.kts` gets deps added *as the code that
  needs them is written*, not up front.

## Architecture at a glance

Multi-module Gradle build. Package root `inkspire.morphic.*`; appId `inkspire.morphic.launcher`;
root Gradle project name `Launcher2`.

- **`core:*`** — `model` (plain Kotlin data shapes), `common` (DI + coroutine plumbing), `database`
  (Room), `icon`, `designsystem` (Compose), `navigation`.
- **`data:*`** — `apps`, `icons`, `layout` (the highest-logic module — placement engine), `settings`,
  `widgets`. Each exposes repositories the UI consumes.
- **`feature:*`** — `home`, `appdrawer`, `applibrary`, `settings`, `shell`.
- **`app`** — the launcher application shell.
- **`build-logic/convention`** — custom Gradle convention plugins (see the module→plugin table in
  the rewrite plan). Versions are centralized in `gradle/libs.versions.toml`.

Stack: Kotlin, Jetpack Compose, Room, Koin (DI), coroutines. Typesafe project accessors are enabled.

## Core domain model — the "surface" taxonomy

The `core:model` layer is deliberately named with **one suffix per concept**; keep any new model
consistent with this. See [core/model/.../Surface.kt](core/model/src/main/kotlin/inkspire/morphic/core/model/Surface.kt).

- **`Surface`** — a full-screen destination gestured between: `HOME` (center) + side surfaces like
  `APPS`. All values are peers.
- **`HomeEdge`** — `LEFT/RIGHT/TOP/BOTTOM`; the edges of HOME you swipe from to reach a side surface.
  Each edge is bound independently; the binding config lives in the **settings layer, not the model**.
- **`HomeLayout`** — coupled main-area + side-zone combos (`PAGER_WITH_DOCK`,
  `LIST_WITH_WIDGET_AREA`). Modeled as one enum so illegal pairings are unrepresentable.
- **`HomeZone`** — placement regions within HOME: `MAIN`, `DOCK`, `WIDGET_AREA`.
- **`AppsLayout`** — how the APPS surface renders (unifies old "drawer" + "library"; layout alone
  decides the look).

When adding model, prefer: make illegal states unrepresentable, one honest name per concept, and
pure enums/data in `core:model` with detection/logic pushed to the appropriate layer (e.g.
`DeviceConfiguration` is split — pure enum in `core:model`, detection in `core:designsystem`).

## Layout & arrangement persistence (locked 2026-07-23)

How each surface stores *where its items go*. **Two primitives cover everything** — pick the right one
when adding any new placement:

- **Coordinate** — item → `GridPlacement` (page/row/col/spans), stored **per orientation**, gaps
  allowed. Used **only by HOME** (pager main, dock, widget area) and home folders/containers. Lives in
  Room `*_placement` tables keyed by owner + orientation + **`zone: HomeZone`** (MAIN/DOCK/WIDGET_AREA).
- **Order** — item → an ordinal within a bucket (1-D flow); the render layer re-paginates it. Used by
  **everything else**.

| Surface / layout | Kind | Store | Per-orientation |
|---|---|---|---|
| HOME pager / dock / widget area | coordinate | `*_placement` + `zone` | yes |
| HOME vertical list | order | `home_list_item` | no |
| APPS vertical list / grid | derived (A–Z) | none | — |
| APPS pager | paged order (page + in-page slot) | `apps_pager_item` | yes (two lists) |
| APPS pager-w/-category + category card | order within category | `category` + `category_item` | no |
| Folder contents | order (dense) | `folder_item.sortOrder` | no |

Key rules:
- Only HOME **coordinate** placements and the **APPS pager** are per-orientation; everything else is a
  single orientation-independent list.
- **APPS pager** stores an explicit page + in-page slot — pages are hard boundaries (a move compacts
  only the source page; overflow cascades forward). It keeps **two saved lists** (portrait + landscape),
  re-paginated in **repository logic** on first rotate — the DB just holds both lists.
- The two category layouts **share** one `category` + `category_item` store.
- **Categories (defs + membership) live in Room**, not the settings blob — users create custom ones.
- L1's conflated `surface` column became `zone`; L1 `Surface{HOME,DOCK,WIDGET_AREA}` split into L2
  `Surface{HOME,APPS}` + `HomeZone`.

Full rationale: [docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md) → "Arrangement persistence model".

## Containers (icon & widget)

Both are **standalone items on the HOME grid** (each occupies a `*_container_placement` slot); their
*contents* are **ordered** within the container (`sortOrder`), not individually grid-placed. Both are added
from the **widget picker** and both start **empty** — just a "+" button.

- **Icon container** — holds apps and/or folders. Fill it via the "+" button (opens an app picker) or by
  dragging an app/folder onto it. Each membership row is **exactly one** of app-or-folder, and an app/folder
  lives in **at most one** icon container (mirrors how a folder holds an app once). See
  [IconContainerItemEntity.kt](core/database/src/main/kotlin/inkspire/morphic/core/database/entity/IconContainerItemEntity.kt).
- **Widget container** — holds widgets. Created two ways: from the widget picker (empty → "+" opens widget
  setup), or by **dropping one widget onto another** (combines both into a new container). Each widget lives
  in at most one container.

The "combine" and "extract" flows (drop-to-merge, empty-container placement, moving contents in/out) are
**repository logic**, not entity structure — the entities just express membership + ordering + the container's
own grid placement.

## Icon feature — layer-based editor + baked display (locked 2026-07-23)

The icon system is a **layer editor** (like a drawing app) whose output is a **single flat bitmap** shown on
every surface. Distilled from L1's `ICON_LAYER_STUDIO_PLAN` — adopt its end-state, skip its flat-column churn.

**Source & parsing.** App icons come from the `LauncherApps` API. Each is parsed into **two permanent,
non-deletable layers**: a **background** and a **foreground** (fg always renders above bg). Parsing never
splits the foreground further — a legacy raster and a modern adaptive foreground both just *are* fg content
(no glyph matting; it's unreliable). All backgrounds land in the bg layer, **even when empty** (the empty bg
slot still exists for the user to fill).
- **Legacy icons**: the whole bitmap → fg layer; sample edge/corner pixels and, if colour variance is low,
  **pre-fill the bg layer with that solid colour** (L1's detection); busy/transparent edges → leave bg empty.

**Layer content** is a small sum type, not always an image: **app-default (parsed image or colour)**,
**custom image**, or **solid-colour fill** (a colour-only background is a `SolidFill` bg).

**Foreground monochrome.** The fg layer has **filters**, one of which is a **monochrome effect** (tints the
fg). Separately, an app may ship a real **monochrome icon** (the OS themed-icon layer). The fg offers a
**toggle**: an app *with* a monochrome icon → **filtered foreground** *or* **the app's monochrome icon**; an
app *without* → filtered foreground only. The parsed monochrome layer is **stashed aside** at parse time as
that alternate fg source — it is not a third stack layer.

**Editor.** fg/bg are the base; the user inserts **custom layers below bg / between fg&bg / above fg**. The
only ordering rule is **fg stays above bg** (customs are otherwise free). Per layer:
- **transform** — X/Y (in a normalized square frame), zoom, rotation.
- **shape** — an `IconShape`, **fg & bg only**; custom layers keep their own alpha (not shaped). A shape is
  **backed by a vector drawable** (prepared as a resource) and referenced by a stable id; the clip mask is
  built from that drawable's silhouette, so adding a shape = drop in a drawable, no path math in code.
- **effects/filters** — extensible (monochrome + more); do **not** hard-model these as columns.

**Rendering — hybrid:**
- **Display** (home, drawer, folders, pickers): the resolved layer set is **composited to one flat bitmap**,
  cached by `IconId(component, resolvedLayerSet, sizePx)` (value-equality key → correct invalidation for
  free), baked off the main thread. Surfaces draw one `Image`.
- **Editor**: layers render **live** (each a Compose node — transform via `graphicsLayer`, effects via
  colour-filter/blend) so slider drags respond instantly with no per-frame bake; a commit invalidates that
  icon's baked entry.

**Persistence — one serialized `IconLayerSet` blob, NOT flat columns.** (L1 burned four destructive DB bumps
learning this.) A per-app override = `component` + a JSON `layerSet` blob; a global default set is the
fallback every app inherits. Editing an app **snapshots the default and detaches** (Reset re-attaches) — no
field-merge, no variable-length-list diffing. **Consequence for B2:** the current flat, stringly
`IconOverrideEntity` (`shapeChoice`/`foregroundScale`/…) collapses to `component` + a `layerSet` blob when B9
(`data:icons`) lands.

**Deferred:** icon packs as a layer source; skin/backing-plate (L1's separate live-Compose backdrop, distinct
from the baked stack).

## Current status

Per the rewrite plan: **P0 (scaffold) done; P1 (Core) in progress.** Done: `core:model` (B0, migrated +
refactored) and `core:common` (B1, DI + coroutine plumbing). `core:database` (B2 — Room entities, DAOs,
converters, DI) is implemented; not yet reviewed for correctness. Next in P1/P2 per the build order.
Not yet a launcher — the `HOME` intent category is added last (P9), the final flip.

## Conventions summary

- Kotlin + Gradle Kotlin DSL (`.kts`) throughout.
- Follow the existing module boundaries and the build order in the rewrite plan.
- Match surrounding style; keep KDoc current when changing a type's purpose.
