# Icon container — arrangement, reorder and configuration

**Status:** part one complete — slices A–G landed (2026-08-25), each verified on a device.
**Part two (§6) is planned and not started:** the arrangement grows parameters of its own. Slice F was **revised the same
day** after device testing; see the note at the end of §3f.
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

### 2h. Per-container size/spacing multiply, and the slot is what bounds them

`IconContainerCell` resolves through `metrics.resolveIconSizeUnfloored(...)`, deliberately capped by the user's own
`maxIconDp`. A per-container slider is a **multiplier applied to that result**, so a container answers to the same
settings as everything around it and then departs from them by a stated amount.

**Superseded during implementation on one point.** This section said the global `maxIconDp` ceiling must still bind
so a container could not escape it. It cannot: the resolve already returns `maxIconDp` for any slot larger than it
and the slot size for any slot smaller, so *every* value above 100% coerced straight back and the control was inert
at every count and every container size. A guardrail exists for icons nobody sized on purpose, and this slider is
someone sizing them on purpose — the same relationship the icon-size settings have with their own defaults.

What still binds is the **slot**, because past it neighbours overlap. That is also what makes the two sliders one
control group rather than two: lowering the spacing enlarges the slot, which is how icons are given room to grow
into. SL's ranges are adopted — 25–200% for the icon, 50–200% for the gap.

---

## 3. Slices

Each is device-testable alone. A–B–C is the spine; D–F are independent of each other.

### 3a. Slice A — arrangement geometry (pure, no UI, no persistence) ✅

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

### 3b. Slice B — reorder, end to end ✅

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

### 3c. Slice C — a drop from outside lands where the finger is ✅

`AddToIconContainer` grew `index: Int? = null` — null appends, which is every picker caller, since filling a
container from a list names a *set* rather than an arrangement. A drag names a place, so the container's zone
resolves the slot and passes it. The indexed path reads the container and rewrites the whole order through
`setIconContainerItems`, so there is one place `sortOrder` is authored rather than two that could disagree about
density; the append path stays a single row.

**The zone widened from "only my own members" to "anything an icon container can hold"**, which is what §2b's
"deliberately accept" was pointing at: while the finger is inside those bounds the home grid is not the drop
target, so an app can no longer be dragged *over* a container to shove it aside. The container is still moved by
dragging the container.

The preview is the arrangement **opening up**: the slot list is laid out for `count + 1` while a newcomer hovers,
and the entry at the hovered index is a hole (`shown` is `List<ContainerIcon?>`) because the floating proxy is
already drawing it. Same list the drop commits, so the two cannot disagree.

**`canMerge` / `mergeChanges` keep their icon-container arms.** They are now unreachable in the ordinary case —
the zone outranks the merge ring — but not dead: a container that has not been measured yet publishes no zone
(`bounds == null`), and the merge ring is what answers for that frame.

### 3d. Slice D — drag an item out ✅

Unlocked by B2, and small: a slot dragged onto the home grid commits `RemoveFromIconContainer` **plus** a `Move` —
the composition rule CLAUDE.md already states for the APPS pager, and the reason `RemoveFrom*Container` is
membership-only. Decide and record what an empty container does: a folder dissolves at its last item, a container
should **not** (it is a placed object with its own settings, and its empty state is a designed one).

`ContainerSettingsViewModel.removeIcon`'s "it goes nowhere" stays true and stays correct — that is a different verb
from dragging it somewhere.

### 3e. Slice E — the arrangement is chosen before placement ✅

`WidgetPickerSheet`'s icon-container detail page carries a scrolling row of the seven arrangements, and the tile
above shows the chosen one at the size it will land. `createIconContainer` takes it; the hardcoded
`IconArrangement.GRID` is gone.

**The shapes are drawn by the arrangements themselves.** `IconArrangementSwatch` lays dots out through the same
`iconContainerSlots` the real container uses, so a swatch cannot advertise a shape the container does not make —
the anti-drift rule the page's own KDoc already argued for its "+". Slice A retuned three of these formulas and
nothing here would have needed touching.

It has **two consumers**, which is what made it worth extracting: the picker chooses by it, and the settings
chooser dialog now shows it beside each name. That dialog was text alone, and four of the seven names differ only
by a direction that is far quicker to see than to read.

**The picker offers four shapes, not seven values.** The `FAN_*` family is one shape in four orientations, and
which corner it opens from is a property of a container that already exists — adjusted where it can be seen on the
wallpaper. Asked here it would be four near-identical swatches and a decision the user has no way to judge yet, so
the row shows one fan (`FAN_TOP_LEFT`, which fills in reading order) and the container's settings keep all seven.

**The dot count is a property of the shape, not of the caller**, because they do not become recognizable at the
same number: a grid is a grid at six and only gets busier, a beehive wants its centre plus one complete ring —
seven exactly, since an eighth starts a second ring that reads as a lump — and a ring is a ring almost
immediately. The fan is the demanding one and the reason the count is per-shape at all: its arcs hold 1, then 3,
then 4, then 6, so under eight it draws one arc and part of another, and fourteen is where four come out complete.
`swatchCount` is exhaustive over the enum, so a new arrangement must say what shows it before it can be offered.

The picker's `addFor` became `canAdd`: the sheet can still say *whether* a component is placeable, but it can no
longer build the commit, because an icon container's page now carries a choice and the commit has to be made where
that choice lives.

**What it does not do is preview the contents.** A container lands empty, and which apps go in has nothing to do
with which shape is being chosen — dots say "shape" without pretending to know.

### 3f. Slice F — a live preview in the settings screen ✅

An `IconContainerCell` above the contents list, over the real membership and the real arrangement — §2e's point,
in its strongest form: not two implementations kept in step, but one implementation drawn twice. It follows the
arrangement chooser as it is used, which is what that row of names was missing.

`containerId` is left null, which is what stops the cell publishing a drop zone — a second target for an id the
real container on home already answers for would outrank it at `z = 1`.

**Only when the container has something in it.** The screen's order argues that adding comes first because an
empty container is what it is most often opened on, and that is exactly when a preview is a large picture of a "+"
the row below already offers.

**Revised 2026-08-25 — pinned, punched through, and drawn at true scale.** The slice as first built drew the cell at
a size of its own (55% of the width, square) above a scrolling list, and it was wrong in three ways that only showed
on a device:

- **It scrolled away.** Every control under it changes how the container looks, so a preview that leaves the screen
  while a slider is being dragged cannot be judged while it is being used — the one moment it has anything to say.
  It is now a sibling of the list rather than an item in it, beside the toolbar, which is the whole of "pinned".
- **It was not a scale model, it was a different container.** Two things inside a container are absolute rather than
  proportional — the icon gap is a flat 8dp and an icon is capped at the user's `maxIconDp` — so a 148dp box gave
  its icons a *larger* share of their slots (the cap stopped binding) and its gaps a smaller one, and `gridSlots`
  chose a different column count outright, reading the box's own aspect ratio. Drawn "at the size it lands" it
  agreed with nothing.

  The fix is to lay the cell out at its **real dp footprint** and scale the finished drawing (`graphicsLayer`,
  `requiredSize`), so panel corner, gaps, icons and arrangement shrink together and the whole difference is one
  scale factor. The footprint comes from `rememberContainerFootprint`, which asks the surface's own functions —
  `homeZoneArea` (extracted from `rememberHomePagerLayout` for this second caller), the blueprint's `fitGridConfig`,
  and `GridArea.footprintOf`. It also carries the zone's `IconMetrics`, because a settings screen's ambient
  `maxIconDp` is not home's and that alone would have kept the icons wrong. The span is the container's **live
  placement**, not the 2×2 it landed with, so a resized container previews as the shape it is.
- **It sat on a settings background.** A container is a frosted panel whose entire appearance is a function of what
  it is over, so the screen is now a `PunchThroughLayer` and the preview a `punchThroughHole` — the recipe the icon
  sizing preview already used, moved to `core:designsystem` when this became its second consumer.

Two things about the screen around it changed with the same commit and for the same reason — the space the preview
needs had to come from somewhere: the **title moved into a pinned toolbar** and the description paragraph was
deleted outright (a screen reached by pressing *Settings* on a container does not have to say what a container is),
and the list's system-bar padding became **layout** padding rather than content padding. That last one is a stated
departure from the launcher's own rule: a surface pads its content so rows pass under the bars because the wallpaper
is behind them, but this is a solid-background list whose bottom row is a *slider*, and a slider under the
navigation bar cannot be dragged by the finger the bar is taking.

The screen also never opened a `LauncherTheme` — the only destination in `LauncherNavHost` that did not — so
`MaterialTheme` fell through to M3's baseline and every stock component on it was purple. It was silent because
`LocalMorphicColors` has a `MorphicColors.Dark` **default**, so the explicit reads all resolved to something
plausible while the bridged ones did not.

### 3g. Slice G — per-container icon size and spacing ✅

`icon_container` gained `iconScalePercent` and `spacingScalePercent` (default 100), carried through
`IconContainerEntity` → `iconContainersOf` → `IconContainer`, written by `SetIconContainerScales`. Two
`MorphicSliderRow`s under the arrangement, over the slice-F preview, which follows them **while they are dragged**
(`onPreview`) rather than only on release — a slider that commits on release would otherwise leave the tile still
through the whole gesture and jump at the end, which is the one moment the control has anything to say.

**DB v5 → v6, and the layout is wiped**, since the builder still falls back to a destructive migration. Confirmed
on the emulator: the home screen came back re-seeded. Pre-launcher, so the cost is a dev database.

The hit-test in `HomePagerSurface` takes the spacing scale too. It has to: `iconContainerSlots` is the shared
derivation precisely because a second copy of that call would put the touch targets a few dp from the picture, and
a per-container gap is exactly the sort of thing that would have made them drift.

**The background toggle is not taken.** It was listed here as optional, and `containerPanel` has three consumers,
one of them the floating drag proxy — a per-container toggle has to reach the proxy or a picked-up container
changes appearance mid-drag. Worth doing deliberately rather than as a rider on this.

---

## 6. Part two — the arrangement grows parameters of its own

**Planned, not started.** Part one gave a container four shapes and left each of them with no settings. The shapes
are good; what a user cannot do is say *how* a shape should place their icons, and for three of the four there is
nothing to say it with.

### 6a. Why the enum has to split

`IconArrangement` is one enum doing two jobs — **which shape**, and **which variant of that shape** — and every
place that hurts is already visible in part one:

- The picker cannot offer the enum. `PickableArrangements` exists solely to filter seven values down to the four
  that are actually *shapes*, and §3e had to write a paragraph explaining that the fan's corner is not a shape.
- The fan cannot grow. Four corners are four values; adding the four edge anchors below would make eight, and the
  picker's filter would then hide seven of them.
- The grid cannot be parameterised at all. A column count is not an enum value, and no amount of naming makes it
  one.

So: **a shape, and the parameters that shape has.** Not one flat vocabulary of every combination.

### 6b. The model — a sealed shape, stored as one blob

```kotlin
@Serializable
sealed interface IconArrangement {
    @Serializable @SerialName("grid")    data class Grid(val fill: GridFill = GridFill.Auto) : IconArrangement
    @Serializable @SerialName("circle")  data class Circle(val startDegrees: Int = 0) : IconArrangement
    @Serializable @SerialName("beehive") data class Beehive(val orientation: HexOrientation = POINTY) : IconArrangement
    @Serializable @SerialName("fan")     data class Fan(val anchor: FanAnchor = TOP_LEFT) : IconArrangement
}
```

Sealed rather than an enum plus four nullable columns, for the reason `HomeLayout` is one type and
`ContainerSettings` is sealed: it makes **a column count on a circle unrepresentable**, rather than a field that is
merely ignored there. A shape's parameters travel with the shape or they drift from it.

**Stored as one serialized blob in a renamed column**, not as flat columns. That is the icon-layer lesson taken
rather than relearned — CLAUDE.md records that L1 burned four destructive DB bumps discovering that a per-item
recipe belongs in a blob — and the rename is the standing rule that *a key's name is the seam for a semantic
break*: `arrangement` holds an enum name today, and re-interpreting that column in place fails silently. It becomes
`arrangementSpec`, `IconArrangementConverter` serializes instead of calling `.name`, and the DB goes to **v7**
(destructive, as v6 was; pre-release, so the cost is a dev database).

`@SerialName`s are short and explicit for `GridItem`'s reason one file over: this reaches a user's stored blob, so
the discriminator must not be a class name that a refactor could move.

### 6c. Grid — how many rows, or how many columns

Three settings, one of them at a time:

- **Auto** — today's `sqrt(count × aspect)` derivation. Stays the default, so an unconfigured container is
  unchanged.
- **Rows = n** — the container has `n` rows. The list fills them and the **columns** grow.
- **Columns = n** — the container has `n` columns. The list fills them and the **rows** grow.

**The list is unbounded and the icons take the strain**, which is the rule every other shape here already follows:
a container appends to the end of its list, its own bounds are the list's bounds, the arrangement places the whole
list inside them, and the icons scale down until it fits. That is why 33 icons in a 2×2 container is a legal
picture, and why `rows = 1` with two hundred icons is a very thin dock rather than an error. There is no capacity
anywhere in this, and nothing is ever refused.

#### The fill is reading order, always; the setting only moves where the wrap comes from

Left to right, then down — in all three modes. **List order is reading order**, so the seventh icon is the seventh
thing you look at, whatever the setting is. That matters more here than anywhere else, because reordering is a drag
onto a *position*: an order the eye cannot follow is one the finger cannot aim at.

What the setting changes is only where the wrapping count comes from:

| Setting | Columns used | The container grows |
|---|---|---|
| `Auto` | `sqrt(count × aspect)` | either way, as the count decides |
| `Columns = n` | `n` | **downward** — new rows appear at the bottom |
| `Rows = n` | `ceil(count / n)` | **rightward** — new columns appear at the right |

That last column is the whole of what the two settings mean to a user: pin the rows and the container extends
sideways, pin the columns and it extends down. `rows = 1` therefore appends at the right-hand end of the strip,
which is what a dock does.

**A correction to an earlier draft of this section**, which claimed a pinned row count *forced* a column-major fill
because row-major would need the derived column count to wrap against. It would, and that count is available:
`ceil(count / rows)` is known before any slot is placed. Both orders were always computable, so the direction was a
real choice rather than a consequence — and reading order is the choice.

**The pinned count divides its axis even when there are too few icons to fill it.** Three rows with two icons is one
column of two, laid out on a three-row division, not a two-row one. Otherwise the icons would resize on every add
until the container filled up, which is the opposite of what pinning an axis is for.

Implementation is smaller than the discussion: `gridSlots`' fill expressions — `row = i / cols`, `col = i % cols` —
do not change at all. Only the provenance of `cols` does, and slice A's centred short last row keeps working
because it was written against `cols` rather than against the derivation.

#### Rows and columns are not two dimensions of a frame

There is no R × C in this container and no cell count to fill. They are the same setting read from either axis, and
exactly one of them is ever set — which is what the one-of records. A `Grid(rows, columns)` pair would not be a
laxer version of it, it would be the wrong shape: it invites a fixed frame with a capacity, which is precisely what
the paragraph above says this is not.

```kotlin
@Serializable @SerialName("grid")
data class Grid(val fill: GridFill = GridFill.Auto) : IconArrangement

@Serializable sealed interface GridFill {
    @Serializable @SerialName("auto")    data object Auto : GridFill
    @Serializable @SerialName("columns") data class Columns(val count: Int) : GridFill
    @Serializable @SerialName("rows")    data class Rows(val count: Int) : GridFill
}
```

The screen still shows the two controls the user thinks in — **Rows** and **Columns**, each *Auto* or a number —
and setting one returns the other to Auto.

#### The dock, and why it is already reachable

`rows = 1` in a wide, short container is the macOS-dock shape: one strip across the bottom of a tablet, with the
container's own frosted panel as the dock's background.

Nothing in the geometry needs adding. Slice A already made the grid cell `min(availW/cols, availH/rows)` and centred
the block, so a wide strip with one row gives icons as tall as the strip, centred, until the count is high enough
that the width binds and they shrink. With `rows = 1` the two fill orders coincide — every column holds one icon —
so the dock is the one case that never depended on the question above.

And the shape can be dragged out today: a container's resize floor is `cellMultiplier` on *each* axis — one visual
cell, not the 2×2 it is created at — verified in `HomeResizeRules.Container`. Worth knowing, because this whole
case would be theoretical if it were not.

**This reverses §2f**, which declined a column count on the grounds that a pinned one fights the resize handle. The
objection is answered rather than ignored: `Auto` remains the default, so the derivation governs every container
until someone deliberately overrides it — and a user who pins three columns and then widens the container is asking
for three wide columns.

### 6d. Fan — one anchor, and the sweep follows from it

Eight anchors, not four: the four **corners** and the four **edge midpoints**.

```
TOP_LEFT      TOP        TOP_RIGHT
LEFT                     RIGHT
BOTTOM_LEFT   BOTTOM     BOTTOM_RIGHT
```

A corner sweeps a **quarter** circle, an edge sweeps a **half** — which is the half-circle shape being asked for,
arrived at without a second control. The sweep is implied by the kind of anchor, so "a corner with a 180° sweep"
cannot be expressed and there is no pair of settings to keep in step. `fanSlots` already takes its corner as two
booleans decomposed from the enum; it takes an anchor and a sweep angle derived from it instead, and the arc maths
from slice A is unchanged — only the range the angles span.

**A centre anchor with a full sweep is deliberately not added**, even though it would complete the table: that is
the circle, and it is not the same circle. `Circle` is a *single* ring whose radius solves the chord so neighbours
sit one pitch apart; the fan is *nested* arcs at a fixed radial pitch. They coincide at low counts and diverge
completely at high ones, so folding them together would mean one of the two laws quietly losing.

### 6e. Beehive — orientation, not rotation

A hex lattice has six-fold symmetry, so a free angle is sixty distinct degrees pretending to be three hundred and
sixty. The rotation that changes the picture is **30°**: pointy-top becomes flat-top. Two values, named for what
they are.

That is a better control than a slider would be, and cheaper: `beehiveSlots` projects axial coordinates through a
pointy-top formula, and flat-top is the same projection with its two terms exchanged.

### 6f. Circle — a start angle, and an honest note about it

Offered last, or not at all. With `n` icons the ring has `n`-fold symmetry, so rotating by a whole step only
permutes *which* icon sits at twelve o'clock — which reordering already does, better. Only a fraction of a step
changes the shape, and only where `n` is small enough for the shape to have an orientation: three icons pointing up
versus down is a real choice, twenty is a circle either way.

So it is a real control at n≤6 and inert above it, which is worth knowing before it is built rather than after. If
only one of §6c–§6f gets built, this is the one to drop.

### 6g. The control is two rows, and the dialog goes away

The shape row from §3e stays as it is — four swatches, one per sealed subtype. Under it, **a second row that
belongs to the chosen shape**: the fan's eight anchors, the grid's column counts, the beehive's two orientations.
Empty for a shape with nothing to say, which will be the circle if §6f is dropped.

Both rows go in **both places**: the picker's detail page, where they are already, and the container's settings
screen. In settings this **replaces the `ChooserRow` + `AlertDialog`** entirely. That dialog made sense when the
screen had nothing to show; now there is a live preview pinned above it, and a dialog covers the one thing worth
watching while choosing — which is exactly the argument §3e made for using a row in the picker, applied to the
place the argument is now stronger. Two consumers of one control is what makes extracting it right rather than
speculative.

**The swatch needs no change at all**, and that is the split paying for itself immediately: `IconArrangementSwatch`
takes an arrangement and lays dots out through `iconContainerSlots`, so once the arrangement carries its own
anchor, a fan swatch draws the *right* corner without the swatch knowing what a corner is.

### 6h. What else moves

- `IconArrangement.slots()` keeps its shape: one exhaustive `when`, now over the sealed subtypes, each arm reading
  the parameters it owns. A new shape still fails to compile until it says how it lays out.
- `swatchCount` becomes a property of the sealed type, still exhaustive, still per-shape (§6 does not change the
  numbers: grid 6, circle 8, beehive 7, fan 14).
- `PickableArrangements` is deleted. The shape row is the four subtypes; the variant row is the parameter.
- `ContainerLabels.label` splits: a shape name, and a variant name for the second row.
- `SetIconContainerArrangement` carries the whole value, so it already fits.

### 6i. Slices

- **H — the model split, no new parameters.** `Fan` keeps only its four corners, `Grid.columns` is always null, the
  other two take their defaults. Pure refactor: model, converter, DB v7, `slots()`, swatch, both call sites.
  Nothing about the launcher should look different, and that is how it is verified.
- **I — the two-row control.** The variant row appears and the settings dialog is replaced. With only the fan's
  four corners to show, it is testable before any new geometry exists.
- **J — the grid's fill axis.** `Auto`, or rows or columns pinned to a count. The tablet-dock case is
  the acceptance test: a 4×1 container with `rows = 1` should stay one row as icons are added.
- **K — fan edge anchors.** The half circle; the geometry from §6d.
- **L — beehive orientation.** Flat-top.
- **M — circle start angle.** Optional; see §6f.

**H before everything, and alone.** It is the only slice that touches persistence, and it is behaviour-preserving —
which means it can be verified by *nothing changing*, and any later slice that breaks something has a clean commit
to bisect against. Shipping it together with J or K would confuse a migration bug with a geometry bug, and those
look identical on screen.
