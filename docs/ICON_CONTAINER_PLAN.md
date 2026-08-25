# Icon container — arrangement, reorder and configuration

**Status:** planned, nothing landed.
**Covers:** the icon container's inner geometry, drag-reorder of its contents, drag-out, and how it is configured.
**Extends** [CONTAINERS_PLAN.md](CONTAINERS_PLAN.md), whose §3d ("Slice 3 — arrangement and axis") shipped the
chooser and stopped there. Everything in §0–§2 of that document still stands; this one does not revisit it.
**Reference:** Smart Launcher 6.6 (`ginlemon.flowerfree`), "Icon group" widget, driven on device 2026-08-24. Not a
port — we already have more arrangements than it does. It is used here the way L1 is used: as an answer key for the
parts we got thinner, and questioned everywhere else.

---

## 0. What exists today

| Piece | State |
|---|---|
| `IconArrangement` — 7 values (grid, circle, 4 fans, beehive) | done, and **unchanged by this plan** |
| `IconArrangements.slots()` — pure, exhaustive `when`, unit-tested | done, **with the geometry gaps in §1b** |
| `IconContainerCell` — absolute slot placement, panel, empty-state "+" | done |
| `ContainerSettingsScreen` — contents list, multi-select add, per-row remove, arrangement chooser | done |
| Drop app/folder **onto** a container → `AddToIconContainer` | done, **appends** |
| Resize (a container resizes like a widget) | done |
| **Reorder inside a container** | **nothing — no op, no gesture, no persistence path** |
| **Drag an item out of a container** | **nothing** — removal is a row in settings, membership-only |
| Arrangement at creation | hardcoded `IconArrangement.GRID` (`HomeViewModel.createIconContainer`) |
| Per-container icon size / spacing / background | none — global metrics, a flat `8.dp` gap, panel always drawn |

`icon_container_item.sortOrder` exists and is read (`ORDER BY sortOrder`, `iconContainersOf` sorts by it). **Nothing
ever writes a value other than "append at the end"** — `AddToIconContainer` takes `maxSortOrder + 1`. The column is
already the whole persistence story for reorder; it has simply never had a second writer.

---

## 1. The reference, and what of it is worth taking

### 1a. What Smart Launcher does that we do not

Four shapes to our seven — *Flower* ≈ our `CIRCLE`, *Grid* ≈ `GRID`, *Honeycomb* ≈ `BEEHIVE`, *Arch* ≈ our `FAN_*`
(theirs curves, ours is a straight diagonal triangle). So the shape vocabulary is not what we are missing. What it
has that we do not, in the order it matters:

1. **Drag-reorder inside the container, as a swap.** Dragging the 9th icon onto slot 1 exchanges the two and moves
   nothing else — verified by reading every slot's bounds before and after.
2. **A drop from outside lands at the finger**, taking the nearest slot and displacing what was there, rather than
   appending.
3. **Drag out onto the home screen**, after which the container compacts.
4. **A live preview while configuring** — its settings are a floating panel over the running home screen, draggable
   and minimizable, so every slider moves the real container.
5. **The shape is chosen before placement**, in the picker.
6. **Per-container icon size (25–200%) and item spacing (50–200%)**, and a background toggle.
7. **Layout-dependent options** — *Columns* (1–8) and *Position* (top/center/bottom) appear for Grid and for
   nothing else.

Its *collapse* feature (`Icons visibility`: always / remember / hidden-on-start / always-hidden) is real and works —
the widget keeps an invisible tap region and expands on tap. It is the one thing here I would not take; see §5.

### 1b. Where our geometry is measurably thinner

Measured on a 520dpi device, its container at 4×4 cells is 251×255dp, its icon at 100% is 64dp, and its pitch at
spacing 100% is 96dp — i.e. **the gap is half the icon**, not a fixed number of dp.

Three concrete divergences in `IconArrangements.kt`:

- **`gridSlots` cells are not square.** `cellW = width / cols`, `cellH = height / rows`, so on a non-square
  container the horizontal and vertical pitch differ and the icons sit on a sheared lattice. SL uses
  `min(availW/cols, availH/rows)` and centers the resulting block.
- **`gridSlots` leaves the last partial row flush left** (`(i % cols) * cellW`). SL centers it. This is the single
  most visible difference and it is one expression.
- **`circleSlots` uses a fixed `ringR = maxR * 0.62`** whatever the count, so three icons float sparsely in a large
  ring and the icons shrink to compensate as the count grows. SL solves the radius from the chord instead —
  `r = pitch / (2·sin(π/n))`, capped by the box — so few icons make a *tight* cluster and the icons only start
  shrinking once the ring has hit the wall. Measured: 47% of the max radius at n=3, 73% at n=8, 92% at n=33.
- **`fanSlots` is a triangle, not a fan.** It walks straight anti-diagonals from the corner, so the shape is a
  right triangle with a stepped hypotenuse. SL's *Arch* puts its icons on **concentric quarter-arcs** around the
  corner, which is what a fan actually is — see §2g.

`beehiveSlots` already scales its whole cloud to fit and needs nothing; at high counts it is better than SL's, whose
honeycomb visibly overlaps its icons.

---

## 2. Design decisions

### 2a. Reorder is a **swap**, not a MovingGap insert

`OrderedFlow.kt` already implements the reorder model every ordered surface here shares — a gap migrating through a
list — and the folder and the APPS pager both use it. It does not fit this surface, and the reason is structural
rather than a matter of taste: **every function in it is defined on `GridGeometry`** (`cellFractionX`,
`thirdInCell`, `flatSlotOf`), because an insertion index only means something where there is a reading order to
insert into. A circle has no "before"; a beehive spiral has one only in the sense that a spiral is a sequence, which
is not what a finger over the ring is expressing.

What *is* well defined for all seven arrangements is **which slot is nearest the finger**, and `slots()` already
returns exactly that list of rectangles. So:

- hover resolves to a slot index by nearest-center hit-test against `slots()`;
- the preview exchanges the dragged icon with that slot's occupant;
- the drop commits the exchange.

This is also why SL's choice is right rather than merely simpler: in a shaped layout the user is pointing at a
*place*, and shifting six neighbours around to honor that is a different gesture from the one they made. Note this
is a deliberate departure from how a *folder* reorders, and the two must not be "unified" later — they answer
different questions.

**Rejected:** generalizing `OrderedFlow` over an arbitrary slot list. It would make `GRID` behave like the folder
and leave the other six needing the nearest-slot path anyway, i.e. two models with one name.

### 2b. The container carries its own `DropZone`

`AppCollectionDragDelegate` set the precedent and its KDoc records why the alternative was wrong: the collection's
reorder logic used to be published up to the host surface, which routed to it from a `when (zone.id)` in its own
planner — the same two branches repeated in three surfaces. **A zone carries its own behavior.**

So `IconContainerCell` registers a `DropZone` with `z` above the home grid's, a planner that returns
`PlacementPlan(footprint = the container's own placement, intent = DropIntent.REORDER)`, and an `onDrop` that
commits. `DropIntent.REORDER` already exists and `DropFootprint` already returns early on it, so no shadow is
painted — correct here, because the preview *is* the two icons trading places.

Consequence to accept deliberately: while a finger is inside a container's bounds, the home grid cannot be the drop
target. That is what makes drop-at-the-finger work, and it is the same trade the open collection already makes.

### 2c. One gesture machine per press, deciding what it lifts

**Superseded during implementation.** This section planned to give each slot its own `launcherItemGestures` and
arbitrate between it and the cell's. That does not work, and the reason is in the contract rather than in the
tuning: `ItemGesturePhase.MenuOpen` reports `ownsFinger`, so from the long-press onward a nested machine *and* its
parent both consider the finger theirs, and both fire `BeginDrag` on the next move — two `coordinator.start` calls,
with whichever ran last surviving. The contract arbitrates against **ancestors** (consumption seen on `Initial`,
and again on `Final` for a `Main`-pass parent) and has no notion of a sibling machine on the same press. Worse, the
long press fires from a *timer*: with the finger held still there is no pointer event for either side to consume,
so nothing can tell them apart at the moment it matters.

What is built instead keeps the container's single gesture exactly where it was — on the whole cell, per
`LauncherDragCell`'s stated exception — and decides **what that press is on** from where it landed:
`LauncherDragCell` gained `innerItemAt`, a hook giving the cell-local position and size, and the container answers
with the icon under the finger or with nothing (meaning itself).

**The resolution happens once and all three verbs follow it** — the drag lifts that item, a tap opens it
(`onOpenInner`), and a long-press raises *its* menu (`onShowInnerMenu`), anchored to its own rectangle rather than
to the cell. Splitting them would let one cell drag one thing and launch another. `launcherItemGestures` therefore
reports the press position on `onOpen` and `onShowMenu` as well, which it already did for `onBeginDrag`.

**A slot carries no `clickable`, and that is the same rule stated everywhere else** (CLAUDE.md: cells carry no
`onClick`; taps arrive through the one gesture contract). It had one, and the bug was exactly what that rule
exists to prevent: `clickable` fires on release whatever the gesture did, so a long-press raised a menu and then
launched the app underneath it, and a completed reorder launched the icon it had just dropped.

Two consequences worth knowing:

- The hit-test must agree with the drawing to the pixel, so the slot geometry became one function both call —
  `iconContainerSlots` in `feature:home`. The gap is a bare dp, so a second copy of that call would put the touch
  targets a few dp off the picture and read as "that icon is hard to grab" rather than as a wrong number.
- It answers on **containment**, not nearest: a ring's hollow middle and the slack around a short arc are how the
  container itself is picked up, resized and long-pressed. Nearest-slot is the right question at *drop* time, when
  a drag already in flight has to land somewhere, and `nearestIndexTo` is that separate answer.

### 2d. The gap becomes proportional to the icon, not to the container

`IconContainerCell` currently argues for a flat `8.dp`: *"breathing room between two icons, which is a constant of
how the eye separates them and not of how big the container is"*. That reasoning is sound and this does not
contradict it — it is about the **container's** size, and what is proposed is a fraction of the **icon's**. A 64dp
icon and a 24dp icon do not want the same 8dp between them; SL's own default is half the icon.

Keep the literal at the call site (a dp value is written where it is used); make it a multiplier of the resolved
icon size rather than an absolute.

### 2e. The live preview goes **in our settings screen**, not in a floating panel

SL's floating draggable panel is the best interaction it has, and I would still not copy the panel. Our
`ContainerSettingsScreen` became a nav destination for a stated reason — the "+" used to open a picker directly, so
arrangement was unreachable — and an overlay would half-revert that while splitting one screen across two places.

The property worth having is that **changing a setting shows you the result**, and there is a cheaper way to get it
that the codebase already demands: *two implementations of one thing are kept honest by a shared derivation*. Put a
real `IconContainerCell` at the top of the settings screen, fed the same `ContainerSettings.Icon` the rows are built
from. `slots()` is pure and the cell is self-contained, so the preview and the home-screen surface cannot disagree
about geometry — they are the same composable over the same data. A drawing of a container would be the hazard;
the container is not.

### 2f. `Columns` and `Position` are **not** taken

SL shows them for Grid and hides them for its other three. Ours derives the column count from the box
(`sqrt(count × aspect)`), which is what makes a container's grid stay square-ish as it is resized — a user-pinned
column count fights the resize handle that CONTAINERS_PLAN §5 already delivered. `Position` is likewise answered by
centering the block (§3a), which is the only value of the three anyone picks.

If a column override is ever wanted it is additive; it is not worth a per-arrangement conditional settings list now.

### 2g. `FAN_*` becomes a real fan — concentric arcs, not a triangle

The current implementation walks straight anti-diagonals out of the corner, which draws a right triangle with a
stepped hypotenuse. **That was never what the name promised**: a fan is curved, and the triangle is the one shape
in this file that does not match the value it implements. SL's *Arch* is the honest version — icons on concentric
quarter-arcs around the corner — and it is the shape we want.

Nothing is traded away by this. The four corner variants are the `fromLeft`/`fromTop` decomposition, which is
untouched, so we end with four orientations of the curved fan where SL ships one. **And `IconArrangement`'s names
do not change**, so there is no model change and no migration — `IconArrangementConverter` stores `value.name`, and
`FAN_TOP_LEFT` still means exactly what it says. This lands entirely inside `IconArrangements.kt`.

The rule, in normalized polar coordinates anchored at the corner, then **scaled to fit** — deliberately the same
technique `beehiveSlots` already uses, so the two shapes that grow in rings do it one way:

1. Radial pitch `1`; ring *k* (from 1) sits at radius `k − 0.5`.
2. Ring *k* holds as many icons as fit along its quarter arc at **unit tangential pitch** —
   `max(1, floor((π/2)·r_k) + 1)` — so tangential spacing tracks radial spacing. That uniform polar density is
   what makes it read as a fan rather than as a bent grid, and it is the property to preserve if the constants are
   tuned.
3. Rings emit outward until `count` is placed. Angles spread over `[0, π/2]` with the endpoints included; a lone
   icon sits at `π/4`. The **last, partial ring is centered on the arc**, for the same reason slice A centers the
   grid's last row.
4. Scale to fit, and **inset the polar origin by half an icon** — the pivot is half an icon in from the corner,
   not on it. A quarter disc anchored at a corner is bounded by the **shorter** side, so with `S = min(width,
   height)`: `scale = S / (maxR + iconUnit)` and `icon = iconUnit × scale`, which solves `S = icon + maxR × scale`.

   The inset is the step that is easy to miss and it fails *visibly*: the icons on the two axes (angle `0` and
   `π/2`) have their centers exactly on the box edge, so without it every ring's first and last icon is **half
   outside the container**. Scaling for `maxR + iconUnit/2` — the beehive's formula, which is correct there because
   its cloud is centered — leaves exactly that bug here, because this cloud is corner-anchored and therefore
   asymmetric. Simulated before writing this: the naive version puts icons outside the box at every count; the
   formula above puts none outside at 6, 9, 14 or 33.

Measured against SL for sanity: its arcs hold 1, 3, then 5 icons, with radii roughly 192 / 445 / 725 px on an
815×830 container — i.e. near-linear radial growth, which is what step 1 gives. Step 2 yields 1, 3, 4, 6, 8 per
ring (cumulative 1, 4, 8, 14, 22), so the two agree closely without copying its constants. The exact constants (the
`−0.5`, the `0.8` icon-to-pitch ratio) are worth tuning on device rather than pinned in a test; the tests should
pin the *properties* — see §3a.

**Consequence for the first icon:** it no longer sits *in* the corner, it sits on the first arc at `π/4`. The
corner is the pivot, not a slot. `each fan pivots on its own corner` currently asserts the flush-corner version and
has to be restated as "the fan opens away from its own corner" — the four values still differ only by which corner,
which is what that test exists to protect.

### 2h. Per-container size/spacing multiply, they do not replace

`IconContainerCell` resolves through `metrics.resolveIconSizeUnfloored(...)`, deliberately capped by the user's own
`maxIconDp` — *"the same guardrail every other surface resolves through"*. A per-container slider must be a
multiplier applied to that result and still coerced to the slot, so a container cannot escape the global ceiling by
its own setting. SL's 25–200% / 50–200% ranges are a reasonable starting shape.

---

## 3. Slices

Each is device-testable alone. A–B–C is the spine; D–F are independent of each other.

### 3a. Slice A — arrangement geometry (pure, no UI, no persistence)

`IconArrangements.kt` and `IconArrangementsTest` only. Three shapes change; the enum, the model and the DB do not.

1. `gridSlots`: square cells (`min(width/cols, height/rows)`), block centered in the box, **last partial row
   centered**.
2. `circleSlots`: radius solved from the chord and capped by the box, replacing the fixed `0.62`.
3. `fanSlots`: concentric quarter-arcs per §2g, replacing the anti-diagonal triangle.

The fan is the largest of the three and can be split out if slice A gets unwieldy — it shares no code with the
other two. Do it **last** within the slice, so a regression in the grid or the circle is not hunted through new
polar arithmetic.

**Four existing tests are a spec change, not a failure to route around**, and must be rewritten to state the new
rule rather than relaxed to accept it:

- `grid tiles the box without gaps or overlap` asserts `slots[0].width == width / 3f`. The grid no longer tiles the
  box — it tiles a centered square block inside it. The property worth keeping is "no overlap, no holes *within the
  block*".
- `circle shrinks its icons as the ring fills` compares n=3 against n=12. Under the new rule the icon size is
  **constant until the ring caps**, then shrinks, so the assertion has to name the capped case explicitly.
- `fan cascades along anti-diagonals without overlap` asserts `slots[0].width == height / 3f` — the triangle's
  `g`-rule. It goes entirely; replace it with the fan's real invariants: **radius strictly increases ring over
  ring**, and no overlap.
- `each fan pivots on its own corner` asserts the first slot is flush in the corner. Restate per §2g — the corner
  is the pivot, and what the four values must still differ by is *which* corner they open away from.

Surviving unchanged, and worth confirming rather than assuming: `fan cells stay square`, `every arrangement keeps
its slots inside the container` (check the fan at n=1 and n=2, and the capped circle at n=1), `every arrangement
gives every icon a positive size`, and `gap separates neighbouring icons by exactly that much` (expressed relative
to `plain[0].width`, so the grid's new block size does not reach it).

Verify on device by resizing a container in each arrangement: nothing should shear, three icons in a `CIRCLE`
should read as a tight cluster rather than a sparse ring, and a `FAN_*` should curve — compare against the
reference capture of SL's *Arch* at 9 and at 33 icons.

### 3b. Slice B — reorder, end to end

1. **`data:layout`** — `LayoutChange.ReorderIconContainer(containerId, items: List<IconItem>)`, and its `apply` arm
   mirroring `ReorderFolder` exactly: `iconContainerItem.clearContainer(id)` → `detachIconItem(each)` → `upsert`
   with fresh indices. The DAO already has all three calls; nothing new is needed below `data:layout`.
2. **`IconContainerCell`** — per-slot `launcherItemGestures` (§2c), container gestures unchanged on the root.
3. **The zone** (§2b) — planner hit-tests `slots()` for the nearest center; a delegate holds the previewed swap and
   commits it.
4. **The preview** — the two slots animate to each other's positions. The slot list is already recomputed from
   `remember(arrangement, icons.size, …)`, so the preview is a reordered `icons` list, not a second geometry.

Verify on device in `GRID`, `CIRCLE` and `BEEHIVE`: dragging any icon onto any other exchanges exactly those two,
and the arrangement is unchanged when the drag is cancelled.

### 3c. Slice C — a drop from outside lands where the finger is

With the zone from B in place, `AddToIconContainer` grows `index: Int? = null` (null appends, which is every
existing caller). The `apply` arm stops using `maxSortOrder` for the indexed case and instead reads
`getByContainer`, inserts at the index, and rewrites the order — the same read-modify-write `ReorderIconContainer`
does, so there is one way sortOrder is authored rather than two.

Home's merge path (`HomeViewModel.mergeChanges`) passes the slot the container's zone resolved.

### 3d. Slice D — drag an item out

Unlocked by B2, and small: a slot dragged onto the home grid commits `RemoveFromIconContainer` **plus** a `Move` —
the composition rule CLAUDE.md already states for the APPS pager, and the reason `RemoveFrom*Container` is
membership-only. Decide and record what an empty container does: a folder dissolves at its last item, a container
should **not** (it is a placed object with its own settings, and its empty state is a designed one).

`ContainerSettingsViewModel.removeIcon`'s "it goes nowhere" stays true and stays correct — that is a different verb
from dragging it somewhere.

### 3e. Slice E — the arrangement is chosen before placement

`WidgetPickerSheet`'s `ComponentKind.ICON_CONTAINER` already has a detail page, and its KDoc already says the page
*"draws the real thing"*. Add the seven arrangements there, previewed with the real cell over sample icons, and
thread the choice through `onAddIconContainer` → `createIconContainer(zone, config, arrangement)`, deleting the
hardcoded `IconArrangement.GRID`.

### 3f. Slice F — a live preview in the settings screen

§2e. A real `IconContainerCell` above the contents list, at a fixed preview size, fed `ContainerSettings.Icon`. It
costs one item in the `LazyColumn` and makes the arrangement chooser mean something.

### 3g. Slice G — per-container icon size and spacing

`icon_container` gains two percent columns (default 100), `IconContainerEntity` and `iconContainersOf` carry them,
`IconContainer` gains two fields, and `SetIconContainerMetrics(containerId, iconPercent, spacingPercent)` writes
them. Two `MorphicSliderRow`s on the settings screen, under the preview from F, where they are visibly live.

**This bumps the DB to v6 and the app is on `fallbackToDestructiveMigration(dropAllTables = true)`** — the layout
is wiped on next install. Acceptable pre-release, but do it in one bump alongside anything else that wants a column,
not on its own.

A background toggle belongs here too if it is wanted, with one caveat: `containerPanel()` has three consumers and
one of them is the **floating drag proxy**, which exists precisely so a picked-up container does not change
appearance. A per-container toggle has to reach the proxy or it will.

---

## 4. Things to check while implementing

- **The container's zone must not swallow a drag of the container itself.** `DropZone.accepts` is the hook; a
  container hovering over its own bounds should resolve to the home grid, not to itself.
- **A `CIRCLE` has a hollow center**, so a long-press there is a container press, not a slot press. Good — but it
  means "the container's own menu" is reachable in some arrangements and cramped in others (`BEEHIVE` at high
  counts leaves almost no gap). The settings screen is reachable from the item menu either way; confirm there is
  always *some* route.
- **Nearest-slot needs a miss radius.** A finger in the middle of a sparse `CIRCLE` is not near any slot; decide
  whether that is "no swap" or "the nearest anyway", and make the preview say which.
- **Slot count vs item count.** `slots()` returns one slot per item, so the geometry changes while a drag is in
  flight if the item count changes. It does not during a reorder; it does during a drop-in. Resolve the target slot
  against the **pre-drop** list.
- `IconArrangementsTest` asserts every arrangement keeps its slots inside the box. The centered grid block still
  does; confirm the capped circle does too at n=1 and n=2.
- A **folder inside a container** is a draggable slot like any other, and it has no grid placement. Dragging one
  out needs `folderPlacement` written, not just `appPlacement`.

## 5. Deliberately not doing

- **The collapse / `Icons visibility` feature.** An invisible tap region that shows nothing until touched is a
  control that changes nothing, which is the standing rule's whole target — and we have a known gap where no item
  is reachable by an accessibility service (`launcherItemGestures` carries no `semantics`), so a container whose
  only affordance is an invisible region would be entirely unreachable rather than merely awkward. Revisit if and
  when semantics land.
- **The floating settings panel** — §2e.
- **`Columns` / `Position`** — §2f.

(Curved fans were briefly listed here and are **in** — §2g, slice A. The triangle is the thing being dropped.)
