# CLAUDE.md

Working guidance for **Morphic Launcher 2** — an Android launcher, mid-rewrite.

## What this project is

A ground-up rewrite of Morphic Launcher (**"L1"**), aimed at a codebase that is clean at every layer.
It is a **refactor, not a re-type**: L1 runs, but it is fragile and smell-ridden, so it is the reference
to measure against and improve on — never the thing to copy.

**[docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md)** is the source of truth for *what* to build and *in what
order*; read it before anything structural. Module state and the record of how each piece arrived:
[docs/STATUS.md](docs/STATUS.md).

## How to work here

### Delivering work

- **A change is one idea.** Size follows the idea: a coherent change is not chopped up to hit a line
  budget, and two unrelated ideas do not share a commit.
- **Commit straight to `master`; never create a branch unless asked.** One developer, nothing released,
  no CI — a branch buys nothing and costs a merge nobody asked for. This overrides any default habit of
  branching off the main branch.
- **A green build is not "done".** Compiling proves nothing about behavior. Anything touching a surface,
  a gesture, persistence or rendering is verified on the device before it is called finished, and the
  commit waits for that confirmation.
- **State the decisions in the summary** — what was chosen, what was rejected, and why. That is what a
  diff cannot show; it belongs in the summary rather than spread through the code as commentary.

### Reading L1

- **Never port L1 verbatim.** Per piece: understand what it does and *why* → question the design
  (duplicated? honest name? right layer?) → fix the smell here. Never delete L1; it is the answer key.
- **Locate it before assuming a path.** L1 is a sibling of this repo — `../Morphic-Launcher` on one
  machine, `../launcher` on the other. Run `ls ..` when it is first needed. A wrong guess fails
  silently: a missing directory reads as "L1 has nothing on this", so the comparison this rule exists
  to force is skipped rather than reported.

### Writing code

- **A file holds one thing** — one screen, one component group, one concern. Length is the symptom, not
  the rule: a long file that is genuinely one thing is fine, while a short one holding three unrelated
  composables is not. A file is not a folder for whatever happened to be open.
- **A function does one thing, and long ones are usually several.** A composable past ~80 lines is
  normally hiding sections that want names.
- **Extract on the second consumer, not the first.** A near-copy is how two implementations of one thing
  drift apart; a shared type built for a single consumer has no context to shape it. The trigger is the
  second call site arriving, and it is not optional then.
- **Literals are fine — name a value when it earns a name.** It earns one when it has two readers, or
  when it stands in for a setting that does not exist yet, in which case name that setting. A constant
  read once in one file is an indirection, not a name.
- **No model in a vacuum.** Build a module or type when a consumer needs it, so it has context to be
  shaped by. Dependencies likewise: added to `build.gradle.kts` as the code needing them is written.

### Documenting code

- **KDoc every type, and say what it is *for*** — purpose, not a paraphrase of the signature.
- **Document what the code cannot say**: units, edge cases, the reason behind a non-obvious choice, and
  anything that fails *silently* when it is wrong. Those earn their space.
- **Do not document what the signature already says.** A KDoc that would still be true if the
  implementation changed is restatement — leave it out.
- **Do not document history in KDoc.** What a function replaced, what it used to be called, which slice
  introduced it — git holds all of that, and it is the part that goes stale first. `setPagerGrid` is
  explained three times in one file as the thing `setDevice` replaced, and has not existed for a while.
  If the story is worth keeping it goes in `docs/`. `tools/check-stale-docs.sh` finds these.
- **A rule is its bound plus one line of why.** Derivations, worked examples and rejected alternatives
  belong in `docs/`, not inline. This file reached 3,400 lines by keeping every one of them, and the
  code takes its register from this file.

### Standing rules

- **Absent, not disabled.** A control that changes nothing is worse than a missing one, so a verb with
  no op behind it does not appear. One exception: a control gated by a *continuous* control beside it is
  disabled rather than hidden, because hiding it moves the layout under a finger mid-drag.
- **Two implementations of one thing are kept honest by a shared derivation, never by intention.**
  Wherever a second path must agree with a first — the two icon renderers, a settings preview and the
  surface it configures, a planner and the geometry it plans against — extract the part that would be
  *invisibly* wrong and have both call it. Arithmetic that merely looks alike in two files is the bug
  this codebase keeps rediscovering.
- **A settings key's name is the seam for a semantic break.** Slices carry no version: additive change
  is safe both ways through `ignoreUnknownKeys` plus fully-defaulted fields. When a stored shape changes
  *meaning*, rename the key — re-interpreting it in place fails silently.

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
- **HOME's vertical list is a store of its own, seeded from the grid — not a view of it.** `HomeListRepository`
  (`data:layout`, the third repository beside `LayoutRepository` and `AppsOrderRepository`) owns `home_list_item`,
  and the split is what makes HOME's two pairings independent. L1 derived its list *live* from the pager's
  placements, flattened by (page, row, col), so one drag in the list wrote `MoveApp(page = 0, row = i, col = 0)` for
  every app and destroyed the grid arrangement permanently. The good half of that idea survives as
  `seedIfEmpty(readingOrder(...))`, run when the pairing is first chosen: switching to the list hands the user their
  apps in the order they already recognize, rather than a blank screen with no picker to fill it. Membership is fixed
  by `setOrder`, which **reconciles against what is stored** (`reconcileReportedOrder`) — the guard is in the store
  rather than at the call site, as `AppsCategoryChange.Reorder`'s is, because the caller has nothing true to
  reconcile against.

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
an app to another page is how it changes category) and its cells split into **halves** with no center merge ring,
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

## Icon feature — layer editor + baked display

The icon system is a **layer editor** (like a drawing app) whose output is a **single flat bitmap**
shown on every surface — plus one thing drawn live and *outside* that bitmap: the **plate**, a
silhouette of blurred wallpaper that depends on where the icon is and so cannot be baked at all. The
stored unit is an `IconAppearance` (recipe + plate + zoom). The model lives in `core:model.icon`, both
renderers in `core:icon`, the editor in `feature:settings/iconstudio`, persistence in `data:icons`
(one serialized blob per detached app — **not** flat columns; L1 burned four destructive DB bumps
learning that).

**Two renderers is the standing hazard of this subsystem.** An icon that looks right while being
edited and wrong on every surface is a bug the editor structurally cannot show you. The agreement
between the baked path and the live path is made of **shared derivations, never shared intentions** —
read the doc before touching either.

Full design record — layer model, both render paths, all nineteen effects, the studio, the plate,
persistence: **[docs/ICON_ARCHITECTURE.md](docs/ICON_ARCHITECTURE.md)**.
Plans: [docs/ICON_STUDIO_PLAN.md](docs/ICON_STUDIO_PLAN.md) (S1–S8),
[docs/ICON_EFFECTS_PLAN.md](docs/ICON_EFFECTS_PLAN.md) (the effect expansion; §8 is phase 2).

## Design system (`core:designsystem`)

- **Keep Expressive *motion*, drop Expressive *visuals*.** New/reworked UI uses Material 3 **Expressive
  motion** but not its look. `LauncherTheme` sets `motionScheme = MotionScheme.expressive()` on the base
  MaterialTheme; components get the expressive spring choreography by consuming `MaterialTheme.motionScheme`.
  The Compose BOM does **not** carry the Expressive APIs, so `material3` is pinned to `1.5.0-alpha22` in the
  version catalog; opt in per-usage with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where the
  compiler asks.
- **Monochrome palette.** Grayscale chrome so the wallpaper + app icons carry the color. `accent` is a
  high-contrast grayscale *emphasis* (not a hue) — selection/active read by contrast; **red is reserved for
  `error`** only. **Both light and dark are first-class** (dark mode is an accessibility barrier for some
  users). Semantic tokens live in [theme/MorphicColors.kt](core/designsystem/src/main/kotlin/inkspire/morphic/core/designsystem/theme/MorphicColors.kt).
- **Theme layering + monochrome M3 bridge.** `MorphicTheme` provides our colors only (`LocalMorphicColors`).
  `LauncherTheme` = M3 base + expressive motion + `MorphicTheme`, and it feeds MaterialTheme a **monochrome
  M3 `ColorScheme` bridged from `MorphicColors`** (`MorphicColors.toM3ColorScheme(dark)`), so stock M3
  components render grayscale *and* keep Expressive motion. Use `LauncherTheme` as the app wrapper.
- **Build components *on* M3, restyle — go fully custom only where M3 has no equivalent.** Because the scheme
  is bridged monochrome, wrap the real M3 component and get its native Expressive motion for free:
  `MorphicButton` = the M3 button family + `ButtonDefaults.shapes()` (press shape-morph); `MorphicSlider` =
  M3 `Slider` with a custom `thumb` slot over M3's own track (our grow-on-press thumb, M3's track + grab). Reserve fully-custom `Row`/`Canvas`
  for controls M3 lacks — the **2D pad**, the **segmented control** and the **switch**. The **range slider** is built on M3
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
- **Settings vs launcher color = one theme, two "is-dark" inputs** (not two palettes). Settings feeds
  `darkTheme = isSystemInDarkTheme()` (our controlled surface); the launcher feeds a **wallpaper-brightness**
  signal (chrome must contrast the wallpaper — bright wallpaper → Light scheme/black tint, dark → Dark/white).
  Apply the theme per **zone boundary** (launcher shell vs settings graph), not per nav destination; a nested
  `LauncherTheme` overrides its subtree. **The brightness half is built, and so is the frosted backdrop** (see [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md));
  `FrostedTextField` and the rest of the frosted-chrome subsystem stay deferred; settings needs none of it. **The window
  half has landed**: `app`'s theme carries the platform's own `Theme.Wallpaper` recipe (`windowShowWallpaper` over a
  transparent `windowBackground`, `colorBackgroundCacheHint` null), which is what makes this a launcher's window rather
  than an app's — and what capture, the icon preview's `BlendMode.Src` punch-through and the frosted backdrop were all
  waiting on. Home paints **no background** as a result (it *is* the wallpaper, and its cell labels already carry a
  shadow); **APPS and the folder are transparent too, now that the frost they promised exists** — see the full-screen-frost note in
  [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md). The note here used to say "APPS stays opaque, which is legibility rather than
  inconsistency", naming L1's frosted backdrop as what would replace it; that is what `SurfaceBackdropLayer` is.
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
  - **And the rectangle it reports must be *unclipped*, which `boundsInRoot()` is not.** That call clips to every
    ancestor's bounds, and a scroller clips — so a node below the fold reports an **empty** rectangle and one half
    off the top reports half of itself. Anything hit-testing against it then silently refuses, which is not a
    misplacement you can see: it reads as "that target just doesn't work". Use `positionInRoot()` + `size` for a node
    *inside* a scroller; `boundsInRoot()` stays right for a **viewport**, which is above the clip it applies. The
    APPS category card is the worked example, and it only became reachable when that grid stopped being lazy —
    while it was lazy an off-screen card did not exist, so the difference between "not composed" and "composed and
    lying" never arose.
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
  `IconLabelCell`'s own arithmetic run forwards (`derivedCell` in `core:designsystem/grid/CellFit.kt`, ported from L1's
  `gridCellHeightDp`). Storing it *as well* would let two settings disagree — enlarge the icons and get bigger icons in
  cells that stayed the same height — where deriving it makes S3's icon sliders visibly move the grid, as they do in
  L1. The list's row height is the opposite case and the reason the distinction is worth stating: a list has no cell
  width to derive from, so nothing determines the row and it is genuinely the user's to set, with the icon a fraction
  of *it*.
  - **A fraction spent on a derived height must not be spent again by the cell**, which is why `derivedCell` hands back
    a height **and** the metrics to draw with (`iconPercent = 1f`) and why its callers must use both. The height *is*
    `iconPercent` of the inner width plus the chrome, so a cell given the original metrics resolves its icon as
    `iconPercent × min(innerWidth, iconArea)` where `iconArea` is already that product — `iconPercent²` of the width,
    which lands on the lower guardrail inside a row built for something larger (at 50% on a 4-column phone: a 24dp icon
    in a row sized for 41). L1 had the same double-application (`gridCellHeightDp` × `resolveIconSize`) and this port
    inherited it; nothing showed until the icon defaults reached 100%, where the two agree. So on these grids the user's
    fraction chooses the **cell height**, and the icon then fills exactly what that height bought — one job each.
- **Insets are one expression, `uiInsets` (`insets/UiInsets.kt`), and a surface paints *through* them.** L1's helper,
  finally ported: `systemBars ∪ displayCutout` — deliberately not `safeDrawing` (nothing on a launcher surface takes
  text, so reserving an IME that never appears is a permanent gap) and deliberately not `systemBars` alone (a notch is
  not a system bar). It had been written out longhand in eleven files, which is how a launcher ends up with home
  honoring the cutout and its settings screen not. `Modifier.uiInsetsPadding(sides)` is the padding form —
  `@Composable`, not L1's deprecated `composed { }`.
  - **The bars are *content* padding, never layout padding, on any surface with a background.** The window is
    transparent under this launcher's theme (`windowShowWallpaper`), so a container that stops short of the window edge
    does not leave a blank strip — it leaves a **hole through to the wallpaper**. That is what the settings shell did:
    `Scaffold`'s default `contentWindowInsets` reserved the navigation bar, and with `containerColor = Transparent`
    (which the icon preview's `BlendMode.Src` punch requires) the reserved strip showed the wallpaper. So both scaffolds
    zero `contentWindowInsets`, each pane fills to the window edge and paints itself, and the insets are applied *inside*
    — `contentPadding` on `SettingsList` (so rows scroll under the bar) and `uiInsetsPadding` inside `PunchThroughPane`.
    The FAB then pads itself, since the scaffold's reservation is what normally lifts it clear. L1 zeroed the same two
    fields, on exactly the sections whose detail shows the wallpaper.
  - **Which sides is the shell's answer, not the pane's** — a pane beside another pane must not inset the edge its
    neighbor covers, or the gap opens in the middle of the screen. So `insetSides` is a parameter on both `SettingsList`
    and `PunchThroughPane`: horizontal+bottom on a phone, start+bottom / end+bottom either side of the tablet divider
    (L1 passed the same two to its two-pane list). The **top** is nobody's: the app bar covers the status bar and
    `consumeWindowInsets(innerPadding)` says so, which is why the panes below can ask for every side and get nothing
    back on top.
  - The **top app bar** takes `uiInsets` too, because M3's default (`systemBars` only) would seat its back button under
    a landscape notch. `MainActivity` sets both bars scrimless before `super.onCreate` and turns
    `isNavigationBarContrastEnforced` off on API 29+ — `enableEdgeToEdge` leaves it on, and it lets the system paint a
    translucent scrim over the wallpaper. L1 did both, in that order, in that place.
- **Packaging discipline (unlike L1):** `component/` holds *only* the generic `Morphic*` UI primitives;
  colors/theme live in `theme/`; launcher-specific icon cells (`AppCell`/`IconMetrics`) get their own
  package. Do **not** mix generic components and app-icon widgets in one package like L1 did.
- **Port per-consumer.** L1's `core:designsystem` is ~50 files / 5.4k LOC — pull a group only when the
  screen that needs it is built, not up front.
- **Subsystem detail lives in [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)** — why individual
  components are shaped the way they are (`MorphicSwitch`, `MorphicColorPicker`, `AppPicker`), how the
  frosted backdrop and the full-screen frost work, how the wallpaper-brightness signal is derived, and
  the grid's horizontal-padding rules. Read it when working on one of those; the rules above are the
  ones that apply to any UI task. `data:wallpaper`'s own state moved to
  [docs/STATUS.md](docs/STATUS.md), being module status rather than a design-system rule.

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
  - **That last clause is only true because `NavDisplay` is given `rememberViewModelStoreNavEntryDecorator()`**,
    and it was false for a long time. `navigation3-runtime` ships only the saveable-state decorator, so by default
    every `NavEntry` shares the **Activity's** store: a `koinViewModel<T>()` keys on the type, so the first entry
    to ask for a given ViewModel created it and every later entry — different destination, different arguments —
    was handed that same instance, with `viewModelScope` outliving the screen it belonged to. It stayed invisible
    because no ViewModel took a **per-instance parameter** until the icon studio's route; every other one reads the
    same repositories and rebuilds the same state, so a shared instance was indistinguishable from a fresh one.
    Supplying the decorator list **replaces** the defaults, so the saveable-state one has to be named again or
    every entry loses its `rememberSaveable` state.
- **Keep logic out of the composable.** The composable reads `state` (via `collectAsStateWithLifecycle()`) and
  calls methods; assembly, persistence, and optimistic state live in the ViewModel so it stays unit-testable.
- **Reference:** [feature/home](feature/home/src/main/kotlin/inkspire/morphic/feature/home) — `HomeViewModel`
  (StateFlow + `launch`/`applyChanges`), `HomeScreen`, `HomeState`, `di/HomeModule`.

**Feature modules own their screens; `app` only assembles.** Each screen lives in its own `feature:*` module (not
in `app`), applying the `launcher.android.feature` convention plugin — which is why that plugin pre-wires
core:model/common/designsystem + lifecycle-viewmodel + koin-compose + Compose. A feature adds only the extra
deps it needs (e.g. `feature:home` adds `data:apps` + `data:layout`). The `app` module is the shell: it starts
Koin with every module's DI graph and hosts the entry points. New UI starts in a `feature:*` module from day one — do
**not** prototype it in `app` and "extract later".

**Repository vs command split.** A repository is *read/refresh* access to data (e.g. `AppRepository` streams the
app cache). A side-effecting *command* gets its own honest type rather than being bolted onto a repository — e.g.
launching is `AppLauncher` (`data:apps`), a one-method interface, not a `launch()` on `AppRepository`. (L1 folded
launch onto the repository; we don't.)

## Current status

The launcher runs: HOME (both pairings), all five APPS layouts, widgets, the drag toolkit, eight
settings sections, the icon studio, wallpaper. Foundations P0–P1 are done and P9 is flipped — this
declares `category.HOME` and resolves from the home button.

What is built per module, why each piece is shaped the way it is, and what is deliberately deferred:
**[docs/STATUS.md](docs/STATUS.md)**. Phase checklist and per-module build map:
[docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md).

**Standing gaps worth knowing before starting anything:**
- **detekt runs on `check`, over a baseline.** `config/detekt/detekt.yml` states only the departures from the
  defaults, and each module's `detekt-baseline.xml` records what was already there the day it was switched on —
  so the build is green, new code is checked, and the backlog is a list to work down rather than a wall. Fixing a
  finding means deleting its entry.
- **ktlint runs on `check` too, over a hand-picked ruleset** — unused imports, import order, trailing whitespace,
  final newline, file naming. Its *whole standard set is off* in `.editorconfig`: run with the defaults it reported
  5,032 violations against code nobody had complained about, and autocorrecting them touched 138 files without
  converging. Wrapping and indentation stay this codebase's own; sizes and shapes are detekt's, at thresholds tuned
  here rather than to a style guide. `gradle ktlintFormat` fixes everything it reports.
- **The Gradle wrapper is missing** from the repo (`gradle-wrapper.properties` is tracked; `gradlew`,
  `gradlew.bat` and the jar are not), so there is no CLI build from a fresh clone — but a Gradle
  9.6.1 distribution is cached, so
  `~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle :app:assembleDebug` works today.
- **No item is reachable by an accessibility service** — `launcherItemGestures` is raw `pointerInput`
  with no `semantics { onClick { … } }`.
- `feature:settings` and `feature:apps` declare junit but have **no tests**.

## Conventions summary

- Kotlin + Gradle Kotlin DSL (`.kts`) throughout.
- Follow the existing module boundaries and the build order in the rewrite plan.
- Match surrounding style; keep KDoc current when changing a type's purpose.
- **`delay` takes a `Duration`, not a bare `Long`** — `delay(FooDwellMs.milliseconds)`, never `delay(FooDwellMs)`
  (`kotlin.time.Duration.Companion.milliseconds`). The IDE flags the `Long` overload, and this codebase is full of
  dwell/timeout constants, so the unit belongs at the call site where a wrong one would otherwise be invisible. The
  constants themselves stay `Long` with an `Ms`/`_MS` name; only the call converts.
