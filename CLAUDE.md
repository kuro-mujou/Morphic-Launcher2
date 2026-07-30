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
  type — rejected a `Vector` name because it carries spans, so it's a box, not a vector); the grid blueprints
  (`Drawer{Classic,Pager,Grouped}Grid` → `Apps{Scroll,Pager,Category}Grid` — "drawer" is vocabulary the surface
  taxonomy abolished, and `feature:apps` would have re-imported it the moment it consumed one).
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
- **`feature:*`** — `home`, `apps`, `settings`, `shell`. One module **per surface**, not per look: `apps` is the
  whole APPS surface and picks its arrangement from `AppsLayout` internally, which is why L1's separate
  `appdrawer` + `applibrary` modules are gone rather than ported.
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
  Room `*_placement` tables keyed by owner + orientation, each row carrying a **`zone: HomeZone`**
  (MAIN/DOCK/WIDGET_AREA). The zone is a *column, not part of the key* — which is what lets a drag between zones
  re-stamp the same row instead of needing a new op or a migration.
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

**Settled for the APPS pager: it holds folders, and a folder's slot *is* its row.** `apps_pager_item` was keyed on
`component`, so it could only hold an app, and an APPS-hosted folder had nowhere to store its position. Both were
answered by one reshape (DB v2): the row became **exactly one of** app-or-folder — `IconContainerItemEntity`'s
shape, which `IconItem`'s KDoc had already predicted by naming "the `Surface.APPS` pager and an `IconContainer`"
as its two holders. There is no `apps_pager_placement` table and should not be: an ordered surface stores a slot,
not a coordinate. One difference from `icon_container_item`, and it is silent when wrong — the unique indices are
scoped **per orientation**, since the pager keeps two saved lists and an app appears once in each.

⚠️ **Still open for the category *card*.** `category_item` is keyed on `component` too, so the same reshape is
owed there if the card is to hold folders; the pager's version is the worked example. (The category *pager* is
settled the other way — see below.) The UI side is ready: `FolderHostState` is surface-independent and shared.

**Settled, and it narrows that question: the category *pager* (`PAGER_WITH_CATEGORY`) holds no folders.** A
category *is* the grouping there, so a folder inside one would be a second, redundant one. Its pages are dragged
between (carrying an app to another page is how it changes category) and its cells split into **halves** with no
centre merge ring, since there is nothing to merge into — which is what `CategoryPagerPlayground` prototypes. That
harness is the category *pager*, not the category *card*; its "no folders here" is that layout's intent and says
nothing either way about the card, which is still open above.

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

## Design system (`core:designsystem`)

- **Keep Expressive *motion*, drop Expressive *visuals*.** New/reworked UI uses Material 3 **Expressive
  motion** but not its look. `LauncherTheme` sets `motionScheme = MotionScheme.expressive()` on the base
  MaterialTheme; components get the expressive spring choreography by consuming `MaterialTheme.motionScheme`.
  The Compose BOM does **not** carry the Expressive APIs, so `material3` is pinned to `1.5.0-alpha22` in the
  version catalog; opt in per-usage with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where the
  compiler asks.
- **Monochrome palette.** Greyscale chrome so the wallpaper + app icons carry the colour. `accent` is a
  high-contrast greyscale *emphasis* (not a hue) — selection/active read by contrast; **red is reserved for
  `error`** only. **Both light and dark are first-class** (dark mode is an accessibility barrier for some
  users). Semantic tokens live in [theme/MorphicColors.kt](core/designsystem/src/main/kotlin/inkspire/morphic/core/designsystem/theme/MorphicColors.kt).
- **Theme layering + monochrome M3 bridge.** `MorphicTheme` provides our colours only (`LocalMorphicColors`).
  `LauncherTheme` = M3 base + expressive motion + `MorphicTheme`, and it feeds MaterialTheme a **monochrome
  M3 `ColorScheme` bridged from `MorphicColors`** (`MorphicColors.toM3ColorScheme(dark)`), so stock M3
  components render greyscale *and* keep Expressive motion. Use `LauncherTheme` as the app wrapper.
- **Build components *on* M3, restyle — go fully custom only where M3 has no equivalent.** Because the scheme
  is bridged monochrome, wrap the real M3 component and get its native Expressive motion for free:
  `MorphicButton` = the M3 button family + `ButtonDefaults.shapes()` (press shape-morph); `MorphicSlider` =
  M3 `Slider` with a custom `thumb` slot over M3's own track (our grow-on-press thumb, M3's track + grab). Reserve fully-custom `Row`/`Canvas`
  for controls M3 lacks — the **2D pad** and the **segmented control**. The **range slider** is built on M3
  `RangeSlider` (custom thumbs, M3 track); a **vertical slider** (custom Canvas — M3 has none) is deferred
  until a consumer needs it.
- **Modern state APIs behind convenient facades.** Components sit on the **state-hoisted** M3 APIs internally
  (`rememberSliderState`/`rememberRangeSliderState`) — not the value-based overloads (deprecation path). But
  they expose a plain `value`/`onValueChange` API and create + bridge the state *inside* (`LaunchedEffect(value)`
  in, `snapshotFlow { … }.drop(1)` out), so call sites need no `remember*State`. **Exception — the text field
  keeps a hoisted `TextFieldState`** (`rememberTextFieldState()` at the call site): its config-change survival
  needs the caller to own the state, so hiding it would throw that benefit away.
- **The text field wraps `BasicTextField` (foundation primitive) + a `decorator`, not M3's `TextField`.** M3's
  styled field is too opinionated about focus/label/indicator; the primitive gives full focus control — our
  own `onFocusChanged` state, the focus ring, placeholder-behind-field, and clearing focus when the IME is
  dismissed (`WindowInsets.isImeVisible`).
- **Settings vs launcher colour = one theme, two "is-dark" inputs** (not two palettes). Settings feeds
  `darkTheme = isSystemInDarkTheme()` (our controlled surface); the launcher feeds a **wallpaper-brightness**
  signal (chrome must contrast the wallpaper — bright wallpaper → Light scheme/black tint, dark → Dark/white).
  Apply the theme per **zone boundary** (launcher shell vs settings graph), not per nav destination; a nested
  `LauncherTheme` overrides its subtree. The wallpaper-brightness analyzer, transparent/frosted launcher
  surfaces, and `FrostedTextField` are a **deferred launcher-UI subsystem**; settings needs none of it.
- **An item's touch target is its visible extent, never its cell.** A cell is a *layout* footprint, usually much
  bigger than what is drawn in it (a home cell is a 2×2 visual slot around one icon + label). `LauncherDragCell`
  therefore hands `itemGestures` **down to its content**, which decides what is touchable: `IconLabelCell` puts it
  on the icon+label group; content that genuinely fills its cell (a widget, a harness tile) `.then()`s it onto its
  root. The slack must stay free — otherwise a full page of icons leaves nowhere to press-and-hold for the
  *surface's* menu. Works because `launcherItemGestures` never consumes a down. Consequence: cells (`AppCell`,
  `FolderCell`) carry **no `onClick`** — taps arrive through the one gesture contract. See
  [docs/DRAG_AND_DROP_DESIGN.md](docs/DRAG_AND_DROP_DESIGN.md) §5.
- **Don't invent a dimension nothing owns yet.** Launcher surface metrics (dock extent, home padding, icon size, grid
  rows) are **settings-driven by design**. Until `data:settings` exists, use the plainest possible stand-in — a named
  dp constant, or "fill the rest" — and KDoc it as a placeholder naming the setting that replaces it. Do **not** derive
  it from something else to make it look principled: derived-looking arithmetic reads as a decision when it is really
  a guess, and it hides that the value is still unowned (worse, it can invert the real dependency — the dock's row
  count comes *from* its height, so sizing its height from a row count is backwards). `DockHeight` in `HomeScreen` is
  the worked example.
- **Packaging discipline (unlike L1):** `component/` holds *only* the generic `Morphic*` UI primitives;
  colours/theme live in `theme/`; launcher-specific icon cells (`AppCell`/`IconMetrics`) get their own
  package. Do **not** mix generic components and app-icon widgets in one package like L1 did.
- **Port per-consumer.** L1's `core:designsystem` is ~50 files / 5.4k LOC — pull a group only when the
  screen that needs it is built, not up front.
- A **dev gallery** (`app` → `dev/DevGalleryScreen`) hosts every `Morphic*` component + the palette under a
  light/dark toggle; add each new component to it.

## Feature & presentation architecture (locked 2026-07-27)

**Plain MVVM — no MVI, no reducer.** This is a hard rule precisely because L1 *claimed* "MVVM + MVI + clean
architecture" and drifted into monolith ViewModels (a 500-line sealed-`HomeEvent` + `when(event)` block). We do
**not** repeat that. The pattern for every screen:

- **A `ViewModel` per screen** (`androidx.lifecycle.ViewModel`), not a hand-rolled singleton. It exposes **one
  immutable `StateFlow<XxxState>`** (the "Model") and **plain typed methods** for events (`launch()`,
  `applyChanges()`) — the Now-in-Android style. **No sealed `Intent`/`Event` hierarchy and no single `onEvent`
  dispatcher**; that ceremony is what rotted L1. Unidirectional flow (UI → method → state → UI) with a single
  state object is the whole of the "MVI" benefit, and we already have it without the machinery.
- **Coroutines run on `viewModelScope`** (not an injected `ApplicationScope`), so work cancels with the screen.
  Bind with Koin's `viewModel { }` DSL (`org.koin.core.module.dsl.viewModel`) and inject with
  `koinViewModel<XxxViewModel>()` — this scopes the instance to the screen's `ViewModelStore` (survives
  rotation, cleared with the screen).
- **Keep logic out of the composable.** The composable reads `state` (via `collectAsStateWithLifecycle()`) and
  calls methods; assembly, persistence, and optimistic state live in the ViewModel so it stays unit-testable.
- **Reference:** [feature/home](feature/home/src/main/kotlin/inkspire/morphic/feature/home) — `HomeViewModel`
  (StateFlow + `launch`/`applyChanges`), `HomeScreen`, `HomeState`, `di/HomeModule`.

**Feature modules own their screens; `app` only assembles.** Each screen lives in its own `feature:*` module (not
in `app`), applying the `launcher.android.feature` convention plugin — which is why that plugin pre-wires
core:model/common/designsystem + lifecycle-viewmodel + koin-compose + Compose. A feature adds only the extra
deps it needs (e.g. `feature:home` adds `data:apps` + `data:layout`). The `app` module is the shell: it starts
Koin with every module's DI graph and hosts the entry points (currently the `dev/DevRootScreen` harness). New UI
starts in a `feature:*` module from day one — do **not** prototype it in `app` and "extract later".

**Repository vs command split.** A repository is *read/refresh* access to data (e.g. `AppRepository` streams the
app cache). A side-effecting *command* gets its own honest type rather than being bolted onto a repository — e.g.
launching is `AppLauncher` (`data:apps`), a one-method interface, not a `launch()` on `AppRepository`. (L1 folded
launch onto the repository; we don't.)

## Current status

Foundations: **P0 done; P1 Core done** — `core:model` (B0), `core:common` (B1), `core:database` (B2). **B3
`core:icon` done** (parse → layer model → render/bake → `IconRenderManager` → `LauncherIcon`; per-layer effects
+ live editor deferred). **B6 `data:apps` partial** (LauncherApps wrapper, `AppRepository` + Room cache,
`RawIconSource`; categorization + `AppEvent` live updates/pruning deferred).

**B4 `core:designsystem` — well along.** Theme + `Morphic*` components, plus the interaction primitives, all
validated in the `app/dev` harness (`DevRootScreen`): the drag toolkit (`DragCoordinator`, `FreeGridPlanner`,
MovingGap, `launcherItemGestures`, `DropFootprint`/`FloatingDragIcon`), `LauncherPager`, `SurfacePager`
(HOME↔side-surface pan; per-edge one/two-finger `OneFingerSwipe` policy — `ALWAYS`/`AT_EDGE`/`NEVER`), and the
grid (**grid plan G1–G5 + extras**, see [docs/GRID_LAYOUT_PLAN.md](docs/GRID_LAYOUT_PLAN.md)): `LauncherGrid`
(FIXED_PAGER + SCROLL_GRID sizing), the `coordinateItems`/`flowItems` placement-strategy DSL, `animatePlacement`,
and the extracted `GridGeometry` seam. Only grid **G6** (full-harness regression gate) is unticked — treat as
done-on-device. **Built for the home since:** the shared **coordinate drag surfaces** — `CoordinateDragGrid`
(single zone) + `CoordinateDragPager` (paged, viewport zone + edge-flip) + the shared `LauncherDragCell`
(per-item drag wiring), all reused by the harness `GridSurface`; the `AppCell`/`FolderCell` launcher cells (via
`IconLabelCell` + `cellLabelHeight`); and the full **folder subsystem** — `FolderOverlay`, `folderInnerSize`
(a `@Composable` facade over pure sizing arithmetic), `FolderReorder` MovingGap, `FolderDragDelegate`, and
**`FolderHostState`/`FolderPhase`** (the open/leave/enter lifecycle any folder-hosting surface reuses; 20 unit
tests). Item gestures are scoped to the icon+label group, not the cell — see the design-system rules above.

**B8 `data:layout` — first cut done.** Geometry engine (`FreeGridPlanner`/`GridReflow`/`GridOccupancy`/
`FreePush`) plus the command + persistence layer: `LayoutChange` (L1's 19 ops → 13), `LayoutRepository` (slim
~30 → 6: one `placements` flow keyed by `GridItem` with `HomeZone` in the value, four definition flows, one
`apply` sink), and a complete Room-backed `LayoutRepositoryImpl` (five placement tables + folder / icon-container
/ widget-container / widget definitions; `apply` exhaustive over all 13 ops; twelve DAOs bundled in `LayoutDaos`).
`CreateFolder`/`AddToFolder` also delete the folded apps' grid placements (an app lives in one place); folder
delete cascades its membership + placement rows. The APPS pager/category/list **order** stores get their *own*
repository (not built). Deferred: cross-orientation rotate-seeding (empty-folder auto-dissolve now done, in the
home layer).

**Home surface — extracted to `feature:home`, real-sized, two zones (pager + dock), with a full folder subsystem.**
Its own module (`feature:home`, `inkspire.morphic.feature.home`); plain-MVVM `HomeViewModel` (screen-scoped
`ViewModel`, optimistic placement state, logic out of the UI) joins `LayoutRepository.placements` + `AppRepository`
apps + `LayoutRepository.folders`. The main area renders on the paged `CoordinateDragPager` at the **real blueprint
size** (`HomePagerGrid.toGridConfig(device)` — device-aware, `cellMultiplier = 2`, apps are 2×2 logical footprints
snapped to the visual lattice; device detected in the UI, fed to the VM for seeding). Portrait only. Hosted as
the default `DevRootScreen` screen.
- **Launch** on tap → `AppLauncher` (`data:apps`, a one-method command separate from `AppRepository`'s reads;
  resolves the per-profile user from `ComponentKey.userSerial`, unlike L1's hardcoded personal user).
- **Drag-to-rearrange** persists through `LayoutRepository.apply` (optimistic → no flicker; `FreeGridPlanner`
  push with directional-push + merge-ring partition, dwelled preview, edge-flip pages, trailing empty page mid-drag).
  Seed leaves a free row so a full grid stays rearrangeable.
- **Folders.** Dropping an app on another (centre merge ring) creates a folder; folders render as a `FolderCell`
  (2×2 icon preview). Tapping opens `FolderOverlay` — two zones (black scrim + a bounded card sized live by
  `folderInnerSize` per device/orientation, inset to `systemBars ∪ displayCutout`), a **dense-flow pager** of the
  folder's ordered apps, launch on tap, in-folder **MovingGap reorder** (persist `ReorderFolder`), and a border
  outlining the inner zone while dragging.
- **A folder is a place one drag passes *through*, not a destination it commits to.** This is the whole model, and the
  rest of the folder rules fall out of it. **Enter**: hold a dragged app on any folder's merge ring (~1s) and it opens
  mid-drag with the drag carrying on inside, so the app lands at a *chosen* slot. **Leave**: hold outside the card
  (~1s) and the folder **closes**, with the same drag carrying on over the grids beneath. Both directions are
  **repeatable, in any order, over any number of folders — including re-entering one already visited**, because
  *neither half writes anything*: membership is decided **only at the drop**. It is one continuous gesture on a
  **single shared `DragCoordinator`** (home + dock + folder zones on it, planner/drop dispatch by zone, the folder
  publishes a `FolderDragDelegate`). The dwells are **equal by design** (`LeaveDwellMs` == `OPEN_FOLDER_DWELL_MS`):
  opposite halves of one gesture, so a user who learnt one hold has learnt both.
  - **Leaving must genuinely close the folder, not hide it.** An earlier cut kept a `FolderPhase.Extracting` whose
    folder was still "open" (faded to alpha 0) and latched an `extracting` flag until the drag ended. That made every
    folder a one-shot: the folder you left could never be re-opened, and re-presenting it rendered nothing and
    registered no drop zone. Nothing in the overlay may be scoped to the **drag** when it belongs to a **visit** —
    the arming flag below is the live example.
  - **Leaving is armed only after the finger has been over the inner grid, per visit.** A folder that opens mid-drag
    does so under wherever its cell sat, which near a screen edge is already the outer zone — un-armed, the same held
    finger that put the app in would instantly eject it (and with a per-*drag* flag, re-entry would eject instantly
    too). A drag that starts inside is armed on its first frame, so one rule covers arriving, leaving, and returning.
  - A drag out of a folder can land on a **merge ring** as well as an empty cell (`mergeExtractedApp`) — so a *quick*
    folder→folder move needs no dwell at all; dropping on the folder it came from is a **no-op** (still a member,
    nothing written). Every landing shares one `leaveFolderChanges` (remove + auto-dissolve), so the paths can't
    disagree about what leaving a folder means. Removing the second-last app **auto-dissolves** it (last app inherits
    its cell).
  - **Releasing outside the open folder cancels** (close it, write nothing). Leaving is a deliberate dwell, so a
    release out there reads as "never mind" — and it *cannot* be honoured anyway: an app being carried inside a folder
    has no grid placement, so "placing" it would leave it in the folder **and** on the grid.
- **What the drag owes is fixed at lift: `FolderHostState.dragSourceFolderId`.** The folder a drag *started in* (null
  if it started on a grid), captured at `onDragStart` and held until release, whatever it visits in between. It answers
  two questions at once, and they are the same folder for the same reason — the gesture began on one of its cells:
  that overlay must **stay composed** (an in-flight pointer stream **cannot** be handed to another node — see the rule
  in `launcherItemGestures`; a root-level pointer overlay was tried and rejected because it swallows item events), and
  it is the one **owed a removal** wherever the app lands. It is deliberately *not* a `FolderPhase` field: the phase
  names whichever folder is *on screen*, which after one hand-off is no longer the one owed anything.
  **Capturing it at lift rather than at the first hand-off is what makes re-entry work** — an app carried *in* from a
  grid and back out owes that folder nothing, so nothing pins it and nothing bars re-opening it. `FolderOverlay`'s
  `presenting = false` is the pointer-holder role: invisible, no back handler, no delegate, no drop zone, no proxy —
  and reversible, since a drag can come back.
  **Three traps if you touch this:** both overlays must be emitted from **one keyed call site** (a second call site is
  a different composition position, so Compose disposes the folder and kills the drag it was preserving); an app with
  no placement must be resolved through `HomeState.appInfo` (**placed apps *and* folder contents**) or it is
  unrenderable — both as a folder's `incoming` and as home's floating **proxy**, which home takes back the moment a
  folder closes; and the folder drop zone is **one shared `ZoneId`**, so only the *presenting* overlay may register or
  unregister it (an unguarded `onDispose` on the holder tears the zone out from under the folder on screen).
  The whole open/leave/enter lifecycle lives in `FolderHostState` (`core:designsystem`), not the screen; home
  supplies only the surface-specific *"which folder does this merge plan target?"* lambda (a **zone + placement**
  match — placement alone matches the other zone's folder at the same cell) and the commit calls. Two guards worth
  knowing: `reconcileFolderOrder` (`FolderOrder.kt`) folds a UI-reported order
  back onto real membership, because `ReorderFolder` replaces membership wholesale and the UI can only report
  members it could render — writing its list verbatim **deleted** anything unresolvable (an uninstalled app,
  B6 pruning still deferred); and `FolderOverlay` is wrapped in `key(folderId)` so switching folders doesn't
  inherit the previous one's pager position or reorder gap.
- **Dock — a second coordinate zone, a peer of the main area.** A single non-paged `CoordinateDragGrid`
  (`DockGrid.toGridConfig(device)`) below the pager, registered as a second zone (`DockZoneId`) on the **same shared
  `DragCoordinator`**. Because one coordinator hit-tests every zone in one space, **pager↔dock drag is not a feature
  at all** — it is one gesture whose drop reports the zone it landed in, and `homeZoneOf(zoneId)` turns that into the
  `HomeZone` the `Move` writes. It needs **no new op and no schema change**: `LayoutChange.Move` already carries
  `zone`, and the `*_placement` tables key on *item + orientation* (not zone), so the same row is re-stamped.
  Everything else is zone-generic rather than duplicated: one `planCoordinateDrop` serves both grids (differing only
  in geometry/config/occupants/page — deliberately **not** L1's `resolveDockDrop`, a drifted near-copy of its home
  resolver), one `handleDrop`, one `HomeItemCell`. The dock takes apps, folders and (later) widgets, merges into
  folders, and hosts folders that open, reorder, and hand apps in and out like any other — the folder hand-offs are
  continuous across it (closing a folder drops its zone, so the drag lands on whichever grid is beneath).
  **The dock starts empty** — an app lives in exactly one place, so seeding it would carve apps out of the main
  area, and *which* apps belong there is a default worth a picker; fill it by dragging.
- **An item carries its zone; a placement alone is not a location.** Each zone is its own coordinate space, so dock
  cell (0,0) and main cell (0,0) are the same `GridPlacement` value in different places. `HomeItem.zone` (mirroring
  `PlacedItem`) is what disambiguates them, and the zone is part of *identifying* a target, not just of writing the
  result — every per-zone derived list (grid contents, planner occupants, page count) must be built from one zone's
  items alone. Two bugs came from getting this wrong, both worth not repeating: matching a merge target on placement
  alone resolved a folder in the wrong zone, and scoping the *open-folder* lookup to MAIN left a tapped dock folder
  with its id set on the host and nothing to render (opening is zone-independent; merge *targets* are per-zone).
- **Layout: fixed-height dock, pager takes the rest, and no padding anywhere.** `DockHeight` is a flat placeholder
  constant — the dock's extent is meant to come from a **dock setting**, with its **row count derived from that
  extent and the icon size**, so any row-count-driven sizing inverts the real dependency. Home carries **no
  decorative padding** either (L1 had a configurable horizontal padding; L2 adds it *with* the setting, not before).
  The one applied inset is the bottom of `systemBars ∪ displayCutout`, so the dock clears the navigation bar — a
  system constraint, not styling. See the sizing rule in the design-system section.

**APPS surface — one module for every layout; the vertical list is the first.** `feature:apps`
(`inkspire.morphic.feature.apps`) is the whole surface: L1's `feature:appdrawer` + `feature:applibrary` were
deleted, not ported, because they rendered the same apps with the same launch behaviour and differed only in
arrangement — the model had already collapsed that into `Surface.APPS` + `AppsLayout`, and two modules made
"drawer or library?" a question that had to be answered before, and separately from, "which layout?".
- **`AppsScreen` is the one place a layout is chosen** — a `when` over `AppsLayout` above shared wiring (ViewModel,
  ordering, theme, background), so no layout can disagree with another about what the app list *is*. The unbuilt
  arms are listed individually rather than behind an `else`, so a new `AppsLayout` value fails to compile until it
  is rendered. `layout` is a **parameter with a default** — the real choice is per-binding user preference and
  belongs to `data:settings` (B7).
- **One `AppsViewModel` per surface, not per layout** (switching layout must not reload anything), exposing one
  `AppsState`. It has **no write path at all**: the list is a *derived* layout, so its order is a function of the
  app cache and nothing else. Ordering is a locale-aware `Collator` + a component tie-break, deliberately not L1's
  `sortedBy { label.lowercase() }` — that compares raw UTF-16, so every accented label sorts after `Z` and a
  Vietnamese or French list breaks into two alphabets.
- **Both *derived* layouts are built** — the vertical list and the vertical grid. They came first together because
  neither stores anything: each re-renders straight from the app cache, so between them the surface is proven end
  to end (repository → ordering → cells → launch) without the APPS **order** repository, which is what every
  remaining layout is blocked on.
  - **`AppsVerticalList`** — a `LazyColumn` of `AppRowCell` (`core:designsystem/cell`, the horizontal sibling of
    `AppCell`); **row height a flat placeholder**, icon sized from the list's own `IconMetrics`
    (`iconPercent = 1f` — no label underneath to leave room for).
  - **`AppsVerticalGrid`** — a `LazyVerticalGrid` of `AppCell`; **columns from the `AppsScrollGrid` blueprint**
    (`colsFor(device)`, since a `SCROLL_GRID` blueprint has no rows and so can't go through `toGridConfig`), cell
    height a flat placeholder, denser `IconMetrics` than home's. It is **not** `LauncherGrid`'s SCROLL_GRID mode:
    that composes every child at once, which is right for the bounded per-category page it was built for and wrong
    for hundreds of icon-baking cells. This is the grid plan's *right tool per surface* rule, and it costs nothing
    because a derived layout is never dragged **within** itself — so it needs no shared lattice and no published
    `GridGeometry` (a drag *out* is `EjectToHome`, which reads the finger).
- Cells go through the shared `launcherItemGestures` contract rather than a `clickable`, so APPS cannot drift from
  the rest of the launcher on long-press timing or slop — exactly what L1 did, hand-rolling a recogniser (plus a
  click-suppression flag) inside its list composable. The tap-only wiring lives in one `appsItemGestures` shared by
  both layouts, so a layout can't half-wire it and there's one file to change when the P7 menu and `EjectToHome`
  land. **A row's touch target is the whole row** — the same "visible extent" rule as a grid cell, not an
  exception: a row's visible extent *is* the full-width strip. Consequence: a list leaves no slack for a surface
  long-press.
- **The pager is the first layout that stores an arrangement** (`AppsLayout.PAGER`, `AppsPager`) — pages of
  `LauncherGrid` in FIXED_PAGER mode at `AppsPagerGrid`'s size, drawn from `AppsOrderRepository` rather than
  re-derived. Page capacity is a UI read (device → blueprint), pushed to the VM via `setPagerGrid`, exactly as
  home pushes its `GridConfig`. `AppsState` gained `pagerPages: List<List<AppsItem>>` alongside `apps`: both
  shapes are always maintained so switching layout reloads nothing, and one collector keeps the store in step
  with what is installed (first run, install/uninstall and a capacity change are all the same sync).
- **The pager drags by MovingGap, not push** — an ordered surface migrates a gap and lets the flow densify, where
  a coordinate one shoves occupants aside. Crossing pages reuses home's shape exactly: one drop zone is the whole
  viewport, page-swipe is gated off while an item is in flight, an edge dwell flips the page, and
  `keepAllPagesPlaced` keeps the source page composed so the lifted cell keeps its pointer stream. Two consequences
  worth knowing: the gap is an index **within one page** (pages are hard boundaries, so arriving on a page seeds a
  fresh gap rather than continuing the old), and the dragged cell **stays composed on its source page** even after
  the finger carries it elsewhere — both copies are invisible, the far one only reserving the gap, because
  disposing the near one would kill the drag. Still to come: folders (the merge ring, `FolderOverlay` hosting,
  extract, dissolve) and a page indicator.
- Not built: the alphabet filter strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing
  helpers into the list file — three concerns in one composable), search, drag-out-to-home (`EjectToHome`), and
  the two **category** layouts, which need the `category` + `category_item` store.

**Next likely:** a **home long-press → options menu** (the free cell space now falls through to the surface for
exactly this, and nothing listens yet), the folder **frosted backdrop** (currently solid black), **`data:settings`**
(which unblocks the dock's configurable extent + derived row count, and home padding — see the dock layout note
above), home **orientation**, widgets/containers on the grid, or `data:apps` categorization (B6). On APPS: the
pager's **drag + folders** (the next part — the store, the ordered-flow primitives and the render are all in
place), the alphabet filter strip, search, or the **category** store (`category` + `category_item`), which
unblocks the two category layouts and forces the folders-on-the-card question above. Folder follow-ups: rename, add-via-picker, cross-page reorder,
onto-an-app open-then-create. Not yet a launcher — the `HOME` intent category is added last (P9), the final flip.

**Known gaps, deliberate:** no item is reachable by an accessibility service — `launcherItemGestures` is raw
`pointerInput` with no `semantics { onClick { … } }` (P7 gestures). No formatter in the build (no
ktlint/spotless/detekt), so style drift isn't caught. The Gradle **wrapper is missing** from the repo
(`gradle/wrapper/gradle-wrapper.properties` is tracked but `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` are not),
so there is no CLI build from a fresh clone — Android Studio only.

## Conventions summary

- Kotlin + Gradle Kotlin DSL (`.kts`) throughout.
- Follow the existing module boundaries and the build order in the rewrite plan.
- Match surrounding style; keep KDoc current when changing a type's purpose.
- **`delay` takes a `Duration`, not a bare `Long`** — `delay(FooDwellMs.milliseconds)`, never `delay(FooDwellMs)`
  (`kotlin.time.Duration.Companion.milliseconds`). The IDE flags the `Long` overload, and this codebase is full of
  dwell/timeout constants, so the unit belongs at the call site where a wrong one would otherwise be invisible. The
  constants themselves stay `Long` with an `Ms`/`_MS` name; only the call converts.
