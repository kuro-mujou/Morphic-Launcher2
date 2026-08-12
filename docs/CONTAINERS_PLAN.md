# Containers port plan — icon container + widget container

Both containers are **standalone items on the HOME grid** whose *contents* are ordered within them. The entities,
the DAOs, the `LayoutChange` vocabulary and `LayoutRepositoryImpl.apply` all already exist; what does not exist is
a cell that can draw either, a way to create one, or a way to put anything in one.

This plan is written to be implemented from. Read **§1 (defects)** before writing any UI — four of them are in
the write path, and three are silent when wrong.

---

## 0. What exists today

| Layer | State |
|---|---|
| `core:model` — `IconContainer`, `WidgetContainer`, `IconArrangement`, `WidgetContainerAxis`, `IconItem`, `GridItem.{IconContainer,WidgetContainer}` | done |
| `core:database` — 6 entities + 6 DAOs, cascades correct | done |
| `data:layout` — 7 container ops in `LayoutChange`, all handled in `apply`; `iconContainers()` / `widgetContainers()` flows; `IconContainerMappers` | **done, with the defects in §1** |
| `feature:home` — `HomeItem` arms, cells, creation, drag | **nothing** |
| Picker — L1's "Components" section | deliberately absent ("no cell that can draw either") |

`HomeViewModel`'s state assembly has the marker: `else -> null // containers get their cells later`.

---

## 1. Data-layer defects to fix first

These are all in `LayoutRepositoryImpl`. **Fix them as their own commit, before any UI** — otherwise the UI slices
will be debugged against a store that silently drops writes.

### 1a. `AddToIconContainer` does not detach, and the write is silently dropped

`icon_container_item` has `@PrimaryKey(autoGenerate = true) val id` plus **unique** indices on `component` and
`folderId`. Room's `@Upsert` inserts, catches the constraint failure, then updates **by primary key** — which is the
synthetic `id`, `0` for a new row, so it matches nothing. The write is dropped with no error and no row changed.

This is the *exact* mechanism `LayoutRepositoryImpl`'s own `detachAll` KDoc already documents for `folder_item`.
Moving an app from one icon container to another therefore does nothing at all.

Fix: call `daos.iconContainerItem.removeByComponent(…)` / `removeByFolder(…)` **before** the upsert, mirroring
`AddToFolder`'s `folderItem.detachAll`.

### 1b. `AddToIconContainer` leaves the item on the grid (and in its folder)

`AddToFolder` deletes the app's grid placement, because *an app lives in exactly one place*. The container ops do
not, so an app dragged into a container renders **twice** — in the container and in its old cell.

Fix, per `IconItem`:
- `IconItem.App` → `appPlacement.deleteByComponent`, `folderItem.detachAll(listOf(component))`,
  `iconContainerItem.removeByComponent`.
- `IconItem.Folder` → `folderPlacement.deleteByFolderId`, `iconContainerItem.removeByFolder`.

L1's `CreateIconContainer` does exactly these five calls; ours does none of them. Worth extracting one
`private suspend fun detachIconItem(item: IconItem)` so `CreateIconContainer` and `AddToIconContainer` cannot
disagree — the same shape as `detachAll`.

### 1c. `AddToWidgetContainer` has both bugs, on `appWidgetId`

`widget_container_item` PK is `(containerId, appWidgetId)` with a unique index on `appWidgetId` alone. Inserting
`(B, W)` for a widget already in container A conflicts on the index, then updates by PK `(B, W)` — no match, write
dropped. And the widget keeps its grid placement.

Fix: `widgetContainerItem.removeByWidget(appWidgetId)` then `widgetPlacement.deleteByWidget(…)` before the upsert.
Check the placement DAO actually has a delete-by-widget; add it if not.

### 1d. Two KDoc claims that are false

- `RemoveFromWidgetContainer` says "the widget is unbound". It only removes the membership row. Unbinding is
  `data:widgets`' job — `PlaceWidget`'s own KDoc says so ("this store only keeps records"). Correct the sentence.
- `WidgetContainer` says widgets are "**stacked** along `axis`". L1 *pages* them (`ContainerPager`) — see §2a.

### 1e. Orphaned widget rows when a container is deleted

`RemoveFromGrid(GridItem.WidgetContainer(id))` deletes the container row; membership and placement cascade. The
contained widgets' **`widget` definition rows do not**, and neither do their allocated `appWidgetId`s. Removing a
widget container must therefore do what `HomeViewModel.removeWidget` does, once per contained widget: delete the
definition and call `widgetHost.deleteId`. See §3c.

---

## 2. Design decisions

### 2a. The widget container is a **pager**, not a stack

L1's `WidgetContainerCell` swipes between widgets along `axis` and draws page dots on the trailing edge
(`Alignment.CenterEnd` for vertical, `BottomCenter` for horizontal). Adopt that: `axis` means **which way you
swipe**, not which way they stack. Stacking two widgets in one 2×2-cell footprint halves each, which is the
opposite of why a user groups them.

Correct `WidgetContainer`'s KDoc accordingly.

### 2b. `IconArrangement` gets one exhaustive function, not L1's interface + registry

L1 has an `Arrangement` interface, seven objects, and an `arrangementFor(type)` mapping — one implementation per
enum value, nothing external ever implements it. That is the wrong-interface-abstraction smell `GridBlueprint`
already had removed.

L2: one pure top-level `fun IconArrangement.slots(count: Int, width: Float, height: Float): List<ArrangementSlot>`
with a `when` over the enum, so a new arrangement **fails to compile** until it is laid out, and there is no
registry to keep in step. The maths itself ports verbatim from L1's `Arrangement.kt` (165 lines: grid, circle, four
fans, beehive) and is worth unit tests — it is pure, and this codebase tests its pure geometry (`CellFit`,
`MenuAnchoring`, `FolderHostState`).

Port all seven even though nothing chooses between them yet: the `when` must be exhaustive anyway, and the chooser
is one menu row (§3d). The alternative — `GRID` plus six `TODO()`s — makes the enum a landmine.

Home: `core:designsystem/container/IconArrangements.kt`, beside the other pure layout maths.

### 2c. A container slot draws the **icon alone**, never an `AppCell`

Same rule, same reason, as the APPS category card: `AppCell` wraps `IconLabelCell`, which insets by
`CellPadH`/`CellPadV` and reserves a label row. Seven of those inside one container cell would be seven smudges
with unreadable labels, and the slot spacing the arrangement computed would be swallowed by per-cell padding.

So: `IconItem.App` → `LauncherIcon` sized to the slot; `IconItem.Folder` → `IconPreviewPlate(backing = false)`.
`backing = false` for the cluster's reason — the container already has a fill, so a plate inside it is a box within
a box.

L1 used `AppCell`/`FolderCell` here. Don't copy that.

### 2d. Two tap targets per container cell, like a card

- Each **slot** launches its app / opens its folder (`Modifier.clickable` on the slot).
- The container's own `itemGestures` go on the **whole cell** — a container fills its cell, like a widget, so this
  is `LauncherDragCell`'s stated exception rather than the icon+label rule.
- The container's `onOpen` is a **no-op**: there is no expanded view. A tap on a slot launches; a tap on the gaps
  does nothing.

This composes without extra work, and the reason is worth knowing: `clickable` consumes the down on the Main pass,
but `launcherItemGestures` takes `awaitFirstDown(requireUnconsumed = false)` and reads movement with
`positionChangedIgnoreConsumed()`, so a long-press *on a slot* still reaches the container's menu and drag.

### 2e. The empty state: an affordance, not a button

An empty container must draw *something* — an empty cell that cannot be removed reads as a rendering fault, which
is the same argument `WidgetCell` makes for naming an unresolvable widget.

- **Widget container**: a real "+" button, opening the existing `rememberWidgetAddFlow`. This one works today.
- **Icon container**: a "+" **glyph on the plate, not a tap target** — inert, saying "drag an app here". Its
  spec'd behavior is to open an *app picker*, and there is no app picker in the launcher (see §5). A row that
  does nothing is worse than a missing one, so it must not be tappable until the picker exists.

### 2f. The widget container's swipe will be eaten, and needs the lock

A container's pager is a **Compose** pager deep inside the surface. `surfacePagerGesture` runs on
`PointerEventPass.Initial`, so it claims at slop and consumes before any descendant scrollable sees the movement —
the same structural problem the widget cell just had, but `EmbeddedViewTouchFrame` cannot help, because a Compose
pager is not an embedded View and cannot call `requestDisallowInterceptTouchEvent`.

Home's *own* pager escapes this only because it publishes `ScrollEdges` and the edge policy is `AT_EDGE` — and
`ReportScrollEdges` is deliberately **one answer per surface**, so a per-container report is not expressible.

Decision: **hold `SurfaceGestureLock` while a finger is down inside a widget container.** A small modifier next to
`LockSurfaceGesture` — `awaitFirstDown(requireUnconsumed = false)`, hold, release on up; it consumes nothing.
Consequence: a surface pan cannot *start* on a widget container.

That is the design I rejected for a plain widget (it would make every widget a dead zone), and it is right here for
a reason that is a property of the thing: a widget container exists *to be swiped between pages*, so its whole area
is a gesture target, where a plain widget usually is not.

### 2g. A new container is 2×2 visual cells

L1: `span = 2 * gridConfig.cellMultiplier`, i.e. 2×2 **visual** cells (four times an app's footprint), found with
`GridOccupancy.findFreeRect`. Keep it. One visual cell cannot hold a legible group.

### 2h. Creation is not optimistic

`Create*Container` inserts with an autogenerated id, so there is nothing to mirror until the store answers —
exactly as `CreateFolder` already behaves (`withApplied` falls through to `else -> Unit`). Leave it; do **not**
invent a provisional id.

---

## 3. Slices

Each is meant to be device-testable on its own and small enough to read in one sitting.

### 3a. Slice 0 — the write path (no UI)

`LayoutRepositoryImpl` only: §1a–1d. Add `detachIconItem` / the widget detach, correct the two KDocs.
Unit-testable if `data:layout` grows a Room test; otherwise it is verified by slice 2.

### 3b. Slice 1 — both containers render and can be created

1. `core:designsystem/container/IconArrangements.kt` — `ArrangementSlot` + `IconArrangement.slots(…)` (§2b),
   plus `IconArrangementsTest`.
2. `HomeState.kt` — `HomeItem.IconContainer(container, icons: List<ContainerIcon>, …)` and
   `HomeItem.WidgetContainer(container, widgets: List<WidgetInfo>, …)`; a `ContainerIcon` sum type
   (`App(AppInfo)` / `Folder(FolderModel, List<AppInfo>)`) mirroring how `HomeItem.Folder` carries resolved apps.
   Extend `HomeState.appInfo` to look inside icon containers — a drag out of one must resolve to something
   drawable, the same reason folders are searched there.
3. `HomeViewModel.kt` — `HomeDefinitions` grows from 2 fields to 4 (`iconContainers()`, `widgetContainers()`);
   resolve the two `GridItem` arms and delete the `else -> null`. Add `createIconContainer(zone, config)` /
   `createWidgetContainer(zone, config)`, both `findFreeRect(span 2×2 visual)` → `Create*Container`, returning
   `false` when nothing fits (`placeWidget`'s contract).
4. `feature:home/IconContainerCell.kt`, `WidgetContainerCell.kt` (§2c–2f).
5. `HomePagerSurface.kt` — `HomeItemCell` arms, `openItem` arms (both no-ops), `showMenu` arms, and pass the two
   creation lambdas to the picker.
6. `WidgetPickerSheet.kt` — L1's "Components" section: two rows, `Icons.Filled.GridView` / `Icons.Filled.Widgets`,
   L1's own labels ("Icon container" / "A panel that holds app and folder icons."; "Widget container" — reword the
   description, since ours pages rather than stacks).

Menus (the honest set for this slice): icon container → *Remove container*; widget container → *Add widget*,
*Remove container*. "Add widget" is what makes paging reachable and testable before any drag work exists, and it
costs nothing — it reuses the add flow the surface already holds.

**Not in this slice**: dragging anything into a container, extraction, arrangement/axis choice.

### 3c. Slice 2 — filling and emptying by drag

1. `HomeDropPlanning.canMerge` — allow `App`/`Folder` onto `GridItem.IconContainer`, and `Widget` onto
   `GridItem.Widget` and onto `GridItem.WidgetContainer`. Its KDoc already predicts this ("container merges arrive
   with those item types").
2. `HomeViewModel.mergeChanges` — the new target arms:
   - app/folder onto an icon container → `AddToIconContainer`;
   - widget onto a widget → `CreateWidgetContainer(axis = HORIZONTAL, widgetIds = [target, dragged], at = target's
     placement)`, which is `CreateFolder`'s shape exactly (the pair takes the target's cell);
   - widget onto a widget container → `AddToWidgetContainer`.
3. `removeWidgetContainer(id)` — §1e: for each contained widget, `RemoveFromGrid(GridItem.Widget)` plus
   `widgetHost.deleteId`, then `RemoveFromGrid(GridItem.WidgetContainer)`. One method, so no caller has to know.
4. **Extraction**: dragging an item *out* of a container. `RemoveFrom*Container` changes membership only, so a
   landing pairs it with a `Move` — the APPS pager's composition rule, already stated in CLAUDE.md. Auto-dissolve
   at the last item, mirroring a folder's (last item inherits the container's cell).

Extraction is the largest single piece here and may want to be its own slice. Note that a container is **not** a
`FolderHostState` consumer: nothing opens, so there is no open/leave/enter lifecycle — the drag starts on a slot
that is already visible.

### 3d. Slice 3 — arrangement and axis

A chooser for `IconArrangement` (7 values) and `WidgetContainerAxis` (2). Two options, and it is a real decision:
- a **submenu on the item menu** — reachable where the object is, no navigation; but seven rows is a long menu; or
- a **settings section** — consistent with how every other surface is configured, but a container is an *instance*,
  not a surface, and the settings tree has no vocabulary for "this one container".

Leaning to the item menu, with the seven arrangements as a second stage (the menu already has a two-stage shape for
shortcuts). `SetIconContainerArrangement` exists; the axis has **no op** — `WidgetContainer.axis` can only be set
at creation today, so slice 3 adds `SetWidgetContainerAxis` (and a `WidgetContainerDao.setAxis`, mirroring
`IconContainerDao.setArrangement`).

---

## 4. Things to check while implementing

- `WidgetPlacementDao` — confirm a delete-by-`appWidgetId` exists for §1c; add it if not.
- `LauncherPager` is **horizontal only** (it measures `pageWidth` from `constraints.maxWidth` and drives
  `state.pageSize` from it). A vertical container pager needs foundation's `VerticalPager`, which is also what the
  widget picker's detail pane already uses. Do not try to generalize `LauncherPager` for this — it carries the
  launcher's edge-flip and drag machinery, none of which a container's pages want.
- A **folder inside an icon container** has no grid placement, so home's folder-open path must still find it. Check
  `folderHost.open` and the `presentedFolder` lookup, which today match on placement + zone.
- The widget area (`LIST_WITH_WIDGET_AREA`) declares `accepts` widgets only. A **widget container** should be
  accepted there too; an **icon container** should not.
- `SurfaceMetrics` has no per-container sizing and does not need any — a container's footprint is its grid
  placement, and its inner layout is derived from the cell.

## 5. Blocked on something else

- The icon container's **"+"** needs an app picker (§2e). That picker has three waiting consumers — this, the home
  list's "Add apps", and the home menu's "Add app" — so it is worth building once as its own feature rather than
  inline here.
- **Resize** already exists for widgets and applies unchanged to containers (a container's footprint is a
  placement like any other), but its menu row is currently built from `widgetHost.boundWidget(...)?.resize`, which
  a container has no answer for. A container's resize bounds are its own (at least 2×2 visual, say), not a
  provider's.
