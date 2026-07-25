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
set of zones. (A dwell-to-close animation is optional UX polish on top; drop mechanics don't depend on it.)

---

## 5. Gesture pipeline (root-owned)

One pointer pipeline in the coordinator, applied uniformly to every item on every surface (guaranteeing identical
behavior — the thing L1's 4 recognizers could never guarantee). One timing config, one slop, one state machine.
Items are pure render + registration; they do **not** attach their own drag recognizers.

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
        │ Dragging│         ← coordinator tracks finger at ROOT
        └────┬────┘           (survives page-flip / folder-close / CrossPager collapse)
             │ release
        ┌────▼────┐
        │  Drop   │→ apply the already-computed PlacementPlan
        └─────────┘

   quick lift before timeout & no move ──▶ Tap → open item
```

The moment `Dragging` begins, finger tracking is owned by the **root coordinator overlay**, not the item — so a
page flip, folder close, or `CrossPager` collapse mid-drag can't drop the pointer. This is the structural
replacement for L1's `HomeDragBridge` + `onDragOutMove/onDragOutEnd` re-tracking loop.

---

## 6. DropBehavior = partition strategy + reflow policy

Every per-surface difference is two pluggable policies on the zone. The coordinator stays behavior-agnostic.

### 6a. Partition strategy — finger-in-cell → `CellIntent`

```kotlin
sealed interface CellIntent { data class Push(val dir: PushDirection); object Merge; object None }
```

| Surface | Partition of the destination cell | Merge |
|---|---|---|
| **Home free-grid**, **Dock** | **icon area only** (excludes label). Outer ring = **4 directional push** (finger on top sub-zone → push occupant toward bottom, etc.); inner ring = **merge**. | merge ring present **only if** target is precomputed mergeable at lift (§6c); otherwise whole cell is 4-way push. |
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
| **Home** | ✓ | ✓ | ✗ (drawers are A–Z-derived) | ✓ (drop into folder) |
| **Dock** | ✓ | ✓ | ✗ | ✓ |
| **Side surface** | ✓ (extract) | ✓ (extract) | reorder within self | — |
| **Folder** | ✓ (extract) | ✓ (extract) | ✗ | reorder within self |

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
- [ ] **1d. `MovingGap`** (APPS pager / folder 1-D reflow) + `DenseReorder` (vertical list). *(Deferred — the
  gap-migration engine core is thin and folds in with the partition strategy; §6b.)*
- [ ] **2. `DropZone` registry + `DragCoordinator` state** (`core:designsystem/drag`) driven by a fake planner +
  one test surface. No cross-surface yet.
- [ ] **3. The gesture pipeline** (§5 state machine) wired to one grid.
- [ ] **4. `FloatingDragIcon` + `DropFootprint`** rendering from `PlacementPlan` (§7 states).
- [ ] **5. Multi-zone**: register dock + a side surface → prove cross-surface drop.
- [ ] **6. Folder overlay zone** → prove drag-out.
- [ ] **7. `EjectToHome`** (vertical grids) + wire `MovingGap`/`DenseReorder` partitions.
- [ ] **8. Page-flip on edge dwell** (§9) and cross-page repagination on drop.
