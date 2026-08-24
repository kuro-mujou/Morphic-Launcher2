# Build status — what exists, what is next

*Split out of [CLAUDE.md](../CLAUDE.md) on 2026-08-20.*

Per-module state of the rewrite: what is built, what each piece does, why it is shaped the way it is,
and what is deliberately deferred. **Read this on demand when picking up a module** — it is a record,
not a rule.

It is currently written as an accumulated log rather than a status: entries describe *how* each piece
arrived as much as what it is. Trimming it to "built / next / deferred" is the obvious follow-up, and
was left undone in the split so nothing was lost by an editorial call.

Phase checklist and per-module build map: [REWRITE_PLAN.md](REWRITE_PLAN.md).

---


Foundations: **P0 done; P1 Core done** — `core:model` (B0), `core:common` (B1), `core:database` (B2). **B3
`core:icon` done** (parse → layer model → render/bake → `IconRenderManager` → `LauncherIcon`), and since the icon
studio it also holds the **live** render path (`IconLayerStack`) plus the shared derivations that keep the two
honest — the layer *model* moved out to `core:model.icon` on the way, and the **plate** is drawn a layer further out
still, by `AppIcon` in `core:designsystem`, since it samples the wallpaper by position and so cannot be baked.
**B9 `data:icons` done** for everything but icon packs: `IconOverrideRepository` over the collapsed `icon_override`
— an `IconAppearance` blob per detached app — and `CustomIconStore`. **B6 `data:apps`
partial** (LauncherApps wrapper, `AppRepository` + Room cache,
`RawIconSource`, and **categorization** — `AppCategorizer` folds a curated asset → platform
`ApplicationInfo.category` → keyword heuristics into a `CategoryGroup` id, ported from L1 but narrowed to *one app
in, one id out*: ordering and overrides are the category store's job, not the classifier's).

**The app cache is a mirror, and it keeps itself in step** — L1's `AppEvent` half, no longer deferred, and the fix
for an uninstalled app staying on every surface. Three parts:
- **`refresh` replaces rather than upserts.** An additive refresh cannot express "this app is gone", and *every*
  surface resolves its items through this cache — home's placements and list through `HomeState.appInfo`, the APPS
  order stores through the installed set their sync prunes against, the derived APPS layouts directly — so a row
  that never disappears is an icon that never disappears, everywhere at once. `AppInfoDao.replaceAll` is one
  `@Transaction` for `HomeListItemDao.replaceAll`'s exact reason: a separate `clear()` is observable, so every
  refresh would blank the home screen and the drawer for a frame.
- **`LauncherAppsWrapper.packageChanges()`** is a `callbackFlow` over `LauncherApps.Callback`, registered with an
  explicit **main-Looper `Handler`** — the argumentless `registerCallback(callback)` builds a `Handler()` for the
  *calling* thread, which throws when that thread has no Looper, and this flow is collected on `ApplicationScope`
  (`Dispatchers.Default`), which never has one. The throw dies in the scope's `CoroutineExceptionHandler`, so the
  symptom is not a crash but a listener that was never registered and an uninstall that updates nothing. It
  reports **which packages** moved and never what happened to them — "what is installed now?" has one answer and it
  is `queryActivities`, so adds and removes here would be a second source of truth about it, while *which* package
  moved is a question no re-read can answer (see the baked icons below). The
  repository collects it on `ApplicationScope` from its `init`, which is the exception the "coroutines run on
  `viewModelScope`" rule reserves: a cache that must mirror the device cannot stop mirroring it because the screen
  watching it went away. One collector rather than a flow handed to both ViewModels, so there is one answer to
  "when should the cache be re-read?".
- **An empty query is treated as a failed read**, not as an empty device: every device has at least this launcher
  installed, so nothing back means a profile mid-unlock or a binder hiccup, and replacing with it would empty every
  surface at once. That risk is the price of the mirror being authoritative, and it is the one thing an upsert
  could never do.

**And the baked icons go with it** — `BakedIconInvalidator`, the other half of making an update visible. The two are
separate because they go stale for different reasons: the *cache* goes stale when the set of installed apps changes
and a re-read fixes it, while a *baked icon* goes stale when an app replaces its own artwork, which changes nothing
the cache can see (same component, same label, same row). That is why `packageChanges` reports **which packages**
and never what happened to them: "what is installed now?" has one answer and it is `queryActivities`, but *which*
package moved is a question no re-read can answer. Two consequences worth knowing:
- **`IconId` cannot capture it, which is what `IconRenderManager.generation` is for.** That key was built so
  invalidation is automatic — any change *we* make produces a different key — and the app's own artwork is the one
  input it cannot see. `LauncherIcon` folds the generation into its bake keys, because evicting alone would not
  redraw anything: the three existing keys are unchanged by an update, so the stale bitmap would sit there until
  something else happened to recompose that cell. A bump that evicted nothing is skipped, so a first install does
  not recompose every icon on screen to discover there was nothing to do.
- **It lives in `data:apps`, not in `core:icon` and not in the Activity.** `core:icon` bakes icons and must not
  learn about package events; and `MainActivity`'s KDoc already names icon-cache invalidation inside `setContent`
  as one of the L1 mistakes it exists to avoid. It has no methods — constructing it starts it — so Koin builds it
  eagerly.

**What is deliberately *not* pruned is the layout.** A placement, a folder membership or a list ordinal for an app
that has gone stays in Room: it renders as nothing (every join is a `mapNotNull` through the cache), the planner
sees a free cell, and reinstalling puts the app back where it was. That is the same position `reconcileReportedOrder`
takes — an app the UI could not resolve must not be deleted on that evidence — and it is what makes an app briefly
unavailable (SD card, locked work profile) survive rather than lose its place.

**B4 `core:designsystem` — well along.** Theme + `Morphic*` components, plus the interaction primitives, all
validated in the `app/dev` harness while it existed: the drag toolkit (`DragCoordinator`, `FreeGridPlanner`,
MovingGap, `launcherItemGestures`, `DropFootprint`/`FloatingDragIcon`), `LauncherPager`, `SurfacePager`
(HOME↔side-surface pan; per-edge one/two-finger `OneFingerSwipe` policy — `ALWAYS`/`AT_EDGE`/`NEVER`, with
`AT_EDGE`'s nested-scroll hand-off wired — see the surface-swipe rules below), and the
grid (**grid plan G1–G5 + extras**, see [docs/GRID_LAYOUT_PLAN.md](GRID_LAYOUT_PLAN.md)): `LauncherGrid`
(FIXED_PAGER + SCROLL_GRID sizing), the `coordinateItems`/`flowItems` placement-strategy DSL, `animatePlacement`,
and the extracted `GridGeometry` seam. Only grid **G6** (full-harness regression gate) is unticked — treat as
done-on-device. **Built for the home since:** the shared **coordinate drag surfaces** — `CoordinateDragGrid`
(single zone) + `CoordinateDragPager` (paged, viewport zone + edge-flip) + the shared `LauncherDragCell`
(per-item drag wiring), all reused by the harness `GridSurface`; the `AppCell`/`FolderCell` launcher cells (via
`IconLabelCell` + `cellLabelHeight`, with `FolderCell`'s 2×2 tile extracted as `IconPreviewPlate` once the APPS
category card needed the same tile for its overflow cluster); and the full **app-collection subsystem**
(`core:designsystem/collection/`) — `AppCollectionOverlay`, `appCollectionInnerSize` (a `@Composable` facade over pure
sizing arithmetic), the `AppCollectionReorder` MovingGap, `AppCollectionDragDelegate`, and
**`AppCollectionHostState`/`AppCollectionPhase`** (the open/leave/enter lifecycle any collection-hosting surface
reuses — **generic in the collection id**, `Long` for a folder and `String` for an APPS category; 21 unit tests). Item
gestures are scoped to the icon+label group, not the cell — see the design-system rules above.

**B8 `data:layout` — first cut done.** Geometry engine (`FreeGridPlanner`/`GridReflow`/`GridOccupancy`/
`FreePush`) plus the command + persistence layer: `LayoutChange` (L1's 19 ops → 13), `LayoutRepository` (slim
~30 → 6: one `placements` flow keyed by `GridItem` with `HomeZone` in the value, four definition flows, one
`apply` sink), and a complete Room-backed `LayoutRepositoryImpl` (five placement tables + folder / icon-container
/ widget-container / widget definitions; `apply` exhaustive over all 13 ops; twelve DAOs bundled in `LayoutDaos`).
`CreateFolder`/`AddToFolder` also delete the folded apps' grid placements (an app lives in one place); folder
delete cascades its membership + placement rows. The APPS pager/category/list **order** stores get their *own*
repository (not built). Deferred: cross-orientation rotate-seeding (empty-folder auto-dissolve now done, in the
home layer).

**Home surface — two *pairings*, chosen in one place.** `HomeScreen` is a `when` over `HomeLayout` above shared
wiring (the ViewModel, the device report, the state), which is deliberately `AppsScreen`'s shape and the same
argument: both arrangements render the same apps with the same launch behavior, so "which pairing?" is answered
once, above everything both need. The arms are **`HomePagerSurface`** (`PAGER_WITH_DOCK`) and **`HomeListSurface`**
(`LIST_WITH_WIDGET_AREA`); the unbuilt-arm-behind-`else` trap is avoided by listing both, so a third value fails to
compile until it is drawn. L1 answered this with a `HomeSurfaceRegistry` of `HomeSurface` implementations, each
declaring a `HomeGestureSet` (`hasPager`/`hasDock`/`hasWidgetArea`/`dropPolicy`/`longPress`) its shell interpreted —
indirection that made the set of layouts *open* while every consumer still branched on which one it had, so
`isVerticalListHome` appears nine times in its home screen alone, each occurrence re-deciding which store to read.
- **`HomeZoneScaffold` is the shared half**, and the only one: a `Column` under a strip, a `Row` beside a rail, the
  side zone first or last as `SideZoneEdge.isLeading` says, the main area taking the remainder, `uiInsets` on the
  pair and each zone's own horizontal margin on *its own* modifier (which is what keeps drag geometry correct for
  free — both drag surfaces publish bounds from an `onGloballyPositioned` placed after the caller's modifier). L1's
  `Shell(dockLandscape, dockThickness, dockAtStart)` did the same job with two booleans.
- **The two surfaces share nothing else, and that is not an omission**: a coordinate surface asks "which cell, and
  who gets shoved aside?" where an ordered one asks "which index?", so there is no planner, no store and no gesture
  in common.
- **`HomeLayout.mainSlot`/`sideSlot`/`sideZone`** (`core:model/HomeLayoutGrids.kt`) are what make every *other*
  consumer layout-agnostic — the ViewModel, both settings sections and the icon-sizing block ask for "the main slot"
  rather than naming `HOME_MAIN`/`HOME_DOCK`. Three exhaustive one-liners, so a third pairing cannot be added
  without saying what it is made of.
- **`HomeState.main` is a sum type** (`HomeMainSizing.Pager` / `.List`), because the two pairings configure a
  *different quantity* rather than the same one differently: a pager divides the space it is given (counts), a list
  is one lane with nothing to divide (a row height). Neither could supply the other's value. `DockSizing` became
  `SideZoneSizing`, one type for both zones, since a dock and a widget area are the same *kind* of thing.

**`PAGER_WITH_DOCK` — real-sized, two zones (pager + dock), with a full folder subsystem.**
Its own module (`feature:home`, `inkspire.morphic.feature.home`); plain-MVVM `HomeViewModel` (screen-scoped
`ViewModel`, optimistic placement state, logic out of the UI) joins `LayoutRepository.placements` + `AppRepository`
apps + `LayoutRepository.folders`. The main area renders on the paged `CoordinateDragPager` at the **real blueprint
size** (`HomePagerGrid.toGridConfig(device)` — device-aware, `cellMultiplier = 2`, apps are 2×2 logical footprints
snapped to the visual lattice; device detected in the UI, fed to the VM for seeding). Portrait only. Hosted as
the launcher's home surface.
- **Launch** on tap → `AppLauncher` (`data:apps`, a one-method command separate from `AppRepository`'s reads;
  resolves the per-profile user from `ComponentKey.userSerial`, unlike L1's hardcoded personal user).
- **Drag-to-rearrange** persists through `LayoutRepository.apply` (optimistic → no flicker; `FreeGridPlanner`
  push with directional-push + merge-ring partition, dwelled preview, edge-flip pages, trailing empty page mid-drag).
  Seed leaves a free row so a full grid stays rearrangeable.
- **Folders.** Dropping an app on another (center merge ring) creates a folder; folders render as a `FolderCell`
  (2×2 icon preview). Tapping opens `AppCollectionOverlay` — two zones (a full-screen `SurfaceBackdropLayer` that **fades in**,
  plus a transparent bounded card sized live by `appCollectionInnerSize` per device/orientation, inset to
  `systemBars ∪ displayCutout`), a **dense-flow pager** of the folder's ordered apps, launch on tap, in-folder
  **MovingGap reorder** (persist `ReorderFolder`), and a border outlining the inner zone while dragging. The frost is
  its own node *under* the card rather than the card's parent, so the two can be given different entrances later; the
  card's alpha and the frost's share one driver today. Going through the shared layer is also what fixed a real fault —
  calling `wallpaperBackdrop` directly meant liquid glass tried to draw its refraction rim across the whole screen.
- **A folder is a place one drag passes *through*, not a destination it commits to.** This is the whole model, and the
  rest of the folder rules fall out of it. **Enter**: hold a dragged app on any folder's merge ring (~1s) and it opens
  mid-drag with the drag carrying on inside, so the app lands at a *chosen* slot. **Leave**: hold outside the card
  (~1s) and the folder **closes**, with the same drag carrying on over the grids beneath. Both directions are
  **repeatable, in any order, over any number of folders — including re-entering one already visited**, because
  *neither half writes anything*: membership is decided **only at the drop**. It is one continuous gesture on a
  **single shared `DragCoordinator`** (home + dock + folder zones on it, planner/drop dispatch by zone, the folder
  publishes an `AppCollectionDragDelegate`). The dwells are **equal by design** (`LeaveDwellMs` == `OPEN_COLLECTION_DWELL_MS`):
  opposite halves of one gesture, so a user who learned one hold has learned both.
  - **Leaving must genuinely close the folder, not hide it.** An earlier cut kept an `AppCollectionPhase.Extracting` whose
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
    release out there reads as "never mind" — and it *cannot* be honored anyway: an app being carried inside a folder
    has no grid placement, so "placing" it would leave it in the folder **and** on the grid.
- **What the drag owes is fixed at lift: `AppCollectionHostState.dragSourceCollectionId`.** The folder a drag *started in* (null
  if it started on a grid), captured at `onDragStart` and held until release, whatever it visits in between. It answers
  two questions at once, and they are the same folder for the same reason — the gesture began on one of its cells:
  that overlay must **stay composed** (an in-flight pointer stream **cannot** be handed to another node — see the rule
  in `launcherItemGestures`; a root-level pointer overlay was tried and rejected because it swallows item events), and
  it is the one **owed a removal** wherever the app lands. It is deliberately *not* an `AppCollectionPhase` field: the phase
  names whichever folder is *on screen*, which after one hand-off is no longer the one owed anything.
  **Capturing it at lift rather than at the first hand-off is what makes re-entry work** — an app carried *in* from a
  grid and back out owes that folder nothing, so nothing pins it and nothing bars re-opening it. `AppCollectionOverlay`'s
  `presenting = false` is the pointer-holder role: invisible, no back handler, no delegate, no drop zone, no proxy —
  and reversible, since a drag can come back.
  **Three traps if you touch this:** both overlays must be emitted from **one keyed call site** (a second call site is
  a different composition position, so Compose disposes the folder and kills the drag it was preserving); an app with
  no placement must be resolved through `HomeState.appInfo` (**placed apps *and* folder contents**) or it is
  unrenderable — both as a folder's `incoming` and as home's floating **proxy**, which home takes back the moment a
  folder closes; and the folder drop zone is **one shared `ZoneId`**, so only the *presenting* overlay may register or
  unregister it (an unguarded `onDispose` on the holder tears the zone out from under the folder on screen).
  The whole open/leave/enter lifecycle lives in `AppCollectionHostState` (`core:designsystem`), not the screen; home
  supplies only the surface-specific *"which folder does this merge plan target?"* lambda (a **zone + placement**
  match — placement alone matches the other zone's folder at the same cell) and the commit calls. Two guards worth
  knowing: `reconcileReportedOrder` (`ReportedOrder.kt`) folds a UI-reported order
  back onto real membership, because `ReorderFolder` replaces membership wholesale and the UI can only report
  members it could render — writing its list verbatim **deleted** anything unresolvable (an uninstalled app,
  B6 pruning still deferred); and `AppCollectionOverlay` is wrapped in `key(folderId)` so switching folders doesn't
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
- **Where a side zone sits is `SideZoneEdge`, and it is a rule rather than a setting.** Four values, because HOME's
  two pairings put their zone on opposite ends: a **dock** is a bottom strip or a trailing rail, a **widget area** is
  a top strip or a leading rail — one is the thing you reach for, the other the thing you look at.
  `DeviceConfiguration.sideZoneEdge(layout)` is the whole rule, and `isStrip`/`isLeading`/`opposite` are what every
  consumer reads instead of re-deriving it. This was `DockEdge` with two values while the dock was the only side zone;
  L1 could not express it at all and passed a `landscape` boolean *and* a `dockAtStart` boolean side by side. The dock
  half of the rule is unchanged: a **bottom strip** on phone portrait,
  tablet portrait and tablet landscape; a **rail on the trailing edge** on phone landscape — the one posture that is
  short and wide, where a bottom dock would take a third of the height for one row of icons. `END` rather than `RIGHT`
  because a `Row` places it last, which is the right side in LTR and the left in RTL. It keys on the whole
  `DeviceConfiguration` and not on `isLandscape`: a tablet in landscape has the height to spare.
  One fact with four consequences, which is why it is a type rather than a `landscape` boolean threaded separately
  through each (L1 passed one to `homeGridArea`, `HomeGridEditor`, `DockGridEditor` and its extent slider, and each
  re-derived what it meant): home stacks its zones in a `Column` or a `Row`, the dock's extent is a **height** or a
  **width**, the extent bounds the **rows** or the **columns**, and the grid editor draws its companion zone below or
  beside. The blueprint's phone-landscape default is the transpose for the same reason — `1 × 4` where the others are
  `4 × 1`. `GridArea.splitForDock` is the one expression that divides the window, returning **both** halves together
  (like `DerivedCell`) because three callers depend on them agreeing and cannot check each other: the surface draws
  them, and both settings sections bound their grids against them.
  - **Rotation must not re-fit what it is not drawing.** Placements are keyed by `Orientation` and home reads
    `PORTRAIT` only (home orientation is unbuilt), so `fitMainTo`/`fitDockTo` are gated on
    `HomeViewModel.drawsStoredPlacements`. Without it, rotating a phone would settle a portrait arrangement against a
    landscape grid — and against the **rail**, which is the transpose, so nearly every dock item would be evicted to
    home and would not come back. A grid drawn out of bounds for as long as a rotation lasts is cosmetic and reverses
    itself; the write does not. The guard becomes vacuous the day placements are stored per posture.
- **`cellMultiplier` is a *placement* subdivision, and the snap has to honor it or it buys nothing.** HOME's three
  free-placement grids (pager, dock, widget area) declare `cellMultiplier = 2`: a 4×5 grid of visible cells really is
  8×10 logical ones, and an app is a 2×2 logical footprint. The user is never shown that — they see 4×5 cells with
  one icon each — and what the subdivision buys is that an icon can come to rest **straddling** two visible cells,
  because its top-left may be any *logical* cell. The offsets between the cells are reachable, which is the only
  reason to subdivide.
  `planCoordinateDrop` passed `step = cellMultiplier` to `GridGeometry.snapTopLeftCell`, rounding the top-left back
  onto the visual lattice — so a grid declared at 2 behaved in every observable way like one declared at 1, and the
  subdivision cost twice the occupancy bookkeeping for nothing. The `step` parameter is now **gone rather than
  defaulted**: its only ever use was that mistake, and a parameter is an invitation to repeat it. L1 resolves the
  hovered cell at logical granularity and centers the footprint on it, which is what this now does.
  - **The lattice is shown while dragging** (`gridSnapMarkers`, `core:designsystem/grid`), which is the half that
    makes the freedom legible — L1's `GridLinesCanvas`, ported: a concave-diamond marker at every **visual** cell
    corner, fading in around the dragged footprint's *edges* and out again as it leaves. Visual corners rather than
    every logical intersection deliberately: they are the grid the user thinks in and the reference a half-cell
    offset is read *against*, where marking every logical cell would double the dots and put half of them in the
    middle of a cell. A `drawBehind` taking lambdas, so a drag re-runs the draw phase and nothing else.
- **Layout: the dock has an extent of its own, the pager takes the rest, and there is no padding anywhere.** The dock's
  extent is a **setting** (`SurfaceMetrics.extentDp[HOME_DOCK]`, defaulting to `DockGrid.extentDp`) *and* its rows and
  columns are stored counts — the extent does not replace a count, it **bounds** the one it divides, since a cell is
  `extent ÷ count`. It is an *extent* and not a height because `SideZoneEdge` decides which dimension it names; one
  value per device serves both, since a user configuring a phone in landscape is configuring the rail.
  `CellFit.fitGridConfig` resolves a stored size against what an area can actually hold, from the two
  inputs only the surface knows (the measured width, and the type scale behind a label row) — and it needs **no branch**
  for the rail, because the extent is simply in the area's width there and each axis is fitted to the dimension it is
  given. Deriving the counts from
  the extent was specified and abandoned on both axes: rows read as an editor missing half its buttons, and at the
  default icon guardrail a derived phone dock is eleven columns wide against the four it has today. **The count on the
  axis the extent does *not* divide is clamped on read and never written back** (shrink the icons again and it
  returns); **the one it does divide *is* reduced in storage**, at the moment an extent commit invalidates it — the
  asymmetry is that the extent was a deliberate change,
  where an icon-size change is not about the dock at all. L1 wrote every clamp back from a `LaunchedEffect` *inside
  its dock settings screen*, which destroyed the preference and only ran while that screen was open. Home carries
  a **horizontal margin per zone** (S4g, defaulting to 0 — so an unconfigured launcher still runs edge to edge). The applied
  inset is `uiInsets` (`systemBars ∪ displayCutout`), one value feeding both the padding and the width
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
- **The dock's extent is subtracted from home, so growing it can invalidate home's counts too** — the same invalidation
  one grid over, on whichever axis the subtraction happened (a bottom strip takes home's height, a rail takes its
  width), and it is answered in the two halves this codebase already splits such things into. The **surface**
  fits the pager to what is left (`CellFit.fitGridConfig` over `splitForSideZone`'s main half, the same expression the
  Home settings section computes its bounds from) and re-homes the displaced items to a further page
  (`HomeViewModel.fitMainTo`, the pager's `fitDockTo`); the **count itself is written down** only by the dock
  section's extent commit, which is the deliberate change that caused it. That is the dock's own asymmetry applied
  outward: a count invalidated by a committed extent is written, one invalidated by an icon-size change is clamped on
  read and returns. L1 did the clamp from the home *surface* (`LaunchedEffect` on its measured pager bounds — its
  comment names "the dock is turned back on") and wrote it on **every** cause, so an icon tweak permanently destroyed
  a row count that had nothing to do with it. L1 also measured home's area two ways (`pagerBoundsInWindow` on the
  surface, `homeGridArea` in settings) which could disagree; L2 has one expression. Note it takes a *large* dock or
  *large* icons to bite at all: home's smallest usable cell is ≈52dp at the default guardrails, so even a 320dp dock
  leaves room for more rows than the five it stores. **Neither zone settles until the store has answered for it** —
  a blueprint fallback is a smaller grid than one the user has grown, and settling against it would make a transient
  first frame a permanent write.

**`LIST_WITH_WIDGET_AREA` — a widget area at the leading edge, a hand-ordered list of apps filling the rest.**
`HomeListSurface`, the second arm of `HomeScreen`'s `when`, and structurally the dock pairing's mirror: the same
`HomeZoneScaffold`, the same "extent bounds the count it divides" rule, the same settings sections. What differs is
what each zone *holds*, and every difference below follows from that rather than being a choice.
- **The widget area is the dock in every way but its contents**, which is one field: `WidgetAreaGrid.icon` is
  **null**, because a widget is not an icon in a cell. That null is load-bearing three times over — `CellFit` fits it
  by `WidgetMinCell` (48dp square, L1's `MIN_WIDGET_DP`) rather than by inverted icon guardrails, the ViewModel skips
  it when resolving icon sizing (`SettingsRepository.iconSizing` rightly throws for it), and its settings section
  draws no icon group at all. That last one is exactly the shape L1's `DockSettingsDetail` took in its `minimalist`
  branch, returning early before `IconLayoutControls`. Defaults are L1's `WidgetAreaSettings`: 280dp, 4×3 portrait,
  3×4 landscape — far thicker than a dock's 96dp, which is the difference between the two zones stated as a number.
- **It renders empty today, and that is a missing feature rather than a missing surface.** Widgets are unbuilt
  (`GridItem.Widget` has no cell), so nothing can be placed in it — but the zone is real: measured, fitted, and
  registered as a `CoordinateDragGrid` drop target that **accepts widgets only**. That is L1's `areaReject` rule
  expressed as `DropZone.accepts` instead of as a check at drop time, so an app carried over it falls through to the
  list beneath rather than being rejected on release.
- **Two things the widget area is still owed, both gated on widgets existing.** A shrink does not re-home what no
  longer fits (`settleDock` evicts to a *coordinate* main area, and this one sits beside a list, which has nowhere to
  put a widget) — deliberately not L1's answer, which deleted them. And nothing seeds it.
- **The list is ordered, and dragging it reorders it — nothing else.** No merge ring (a list of apps holds no folders,
  as L1's does not), no page to carry an item onto, no coordinate to write: a drop is an index, committed through
  `HomeViewModel.reorderList`. The preview is **MovingGap**, the same model the APPS pager and every folder use;
  `OrderedFlow` gained `cellFractionY` for it, because a list flows *down*, so "insert before or after?" is the top
  half of a row rather than the left half of a cell.
- **A row is a `LauncherDragCell`, and taking the shared cell rather than its parts is what made the drag feel
  right.** The first cut hand-rolled three of its four jobs — `launcherItemGestures`, the `alpha = 0` on the lifted
  row, the drop wiring — and silently dropped the fourth, `animatePlacement`. A `Column` places children in order, so
  reordering them *moves* each row: with the modifier the rows glide as the gap migrates, and without it they jumped
  between positions. The lifted row must also **drop** the modifier (which the shared cell does), or it flies back
  from its old slot on release instead of landing where it was put.
- **Three things about the commit, each of which read as "the drop failed" when it was wrong.**
  - **The gap is cleared when the drag ends, never by the code that reads it** — a `LaunchedEffect(isDragging)`, the
    APPS pager's shape. An earlier cut reset it at the top of `handleDrop` and then built the committed order from it
    two lines later, so every drop wrote `movingGapDisplayOrder(order, app, 0)` and sent the app to the top.
  - **The list leads its store**, exactly as `placements` does one field over, and here it is not optional: the
    MovingGap preview lives on the *drag*, so it is gone the instant the finger lifts. Rendering the stored order
    across the write meant the dropped row visibly returned to where it started and jumped onward a frame or two
    later. `HomeViewModel.listOrder` + `listWritesInFlight` closes that window; the store's echo afterwards is also
    the correction, since `setOrder` reconciles what the UI could render against real membership.
  - **`HomeListItemDao.replaceAll` is one `@Transaction` for a reason that is invisible until it is removed.** As a
    `clear()` then an `upsert()` the clear is *observable*: the DAO's flow re-runs on that invalidation and emits an
    **empty** list, so the surface blanks mid-reorder. It is the one transaction in `data:layout`, and it is not the
    general fix its siblings still need — those should take the database and use `withTransaction`, together.
- **The drag proxy keeps the list's left edge and follows the finger in y only.** Every other surface centers its
  proxy on the finger because a proxy is one cell there — roughly square, smaller than the finger's travel. A row is
  the full width of the list, so centering it swings the whole row sideways with the thumb.
- **A `Column` in a `verticalScroll`, deliberately not a `LazyColumn`** — the opposite call from `AppsVerticalList`,
  for two reasons that are properties of this list rather than preferences. A lazy list disposes rows that scroll
  away, and the lifted row **owns the pointer stream driving the drag**, so auto-scrolling far enough would kill the
  gesture (this is what the APPS pager needs `keepAllPagesPlaced` for). And a home list is the handful of apps the
  user chose where the drawer is every app installed, so composing it whole costs nothing. It also makes the drag
  geometry the documented one: the viewport's `onGloballyPositioned` sits **outside** the scroller and
  `viewportTop - scrollState.value` is the content origin, republished every frame — plus the re-send after
  auto-scroll, in one `SideEffect`, since the coordinator only re-plans when the *finger* moves.
  The cost of the scroller is that **lifting a row needs a still finger**: movement during the long-press scrolls
  instead, since the scroll gesture is the parent's. That is ordinary for a reorderable list — L1 sidestepped it with
  its library's drag *handle* — and it is the thing to change if the lift ever feels finicky, rather than a symptom
  of anything above.
- **Not built**: the "Add apps" row (a picker), which is L1 behavior. Without one its contents are whatever the seed
  put there — and it is the reason the list's own menu verb is *Remove* rather than a pair: an app can be taken off
  the list but there is still no way to put one back. That menu is the shared item menu with one contribution, and
  **the contribution is neither `RemoveFromGrid` nor a reorder**: this list is an order store of its own, so removing
  is a *membership* write (`HomeListRepository.remove`). Writing the order without the app looks equivalent and is
  not — `setOrder` reconciles a reported order against real membership, so the app is treated as one the surface
  could not render and is appended straight back at the end. That guard is right, which is exactly why removal needs
  an op that says what it means rather than one that hopes to be inferred.

**APPS surface — one module for every layout; the vertical list is the first.** `feature:apps`
(`inkspire.morphic.feature.apps`) is the whole surface: L1's `feature:appdrawer` + `feature:applibrary` were
deleted, not ported, because they rendered the same apps with the same launch behavior and differed only in
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
    (which fills the row — a list's label sits *beside* the icon, so there is no label row to leave height for).
  - **`AppsVerticalGrid`** — a `LazyVerticalGrid` of `AppCell`; **columns from the `AppsScrollGrid` blueprint**
    (`colsFor(device)`, since a `SCROLL_GRID` blueprint has no rows and so can't go through `toGridConfig`), cell
    height a flat placeholder. It is **not** `LauncherGrid`'s SCROLL_GRID mode:
    that composes every child at once, which is right for the bounded per-category page it was built for and wrong
    for hundreds of icon-baking cells. This is the grid plan's *right tool per surface* rule, and it costs nothing
    because a derived layout is never dragged **within** itself — so it needs no shared lattice and no published
    `GridGeometry` (a drag *out* is `EjectToHome`, which reads the finger).
- Cells go through the shared `launcherItemGestures` contract rather than a `clickable`, so APPS cannot drift from
  the rest of the launcher on long-press timing or slop — exactly what L1 did, hand-rolling a recognizer (plus a
  click-suppression flag) inside its list composable. The tap-only wiring lives in one `appsItemGestures` shared by
  both layouts, so a layout can't half-wire it and there was one file to change when the menu and `EjectToHome`
  landed — both now have. **A row's touch target is its icon and its label, not the strip they sit in** — the same "visible extent"
  rule as a grid cell, and `AppRowCell` applies it the same way, by hanging the gestures on a wrap-content group.
  This reverses an earlier reading ("a row's visible extent *is* the full-width strip, so a list leaves no slack"),
  which conflated the row's **footprint** with what is drawn in it: a row paints no background, so the width past
  the end of a short label is exactly the slack a grid cell has around its icon, lying on the other axis. So a list
  *does* leave room for a surface long-press. The bill is that a tap out there launches nothing either — one
  contract covers both — which is already true of the slack around a grid icon. **Both lists keep the slack free**,
  including this one — and the surface press is now what it is free *for*, with the alphabet strip and search still
  below: a
  target narrowed once is worth more than one narrowed again later, when users have learned the wider one.
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
- **Folders on the pager work exactly as they do on home**, because the lifecycle is the same `AppCollectionHostState`:
  tap to open, dwell on a merge ring to enter mid-drag, dwell outside the card to leave, drag an app back out onto
  a page or straight into another folder, auto-dissolve at the second-last app. The surface supplies only the one
  answer the host can't know — *"which folder does this merge plan target?"* — as a **slot** match, which is the
  ordered half of the split that lambda was built for. A merge plan's footprint is meaningful (it names the hovered
  cell) where a reorder plan's is not, which is what makes the slot resolvable. Cells are **three zones** here
  (outer thirds insert, center merges), unlike the folder's and the category pager's halves.
- The pager's op set composes rather than special-cases: `RemoveFromFolder` takes an app out of membership and
  *nothing else*, so a landing pairs it with a `Move` (onto a page) or an `AddToFolder` (into another folder), and
  one batch commits both. `CreateFolder`/`DissolveFolder` name a **neighbor** instead of a slot — "this takes that
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
  unfiles anything under an unrecognized id and deletes its definition row, so the classifier can place those apps
  again. Without it a rebalance strands them: the read is driven by the definitions table, so they would render
  nowhere while still occupying a row that `syncCategoryItems` counts as filed. It is the same path a user-deleted
  category will take once `known` grows to include user-created ids (L1's `u1`/`u2` prefix is the pointer).
- **The category card** (`CATEGORY_CARD`, `AppsCategoryCard`) — **the fifth and last APPS layout**: a lazy 2-column
  grid of square cards, one per category, each a header plus a 2×2 preview, tapped open to reach the rest. It shares
  the pager's store, so there is nothing to seed or classify here and switching between the two layouts shows the
  same categories with the same apps. Lazy (unlike the category *pager*'s `LauncherGrid` pages) because a card
  composes up to seven baked icons, so the card *count* is small but the icon count is not — the vertical grid's
  argument, not the pager page's.
  - **The expansion is a real `AppCollectionOverlay`, not a lookalike.** That type's parameters were already a label and
    a list of apps with no folder id in them, because what it renders is *an ordered collection of apps opened over a
    surface* — and the grid it sizes itself from is `FolderGrid`, whose KDoc has always called itself the "folder /
    category-card grid". Reuse brings the paging, dots, MovingGap reorder and scrim for free. This is what **named**
    the type: it was `FolderOverlay` in a `folder/` package, and the second case is what proved the name described a
    *case* rather than the thing. No `AppCollectionHostState` here: that machine exists for the phases an app passes
    through while its *membership* changes mid-drag, and a tap-opened expansion changes none.
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
  - **Dragging between categories is the folder↔home gesture, on cards** — the same `AppCollectionHostState`, so the rules
    are that machine's and not this layout's: dwell on a card (~1s) to expand it mid-drag and place the app at a
    *chosen* slot, dwell outside an expansion to close it with the drag carrying on over the cards, drop straight on a
    card to append with no dwell, repeatable in any order including re-entry, membership decided only at the drop.
    Reaching a card past the fold is `DragAutoScrollEffect` (widened to `ScrollableState` so a lazy grid can use it);
    manual scroll is gated off mid-drag as on every other dragging surface.
    Three things differ from home, all of them properties of the surface rather than choices:
    - **A drag starts inside an expansion, never on the card grid.** Home has loose apps to pick up; here every app is
      filed in exactly one category, so nothing sits *on* the surface — expansion→card *is* the whole gesture, the
      analog of home's folder→folder. Preview icons stay tap-only (a folder's preview tile isn't draggable per-icon
      on home either), which is also what lets the grid stay **lazy**: nothing on it owns a live pointer stream, so a
      card disposed by auto-scrolling can't kill the drag. Making previews draggable would pin the source card in
      composition, which a lazy grid cannot honor.
    - **There is no "empty cell" landing.** Off a card there is nowhere for an app to be, so the planner reports *no
      plan* and a release there is a cancel — which is why `MERGE` is the only intent this surface ever reports.
    - **Landing owes the source category nothing.** `Move` unfiles the app from every other category as part of filing
      it, so one op is the whole re-file, where the pager pairs `RemoveFromFolder` with a `Move`. Dropping back on the
      category the app came from is a no-op, as on home.
    Hit-testing is a **per-card bounds map**, not a `GridGeometry`: cards are lazy, square and separated by spacing
    that belongs to no cell, so there is no lattice to compute from. Entries are added as cards lay out and removed as
    they scroll away.
  - **`AppCollectionHostState`/`AppCollectionPhase` became generic in the collection id** (`Long` for a folder,
    `String` for a category) so this surface could *use* the open/leave/enter machine instead of growing a near-copy of
    it — the L1 `resolveDockDrop` mistake this codebase keeps un-making. Nothing in the lifecycle reads an id beyond
    comparing it, which is what made the parameter free; a unit test pins that with `String` ids. **The naming caught
    up in its own mechanical commit**: `core:designsystem/folder/` is `collection/`, and `Folder{Overlay, HostState,
    Phase, DragDelegate, ReorderPlan}` / `folderInnerSize` are `AppCollection*` / `appCollectionInnerSize` — the
    vocabulary the KDoc had already settled on. Deliberately **not** `IconCollection*`, which would read as a sibling
    of `IconContainer` (a grid item holding icons) when the two are unrelated. What stayed `Folder` is everything that
    really is one: the Room tables and ops, `GridItem.Folder`, `FolderCell`, the Folders settings section, and
    `GridSlot.FOLDER`/`FolderGrid` — that last one because the slot name is a **stored settings key**, so renaming it
    would reset every per-folder icon and grid override.
  - Still to come: an optimistic layer (a drop waits for the write, so a re-file lands a frame or two late — the
    `Injected` phase is what stops the app blinking out meanwhile).
- Not built: the alphabet filter strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing
  helpers into the list file — three concerns in one composable), search, drag-out-to-home (`EjectToHome`), and
  category **management** (create/rename/delete/reorder — a `feature:settings` concern, which is also why a card
  carries no menu and cannot be dragged).

**The icon effects expansion is complete — every slice of
[docs/ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md), all thirteen effects — and phase 2 has started.** That
second list is **§8** of the same plan: six more effects and one architectural item, assessed against the built code
rather than against captures. **Four of the six are re-pointing what already exists** — `ColorMatrices.duotone` *is* a
gradient map, `LayerGradient.radial` places a vignette, `IconRenderer.haloed` is an inner shadow inverted, and
`dilated` is an outline's outward half — so the only genuinely new kernel is **bevel & emboss** (a Sobel over a
blurred alpha, which does not fit `resample` because that helper samples one point where a Sobel reads a
neighbourhood). The one primitive missing is an **alpha-inverting matrix**, which three of the six want. The
architectural item was proposed as "extract the falloff onto the base effect"; it is **neither an extraction nor a
falloff** — Bloom's falloff is the light's own geometry, so nothing moves, and what is being asked for is a per-effect
**mask**, which is ~20 lines in `applyEffects` (run the effect into a buffer, composite it back through a ramp's
alpha) plus a restructure of each live-drawable effect. It goes **last**, because its cost multiplies by the number of
effects. **Built so far: `LayerEffect.Duotone`, `LayerEffect.Vignette`, `LayerEffect.InnerShadow` and
`LayerEffect.InnerGlow`, `LayerEffect.Outline` and `LayerEffect.Bevel`** — **all six are built** (see their notes
above). What is left of §8 is the **effect mask**, which was always last because its cost multiplies by the number
of effects. **One plan claim is already overturned** — the
"missing alpha-inverting matrix" was never needed: destination-out over a filled buffer *is* the inversion, and
outline's erosion is that same op twice. One number worth watching: four of the six do not draw live, taking the total to ten
of nineteen, and `drawsLive` is all-or-nothing per icon — so most recipes worth making will preview from the bake, and
the live path narrows to the plain ones.

Thirteen effects were drawn from captures of another icon studio, and the plan's whole finding is that
**only the *live* path has API restrictions**: the bake owns a software bitmap, so a blur is a `BlurMaskFilter` and
a displacement is arithmetic over an `IntArray` at every API level. Gating six effects to API 31/33 was considered
and rejected — it would deny glow and drop shadow to every device below Android 12 to solve a problem only the
*editor* has. Instead the studio will preview **from the bake**, downscaled and throttled during a gesture, which is
why `LayerEffect.drawsLive` exists now with nothing yet answering it `false`.

**The bake-backed preview is built (slice 8), so the remaining six are unblocked.** `IconPreview` is the studio's one
entry point: it draws through `IconLayerStack` where the live path can manage it and from `IconRenderer` where it
cannot. Every studio preview goes through it — canvas, layer tiles, composite tile — because a call site that could
forget to ask is one that silently shows a lie. Five things worth knowing:
- **The whole icon falls back, never one layer**, which reverses what the plan's §2 assumed. A per-layer fallback is a
  *hybrid stack* — one layer from a bitmap, the rest live around it — so the two halves must agree about geometry at a
  seam **inside one icon**, which is the two-renderer hazard in its worst form: no longer two whole pictures that can
  be held against each other. It also costs nothing, the bake rendering a whole set either way and its expense being
  the effect rather than the layer count. `IconLayerSet.drawsLive` is the one question asked.
- **`IconLayerSpec.drawsLive` keeps a real job** and is not made vestigial by that: a *layer tile* in the rail solos
  one layer, so it falls back on that layer's own effects. One property, two scopes, each asking about what it draws.
- **Draft first, then full — the throttle *and* the resolution split in one mechanism — and the draft is never
  abandoned.** Every recipe is baked downscaled immediately and then, once nothing newer has arrived for `SettleMs`,
  at full size. **Only the full-size pass is cancelled by a newer recipe**, and that asymmetry is a correction: the
  bake used to live in a `LaunchedEffect` keyed on the recipe, so anything newer killed whatever was running.
  Conflating by cancellation works only while the work is shorter than the gap between two emissions, and nothing
  checked that — a slider thumb emits per pointer event, **about 7ms apart on a 144Hz phone against a measured 15ms
  draft**, so for any effect heavy enough to reach this path *no draft ever finished and the preview did not move at
  all until the finger lifted*. It was reported as the preview freezing on a drag while the +/- buttons worked, which
  sounds like two bugs and is one: a discrete step leaves a gap long enough for a draft to land. Letting the draft
  finish and then taking the newest recipe gives the property actually wanted — as fast as the machine can draft,
  never slower and never not at all — and still coalesces, since everything emitted mid-draft collapses into one
  value. **The plan's gesture-in-flight signal turned out to be unnecessary** — "nothing newer has arrived" *is* what
  settled means, where `onCommit` is a proxy any non-slider edit would answer differently.
- **Deliberately not `IconRenderManager`.** Its cache is keyed on the resolved layer set, which is exactly what changes
  every frame of a drag — a preview going through it would evict every real icon on the device within seconds. The
  editor wants one slot and has one. Its coalescing and concurrency cap are moot here too: there is one bake in flight
  by construction.
- **`IconRenderer.render` gained a `customImage` lambda**, defaulting to the disk read it always did. That default is
  right for every surface and **wrong for the studio**, whose whole point is that a freshly picked image previews
  before anything is written (see `CustomIconStore`) — so the editor passes the same lambda it gives the live path and
  the two draw the same picture. `IconRenderManager`'s call had to name `packImage` as a result: a trailing lambda now
  binds to the new parameter, silently and only for pack layers.
- **A layer tile drops the whole-icon effects**, which it did not before the composite had any. A tile answers *which
  layer is this?*, and a grain belonging to the icon obscures exactly that at 48dp — it would also drag every tile onto
  the baked path for something none of them show. The composite tile is where those are seen.

What is **not** built is the *"working"* hint for a bake that runs genuinely long: it wants measuring against the
heaviest effect on the slowest device to hand, and the draft is what makes its absence survivable since something
always lands quickly. **The draft is one number, `DraftPx`, and it was three that contradicted each other** — a
fraction of the settled bake, a floor and a cap, where the floor asked for 144 and the cap pulled it straight back to
128, so the floor was dead code and the paragraph explaining it was false. It is 144 rather than as small as possible
because a draft can be too small to be *true*: roughly the smallest bitmap a surface bakes, so the draft can represent
anything a surface can. Measured on a Snapdragon-class phone, one grain bake: **~15ms at 128px, ~19ms at 144, 332ms at
768**.

Built so far: the ordered effect **pipeline** (slice 0), the paged effect **panel** with per-effect switches and
`SliderControl` (slice 1), the **filter** library, now 46 looks in seven categories (slice 2), the **layer rail** that replaced the
`LAYERS` tool entry (slice 3), slice 4 — **`LayerEffect.Bloom`** and **`LayerEffect.Gloss`**, plus **whole-icon
effects**, which that plan had not noticed it needed (six of the thirteen are only correct over the composite) — and
slice 5, **perspective**, which turned out to need the live path to give up its `graphicsLayer`, slice 6, **Pattern**,
with an asset library of its own, and slice 7, **Extrude** + **Chromatic split**. That is all of tier 1; everything
remaining — Glow, Drop shadow, Pixelate, Ripple, Grain, Progressive blur — waits on the **bake-backed preview**, which
Extrude has already given a second reason to build.

**Also still open: icon packs (S8)** — with the plate and the finalize step built, the last piece of the icon
studio proper. A pack is one more `LayerSource`
variant rather than a mode, so "apply a pack to everything" is setting the global default's fg/bg source and goes
through the same commit, cache key and invalidation as any other edit. What has to be built with it is pack
*detection* (theme-intent actions), `appfilter.xml` parsing, and — for browse and search — a drawable lister, which
L1 never finished either.

**The effects sequence below is done and is kept as the record of how S5f was split.** S5f split
into three when it was costed, because as one slice it was `BackdropEffect` + the params slice + `Blur.kt` + a 350-line
`Modifier.Node` + an AGSL shader + a settings section + three consumers:
- **S5f-1 — the brightness signal. Done.** The shell's hardcoded `darkTheme` is gone; see the design-system notes. It
  turned out to need *neither* `Blur.kt` half, which is why it was worth taking first — it is the one reading of the
  wallpaper that is a system call rather than image processing.
- **S5f-2 — the frosted backdrop. Done.** `BackdropEffect` (which B0 had already built, unconsumed) became a
  `data:settings` slice, `Blur.kt`'s box blur landed in `data:wallpaper` beside the cropping and scaling it belongs
  with, `wallpaperBackdrop` + `BackdropState` + `LocalBackdrop` landed in `core:designsystem/backdrop`, and the folder
  overlay's opaque black sheet became the first frosted surface. See the design-system notes above for the four
  departures from L1's version, and the wallpaper notes for how the sampled image is chosen.
- **S5f-3 — liquid glass and the effects section. Done.** The AGSL shader (see the design-system notes) and the
  seventh settings section, which is also the slice's first writer. **S5 is now complete.**
- **S5f-4 — the full-screen frost. Done, and it was not in the plan.** APPS and the folder went transparent behind one
  shared `SurfaceBackdropLayer`, which is what the "APPS stays opaque" note had been promising since the window learned
  to show the wallpaper. It reshaped the model on the way through (`None` → `Plain`; every effect blurs), fixed a
  full-screen refraction rim in the folder, and left the effect *sliders* dormant — see the design-system and effects
  notes for all four. They have consumers now, and not the ones expected: the context menu and the bottom sheets both
  turned out to belong to the **film** (`Modifier.filmBackdrop`), since each borrows the window's edges, so what reads
  the sliders and draws the rim is the **container tile** and the **icon plate**.

**Widgets are built: the picker, the host, the cell.** `data:widgets` holds two types with deliberately different
jobs — `WidgetCatalog` answers "what *could* be added?" from `AppWidgetManager` with no host at all (a live read,
not a cache, for `AppShortcuts`' reason), and `AppWidgetHostController` owns the one `AppWidgetHost` per process.
The distinction runs all the way through: `WidgetProvider` has no id and `BoundWidget` does.
- **An allocated `appWidgetId` is a resource, not a value.** It outlives the process, so a widget whose id is
  allocated but never placed is a leak the user can neither see nor clear. Every path that abandons the add gives
  it back, which is why `WidgetAddFlow`'s callback returns a **Boolean** — the caller says whether it *kept* the
  widget. Removing one is likewise two halves that must both happen (`HomeViewModel.removeWidget`): the layout
  rows, and the id.
- **`LayoutChange.PlaceWidget` writes the definition *and* the placement.** A widget cannot be `Move`d onto the
  grid the way an app can: an app is a component the cache already knows, while a widget's provider and label live
  in a row only this op writes, so a bare `Move` would leave a placement resolving to nothing.
- **The add flow decides nothing about placement.** L1's controller held the home state, both grid configs, four
  cell sizes and the surface kind so it could place the widget itself — fourteen mutable fields reassigned every
  composition. Here it owns only the activity-result choreography (silent bind → system dialog if refused → the
  provider's configuration screen if it has one) and hands back a `BoundWidget`.
- **`startListening` is scoped to the launcher being on screen**, from `LauncherShell` — a provider only pushes to
  a listening host, so a clock stops ticking without it. Not `MainActivity`, which also hosts settings.
- **A widget's footprint is the item's, not the grid's** — and assuming otherwise was one bug with three faces.
  `planCoordinateDrop` hardcoded one visual cell, so a big widget got a 1×1 shadow *and* a `Move` that resized it
  on drop; the drop shadow separately derived its own size from a cell count rather than from the plan that had
  already resolved one. Both now read the item's own span, and the shadow reads `plan.footprint`.
- **The drag proxy is a *snapshot*.** Every other dragged thing is a cell the launcher can re-draw; a widget's
  content is another app's `RemoteViews`, and a second `AppWidgetHostView` for one id would be a second live
  instance. `AppWidgetHostController.snapshot` draws the on-screen view into a bitmap — L1's `captureBitmap`, for
  exactly this.
- **A widget's tap is not ours to suppress, so the view is *told* the gesture was taken.** `AppWidgetHostView`
  receives the same touches we do and fires its own click on `ACTION_UP`, which is why long-pressing a widget and
  releasing used to trigger it. The gesture machine cannot help — it only decides whether *we* open the item — and
  consuming the release does not either, because the interop layer hands the up to the view on the Initial pass,
  before a Main-pass node could consume. `WidgetCell` sends a synthetic **`ACTION_CANCEL`** instead, driven off
  two signals it already has (`LocalMenuHost.request`, the coordinator's `isDragging`) rather than a second
  long-press timer to keep in step with ours. AOSP's Launcher3 does the same from a `CheckLongPressHelper`.
  `ItemGesturePhase.ownsFinger` came out of the same investigation and is a separate, smaller correction: the item
  now consumes once it owns the finger rather than only while swiping or dragging.
- **And a widget's *swipe* is not ours to take, which is what `EmbeddedViewTouchFrame` is for** — the fix for a list
  widget that could not be scrolled at all, because the surface pan claimed the finger first every time. It is
  structural rather than a missed check: `surfacePagerGesture` runs on `PointerEventPass.Initial`, which runs parents
  **before** children, so the hosted view is always behind it — and while that view can still be intercepted Compose's
  `PointerInteropFilter` hands it moves on the **`Final`** pass, two passes *after* the pan has decided on the same
  event. `AndroidView` does wire the platform's own `requestDisallowInterceptTouchEvent` through
  (`AndroidViewHolder` → the filter), but all that changes is *when* the filter dispatches; it cannot outrank an
  ancestor on Initial. Worth knowing that the *other* two gestures were already correct for the same reason inverted:
  the filter sets `suppressMovementConsumption` while the view has merely handled the down, so `LauncherPager` still
  pages over a clickable widget and stops only once the widget genuinely claims. The pan was the one gesture the
  signal could not reach.
  - **So the claim is made in reverse, and the pan learned to wait.** The frame takes a `SurfaceGestureLock` claim at
    the **down** — on the chance the view wants the gesture — and hands it back once the view has had *its own* touch
    slop's worth of movement without asking to keep it. `surfacePagerGesture` now treats a claim at slop as *"not
    yet"*, deciding on the first event at which nothing is claiming, where it used to hand the swipe back for the
    whole gesture. Both halves are needed: a pan that broke off would make every interactive widget a dead zone for
    surface switching, which is the cheaper fix and was rejected for that reason. The bill is **one event of latency**
    for a pan that starts on a widget, and nothing else — no other claimant ever releases mid-gesture, so none of them
    can observe the difference.
  - Two details that are silent when wrong. `super.dispatchTouchEvent` must run **before** the verdict, since a child
    claims from inside its own touch handling and so arrives on the very event being judged. And a view that does not
    handle the **down** receives nothing further (the filter records that return value and stops dispatching), so it
    can never claim and the frame must answer for it there — otherwise the claim outlives the gesture and locks the
    swipe for the session.
- **Still to come**: resize, widget containers, and the widget area's shrink eviction (it cannot evict to a list).

**Next after that: HOME's second pairing is built, so widgets are the thing it is waiting on.** `LIST_WITH_WIDGET_AREA`
renders, is configurable, and reorders — and its widget area is an empty, correctly-sized, correctly-refusing drop
zone until `GridItem.Widget` has a cell. Two things become owed the moment it does: re-homing what a shrink evicts
(the widget area's `settleDock`, which cannot evict to a list), and seeding it.

Also open: on the surface swipe, **the five transitions past SLIDE** are all that is left of L1's `CrossPager` — the
nested-scroll hand-off, the frosted backdrop and `EjectToHome`, the other three things tangled into it, have all
landed. The home long-press → options menu now exists and is what the free cell space was being kept for; what it is
missing is verbs, each waiting on its own feature (an **app picker**, **widgets**, **page management**). The vertical
list's **"Add apps" picker** is the same gap seen from its own surface — without one, its contents are whatever the
grid seeded. Home **orientation**, or
widgets/containers on the grid. On APPS, **all five layouts render, all the
arrangement-owning ones drag, and every one of them can drag an app out onto HOME**; what is left is the surrounding
behavior: the alphabet filter strip, search, an optimistic layer for both the pager and the card (a drop waits for
the write) + the pager's page indicator. The queued **mechanical** job is done: `core:designsystem/folder/` is now
`collection/` and its types are `AppCollection*` (see the card's notes). Folder
follow-ups: rename, add-via-picker, cross-page reorder, onto-an-app open-then-create.

**P9 is flipped: this is a launcher.** `app`'s manifest declares `category.HOME` + `category.DEFAULT`, which is what
puts Morphic in the system's home-app chooser and lets the home button resolve to it. Four attributes come with the
role and each answers something an ordinary activity never faces — `launchMode="singleTask"` (home must *return* to
the running instance, not stack a second), `stateNotNeeded` (the system kills and restarts a home app freely, and its
state is its stores rather than a `Bundle`), `resumeWhilePausing` (home may resume while the app being left is still
pausing, which is what makes the button feel immediate), and `configChanges` covering everything (Compose re-reads
the window; recreating on a rotate would tear down the drag, the open folder and every baked icon). **One filter
where L1 had two** — its second `MAIN`+`LAUNCHER` filter is redundant, since an intent matches a filter whose
categories are a superset of its own. Permissions stay where L2 puts them, with the module that needs them:
`QUERY_ALL_PACKAGES` is still not requested anywhere, `data:apps` requests **`REQUEST_DELETE_PACKAGES`** (see the
menu notes — without it Uninstall silently does nothing, and L1 only got away without it because
`QUERY_ALL_PACKAGES` covered it), `data:wallpaper` requests **`READ_MEDIA_IMAGES`** (plus the pre-33 `READ_EXTERNAL_STORAGE`
capped at 32) because watching the image collection is the only way a screenshot can be noticed — those two were
declared with the capture flow and then **deleted by the commit that added the rotating service**, which rewrote that
manifest to make room for it, and nothing failed loudly: `RequestPermission` on an undeclared permission is refused
immediately and without a dialog, and the capture screen treats a refusal as a cancel, so Start bounced back and read as
a dead button — and the wallpaper section's live-wallpaper shelf got the narrow `<queries>` it
had been silently missing (without it that query is filtered to this app's own services, so
the shelf renders permanently empty — being the home app exempts `LauncherApps` from visibility filtering, not
`PackageManager.queryIntentServices`). One consequence worth knowing: **shortcuts only exist once we are the active
home app** — `hasShortcutHostPermission()` is false otherwise, so before the flip the menu's first stage would have
been empty for every app on every device.

**Settings — `data:settings` (B7) is real, and seven sections are live.** Storage is **one `@Serializable` JSON blob per
slice** under one DataStore key (`SettingsSlice`, pure and unit-tested), not L1's ~265 flat keys behind a 693-line codec;
per-slice flows, not one god flow; and a slice carries no version because `ignoreUnknownKeys` + fully-defaulted fields
make additive change safe both ways, with the **key name** as the seam for a semantic break. Four slices exist:
`SurfaceRegister` (HOME's layout, per-edge `SideBinding`, transition), `SurfaceMetrics` (per-grid **icon** and **grid**
overrides, plus **`extentDp`** and **`rowHeightDp`**), `AppsChrome`, and `SurfacePaging` (per-pager infinite scroll —
see the surface-swipe rules above for why it is not in `SurfaceMetrics`). **`extentDp` and `rowHeightDp`** are **slot-keyed**, which reverses an earlier call
worth keeping: each was a bare `Map<DeviceConfiguration, Int>` named for the one grid that could answer it
(`dockHeightDp`, `listRowHeightDp`), on the grounds that a slot-keyed map would make most keys meaningless. HOME's
second pairing changed the arithmetic — a widget area is a fixed-extent strip too, and a home list declares a row
height too — so there are now *two* grids per question and a second named map would have been the same pair of
functions twice. The unrepresentable stays unrepresentable one layer out: `SettingsRepository.extent`/`rowHeight`
refuse a slot whose blueprint declares neither, exactly as `updateGrid` refuses one with no `editRange`, because the
blueprint is where "does this grid have one" is declared. **The bill is a storage break**: the old keys are dropped by
`ignoreUnknownKeys`, so a stored dock extent or APPS list row height resets to its blueprint default once. Acceptable
only because nothing has shipped — the key name is this slice's seam for a semantic break, and this is one. Overrides are **sparse and doubly so** — keyed `GridSlot` × `DeviceConfiguration`, nullable per field, and an
emptied entry is *removed*, which is what keeps "a default lives in exactly one place" literally true (the blueprint) and
makes "reset" a plain write of nulls. Reads are **resolved in the repository** (`iconSizing`/`gridConfig`/`gridCols`), so
no surface sees the keying. `GridSlot` names the launcher's ten grids and lives **on** `GridBlueprint` so the two
cannot drift; `GridBlueprints` proves the mapping total. `feature:settings` is **one destination whose sections are panes**, ported from L1's shell: a section list beside a
detail on a tablet, sliding between the two on a phone, with `SettingsSection` as ordinary state inside
`SettingsScreen`. An earlier cut gave each section its own `NavKey`; that was reversed because a pane which shares the
screen with another is not a destination. L1's *actual* mistake is still avoided — it declared that enum in the
**navigation module**, so `feature:home` could import `SettingsSection.WALLPAPER`; ours never leaves the feature.
**The surface register is a cross, because the setting is spatial** — L1's `SurfaceRegister`, ported: HOME in the
middle, the four edges around it, five screen-shaped cards in a plus. Which edge opens what is a fact about *where
things are*, and the four labeled chip groups this replaced made a reader rebuild that arrangement in their head. It
also reverses this section's own earlier reasoning, which is worth keeping: chips beat the segmented control because
"an edge offers six options", and what that missed is that the **edges** were the part with a shape, not the options —
so the options moved into a modal (`SideBindingPicker`, L1's dialog with its radio-and-plain-text body replaced by the
same mockups) and the edges got the picture.
- **A card names what is bound; it does not draw it** — L1's icon-and-label card, kept. A cut that filled each card
  with the layout's own mockup (reusing `AppsEditorPreview`, the drawing the grid editor already owns) was built and
  **reversed at the author's call**: at 88dp a mockup is a smudge, five at once turn a picker into a wall of texture,
  and what a reader scans this screen for is *which edge has something on it*, which a glyph and a name answer at once.
  The picture belongs where a layout is chosen or sized, not where four of them are placed. The picker is L1's radio
  list for the same reason.
- Every side shows the **same** glyph, because every binding is the same *surface* and the label carries which
  arrangement. L1 varied it because its two side surfaces were two modules; the surface taxonomy's collapse of
  drawer + library into APPS shows up right here.
- **HOME's card is the gear alone — the one card with a single target**, and the "two targets" rule below is about the
  four around it. A side card's body changes *what is bound there*, which is the question this cross exists to ask;
  HOME is bound to nothing, so it has no such question. What it has is a *pairing*, and that lives in the Home hub
  (above). The card took a tap for one stretch, between the second pairing landing and the hub existing; L1's own
  center card was inert throughout, for the narrower reason that it had only one pairing to be.
- **The gear is L1's, and it lands on the layout, not just the section.** A card is two targets divided by a rule —
  the body *changes* what is bound, the gear *configures* what is bound already — and since the APPS section edits one
  layout at a time, the jump carries which one (`onOpenSection(section, layout)`; `SettingsScreen` holds it as a second
  saveable enum beside `selected`, because `SettingsSection` is the list's vocabulary and a payload one section can
  carry does not belong inside it). `AppsDetail` applies it in a `LaunchedEffect(Unit)` — once per *arrival* at the
  pane, not per distinct value, or gearing the same layout twice in a row would silently not re-select it.
  **No gear where there is nothing to open**: the gear is drawn only for a layout in `ConfigurableLayouts` — L1's
  `settingsSection` is nullable for exactly that reason. That list is **total today**, since the category card gained
  its own chip, so the branch never takes its `else`; it is kept because the condition states when a gear belongs
  rather than working around one layout, and an arrangement with no editable grid would otherwise land on a pane with
  no chip for it.
- The card is the shape of **this** device, from `usableWindowArea` rather than L1's `LocalConfiguration`, with L1's
  fixed long side (176dp) and the short side following the ratio — the same rule `GridEditor` sizes its mockup by, and
  for the same reason a fraction of the pane was rejected there.
- `AppsLayout.label` is now **one** vocabulary (`feature/settings/LayoutLabels.kt`). It existed twice with *different*
  strings — "Pages"/"Category pages"/"Category cards" against "Pager"/"Pager + category"/"Cards" — each promising in
  KDoc to move when a second screen needed it; the picker was the third.

**HOME is one row in the list, and behind it a *hub* — the pairing switch, a picture of it, and a row per zone.**
Built 2026-08-19; full argument in [docs/HOME_SETTINGS_HUB_PLAN.md](HOME_SETTINGS_HUB_PLAN.md). The list used to
carry HOME's two zones as two top-level rows, which split HOME by **zone** while splitting APPS not at all — and the
tell was that `SettingsSection.meta` had to take a `HomeLayout` so two *index* rows could rename themselves as a
setting in the surface register changed. `HomeLayout` is one enum precisely so a main area and its side zone cannot be
chosen apart; the list was the one place in the codebase taking that couple back apart. `HOME_GRID` and `DOCK` are
still panes, reached through `SettingsSection.HOME`. Six things:
- **A hub rather than one long pane, and `SurfaceDetail` is the reason.** Two zones means two icon groups, and that
  scaffold pins exactly one (heading + live preview) — the icon controls are only legible *through* their preview, so
  the second group's would scroll away from the sliders it explains. Same shape as the Icons hub, different cause.
- **The switch *switches*; it does not select.** This is where it parts from APPS' chip row, which deliberately writes
  nothing because a user can have one arrangement on the left edge and another on the right, both live. HOME has no
  such state — one HOME, one pairing in force — so a selector that only selected would configure a home that does not
  exist. `HomeLayoutPicker` is therefore **deleted** and the register's HOME card is back to **gear-only** (its gear
  repointed at the hub). That reverses this file's previous note twice over, and the reason is not a ranking of the
  two screens: it is that the pairing existed in two live controls at once. L1 also put this choice in its Home
  section, as a row of two mockup cards.
- **`HomeLayout.mainSlot`/`sideSlot` again** — both zone ViewModels resolve *the slot*, so one `IconSizingEdits`, one
  extent control and one `GridEditor` serve both pairings. `GridSizeState.main` is a sum type (`MainAreaSize.Grid` /
  `.Rows`) for `HomeMainSizing`'s reason, one layer down. The Dock section still becomes the **Widget area** section
  (its title, subtitle, extent range — L1's `120..480` against the dock's `80..320` — and whether it has an icon group
  at all); what changed is that this renaming is now a level down from the index rather than in it.
- **The mockup draws the *stored* extent**, through `GridEditor` with both bounds null and its companion split — the
  arrangement it already draws for the APPS list. Reused rather than hand-drawn because which side the zone sits on is
  `SideZoneEdge`'s rule, and a hand-rolled rectangle would eventually disagree with the two panes that draw it
  properly; the extent is the user's rather than the blueprint's, or the hub would contradict the zone's own pane one
  tap away. Drawing it is what earned `HomeHubViewModel` — a keyed read over two flows is a section's shape, not the
  shell's, so `SettingsShellViewModel` stays read-only and there is exactly **one writer** of the pairing.
- **`SettingsSection.parent` is how depth works, and it is a property rather than a back stack** — the hierarchy is one
  level and sections are panes, which is the same argument that kept them out of the navigation graph. Three consumers,
  and the third was missed by the plan: single-pane back closes to the parent; the two-pane list highlights the *listed
  ancestor* (or a detail shows beside no marked row); and `SettingsTwoPane` gained an "up" at all, since
  `BackHandler(onBack = onBack)` would leave settings from a child pane and skip the hub. The single-pane slide
  direction reads `paneDepth`, because `targetState != null` is no longer the same question once something sits in the
  middle.
- **Switching is non-destructive**, which is what makes it one tap with no confirm: each pairing's zones have their
  own stored sizes and their own stored contents, so the one going off screen keeps everything it had.

The rule the settings sections live by is unchanged by any of this — a control appears when the thing it configures
exists: `transition` still has no control, because `SurfacePager` still implements only `SLIDE`, and the
**infinite-scroll switch** is the same rule in the other direction (it appears on the pager pairing and on the two
paging APPS chips, and nowhere else, because only those grids have the setting). The hub's **shared** block is that
rule again: there is nothing genuinely shared between the two pairings today — every control in both panes is
zone-scoped — so no such block is drawn.

**A section belongs to a surface and holds everything about it**, layout controls *and* icon sizing, exactly as each
of L1's five details embedded `IconLayoutControls` under its layout section. **All five surfaces now have theirs** —
Home, Dock, Apps and Folders — and the standalone icon-sizing screen is **gone**, because the folder section took the
last grid out of it and a heading with nothing under it is not a section. `IconSizingControls` shares the UI and
`IconSizingEdits` the write commands, so a section costs neither; a section with one fixed grid supplies a constant slot
where APPS supplies its chip's.
The **folder section** is the smallest and is L1's shape exactly: its `FolderSettingsDetail` has an *empty* layout group
and its icon controls are the whole screen, because `FolderGrid` declares no `editRange` — a folder's card is sized to
the screen, so its rows and columns follow rather than being picked. It states the resolved page size as a fact instead,
and states that it also governs the **category card's expansion**, which is the same `AppCollectionOverlay` on the same grid.
**The name `Icons` has returned with the icon studio** (per-app: shape, background, layers), which is what L1's
`Icons` section actually is — not grid sizing, which L1 never kept there either. It is the **eighth** section and
the third in Personalization, and unlike every other one it is a **hub rather than an editor**: two actions and the
**preset library** — a grid of rendered looks, one tap to apply, a menu to edit or delete — with the editing in a
full-screen destination. It has its own `IconsViewModel`, the last pane that borrowed the shell's, which stopped
being tenable the moment the library had to *render*: a preview needs an app, and an app means `AppRepository`. See
the icon-feature section for the library's rules and for why the editor is not a pane.
**The wallpaper section is the sixth, and the first that is not about a surface** — which is why the list is now two
named groups, Personalization and Layout, as L1 had it. It is a full port of L1's `WallpaperTab` layout: a **two-page
pager of *modes*** ("Single wallpaper" / "Wallpaper rotate") over **three browse shelves** ("My wallpapers", "Backdrops
(By Unsplash)", and a `LazyRow` of installed live wallpapers queried from the package manager). Paging the modes rather
than stacking them is the part that carries meaning — only one of them is ever the wallpaper, and two headings do not
say that. One shared `WallpaperModePage` anatomy serves both (title + status line, apply control on the right, a
preview band, two tonal icon buttons), where L1 hand-wrote its two pages — the same reason one `GridEditor` serves home
and the dock. Three places it does not copy L1, and one earlier note that is now wrong:
- **One button and a chevron, not `SplitButtonLayout`** — both halves of L1's ran `expanded = true`, so the split was
  decoration over a single action (applying always asks *where*). The chevron stays; the seam does not.
- **Previews keep the screen's ratio inside L1's band.** The stored file is already cropped to this screen, so
  stretching it across a landscape band would show a crop the device never displays. `fitAspect` picks which axis to
  fill, which is also what lets the portrait and landscape rotate slots be one composable rather than two identical
  rectangles.
- **Our own `RotatingWallpaperService` is filtered out of the live-wallpaper shelf.** It genuinely *is* one, so the
  query returns it — but the rotate page already owns it, and the card would be the one route with no guard: that
  page's Apply stays disabled until an orientation exists, where a card would open the chooser for a wallpaper with
  nothing to draw. Excluded by the component the repository states, not by our package name.
- **Reversing an earlier call**: this used to say the shelves were absent (empty-state hints for sources that do not
  exist, plus a duplicate of the system chooser) and that the installed-live-wallpaper browser was not carried. Both
  are in, at the author's call — the shelves are where the future *sources* go, so they are the shape rather than the
  filler.

**The effects section is the seventh, and the second in Personalization** — L1's `EffectsTab`, and structurally the
same screen: a chooser, then the sliders belonging to whatever is chosen. It is also the first thing to *write*
`backdropEffect`, which S5f-2 left read-only on purpose. Five things worth knowing:
- **Every slider is live, and the last one to become so was Blur.** They were written against a frosted **panel** that
  did not exist yet, and kept rather than cut on the author's call; the context menu, the home bottom sheet and the
  container panel are that panel. Tint, and every liquid-glass parameter, worked from the moment those landed — they are
  draw-time reads of the stored effect. **Blur was the one that could not**, because the *picture* every surface sampled
  was blurred at the film's fixed strength: the slider moved a number nothing rendered. `ShellViewModel` now asks the
  repository for the wallpaper at **both** strengths and `BackdropRole` says which surface takes which, so the slider
  governs panels while the frost stays fixed. `Plain`'s slider lost its subtitle back when it had nothing to say; the
  control itself is now the honest one in the section.
  - **The frost is still not tunable, and that is the whole point of two pictures.** A surface arriving over HOME has to
    occlude it whatever decoration was picked, so `fullScreenFilm` replaces both parameters — and the layer names
    `BackdropRole.FILM` on the line below the one that names the fixed effect, because those are one decision made
    twice: the strength it renders at and the strength its picture was blurred at have to be the same number.
  - **A panel strength commit costs one decode, the panel's** — each picture has its own subscription keyed on its own
    strength, so the film's is baked once and again only when the displayed wallpaper changes. Keyed on the *pair*, as
    the first cut was, moving the slider restarted both and the film re-blurred at a strength that cannot move; the rule
    is to **de-duplicate where the value is owned**, since a key made of several things cannot say which of them changed.
    At a panel strength of **exactly zero** that picture is the whole
    screen — which is what "no blur" means, and the only strength that reaches full resolution. Everything the launcher
    actually blurs is halved first (`MIN_BLURRED_DOWNSCALE`): a blur wide enough to see has destroyed detail at its own
    radius, so the halving is free to the eye, and it is what keeps the four live buffers of a full-resolution blur —
    decode, pixels, scratch, result, 13MB each on a 1216×2688 screen — out of the first few percent of the slider, where
    the effect is least visible. The film's picture is an eighth of the screen and rounds to nothing beside it.
  - **The orientation reaches `WallpaperRepository.backdrop` as a *flow*, and that is a measured fix rather than a
    style.** Only the rotating pair is two files; for a picked or captured image the path is the same string whichever
    way the phone is held. As a value it was a subscription key, so every rotation restarted the collection, the
    "did the source change?" comparison had nothing to compare against, and the launcher re-decoded and re-blurred a
    picture identical to the one it held — two decodes per turn of the device, one of them nearly full-screen. Passed as
    a flow it reaches that comparison instead.
- **Two entries where there were five, because four of them were one effect.** `Plain`, "Light blur", "Dark blur" and
  "Material You" all blurred the wallpaper identically and differed only in the color painted on top — so the color
  became **`BackdropTint`** (`NONE / LIGHT / DARK / WALLPAPER / CUSTOM`), a parameter of `BackdropEffect.Blur`, and the
  sealed type is down to the two effects that are genuinely unlike: a blur, and a lens. `EffectKind` is the chooser's
  two-value vocabulary and still the section's own rather than the model's, as `BackdropOption` was. Four consequences:
  - **A `MorphicSegmentedButtons`, not a `FlowRow` of chips.** Five options were a chip row; two mutually-exclusive ones
    are what a segmented control is for. This reverses the register section's "chips beat the segmented control"
    finding rather than contradicting it — that was about *six* options per edge.
  - **The parameters now survive a change of wash**, which is what the merge really bought. Switching between variants
    discards them (the sealed type's trade, stated in `SettingsRepository.setBackdropEffect`), and a wash used to be a
    variant — so trying Light and coming back to Dark reset a tuned strength. It is a `copy` now. Switching between the
    two remaining *effects* still carries what they share: `Blur.strength` and `LiquidGlass.blur` are the same quantity
    under two names, so `select` carries it across.
  - **An enum plus a stored `customTintArgb`, not a `Custom(argb)` variant.** A payload-carrying variant would discard
    the mixed color the moment another swatch was picked, so browsing the five would cost the user their color; the
    field lives beside the choice and `CUSTOM` returns to it. Same shape as the icon studio keeping an effect's
    parameters while its switch is off.
  - **One tint ceiling where there were two.** Light/dark stopped at 0.6 (past which a wash is a sheet rather than a
    frost) and Material You went to 0.9 (its hue *is* the effect). A custom color makes that undecidable from the
    option — a pale color at 60% covers less than black at 40% — so the bound is the higher one and the **preview** is
    what tells a user they have gone too far, which is a thing it can now do.
- **A tint of `NONE` paints nothing, and getting that wrong was one `copy` call.** `Color.Transparent` is transparent
  *black*, so `.copy(alpha = tintAmount)` on it does not stay transparent — it resurrects the black at whatever amount
  the last tint was set to, and "None" painted a ~30% dark film indistinguishable from Dark, taking its strength from a
  slider the section deliberately hides while None is selected. `BackdropEffect.Blur.wash` asks the *tint* rather than
  the color: no wash means no alpha to apply. The same trap `BitmapBlur` documents one layer down, where a transparent
  pixel is almost always transparent black and averaging its channels drags black into every edge. The amount is still
  kept in the model, as `customTintArgb` is, so choosing a color again returns to the wash the user had. Pinned by
  `BackdropWashTest`, whose tolerance is one channel step rather than a hundred-thousandth — an alpha is eight bits, so
  0.42 comes back as 107/255.
- **Every edit is a *transform* applied inside the write, never a value the screen computed** —
  `SettingsRepository.updateBackdropEffect`, which replaced a whole-value `setBackdropEffect` rather than sitting beside
  it. A control that builds its next state with `copy` off the effect it last *saw* is a read-modify-write across an
  async store round-trip, and it lost updates: tapping a tint swatch and then pressing a stepper wrote the **old tint**
  back over the new one, because the stepper's lambda still held the pre-tap effect. The steppers are what made it easy
  to hit — a slider drag lasts long enough for the store to catch up, a press does not — and the same race the other way
  round loses the blur strength when a swatch is tapped just after a step. The transform runs where the old value is
  current, inside the same `edit` that writes, which is where this module already puts every other read-modify-write.
  The whole-value setter is *gone* rather than kept for convenience: leaving one is leaving the trap loaded, and an
  effect chosen outright is `updateBackdropEffect { chosen }`.
  - **The live preview takes the same transform, and that half was missed the first time.** The first fix left previews
    built from the effect at hand, on the reasoning that a stale preview is discarded next frame. True of a *drag* and
    false of a *tap*: a stepper's preview stands until its write comes back round, so stepping the tint and then the
    blur built the blur's preview from an effect whose tint amount was still the old one, and repeated taps never caught
    up — the two controls appeared to fight each other. `EffectsDetail`'s `previewBlur`/`previewGlass` apply the edit to
    **what is already previewed** (`dragged ?: state.effect`, read from snapshot state so it is current), so successive
    edits to different fields accumulate exactly as the store's do. One transform, two places, no snapshots anywhere.
    What is left is a converging flicker rather than a wrong result: an intermediate store emission clears the dragged
    effect, so a value can show its previous state for one round-trip before the next write lands.
- **`BackdropTint.washColor` resolves a tint to a color, in `core:designsystem` and not the model** — the
  `DeviceConfiguration` split again: a tint names a choice, where turning one into a color needs the mode's
  `surfaceVariant` and the wallpaper's accent. Public **because the swatches draw the same colors the renderer paints**;
  a chooser that resolved its own would eventually advertise a wash the surface does not use. The swatches are drawn
  **opaque** while the surface paints them at `tintAmount`, since a swatch answers *which color* and the slider under it
  answers *how much* — at a low amount all five would otherwise look like the same pale nothing. The wallpaper wash is
  labeled **"Wallpaper"**, not "Material You" and not "Themed": that is Google's name for the *OS* palette, which this
  deliberately is not (the launcher bridges a monochrome scheme, so the color is read off the wallpaper), and naming the
  source is both truer and the one label that fits a fifth of a phone's width on one line.
- **The pane titles nothing** — not itself, not the picture, not the chooser. The app bar names the section, and a
  frosted panel and two buttons reading "Blur" and "Liquid glass" say what they are without a word over them. The
  headings that remain name something a reader could not otherwise guess: `Tint` over five color swatches, `Lens` and
  `Light` splitting the lens parameters. The chooser sits *under* the preview, which is the order the eye wants — the
  picture is what you came to look at, and the chooser is the first thing you reach for to change it.
- **The controls are the icon studio's slider shape** (`SettingsSliderRow`): name, value and reset over a track flanked
  by a stepper each side. A wash at 28% and one at 30% are hard to tell apart on a photograph, so a readout and a reset
  are worth more here than on a control whose result is a column count. It shares the studio's *arithmetic* — `snappedStep`
  and `finestStep`, which are what make a press land on 1.00 rather than 0.987 and would be silently wrong written twice
  — and **not** its chrome, since the studio fixes its content color to white for a canvas the user switches between
  black and white, where a settings pane follows the theme. Both are in `feature:settings`, so sharing the arithmetic is
  an import rather than an extraction. The tint amount is the one row with **no name**: the swatch row above it is the
  label, and heading it "Tint" under a heading reading "Tint" says one thing twice.
  - **A drag's state is one object for the row's life, cleared by a *write* — never `remember(value)`.** Keying that
  `remember` on the incoming value reads as the obvious way to say "a fresh value ends the drag", and it **recreates**
  the state: a callback Compose has already memoised then goes on using the dead instance. On a stepper the step and the
  release ended up holding *different* instances, so the step wrote into the live state and the release read `null` from
  the dead one — **every press silently failed to commit**, which then presented as the tint reverting, the blur
  vanishing and the two controls fighting, all of them downstream of one lost write. Found with adb by logging
  `identityHashCode` at both ends; three rounds of reasoning about it from the code had each fixed a real but different
  bug. A `LaunchedEffect(value)` clears the one object every closure shares, and the frame of lag that buys is invisible
  because the value it clears *for* is the one just committed. `SettingsCommitSlider` and `SettingsRangeSlider` carried
  the same pattern and are fixed with it — a drag never reached it, having one gesture and one write.
- **The steppers hold to repeat, through `Modifier.repeatingPress`** — extracted from the studio's `StudioStepperButton`
    on this second consumer, because what would drift is the part nobody would think to check: it fires on the *press*
    (a release-fired repeat shows nothing for the whole first tap), waits the platform long-press timeout before the
    first repeat (so a tap can never become two), keys the gesture on `Unit` and reads `enabled` from inside it (keying
    on `enabled` restarts the gesture when a hold reaches a bound, so the finger lifts with the edit made and the
    finish callback never fires), and takes **two** callbacks so a hold is one edit. That last one is worth more in
    settings than in the studio: a commit here is a store write, and for the blur it is a re-blur of the wallpaper —
    thirty repeats would be thirty of each, where now each repeat previews and only the release writes. **Reset stays a
    plain tap**, being one discrete act with nothing to coalesce.
- **The custom color picker expands inline, never in a dialog.** A `Popup` is a separate platform window, so it would
  cover the preview *and* fall outside the pane's offscreen layer, which is what the punch-through draws through — and
  mixing a color you cannot see applied is the one thing a picker here must not ask for. `MorphicColorPicker` reports
  every change and has no gesture-end, so each change **previews** and a button commits; writing per change would be a
  JSON encode and a file write per frame, which is what `SettingsCommitSlider` exists to avoid.
- **Liquid glass is hidden, not disabled, below API 33**, with L1's sentence explaining why. An effect that silently
  comes out as a plain blur is worse than one that is not offered.
- **It has a live preview now, and it is a real frosted panel rather than a drawing of one** (`BackdropPreview`). This
  said there could be none, on two grounds that have since been answered: the pane punches through to the window for the
  wallpaper (`BlendMode.Src` over `PunchThroughPane`, the same trick every icon preview here uses, which needed
  `Theme.Wallpaper` to exist), and the card is drawn by `wallpaperBackdrop` itself — sampling by screen position, so its
  crop is continuous with the sharp wallpaper punched through around it. Nothing is approximated; it is a panel with two
  lines of text on it, because legibility over a photograph is what the effect is *for*. L1 had no preview here at all.
  What it previews is the **material** rather than any one surface, which became the point once the menu took the film's
  recipe: the film fixes its own strength and the icon plate follows the sliders, so the card is drawn at the plate's
  parameters — the ones every control here still moves — and shows the same variant and wash the film shows. It draws
  **no rim** for the same reason nothing else does. Four things:
  - **`LocalBackdrop` is provided at the *pane*, not at the settings zone's root**, which is the narrower half of the
    shell's own rule rather than an exception to it. The shell provides it at a zone boundary because every surface in
    that zone samples the wallpaper; here exactly one thing does, and it is a preview rather than a surface, so anything
    higher would invite a second pane to frost itself against a picture nobody asked for. L1's mistake was different
    again — it provided the *launcher's* backdrop inside `HomeScreen`, so its settings feature needed a duplicate.
  - **Every parameter previews per frame, blur included — and blur is the only one that costs anything.** The wash and
    the saturation boost are draw-time reads of the effect, so the pane keeps the dragged value in state and
    hands it to the card. Blur lives in the *bitmap*, so it needs a second picture: `WallpaperRepository.backdropPreview`
    decodes the wallpaper **once** at a quarter of the screen and re-blurs that per emission, where `backdrop` is a
    subscription per strength (a fresh decode each time, which at 10–20ms a decode cannot keep up with a finger). Three
    things make it work, and each is the interesting part:
    - **The strength arrives as a `Flow`, and the holder is a `StateFlow`.** As a subscription key it would re-decode; as
      a flow it reaches the blur. And a `StateFlow` **conflates**, so the blur runs on the newest value and drops the
      ones it was too slow for — a drag produces as many frames as the machine can and never a backlog. That is the
      correction `IconPreview` needed the hard way, had for free here by picking the right holder.
    - **The buffers are lent, not allocated.** `BitmapBlur.blur` takes an optional `scratch`, and the decoded pixels are
      copied into a working array rather than re-read — so a frame allocates only its result bitmap. Ping-ponging two of
      those is the next step if it ever janks, and it needs a way for a caller to notice a bitmap it already holds has
      changed, which Compose's instance-keyed `remember` does not give.
    - **The reduction is fixed and the radius is measured against it**, so a strength is the same *softness* here as on a
      surface. It cannot follow the radius the way `backdrop`'s does — that would re-decode per strength, the thing being
      avoided — so the bill is resolution at the sharp end: below about an eighth of the slider the settled picture holds
      more of the wallpaper than the dragging one. The pane draws the dragging picture **only while the blur strength
      differs from the stored one**, which is also what keeps a tint drag on the full-size picture: comparing the two
      strengths is the one thing the pane knows and the state holder does not.
  - **The picture is the launcher's own panel picture, at the stored strength** — the same repository request
    `ShellViewModel` makes, deliberately, so the preview shows what the panels show rather than something made for it.
    Null renders the card's scrim, which is exactly what a real surface does with nothing to sample.
  - **It pins in a `stickyHeader`**, `SurfaceDetail`'s arrangement and for its reason: the sliders are read *through* the
    picture, so reaching one must not scroll it away. That is why this pane became a `LazyColumn`, and it is safe with
    the punch only because `PunchThroughPane` nulls the overscroll factory — a stretch re-composites the content and the
    punch stops reaching the window for as long as it lasts.

The `busy` flag is L2's own rather than a port, because L1's picker went to its crop screen and the work happened
behind that. "Choose image" is `PickVisualMedia` and opens the **crop screen** — `feature:settings`' own `NavKey`
mapped by `app`, a destination rather than a pane, because back out of it means "not that image".
**Every section has L1's live icon preview**, between its layout group and its icon group: a real `AppCell` (or
`AppRowCell`) at the **real cell size** that section computed, with the cell and both icon guardrails outlined over it,
tracking the sliders per frame (`onPreview`) rather than on release. It is what makes the icon controls legible — a
fraction and two dp bounds say nothing about *this* cell, and which of the three is binding is the whole question while
dragging. The geometry is **asked for, not copied** (`cellIconLayout` in `core:designsystem/cell`, so a guide cannot
drift from the cell it is drawn over — L1 restated the cell's padding under a "keep in sync" comment, and it publishes
the cell's **inner box** as well, since a guardrail larger than the cell must stop where the icon really can, not on the
outer ring); each section
supplies its own cell size, which is the part that cannot be shared (home divides its area, the dock divides its height
setting, APPS branches on layout, the folder asks `appCollectionInnerSize`); and the guardrails are **grayscale by stroke**
(solid = cell, dashed = upper, dotted = lower) because L1's green/red cannot survive a palette that reserves red for
`error`. **The wallpaper behind it is L1's trick and it has landed** — the cell box composites with `BlendMode.Src`,
punching through the pane (`PunchThroughPane` composites the detail offscreen with `withSaveLayer`) to the window,
which shows the wallpaper because `app`'s theme now carries `Theme.Wallpaper`.
**The four surface sections share one arrangement, `SurfaceDetail`, and it is L1's** (`IconDetailPortrait` /
`IconDetailLandscape`, which its five details all went through — an earlier note here said those scaffolds were "not
carried", and that was reversed). Three things about it are load-bearing rather than decorative:
- **The grid editor is first, then the sliders that constrain it** — the editor is the picture of the surface, so it is
  what a user arrives looking for, and the margin / height / row-height sliders under it are adjustments *to* that
  picture, each previewing live into it. L1 orders all five details this way (editor, then extent, then padding). Where a
  section chooses *what* it is configuring — the APPS chip row, L1's home-surface cards — that comes above the editor,
  because it decides which editor you are looking at.
- **The icon heading and the preview pin together** in a `stickyHeader`, so the preview stays on screen while the
  controls scroll under it. That is the whole reason these panes are a `LazyColumn` rather than a `Column` +
  `verticalScroll`, and it is what makes the icon controls legible at all: their three numbers are read *through* the
  preview. `IconSizingPreview` is the body only and `IconSizingControls` emits no heading — both belong to the pinned
  block now, so neither states it a scroll apart.
- **Landscape is a different arrangement, not a narrower one**: the layout group scrolls away, the heading pins, and the
  icon group fills the viewport as a final full-height item — controls scrolling on the left, preview fixed at a
  **fixed** 220dp on the right (L1's number, and fixed because the preview draws a cell at true size, so the column has
  to hold one rather than take a share of the pane). A phone in landscape has room for a cell beside its sliders and none
  above them.
What is *not* carried from L1's scaffolds is the machinery they wrapped around the punch — the offscreen layer, the pane
background, the insets and the disabled overscroll are all `PunchThroughPane`'s here, because L1 had no shared pane and
so repeated them in both. Overscroll being off is what makes a lazy list safe with the punch at all: a stretch
re-composites the scrolling content and the punch stops reaching the window for as long as it lasts.
**The APPS section is one section with a chip per layout** — the settings mirror of one `feature:apps` for five
layouts, and the same argument: they differ only in arrangement, so what a user configures is "the paged one" or "the
list". Selecting a chip writes nothing (which layout you *get* is per home edge, in the register). Its resize is **one
write** where home's is two, and that is the ordered/coordinate split reaching settings: every APPS grid is ordered or
derived, so the flow re-densifies and there is nothing to displace — the pager's store even re-paginates itself, since
a capacity change is the sync it already runs for installs. Only the edge's *axis* is read, as in L1's drawer editor.
The **list** is the odd one: one lane, so it has no grid to edit and its size *is* its row height
(`AppsListGrid.rowHeightDp`, the third way a cell gets a height — see the derive-vs-store rule in the design-system
notes). **Its slider's bounds are the icon guardrails plus the row's own inset** (`rowHeightRangeDp`), so the icon range
slider *governs* the row-height slider: a row shorter than `minIconDp` + padding cannot honor the smallest icon
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
chunky icons before switching them off. **Chrome is a third slice, `AppsChrome`** — `SearchPlacement` (which `core:model` had built and nothing consumed) plus
the tab bar's `VerticalEdge`, whose KDoc had named this consumer since B0. It is the one setting whose **only current
consumer is the editor preview**: search is unbuilt on the APPS surface and neither pager draws a tab bar, so this is a
deliberate exception to "no model in a vacuum" — a preview is a real consumer with a real question, and L1's editor
draws both from its own stored pair. Its search default is `Hidden` where L1's is `TOP`, because a default that drew a
search bar the launcher has not got is the one thing a preview must not do. **Which options are offered is
layout-dependent**, which is `SearchPlacement`'s whole shape: a standalone layout pins to an edge, the category pager
embeds in its header. L1's flat `SearchPosition` let a user pick a state their layout could not draw.
**The category card has a chip now, and it closed the one gap this section had.** It was held back because a card is a
*tile*: how narrow one may get is not an icon guardrail, its blueprint declares no icon sizing, and picking a ceiling
by hand was what the rest of this port had avoided. Two things resolved it. The card's settings **already existed** —
`AppsCardGrid` declares an `editRange` and per-device lane defaults, and `AppsScreen` was already reading both the lane
count and the margin — so the gap was a value the user could not reach rather than a feature nobody had built. And the
ceiling belongs where every other floor is stated: **`CellFit`**, as a `MinCell` beside `WidgetMinCell`, which
is the case that pair of overloads exists for.

**Then the card gained real settings, and that retired the constant.** Two flat numbers were picked by eye and both were
wrong — 96dp let a 393dp phone draw four lanes of unreadable dots, 120dp only looked right because it absorbed chrome it
could not see. The fix was to stop guessing: `AppsCardGrid` now declares `icon = IconSizing(showLabel = false)`, so
`CellFit.cardMinCell` *derives* the floor as `2 × minIconDp` + the card's two paddings + the gap between lanes — the same
inversion `minCellWidthDp` performs for an icon cell, applied to a tile holding four icons. Ask for larger icons and the
lane count comes down on its own. The one thing to keep straight is that a column fit divides the grid's **raw** width, so
the floor must describe a *lane* (card + spacing) and the callers must subtract the gutter first; getting that wrong is
exactly how both constants went wrong.

**The tile is the square, and the label is outside it** — iOS's App Library shape, reached in two corrections. The card
was originally the square with the title *inside* it, eating into it from the top: the leftover box came out wider than
tall, the slots sized themselves from its *height*, and the icons ended up the smallest thing on a tile whose whole job
is to make them recognizable. Making the icon area the square fixed the icons but left the title sharing the fill, which
reads as a header bar rather than a label. Now the background, corner and padding are all the **tile's** and the name
sits under it, centered — so the fill traces the icons exactly and a card is a square plus one line of text.

**`CardChrome` is the tile's own settings** (`core:model`, stored as a fifth `SurfaceMetrics` map keyed slot × device,
sparse like `IconOverride`): title scale, corner radius, and the icon area's **outer** and **inner** padding. All three dp
values **start at zero** — a card begins as a plain rectangle of edge-to-edge icons, and every bit of decoration is
something a user turned on. That is deliberate, after a version where the inset, gap and corner were hardcoded numbers no
control could reach. The outer padding insets the *icon area* only; the title keeps a fixed inset, because a title against
the corner reads as a rendering fault rather than a choice. The section therefore shows lanes, margin, an icon group with
**no text controls** (`APPS_CARD` joins `APPS_LIST` in that gate — the slots carry no labels), and the four chrome
sliders; still no rows (`minRows = null`) and no infinite scroll (declares no `wraps`). The expansion's sizing stays the
**Folders** section's, since an expansion *is* that overlay on `FolderGrid`.

**A card slot draws `CategoryPreviewIcon`, not an `AppCell`** — the icon alone, sized against the *whole* slot. A cell
wraps `IconLabelCell`, which insets by `CellPadH`/`CellPadV` and reserves a label row, so two adjacent slots kept an 8dp
gap however far the spacing slider was dragged down: a control unable to express the thing it is named for. A slot has no
label and no chrome; it *is* the icon's box, which is what `iconPercent = 1f` means literally here. The **overflow
cluster** is sized by the same expression (`CategoryClusterTile`): it stands in for one of the four apps, so given the
raw slot it stayed full-size while its neighbors shrank with the slider. It also draws **no backing plate**
(`IconPreviewPlate(backing = false)`, which drops the inset with it, since the inset only exists to keep icons off the
plate's rounded edge) — a cluster sits inside a tile that already has a fill, so a plate there is a box within a box and
made it the one slot on the card with a visible container. A folder on a *grid* keeps its plate: loose among plain
icons, that is what makes it read as one object. The preview draws the cluster too, off the
shared `categoryOverflowCluster` split — a preview that divided the apps differently from the surface would be worse
than none, and four-apps-no-cluster is the one arrangement a category big enough to need this screen never has. Relatedly, `APPS_CARD`
had to join `AppsViewModel.IconSlots` — leaving it out was not a skipped lookup but a silent substitution, since an
unresolved slot falls back to `LocalIconMetrics`, which inside `AppsCategoryCard` is the **folder's**: the surface drew
labels the card grid explicitly turns off while the settings preview drew none.

**The preview's fill is the one number that does not match the surface** — `PreviewFillAlpha` at 0.5 against the
surface's `CardAlpha` of 0.10. A card on the launcher sits on the *frosted* backdrop, so a faint tint reads clearly;
the settings punch reveals **raw** wallpaper, where 10% all but disappears and the corner-radius slider has no visible
corner to act on. Two subtler fixes were tried first and both failed, which is why the number is written down rather
than reverted: a wash behind the card *cut* the contrast (in a dark theme the wash and the card are both near-black —
the real frost works because it **blurs**, not because it washes), and an outline blended into the wallpaper it was
drawn over. Everything the controls actually change still matches the surface exactly; only the opacity is exaggerated
so those are legible.

**Its preview is a whole card, where every other section previews a cell** — `CategoryCardFace`, extracted to
`core:designsystem/cell` for exactly this: `feature:settings` cannot depend on `feature:apps`, and a second card
hand-rolled beside the sliders would drift from the one the surface draws. Same extraction, same reason, as
`IconPreviewPlate`. Half of what the card's controls shape is the tile *around* the icons, which a lone cell cannot show.
It draws a **made-up category filled with installed apps**, never one of the user's: a real category would show whatever
that phone happens to hold, and one holding two apps leaves half the slots empty — which is exactly the state in which
the spacing and padding sliders show nothing. A preview has to draw the full case for its controls to be readable. It
**punches through to the wallpaper** like the cell previews (`BlendMode.Src` over `PunchThroughPane`), since a card is
translucent by design and judging its fill against a flat panel judges it against something it never sits on. And it is
drawn at the width one card is *really* given — the lane less its share of the spacing between lanes — with
`requiredWidth`, because the pinned header hands down a fixed full-width constraint that a plain `width` is coerced into:
the card rendered at the pane's width, silently, which is the one thing a size preview must not do. The card's four
sliders sit **below** it in the pinned group with the icon controls, not above it in the scrolling layout group — a
control that scrolls its own preview off the screen cannot be judged.
**Resizing a grid names an edge, not a count**, because that is what decides where the items go — removing the *left*
column shifts everything left, removing the right one drops what sat there. So a press is **two writes**: the count
(`updateGrid`) and the placements it displaces (`GridReflow.edit` → `LayoutRepository.apply`), ordered grow-first for
an add and place-first for a remove so no observer sees a grid too small for its contents. That is why
`feature:settings` depends on `data:layout` at all: only the button press knows the edge, and a surface re-reading the
new size later cannot recover it. **The dock's version spills onto home** (`settleDock`, shared with
`HomeViewModel.fitDockTo` so the two triggers cannot disagree), never deleting as L1 did. The **companion zone can be on any of four sides** (`CompanionSide.of(edge)`, bridging `SideZoneEdge`), which is what
makes a top-strip widget area draw above its list rather than below it — and a caller-supplied `preview` now sits
**inside** the companion split rather than replacing the whole screen, so HOME's list shows its lanes *and* the widget
area over them. (Passing a preview replaced everything while only the APPS layouts passed one; they have no companion,
so nothing changed for them.) `LanePreview` moved to `component/` when the home list became its second consumer, as
`IconPreviewPlate` did. One `GridEditor` composable
serves every grid — a screen-shaped mockup at a **fixed size per posture** (L1's four numbers; a fraction of the pane
made the preview a different size on a tablet and, at a tall phone ratio, taller than the section holding it), ringed
by L1's own button arrangement, in **three shapes it takes from L1 as well** — both axes editable gives the plus-shape
(**removes along the top and left, adds along the right and bottom**, each pair spread to the preview's extent so a
button sits at the corner it acts on); *columns only* drops the top and bottom rows and gives each side rail **− above
+ for its own edge** (L1's `showRowControls = false`, and better than hiding the rails, which left the controls
furthest from the columns they change); *neither* draws the frame alone. `colBounds` is nullable to say the third,
symmetric with `rowBounds` — both mean "this axis is not the user's to set" — and the caption follows, down to being
omitted entirely when there is no axis, since a list showing "1 column" would be a count masquerading as a choice. The
companion zone is drawn at its real proportion **and on the side it really occupies** — `EditorCompanion` carries a
`CompanionSide`, four values where `DockEdge` has two because each section sees the split from its own end (home's
companion *is* the dock, bottom or end; the dock's is the pager, top or start), and the mockup splits with a `Column` or
a `Row` to match. Without that, a phone-landscape dock would draw as a thin strip along the bottom while the surface
drew a rail.
L1 had two ~220-line near-copies of this. **The APPS editor takes a `preview` slot**, as L1's drawer editor does, so
each layout shows the surface it configures: the three scrolling grids draw `ReflectivePreview` — cells at their
*derived* aspect, filling downward and clipped at the fold, which is the only mockup that makes adding a column
visibly gain rows — while the pagers and the card grid keep the even lattice, because a FIXED_PAGER really does divide
its page evenly and a card's height *is* its width. The category pager additionally draws L1's header + tab row, and **the list gets an editor with no buttons on it** —
one lane and a declared row height means nothing to press, but two things to see: `LanePreview` draws full-width lanes
at `rowHeight ÷ width` (icon square beside a label bar, the one structural fact separating a list from a one-column
grid at this size), and the search edge sits above or below them. Deliberately *not* `ReflectivePreview`, which derives
height from width: that is what the scrolling **grids** do, where a list's height is declared — the three-way split in
`GridBlueprint.rowHeightDp`, reaching the preview layer. **Every slider that moves a preview previews live**
(`onPreview`), including the dock's *height*, where the shown value feeds the companion split, the fitted grid, the
editable range, the caption and the icon preview's cell height together — a preview showing the new proportion but the
old row count would be worse than one showing neither, since the row count is what the height decides.
**A grid's horizontal margin insets the lattice only, never the companion zone**: home's pager and dock store separate
margins, so insetting both from one number would show a dock narrowing because the pager's slider moved (L1 passes
`insetFraction` to its `GridPreview` and none to its `NonGridPreview`). **What is not carried is the color** — it tells add from remove by red vs
green, which the palette forbids — and that costs nothing, because in L1 the *position already encodes the action* and
the color was reinforcement. An earlier cut mistook the color for the signal and centered a −/+ pair on each edge
instead; the arrangement above replaced it.
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
**One set of icon defaults, taken by every grid** (`IconSizing()` unmodified everywhere): the icon **fills its cell**,
capped at **48dp**, never below **24dp**, with `IconSizingRanges.IconDp = 24..120`. At 100% the *upper guardrail is the
icon size* on any cell bigger than it, so icon size is one number in dp rather than a fraction of a cell the user has to
picture — the per-grid fractions this replaced (home 88%, app grids 75%) were the fraction doing that job, and
double-counting density while at it, since a narrower cell already gives a smaller icon at 100%. The **lower** guardrail
is a *cell* floor (`CellFit` inverts it), which is why its bound is 24 and not lower: at 16 the arithmetic offered a 24dp
cell — thirteen rows in a 320dp dock — that nothing could be tapped in. 24 is also L1's own unused `MIN_CELL_DP`
("press-area floor"). `IconMetrics`' Compose-side defaults mirror `IconSizing`'s and **must keep doing so** — same record,
two type systems, so a difference would surface as a jump the moment the store answered.
`core:designsystem/grid/CellFit.kt` answers "how large can this grid be": `boundsIn`/`editableRangeIn` as a **ceiling**
for a grid whose counts a user picks, and `fitGridConfig` reading the same maximum as a **value** for the dock's rows.
Beside it, `usableWindowArea` is the **one place the launcher measures the screen** — home, the APPS surface and every
settings section read it, because a settings screen cannot measure the surface it configures and so must measure the same
window the same way (L1 kept `homeGridArea` in settings and `pagerBoundsInWindow` on the surface, which could disagree).
The **dock section** (S4c, done) is the first consumer of both and the pattern the rows/cols editor follows — its
bounds are computed where the window is measured (one usable cell up to a third of the screen) rather than stored,
because a store cannot check either without measuring. **Every surface now reports its `DeviceConfiguration` to its ViewModel** (`setDevice`), which *replaced* the
narrower `setGridConfig`/`setPagerGrid`: pushing the input down means page capacity and icon sizing both derive there.
Full plan, phase state and the settled dock spec: [docs/SETTINGS_PORT_PLAN.md](SETTINGS_PORT_PLAN.md).

**Navigation + shell (B5) done.** `core:navigation` holds `HomeRoute` and a two-method `Navigator`; feature
vocabulary stays *out* (L1 exported an 11-value `SettingsSection` to every consumer), which is why `SettingsRoute`
itself lives in `feature:settings` now that it carries a section — see the surface-menu notes. `app`
once declared its own dev-harness key, which is what proved `entryProvider` is a mapping and not a registry — a
destination can be contributed by any module, so `feature:settings` keeps its own routes. `feature:shell`'s
`LauncherShell` is the launcher — `SurfacePager` with `HomeScreen` center and side surfaces from the register — and it
owns the **launcher theme boundary**, which is why `HomeScreen`/`AppsScreen` no longer theme themselves. It also owns
the **full-screen frost**, which `SurfacePager` takes as an `overlay` slot between the center and the sides: not panned
with either, and driven by `SurfacePagerState.progress` (the pan collapsed to "how far in is the other surface",
unsigned and edge-agnostic) so the content slides while the frost fades. The launcher
boots into it. The dev harness that used to sit beside it as a peer destination is deleted, and with it the
floating button in settings that opened it — so there is no dev chrome in the app at all.

**The drag is the launcher's, not a surface's — so an app can be dragged from APPS onto HOME.** `feature:shell` hosts
the **one** `DragCoordinator` and provides it through `LocalDragCoordinator`; every surface reads it. Each surface used
to remember its own, which was indistinguishable from this while no drag crossed a boundary. Three changes carry it,
and each removed something rather than adding a layer:
- **A `DropZone` answers for itself, end to end** — it carries its own `planner` *and* its own `onDrop`, and
  `DragCoordinator.drop()` dispatches the landing to the zone the finger came to rest in before returning. That is
  docs/DRAG_AND_DROP_DESIGN.md §10's *"behavior travels with the destination zone"* made structural, and it is
  **required** rather than tidy: an app lifted in the drawer is released by a cell in `feature:apps`, and the thing
  that must commit it is *home's* grid. It deleted the `when (zone.id)` every multi-zone surface repeated in its
  planner and its drop, and with it the `AppCollectionDragDelegate` hand-off and the construction-order squeeze three files
  documented (the delegate had to exist before the coordinator, which had to exist before the folder host). A cell's
  `onRelease` is now only what the *source* surface knows — that a drag has left it.
- **`RegisterDropZone` + `LocalSurfacePresented`.** A slot stays composed for the whole of a pan and, when a drag was
  ejected from it, for the whole of that drag — which is what satisfies the "keep a source surface composed while a
  drag from it is in flight" rule and is why an ejected drag survives with no re-tracking. The bill is that *composed*
  is no longer *on screen*: an off-screen surface's `onGloballyPositioned` does not reliably re-fire as the pan moves
  it, so bounds it published while it *was* on screen would sit in the registry claiming the finger from off-stage. So
  registration moved out of the layout callback into a composable gated on presence (defaulted from the local, so it
  cannot be forgotten), and teardown removes a zone **by instance**, which is what lets two folder overlays hand
  `ZoneId("folder")` over inside one composition. The same local gates each surface's floating proxy, so two never
  appear under one finger.
- **`EjectToHome` is one method** — close this surface — because that is all that was left to do. Compare L1's
  `HomeDragBridge`, which passed the app, the finger in window space and a grab offset, because its `CrossPager`
  stopped delivering pointer events to either subtree as it collapsed and the drag had to be re-tracked at the
  ancestor. **Two triggers, and the split is a property of the layout rather than a preference:** the **derived**
  APPS layouts (A–Z list, A–Z grid) eject *on lift*, since they own no arrangement and a drag on one can only mean one
  thing; the layouts that **store** one (pager, category pager, category card) eject when the finger reaches
  `TopActionZone` — see the band's own rules below. HOME then takes the app wherever its zones do: the pager and the dock **place** it (`Move` is
  an upsert, so an app with no placement needs no separate path — `HomeState.catalog` is the one addition, so home can
  draw an icon for an app it has never placed), and the vertical list **appends** it (MovingGap migrates a gap from
  where an item already *is*, and a stranger is nowhere). Nothing is removed from the drawer on the way: an app lives
  in the APPS arrangement *and* may sit on home.
- **The category card's grid stopped being lazy**, which reverses its own note. Its preview icons are draggable now —
  a preview is the only part of a card that names one app, so it is the only place a re-file can start without opening
  the category first — and a lifted cell owns the pointer stream, so a card disposed while auto-scrolling toward the
  target would kill the gesture. `HomeListSurface` made the same trade for the same reason. One consequence to know:
  a card's measured rectangle is no longer trustworthy inside a scroller, so each entry in the hit-test map records
  the **scroll offset it was measured at** and the correction is subtracted at hit-test time — self-contained, and
  zero-cost if `onGloballyPositioned` does re-fire.
- **The band has two states and two modes, and the states are the design.** `TopActionZone` is a full port of L1's,
  not the always-open banner the first cut drew. **Collapsed** it is exactly the status-bar inset deep — a hint that
  costs no screen and cannot be hit on the way to home's top row. **Expanded** (96dp) it has committed to being a
  target and names what it will do in words, which is the only way to tell *remove* from *uninstall* and the reason a
  drop shadow could never do this job. The threshold between them is **asymmetric** (status bar to arm, 96dp to
  disarm), which is what stops it chattering while the finger sits near the boundary — L1 spelled the same rule as two
  named thresholds. `rememberTopActionState` owns the timing; `DropIntent.REMOVE` is the second value with no cell
  behind it, added on `REORDER`'s terms rather than letting the band return a `PLACE` plan whose footprint is a lie.
  - **The two modes commit at different moments, and that is not a preference.** `ADD_TO_HOME` opens *at once* and
    fires on a **dwell** (~700ms), because its whole point is to get the drawer out of the way *while the finger is
    still down* — a release would end the gesture it exists to continue. `DELETE` opens after a shorter dwell
    (~300ms) and fires on **release**, because a destructive action that armed itself under a held finger would be a
    trap, and fingers cross the top of the screen on their way elsewhere.
  - **Firing collapses the band, and the next opening waits.** After the hand-off the mode flips to `DELETE` under a
    finger that may not have moved, so re-opening immediately would swap "Drop to home" for "Remove | Uninstall"
    in place — which is how a user ends up hovering a delete target they never went looking for. L1's
    `TOP_ACTION_SWITCH_GRACE_MS`: act → shrink away → pause → open again, offering something else.
  - **It is registered as a drop zone in *both* modes**, and in `ADD_TO_HOME` that is for the **masking** rather than
    the drop: while the finger is up there it must stop being the drawer's, or the pager's planner keeps migrating its
    reorder gap and a release before the dwell lands the app at the top-left instead of canceling. L1 blanked
    `hoverTarget`/`pendingGap` explicitly for the same reason; being the topmost zone says it structurally.
  - **Both targets are the shell's**, because the band spans every surface and the item under the finger may have been
    lifted in the drawer and never placed at all. `ShellViewModel.removeFromHome` is a plain `RemoveFromGrid`, which is
    also why it needs no "is it placed?" test — on an unplaced app it deletes no rows, so Remove *is* the cancel.
    `AppUninstaller` (`data:apps`, the sibling of `AppLauncher`) only opens the platform's uninstall prompt, which
    confirms and acts for itself; nothing is removed from the layout first, since a declined uninstall would otherwise
    leave an installed app the user can no longer see. **One bound:** an app dragged out of a home *folder* onto
    Remove goes back to that folder — it has no grid placement to remove, and the shell cannot see folder membership.
    L1 behaved identically, for the same reason.
- **The item context menu is the shell's too, and for the band's exact reason** (P7, `core:designsystem/menu`). One
  `ItemMenuHost` provided through `LocalItemMenuHost`, one `ItemMenuOverlay` above every surface — because the verbs
  on an item's menu belong to the **item**, and the same app is reachable from home, from the drawer and from inside
  a folder. L1 answered it per surface (home's `ItemContextMenu`, a near-copy `SideContextMenu` for the drawer and
  library) and wrote the two stages, the loading state and the toggle twice. The shell binds the three app commands
  once (`AppInfoOpener`, `AppUninstaller`, `AppShortcuts`); a surface contributes only what it owns — home's
  *Remove*, the drawer's nothing. It changed none of the drag wiring, exactly as this note predicted.

**Context menus (P7) — `core:designsystem/menu`, one host at the shell.** Long-press an **item** and its menu opens
under the finger; move on and it becomes a drag, exactly as `ItemGestureMachine` has modeled since B4. Long-press
**empty space** and the surface's own menu docks to the nearest screen edge. Ported from L1's `InlineContextMenu` +
`ItemContextMenu` + `ContextMenuPopup`, with these differences:

- **The menu outlives the finger, which meant changing the gesture machine.** `MenuOpen` + `Up` used to emit
  `DismissMenu` — written before there was a menu, and unusable once there was: a row can only be tapped after the
  finger is off the item, so the *release* is how the user reaches the menu. It now closes on a choice, on a tap
  away, or on the drag that may follow. A **cancel** still dismisses (the pointer was taken away, not given up).
  Nothing depended on the old behavior: every `onDismissMenu` in the tree was `{}`, which is also why that parameter
  is gone — the contract dismisses the host itself, on `launcherItemGestures`' "wiring that cannot be forgotten
  belongs in the one place every caller already goes through" rule.
- **It renders inline, never in a `Popup`** — L1's own conclusion, and the reason its two implementations exist. The
  menu opens *while the finger is down*, and a `Popup` is a separate platform window: raising one mid-gesture takes
  focus and can cancel the pointer stream the drag depends on.
- **The anchor is reported by the gesture, not reconstructed by the surface.** `onShowMenu` now carries the
  rectangle the modifier is attached to — which, by the touch-target rule, *is* the item's visible extent. L1 rebuilt
  it three ways (cell centers on the grid, an icon half-width plus a Y offset in a folder, a row's bounds in the
  list) and each could drift from what was drawn. `positionInRoot() + size`, never `boundsInRoot()`, for the
  clipped-inside-a-scroller reason stated elsewhere in this file.
- **Placement is pure and unit-tested** (`MenuAnchoring.kt`): a tall frame stacks the menu above/below and a wide one
  puts it beside, each flipping toward the half with room, then clamped into the frame with one gap doing both jobs
  (off the item, off the edges). Two departures — the halves are judged against `uiInsets`' usable area rather than
  the raw window, and `MenuPlacement` is **four values** where L1 had `(vertical, towardEnd)`, the same correction
  `SideZoneEdge` made to a pair of booleans.
- **Two stages: the app's own shortcuts first, the launcher's actions behind a chevron.** That order is L1's and it
  is right — shortcuts are what the *app* offers and are what a long-press is usually for. An item with no shortcuts
  (a folder, an app publishing none) collapses to one stage with **no toggle**, so the second stage never announces
  itself as missing. The load is a **suspending lambda the menu owns**, where L1 ran that `LaunchedEffect` in each of
  its three surfaces; it is called from the menu's composition, so it cancels when the menu closes.
- **It is modal, and the overlay's own full-screen tap-catcher is what makes it so.** Compose hit-tests siblings
  in reverse draw order and stops at the first hit, so a press anywhere reaches the catcher and never reaches the
  surface beneath it — no consumption involved, which is why the items' deliberate `…IgnoreConsumed` reads do not
  defeat it. The overlay also holds `SurfaceGestureLock` for as long as it is up, so nothing may pan out from under
  a menu, and back dismisses it before anything else answers.
  - `launcherItemGestures` briefly carried a second guard — ignore a new press while a menu is open — added on the
    reasoning that consumption could not stop a tap "away from the menu" from also launching the icon it landed on.
    That reasoning was wrong about *which* mechanism blocks it, and the widget picker's scrim demonstrated as much:
    a long-press over an icon behind it produces nothing from the icon. The guard was removed as dead code. The one
    case sibling hit-testing genuinely does not cover is a press already **down** when an overlay appears, since a
    pointer keeps the hit path it was assigned at DOWN — but that is the gesture that opened the menu, which is
    supposed to keep running.
- **Colors come from the theme and the panel is frosted with the film's own material.** L1 hardcoded `Color.White`
  throughout, which would be the one place in the launcher ignoring the wallpaper-brightness signal the whole theme is
  built on. The frost is `Modifier.filmBackdrop` — the same recipe `SurfaceBackdropLayer` uses, clipped to a rounded
  rect — so a menu on HOME is a piece of the sheet the APPS surface is read against rather than a second glass at the
  user's own strength. It was briefly the launcher's first *panel*, reading the stored strength and drawing liquid
  glass's rim; both were given up for that, the rim deliberately, since a menu holds a screenful of rows and is
  anchored to the window rather than floating on it. Over the film it draws no frost at all (`LocalOverFrost`) and
  renders flat.
- **One width for every menu** (248dp), where L1 sized each to its widest row: a row can then `fillMaxWidth`, so the
  whole row is the tap target rather than the text on it, and a menu stops changing width between icons.
- **Unbuilt verbs are absent, not disabled.** L1 showed "Rename" and "Edit icon" grayed out; the settings sections'
  own rule is that a control which changes nothing is worse than a missing one. So a home folder's menu is
  *Remove folder* alone, an APPS-pager folder and a category card get **no menu at all**, and rename returns with its
  op. **"Edit icon" is no longer one of them** — it is bound at the shell like the other app commands, and routed
  through `app` so `feature:shell` never learns the icon studio exists.
- **What each surface adds is only what it owns**: home's *Remove* (`RemoveFromGrid`), the home list's *Remove* (its
  own order store, not a placement), an app **inside a folder** nothing — it has no placement, so a Remove row there
  would do nothing, and taking it out is the drag this menu's long-press already leads into. `data:apps` gained the
  three commands behind it — `AppInfoOpener`, `AppShortcuts`, and the wrapper reads they need — each resolving the
  profile from `userSerial` like `AppLauncher`, where L1 hardcoded `Process.myUserHandle()` and `AppInfoOpener` uses
  `LauncherApps.startAppDetailsActivity` rather than L1's package-only settings intent for exactly that reason.
- **Uninstall needed two things L1's byte-identical intent did not**, and without either it does nothing *visibly*:
  **`REQUEST_DELETE_PACKAGES`** (an app targeting API 29+ must hold it to ask for a package to be removed — the
  uninstaller activity otherwise finishes the instant it opens, with no dialog and no exception to catch; L1 was
  covered by `QUERY_ALL_PACKAGES`), and **`Intent.EXTRA_USER`**, so a work-profile app is removed *in its profile*
  — the same per-profile correction the other three commands make. AOSP's Launcher3 sends exactly this pair.
  Diagnosing it turned up a broader gap: **nothing had ever planted a Timber tree**, so every `Timber.w` in the
  codebase wrote nowhere and this failure had no way to report itself. `LauncherApplication` now plants one on
  debuggable builds, read from `ApplicationInfo.FLAG_DEBUGGABLE` rather than `BuildConfig.DEBUG`, which only exists
  where that build feature is switched on.

**The surface menu is the same panel with a different anchor, and the anchors are a sum type.** `MenuAnchor.Item`
carries the item's bounds; `MenuAnchor.Press` carries the point a long-press landed on. That is L1's two position
providers named as one thing rather than two composables a caller had to pick between correctly, and the difference
is not decoration:
- **An item menu points at a thing, so it sits beside it and scales out of the edge nearest it.** A surface menu
  points at nothing, so it **docks flush to whichever vertical edge the press was nearer** and slides in from it,
  vertically centered on the finger — L1's `EdgeDockedPopupPositionProvider`. Planting it on the press point would
  claim a relationship with whatever patch of wallpaper it covered, and hugging the edge leaves that wallpaper
  visible beside it. `ResolvedAnchor` computes the side **once**, because the reveal needs it in composition and the
  placement needs it in measurement.
- **A surface menu has no header**, as L1's had none: there is no honest title for "the home screen" that is not a
  word taking a row.
- **`surfaceMenuGestures` is how a press reaches it, and it needs no geometry.** It goes on a surface's *root*, so
  it sees presses that land on icons too (`launcherItemGestures` never consumes a down) — answered twice: the item
  is given a **head start** (`longPressTimeoutMillis` + 120ms, so which timer wins is a fact rather than a coin
  toss), and then the gesture **asks `SurfaceGestureLock`**, which is already exactly "something owns this finger".
  L1 instead ran one root recognizer that resolved the cell and branched on `isOnIcon` — which works, but makes the
  surface responsible for knowing where every item drew its icon, the very decision this codebase hands down to the
  cell's content. Asking the lock settles a case nobody wrote down for free: an open folder holds it, so pressing a
  folder's backdrop cannot open the menu of the surface underneath.
- **One detector per surface where L1 had three** (home, dock, widget area). L1's three differed because their
  *actions* did — "Widgets" on home, "Add widget" on the widget area, nothing on the dock. Ours all resolve to the
  same row, so three would be three ways to say one thing; the split returns with the first verb that is not
  launcher-wide.
- **HOME's menu is one row — Settings — and that is the honest state rather than a stub.** Every verb L1 put above
  it waits on something unbuilt: "Add app" and "Widgets" need pickers, "Remove page" needs page management. L1's
  *Wallpaper* row is no longer blocked — see the route note below — and is now a one-line decision rather than an
  architectural one; it is left out until someone asks for it.
- **Deep-linking into settings moved `SettingsRoute` out of `core:navigation` and into `feature:settings`**, which
  is the decision that file's KDoc reserved ("whether a section becomes a route argument or its own `NavKey` is a
  decision for the port that introduces them"). Three parts: a **route argument**, because settings is one
  destination whose sections are panes and a pane is not a place on the back stack; **declared in the feature**,
  which is what lets the argument *be* a `SettingsSection` — L1 put that enum in its navigation module purely
  because its route carried one, and every module touching navigation could then import the whole taxonomy;
  and `core:navigation` keeps only `HomeRoute` and the `Navigator`, which is the shape it was always arguing for.
  **`app` is still the only layer that names a section**: the APPS surface says "open my settings" and passes the
  `AppsLayout` it is showing (`core:model`, shared by everyone), and the mapping from that to a pane happens in
  `LauncherNavHost` where both are already visible.
- **It replaced the dev gear chip**, which `app` had been carrying as admitted scaffolding "until the P7 long-press
  menu exists". Navigation reaches the shell as an `onOpenSettings` action rather than through `LocalNavigator`,
  which is `SettingsScreen`'s own `onOpenDevHarness` pattern: `app` owns the back stack, so `app` says where a verb
  goes and no feature module learns that a destination exists.
- **APPS gets one too, which reverses L1's call** ("no context menu on empty space in side surfaces") — at the
  author's direction, and the reason is a property of L2 rather than a preference. L2's touch targets are narrower
  by construction: an item's gestures cover its icon and its label, never its cell or its row, so every APPS layout
  has real free space between items and none of it did anything. Its one verb is **"Apps settings"**, which opens
  the settings for the *arrangement being looked at* — the APPS section has a chip per layout, and without this
  reaching the one you are using is a long-press on home, then a section, then a chip. One detector on
  `AppsScreen`'s root, so all five layouts get it identically and a new one cannot forget it.
- The host is `LauncherMenuHost` / `LocalMenuHost` / `MenuOverlay` — renamed from `ItemMenuHost` in the same change
  that gave it a second kind of menu, since **one host** is what keeps "both open at once" unrepresentable.

**A side slot is composed only while it is needed, and that is an ANR fix rather than a tidy-up.** `SurfacePager`
used to compose every bound slot at all times. With one binding that was invisible; with **four** it was five seconds
of dropped input on a weak device — four whole APPS surfaces, four sets of cells, all baking icons at once. Three
things caused it together and all three are fixed, which is worth knowing because only the first is about the pager:
- **The slot gate.** Composition begins the instant a swipe moves off HOME (`SurfacePagerState.engagedEdges`, which
  flips at *zero* where `openEdge` flips at a half — content has to exist before it can be seen) and ends when the pan
  settles back. `retainedEdges` is the exception the drag needs: the shell pins the edge an eject came from,
  synchronously inside `EjectToHome` — the one instant the answer is both needed and still true — and releases it when
  the drag ends. Each slot is wrapped in a `SaveableStateHolder.SaveableStateProvider`, so a drawer closed and
  reopened is on the page it was left on; without that the gate would be paid for in the thing a launcher is judged on.
  The cost that remains is real and accepted: the first frame of a swipe now composes a surface, where before it was
  already there.
- **`IconRenderManager.get` coalesces concurrent bakes.** A plain `cache.get() ?: bake()` is a thundering herd — every
  caller arriving before the first bake finishes repeats the whole load-parse-composite and allocates a bitmap the
  next `put` immediately makes garbage. That allocation *was* the `HeapTaskDaemon` at 57% in the trace. It is now
  `suspend`, and duplicate callers await the first one's result rather than holding a thread.
- **Baking is capped at half the cores, never more than three.** `Dispatchers.Default` is sized to the core count, so
  a screenful of cells launching a coroutine each will take every core and leave none for the main thread. Leaving
  cores idle is the point. `LauncherIcon` names no dispatcher any more — where baking runs belongs to the thing that
  bakes.

**The surface swipe is nested-scroll aware, and one fact answers it from both ends.** A one-finger swipe crossing a
surface boundary belongs to whatever scrolls under it until that content runs out — so `OneFingerSwipe.AT_EDGE` is now
genuinely different from `ALWAYS`, which is what `LauncherShell`, `SurfacePager` and the playground all said was
deferred. It is two types, deliberately split by what changes:
- **`ScrollAxes`** (static) — what a layout scrolls on each axis (`AxisScroll.NONE`/`BOUNDED`/`INFINITE`), which is a
  function of the **infinite-scroll setting** for a paged axis (`AxisScroll.ofPager`).
  `ScrollAxes.oneFingerSwipe(edge)` is the whole derivation of the policy, and it is the surface-pager playground's
  private `Scroll.toSwipe()` promoted to a real type once the real layouts existed to answer it. Each feature declares
  its own — `HomeLayout.scrollAxes` in `feature:home`, `AppsLayout.scrollAxes` in `feature:apps` — because the shell
  owns the *question* (it is the only layer seeing both sides of an edge) and each module owns its answer.
- **`ScrollEdges`** (live) — where that content is resting right now, four booleans keyed by `HomeEdge`. Published by
  each layout through `ReportScrollEdges` into a per-slot `ScrollEdgeSlot` that `SurfacePager` provides, and read by
  the gesture.

Five things about it are load-bearing:
- **The gesture runs on `PointerEventPass.Initial`, and nothing above works on any other pass.** `positionChange()`
  returns `Offset.Zero` once a change is consumed, so a parent on the Main pass — which sees its children's events
  *after* them — accumulates zeros over any scrolling content, never crosses slop, and never reaches the claim block
  at all. That is exactly how this landed the first time: every piece was wired and none of it ran. Initial gives the
  pan first refusal on the raw delta and it **consumes only if it claims**, so declining costs the child nothing.
  L1's `CrossPager` used Initial for the same reason, and taking the default pass was the one part of it not ported.
  Two consequences fall out, both of which were bugs until they were answered:
  - **A claim must never be idle.** A swipe pressed against a bound the pan is already clamped at moves nothing while
    consuming the finger, which on Initial eats every forward page-swipe and downward scroll the open surface's own
    content needs. So closing is gated on the finger genuinely traveling toward the edge the surface came in from.
  - **`launcherItemGestures` reads the finger with `positionChangedIgnoreConsumed()`**, the twin of the
    `changedToUpIgnoreConsumed` beside it. The pan claims at the platform slop (~8dp) and an item needs 20, so with
    the consumption-sensitive read a swipe begun on an icon went `Down` → `Up` with no `Move`, stayed in `Pressed`,
    and **launched the app**. Where the finger is is never in dispute; whether the item acts is the machine's
    decision, and `ReleasedToParent` is the phase that says so.
- **Opening and closing are the same question from opposite ends.** Opening edge E asks HOME's content about the edge
  facing E; closing asks *that surface's* content about `E.opposite`, because closing drags it back the way it came.
  One expression each, so they cannot drift. **L1 had two differently-shaped types for this** — `SurfaceScrollEdges`
  for side surfaces and `HomeGestureRelease` (`swipeRightOpensLeft`/`swipeUp`/…) for HOME, which conflated the live
  edge with the policy and could not express a scrolled home list at all: its list home was a flat `swipeUp = false`,
  forbidding the crossing rather than handing it off at the end of the list.
- **The slot holds a lambda, not a value, and the gesture invokes it from a pointer callback.** The question is asked
  once per gesture, so reading the scroll states *there* is free and never stale — where publishing a value means
  reading `canScrollForward` in composition, and for a `ScrollState` that is a raw read of `value`, i.e. a
  recomposition of the whole surface per scroll frame. That is the trap `CategoryPage`'s geometry comment already
  warns about on the same surface, and L1 fell into it (`PlainGridLayout`, `IosCategoryGridLayout`).
- **One report per surface, not one per scroller** — a second call in the same slot replaces the first rather than
  combining with it. `AppsCategoryPager` is the case: it owns both axes, so it builds one value from its pager *and*
  the current page's scroller, which `CategoryPage` hands up beside its geometry (only the surface knows which page is
  current, and asking in composition would subscribe the layout to the pager's animated position).
- **The decision is made once, at slop, and stands for the whole gesture.** Scrolling a list to the bottom and
  carrying straight on does not start panning; a second swipe from the bottom does. L1 behaves the same way. The
  honest limit of a hand-off decided by a claim rather than by leftover deltas — continuing would mean real
  `nestedScroll` connections on every surface. Two fingers skip the question entirely.
  - **One qualification: a `SurfaceGestureLock` claim at slop postpones the decision rather than ending it.** The loop
    keeps accumulating and decides on the first event at which nothing is claiming. That exists for
    `EmbeddedViewTouchFrame` (see the widget notes) — an embedded Android View cannot be *asked* whether it wants a
    gesture, so it claims pre-emptively and releases when it declines. Nothing else notices, because every other
    claimant holds its claim until its own gesture is over.

**Infinite paging is a setting again, and it is per pager.** `SurfacePaging` is the fourth slice — one sparse
`Map<GridSlot, Boolean>`, read resolved through `SettingsRepository.pagerWraps`. Its own slice rather than a field in
`SurfaceMetrics` because every map in that one is a *size* keyed `slot × device`, and wrapping is a behavior with no
device dimension: turning the phone on its side is not a reason for the pages to stop looping. The default lives on
`GridBlueprint.wraps`, null meaning "not a pager whose wrapping is the user's" — the same convention `extentDp` and
`rowHeightDp` use, and what lets `setPagerWrap` refuse a slot rather than every caller checking. Exactly three grids
declare one: `HOME_MAIN`, `APPS_PAGER`, `APPS_CATEGORY`. A folder pages too and is deliberately not askable — its
pages are a handful of apps in a card, bounded by construction.
- **Per pager, where L1 had one global flag.** L1's `pager.infiniteScroll` was one boolean read by home's pager *and*
  both drawer pagers, with its only control in the **Home** screen — so turning it on to make the home pages loop
  silently changed the app drawer, and a user configuring the drawer had no control to find. Here each pager owns its
  answer, so the toggle can live in the section that configures that surface: Home's in the Home section (gated on the
  pager pairing, as L1's was), and APPS' in the APPS section, where **it follows the chip** — selecting the category
  pager selects that grid's setting.
- **It defaults to off, where L1's defaulted on**, and that is the reversal the swipe rules force. A wrapping pager
  never reaches an end, so there is no edge to hand off at: `AxisScroll.INFINITE` → `OneFingerSwipe.NEVER`. On by
  default would mean a horizontal edge binding could only be opened with **two fingers** out of the box, with nothing
  on screen to explain it. Both switches say so in their subtitle, because a control that takes a gesture away has to.
- **`HomeLayout.pagerSlot`/`AppsLayout.pagerSlot` are what keep every consumer from re-deciding which grid is meant**
  — `core:model`, because `feature:settings` needs the APPS answer and does not depend on `feature:apps`. Null on the
  layouts that do not page, which is also what both settings states carry (`wraps: Boolean?`) so a pane draws the
  control from its state rather than testing the layout a second time.
- The one genuinely awkward consequence: `HomeMainSizing.Pager` now carries `wraps`, so a type named for sizing holds
  a behavior. Kept there because it is a setting only a *pager* can have — a `List` given one would be meaningless —
  and a nullable field beside the state would make exactly that expressible, which is what the sum type exists to
  prevent.

Two things L1 did here are **not** carried. Its reporters blanked every field to false while an item drag was in
flight, so the pan could not claim mid-drag; L2 answers that one layer up with `SurfaceGestureLock`, which gates the
whole gesture instead of lying about where the content is. And L1 hoisted four `mutableStateOf`s plus four reporter
lambdas into its home screen; the pager composes the slots, so the pager owns them and the shell wires nothing.
The report is **one answer for a whole surface**, so a vertical swipe starting inside the dock or the widget area is
treated as if it were over the main area — L1's own simplification, and refining it would mean a per-region answer to
a question decided once, at slop.

**Known gaps, deliberate:** the item context menu offers **nothing for a folder on the APPS pager** and nothing for
a category card — rename and dissolve have no ops on the APPS order store, and category management is a
`feature:settings` concern, so those long-presses show no menu at all rather than a menu of disabled rows. The
**rename** is the one verb the menu is still owed on home, the icon studio's "Edit icon" having landed. A drag ejected from APPS draws
**no floating proxy** for the frames the surface takes to close: the drawer stops painting one the moment it is no longer presented
and home starts once it is, and neither owns the gap in between (the drop itself is unaffected). The effects section governs **the film and the two real panels**: the context
menu and the bottom sheets took the film's material, so the variant and the wash decide every menu, sheet, the APPS
surface and an open collection alike, while the blur, wash-amount and lens sliders reach the **container tile** and
the **icon plate**. Liquid glass's rim is the tile's alone — the one surface floating on the wallpaper with four
edges genuinely its own.
No item is reachable by an accessibility service — `launcherItemGestures` is raw
`pointerInput` with no `semantics { onClick { … } }` (P7 gestures). **detekt runs on `check`** over a per-module
baseline of the 316 findings that existed when it was added — new code is checked, the old backlog is a list rather
than a wall — and **ktlint runs beside it** over a deliberately narrow ruleset (unused imports, import order,
trailing whitespace, final newline, file naming), its standard set switched off because ktlint's house style is not
this one. Wrapping and indentation remain unenforced, on purpose. The Gradle **wrapper is missing** from the repo
(`gradle/wrapper/gradle-wrapper.properties` is tracked but `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` are not),
so there is no CLI build from a fresh clone — **but a Gradle 9.6.1 distribution is already cached**, so
`~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle :app:assembleDebug` builds and tests from the
CLI today. `feature:settings` and `feature:apps` declare junit but have **no tests**.


## Wallpaper (`data:wallpaper`)

*Moved here from the Design system section on 2026-08-20 — it is module status, not a design-system rule.*

**Wallpaper — `data:wallpaper` (B7b) exists, with the static image in it, and a section that drives it.** Its own module rather than a slice of
`data:settings`, because it decodes bitmaps, writes files and calls `WallpaperManager` — a *service*, where settings is a
store. **It keeps its own one-key DataStore too**, which is a correction to the plan's original line ("borrowing settings
to persist path pointers"): a path to a file we wrote and the id the system gave the wallpaper we set is bookkeeping, and
S0 had already refused that on the way in. The **effect params** (`BackdropEffect`) are the genuinely preference-shaped
half and stay in `data:settings`. State is **four fields where L1 had six**, and two of the four are the
ones L1 had for the frosted backdrop: a **snapshot copy** of the image as applied to home (`appliedHome`) and a latch
saying whether the current pick is that image (`imageApplied`). `appliedSystemId` is an id rather than a boolean because
it also detects a wallpaper set outside the launcher. Built: **all three sources** — pick from a `Uri`
and frame it on the crop screen, **capture** a screenshot of the wallpaper itself, or set the **rotating pair**, one
image per orientation — each sample-decoded, scaled to the screen and stored through one write path, plus apply to
HOME / LOCK / BOTH. `WallpaperSource` is what separates them: a capture is a
picture *of* the wallpaper, so `apply` declines it and it exists only for the effects to sample (it is the one way to
read a **live** wallpaper). Capture landed before its consumer on purpose — an effect has to answer "which image do I
sample?", and answering that once against every source beats re-answering it per source. Nothing invents a crop any more — `setImage` takes a
`NormalizedCropRect` and the screen passes the region the user framed, against the viewport it also passes as the size
to store at, so the rectangle and the result share one coordinate space. **The reading half is `brightness`,
`accentColor` and `backdrop`** — the three questions anything drawing over the wallpaper has: how bright is it, what
color is it, and what does it look like blurred. All three share one change signal and one "is our file what is on
screen?" gate, deliberately, because three answers that could disagree about *which image* they read would be worse
than any one of them being slightly off. Each also asks the system before it decodes anything: `getWallpaperColors`
answers the first two over the wallpaper *actually displayed*, and `Blur.kt`'s `dominantColor` is only the API-26
fallback for the second. One L1 bug not carried: its repository read-modified-wrote its state *outside* any
transaction, so picking an image while an apply was finishing could lose one of them.
**`backdrop` answers "which image does an effect sample?" once, for all three sources** — the question the whole
sources-before-effects ordering was arranged around. Our rotating service active → that orientation's half; a
**capture** → always (it *is* a picture of what is displayed, and gating it on being applied would reject it forever);
a picked image → only if `appliedSystemId` still matches the live wallpaper id; otherwise nothing, and every frosted
surface falls back to its scrim. **That third test needs both halves, and this file claimed for a while that it needed only
one.** L1 froze a snapshot of the applied image (`appliedSingle`) so an edited-but-unapplied pick could not
desynchronize the backdrop, and the id comparison was taken to replace it. It does not: the id answers *"is the
wallpaper on the system still the one we set?"* — which the snapshot could not, since a wallpaper changed outside the
launcher left L1's copy claiming to match — but it says nothing about whether **we still have that picture**, and
`WallpaperFiles.IMAGE` is one fixed name that every pick overwrites. So `appliedHome` is a real copy, taken after a
successful apply that included `FLAG_SYSTEM`. Three things went wrong without it, and the third is what surfaced it:
picking an image killed the backdrop until it was applied; `setImage` built a **whole new `WallpaperState`**, so a pick
also erased the rotating pair's references and left its files orphaned; and "pick, then apply to the *lock* screen" —
which cannot change the home screen at all — erased the frost outright. **Which is also why the pick's own
applied-ness is its own field**: `appliedSystemId` stays true across a new pick, so it must not be reset by one, and the
section's "Apply" / "Re-apply" reads `imageApplied` instead (L1's `singleDirty`, inverted). It is the **same gate**
`brightness` uses, deliberately: "is our file what is on screen" is one question, and two answers to it would drift.
**Two traps in that gate, both fixed and both silent while they were live.** The id is only recorded when an apply
actually included `FLAG_SYSTEM` — recording it unconditionally meant a *lock-only* apply wrote down the id of a home
wallpaper it had not touched, so the gate compared that id against itself and answered true on no evidence, leaving the
frost blurring an image that was not on screen and the brightness fallback theming against it (L1 reads the same id
regardless of its own `which`). And the backdrop keys on the file's **identity** — path plus `lastModified` plus
`length` — not on its name: the stored images sit at fixed paths and are overwritten, so a capture over a capture, or a
rotating half replaced while that pair is live, changed the picture and nothing else. That one needed the internal state
signal to stop de-duplicating too (`storedState` vs the public `wallpaper`), since a repeated capture produces a
byte-identical `WallpaperState` — de-duping a *value* is right for a consumer that renders it and wrong for one whose
real subject is a file the value only names.
It is also a **flow** where L1's was a one-shot read, because two of those four answers change with no action from us.
**The rotating pair is a *live* wallpaper, and that shapes three things.** Android has no per-orientation static
wallpaper — `setBitmap` takes one image and the system crops it — so drawing a different picture in landscape means being
the renderer: `RotatingWallpaperService` lives in `data:wallpaper` beside the files it reads, where L1 put it in its
settings feature and left its data layer unable to name its own service. It cannot be applied silently either — the
platform insists the user confirm in its own preview — so the section opens that chooser and then **asks on resume**
whether ours ended up active; `WallpaperManager.wallpaperInfo` is the answer, so nothing is latched and there is no
reconciler, where L1 stored `appliedMode` and needed `reconcileLiveWallpaper` to repair it. And the crop screen frames
the landscape half **letterboxed** rather than pinning the activity's orientation as L1 did, which it can afford because
the frame decides the *shape* while the target screen decides the *resolution*. And the service **publishes its
colors**, which L1's did not — see the design-system note above; it is what lets the rotating pair answer the
brightness question through the same system API as every other wallpaper.
