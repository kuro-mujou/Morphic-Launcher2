# Known issues

What is wrong or wanted in the launcher as it stands, and **not yet done**. One entry each: what happens (or what is
asked for), where it lives, and what it touches or depends on.

Two kinds of entry, kept in two lists because they are closed differently:

- **Defects**: something that **is** built and is wrong. Closed by making it right.
- **Requested changes**: something built that works as designed, where the design is now wanted to be different, plus
  the few pieces of new scope that came in with them. Closed by building it. This is often a *reversal* of a decision
  a KDoc or a design doc argues for, and when that is so the entry names the document. Change both together, or the
  doc goes on defending the old behavior.

This is still not a plan. A plan orders work and says how it is built; an entry here only records that it is owed.
Anything that grows a design moves to its own `docs/*_PLAN.md`, and its entry here shrinks to a link.

An entry says whether it was reproduced or only reported, and by what. "Reported" is not a weaker claim about the
bug — it is an honest one about the evidence.

Everything below was **reported by the author on 2026-09-14**. None of it has been reproduced yet. The "where" lines
come from reading the code, not from a debugger.

---

## Defects

### D3. A label's ink snaps between light and dark with no transition

**What happens:** A HOME label crossing a light/dark boundary in the wallpaper (a pager swipe, a scroll) switches color
instantly and often. It reads as flicker.

**Where:** `SpotTheme` (`core:designsystem/backdrop/SpotTheme.kt`) switches `MorphicTheme(darkTheme = light)` on a
boolean. That boolean is snapshot state set from `onLayoutRectChanged` with no throttle and no debounce. Nothing
animates the resulting `content` color. The backing alpha already changes continuously; the ink does not.

**Direction:** Animate the ink color, not the boolean. The ink is read in composition, so an `animateColorAsState`
over `colors.content` is where it would go. Probably worth a little hysteresis too, so a label parked on a boundary
does not oscillate. Fold this into R1 if that lands first: the glow's color has to follow the same animation.

### D4. A side surface starts following the finger only after ~1cm of travel

**What happens:** Swiping up from HOME to open a side surface (APPS), the finger travels about **1cm** before the
surface starts to move. Other launchers move their drawer after a few millimeters, so the surface looks attached to
the finger from the start. Reported by the author on device. It has not been measured yet; see the last paragraph.

**What 1cm means:** The pan claims at the platform touch slop, about 8dp (~1.3mm on a phone), and on the claiming
event it applies the travel past slop (`pastSlop` in `SurfacePagerGesture.kt`). So the finger should be only the slop
ahead of the surface. About 1cm is roughly 60dp, six to eight times the slop. The recognition threshold does not
account for it. Either the claim comes late, or the pan moves on time and the surface is not *drawn* on time.

**Ruled out by reading:**

- **The drag math.** `dragXBy`/`dragYBy` are a linear `px / viewport` with no resistance or dead zone.
- **The transition.** The default is `SurfaceTransition.SLIDE` (`SurfaceRegister`), a plain offset. The other five
  transitions fade or scale (`SurfaceSlotTransform.kt`), and some of those do hide early travel, so confirm which
  transition the device was set to when this was seen.
- **`SurfaceGestureLock`, on empty wallpaper.** Its claimants on HOME are an item only from `ShowMenu`/`BeginDrag`
  (`LauncherItemGestures`), an open menu (`MenuHost`), a widget container for its whole press, and an embedded widget
  view until it declines. None of them holds a claim at the down on empty space. A swipe that *starts on a widget* is
  the exception: the pan waits for the claim to drop, so on a widget this is expected, and worse.

**Where the cause appears to be:**

1. **The side surface is not composed until the pan moves (main suspect).** `SurfacePager` skips a side slot while
   `edge !in engagedEdges && edge !in retainedEdges`, and `engagedEdges` is empty until the pan is past exactly zero.
   So the first drag event after slop is the one that composes APPS: its layout (lazy grid/list, category pages), its
   state, its icon loads, and the frost overlay starting to fade in. That frame is long. The finger keeps moving while
   it runs, and the surface appears already behind. Content that loads asynchronously (icon bitmaps) arrives a few
   frames later still, so the surface can move while still visibly empty. `rememberSaveableStateHolder` restores a
   slot's state, which makes un-composing it look free, but it is not free on the first frame of every swipe. Read
   `SurfacePager`'s class note for why side surfaces are gated before changing it.
2. **The pump starts a coroutine for the first step.** `PanPump.add` runs the first `snapTo` in `scope.launch`, which
   dispatches rather than runs inline, so the claiming event's drag can land a frame later. At most one frame (~16ms),
   so it does not explain 1cm alone, but it adds to (1).
3. **The frost is drawn over the same frames.** `SurfaceBackdropLayer`'s blur starts as soon as `progress` leaves
   zero. If building its first render effect or capture is expensive, it lands on the same frame as (1).

**Cheap experiments that separate them:**

- **Pass the APPS edge in `retainedEdges` permanently** (shell side, one line, not for commit). If the lag is gone,
  (1) is confirmed and the fix is composing the surface ahead of the swipe: at rest after HOME settles, or at the
  down. The gap between the down and slop is short, so composing at the down only helps if composition is warm.
- **Set the transition to SLIDE and the frost off** to isolate (3).
- **Profile the first frame** with a Perfetto trace, or Profile HWUI rendering, over one swipe. A composition spike
  on the claiming frame is (1).

**Measuring it:** The author will record a video with the pointer visible and split it into frames. For readable
numbers: turn on **Developer options → Pointer location**, so the trail and the finger's coordinates are drawn on
every frame, and record at the display's refresh rate. Then count frames from the first frame the pointer moves to
the first frame the surface's edge moves, and read the pointer's travel off the overlay at that frame. Both the delay
(frames × frame time) and the distance (px ÷ density = dp) come out, and the frame where the surface first *appears*
(composed but not yet drawn) shows which suspect it is. Repeat once with the finger starting on a widget, since that
path is slower on purpose and should not be mixed into the result.

**Other swipe paths, not the one reported** (from reading the code, not timed):

- **System-panel actions** (`ShadePull.NOTIFICATIONS` / `QUICK_SETTINGS` / `BY_SIDE`) replay a finger stroke through
  the accessibility service (`MorphicGestureService.swipeDownFromTop`): `SWIPE_MS` 200ms, and for quick settings two
  strokes with `SETTLE_MS` 350ms between them, about 750ms before it starts expanding. `ShadePull.PULL_DOWN`
  (`GLOBAL_ACTION_NOTIFICATIONS`) has no stroke and is the control. Shorten the durations by testing on the devices
  `SystemShade` names; a stroke too short is read as a tap.
- **Every swipe action** goes through `ShellViewModel.runHomeSwipe`, which re-reads `homeGestures.first()` from
  DataStore before running the action the shell already holds. Probably milliseconds; pass the action in instead.
- **A swipe that starts on an icon** is recognized at 20dp instead of ~8dp, and `ItemGestureMachine` fires
  `EdgeAction` on **Up**. Firing on release was deliberate (letting go early cancels), so changing it is a design
  decision.
- **The settle after release** uses `spring(stiffness = Spring.StiffnessMediumLow)` with a long tail, and
  `FLING_THRESHOLD_PX` = 1000 is raw px/s, not dp, so on a dense screen a short flick is more likely to snap back.

---

## Requested changes

### HOME labels

#### R1. Replace the label's shadow and backing pill with a glow

Wanted: a glow behind the label text, and **both** of today's treatments removed:

- the drop `Shadow` in `CellLabel` (`core:designsystem/cell/IconLabelCell.kt`: 60% background, 2px y-offset,
  4px blur);
- the rounded-pill backing `SpotTheme` draws behind content (`drawBacking`), whose alpha `inkOver` computes.

The glow would take over the backing's job of making ink readable where the spot straddles light and dark. That means
`inkOver`'s `backingAlpha` becomes the glow's strength, or goes away; decide which. Otherwise the reading is computed
and never used. Reverses the "halo softens, backing lifts" split in `CellLabel`'s KDoc and `SpotTheme`'s. The
adaptive-content-color section of `docs/DESIGN_SYSTEM.md` needs the same edit.

#### R2. Take automatic ink off the film; keep it on the wallpaper

Wanted: text over the **blurred film** (APPS, an open collection, the menu over HOME) stops choosing its color spot by
spot. The blur already evens out the picture enough that one color is readable everywhere on it. Per-spot ink stays
where it earns its cost, straight on the wallpaper on HOME.

**Where:** The film publishes itself as an `InkSurface` (the film's blurred picture plus its wash) through
`LocalInkSurface`, which is what turns `SpotTheme` on there. Providing `null` inside the film makes `SpotTheme` do
nothing, and the surface's single verdict (`OnFilm`) takes over. This reverses "four backgrounds, each text reads its
own spot" in `docs/DESIGN_SYSTEM.md` → "Adaptive content color", and `InkSurface`'s KDoc says two surfaces exist.
Both change with it.

### Context menu

#### R6. Redesign the context menu

Wanted: a better-looking menu. No direction given yet. Get one (a reference, or a mockup) before building. Scope is
`MenuSurface`, `MenuRow`, `MenuHeader` and `ChevronMark` in `ContextMenu.kt`. The inline rendering and staging
(`MenuStage`) are behavior, not look, and stay. The menu's content is also misaligned (reported without the case); the
redesign absorbs that rather than fixing it first. Placement is `MenuAnchoring.kt`, and it must not move into a `Popup`:
a drag has to continue on the same pointer stream.

### Settings

#### R7. Settings panes stop padding their own top edge

**What happens:** Every text-style detail pane pads itself `20.dp` on all four sides inside its scroll. That is
`AboutDetail`, `PermissionsDetail`, `PrivacyPolicyDetail`, `EffectsDetail`, `ExtrasDetail`, `GesturesDetail`,
`HomeDetail`, `OrientationDetail` and `SurfaceRegisterDetail`, each with `.padding(20.dp)`. The pane already sits
under a top app bar with plenty of air, so the top 20dp doubles a gap that was not needed.

**Wanted:** The outermost padding leaves the shared shape and moves into each screen's own components, so each screen
decides its own edges. The top padding goes, or is cut back.

**Where it must not break:** The inset rules in CLAUDE.md. Bars are content padding and never layout padding, and
which sides get insets is the shell's call (`insetSides`). This padding is separate from the insets. Keep them
separate so the landscape work in R8 does not re-add a bar inset by hand.

#### R8. Fix phone landscape for five settings screens

**What happens:** Five screens lay out badly on a phone in landscape: **Wallpaper** (`WallpaperDetail`), **Effects**
(`EffectsDetail`), **Icons** (`IconsDetail` / `icons/`), **Screen manager** (`SurfaceRegisterDetail`) and
**Home screen** (`HomeDetail`). Reported without specifics. Several of these have a live preview above their controls,
and on a short, wide screen that likely leaves no room for the controls. Confirm per screen on the emulator.

After R7, since that change moves the padding these layouts are built from.

### Wallpaper studio

#### R11. Credit gart in the wallpaper studio

Wanted: the studio says where its generators come from: gart (`D:\Android\gart`, BSD-2). **After** the in-progress
studio fixes (the W11 passes). Do not start it on a studio still being reworked.

**Not only courtesy:** BSD-2 requires the copyright notice and license text to go with binary distributions. gart is
harvested source, not a Gradle dependency, so the AboutLibraries list in About does **not** pick it up on its own. The
credit needs a license entry there as well as the note in the studio. `docs/GART_HARVEST.md` records what was taken.

### APPS surface

#### R12. Dark shades under the status bar and navigation bar on APPS

Wanted: two translucent black gradients, one under the status bar and one over the navigation bar, so content
scrolling beneath the bars stays readable. On four APPS layouts: **vertical list**, **vertical grid**,
**category pager**, **category card**. Not on the APPS pager, which is not on the list.

**Where:** Nothing in `feature:apps` reads `statusBars` or `navigationBars` today. The layouts inset through `uiInsets`.
Size each shade from the real bar inset. A dp guess leaves a strip on gesture navigation, where the navigation bar is
thin. The shades are chrome over the film, so they belong in one place `AppsScreen` draws once. Written into each
layout, it would be four copies. `MainActivity` sets both bars scrimless and turns navigation-bar contrast off, so the
system will not draw one of these for us.

---

Firebase, in-app updates and the paywall moved to their own plan: [FIREBASE_PAYWALL_PLAN.md](FIREBASE_PAYWALL_PLAN.md).
