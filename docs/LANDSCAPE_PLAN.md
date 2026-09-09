# Landscape Plan — one arrangement key, a reference layout, and two projections

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

Five constants and a gate:

| Where | What it pins |
|---|---|
| `HomeViewModel.ORIENTATION` | every home placement read and write |
| `HomeViewModel.drawsStoredPlacements` | disables `fitMainTo`/`fitDockTo` whenever the device is rotated |
| `AppsViewModel.ORIENTATION` | the APPS pager's saved list |
| `ShellViewModel.ORIENTATION` | removal writes |
| `DockViewModel.ORIENTATION` | the dock settings section's writes |
| `GridSizeViewModel.ORIENTATION` | the grid editor's displaced `Move`s |

…plus an unaudited UI tail (sheets, studios, pickers, the settings panes that never got a landscape pass).

## The model

### The key: `ArrangementKey`, six values

The placement column stops being `orientation` and becomes `arrangement`. Six values, in `core:model` beside
`DeviceConfiguration`:

```
PHONE_PORTRAIT   PHONE_LANDSCAPE   PHONE_SHARED
TABLET_PORTRAIT  TABLET_LANDSCAPE  TABLET_SHARED
```

The four concrete keys are **authored** arrangements — what a drag writes when that configuration owns its own
layout. The two `*_SHARED` keys are **reference** arrangements: one per form factor, holding the layout that
every configuration of that form factor projects from while sharing is on.

**Renamed rather than re-interpreted**, per the standing rule that a semantic break takes a new name — the old
column meant "which orientation", the new one means "which arrangement", and re-reading one as the other would
fail silently. `version = 1` with `fallbackToDestructiveMigration(dropAllTables = true)` is already set, so the
bump costs nothing and there is no migration to write. Affects the five `*_placement` tables and
`apps_pager_item` (whose per-orientation unique indices become per-arrangement).

**`Orientation` survives, scoped to wallpaper.** The rotating pair is genuinely two images per orientation and
has nothing to do with arrangements. After this, `Orientation` is `data:wallpaper`'s type and nothing else's —
which is what stops it drifting back into being a second, weaker arrangement key.

### Two policies, one lattice

Both of the user's settings ask the same question — *do these two configurations share an arrangement, or get one
each?* — so they are one policy over one key, not two features.

| `independentLayout` | `independentFormFactor` | Authored keys in play |
|---|---|---|
| off | off | one (`PHONE_SHARED` ≡ `TABLET_SHARED`, kept in step) |
| on | off | two — portrait and landscape, shared across form factors |
| off | on | two — `PHONE_SHARED` and `TABLET_SHARED`, independent of each other |
| on | on | four — every configuration authored |

Both default **off**: a fresh install has one arrangement, and rotating or unfolding shows it adjusted.

### The reference is portrait

Stated by the user and load-bearing: *"we use portrait as our main reference layout"*. A `*_SHARED` arrangement is
held in **portrait terms** and projected into landscape for drawing. A drag performed in landscape while sharing
is projected **back** into the reference.

### Materialize on toggle, never continuously

When independence is turned on, the projection is materialized **once** into the authored keys it just created —
which is exactly the behavior asked for ("the first rotate after turning the toggle on still shows the adjusted
layout"). The authored keys do **not** exist before that, and the shared layout is **not** copied into them
eagerly. Copying eagerly would mean every drag writing five rows, with a bug in any copy staying invisible until
the toggle flipped weeks later.

Turning independence back **off** asks which arrangement becomes the new reference: **portrait / landscape /
none** (none keeps the reference untouched since the toggle went on). All three are well-defined precisely
because the reference was left alone while independence was on.

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

### Rotate in place requires coupled grids, and is only offered in shared mode

The transform is a bijection **only** when the target grid is the source's transpose: `4×6` portrait ↔ `6×4`
landscape. So in **shared** mode the grid sizes are coupled — editing one orientation's counts transposes the
other's — and rotate-in-place becomes available. In **independent** mode each configuration owns its grid size
freely (`4×6` portrait against `5×8` landscape is legitimate), no transpose exists, and rotate-in-place is
therefore **absent**, not disabled.

Coupling is not the fight with the dock rail it first looks like: the phone blueprints are *already* transposes
(`DockGrid`'s own comment says so). What coupling costs is landscape density — phone landscape's blueprint
default is `6×4` (24 cells) where portrait's transpose is `5×4` (20). Shared mode trades those four cells for an
exact round-trip; independent mode gets them back.

Turning independence on materializes each authored key's **grid size** alongside its arrangement, so a layout
keeps the lattice it was built against.

## The settings

A new **Orientation** group. Three controls, plus the mode picker:

- **Rotation** — `AUTO` (follow the device, default) / `PORTRAIT` / `LANDSCAPE`, the latter two locking via
  `activity.requestedOrientation`. L1's only use of that API was pinning its crop screen; L2 letterboxes there
  instead, so this is the first real consumer.
- **Independent layout** (default off) — splits portrait from landscape.
- **Independent foldable setup** (default off) — splits phone from tablet. Present only on a device that can be
  both; a phone that cannot unfold has nothing to independent-ise.
- **Sync mode** — Reflow / Rotate in place. Shown only while `independentLayout` is off *and* the side zone
  changes axis on this form factor. Absent, not disabled, both times.

Turning `independentLayout` off raises the **portrait / landscape / none** chooser described above.

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

#### L3b — the sharing policy

- [x] `*_SHARED` keys wired — as **snapshots**, not as continuously-maintained references; see the progress note.
- [x] The `independentLayout` toggle, its snapshot-on-enable, and the turn-off chooser
      (portrait / landscape / neither).
- [x] Write-back from a non-reference posture, and the unconditional re-derive on entry that pairs with it.
- [ ] `independentFormFactor` — **deferred, and it needs one decision**; see the progress note.
- [x] **The default path verified on device** (2026-09-09): a landscape drag wrote back into portrait *on the drop*
      rather than on the next rotation, the re-derive carried a portrait edit into landscape, and three rotations
      either way produced no drift — the no-op guard holds.
- [x] **Independence and the chooser verified as far as they go:** the toggle writes `PHONE_SHARED` on enable, and
      the dialog offers its three answers with Cancel leaving independence on.
- [x] **Independence holds a posture apart for removals**, verified after the fix below: removing in landscape
      leaves portrait whole, and with the two shared it reaches portrait without resurrecting on the next rotation.
- [ ] **Still open — structural additions.** A folder made under independence still takes its apps off the other
      posture without giving it the folder; see "2b" below, which needs a decision.
- [ ] **Still unverified:** the KEEP_PORTRAIT and KEEP_LANDSCAPE answers, which need a run that does not trip the
      bug above.

#### L3c — coupling and the second mode

- [ ] Grid-size coupling while sharing.
- [ ] Rotate-in-place, on `GridPlacement.rotateForLandscape`, offered only where the side zone changes axis.

- **Verify (L3b + L3c):** the full toggle lattice, including the sequence the user named — independence on, edit
  portrait, rotate, edit landscape, independence off, each of the three chooser answers.

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

### L3b — code complete 2026-09-09, awaiting device verification

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

#### 2b. Still open — a structural change made under independence reaches the other posture anyway

**The original repro's app loss was `CreateFolder`, not `RemoveFromGrid`**, and scoping removal does not close it.
Folder and container *membership* has no arrangement column, so filing an app into one must detach it from every
grid — that part is right, and a per-posture detach would leave portrait drawing an app that a landscape folder also
contains.

What is missing is the other half: the new folder is placed **only** in the posture it was made in. While the two
are kept in step the write-back carries it across; under **independence** nothing does, so the other posture loses
the apps and gains no folder. The same holds for any structural addition — a new icon container, a placed widget.

Three answers, none obviously right, and it is the user's call:

1. **Place it in every arrangement**, finding a free cell in each posture's own grid. Needs those grids, which
   `data:layout` deliberately does not resolve — so they arrive as a parameter, as `copyArrangement`'s do.
2. **Accept the loss and make it visible**, by telling the user a folder made here does not exist over there.
3. **Give membership an arrangement column**, which makes folders genuinely per-posture and is a storage change.

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
