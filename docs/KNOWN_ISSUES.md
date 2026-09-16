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

### D6. Other swipe paths may lag

**What happens:** Not reported. Found while tracing D4, the surface-swipe stall that composing side slots ahead of the
swipe fixed. From reading the code, not timed:

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
adaptive-content-color section of `docs/DESIGN_SYSTEM.md` needs the same edit. The glow's color has to follow
the ink's cross-fade in `SpotTheme`, or it snaps while the text fades.

### Context menu

#### R6. Redesign the context menu

Wanted: a better-looking menu. No direction given yet. Get one (a reference, or a mockup) before building. Scope is
`MenuSurface`, `MenuRow`, `MenuHeader` and `ChevronMark` in `ContextMenu.kt`. The inline rendering and staging
(`MenuStage`) are behavior, not look, and stay. The menu's content is also misaligned (reported without the case); the
redesign absorbs that rather than fixing it first. Placement is `MenuAnchoring.kt`, and it must not move into a `Popup`:
a drag has to continue on the same pointer stream.

### Settings

#### R8. Fix phone landscape for five settings screens

**What happens:** Five screens lay out badly on a phone in landscape: **Wallpaper** (`WallpaperDetail`), **Effects**
(`EffectsDetail`), **Icons** (`IconsDetail` / `icons/`), **Screen manager** (`SurfaceRegisterDetail`) and
**Home screen** (`HomeDetail`). Reported without specifics. Several of these have a live preview above their controls,
and on a short, wide screen that likely leaves no room for the controls. Confirm per screen on the emulator.

### Wallpaper studio

#### R11. Credit gart in the wallpaper studio

Wanted: the studio says where its generators come from: gart (`D:\Android\gart`, BSD-2). **After** the in-progress
studio fixes (the W11 passes). Do not start it on a studio still being reworked.

**Not only courtesy:** BSD-2 requires the copyright notice and license text to go with binary distributions. gart is
harvested source, not a Gradle dependency, so the AboutLibraries list in About does **not** pick it up on its own. The
credit needs a license entry there as well as the note in the studio. `docs/GART_HARVEST.md` records what was taken.

---

Firebase, in-app updates and the paywall moved to their own plan: [FIREBASE_PAYWALL_PLAN.md](FIREBASE_PAYWALL_PLAN.md).
