# Landscape Plan — one arrangement key per mode, a reference layout, and two projections

**Goal:** make the launcher correct in landscape on both form factors, and give the user control over whether
landscape is *the portrait layout adjusted* or *a layout of its own*.

> Companion to [GRID_LAYOUT_PLAN.md](GRID_LAYOUT_PLAN.md) (which built the grid this rotates) and
> [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) (`SideZoneEdge`, `CellFit`, `uiInsets`). Module state:
> [STATUS.md](STATUS.md), whose "Deferred: cross-orientation rotate-seeding" note this plan closes.

## What is already built — do not rebuild it

The **geometry** half of landscape landed with the surfaces themselves. Only the **arrangement** half is missing,
and the two are the split `Orientation.kt`'s KDoc already names.

- `DeviceConfiguration` — four values, detected live from the window (`currentDeviceConfiguration()`), read in
  ~15 composables. Rotation does **not** recreate the Activity: `configChanges` covers
  `orientation|screenSize|screenLayout|smallestScreenSize`.
- Every `GridBlueprint` carries a `phoneLandscape` and a `tabletLandscape` default.
- `SideZoneEdge` + `splitForSideZone` + `HomeZoneScaffold` — the dock is a **rail** on the `END` edge in phone
  landscape and a **strip** on `BOTTOM` everywhere else; the widget area is `START` / `TOP` the same way. Tested
  (`SideZoneSplitTest`).
- `SurfaceMetrics` — grid size, icon sizing, extent, row height and horizontal padding all stored **per
  `DeviceConfiguration`**, with tests covering the landscape keys.
- `AppCollectionSizing` — the folder overlay is sized per configuration, both landscapes included.
- Settings: two-pane on tablet; `SurfaceDetail` and `IconsDetail` have genuine landscape *arrangements* (not
  narrower portrait ones); `GridEditor` previews per configuration and draws its companion zone on the side it
  really occupies (`CompanionSide`).
- Wallpaper: the rotating pair is per `Orientation`, and `ShellViewModel` already takes a live orientation report.
- **Persistence is already per-orientation end to end** — `LayoutRepository.apply(orientation, …)`,
  `placements(orientation)`, and two saved `apps_pager_item` lists with per-orientation unique indices.

## What is actually missing

**The six pinned constants and the gate are gone** — L1 removed them, and every surface now derives its key from
the reported `DeviceConfiguration`. What remains:

- **L3c** — grid coupling and the rotate-in-place projection. The last of the arrangement work; L3d landed the
  keys they are defined against.
- **L4** — the unaudited UI tail: sheets, studios, pickers, and the settings panes that never got a landscape pass.

## The model

### The key: `ArrangementKey`, eight values

The placement column stops being `orientation` and becomes `arrangement`. Eight values, in `core:model` beside
`DeviceConfiguration`:

```
PHONE_PORTRAIT   PHONE_LANDSCAPE     PHONE_PORTRAIT_LINKED   PHONE_LANDSCAPE_LINKED
TABLET_PORTRAIT  TABLET_LANDSCAPE    TABLET_PORTRAIT_LINKED  TABLET_LANDSCAPE_LINKED
```

Three axes, all of them real: **form factor** × **orientation** × **mode**. The first four are the layouts a
posture owns while it is being arranged on its own; the `*_LINKED` four are the layouts of the same eight
postures while the two orientations are kept in step.

**The mode is an axis, not an overlay, and that is the whole change.** An earlier cut had six keys, with the two
`*_SHARED` ones holding a reference snapshot while sharing wrote through the *authored* keys. That made the two
modes share storage, so every flip of the switch had to decide which layout survived — and every answer destroyed
one. See "Rejected" for the table of what each answer cost. Separating the modes means the toggle stops being a
merge and becomes a **view switch**: each pair keeps sitting where it was, and flipping back shows it again.

**`*_LINKED` rather than `*_SHARED`.** "Shared" reads as one row-set two postures read, which is exactly what
these are not — they are two row-sets kept in step with each other. The name has to say the second thing, or the
next reader looks for the single row-set that does not exist.

**Landscape needs its own row-set in *both* modes, which is why this is eight and not six.** A linked landscape
still has to hold real coordinates: the surface draws from them and a drop lands in them. Deriving landscape at
render time was rejected earlier for exactly that reason, and it is why the linked landscape previously had to
squat on the authored `PHONE_LANDSCAPE` key. That squatting was the collision.

**Renamed rather than re-interpreted**, per the standing rule that a semantic break takes a new name — the old
column meant "which orientation", the new one means "which arrangement", and re-reading one as the other would
fail silently. `fallbackToDestructiveMigration(dropAllTables = true)` is already set, so the bump (2 → 3) costs
nothing and there is no migration to write. Affects the five `*_placement` tables and `apps_pager_item` (whose
per-arrangement unique indices simply gain values).

**`Orientation` survives, scoped to wallpaper.** The rotating pair is genuinely two images per orientation and
has nothing to do with arrangements. `Orientation` is `data:wallpaper`'s type and nothing else's — which is what
stops it drifting back into being a second, weaker arrangement key.

### One policy, and the form factors are simply separate

`independentLayout` picks which **pair** of keys every surface reads and writes. That is its whole job.

| `independentLayout` | Keys in play on a phone | Kept in step |
|---|---|---|
| off (default) | `PHONE_PORTRAIT_LINKED`, `PHONE_LANDSCAPE_LINKED` | yes, with each other |
| on | `PHONE_PORTRAIT`, `PHONE_LANDSCAPE` | no |

Default **off**: a fresh install has one layout, and rotating shows it re-arranged to fit.

**`independentFormFactor` is dropped rather than deferred.** Under this model a phone and a tablet already own
separate keys in both modes — there is no pair holding them together, so there is nothing for the toggle to turn
off. Keeping them in step would need a *third* mode and four more keys, to answer a question nobody asked: the
earlier note deferring it said it "needs a decision, not because it is large", and the decision is that a
foldable's two form factors are two screens and are arranged as such.

### The reference is portrait, within a pair

Stated by the user and load-bearing: *"we use portrait as our main reference layout"*. While the linked pair is in
use, `*_PORTRAIT_LINKED` is the reference: it is held in portrait terms, projected into `*_LANDSCAPE_LINKED` for
drawing, and a drag performed in linked landscape is projected **back** into it.

This is unchanged machinery — `ArrangementProjection`, `copyArrangement`, the unconditional re-derive on entry and
the write-back on edit all keep working, pointed at a different pair. **That is the strongest argument for the
model: it is a re-keying, not a rewrite.**

### The toggle copies nothing, and asks nothing

Switching `independentLayout` changes which pair is read. Neither pair is written, neither is cleared, and there
is no question to put to the user — so the merge chooser goes, and with it `IndependenceMerge`,
`snapshotArrangement`, `referenceSnapshot`, and the orphaned-folder consequence that came out of naming a winner.

One copy remains, and it is a seed rather than a merge: **an independent pair with nothing in it is seeded once
from the linked pair** (`copyArrangementIfEmpty`, which exists). Otherwise the first flip of the switch would show
the alphabet instead of the layout the user was just looking at. It is non-destructive by construction — it only
ever writes into a key holding nothing — and it runs in one direction, because the linked pair always has
something and never needs seeding.

**There is deliberately no way to promote one mode's layout into the other.** "Make my independent landscape the
shared one" was what the old chooser's *Landscape* answer did, and it is the answer that overwrote a layout the
user did not name. If it turns out to be missed it comes back as an explicit action with a visible name, not as a
side effect of a switch.

## The projection

Two modes. Both take a source arrangement and a target `GridConfig`; both are pure and belong in `data:layout`
beside `GridReflow` and `FreeGridPlanner`, with no Room and no Compose in them.

### Reflow (default)

Flatten the source by `(page, row, col)` and re-lay it row-major, densely, into the target grid — the same idea as
`HomeListRepository.seedIfEmpty(readingOrder(...))`, which is the precedent in the tree. Fills the wider landscape
grid; gaps do not survive. Overflow cascades to the next page.

**Write-back is order-only.** A landscape drag re-lays the reference densely in the new reading order, so the
reference's gaps go. Defensible — reflow mode already means "one arrangement, re-flowed" — and the alternative is
refusing landscape drags, which is worse. It is a real loss and the doc says so.

### Rotate in place

The device turned 90°: the whole board rotates with it, items keeping their physical position on the glass.
`(row, col, rowSpan, colSpan)` → `(cols − col − colSpan, row, colSpan, rowSpan)`. L1's `GridRect.rotateForLandscape`
was **already ported** into `core:model` as `GridPlacement.rotateForLandscape` (with `rotateForPortrait` and
`GridConfig.swap()` beside it) and has had no consumer since; this mode is the one it was waiting for.

- **Bijective, so it round-trips exactly** — a landscape drag projects back into the reference with no loss, gaps
  and spans included. That is the whole reason this mode is worth having.
- **Direction is counter-clockwise, fixed.** Both physical directions produce landscape, but only one stored
  arrangement exists, so the transform picks one. CCW is the one that carries the bottom edge to the trailing
  edge, which is where `SideZoneEdge` puts the rail. Visible consequence: the dock strip read left-to-right
  becomes the rail read **bottom-to-top**. That is what physically turning the device does, which is what the
  option promises.
- **L1 clamped widgets and containers instead of rotating them** because its grids were not coupled, so a
  transposed span could land outside the target. Coupling (below) removes that case, and every item kind rotates
  the same way here — one transform, not two.

### Availability: exactly when the side zone changes axis

```
sideZoneEdge(portrait).isStrip != sideZoneEdge(landscape).isStrip
```

True on **phone** (dock `4×1` strip → `1×4` rail; widget area `4×3` `TOP` → `3×4` `START`), false on **tablet**
(dock stays a `5×1` `BOTTOM` strip; widget area stays `TOP`). On a tablet the launcher deliberately does *not*
rotate the board — it re-anchors the dock to the bottom — so board rotation has no meaning there and would
rotate the main area over a dock that did not move. Tablet sync therefore uses reflow.

Derived from `sideZoneEdge` rather than tested as `isTablet`, so the rule cannot drift from the thing it
describes.

### Rotate in place requires coupled grids, and is only offered on the linked pair

The transform is a bijection **only** when the target grid is the source's transpose: `4×6` portrait ↔ `6×4`
landscape. So while the **linked** pair is in use the grid sizes are coupled — editing one orientation's counts
transposes the other's — and rotate-in-place becomes available. On the **independent** pair each posture owns its
grid size freely (`4×6` portrait against `5×8` landscape is legitimate), no transpose exists, and rotate-in-place
is therefore **absent**, not disabled. Coupling is tied to the linked pair rather than merely to the mode picker,
per the user's answer: coupled whenever the two are kept in step.

Coupling is not the fight with the dock rail it first looks like: the phone blueprints are *already* transposes
(`DockGrid`'s own comment says so). What coupling costs is landscape density — phone landscape's blueprint
default is `6×4` (24 cells) where portrait's transpose is `5×4` (20). The linked pair trades those four cells for
an exact round-trip; the independent pair gets them back.

**Open, and it is L3c's to answer: a grid size is not keyed by mode.** `SurfaceMetrics` stores counts per
`DeviceConfiguration`, which has four values and no notion of linked-versus-independent — so coupling applied
while linked leaves the transposed count in place after the switch, and the independent landscape inherits `5×4`
rather than returning to its `6×4` default. The eight-key model does not fix this, because the key it added is
for *arrangements* and a grid size is not one. Either the metric gains a mode axis, or coupling writes only while
linked and the independent pair keeps whatever it last had. Do not build coupling before picking one.

## The settings

A new **Orientation** group. Three controls:

- **Rotation** — `AUTO` (follow the device, default) / `PORTRAIT` / `LANDSCAPE`, the latter two locking via
  `activity.requestedOrientation`. L1's only use of that API was pinning its crop screen; L2 letterboxes there
  instead, so this is the first real consumer.
- **Independent layout** (default off) — switches which pair of keys the surfaces read. **No dialog either way**,
  which is the visible half of the eight-key model: there is nothing to merge, so there is nothing to ask.
- **Sync mode** — Reflow / Rotate in place. Shown only while `independentLayout` is off *and* the side zone
  changes axis on this form factor. Absent, not disabled, both times.

**Independent foldable setup is gone**, per "One policy, and the form factors are simply separate".

## Phases

Each phase ends verified **on the device**, not on a green build.

### L1 — Re-key and unpin

- [x] `ArrangementKey` in `core:model`; `Orientation` narrowed to the wallpaper's use.
- [x] Rename the column on the five `*_placement` tables and `apps_pager_item`; bump the DB version (1 → 2).
- [x] `LayoutRepository` / `AppsOrderRepository` signatures take `ArrangementKey`.
- [x] Delete the `ORIENTATION` constants — **six** sites, not five; each ViewModel derives its key from the
      reported `DeviceConfiguration` (always the authored key, no sharing yet).
- [x] Delete `drawsStoredPlacements` and its two guards.
- [x] **Verified on device** (2026-09-09, Pixel Fold emulator): each posture writes its own key, and a drag made in
      one lands only under that key. **The A–Z clause is stale and was not observable** — L2's seed and L3b's
      re-derive both run ahead of `seedIfEmpty`, so the alphabet is the third fallback and a populated home never
      reaches it. Checking L1 in isolation would mean reverting two slices.
- [ ] **Still unverified:** the dock rail accepting drops, and `fitDockTo` running where `drawsStoredPlacements`
      used to refuse. The seed fills `MAIN` only, so the rail was empty throughout and nothing exercised it.

### L2 — The projection

- [x] `ArrangementProjection` in `data:layout` — **reflow only**, pure, 11 unit tests. Rotate-in-place moved to L3;
      see the progress note.
- [x] Seed an empty authored key from its portrait counterpart on first use — HOME (every zone) and the APPS pager.
- [x] **Verified on device** (2026-09-09) for HOME `MAIN`: portrait's reading order arrives in landscape intact,
      re-laid densely into the wider grid and re-aligned to whole cells. Pinned by making portrait's order differ
      from the alphabet — the app the seed would have placed *first* came back **last**.
- [ ] **Still unverified:** the dock and widget-area zones, multi-cell widgets, folders, and the APPS pager — the
      test home held apps in `MAIN` only.

### L3 — Reference, policy, settings

**Split into three, because it is three ideas and one commit cannot hold them.** L3a first so the settings group
L3b's toggles need already exists.

#### L3a — the rotation lock

- [x] `RotationMode` in `core:model`; `OrientationSettings` slice in `data:settings`.
- [x] A new **Orientation** settings section, in the Layout group beside the screen manager.
- [x] `MainActivity` follows the stored mode via `requestedOrientation`.
- [x] **Verified on device** (2026-09-09): choosing "Landscape" from inside the Orientation pane turned **that pane**,
      which is the half worth checking — the lock lives on the Activity precisely so settings obeys it too.
- [ ] **Still unverified:** a lock set while the launcher is backgrounded being in force when it returns, and
      `AUTO` declining to rotate while the system's own auto-rotate is off.

#### L3b — the sharing policy (built on six keys; **superseded by L3d**)

Kept as the record of what was built and what the device said, because L3d changes the keys and not the machinery
— every line below that is not about `*_SHARED` survives the re-key.

- [x] `*_SHARED` keys wired — as **snapshots**, not as continuously-maintained references; see the progress note.
      **L3d deletes these**: the snapshot exists only to answer a chooser that stops being asked.
- [x] The `independentLayout` toggle, its snapshot-on-enable, and the turn-off chooser
      (portrait / landscape / neither). **The chooser goes in L3d.**
- [x] Write-back from a non-reference posture, and the unconditional re-derive on entry that pairs with it.
      **Kept** — L3d points the same two hooks at the linked pair.
- [x] **The default path verified on device** (2026-09-09): a landscape drag wrote back into portrait *on the drop*
      rather than on the next rotation, the re-derive carried a portrait edit into landscape, and three rotations
      either way produced no drift — the no-op guard holds.
- [x] **Independence holds a posture apart for removals**, verified after the fixes below: removing in landscape
      leaves portrait whole, and with the two kept in step it reaches portrait without resurrecting on the next
      rotation. A folder made in landscape under independence leaves portrait untouched.
- [~] `independentFormFactor` — **dropped**, not deferred; see "One policy, and the form factors are simply
      separate".
- [~] The KEEP_PORTRAIT / KEEP_LANDSCAPE chooser answers were never verified, and now never will be — L3d removes
      them.

#### L3d — eight keys, and the toggle stops being a merge

The model change, and it was a **re-keying rather than a rewrite**: the projection, the re-derive and the
write-back were untouched and simply address a different pair.

- [x] `ArrangementKey` gains the four `*_LINKED` values; DB version 2 → 3 (destructive, no migration to write).
- [x] Key resolution is `DeviceConfiguration.arrangementKey(independentLayout)` in `core:model`, and every surface
      reads its pair through it — six call sites. Three of them (`ContainerSettings`, `Dock`, `GridSize`) had to
      start reading the flag, since a posture alone no longer names an arrangement.
- [x] `portraitOfPair` replaces `portraitOfFormFactor` and answers **within the asking key's own mode** — the one
      place the two pairs could still have met. `ArrangementKeyTest` pins it, along with "the two modes never
      resolve to the same key" and "every key is reachable", because none of these fail loudly.
- [x] Deleted: `IndependenceMerge`, `IndependenceMergePicker`, `snapshotArrangement`, `referenceSnapshot`,
      `oppositeOrientation`, `portraitCounterpart`, and `disableIndependentLayout`'s three branches.
      `OrientationViewModel` went from three dependencies to one.
- [x] Seeding an empty independent arrangement is `mirrorArrangementIfEmpty` — the old `snapshotArrangement` with a
      new job and an emptiness guard. **Verbatim, not projected**: the source is the same posture in the other mode,
      so it is the same grid, and re-laying would close the gaps being carried over.
- [x] The APPS pager follows the same eight keys, with the same two-step seed (its linked twin, then its pair's
      portrait) — trivial there, since an ordered store has no verbatim/projected distinction.
- [x] **Verified on device** (2026-09-09, Pixel Fold emulator): arrange linked portrait → rotate → switch to
      independent → edit independent landscape → switch back → rotate. **All four keys diff clean against their
      pre-switch rows**, and the mirror landed byte-identical rather than re-laid. No dialog appears in either
      direction. The linked write-back still carries a landscape removal into linked portrait, and the portrait
      surface draws the independent pair's sixteen apps where the linked pair holds fifteen — so the read side
      picks the right pair, not just the write side.

#### L3c — coupling and the second mode

**After L3d**, since coupling is defined against the linked pair.

- [ ] Decide where a coupled grid size is stored — see the open note under "Rotate in place requires coupled
      grids". Do not build coupling first.
- [ ] Grid-size coupling while the linked pair is in use.
- [ ] Rotate-in-place, on `GridPlacement.rotateForLandscape`, offered only where the side zone changes axis.
- [ ] **Verify on device:** editing either orientation's counts transposes the other's while linked and does not
      while independent; a board rotation round-trips a phone layout exactly, gaps and spans included.

### L4 — The UI tail

Audit, then fix. Known suspects:

- [ ] `BottomSheet` at `SheetHeightFraction = 0.7f` — 70% of a short screen. Affects the widget picker, the app
      selection sheet and the gesture sheet.
- [ ] `IconStudioScreen`, `StudioFinalizeScreen`, `PackDrawablePicker`, `StudioColorPicker` — no landscape branch.
- [ ] `WallpaperStudioScreen`, `WallpaperCropScreen`, `WallpaperCaptureScreen` — no landscape branch.
- [ ] `ContainerSettingsScreen`, `GestureActionScreen`, `ArrangementPicker`.
- [ ] The settings panes with no landscape pass: `EffectsDetail`, `ExtrasDetail`, `SurfaceRegisterDetail`,
      `WallpaperDetail`.
- [ ] Cancel an in-flight drag on a configuration change — `configChanges` means no recreation, so a drag
      currently survives a rotate holding geometry for a window that no longer exists.
- [ ] `uiInsets` under a landscape cutout on every surface (the notch moves to a long edge).

## Progress

### L1 — code complete 2026-09-09, awaiting device verification

`gradle check` green (ktlint, detekt, 1057 unit tests, 0 failures); `:app:assembleDebug` green. Room exported
`schemas/…/2.json` with all six tables re-keyed and `1.json` untouched.

Eight things came out differently from the plan above. Each is a decision, not a slip:

- **`Orientation` stayed in `core:model`.** The plan said "narrowed to `data:wallpaper`", meaning the module.
  Moving it there would put a plain domain shape inside a data module, which inverts the architecture — so what
  narrowed is its *scope*, not its address: only wallpaper code names it now, and the
  `DeviceConfiguration.orientation` bridge is deleted (it had exactly one consumer, `drawsStoredPlacements`).
  Its KDoc says so, because the next person to need a per-posture key will reach for this enum first.
- **There were six pins, not five.** `ContainerSettingsViewModel` reached across features for
  `HomeViewModel.ORIENTATION`. It now takes a device report of its own — needed for a *read*, not bookkeeping:
  the footprint preview shows where the container sits, which differs per posture.
- **`RemoveFromGrid` ignores the arrangement key**, and `ShellViewModel`'s comment claimed the opposite ("a
  removal has to name the same tables the placement did"). It deletes an app's placement across *every* key and
  destroys a folder or container outright. Left as-is for L1 and documented; whether removing in landscape should
  remove in portrait is an L3 policy question, not a re-keying one.
- **`drawsStoredPlacements` needed a replacement, and the replacement is not a gate.** The optimistic `placements`
  map belongs to one key, so a rotation must not leave the previous posture's items in it — `fitMainTo`/`fitDockTo`
  would reflow *those* and write the result under the *new* key. Clearing the map where the collector switches
  arrangements removes the hazard at the source: a reflow of nothing reports no change and writes nothing.
- **`AppsViewModel` needed the fit to carry its device.** The device and the measured page capacity arrive as two
  separate UI reports, and the capacity's lags — it waits on the new posture's stored grid. For those frames the
  key had moved and the capacity had not, and pagination *writes*, so the pair would have re-slotted the new
  posture's list at the old one's page size. `PagerFit` now carries the device it was measured for, and
  `fittedPager` turns the disagreement window into a null instead of a plausible wrong number.
- **Landscape is A–Z seeded on first rotate, not blank.** `HomeViewModel.seedIfEmpty` is per-arrangement, so it
  simply does its first-run job again for the new key. Better than a blank screen, and it is the thing L2's
  projection has to take over from rather than race. Only `HomeZone.MAIN` is seeded, so the landscape dock rail
  comes up empty.
- **Renaming invalidated three detekt baseline entries**, because those entries are keyed on the offending
  element's source text — two `MaxLineLength`s on `@Query` strings and `applyChange`'s `CyclomaticComplexMethod`.
  The two long queries were **fixed** and their entries deleted (emptying `core:database`'s baseline); a 13-op
  `when` at complexity 35 is not a re-keying job, so that one was **re-baselined** under its new signature. Worth
  knowing for L2–L4: any rename in this codebase will invalidate baseline entries in proportion to how much
  source text it touches, and each one is a fix-or-re-baseline decision rather than noise.
- **`ContainerSettingsContent` was already at detekt's `LongMethod` limit**, and two lines of device plumbing put
  it over. Resolved by moving the holder acquisition *and* the device report up to `ContainerSettingsScreen` and
  passing the holder down — which leaves the content function shorter than it started and gives the wrapper an
  honest job ("wire the holder, then theme the content") rather than dodging the threshold.

### L2 — code complete 2026-09-09, awaiting device verification

`gradle check` green (1068 unit tests, 11 of them new, 0 failures); `:app:assembleDebug` green.

- **Rotate-in-place moved to L3, and this is a scope change rather than a slip.** The plan had L2 build both
  modes, but rotate-in-place is a bijection *only* against a transposed grid, and grid coupling is L3's — so built
  here it would have had no reachable caller and no way to be exercised outside a unit test. Its primitive is
  already waiting: `GridPlacement.rotateForLandscape`/`rotateForPortrait` and `GridConfig.swap()` are in
  `core:model` today, ported from L1 with **no consumer at all**. L3 is where they get one.
- **The projection could not be `GridReflow`, and that is the finding worth keeping.** `GridReflow.reflow` is a
  *settle*: whatever still fits keeps its exact cell. Portrait 4 cols settled into landscape 6 would leave every
  item in columns 0–3 with the gained columns empty. `ArrangementProjection.project` re-lays everything in reading
  order instead, and `a wider target uses the columns it gained` is the test that pins the difference.
- **It also could not use `GridOccupancy`'s scan**, which steps one *logical* cell. HOME's grids are
  `cellMultiplier = 2`, so a dense re-lay would seat items half a cell out — reliably, since every item goes
  through the scan, where `GridReflow` only sends it strays whose hinted coordinate was already aligned. The
  projection walks the grid in visual cells and uses `GridOccupancy` as the free-cell index only. That latent
  misalignment still exists in `GridReflow.rehome`; it is just very hard to reach.
- **The APPS pager needed seeding too, and got it almost free.** Without it, rotating threw away the user's pager
  arrangement and `syncPager` refilled the list A–Z. An ordered surface makes the projection trivial — same order,
  divided by the new page capacity, which `normalizePages` already does — so it is `seedPagerIfEmpty` on the
  repository. It runs **before** `syncPager` rather than instead of it, so a newly installed app is still appended
  to the seeded list.
- **Seeding is driven by `HomeZone.entries` with `getValue`, not by iterating the config map.** Iterating the map
  would silently drop the items of any zone missing from it; `getValue` fails loudly at the one place that could
  be wrong. That also removed an unreachable `moves.isEmpty()` guard detekt's `ReturnCount` had flagged — every
  source item has a zone, so a non-empty source always yields moves.
- **Neither seed overwrites an existing arrangement**, which is what makes both safe to call on every
  configuration change rather than exactly once, and what stops a rotation undoing work.

### L3a — code complete 2026-09-09, awaiting device verification

`gradle check` green (1068 unit tests, 0 failures); `:app:assembleDebug` green. No new tests: the slice is one
enum field and the behaviour that matters is a platform call.

- **The lock is applied in `MainActivity`, not in a composable.** `requestedOrientation` belongs to the Activity,
  and a launcher's window outlives any one surface — settings has to obey the lock exactly as HOME does, and both
  live inside this Activity.
- **Collected for the Activity's whole lifetime rather than only while started.** A rotation request made while
  the launcher is backgrounded is precisely the one that must be in force *before* it is next shown; deferring to
  `STARTED` would let it come back in the orientation it was locked out of and turn afterwards.
- **`AUTO` maps to `SCREEN_ORIENTATION_UNSPECIFIED`, not `USER` or `SENSOR`.** It means "ask for nothing", so the
  device's own rotation setting decides — including the user having auto-rotate off system-wide, which is not a
  launcher preference's to override.
- **Three peers rather than a switch plus a hidden chooser.** A user who wants landscape has not turned something
  on; they have picked one of three, and a segmented row says that.
- **Adding a section pushed `SettingsSection.meta` onto detekt's complexity bound**, so the two rows that read
  HOME's pairing (`HOME_GRID`, `DOCK`) became named functions of their own. That was worth doing regardless: their
  inline branches made a lookup table of eleven entries read as a function with logic in it.

### L3b — code complete 2026-09-09, verified, and **superseded by L3d**

The six-key model this describes is replaced by the eight-key one above; the machinery it built is not.
Read it for why the write-back and the re-derive are shaped as they are — those keep working, pointed at
the linked pair. The `*_SHARED` snapshot and the merge chooser are what L3d deletes, and the first bullet
below is the argument that turned out to be wrong: a third row-set was not "one more thing to keep in step
for no gain", it was the mode axis, and folding it into the authored keys is what made every toggle
destructive.

`gradle check` green (1090 unit tests, 8 of them new, 0 failures); `:app:assembleDebug` green.

- **The `*_SHARED` keys are snapshots, not maintained references** — the one real departure from the model above.
  While the postures are kept in step, portrait *is* the reference, so a third row-set holding the same layout
  would be one more thing to keep in step for no gain. `PHONE_SHARED` is written **once**, when independence is
  switched on, and read **once**, if the user later switches it off and picks "neither". That is the whole of its
  job — and it is exactly what makes that third answer mean something rather than being a euphemism for
  "portrait".
- **Sharing changes what is *written*, never what is read.** A surface still reads its own posture's key, so the
  read path is untouched from L1. Being in step is two hooks instead: an unconditional re-derive when a
  non-reference posture is entered, and a write-back into portrait after an edit made away from it. Each is
  useless alone — without the re-derive a portrait edit never reaches landscape; without the write-back the next
  rotation overwrites a landscape edit.
- **The re-derive is unconditional, so it needed a no-op guard.** `copyArrangement` compares against what the
  target already holds and skips the write, or every configuration change would rewrite the whole posture and have
  Room re-emit a map identical to the one on screen.
- **`snapshot` must not project, and that is the subtle one.** Source and target describe the same grid, so
  re-laying would close the gaps the snapshot exists to preserve — a snapshot that tidies what it is preserving is
  not one. `snapshot preserves gaps where copy would close them` is the test that pins it.
- **Turning independence off writes only portrait.** Landscape rebuilds itself from portrait the next time it is
  drawn, so setting the reference right and clearing the flag is the entire operation; writing landscape here too
  would be a second answer to one question. The flag is cleared **last**, so nothing re-derives from a portrait
  that is still mid-merge.
- **`ArrangementSync` became extension functions on `LayoutRepository`**, matching `SettingsRepository.homeZoneGrids`
  written in the same change for the same reason: both compose what a repository already offers and hold no state,
  so an injectable object is a dependency every caller carries for nothing. detekt made the case — as a class it
  pushed `HomeViewModel` to nine constructor parameters, and the alternatives were a bundle invented for a single
  consumer or a baseline entry for new code.
- **`independentFormFactor` is deferred because it needs a decision, not because it is large.** With orientation,
  portrait is the natural reference. Between a phone and a tablet there is no canonical one, so "keep them in
  step" has no defined direction until someone picks it — and it only ever matters on a device that can be both.

**Known consequence, documented rather than solved:** replacing a posture's arrangement drops the placement of any
item the winning side does not have, while leaving that item's *definition* alone. A folder made only in landscape,
with "portrait" chosen at merge time, therefore survives as a row nothing draws. That is what the user asked for by
naming a winner; collecting the orphan is a separate cleanup, alongside the empty-folder auto-dissolve.

### Device verification — 2026-09-09, Pixel Fold emulator (API 36)

Driven over adb against the live Room database, so every claim below is a row rather than a screenshot reading.
Two defects, both of which only *became* defects when L1 gave the launcher more than one arrangement.

#### 1. A posture change seeds the new key against the **previous** posture's grids

Folding from the unfolded screen (`TABLET_LANDSCAPE`, 8 visual columns) to the cover screen (`PHONE_PORTRAIT`,
4 columns) wrote `PHONE_PORTRAIT` rows running out to logical column 14 — a layout eight visual columns wide,
stored under a key whose grid is four. Columns 8–14 are outside it, so **eight of twenty apps simply did not
draw**, with nothing on screen to say why.

**Fixed** — the grids now carry the posture they were resolved for (`ZoneGrids`), so the key is read out of them
rather than from a report beside them.

`HomeViewModel`'s seed collector combined `device` with `zoneConfigs`, and `zoneConfigs` is itself derived from
`device`. `combine` emits on the latest of each, so a posture change fired it **once with the new key and the old
grids** before the new grids arrived. The window is one emission wide and always taken.

Which of the three writers it damages depends on whether the writer is guarded:

- `copyArrangement` (the re-derive) is unconditional, so the corrected emission rewrites it — **self-healing**.
- `copyArrangementIfEmpty` and `seedIfEmpty` are guarded on the target being empty, and the bad write is what
  makes it non-empty — **permanent**.

The fix already exists one module over: `AppsViewModel` carries the device inside `PagerFit` and turns the
disagreement into a null rather than a plausible wrong number. HOME needs the same — the grids must name the
posture they were resolved for, and a mismatch must be dropped rather than used.

#### 2. `RemoveFromGrid` deletes across every arrangement, and that is now data loss

L1's progress note recorded this as known and "left as-is… an L3 policy question". It is not a policy question:
on device it destroys placements the user can see. **Fixed** — removal is scoped to the arrangement being applied
(apps, folders, icon containers), a definition no posture places is collected, and the remove band writes back into
the reference while the two are kept in step. Widgets and widget containers stay global, since destroying one is
meaningless without the `AppWidgetHost` unbind and no caller can yet be told whether this was the last posture
holding it.

Two things the fix flushed out, both worth keeping:

- **The remove band never wrote back.** `ShellViewModel.removeFromHome` applies straight to the repository, skipping
  the write-back every drag makes. Invisible while removal was global; scoped, it meant portrait never heard, and
  the next rotation rebuilt landscape from portrait and **brought the removed item back**. The write-back is now an
  extension on `LayoutRepository`, shared by both callers.
- **A definition outliving its last placement needs collecting.** That state was already reachable and observed: a
  folder with both members and no placement under any key.

#### 2b. Filing an app into a folder or container is scoped too — fixed, with one bound left

**The original repro's app loss was `CreateFolder`, not `RemoveFromGrid`**, so scoping removal did not close it: a
folder made in landscape took both apps off portrait's grid and gave portrait no folder in exchange.

**Fixed** by the user's rule, applied to filing as well as removal — `CreateFolder`, `AddToFolder`, `ReorderFolder`
and the container detaches now delete the placement of the arrangement being applied and no other. Verified both
ways on device: with the postures independent, a folder made in landscape leaves portrait at sixteen apps
untouched; with them kept in step, the write-back replaces the reference a moment later and portrait ends up
holding the folder instead (checked in the database — the folder is placed under both keys).

**The bound that remains is storage, not policy.** `folder_item` is uniquely indexed on `component` and
`icon_container_item` on `component` and `folderId`, so an app belongs to at most one folder and one container for
the whole launcher. Two postures can therefore hold different **arrangements** of the same groups, but not
different **groups**: filing an app into a folder in landscape takes it out of whatever folder portrait had it in.
Making that independent means an arrangement column on those two tables, which is a real storage change and has no
demand behind it yet. Recorded on `LayoutChange`'s KDoc, where the next person to file something will read it.

#### Not reached, and why

The dock rail, the widget area, multi-cell widgets, the APPS pager, the grid editor in landscape, and
`ContainerSettingsViewModel`'s footprint preview. The seed fills `MAIN` only, so a default install has nothing in
the other zones and nothing to drag from — populating them is a prerequisite for that half of the pass.

#### Adjacent finding: an unfolded foldable is `TABLET_LANDSCAPE` in **both** physical orientations

The inner screen is 851×883dp. `fromWindowSizeClass` decides orientation from width alone, so 851dp clears the
expanded bound and the posture reads landscape — and turning the device to 883×851 reads landscape again. Rotating
while unfolded therefore changes no arrangement at all. Correct-ish for layout (a near-square screen wants the wide
grid either way) and wrong as a *name*, since the arrangement key inherits it. Worth a decision beside L4's audit.

## Rejected

- **Six keys, with the two `*_SHARED` ones holding a reference snapshot.** Built, shipped to the emulator, and
  replaced. It made the two modes write the *same four row-sets*, so the toggle was a merge and every answer
  destroyed a layout the user had made:

  | Toggle | What it destroyed |
  |---|---|
  | on → edit landscape → off, "Portrait" | the independent landscape |
  | on → edit landscape → off, "Landscape" | the portrait layout |
  | on → edit both → off, "Neither" | the portrait edits made since the toggle |
  | off → on again | the earlier independent landscape, overwritten by the re-derive |

  There is no path through that switch preserving both, which is what "the share layout disturbs the independent
  layout" names. Splitting the mode onto its own axis removes the question instead of answering it, and deletes
  the chooser, the snapshot and the orphan case with it.
- **Keying arrangements on `Orientation` (two values).** Correct on any single device, and it is what the tree
  has — but a foldable changes `DeviceConfiguration` without changing `Orientation`, so folded and unfolded would
  silently share one arrangement with no way to separate them. Rejected once the foldable policy was in scope.
- **Keeping `(row, col)` and overflowing** as the non-reflow mode. This was the first reading of Smart Launcher's
  "rotate on place"; the phrase turns out to contrast *against* board rotation, and the behavior asked for is the
  board rotation. Keep-coordinates also leaves the gained landscape columns empty, which is the gap reflow exists
  to avoid, so it would have been a third mode earning nothing.
- **Copying the reference into the authored keys continuously.** Same visible result as materialize-on-toggle,
  five write paths instead of one, and failures invisible until the toggle flips.
- **A writable projection that refuses structural change** (reorders write back, adds/removes/spans do not).
  Sound for reorders only, and a partial capability that cannot be explained to a user.
- **Making landscape read-only while sharing.** Satisfies the round-trip problem by removing it, but a gesture
  that works in portrait and not in landscape reads as a bug.
