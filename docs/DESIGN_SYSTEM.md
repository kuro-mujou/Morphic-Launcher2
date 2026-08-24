# Design system — subsystem detail

*Split out of [CLAUDE.md](../CLAUDE.md) on 2026-08-20.*

The parts of `core:designsystem` that are a **record of one subsystem** rather than a rule shaping
every UI task: why individual components are built the way they are, how the frosted backdrop and the
full-screen frost work, how the wallpaper-brightness signal is derived, and the grid's horizontal
padding. **Read on demand when working on one of these.**

The cross-cutting rules — palette, theme layering, build-on-M3, state facades, touch targets, scroller
geometry, the derive-vs-store split, insets, packaging — stayed in
[CLAUDE.md](../CLAUDE.md#design-system-coredesignsystem).

---

## Components

- **`MorphicSwitch` is the one component that goes custom even though M3 *has* the control, and the test it
  passes is the same one — "no equivalent" reached from the other direction.** M3's `Switch` exposes
  `thumbContent` and `colors` and **nothing for the track**: its 52×32 pill comes from `SwitchTokens`, is not a
  parameter, and `Modifier.size` does not reach it either, so the shape wanted here is unreachable through it.
  The shape wanted is **M2's** — a 34×14 rail with a 20dp knob standing proud of it — because M3's track
  *encloses* its thumb, leaving the state to read as which end a blob is at, where the M2 split gives two
  independent signals (where the knob is, how bright the rail is). On a palette with no hue that second signal is
  worth the custom component. The metrics are taken from M2 exactly rather than eyeballed near them, since the
  proportion is the entire point.
- **Expressive motion is still kept** — the knob travels on `motionScheme.defaultSpatialSpec`, the colors
  cross-fade on `defaultEffectsSpec`: spatial for what moves, effects for what does not. Colors come from the
  **slider's** `trackInactive`/`trackActive`/`thumb` roles so the two controls are made of the same grays, with
  alpha on the *on* track because at full strength `trackActive` **is** `thumb` and the knob would vanish into
  the rail. Off, the knob is `contentMuted` on a `trackInactive` rail — light-on-dark in the dark theme and
  dark-on-light in the light one, which a fixed pair of colors would not have given.
- **Tap only: there is no drag**, which M3's switch has. Deliberate, because the form to reach for is
  `MorphicSwitchRow`, where the *row* is the target and nobody swipes 14dp of travel; `AnchoredDraggable` on the
  knob is the way back if a bare switch ever lands somewhere a drag is natural.
- **`MorphicSwitchRow` is that form**, not the bare switch: `Modifier.toggleable` with `Role.Switch`
  on the row puts the target, the ripple and the accessibility announcement on the label *and* the switch
  together, where a `Row { Text; Switch }` leaves a small target beside unassociated prose. The switch is then
  handed `onCheckedChange = null` — not `enabled = false`, which would gray it — so one press is handled once.
  Its first consumer is the icon studio's shape anchor, which works there with no variant because the studio is
  already a fixed-dark theme zone (`LauncherTheme(darkTheme = true)` at its root).
- **`MorphicColorPicker`** (a saturation/value panel over a hue bar) has **no alpha channel**, deliberately: every
  color the launcher lets a user pick already sits somewhere carrying opacity, and offering a second way to set
  it is how a color silently loses its transparency. Its hue is held as *state* rather than re-derived from the
  color, which is correctness and not economy — hue is undefined at black, white and every gray, so a picker that
  recomputed it would jump under the finger the moment the panel was dragged into a corner.
- **`AppPicker`** (`picker/`) is the exception to the extract-on-the-second-consumer rule this module otherwise
  follows (`IconPreviewPlate`'s). It went in on its *first*, because the other consumers were named and blocked
  rather than speculative — HOME's "Add app", the home list's "Add apps" row and a folder's own Add cell (the last
  two are built, and both open this). It takes a `List<AppInfo>`, never a repository, and matches with a
  locale-aware `Collator` rather than `lowercase().contains` — the same lesson the APPS ordering already learned.
  - **It is a grid of icons, which is L1's shape and the right one**: a picker is browsed by *recognition*, and an
    icon is what an app is recognized by. The 64dp rows it drew before put a fraction as many apps on screen, which
    is what made ticking several of them a scroll between each. Selection is a drawn disc-and-check at the icon's
    top-end — drawn because this module carries no material-icons dependency, as `MorphicResetButton` and
    `TopActionZone` also do. **No plate behind the picker's icons**, and nothing arranges it: every surface this
    opens on sets `LocalOverFrost`, so `AppIcon` drops it. L1 needed an explicit `LocalSkinBackdropAllowed` for that.
  - **Its multi-select consumers share one sheet** — `AppSelectionSheet` in `feature:home`, the title/`Add {n}`/picker
    composition extracted when the home list became its second consumer. L1's near-duplicate second picker is the
    outcome that shape exists to avoid.
- **`AddAppsCell`** (`cell/`) is the **"Add" cell that trails a collection's apps** — L1's `FolderAddCell`, and the
  affordance a folder had been missing. It is an `IconLabelCell` like every other cell in the grid, which is what
  keeps its mark and label sized by the user's icon settings rather than drifting from the apps beside it; only the
  mark differs, and it is outlined rather than filled because an outline reads as an empty slot waiting to be filled.
  It takes its own `clickable` rather than `launcherItemGestures`, the one place a cell may: the shared contract
  exists so an *item* has one gesture owner, and this is not an item — nothing to drag, no menu, no drop.
  - **Pressing it hides the collection and puts the picker on the collection's own film** (`AppMultiPicker`), which
    is L1's arrangement and the reason the overlay owns the flag rather than its hosts: the picker stands *in place
    of* the card, not over it, so a host holding the state would have to hide a card it does not draw — three times,
    identically. The alternative was a sheet over a card over a frost, three layers of chrome to reach a search
    field. The card is hidden at zero alpha rather than removed, so Cancel is a return and not a rebuild that has
    forgotten which page you were on, and the picker's root swallows taps so the hidden card beneath takes none.
  - **All three hosts offer it** — a home folder, an APPS-pager folder, an APPS category card's expansion — through
    one `AppAdditions`. The overlay subtracts what the collection already holds, so a surface builds **one** sorted
    list for every collection on it. A category is the odd one: filing an app there takes it out of whatever category
    held it, where a folder takes membership, which is why the two commits are separate ops.
  - **The overlay reserves a slot for it in the arithmetic, not in the list.** `AppCollectionOverlay` counts one extra
    slot into its page count so a collection whose apps exactly fill the last page grows a page, and places the cell
    by coordinate at the flat slot after the last app — the same `gridPlacement` maths the drop shadow uses. L1
    appended a `null` to its slot list instead; here the list *is* the reorder's index space, so a pseudo-entry would
    shift every drop by one.
- **`ActionRowCell`** (`cell/`) is a list row that is **not an app** — the home list's pinned *Add apps* row. It sits
  beside `AppRowCell` because it has to share their inset, icon sizing and label style (`rowLabelStyle` is private
  there so nothing can disagree about the last one), and it takes its mark as a **slot** rather than an `ImageVector`
  for the no-material-icons reason above.

## The wallpaper-brightness signal

- **That brightness signal is L2's own idea, not a port, and it is now live** — worth knowing before looking for it
  in L1, which has no luminance analysis anywhere and themes from the system's dark mode. `LauncherShell` reads
  `WallpaperRepository.luminance` and the hardcoded `darkTheme = true` is gone. It is a *number* rather than the
  light/dark verdict it started as; see "Adaptive content color" for why a verdict could not survive the film.
- **It asks the system before it reads anything, and it did not need `Blur.kt`.** The plan had it waiting on the
  dominant-color half of L1's `Blur.kt`; both halves of that assumption were wrong.
  `WallpaperManager.getWallpaperColors` already answers the question over the wallpaper that is *actually displayed*
  — another app's, or a live one, neither of which we can read as a bitmap — with no permission and no decode, and on
  API 31+ `HINT_SUPPORTS_DARK_TEXT` is literally the verdict. And `dominantColor` would have been the **wrong
  statistic** anyway: it weights each pixel by saturation so a vivid accent beats washed-out gray, which is what an
  *accent* wants and the opposite of what "how bright is this?" wants. So the blur *and* the dominant color are both
  still unported, still waiting on the frosted backdrop that is their real consumer.
- **Reading our own file is the fallback, and it is gated on proof.** Only when the system says nothing (API 26, or a
  live wallpaper publishing no colors) *and* `appliedSystemId` still equals the live wallpaper id — i.e. nothing has
  replaced ours since we set it, which is the second job that field's KDoc reserved it for. Otherwise `DARK`, which
  is both the old hardcoded value and the safer miss: light chrome over an unexpectedly bright wallpaper is
  unreadable, dark chrome over a dark one is merely dull. The cut is at relative luminance **0.179**, which is not a
  taste value — it is where the WCAG contrast ratios against black and white cross.
- **`RotatingWallpaperService` now publishes its colors** (`onComputeColors` + `notifyColorsChanged` on each new
  image). A live wallpaper is the one kind the system cannot analyze for itself, so a service that stays silent
  leaves *every* consumer of `getWallpaperColors` with nothing — status-bar icon contrast included. Answering means
  our own rotating pair takes the same path as every other wallpaper instead of needing a special case that reads our
  files behind the system's back. L1's service published nothing and had no caller that missed it.

## The frosted backdrop and the full-screen frost

- **The frosted backdrop is `core:designsystem/backdrop`, and it samples by *position*.** `Modifier.wallpaperBackdrop`
  draws the crop of the pre-blurred wallpaper that sits behind wherever the node currently is, so a surface that moves
  slides *over* the picture rather than carrying a patch of it — which is the whole difference between glass and a
  texture. `BackdropState` is **shared images plus a mapping, not a bitmap per surface**, so two frosted surfaces
  side by side continue each other and the cost is a blur for the screen rather than one per node. There are **two**
  pictures, and the split is `BackdropRole`: a *panel* samples the wallpaper blurred at the user's own strength (the
  effects section's slider), the **full-screen film** samples it at the fixed strength `fullScreenFilm` names. One image
  cannot be both — at a panel blur of zero it is the sharp wallpaper, and a sharp sheet occludes nothing. One `rememberBackdropState` builds both, shared by the shell and the effects preview once
  there were two zones doing it. **The picture
  and its mapping are one type (`BackdropImage`) for a reason that is invisible when broken:** `downscaleFor` reduces in
  proportion to the blur, so the two are routinely *different sizes*, and a mapping applied to the wrong one draws a
  crop at the wrong scale — which reads as the wallpaper sitting slightly off behind the glass rather than as a
  mismatched pair of arguments. It is a `Modifier.Node` and not a
  `drawBehind` because of exactly that motion: the outline and clip `Path` are cached against size and shape, so a
  position-only change rebuilds nothing. Ported from L1's `Backdrop.kt`, with four differences:
  - **Every effect blurs; what they differ in is the *wash*, and the wash is now a parameter rather than a variant.**
    The model used to let an effect decline to sample the wallpaper at all, and the full-screen frost overturned that: a
    surface arriving over HOME has to occlude it whatever decoration the user picked, so the only choice ever really on
    offer was *which wash*. That first made `None` into `Plain(strength)`; then `Plain`, the two `Blur` tones and
    `MaterialYou` collapsed into one `Blur` carrying a **`BackdropTint`**, since four variants that blur identically and
    differ by a color are four names for one effect. `blurStrength` is therefore total over two variants, and "nothing to
    sample" means one thing — `LocalBackdrop` being null, i.e. the launcher has no wallpaper it may read. **A storage
    break, free pre-launch:** `@SerialName("blur")` is kept, so an old blur blob is not an unknown *type* — it fails on
    `tint`, which was a `Float` and is an enum, and the slice falls back to its default with a log. Nothing can be
    silently mis-read, which is the property that mattered.
  - **All four effects carry the wallpaper's hue, and that is the one deliberate exception to the monochrome palette
    rule.** The rule makes *chrome* grayscale so the wallpaper and the icons carry the color; an effect the user picks,
    whose whole subject is the wallpaper, is not chrome. So L1's two-stage blend is ported exactly: a **wallpaper tone**
    = `lerp(surfaceVariant, accent, 0.30)` (mode-appropriate, and desaturated here because our `surfaceVariant` is
    gray), then light = `lerp(White, tone, 0.35)`, dark = `lerp(Black, tone, 0.35)`, and `MaterialYou` = the tone
    outright. A plain white or black film over a blurred photograph reads as dirty, which is the bad effect the 35%
    nudge exists to fix. **This reverses a call made mid-slice** — the first cut dropped the hue everywhere and left
    `MaterialYou` unrenderable, and the author reversed it; the reasoning is kept because the exception is only
    defensible if the rule it bends is stated.
  - **The accent is read from the wallpaper, not from the OS palette.** L1 used `colorScheme.primary` above API 31,
    which worked because its launcher ran a normal M3 dynamic scheme; L2 bridges a **monochrome** scheme, so that
    expression returns gray. `WallpaperRepository.accentColor` reads it directly — `WallpaperColors.primaryColor` on
    API 27+, and L1's saturation-weighted `dominantColor` over our own file below that. So **both halves of `Blur.kt`
    are now ported** after all, and for L1's own reasons.
  - **Liquid glass is a real AGSL shader** (`backdrop/LiquidGlass.kt`, API 33+): a rounded-rect SDF whose rim band
    refracts the backdrop with a circular falloff, plus chromatic dispersion, a sheen highlight and a vibrancy boost.
    **The refraction maths is adapted from [Kyant's AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
    (Apache-2.0) and the attribution must stay in the file.** It samples the *same* crop rectangle the blur path does,
    so switching effects does not shift the picture; the compiled shader and its bound bitmap live on the node, since
    a drag re-sends uniforms every frame and only the uniforms change.
    - **The rim is a *panel's*, and a surface that borrows the window's edges is not one** —
      `wallpaperBackdrop(refracts = false)`. A lens needs an edge to bend light at; across a screen that band falls
      under the system bars, so it costs a shader and shows nearly nothing. What such a surface renders instead is the
      blur plus `BackdropEffect.saturation` — a `ColorMatrix` boost, no shader, every API — which is what makes frosted
      glass read as glass rather than as fog and is iOS's own recipe for its materials. Side effect worth having:
      **below API 33 glass now looks like something**, where it degraded to a plain untinted blur and was
      indistinguishable from `Plain`. That degradation is still L1's own fallback for the rim itself.
    - **What still draws it is the container tile** (`containerPanel`) — a small thing floating on the wallpaper with
      four edges genuinely its own, which is what a lens is for. The context menu and the bottom sheets each looked
      like candidates and are not; `filmBackdrop` below owns the test.
  - **The backdrop is provided at the shell**, the same zone boundary the theme is applied at and for the same reason.
    L1 provided it inside its `HomeScreen`, which is why its settings feature needed a second provider of its own.
  - **`LocalLockedBackdrop` is not carried.** L1's second backdrop exists so a popup menu and the widget picker can
    stay frosted when the global effect is `NONE`; L2 has neither surface, so there is one local rather than two — and
    the need it answered is now gone as well, since `Plain` still blurs and no effect leaves a surface unfrosted. What
    the effect sliders reach is `BackdropRole.PANEL`: the **container tile** and the **icon plate**.
  - **The scrim is a required fallback, not a decoration.** With no backdrop — which is the state until the user gives
    the launcher an image — every frosted surface draws its own flat color, and only the caller knows what that is.
    The folder passes `Color.Black` (its title and labels are white by construction); the shell's layer passes the
    theme's own background, which is exactly what APPS painted before. **It is now the one thing that means "nothing to
    sample"** — it used to mean that *or* an effect of `None`, and every effect blurs now.
- **The full-screen frost is `SurfaceBackdropLayer`, and it is a sibling in the stack rather than a modifier.** APPS and
  the folder paint nothing of their own and are read against one shared sheet of blurred wallpaper sitting **above HOME
  and below whatever covers it**. A frosted *panel* still samples its own crop (`wallpaperBackdrop`) and should — that
  is what makes an icon's plate read as glass sliding over the picture — but a surface that **arrives** wants the
  opposite, and that
  is the whole reason this is a separate node: the content slides while the frost only *fades*. A blur traveling with
  the content reads as a sheet of frosted plastic being carried on screen rather than as the screen frosting over.
  - **Two motions, two drivers.** `SurfacePagerState.progress` — the pan collapsed to "how far in is the other surface",
    unsigned and edge-agnostic — drives the shell's; the folder drives its own from an `Animatable` **seeded at zero**,
    which `animateFloatAsState` cannot do: that helper initializes to its target, so an overlay composed with
    `presenting = true` would snap in and a folder would fade out but never fade in.
  - **The frost is not tunable, and that is a design decision rather than an omission.** `BackdropEffect.fullScreenFilm`
    replaces the stored parameters with fixed ones, and the layer reads it *itself* rather than taking an effect, so no
    call site can hand it a tuned one. Choosing the variant chooses the whole look — a strength or tint slider that can
    make a screenful of text unreadable is not a preference worth offering. **One shared blur strength across all five
    is load-bearing**: the bitmap is blurred upstream from it, so switching variants is a redraw with a different wash
    over an identical picture, never a re-decode.
  - **The context menu and the bottom sheets wear the film's own material, not a panel's** — `Modifier.filmBackdrop`,
    which is where the three arguments that have to agree are written once: the variant at fixed parameters, the
    picture blurred to that strength (`BackdropRole.FILM`), and no rim. `SurfaceBackdropLayer` is the same call
    without a shape. The reason is that a launcher should have **one** frost: a menu on HOME at the user's blur,
    beside a sheet a swipe away at the fixed one, reads as two materials seen a second apart.
    - **Three surfaces render the user's own effect, and they are a closed list**: the **icon container**, the
      **widget container** (both `containerPanel`) and the **icon plate** (`AppIcon`). Everything else frosted takes
      the film. That is the rule as stated, and the test below is why those three and not others.
    - **The test is whose edges these are, not how big the surface is.** A bottom sheet spans the full width and sits
      on the screen's bottom edge — at `SheetHeightFraction` it covers most of the height too — so its rim would run
      along the window's own boundary, under the navigation bar, which is the case `refracts` exists to exclude. The
      menu is anchored to an item but holds a screenful of rows, and a blur slider free to reach zero would seat them
      on a sharp photograph. A **container tile** is the other answer: small, floating, four edges of its own, so it
      keeps the sliders and the rim.
    - Both gave up the blur slider along with the rim, since the picture and the recipe have to name one strength.
  - **Frost does not stack, and the rule is about *anything* already frosted** — `LocalOverFrost`. Two kinds of thing
    set it: the full-screen film (`OnFilm`) and a frosted **panel** (`OnPanel`, a container tile). It was called
    `LocalOverFilm` while the film was the only one, and that name is exactly how a container's icon plates went on
    stacking blur unnoticed — the rule was general and the name was not. An icon plate inside a container is a
    silhouette of the wallpaper sampled a second time on a tile that had already blurred it.
    - **Everything drawn on a panel is on the panel**, which is two things and they were split for a while: themed
      against it *and* forbidden from frosting again. The container's empty "+" had the first and its icons had
      neither, so the glyph read correctly while every plated icon in a container did not.
  - **A panel over the film does not frost at all** — `LocalOverFrost`, provided `true` by `AppsScreen` and by
    `AppCollectionOverlay`. Frost does not stack: below the film everything is already blurred, so a panel that samples
    the wallpaper a second time is not glass over what the user is looking at but a hole cut through to a *sharper*
    picture than its surroundings. Two consumers today: the context menu (flat `surfaceElevated` instead of
    `filmBackdrop`, which is the color it already fell back to with nothing to sample) and the **icon plate**, which
    is dropped outright rather than flattened — a plate is a silhouette *of* the
    wallpaper, and there is nothing on a film for it to be a piece of, while drawing its scrim would put a gray blob
    behind every icon. Dropping the plate also drops its `zoom`, which is a size *relative to the plate*; see
    docs/ICON_ARCHITECTURE.md.
    - **The rule lives in `wallpaperBackdrop`, not at the call sites.** With `LocalOverFrost` set it withholds the
      picture from its draw node, which reaches the flat-fill path the node already had for a device with no
      wallpaper — so *any* frosted thing raised over the film goes solid without its author having to know the rule.
      Expressed as the picture being absent rather than as a flag the node weighs, because to the drawing those two
      states are the same one, and detekt was right to reject the ninth parameter that said otherwise.
    - **`SurfaceBackdropLayer` opts out, and has to.** A film composed inside another film's subtree is routine — a
      collection opened on APPS builds its own inside `AppsScreen`'s local — so without the opt-out every one of those
      would paint a flat scrim where its frost goes. It is also the one stacking that is *wanted*: two films
      compounding is the depth cue below. The rule is about a **panel** over a film.
    - **The icon plate is the one escape**, and it is a different answer rather than a bypass: it checks the local
      itself and does not draw at all, because a silhouette of the wallpaper has nothing on a film to be a silhouette
      of, and its scrim would be a gray blob behind every icon.
    - **The menu carries the answer on its request (`MenuRequest.overFrost`) rather than reading the local.** It is
      composed at the shell as a sibling *above* every surface, so where it is drawn says nothing about what it is
      anchored to. Capturing at *open* time is sound because the menu is modal — it holds the surface gesture lock for
      as long as it is up, so nothing can arrive or leave underneath it. `MenuOverlay` re-provides the local from the
      request, so the panel asks the same question an icon plate asks.
    - This is **not** the compounding note below, which is film over film. Two films is a depth cue; a panel over a
      film is a rendering fault.
  - **A folder over the drawer is frosted twice**, so its wash compounds (≈0.35 → ≈0.58). Accepted as a depth cue —
    it *is* one level deeper — rather than plumbed, since de-duplicating means telling the folder what is beneath it.
    Invisible under `Plain`, which has no wash to compound.

## Adaptive content color

- **Text is colored against what is *immediately* behind it, which is four different things.** The launcher used to
  have one is-dark input — the wallpaper's brightness, fed to `LauncherTheme` at the shell — and that was right while
  every surface sat on the wallpaper. It stopped being right when surfaces started sitting on the **film**, which is
  the wallpaper *plus a wash*: a dark wash over a bright wallpaper leaves the shell saying "light theme, dark text"
  while APPS, the sheets and the menu are all dark. The four backgrounds, and who answers for each:

  | Background | Who themes it | Reading |
  |---|---|---|
  | the wallpaper (HOME) | `LauncherShell` | `isDarkBackground(wallpaperLuminance)` |
  | the film (APPS, collections, sheets, menu) | `OnFilm` | `LocalFilm.isDark`, resolved once at the shell |
  | a panel (container tiles) | `OnPanel` | the wallpaper washed at the **user's** own tint |
  | a solid color (settings) | its own zone | `isSystemInDarkTheme()` |

- **The wallpaper reading is a number now, not a verdict** (`WallpaperRepository.luminance`). A light/dark answer
  cannot be blended with a wash at 35%, and blending is the whole job. The threshold moved to the one place that
  still asks a yes/no question: `isDarkBackground`, at the WCAG crossover of 0.179 where contrast-against-white and
  contrast-against-black cross. `data:wallpaper` keeps its own copy of that number for one job of its own, and the
  duplication is safe because a derived constant has nothing to prefer and so nothing to drift.
- **`OnFilm` is one call for two facts, because they are one fact.** A surface arriving over the film must not frost
  itself again *and* must be themed against the film; the sets needing each are identical, and the second is the half
  nobody remembers because forgetting it is invisible until someone picks a wash that crosses the threshold. Its four
  call sites are `AppsScreen`, `AppCollectionOverlay`, `LauncherBottomSheet` and — theme only — `MenuOverlay`.
- **The film is resolved once, at the shell, and that placement is load-bearing.** Both halves pass through
  `wallpaperTone`, which is 70% `MaterialTheme.colorScheme.surfaceVariant`**, so it answers differently inside a
  subtree that has re-themed itself. Evaluating the wash per surface would paint one material in two colors — the
  folder's frost a different shade from the APPS frost behind it — and letting a surface decide its own darkness from
  a tone that depends on that decision leaves it bistable near the crossover. `LocalFilm` carries both halves
  together, since a wash weighed with one tone and painted with another is the silent disagreement they exist to
  prevent.
- **The menu is the case that proves the rule.** It is anchored to an item on HOME but frosted with `filmBackdrop`,
  so its text answers to the *film* and not to the wallpaper an inch away from it. `MenuOverlay` therefore themes it
  without setting `LocalOverFrost` — which would tell its own panel to fill flat — and over the film it does the
  opposite: the panel is flat, its scrim is a theme color, and re-theming would re-decide a question the scrim
  answers.
- **Grid labels take the theme's content color and a halo struck from the theme's background.** Both flip together:
  near-black text with a white halo on a bright wallpaper, white with a black halo on a dark one. The halo is doing
  real work rather than decorating — a wallpaper is a photograph, so its *mean* says little about the pixels under
  any one label, and a fixed black shadow only ever rescues light text. Per-label sampling would be the correct
  answer and is deliberately not built: it costs a wallpaper read per cell, re-run on every scroll and page change.
- **`SurfaceBackdropLayer` opts out of `LocalOverFrost` and is not wrapped in `OnFilm`** — a film is not drawn *on* a
  film. Two of them stacking is the deliberate depth cue below.

## Grid horizontal padding

- **Horizontal padding is width the grid does not get, and it goes *above* whatever publishes geometry** (S4g). Every
  grid has a `horizontalPaddingDp` on its blueprint (0 by default) with a per-slot × device override, and all seven
  drawn grids apply it — home's pager and dock (and, since the second pairing, its list and widget area), and the
  five APPS layouts. Two rules make it safe, and both are
  properties of where it is applied rather than of extra code:
  - **Subtract before fitting.** Cell dimensions are divided out of the remaining width, so `CellFit` must see the
    reduced area — otherwise a surface sizes cells for a width it does not have, and the settings editor offers
    columns the grid cannot draw. The APPS **pager** is the case where that would be more than cosmetic: its fit is
    also the page *capacity* the store is paginated against.
  - **Pad before the measurement, not after.** `CoordinateDragGrid`/`CoordinateDragPager` publish geometry from an
    `onGloballyPositioned` placed *after* the caller's modifier (their KDoc says so), and `AppsPager` /
    `AppsCategoryPager` register their drop zone from the same bounds — so putting `.padding()` earlier in that chain
    makes the geometry, the drop zone, the edge-flip band and the drag proxy all describe the padded box for free. A
    finger→cell read against the unpadded width would name a cell up to a whole column away near the edge.
  Consequence worth knowing: **the margin belongs to no drop zone**, so releasing a dragged item there cancels and the
  item returns. That is consistent rather than a gap — the same free slack a long-press needs to reach the surface
  rather than an item.

## The dev harness (deleted)

- **The dev harness is gone** (deleted 2026-08-19): the component gallery, the palette page and the nine playgrounds
  that prototyped the drag toolkit, the grids and the surface pan. They had done their job — every one of those
  subsystems now has a real surface exercising it — and a harness nobody opens is a second set of call sites to keep
  compiling. Notes below that say a playground *proved* or *prototyped* something are history and still true; there is
  simply nowhere to go and look any more. **What went with it, and is worth knowing:** the `IconLayers` page was the
  only place the two icon render paths were drawn side by side, so the live/bake comparison is now by eye on a device.
