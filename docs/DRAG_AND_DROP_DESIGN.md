# Morphic Launcher 2 — Drag-and-Drop & Layout Design

**Scope:** the core layout system shared by every surface — the drag-and-drop pipeline, the
per-surface drop behaviors, and the placement engine that resolves them.

> Domain concepts (surface taxonomy, arrangement persistence) live in [CLAUDE.md](../CLAUDE.md);
> phase/build order lives in [REWRITE_PLAN.md](REWRITE_PLAN.md). This file is the source of truth for
> **how drag-and-drop works** — read it before touching `data:layout` (B8) or `core:designsystem/drag` (B4).

Reference: L1 at `../Morphic-Launcher`. Its **placement engine** (`data/layout`: `SpreadPush`,
`GridReflow`, `GridEdit`) is good and worth porting; its **UI/gesture layer** is the mess this design
replaces. Patterns borrowed from the Reorderable lib (`D:\Android\Reorderable`) are noted inline.

---

## 1. Diagnosis: why L1's drag layer is a mess

L1's rot is **structural**, not code-quality: the drag pipeline is **not rooted at a common ancestor**,
so each surface owns its own recognizer. Every smell follows from that:

- **4 parallel gesture recognizers** (home, folder, drawer-extract, a root re-tracking loop) each
  re-implementing long-press/slop/dwell with **inconsistent constants** (350ms vs 500ms long-press).
- The `HomeDragBridge` handoff hack exists **only because** when `CrossPager` collapses to reveal home,
  neither subtree keeps receiving pointer events.
- `SpreadPush.push` runs at **3 independent call sites** (2 commit paths + the render layer re-running the
  engine just to color the footprint) — so the preview can lie about the outcome.
- **3 grid-geometry implementations**, hit-testing with hardcoded `ICON_SIZE_DP=48` instead of the real
  `IconMetrics` — the tappable region drifts from the drawn icon.
- Untyped sentinels (`origin == null` = "from a folder"), dual-field `DropResult` (`target` + `dockTarget`),
  and DOCK + widget-area forced through one `SurfaceId.DOCK` with ~20 `isVerticalListHome` branches.

---

## 2. The one big idea

> **One drag coordinator, rooted above every surface, owns the whole drag. Surfaces do not own drags —
> they register as drop zones in one shared (root/window) coordinate space.**

This generalizes the Reorderable pattern (one engine over an adapter interface, swapped per backend) from
"list vs grid" to **container vs container**: home-main, dock, each side surface, and an open folder each
supply a geometry adapter + accept rules to **one** coordinator. Rooting the drag dissolves every smell in §1
structurally — one recognizer, one geometry source (measured bounds, no drift), one computed result shared by
preview and commit, proper sealed types, first-class N zones.

---

## 3. Layered architecture & module homes

```
feature:home / feature:shell — composition & wiring
  • hosts the DragCoordinator at the ROOT (above CrossPager)
  • implements the PlacementPlanner port → delegates to the engine
        │                                   │
core:designsystem/drag (B4)          data:layout (B8)
  RENDER + GESTURE                     PURE ENGINE
  • DragCoordinator / SurfaceDragState • GridOccupancy
  • DropZone registry                  • PlacementResolver
  • one gesture pipeline + timing cfg  • GridReflow / edge-edit
  • FloatingDragIcon, DropFootprint    • LayoutChange command set
  • PlacementPlanner PORT  ────────────▶ implemented here
    (interface — NO data dep)          • LayoutRepository
        └──────────── both depend on core:model ───────────┘
              (GridPlacement, GridConfig, HomeZone, GridItem)
```

**Critical dependency inversion.** `core:designsystem` must not depend on `data:layout` (core cannot depend
on data). So the drag UI defines a `PlacementPlanner` **port** — "given this drag hovering at this target,
return the `PlacementPlan`." `data:layout` implements it; `feature:home` wires them. This keeps the drag UI
reusable and unit-testable with a fake planner, and **forces preview and commit to share one code path** —
the coordinator only ever asks the port, never re-derives placement. This permanently kills the
"SpreadPush in 3 places" bug.

---

## 4. Coordinate space & the DropZone registry

All hit-testing happens in **root/window space**. Each drop-participating region registers a `DropZone`; items
report their **measured** bounds (via `onGloballyPositioned`), so hit-test geometry can never drift from render
geometry (kills L1's hardcoded-`ICON_SIZE_DP` smell).

```kotlin
data class DropZone(
    val id: ZoneId,
    val boundsInRoot: Rect,
    val z: Int,                       // topmost-first hit-test; folder overlay sits above home
    val geometry: ZoneGeometry,       // window <-> cell/index, from real GridConfig + IconMetrics
    val behavior: DropBehavior,       // §6 — partition + reflow, travels with the zone
    val accepts: (GridItem) -> Boolean,
)
```

On each move the coordinator hit-tests the finger against all zones (highest `z` whose bounds contain the point
and whose `accepts` passes), then asks the planner for a `PlacementPlan` in that zone. **No zone owns the drag.**

**Folder-out falls out for free:** the open folder is just another zone at higher `z`. Inside its bounds → folder
zone wins (reorder). Move outside its bounds → hit-test falls through to the home/dock zones still registered
underneath. "Drag from folder to the surface below" is not a special case — it is one coordinator hit-testing one
set of zones.

### 4a. A folder is a place a drag passes *through* (built, `FolderHostState` + `FolderOverlay`)

The zone model above makes the *mechanics* free; the **interaction** rule that turned out to matter is that entering
and leaving a folder are symmetric, repeatable, and **write nothing**:

- **Enter** — hold a dragged app on any folder's merge ring (`OPEN_FOLDER_DWELL_MS`) → that folder opens mid-drag and
  the same session carries on inside it, so the app lands at a *chosen* slot rather than being appended.
- **Leave** — hold outside the open card (`LeaveDwellMs`, deliberately the **same** duration) → the folder **closes**
  and the same session carries on over the grids beneath.
- Either may happen any number of times in one drag, in any order, **including re-entering a folder already visited**.
  Membership is decided only at the drop, so there is nothing to undo on the way.

Two rules make that survivable, and both were learned by breaking them:

1. **Nothing may be latched for the duration of a *drag* when it belongs to a *visit*.** The first cut kept the folder
   "open" but invisible while the drag was outside it, and latched an `extracting` flag until release — which made
   every folder single-use: it could never be re-opened, and re-presenting it rendered nothing and registered no zone.
2. **The zone id is shared, so only the *presenting* overlay may register or unregister it.** A folder kept composed
   purely to hold a pointer stream (see §5) must not touch the registry — an unguarded `onDispose` pulls the zone out
   from under whichever folder is actually on screen.

What *does* span the whole drag is the folder it **started in** (`FolderHostState.dragSourceFolderId`, fixed on lift):
that overlay stays composed to keep the pointer stream alive (§5), and it is the membership owed a removal wherever
the app lands. Captured on lift rather than on the first hand-off, so an app carried *in* from a grid and back out
owes nobody and its folder stays freely re-openable.

---

## 5. Gesture pipeline (root-owned)

One pointer pipeline in the coordinator, applied uniformly to every item on every surface (guaranteeing identical
behavior — the thing L1's 4 recognizers could never guarantee). One timing config, one slop, one state machine.
Items are pure render + registration; they do **not** attach their own drag recognizers.

**Where the gesture applies: the item's visible extent, never its cell.** A grid cell is a *layout* footprint and
is usually much bigger than what is drawn in it (a home cell is a 2×2 visual slot around one icon and a one-line
label). The gesture goes on the **icon + label group**, not the cell, for a UX reason: on a full page of icons,
if the slack around each icon lifted or launched it, there would be nowhere left to press-and-hold for the
*surface's* own menu (wallpaper / home options). So `LauncherDragCell` hands the gesture modifier down to its
content and the content decides its own touch target — an icon cell narrows it to the group (`IconLabelCell`),
while content that genuinely fills its cell (a widget, a harness tile) applies it to its root. This works
because `launcherItemGestures` **never consumes a down**: whatever the item leaves uncovered falls through to the
surface beneath it.

```
        down on item
             │
        ┌────▼─────┐   move > slop (4-dir)   ┌────────────┐
        │ Pressed  ├────────────────────────▶│ EdgeSwipe  │→ custom action (toast for now)
        └────┬─────┘                         └────────────┘
             │ long-press timeout, no move
        ┌────▼───────────┐
        │ MenuShown      │  (context menu opens; tap now suppressed)
        └────┬───────┬───┘
   move>slop │       │ lift, no move
        ┌────▼────┐  └──▶ dismiss menu, do NOT fire tap
        │ Dragging│         ← item's own pointer stream tracks the finger
        └────┬────┘           (source surface kept composed for the drag; see below)
             │ release
        ┌────▼────┐
        │  Drop   │→ apply the already-computed PlacementPlan
        └─────────┘

   quick lift before timeout & no move ──▶ Tap → open item
```

**Tracking across surfaces (revised — the original root-overlay plan was wrong).** The dragged item's *own*
`pointerInput` tracks the whole gesture: once the pointer is down, the gesture owns it until release, wherever
the finger travels — which is exactly how a drag from home lands on the dock. The coordinator hit-tests that
finger against every registered zone, so cross-surface just works. The **one rule** that makes this robust is:
**keep a source surface composed while a drag from it is in flight** — don't unmount it mid-gesture (a side
surface that "closes" slides/fades but stays in the tree until the drag ends). That single lifecycle rule is
the structural replacement for L1's `HomeDragBridge` + `onDragOutMove/onDragOutEnd` re-tracking loop.

> A tried-and-rejected alternative: a full-screen root **pointer overlay** that takes over tracking. In Compose
> a full-screen `pointerInput` on top **swallows all events from the items beneath it** (pointer events are not
> delivered to every overlapping sibling), so it broke every gesture. Keeping the source composed needs no
> overlay and no handoff.

**"Source surface" reads per *container*, not per screen.** A folder's grid is a surface by this rule: a drag lifted
inside one must keep **that** folder composed until release, even after the folder has closed and another has opened
over the same screen. Hence `FolderOverlay`'s `presenting = false` role (§4a) — the same overlay reduced to an
invisible pointer holder, emitted from the **same keyed call site** as the presented one, because moving a composable
to a different call site is itself a disposal and would kill the drag it exists to preserve.

---

## 6. DropBehavior = partition strategy + reflow policy

Every per-surface difference is two pluggable policies on the zone. The coordinator stays behavior-agnostic.

### 6a. Partition strategy — finger-in-cell → `CellIntent`

```kotlin
sealed interface CellIntent { data class Push(val dir: PushDirection); object Merge; object None }
```

| Surface | Partition of the destination cell | Merge |
|---|---|---|
| **Home free-grid**, **Dock** | **per target item, not per cell** — the partition spans the hovered occupant's **whole rectangle**, so a multi-cell item (2×2 widget) is **one** target: one 4-way push split by its diagonals (finger in top triangle → push occupant down, etc.) + **one** central merge ring, not one partition per sub-cell. Empty cells have no partition (plain place). | merge ring present **only if** target is precomputed mergeable at lift (§6c); otherwise the whole item is 4-way push. |
| **APPS pager (no category)** | full cell incl. label, **`[left \| center \| right]`**; center = merge; **near-side zone disabled**, far-side = push (§6b). | yes (3-zone) |
| **APPS pager (category)**, **Folder view** | same, **`[left \| right]`** | no folders allowed → 2-zone, no merge |
| **Home vertical list** | center-cross (Reorderable-style 1-D) | no |
| **Vertical grid**, **APPS vertical grid** | any drag **ejects**: close the surface, re-host the drag on home | n/a |
| **APPS category card** | per L1 | — |

### 6b. Reflow policy — how push/drop mutates placement

- **`FreePush`** (home, dock) — 2-D spread-push: shove the occupant in the opposite direction of the entered
  sub-zone; **cascade** if the next cell is also occupied. If the chain runs off the grid edge with no room →
  **invalid drop** (error shadow). Gaps allowed; spans supported.
- **`MovingGap`** (APPS pager, folder) — all items are **1×1**. Lifting an item leaves **one visible gap** on its
  page; the grid splits into left-list `[0..i-1]` and right-list `[i+1..end]`. Dragging toward one list disables
  the near zone; the far zone **migrates the gap one step** toward the finger
  (`[1][2][gap][4][5]` → push item2 → `[1][gap][2][4][5]`). Because there is always exactly one gap on the page,
  **a full page never overflows during a live drag.** Densify happens only on drop/merge.
- **`DenseReorder`** (home vertical list) — 1-D reorder, no gap, no merge.

### 6c. Merge-eligibility precompute

At lift, the active-drag registry stores `canMerge(dragged, target)` **per participating item** (one boolean).
That flag flips a zone between its merge and no-merge partition, so dragging different item types never conflicts
(an app over a widget shows only push zones, never a phantom merge zone). This is your improvement over L1, and
it's one precomputed boolean — not a special case in the hit-test.

---

## 7. Drop indicator (the shadow / footprint)

Always drawn, sized to the **dragged item's span**, snapped to the target cell. Its style is a **pure function of
the `PlacementPlan`** — so it can never lie about the outcome (L1's 3×-SpreadPush bug):

| State | Look | When |
|---|---|---|
| Valid | normal fill | `plan.valid`, intent = place |
| Error | red / error fill | zone rejects item, or `FreePush` cascade runs off the grid |
| Merge | shadow **expands** over target | `plan.intent == Merge` |
| Push  | debug tint (dev only) | `plan.intent == Push` |

**Positioning — half-cell hysteresis.** The target cell is the **dragged item's own snapped position**, not the
cell under the finger: take the item's top-left (its proxy is finger-centred) and **round** to the nearest cell.
Rounding gives free hysteresis — the footprint holds still until the item has travelled half a cell, then steps
one cell in the drag direction — so it never jitters cell-to-cell on small movements. The top-left is clamped so
a multi-cell footprint stays on the grid. (This is a surface-geometry rule, so every real surface applies it,
not just the dev harness.)

---

## 8. The engine (`data:layout`, B8)

Pure, unit-testable, no Compose. Preview and commit share one result.

```kotlin
data class PlacementPlan(
    val footprint: GridPlacement?,     // what the shadow paints
    val intent: DropIntent,            // Place | Push | Merge | Invalid
    val valid: Boolean,
    val changes: List<LayoutChange>,   // what commit applies on release
)
```

`LayoutChange` command set — refactored from L1's 19 near-duplicate ops (per REWRITE_PLAN B8):
- Collapse 5 `Move*` → one `Move(item, to, zone)`.
- `Add{App,Folder}ToIconContainer` → `AddToIconContainer(id, item)`.
- **Remove ≠ uninstall**: `RemoveFromGrid` / `RemoveFromFolder` / `RemoveFrom*Container` detach placement (app
  stays installed). Uninstall is a system action in `data:apps`; layout reacts to the `AppEvent` removal and prunes.
- Unify L1's `GridEdit` / `DockGridEdit` into one edge-edit parameterized by `GridBlueprint`.

Geometry pieces: `GridOccupancy` (what sits where), `PlacementResolver` (drop → plan), `GridReflow`
(densify/repaginate). **FLOW is dropped from home** — home stays coordinate-based with gaps allowed.

**Cross-page landing** (APPS pager): dwelling at a page edge auto-flips the page (§9). Dropping onto a different,
full page is **not** a live overflow — it triggers repagination at **drop time** in repository logic
("a move compacts the source page; overflow cascades forward"), per the locked persistence model.

---

## 9. Page-flip on edge dwell

Dwelling the finger at the left/right edge of a paged layout (home pager or APPS pager) auto-advances the page
after a short ramped dwell — reusing Reorderable's auto-scroll pattern (`Scroller`: conflated channel + speed
ramp), with "scroll" swapped for "flip page." One timing constant in the shared config.

---

## 10. Cross-surface transfer matrix

| From ↓ / To → | Home | Dock | Side surface | Folder |
|---|---|---|---|---|
| **Home** | ✓ | ✓ | ✗ (drawers are A–Z-derived) | ✓ (drop on its ring, or dwell to enter) |
| **Dock** | ✓ | ✓ | ✗ | ✓ (same two ways) |
| **Side surface** | ✓ (extract) | ✓ (extract) | reorder within self | — |
| **Folder** | ✓ (leave) | ✓ (leave) | ✗ | reorder within self, **and ✓ folder→folder** |

Every ✓ is one uninterrupted gesture — the cell keeps its pointer stream throughout, and the drop reports the zone it
landed in (§4). **Folder→folder** has two forms, differing only in precision: drop on the target's merge ring to append
the app, or dwell on that ring to open the target and place it at a chosen slot (§4a). Dropping an app back on the
folder it came from is a no-op — it never left.

Behavior always travels with the **destination** zone (a drop into the dock uses the dock's `FreePush`, whatever
the source was). Vertical grids don't appear here — they eject to home on lift, so the drag is a home drag from
the start.

---

## 11. Build order (small, reviewable parts)

Each part is independently readable; the drag UI is built against a **fake planner** before the real engine exists.
The `data:layout` geometry (originally one bullet) is split finer for review — smaller units the author can read
in one sitting.

- [x] **1a. `FreePush`** — 2-D cascade push for free grids (home/dock), with `PushDirection` + `PushResult`.
  Ported from L1 `SpreadPush`. Pure, unit-tested.
- [x] **1b. `GridOccupancy` + `GridReflow`** — packed-cell occupancy + post-shrink re-home. Ported from L1;
  L1's five parallel typed maps collapsed to one generic `Map<K, GridPlacement>`. Pure, unit-tested.
- [x] **1c. `PlacementPlan` + `DropIntent` (`core:model`) + `FreeGridPlanner` (`data:layout`)** — the "planner
  face": turns a resolved hover into the one plan preview + commit share. Pure, unit-tested.
- [x] **1d. `MovingGap`** (APPS pager / folder 1-D reflow) — done in the harness as the ordered-surface path:
  a visible gap migrates via the `[left|center|right]` per-cell partition (left/right set the insertion index
  before/after the hovered item, center merges), items animate around it, and the flow densifies on drop. Lives
  in the render/partition layer as designed (§6b), not `data:layout`. `DenseReorder` (vertical list) still TODO.
- [x] **2. `DropZone` registry + `DragCoordinator` state** (`core:designsystem/drag`) — root-owned drag state,
  zone registry, topmost-z + accept-filtered hit-testing, drop resolution. Driven by an injected `DropPlanner`
  port (fake in tests). Unit-tested (11 cases). Rendering + gestures deferred; `DropZone` will grow
  `geometry`/`behavior` when the real planner lands.
- [x] **3a. The gesture state machine** (`ItemGestureMachine`) — pure reducer of the §5 contract
  (tap / press-swipe / long-press→menu→drag), one machine + one `ItemGestureConfig` for every item. Unit-tested
  (13 cases).
- [x] **3b. The gesture Compose modifier** (`Modifier.launcherItemGestures`) — thin `pointerInput` shell:
  races the long-press timer against movement, feeds pointer changes to `ItemGestureMachine`, dispatches its
  effects to callbacks (drag callbacks in root coordinates). Not unit-tested (plumbing); verified end-to-end
  with the first real surface. Single-surface tracking only; cross-surface root-overlay takeover deferred.
- [x] **4. `FloatingDragIcon` + `DropFootprint`** — the drag visuals. `DropFootprint` renders the four §7
  shadow states from `DropIntent` (monochrome; red only for INVALID; PUSH is a debug tint); `FloatingDragIcon`
  is the root-positioned, lifted proxy that follows the finger. Positioning/wiring is the surface's job (Part
  5). Also hardened `PlacementPlan.footprint` to non-null (INVALID keeps the hovered cell so the red shadow has
  somewhere to paint; "no target" is a null plan).
- [x] **5a. Single-grid dev harness** (`app` → `dev/DragPlaygroundScreen`) — the first end-to-end run: one
  free-placement grid, fake tile items, one registered `DropZone`, `launcherItemGestures` per cell, the
  engine-backed `DropPlanner` (`FreeGridPlanner`), and the `FloatingDragIcon`/`DropFootprint` overlay. Dragged
  tile stays composed (alpha 0) so its pointer stream survives. Proves push/place/invalid, **multi-cell spans**
  (1×1, wide, 2×2; footprint/proxy/push all span-aware, top-left clamped in-grid), the **§6a directional push
  partition** (the hovered cell's 4 quadrants pick the push direction; `FreePush`'s nearest-edge order is the
  fallback), and the **§6a merge ring** (inner circle of a mergeable occupant → MERGE plan + expanded shadow;
  eligibility checked per hover ≈ §6c). No dock / pager / cross-surface yet.
- [x] **5b. Multi-zone + cross-surface**: harness registers **home + dock + drawer** as free-grid zones; one
  coordinator hit-tests all, planner dispatches on `zone.id`. Cross-zone drag moves items source→dest, tracked
  by the dragged tile's **own** pointer stream (no root overlay — that approach swallowed all item gestures and
  was removed). The rule that replaces L1's HomeDragBridge: keep a source surface composed while a drag from it
  is in flight (§5).
- [x] **6. Folder overlay zone** — done, and it went well past "prove drag-out". `FolderOverlay` registers the inner
  card as a zone at `z = 1` over home's two; `FolderDragDelegate` keeps its reorder (`MovingGap` over the dense flow)
  inside the overlay while the shared planner dispatches to it by zone. On top of that, the **visit model** of §4a:
  dwell-to-enter, dwell-to-leave, both repeatable within one drag, folder→folder in one gesture, auto-dissolve of the
  second-last app, and `FolderHostState` as the surface-agnostic lifecycle (20 unit tests). Pointer survival is the
  §5 rule applied per folder — the folder a drag was lifted from stays composed as an invisible holder.
- [ ] **7. `EjectToHome`** (vertical grids) + wire `MovingGap`/`DenseReorder` partitions.
- [x] **8a. Page-flip on edge dwell** (§9) — home pager flips on an edge dwell mid-drag, and grows a trailing empty
  page so an app can be carried onto a new one. Lives in `CoordinateDragPager`; the APPS pager inherits it when built.
- [ ] **8b. Cross-page repagination on drop** — the APPS pager's paged-order store (page + in-page slot, overflow
  cascading forward). Blocked on that repository, not on the drag layer.
