# KWGT — live teardown & what it forces on our widget studio

**Captured 2026-09-07** by driving the installed `org.kustom.widget` **3.82b621115** (Pro) on the user's device over
adb — the editor entered on an empty widget, every root tab opened, one module of each data-shaped kind added and
walked, every enumerated list dumped from the accessibility tree. Screenshots live in the session scratchpad and are
not committed: it is their copyrighted UI.

This is the reference pass that [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) is for the wallpaper
studio, and it exists for the same reason — the wallpaper plan guessed sixteen designs from thumbnails and was wrong
about four of them. Nothing here is inferred from KWGT's documentation or from memory of the app.

**L1 has no prior art.** `../Morphic-Launcher` holds `AppWidgetHostController` and a `WidgetFlow` and nothing else;
there is no expression engine anywhere in it. This subsystem is greenfield in both codebases.

**Decision already taken (2026-09-07):** our widgets are **launcher-native items**, not `AppWidget`s. Several findings
below are only interesting because they are KWGT paying the AppWidget tax, and they are marked as such.

---

## The three findings that matter

1. **KWGT is a layer editor with a small interpreted language bolted to every preference.** The layer half is
   structurally what `IconLayerSpec` already is. The language half is the product. Everything else is scaffolding
   around those two.
2. **Its update cadence is a global user setting with four presets, not a consequence of what a widget reads.** A
   widget showing only a date still wakes on the minute tick, and the app asks to be exempted from battery
   optimization on its settings screen. This is the single clearest thing to do better, and it is now measured rather
   than assumed.
3. **A preset is themeable by someone who did not author it, through nine typed globals.** This — not the effect list
   — is what makes KWGT presets distributable, and it is the piece our icon studio has no equivalent of.

---

## Editor anatomy

One screen, four regions, on a 1216×2688 device:

| Region | Bounds | What it is |
|---|---|---|
| Preview | `[0,263]–[1216,1986]` — **64% of the height** | live render of the widget, at its real aspect |
| Breadcrumb rail | `[7,270]–[137,1979]`, 130px wide, left | the *path* into the item tree: `Root › Text`, one icon per level |
| Preview options | `[1079,270]–[1209,1979]`, right | `screen_bg` (cycle the preview backdrop) and `toggle_visualizer` |
| Settings sheet | `[1986,2623]` — **24% of the height** | tab row + the current node's preference list |

The proportions are the point: **the preview gets two thirds and the controls get a quarter**, with the tree reduced to
a 130px rail rather than a panel. Our icon studio already lands near this; the widget studio should not drift toward a
form with a thumbnail.

**Navigation is drill-down, not a tree view.** Tapping an item in `ITEMS` *replaces* the settings sheet with that
item's own tabs and pushes a crumb onto the left rail. There is no expanded outline anywhere. Back pops one level —
and two backs from a module leaves the app entirely, which is a real bug in their handling and worth not copying.

### Root tabs (7)

`ITEMS · BACKGROUND · LAYER · GLOBALS · FLOWS · SHORTCUTS · TOUCH`

The row scrolls horizontally and **the overflow is invisible** — `SHORTCUTS` and `TOUCH` are past the right edge on a
1216px screen and were missed on the first pass. (Same failure mode as the wallpaper studio's Style tabs; assume any
tab row is longer than it looks.)

- **BACKGROUND** — `Type` (Solid) + `Color`. The widget's own ground.
- **LAYER** — `Scale` (100), `Location` (Default GPS), `TZone` (Location Default). Note what this is: **the data
  context every formula in the widget evaluates against**, settable per widget. A second clock in another timezone is
  a whole second widget with one field changed, not a per-item override.
- **SHORTCUTS** — an author-curated quick-access list. Not fully explored.
- **TOUCH** — the widget background's action; ships defaulted to `Single ⇒ Kustom Action ⇒ Advanced Editor`.

### Item tabs

Per module kind, and they differ:

| Module | Tabs |
|---|---|
| Text | `TEXT · PAINT · FX · POSITION · TOUCH` |
| Series | `SERIES · STYLE · COLOR · POSITION · TOUCH` |
| Progress | `PROGRESS · COLOR · STYLE · POSITION · TOUCH` |

`POSITION` and `TOUCH` are on every one. `POSITION` is `Anchor` (Center) + `XOffset` + `YOffset` — **an anchor plus an
offset, not a coordinate**, which is how one preset survives being scaled to a different widget size.

---

## The object taxonomy — ten kinds, and no more

The `+` sheet at `Root › ITEMS` offers exactly ten (the list does not scroll):

| Kind | What it is |
|---|---|
| **Component** | a prebuilt reusable group (signal indicator, battery icon) or your own |
| **Shape** | rectangle, circle, … filled with color or gradient |
| **Text** | any string, from examples or written with the function set |
| **Font Icon** | scalable vector icon from an icon pack |
| **Image** | a static picture |
| **Text FX** | a line of text with transforms — bending, flipping |
| **Progress** | a progress bar in various shapes and colors |
| **Series** | a customizable series for date, time, battery and more |
| **Overlap Group** | container: transforms, grouping, gravity |
| **Stack Group** | container: items stacked horizontally or vertically |

**Two container kinds, and that is the layout model.** Overlap = free placement by anchor+offset (what our icon layer
set already does). Stack = flow layout along an axis. Our `IconLayerSet` has *only* the overlap kind, and a widget
that reads "icon, then label, then a right-aligned value" wants the stack one. This is the one structural addition the
layer model needs.

**Series and Progress are the data-shaped primitives**, and both are a preset list over a formula:

- **Series** = map a discrete quantity onto an indexed glyph set. `Mode` is `Hours (12h) · Hours (24h) · Minutes ·
  Minutes (5 mins step) · Battery · Battery (0 to 9) · Day Of Week (+Short, +Num) · Day Of Month (+Short, +Number) ·
  Month (+Short)`. Default reads `Battery (0 to 9) (Linear)` — value source, range, interpolation.
- **Progress** = `Battery · Hours · Hours (0–24) · Minutes · Minutes (5 mins step) · Music Playtime · Music Volume ·
  **Custom**`. `Custom` is the escape hatch, which tells you the other seven are conveniences over a formula, not
  separate mechanisms. Style defaults to `Flat Progress`.

Not opened: Component, Font Icon, Image, Text FX, Shape, and the two groups' own settings.

---

## The preference row is the unit of the whole editor

Every preference is one row: `icon | title | value control | checkbox`. Value controls are a button opening a dialog,
or a five-part stepper (`−−  −  value  +  ++`) for numbers.

**The checkbox is a multi-select**, and selecting rows raises a contextual bar with `copy · lock`. Item rows raise
`copy · delete · mass move`. Two of those are worth stealing:

- **lock** — pin a preference so it cannot be changed. This is a *distribution* feature: it is how a preset author
  ships something that stays looking like theirs.
- **mass move** — move selected items into another group in one gesture.

**A preference becomes a global by selecting it and pressing the globe icon** — the globals tab's own hint says so
verbatim, and adds "not all preferences supports globals". So a global is a *binding on a preference*, applied from
the preference, not a variable the item reads.

---

## The formula language

Delimited by `$…$`, function call syntax `name(arg)`, string-concatenating with literal text around it:

```
$bi(level)$%            →  92%
$bi(temp)$°$wi(tempu)$  →  36°C
$df(hh:mm)$             →  19:14
```

Two-letter namespace + a keyword argument. The default new Text item is `$df(hh:mm)$`.

### The editor is what makes it learnable

The formula screen is three stacked panes: **Text Preview** (the evaluated result, live), **Formula Editor** (an
EditText plus a seven-icon toolbar: **globe** = insert global, **palette** = insert color, **star** = save to faves,
then **B / TT / U / I** which insert BB-code markup), and **Examples** — "click appends, long replaces".

**Every example is evaluated against the real device before you insert it.** Each card is three lines: `d` the
description, `>` the *live result* (`92%`, `36°C`, `4315`), `k` the formula. Nobody reads a function reference; they
scroll a list of answers and tap the one that already says what they want. **This is the single best idea in the app
and it costs nothing structurally** — the evaluator is already there, so the picker just runs it over a fixture list.

### The 35 example families (complete, in order)

`Fave Formulas · BB code · Date Format · Music info · Location info · Current weather · Weather forecast · Air quality
· Connected network · Battery info · System info · Time span · Unread counters · System notifications · If condition ·
Text converter · Calendar events · Music queue · Astronomy info · Get from web · Traffic stats · Resource Monitor ·
Health data · Math utilities · Bitmap palette · Color editor · Color maker · Date parser · Global variable · Global
history · Broadcast · Shell command · Timer utilities · Local variable · For loop`

They split into four groups, and the split matters more than the count:

1. **Data providers** (~19) — the ones that need a permission, a network call, or a listener. Battery, music,
   notifications, calendar, weather, air quality, location, traffic, fitness, resources, unread counts, astronomy.
2. **Pure functions** (~10) — date format/parse, time span, text converter, math, color editor/maker, bitmap palette,
   BB code. No I/O, no cadence, trivially testable.
3. **Language constructs** (4) — if, for loop, local variable, global variable.
4. **Escape hatches** (3) — get from web, shell command, broadcast. `Bitmap palette` ("extract colors from images like
   album covers") is worth noting separately: it is the same job as our wallpaper palette extraction.

`Global history` — "historical value of a global by index or timestamp" — implies globals are *recorded over time*,
which is how a graph of a value is drawn without a series store. Not explored.

---

## Globals — the themeable-preset mechanism

Declared at `Root › GLOBALS` with `Title`, `Type`, `Desc`. Nine types:

`Color · Number · On/Off Switch · List · Font · Text · Bitmap · Folder · Secret`

`Secret` is for API keys. `Folder` is a set of images. `List` is an enumerated choice, which is what gives a preset a
"style" selector. Read back in formulas as `$gv(name)$`, toggled by the touch action `Toggle Global Switch`.

**`Desc` is the giveaway that globals are a published interface**, not scratch variables — nobody writes a description
for their own temporary. Advanced Options carries `Global persistence: when the preset is saved, globals on screen
should be overwritten: If changed`, which is the author/consumer boundary again.

---

## Flows — a rule engine standing in for animation

`Root › FLOWS`, each flow a `name + Triggers[] + Actions[]`, created disabled.

- **Triggers:** `Manual · On load complete · Cron · Formula`
- **Actions:** `Delay · Set Global Var · Set Local Var · Get from web · Formula · File picker · Send data`

That is the whole vocabulary. There is no timeline, no easing, no keyframe: an animation is a flow that sets a global
in a loop with `Delay`, and every bound preference re-renders. The touch action `Trigger Flow` is the manual entry.

This is a **worse** answer than we already have — Compose animates state directly and `MotionScheme.expressive()` is
already in `LauncherTheme`. Flows are what you build when your renderer is a bitmap you push over IPC once per tick.
Do not port this. The part worth keeping is the *reactive* half — `Formula` as a trigger, i.e. "when this expression
changes, do that" — which is a `snapshotFlow` in our world.

---

## Touch — and the AppWidget tax, visible

`TOUCH` on any module is a **list**, added with `+`. Each entry is a trigger plus one action. Eleven action types:

`None · Kustom Action · Launch App · Launch Shortcut · Launch Activity · Music Controls · Toggle Global Switch ·
Open Link · Trigger Flow · Change Volume · Disabled (block touch)`

**Every entry is created as `Single` and there is no gesture picker anywhere** — not in the row, not on long-press, not
in the action editor, and adding a second entry produces a second `Single`. That is the RemoteViews ceiling showing:
a widget's touch surface is a `PendingIntent` on a rectangle, so there is no double-tap, no long-press, and no swipe to
bind. `Disabled (block touch)` exists for the same reason — the rectangles overlap and one has to win. *(The absence of
the picker is measured; the RemoteViews explanation for it is inference, but it is the only one that fits an app whose
own settings screen carries a widget-size-detection workaround.)*

**Native, we get all of those for free**, and `launcherItemGestures` already carries them. Two consequences:

- The trigger is a real axis for us (`ItemGesture` exists in `core:model`), so a touch entry is `gesture × action`
  where theirs is `action` alone.
- `Disabled (block touch)` should not be ported. It is a workaround for a hit-testing model we do not have.

`Launch App` reveals exactly one further field (`App`), which is the shape to copy: an action's parameters are
*revealed by the action*, not a union of every action's fields shown greyed out. That matches our "absent, not
disabled" rule already.

---

## Update cadence — the thing to do better, measured

`Settings › Advanced Options › Update mode`, four options, **global to the app**:

| Mode | Behavior (their wording) |
|---|---|
| **Default** | update on location changes, minute tick or every 5 seconds if music is playing, slower in power save mode |
| **Conservative** | still updates on minute tick but ignores music progress and slows other changes |
| **Smart** | per-second when charging, normally when not, slowly on low battery or power save |
| **Fast** | always on the seconds tick — "WARNING this mode uses a LOT of Battery! Please do not use this on a daily basis" |

Plus `Update when Screen Off` (default false, "WARNING this is normally not needed").

And the settings screen's first row: **"Kustom is battery optimized, this might prevent the app to work properly,
click to fix"** — i.e. the app asks the user to exempt it from Doze.

**Nothing here is derived from what a widget actually reads.** A static text widget and a music-progress widget wake on
the same schedule, and the user is the one asked to arbitrate. Our version should invert this: **the evaluator reports
which providers a recipe touched, and the cadence falls out of that** — a date-only widget subscribes to the minute
tick and nothing else, and no setting is exposed at all. This is the argument for making the parser produce an AST we
can walk rather than evaluating a string in place; it is the reason `core:widgetscript` should be its own module.

Two further rows are pure AppWidget tax, listed so we remember what we are not paying:

- `Widget Size — size detection mode, change if you have padding or resolution issues, current: Locked (click on
  widget after resize)`
- `Widget Orientation — launcher preferred orientation, affects touch and object positioning, currently Default
  (Portrait)`

A launcher-native item is measured by our own layout and rotates with our own configuration. Both settings become
unrepresentable, which is the right kind of gone.

---

## Distribution

- **Export Preset** (`drawer › Export Preset`): `File type (KWGT | Image) · Title · Description · Author Name ·
  Author Email`. So the shipped unit carries authorship, and "Image" exports a render rather than a recipe.
- **Restore points**: the revert button opens `On screen preset · Auto save 32 minutes ago · Auto save 1 week ago ·
  CREATE RESTORE POINT`. Autosave is coarse (the 32-minute entry did not move across a whole editing session) and
  named restore points are manual. Our icon studio has nothing like this and probably should.
- **Explore** is *not* a marketplace. It shows "Discover new packs" (a promoted third-party pack) over
  `Installed packs: PACKS 1 — WIDGETS 24`. **A pack is a separate Play Store app containing presets.** KWGT punts
  distribution to Play entirely.

That last one is the interesting divergence from the wallpaper studio's reference: Smart Launcher hosts an in-app
community feed with likes and author attribution, KWGT ships APKs. The wallpaper plan's deferred sharing surface can
now be designed against both, and the KWGT model needs no backend at all — which makes it the cheap first step, not a
worse one.

---

## What this forces on our design

Ordered by how much it changes:

1. **`core:widgetscript` is a real module and it is the project.** Pure Kotlin, no Android, parser → AST → evaluator,
   and the AST must expose *which providers it read* so cadence is derived. Everything else here is a weekend.
2. **The layer model needs a stack container.** `IconLayerSpec` is close enough to reuse the shape of, but overlap-only
   placement cannot express a row of label + value. `Anchor + XOffset/YOffset` is their answer for the overlap case and
   it is the right one — resolution-independent by construction.
3. **Globals are the distribution interface, and they are typed.** Nine types, each with a description, bound to a
   preference rather than read by an item. This is what our icon studio's presets never had and the reason KWGT presets
   circulate.
4. **The example browser must evaluate live.** Three lines per card — description, real current value, the formula.
   This is how the language gets learned, and it is nearly free once the evaluator exists.
5. **Touch is `gesture × action`, and it is a list per item.** We have gestures KWGT cannot reach; the list-of-entries
   shape is right and `Disabled (block touch)` is not ours to port.
6. **Do not port Flows.** Their trigger/action engine is a workaround for a static render target. Keep only
   "formula as a trigger", which is `snapshotFlow` here.
7. **Preference `lock` and named restore points are cheap and both studios want them.**

---

## Not covered

- Component, Font Icon, Image, Text FX, Shape module settings; Overlap/Stack group settings.
- `PAINT` beyond `Style: Fill · Color · Filter: Normal` — the shadow/glow/emboss branches were not opened.
- `FX` beyond `Mask · Texture · Shadow` (values not walked).
- The `SHORTCUTS` root tab's actual behavior.
- `Global history`, `Broadcast`, `Send data` semantics.
- The full function list inside each of the 35 families (only Battery info was expanded). The *families* are the
  architecture; the members are a menu and are in their docs.
- `Available spaces` (their widget-slot manager) — AppWidget-only, deliberately skipped.

## Open questions

- Where does a native widget's recipe live — `data:widgets` beside the AppWidget host, or its own `data:` module? The
  host and the studio share nothing but the word "widget".
- Does a Morphic widget occupy a HOME grid cell like a container does, or is it a *source* a container can hold?
  `WidgetContainerCell` already exists and already rotates through contents.
- Is the "export as AppWidget" escape hatch worth building at all, given that it costs the touch model and the
  cadence model both? Deferred, and this teardown makes it look worse than it did before.
