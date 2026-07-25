# Grid Layout Plan — from harness `offset` to a real `LauncherGrid`

**Goal:** replace the dev harness's item positioning (a `for` loop + `Modifier.offset` at a **fixed** `cellDp`)
with a real, responsive **custom `Layout`** (`LauncherGrid`), and **validate every case in the harness** before
any production/feature layout code is written.

> Companion to [DRAG_AND_DROP_DESIGN.md](DRAG_AND_DROP_DESIGN.md). The drag toolkit (coordinator, gestures,
> planner, `FreeGridPlanner`, `DropFootprint`, `FloatingDragIcon`) is **done and stays unchanged** — this plan
> swaps only *how tiles are positioned*. Reference for technique: `../Carbon-Launcher/.../MosaicGrid.kt`.

## Why (what the harness gets wrong for production)

- **Fixed `cellDp`** — not responsive. A real grid fills its surface and derives cell size from *measured*
  space ÷ grid cols/rows, per screen + orientation (what `GridBlueprint`'s per-`DeviceConfiguration` defaults
  are for).
- **`.offset` + `.size(cellDp)`** — children aren't measured against real constraints; a `MeasurePolicy` should
  measure each child `Constraints.fixed(cellW*colSpan, cellH*rowSpan)`.
- **Hardcoded `GridGeometry` `cellPx`** — the drag layer should read the *measured* cell size, so hit geometry
  can't drift from layout.

## Borrow vs change (from MosaicGrid)

Borrow: custom `Layout` + `MeasurePolicy`; per-child data via `ParentDataModifierNode`; a typed grid scope;
cell-size-from-constraints (`maxWidth/cols`). **Change:** MosaicGrid **auto-packs** (first-fit); our home is
**free placement** — the parent data must carry the full **`GridPlacement`** (row/col/span) and the policy
places at exact coordinates, **gaps allowed**. No packing.

## Component design

- **`LauncherGrid`** (`core:designsystem/grid`, B4) — custom `Layout`:
  - cell size from `GridBlueprint.sizing`: **`FIXED_PAGER`** → both axes bounded (`maxW/cols × maxH/rows`);
    **`SCROLL_GRID`** → cols bounded, fixed cell height, grows/scrolls vertically.
  - reads each child's placement → measures `Constraints.fixed(cellW*colSpan, cellH*rowSpan)` → places at
    `(col*cellW, row*cellH)`.
- **`Modifier.gridPlacement(GridPlacement)`** via `ParentDataModifierNode`, exposed through a
  `LauncherGridScope`.
- **Both surface models resolve to per-child `GridPlacement`s** so the grid is uniform:
  coordinate (home/dock) = stored placement; ordered (pager) = surface computes `slot` from `order + gap` →
  `GridPlacement(slot/cols, slot%cols)`, gap = an empty slot.
- **Geometry seam (the key integration):** the grid publishes its resolved `GridGeometry`
  (origin-in-root + **measured** cell size) to the coordinator/planner; finger→cell math is otherwise
  unchanged.
- **Animation:** wrap in `LookaheadScope` + a placement-animation modifier so items animate to new positions
  when their `GridPlacement` changes — one mechanism for free-grid **push** *and* MovingGap **gap migration**
  (replaces the harness's manual `animateIntOffsetAsState`).
- **Right tool per surface:** custom `Layout` for coordinate grids + pager; a `LazyColumn` for pure scrolling
  lists (home list, APPS list) — don't force those through a custom layout.

## Pager (built first, independent of the grid)

The custom **`LauncherPager`** is ported from L1 (`core:designsystem/pager`) — a good L1 layer, like the
placement engine. It's orthogonal to `LauncherGrid` (pager lays out *pages* = viewport; each page holds a
grid), so it's built and validated first, standalone (`app/dev/PagerPlaygroundScreen`).

- [x] **P1 — Port `LauncherPager` + `LauncherPagerState` + `launcherPagerSwipe` + `PageTransformScope`**.
  Infinite is a **toggle** via modular offset on the real `pageCount` (no Int.MAX); `isBounded = dragMode ||
  !infiniteScroll` keeps paging stable under a drag; `normalizeWrapPosition` bounds the scroll float.
  Refactors from L1: renamed (`InfiniteLauncherPager`→`LauncherPager`, `LauncherState`→`LauncherPagerState`),
  dropped grid-inset coupling + the dead fling `decay` param. Standalone test screen validates swipe / wrap /
  bounded / fling / transform.
- [x] **P2 — Integrate** with the drag harness (`app/dev/PagerDragPlaygroundScreen`): page-swipe gated off
  during a drag (`launcherPagerSwipe(enabled = { !isDragging })`); one drop zone = the viewport, footprint page
  = `currentPage`; edge-dwell page-flip via `animateToPage`; `keepAllPagesPlaced = isDragging` keeps the source
  page placed so a cross-page drag survives it scrolling off. Uses `FreeGridPlanner` (nearest-edge). Standalone
  third harness screen; `DragPlaygroundScreen` untouched.

## Build order — each phase ends by validating in the harness

- [ ] **G1 — Static `LauncherGrid`, no drag.** Build the `Layout` + `gridPlacement` ParentData + scope +
  `FIXED_PAGER` cell sizing. Prove in an isolated harness screen/preview.
- [ ] **G2 — Geometry seam + swap one coordinate surface.** Grid reports measured `GridGeometry`; swap **home**
  from `offset` to `LauncherGrid`; keep the existing drag working off the measured cell size.
- [ ] **G3 — Ordered/MovingGap on `LauncherGrid`.** Swap the **pager**; positions from `order + gap` as
  `GridPlacement`s.
- [ ] **G4 — Placement animation.** `LookaheadScope` + animate-placement; remove manual per-tile animation;
  cover push **and** gap.
- [ ] **G5 — `SCROLL_GRID` sizing + `GridBlueprint` wiring.** Add the scrolling-grid mode; drive sizing from
  `GridBlueprint` (+ the scoped `resolveBounds(blueprint, area, iconRail)` resolver).
- [ ] **G6 — Full-harness regression.** All harness surfaces on `LauncherGrid`; run the whole case matrix
  below; confirm parity with the current demo **plus** responsiveness. This is the gate.

## Test matrix — must all pass in the harness before real layout (G6 exit gate)

**Layout**
- [ ] Fills the surface; cell size = measured space ÷ cols/rows; correct in **portrait and landscape** (resize).
- [ ] 1×1 at explicit row/col; **multi-cell spans** (2×2, 1×2) sized + placed right.
- [ ] **Gaps** (non-contiguous placements) preserved (no packing).
- [ ] Spans at the right/bottom **edge** fit/clamp correctly.
- [ ] Empty grid; full grid; **different dims per surface** (home 4×4, dock 4×1, pager 3×4, drawer 1×3).
- [ ] `SCROLL_GRID`: rows overflow and scroll.

**Drag (parity with the current demo, now on measured cells)**
- [ ] Tap / long-press → menu / press-swipe / drag gestures on every surface.
- [ ] Place on empty; **directional push** (4 quadrants, per-item partition); **merge ring**; **half-cell
  hysteresis** footprint; **invalid** (no room → red).
- [ ] Cross-surface: home↔dock, drawer→home, pager **extract**→home; source stays composed mid-drag.
- [ ] MovingGap: reorder, gap migration, merge, **densify** on drop.

**Integration**
- [ ] Geometry seam: finger→cell uses **measured** cell size — no drift when the grid resizes.
- [ ] `DropFootprint` + `FloatingDragIcon` sized to the measured cell.

**Animation**
- [ ] Pushed occupants animate to new cells; gap items animate; no jank on rapid moves.

## Exit criteria → then "real layout"

When the G6 matrix is green in the harness, `LauncherGrid` is trusted. **Only then** do we write production
layout: build the real `feature:*` surfaces (home / apps / folder) on `LauncherGrid` with live data
(`AppRepository`), persistence (`data:layout` `LayoutRepository` + `LayoutChange`), and `GridBlueprint`-driven
sizing — porting the harness wiring, not re-inventing it.

## What explicitly does NOT change

The whole drag toolkit: `DragCoordinator`, `DropZone`/registry, `ItemGestureMachine` + `launcherItemGestures`,
`DropPlanner` + `FreeGridPlanner`, `DropFootprint`, `FloatingDragIcon`, and the "keep source composed during
drag" rule (§5). We are swapping item positioning only.
