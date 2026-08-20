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
  follows (`IconPreviewPlate`'s). It went in on its *first*, because the other consumers are named and blocked
  rather than speculative — HOME's "Add app", the home list's "Add apps" row, and filling a folder without
  dragging. It takes a `List<AppInfo>`, never a repository, and matches with a locale-aware `Collator` rather than
  `lowercase().contains` — the same lesson the APPS ordering already learned.

## The wallpaper-brightness signal

- **That brightness signal is L2's own idea, not a port, and it is now live** — worth knowing before looking for it
  in L1, which has no luminance analysis anywhere and themes from the system's dark mode. `LauncherShell` reads
  `WallpaperRepository.brightness` and the hardcoded `darkTheme = true` is gone.
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
    - **The rim is a *panel's*, and a full-screen surface is not one** — `wallpaperBackdrop(refracts = false)`. A lens
      needs an edge to bend light at; across a screen that band falls under the system bars, so it costs a shader and
      shows nearly nothing. What a full-screen sheet renders instead is the blur plus `BackdropEffect.saturation` — a
      `ColorMatrix` boost, no shader, every API — which is what makes frosted glass read as glass rather than as fog
      and is iOS's own recipe for its materials. Side effect worth having: **below API 33 glass now looks like
      something**, where it degraded to a plain untinted blur and was indistinguishable from `Plain`. That degradation
      is still L1's own fallback for the rim itself.
  - **The backdrop is provided at the shell**, the same zone boundary the theme is applied at and for the same reason.
    L1 provided it inside its `HomeScreen`, which is why its settings feature needed a second provider of its own.
  - **`LocalLockedBackdrop` is not carried.** L1's second backdrop exists so a popup menu and the widget picker can
    stay frosted when the global effect is `NONE`; L2 has neither surface, so there is one local rather than two — and
    the need it answered is now gone as well, since `Plain` still blurs and no effect leaves a surface unfrosted. Those
    two panels are still what the effect sliders and the glass rim are waiting for.
  - **The scrim is a required fallback, not a decoration.** With no backdrop — which is the state until the user gives
    the launcher an image — every frosted surface draws its own flat color, and only the caller knows what that is.
    The folder passes `Color.Black` (its title and labels are white by construction); the shell's layer passes the
    theme's own background, which is exactly what APPS painted before. **It is now the one thing that means "nothing to
    sample"** — it used to mean that *or* an effect of `None`, and every effect blurs now.
- **The full-screen frost is `SurfaceBackdropLayer`, and it is a sibling in the stack rather than a modifier.** APPS and
  the folder paint nothing of their own and are read against one shared sheet of blurred wallpaper sitting **above HOME
  and below whatever covers it**. A frosted *panel* still samples its own crop (`wallpaperBackdrop`) and should — that
  is what makes it read as glass sliding over the picture — but a surface that **arrives** wants the opposite, and that
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
  - **A folder over the drawer is frosted twice**, so its wash compounds (≈0.35 → ≈0.58). Accepted as a depth cue —
    it *is* one level deeper — rather than plumbed, since de-duplicating means telling the folder what is beneath it.
    Invisible under `Plain`, which has no wash to compound.

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
