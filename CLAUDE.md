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
- **Never port Launcher 1 verbatim.** The original at `../launcher` (aka "L1", root Gradle project `Launcher`) is the
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

**Settled: neither category layout holds folders, so `category_item` stays keyed on `component`.** The reshape the
pager needed is **not** owed here — no migration. The **pager** (`PAGER_WITH_CATEGORY`) has one reason: a category
*is* the grouping, so a folder inside one would be a second, redundant one. Its pages are dragged between (carrying
an app to another page is how it changes category) and its cells split into **halves** with no centre merge ring,
since there is nothing to merge into — which is what `CategoryPagerPlayground` prototypes. The **card**
(`CATEGORY_CARD`) has that reason *plus* one of its own: a card is already a folder in everything but name — a
titled tile previewing a collection, which opens into a bounded grid — so a folder on a card nests a grouping inside
something that looks identical to it, and its 2×2 preview tile would have to render inside one of four preview slots
that are themselves 2×2, at ~20dp a side. Two independent reasons, either sufficient.

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
  (`Slider(state = …)`/`RangeSlider(state = …)`) — not the value-based overloads (deprecation path). But
  they expose a plain `value`/`onValueChange` API and create + bridge the state *inside* (`LaunchedEffect(value)`
  in, `snapshotFlow { … }.drop(1)` out), so call sites need no `remember*State`. **Exception — the text field
  keeps a hoisted `TextFieldState`** (`rememberTextFieldState()` at the call site): its config-change survival
  needs the caller to own the state, so hiding it would throw that benefit away.
  - **The sliders do *not* use `rememberSliderState`/`rememberRangeSliderState`, and must not be "fixed" back to
    them.** Those factories are `rememberSaveable(saver = …) { State(…) }` — an init block that never re-runs — so
    they freeze three arguments at first composition. Two of them matter here: `onValueChangeFinished` (a `var` the
    factory never re-assigns, so a facade hands the state the *first* composition's closure forever — which is what
    made `SettingsCommitSlider` commit its first drag and then re-commit that same value on every later one), and
    `valueRange`/`steps` (`val`s, so a slider whose range legitimately moves — the APPS row height is bounded by the
    icon guardrails — keeps mapping the finger through the range it was born with). So the state is a `remember` keyed
    on `steps`/`valueRange`, with the callback pushed in by `SideEffect`. What is given up is restore across a
    configuration change, and it is not load-bearing: `value` is the caller's, so the state is re-seeded from it.
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
- **A measured position is not trustworthy once the node is inside a scroller — reconstruct it.**
  `onGloballyPositioned` does **not** reliably re-fire when scrolling moves a node, because the scroll moves it through
  its *parent's* placement without re-running the node's own layout. So anything doing finger→cell maths against those
  bounds silently drifts by the scroll distance. Publish from a **stable anchor plus the scroll offset** instead: the
  scroll *viewport*'s position genuinely doesn't move (put `onGloballyPositioned` **outside** `verticalScroll` in the
  chain), and `scrollState.value` is snapshot state, so `viewportTop - scrollState.value` is the content origin and it
  republishes every frame for free. `CategoryPage` in `feature:apps` is the worked example. Two corollaries:
  - **The symptom is a *constant* offset, which is how you tell it from a timing bug.** With a stale origin at offset
    `S0` and a real offset `S`, a finger at `y` resolves to a cell drawn at `y - (S - S0)` — so the drop footprint sits
    a fixed distance from the finger, tracks it while dragging, and snaps into place the moment the content returns to
    `S0`. A lagging or jittering offset is something else; a rigid one is staleness.
  - **A correct geometry that nothing re-reads changes nothing.** `DragCoordinator` resolves a plan only in `moveTo`,
    i.e. when the *finger* moves — while auto-scroll (`DragAutoScrollEffect`) exists precisely to move content under a
    finger held still. A surface that auto-scrolls must therefore **re-send the same finger position** after
    republishing, and in that order (a `snapshotFlow` on the scroll offset fires *before* the recomposition that
    derives the new geometry, so it stays a step behind — put both in one `SideEffect`).
- **Don't invent a dimension nothing owns yet.** Launcher surface metrics (dock extent, home padding, icon size, grid
  rows) are **settings-driven by design**. Until `data:settings` exists, use the plainest possible stand-in — a named
  dp constant, or "fill the rest" — and KDoc it as a placeholder naming the setting that replaces it. Do **not** derive
  it from something else to make it look principled: derived-looking arithmetic reads as a decision when it is really
  a guess, and it hides that the value is still unowned (worse, it can invert the real dependency — the dock's row
  count comes *from* its height, so sizing its height from a row count is backwards). `RowHeight` in
  `AppsVerticalList` is the live example; `DockHeight` in `HomeScreen` was the worked one until S4c gave it an owner,
  and the placeholder deleted cleanly precisely because nothing had been derived from it.
- **But a dimension that is a *consequence* of one the user owns must be derived, not stored** — the other half of the
  rule above, and the two are told apart by whether a formula exists. A scrolling grid's cell **height** is the case:
  its columns fix the cell width, and what is left is exactly what the icon and its label need, which is
  `IconLabelCell`'s own arithmetic run forwards (`cellHeight` in `core:designsystem/grid/CellFit.kt`, ported from L1's
  `gridCellHeightDp`). Storing it *as well* would let two settings disagree — enlarge the icons and get bigger icons in
  cells that stayed the same height — where deriving it makes S3's icon sliders visibly move the grid, as they do in
  L1. The list's row height is the opposite case and the reason the distinction is worth stating: a list has no cell
  width to derive from, so nothing determines the row and it is genuinely the user's to set, with the icon a fraction
  of *it*.
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
`RawIconSource`, and **categorization** — `AppCategorizer` folds a curated asset → platform
`ApplicationInfo.category` → keyword heuristics into a `CategoryGroup` id, ported from L1 but narrowed to *one app
in, one id out*: ordering and overrides are the category store's job, not the classifier's. `AppEvent` live
updates/pruning still deferred).

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
`IconLabelCell` + `cellLabelHeight`, with `FolderCell`'s 2×2 tile extracted as `IconPreviewPlate` once the APPS
category card needed the same tile for its overflow cluster); and the full **folder subsystem** — `FolderOverlay`, `folderInnerSize`
(a `@Composable` facade over pure sizing arithmetic), `FolderReorder` MovingGap, `FolderDragDelegate`, and
**`FolderHostState`/`FolderPhase`** (the open/leave/enter lifecycle any collection-hosting surface reuses — **generic
in the collection id**, `Long` for a folder and `String` for an APPS category; 21 unit tests). Item gestures are scoped
to the icon+label group, not the cell — see the design-system rules above.

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
  knowing: `reconcileReportedOrder` (`ReportedOrder.kt`) folds a UI-reported order
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
- **Layout: the dock has a height of its own, the pager takes the rest, and there is no padding anywhere.** The dock's
  height is a **setting** (`SurfaceMetrics.dockHeightDp`, defaulting to `DockGrid.heightDp`) *and* its rows and
  columns are stored counts — the height does not replace the rows, it **bounds** them, since a cell is
  `height ÷ rows`. `CellFit.fitGridConfig` resolves a stored size against what an area can actually hold, from the two
  inputs only the surface knows (the measured width, and the type scale behind a label row). Deriving the counts from
  the extent was specified and abandoned on both axes: rows read as an editor missing half its buttons, and at the
  default icon guardrail a derived phone dock is nine columns wide against the four it has today. **A column clamp is
  applied on read and never written back** (shrink the icons again and the count returns); **a row reduction *is*
  written**, at the moment a height commit invalidates it — the asymmetry is that the height was a deliberate change,
  where an icon-size change is not about the dock at all. L1 wrote every clamp back from a `LaunchedEffect` *inside
  its dock settings screen*, which destroyed the preference and only ran while that screen was open. Home carries
  **no decorative padding** (L1 had a configurable horizontal one; L2 adds it *with* the setting, S4f). The applied
  inset is `systemBars ∪ displayCutout`, hoisted into one `safeInsets` value that feeds both the padding and the width
  the dock is fitted against — L1 kept two derivations of that area (`homeGridArea` from settings,
  `pagerBoundsInWindow` measured) and they could disagree.
- **A dock shrink spills onto home; it does not delete.** Fewer rows means cells that may hold items, and the strip is
  a *single page*, so there is no next page to push them to. `HomeViewModel.fitDockTo(config)` runs
  `GridReflow.reflow(…, Overflow.EVICT)` — which reports what a single-page grid cannot keep instead of inventing a
  page 1 nothing draws — and hands the evictions to `GridReflow.admit(…)`, which finds each a cell on the pager.
  L1's `DockGridEdit` **dropped** them (`droppedApps`/`droppedFolders`/`droppedWidgets`, deleted by the caller).
  It is a **command on the ViewModel, called when the config changes**, and idempotent, so no caller needs a "did it
  shrink?" test; L1 instead reflowed *inside the `combine` that assembles its home state* and launched the persist
  from within that transform, so the write was a side effect of reading state and raced itself.
- **The dock's height is subtracted from home, so growing it can invalidate home's rows too** — the same invalidation
  one grid over, and it is answered in the two halves this codebase already splits such things into. The **surface**
  fits the pager to what is left (`CellFit.fitGridConfig` over window − insets − dock height, the same expression the
  Home settings section computes its bounds from) and re-homes the displaced items to a further page
  (`HomeViewModel.fitMainTo`, the pager's `fitDockTo`); the **row count itself is written down** only by the dock
  section's height commit, which is the deliberate change that caused it. That is the dock's own asymmetry applied
  outward: a count invalidated by a committed extent is written, one invalidated by an icon-size change is clamped on
  read and returns. L1 did the clamp from the home *surface* (`LaunchedEffect` on its measured pager bounds — its
  comment names "the dock is turned back on") and wrote it on **every** cause, so an icon tweak permanently destroyed
  a row count that had nothing to do with it. L1 also measured home's area two ways (`pagerBoundsInWindow` on the
  surface, `homeGridArea` in settings) which could disagree; L2 has one expression. Note it takes a *large* dock or
  *large* icons to bite at all: home's smallest usable cell is ≈60dp at the default guardrails, so even a 320dp dock
  leaves room for eight rows against the five it stores. **Neither zone settles until the store has answered for it** —
  a blueprint fallback is a smaller grid than one the user has grown, and settling against it would make a transient
  first frame a permanent write.

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
  disposing the near one would kill the drag.
- **Folders on the pager work exactly as they do on home**, because the lifecycle is the same `FolderHostState`:
  tap to open, dwell on a merge ring to enter mid-drag, dwell outside the card to leave, drag an app back out onto
  a page or straight into another folder, auto-dissolve at the second-last app. The surface supplies only the one
  answer the host can't know — *"which folder does this merge plan target?"* — as a **slot** match, which is the
  ordered half of the split that lambda was built for. A merge plan's footprint is meaningful (it names the hovered
  cell) where a reorder plan's is not, which is what makes the slot resolvable. Cells are **three zones** here
  (outer thirds insert, centre merges), unlike the folder's and the category pager's halves.
- The pager's op set composes rather than special-cases: `RemoveFromFolder` takes an app out of membership and
  *nothing else*, so a landing pairs it with a `Move` (onto a page) or an `AddToFolder` (into another folder), and
  one batch commits both. `CreateFolder`/`DissolveFolder` name a **neighbour** instead of a slot — "this takes that
  thing's place" — because a slot index shifts as the folded apps leave the list. `reconcileReportedOrder` moved to
  `data:layout`, beside the whole-order ops it guards, now that several surfaces owe their writes that guard.
- Still to come on the pager: a page indicator, and an optimistic layer (a drop currently waits for the write).
- **The category pager** (`PAGER_WITH_CATEGORY`, `AppsCategoryPager`) — **one page per category**, each page a
  header plus a vertically-scrolling grid at `AppsCategoryGrid`'s column count. It uses `LauncherGrid`'s
  **SCROLL_GRID** mode where the vertical grid deliberately uses `LazyVerticalGrid`: same *right tool per surface*
  rule, opposite answer, because a category page is tens of apps and the vertical grid is all of them. It asks the
  VM for **nothing** — no capacity to report, since a category is one list — which is the difference between the two
  stores showing up in a signature. Empty categories keep their page, so one emptied by dragging can be refilled.
  Classification comes from `data:apps`' `AppCategorizer` (curated asset → platform category → keyword heuristics),
  run in the VM off the main thread and handed to `syncCategories` as `component → category id`.
- **Dragging on the category pager re-files by page.** Reorder within a page and move to another are one `Move` op:
  a page *is* a category, so the destination id carries the difference and there is no separate "change category"
  path. Cells split into **halves** (no merge ring — nothing to merge into). Two things differ from the APPS pager's
  drag and both come from the page scrolling: **geometry is published per page from its grid**, not once from the
  viewport, because the grid slides under the viewport and a finger→cell read against viewport bounds would name the
  cell that *used* to be at that height; and **the page's scroll is gated off mid-drag** the way page-swipe already
  is, since two vertical gestures otherwise fight over one finger. Content past the fold is reached by holding the
  dragged app near the top or bottom edge, which scrolls it — `DragAutoScrollEffect` (`core:designsystem/drag`), the
  vertical counterpart of `EdgeFlipEffect`, with speed ramping by how deep into the band the finger is so the
  boundary doesn't feel like a trapdoor.
- **The rule the category store lives by: a filed app keeps its category even when the classifier disagrees.**
  Classification runs on every launch, so treating it as authoritative would silently undo every drag at startup —
  an assignment is only ever a *first* answer, for apps with no answer yet. That also makes a user override free: it
  is simply the row already being there. Pages are the `CategoryGroup`s; the fine `AppCategory` taxonomy is how
  classification *reasons*, not what is displayed — which is what lets a new fine category be added without adding a
  page. **Rebalanced from 6 groups to 12** after the 6 proved lopsided on a real device (`INTERNET` and `UTILITIES`
  each absorbed 9 fine categories, so two pages held most apps and "Internet" was where you looked for your bank).
  The widest is now `UTILITIES` at 5, deliberately: it and `SYSTEM` are where the unclassifiable goes, and a user
  expects those to be broad.
- **A category that no longer exists cannot hold apps** — the bound on the rule above. `dropUnknownCategories`
  unfiles anything under an unrecognised id and deletes its definition row, so the classifier can place those apps
  again. Without it a rebalance strands them: the read is driven by the definitions table, so they would render
  nowhere while still occupying a row that `syncCategoryItems` counts as filed. It is the same path a user-deleted
  category will take once `known` grows to include user-created ids (L1's `u1`/`u2` prefix is the pointer).
- **The category card** (`CATEGORY_CARD`, `AppsCategoryCard`) — **the fifth and last APPS layout**: a lazy 2-column
  grid of square cards, one per category, each a header plus a 2×2 preview, tapped open to reach the rest. It shares
  the pager's store, so there is nothing to seed or classify here and switching between the two layouts shows the
  same categories with the same apps. Lazy (unlike the category *pager*'s `LauncherGrid` pages) because a card
  composes up to seven baked icons, so the card *count* is small but the icon count is not — the vertical grid's
  argument, not the pager page's.
  - **The expansion is a real `FolderOverlay`, not a lookalike.** That type's parameters were already a label and a
    list of apps with no folder id in them, because what it renders is *an ordered collection of apps opened over a
    surface* — and the grid it sizes itself from is `FolderGrid`, whose KDoc has always called itself the "folder /
    category-card grid". Reuse brings the paging, dots, MovingGap reorder and scrim for free. It does leave the type
    named for one of its two cases; a rename (`IconCollectionOverlay`?) would touch home, the APPS pager and the whole
    `folder/` package, so it waits for a *third* consumer to say what the honest name is rather than being guessed
    from two. No `FolderHostState` here: that machine exists for the phases an app passes through while its
    *membership* changes mid-drag, and a tap-opened expansion changes none.
  - **`AppsCategoryChange.Reorder` is the second category op, and it changes order only.** The expansion reports a
    whole list (it is the same overlay a folder uses), which a `Move` can't express without guessing which app the
    user dragged. Its guard sits **in the store**, not at the call site as `ReorderFolder`'s does — and that
    asymmetry is the point: a folder's membership reaches the UI intact (it *is* the folder definition), so the
    caller has something true to reconcile against, while a category's never does (the UI only sees what the app
    cache resolved). So the op is made incapable of changing membership instead. `reconcileFolderOrder` was renamed
    `reconcileReportedOrder` for the same reason.
  - **Two tap targets per card, with no overlap** — the "touch target is its visible extent" rule applied to a
    container: preview icons launch; the **header row** and the **overflow cluster** open the category. The empty
    slots and the padding stay free. The header is not a fallback for the cluster — a category of ≤ 4 apps has no
    cluster, and without it could never be opened. The cluster tile *is* `IconPreviewPlate`, extracted from
    `FolderCell` when this second consumer arrived so a folder tile and a cluster tile can't drift apart.
  - **Dragging between categories is the folder↔home gesture, on cards** — the same `FolderHostState`, so the rules
    are that machine's and not this layout's: dwell on a card (~1s) to expand it mid-drag and place the app at a
    *chosen* slot, dwell outside an expansion to close it with the drag carrying on over the cards, drop straight on a
    card to append with no dwell, repeatable in any order including re-entry, membership decided only at the drop.
    Reaching a card past the fold is `DragAutoScrollEffect` (widened to `ScrollableState` so a lazy grid can use it);
    manual scroll is gated off mid-drag as on every other dragging surface.
    Three things differ from home, all of them properties of the surface rather than choices:
    - **A drag starts inside an expansion, never on the card grid.** Home has loose apps to pick up; here every app is
      filed in exactly one category, so nothing sits *on* the surface — expansion→card *is* the whole gesture, the
      analogue of home's folder→folder. Preview icons stay tap-only (a folder's preview tile isn't draggable per-icon
      on home either), which is also what lets the grid stay **lazy**: nothing on it owns a live pointer stream, so a
      card disposed by auto-scrolling can't kill the drag. Making previews draggable would pin the source card in
      composition, which a lazy grid cannot honour.
    - **There is no "empty cell" landing.** Off a card there is nowhere for an app to be, so the planner reports *no
      plan* and a release there is a cancel — which is why `MERGE` is the only intent this surface ever reports.
    - **Landing owes the source category nothing.** `Move` unfiles the app from every other category as part of filing
      it, so one op is the whole re-file, where the pager pairs `RemoveFromFolder` with a `Move`. Dropping back on the
      category the app came from is a no-op, as on home.
    Hit-testing is a **per-card bounds map**, not a `GridGeometry`: cards are lazy, square and separated by spacing
    that belongs to no cell, so there is no lattice to compute from. Entries are added as cards lay out and removed as
    they scroll away.
  - **`FolderHostState`/`FolderPhase` became generic in the collection id** (`Long` for a folder, `String` for a
    category) so this surface could *use* the open/leave/enter machine instead of growing a near-copy of it — the L1
    `resolveDockDrop` mistake this codebase keeps un-making. Nothing in the lifecycle reads an id beyond comparing it,
    which is what made the parameter free; a unit test pins that with `String` ids. **Naming is now one commit behind
    the code**: `Folder*` covers folders *and* categories, and the honest vocabulary is a *collection of apps opened
    over a surface* — a rename reaching `FolderOverlay`, `FolderDragDelegate`, `folderInnerSize`, `FolderReorder` and
    every call site is worth one mechanical commit of its own, flagged as a TODO rather than mixed into behaviour.
  - Still to come: an optimistic layer (a drop waits for the write, so a re-file lands a frame or two late — the
    `Injected` phase is what stops the app blinking out meanwhile).
- Not built: the alphabet filter strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing
  helpers into the list file — three concerns in one composable), search, drag-out-to-home (`EjectToHome`), and
  category **management** (create/rename/delete/reorder — a `feature:settings` concern, which is also why a card
  carries no menu and cannot be dragged).

**Next likely:** a **home long-press → options menu** (the free cell space now falls through to the surface for
exactly this, and nothing listens yet), the folder **frosted backdrop** (currently solid black), **`data:settings`**
(which unblocks the dock's configurable extent + derived row count, and home padding — see the dock layout note
above), home **orientation**, or widgets/containers on the grid. On APPS, **all five layouts render and all the
arrangement-owning ones drag**; what is left is the surrounding behaviour: the alphabet filter strip, search,
`EjectToHome`, an optimistic layer for both the pager and the card (a drop waits for the write) + the pager's page
indicator, or `data:apps`' `AppEvent` live updates/pruning (B6). One **mechanical** job is queued and deliberately
unmixed: renaming the `folder/` package's vocabulary now that it hosts categories too (see the card's notes). Folder
follow-ups: rename, add-via-picker, cross-page reorder, onto-an-app open-then-create. Not yet a launcher — the `HOME` intent category is added last (P9), the final flip.

**Settings — `data:settings` (B7) is real and two sections are live.** Storage is **one `@Serializable` JSON blob per
slice** under one DataStore key (`SettingsSlice`, pure and unit-tested), not L1's ~265 flat keys behind a 693-line codec;
per-slice flows, not one god flow; and a slice carries no version because `ignoreUnknownKeys` + fully-defaulted fields
make additive change safe both ways, with the **key name** as the seam for a semantic break. Two slices exist:
`SurfaceRegister` (HOME's layout, per-edge `SideBinding`, transition) and `SurfaceMetrics` (per-grid **icon** and **grid**
overrides). Overrides are **sparse and doubly so** — keyed `GridSlot` × `DeviceConfiguration`, nullable per field, and an
emptied entry is *removed*, which is what keeps "a default lives in exactly one place" literally true (the blueprint) and
makes "reset" a plain write of nulls. Reads are **resolved in the repository** (`iconSizing`/`gridConfig`/`gridCols`), so
no surface sees the keying. `GridSlot` names the launcher's eight grids and lives **on** `GridBlueprint` so the two
cannot drift; `GridBlueprints` proves the mapping total. `feature:settings` is **one destination whose sections are panes**, ported from L1's shell: a section list beside a
detail on a tablet, sliding between the two on a phone, with `SettingsSection` as ordinary state inside
`SettingsScreen`. An earlier cut gave each section its own `NavKey`; that was reversed because a pane which shares the
screen with another is not a destination. L1's *actual* mistake is still avoided — it declared that enum in the
**navigation module**, so `feature:home` could import `SettingsSection.WALLPAPER`; ours never leaves the feature.
**A section belongs to a surface and holds everything about it**, layout controls *and* icon sizing, exactly as each
of L1's five details embedded `IconLayoutControls` under its layout section. Home, Dock and Apps have theirs; the
standalone icon-sizing screen is down to the **folder grid alone** and stays because the **icon studio** (B9, per-app)
will live there. `IconSizingControls` shares the UI and `IconSizingEdits` the write commands, so a fourth section costs
neither. Still missing beside L1: the live icon **preview** between the two groups, which punches through to the
wallpaper and so waits on `data:wallpaper`.
**The APPS section is one section with a chip per layout** — the settings mirror of one `feature:apps` for five
layouts, and the same argument: they differ only in arrangement, so what a user configures is "the paged one" or "the
list". Selecting a chip writes nothing (which layout you *get* is per home edge, in the register). Its resize is **one
write** where home's is two, and that is the ordered/coordinate split reaching settings: every APPS grid is ordered or
derived, so the flow re-densifies and there is nothing to displace — the pager's store even re-paginates itself, since
a capacity change is the sync it already runs for installs. Only the edge's *axis* is read, as in L1's drawer editor.
The **list** is the odd one: one lane, so it has no grid to edit and its size *is* its row height
(`AppsListGrid.rowHeightDp`, the third way a cell gets a height — see the derive-vs-store rule in the design-system
notes). **Its slider's bounds are the icon guardrails plus the row's own inset** (`rowHeightRangeDp`), so the icon range
slider *governs* the row-height slider: a row shorter than `minIconDp` + padding cannot honour the smallest icon
allowed, and one taller than `maxIconDp` + padding is height the largest cannot fill. So the way to ask for a taller row
is to raise the upper guardrail. `iconPercent` is deliberately not in it — the same rule as the grid, and for the same
reason: dividing by it inverted the control, so asking for *smaller* icons pushed the row **taller** (a 56dp row clamped
up to 72dp at 50%). A stored height outside the range is clamped on read by `fitRowHeightDp` — in the list *and* in the
slider, so the control never sits at a height the surface isn't using — and never written back, so the user's number
returns when the guardrails widen. **With icons off, no guardrail applies and the range changes shape**: the floor
becomes the *label's* own height (a row cannot be shorter than its text — `rowLabelHeight`, the row twin of
`cellLabelHeight`, since a row styles its label from `bodyLarge` where a cell uses `labelSmall`) and the ceiling **opens
up** to `IconSizingRanges.IconDp`'s own ceiling. Both ends then stop following the guardrails, which is the point: they
describe an icon that isn't there, and bounding a pure-text row by one forbade a compact list to anyone who had set
chunky icons before switching them off. **Left open: the category card's lane count** — a card is a *tile*, so how narrow one
may get is not an icon guardrail and its blueprint declares no icon sizing; L1 gave its library layout no grid knobs
either.
**Resizing a grid names an edge, not a count**, because that is what decides where the items go — removing the *left*
column shifts everything left, removing the right one drops what sat there. So a press is **two writes**: the count
(`updateGrid`) and the placements it displaces (`GridReflow.edit` → `LayoutRepository.apply`), ordered grow-first for
an add and place-first for a remove so no observer sees a grid too small for its contents. That is why
`feature:settings` depends on `data:layout` at all: only the button press knows the edge, and a surface re-reading the
new size later cannot recover it. **The dock's version spills onto home** (`settleDock`, shared with
`HomeViewModel.fitDockTo` so the two triggers cannot disagree), never deleting as L1 did. One `GridEditor` composable
serves both grids — a screen-shaped preview with a − / + pair **on each edge it affects**, the companion zone drawn at
its real proportion; L1 had two ~220-line near-copies and told add from remove by green vs red, which this palette
(greyscale, red reserved for `error`) cannot do, so the buttons sit where they act instead.
**A grid editor shows the grid that is *drawn*, not the one in storage** — and that is what makes the icon controls under
it move it. Both halves come out of one formula: the icon **guardrails** set the smallest usable cell, and dividing the
area by it gives both the *bounds* the buttons offer and the *counts* the preview draws (`fitGridConfig` for home and the
dock, `fitCols` for the APPS scrolling grids, whose surfaces apply the same clamp to their own measured width).
**Only the guardrails, plus the label controls on the row axis — never `iconPercent`.** A cell's floor is
`minIconDp + cellPadding`, because `resolveIconSize` clamps *up* to the guardrail and so a cell overflows exactly when
the guardrail exceeds its inner width; the fraction scales the icon *within* those bounds and cannot make a cell
unusable. Dividing the guardrail by the fraction (L1's `scrollingMaxColumns`, adopted by the first port) answers a
different question and inverts this one — at 30% a 28dp guardrail demands a 101dp column, so shrinking the icons reports
*fewer* columns. L1's home editor used `gridMaxima`, which leaves the fraction out and is the right shape; it just used
the bare guardrail as the whole cell and forgot the inset. A press
then counts from the drawn number, so `−` on a four-row home writes three instead of writing four because storage still
remembers five. The clamp is still **never written back** — shrink the icons and the row returns; only a press writes,
the dock's height commit staying the one deliberate exception. L1 reconciled it the other way, from a `LaunchedEffect`
inside its home detail that wrote clamped counts into storage on *every* cause, so an icon tweak destroyed a row count
for good and only while that screen was open. **The APPS pager is the one grid whose fit could not stay in a UI**: its
rows × cols is also the page *capacity* `AppsViewModel` paginates the store against, so a count clamped where it is drawn
would describe pages the store never made, and a drop would compute its slot against a capacity the store does not
apply. So the fit is *reported* instead — `AppsScreen` measures, fits the stored grid, and calls
`AppsViewModel.setPagerFit`, which becomes the capacity everything downstream reads. Two properties of that make it
safe: the report is **gated on the store having answered** (paginating against a blueprint placeholder would write pages
nobody chose and then rewrite them — pagination *writes*), and it is a **runtime bound** rather than the removed
`setPagerGrid`'s blueprint-derived default, which is the distinction that keeps `setDevice` the input it was made.
`core:designsystem/grid/CellFit.kt` answers "how large can this grid be": `boundsIn`/`editableRangeIn` as a **ceiling**
for a grid whose counts a user picks, and `fitGridConfig` reading the same maximum as a **value** for the dock's rows.
Beside it, `usableWindowArea` is the **one place the launcher measures the screen** — home, the APPS surface and every
settings section read it, because a settings screen cannot measure the surface it configures and so must measure the same
window the same way (L1 kept `homeGridArea` in settings and `pagerBoundsInWindow` on the surface, which could disagree).
The **dock section** (S4c, done) is the first consumer of both and the pattern the rows/cols editor follows — its
bounds are computed where the window is measured (one usable cell up to a third of the screen) rather than stored,
because a store cannot check either without measuring. **Every surface now reports its `DeviceConfiguration` to its ViewModel** (`setDevice`), which *replaced* the
narrower `setGridConfig`/`setPagerGrid`: pushing the input down means page capacity and icon sizing both derive there.
Full plan, phase state and the settled dock spec: [docs/SETTINGS_PORT_PLAN.md](docs/SETTINGS_PORT_PLAN.md).

**Navigation + shell (B5) done.** `core:navigation` holds type-safe Nav3 `NavKey` destinations and a two-method
`Navigator`; feature vocabulary stays *out* (L1 exported an 11-value `SettingsSection` to every consumer). `app`
declares its own dev-harness key, since `entryProvider` is a mapping and not a registry. `feature:shell`'s
`LauncherShell` is the launcher — `SurfacePager` with `HomeScreen` centre and side surfaces from the register — and it
owns the **launcher theme boundary**, which is why `HomeScreen`/`AppsScreen` no longer theme themselves. The launcher
boots into it; the dev harness (all playgrounds + the component gallery) is kept as a peer destination reached from a
row in settings, so no dev chrome ships on a real surface. A gear chip over HOME is the admitted scaffolding standing in
for the P7 long-press menu.

**Known gaps, deliberate:** no item is reachable by an accessibility service — `launcherItemGestures` is raw
`pointerInput` with no `semantics { onClick { … } }` (P7 gestures). No formatter in the build (no
ktlint/spotless/detekt), so style drift isn't caught. The Gradle **wrapper is missing** from the repo
(`gradle/wrapper/gradle-wrapper.properties` is tracked but `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` are not),
so there is no CLI build from a fresh clone — **but a Gradle 9.6.1 distribution is already cached**, so
`~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle :app:assembleDebug` builds and tests from the
CLI today. `feature:settings` and `feature:apps` declare junit but have **no tests**.

## Conventions summary

- Kotlin + Gradle Kotlin DSL (`.kts`) throughout.
- Follow the existing module boundaries and the build order in the rewrite plan.
- Match surrounding style; keep KDoc current when changing a type's purpose.
- **`delay` takes a `Duration`, not a bare `Long`** — `delay(FooDwellMs.milliseconds)`, never `delay(FooDwellMs)`
  (`kotlin.time.Duration.Companion.milliseconds`). The IDE flags the `Long` overload, and this codebase is full of
  dwell/timeout constants, so the unit belongs at the call site where a wrong one would otherwise be invisible. The
  constants themselves stay `Long` with an `Ms`/`_MS` name; only the call converts.
