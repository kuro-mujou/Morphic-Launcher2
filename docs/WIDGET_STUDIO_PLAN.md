# Widget Studio

**Status:** design locked, **nothing built** (2026-09-07). The third studio, after the icon studio (done) and the
wallpaper studio (nearly). This is the *what and in what order*; the open questions at the end are real.

**Covers:** a built-in editor for user-authored, data-bound, live-rendered widgets — plus the expression language
underneath it and the placement it takes on HOME.

**Companions:** **[WIDGET_STUDIO_TEARDOWN.md](WIDGET_STUDIO_TEARDOWN.md)** is the reference capture (KWGT 3.82b Pro,
driven over adb) and every "theirs" below points at it — read it first.
[ICON_ARCHITECTURE.md](ICON_ARCHITECTURE.md) for the layer model this borrows its shape from, and
[WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md) for the generative engine WS11 draws on.

**L1 has nothing.** `../Morphic-Launcher` holds `AppWidgetHostController` and a `WidgetFlow`; there is no expression
engine anywhere in it. Greenfield in both codebases, so there is no answer key — which is why the teardown exists.

---

## The goal, and the thing that makes it hard

**KWGT is so complex that ordinary users never author anything in it.** They download or buy a premade widget from a
designer. That is not a failure of their engine — the engine is excellent — it is a failure of the surface, and it is
the exact outcome to avoid. **Our goal is a widget studio a normal user actually creates their own widget in.**

That is a product constraint with teeth, so it needs a test rather than an intention:

> **Someone who has never opened this produces a widget they keep, on their home screen, in under three minutes,
> without typing a formula.**

When a design question below is genuinely open, that sentence arbitrates it.

### Why theirs fails, concretely

Every one of these is observable in the teardown, and each has an inversion:

| What theirs does | Why it loses a normal user | What we do instead |
|---|---|---|
| Starts on a blank canvas — `+` gives ten object types | "Add a Text object" is not a thing anyone wants | **Start from a finished template.** The first action is *make this mine*, never *add object* |
| The composable unit is a **primitive** — a Text item bound to `$df(hh:mm)$` | Nobody thinks "text object with a date format"; they think **clock** | The composable unit is a **block** — Clock, Battery ring, Weather line, Next event |
| Data binding is **only** the formula language | The language *is* the product, so the product is for programmers | Blocks bind their own data. The language is the **escape hatch**, not the entrance |
| Position is `Anchor` + `XOffset` + `YOffset` steppers | You never touch the thing you are editing | **Drag it on the canvas.** We render it, so we can hit-test it |
| Every knob visible at once — 5 tabs × ~7 preferences per item | The interesting one is indistinguishable from the other 34 | **Three tiers**, and tier 1 is the default view |
| Globals are an *advanced* feature for preset authors | The one mechanism that makes a preset re-themeable is buried | Globals **are the simple mode** — see below |

### The inversion, in one line

**Theirs is primitives + a language, with presets as a convenience. Ours is templates + blocks + style, with the
language as an escape hatch.** Same engine underneath; the pyramid is upside down.

### The three tiers

| Tier | Who | What they see | Slice |
|---|---|---|---|
| **1 — Style** | everyone | the **globals** the template declared: colors, font, a size, an on/off, a list choice. Nothing else. | WS6 |
| **2 — Blocks** | some | add / remove / reorder blocks; drag and size them on the canvas | WS7 |
| **3 — Advanced** | few | primitives, layer stack, the formula editor | WS8–WS9 |

**Tier 1 is not a stripped mode — it is the same mechanism the reference reserves for experts.** A block declares its
parameter surface; inserting it promotes that surface into the recipe's globals; the Style tab is those globals. So
the work that makes a preset distributable is the same work that makes it editable by someone who did not author it.
One mechanism, two payoffs, and it is why globals land at WS6 rather than late.

---

## Locked decisions (2026-09-07, author-confirmed)

1. **Native only. There is no AppWidget export, and there will not be one.** A Morphic widget runs on Morphic. Not
   "deferred" — rejected, so nothing below may be shaped to keep the door open. What it buys: real per-item gestures
   instead of a single tap on a rectangle, a redraw cadence we own instead of one the system rations, and no bitmap
   crossing a Binder transaction. The teardown records all three as costs KWGT pays.
2. **A widget takes its own grid placement**, exactly as a hosted AppWidget does — a `*_placement` row with `zone`
   and `GridPlacement`, per orientation, dragged and resized on the grid. It is **not** merely something a
   `WidgetContainer` can hold.
3. **Containment is left room for but not built.** When a container is taught to hold ours, its membership row
   becomes **exactly one of** app-widget-or-ours — `IconContainerItemEntity`'s shape, which `apps_pager_item` has
   already been reshaped into once. Not before something exists worth paging between.
4. **The word "widget", unqualified, means ours.** Everything hosting another app's is renamed `AppWidget*` (WS0).
   Two meanings of `WidgetCell` in one codebase is not a naming preference, it is a bug waiting.
5. **The studio is a package in `feature:settings`**, beside `iconstudio/` and `wallpaperstudio/` — not its own
   feature module. Settings is the surface; a studio is a route within it, and both existing ones settled this.
6. **The primitive layer is built, but it is never the entrance.** Blocks are made *of* primitives and the block
   library is authored in our own editor — so tier 3 exists from the inside out. What is decided is the *default
   path*, not the ceiling.

### The rule that governs the whole subsystem

**A recipe declares what it reads, and the cadence is derived from that declaration.** Never a setting, never a
global tick. This is the single thing the reference gets wrong — their update mode is four user-visible presets, none
derived from a widget's own content, which is why their settings screen asks to be exempted from Doze — and it is why
the parser produces an **AST we can walk** rather than evaluating a string in place. A date-only widget subscribes to
the minute tick and nothing else, and the user is never asked.

---

## Module map

| Module | Holds | State |
|---|---|---|
| `data:appwidgets` | hosting *other apps'* widgets — `AppWidgetHostController`, `AppWidgetCatalog`, provider sizing | **renamed** from `data:widgets` (WS0), code unchanged |
| `core:model` → `widget/` | `WidgetRecipe`, `WidgetLayerSpec`, `WidgetSource`, `WidgetGlobal`, `TouchBinding` | new package beside `icon/` and `wallpaper/` |
| **`core:widgetscript`** | lexer → parser → **AST** → evaluator; the provider-declaration walk | **new, pure Kotlin, no Android** |
| **`core:widget`** | the live Compose renderer — parallel to `core:icon` | new |
| **`data:widgets`** | recipe persistence, the block/template library, the data providers as `Flow`s, `CadencePolicy` | new, name freed by WS0 |
| `feature:settings/widgetstudio` | the editor | new package |

**Why `core:widgetscript` is separate, and pure Kotlin.** Not tidiness: an Android-free module *cannot* reach a
provider directly, which forces the evaluator to take its data as an argument and forces the AST to declare what it
needs. Fold it into the renderer and within two slices something calls `BatteryManager` inside a function evaluation,
and derived cadence is quietly gone. It is also where every bug will be, and the only part trivially unit-testable.

**Why the model is in `core:model`.** Both existing studios put theirs there (`icon/IconAppearance`,
`wallpaper/WallpaperRecipe`), and the recipe is read by the renderer, the editor and the repository alike. The AST is
*not* model — it is a parse product, rebuilt from the stored string, never persisted.

---

## The model

Sketch, not a signature — it gets written when WS2's consumer needs it.

- **`WidgetRecipe`** — the stored unit: an ordered `List<WidgetLayerSpec>`, the declared `List<WidgetGlobal>`, a
  background, a default cell span, and the widget's own data context (location + timezone). One serialized blob per
  design, **not flat columns** — the lesson `IconAppearance` already paid for.
- **`WidgetLayerSpec`** — one item: `source`, placement, opacity, blend, effects, touch bindings. Deliberately close
  to `IconLayerSpec`, which is already this shape.
- **Placement is `anchor + offsetX/offsetY`, not a coordinate.** Theirs is, and it is right for the reason a
  coordinate is not: a recipe must survive being drawn at a different cell size, and an anchored offset does that by
  construction. The user never types either — they drag, and the drag resolves to the nearest anchor plus an offset.
- **`WidgetSource`** — a sealed interface: `Text`, `Shape`, `Image`, `Progress`, `Series`, `Generated` (a
  `core:graphics` design), `Stack`, `Overlap`. A container is a *source* holding children, so one type covers leaves
  and groups both.
- **Two container kinds, and that is the layout model.** `Overlap` is free placement by anchor+offset — what
  `IconLayerSet` already does and all it can do. `Stack` is flow along an axis, and it is the one structural thing
  the icon layer model lacks: "icon, label, right-aligned value" is not expressible by overlapping.
- **A bound value is a `String` holding the formula**, not a parsed tree. The tree is derived on load; storing an AST
  would make every parser change a migration.

### Blocks and templates — the same thing at two sizes

A **block** is a recipe fragment with a declared parameter surface: a Clock, a Battery ring, a Weather line. A
**template** is a whole recipe with one. Both are the reference's object type #1 ("a prebuilt reusable component…"),
which theirs buries as one of ten and treats as optional. Here it is the main road.

**Inserting a block expands it into layers *and* promotes its declared parameters into the recipe's globals.** That
single behavior is what makes tier 1 work: after inserting a Clock, the Style tab gains "Clock color", "Clock font",
"24-hour" — and a tier-1 user never learns that a Text layer with a `$df(…)$` binding is underneath.

**Expansion is frozen, not a live reference.** The block's layers become the user's layers, editable and never
clobbered by a library update. The cost is that improving a shipped block does not improve existing widgets; the
alternative is a versioning problem in the first slice that ships, and this is not the subsystem to spend that on.

---

## The language

Borrow the surface syntax wholesale — `$…$` delimiters, `namespace(arg)` calls, literal text concatenating around
them — because it is what preset authors coming from KWGT already read, and there is nothing to improve in it:

```
$bi(level)$%             →  92%
$df(hh:mm)$              →  19:14
```

Four groups, and the split is what the module is organized around (the reference's 35 families, mapped):

| Group | ~n | Where | Cadence |
|---|---|---|---|
| **Pure functions** — date format/parse, time span, text, math, color | ~10 | `core:widgetscript`, no dependencies | none |
| **Language** — `if`, loop, local var, global var | 4 | `core:widgetscript` | none |
| **Data providers** — battery, clock, music, notifications, calendar, weather, network, resources | ~19 | `data:widgets`, each a `Flow` | **declared per provider** |
| **Escape hatches** — HTTP fetch, broadcast, shell | 3 | deferred; shell probably permanently | — |

**`WidgetExpression.providers: Set<ProviderId>`** is the load-bearing API. `CadencePolicy` folds the set over a
recipe into one subscription plan; a recipe reading nothing subscribes to nothing and is drawn once.

**Scope note the goal buys us:** if normal users never write formulas, the 35 families stop being a target. **Cover
eight providers well** — clock, battery, music, weather, calendar, notifications, network, system — because the
blocks are what most people will ever touch, and a block that works beats a namespace that exists.

**When the formula editor does appear (WS9), the example browser is not documentation.** Theirs renders every example
as three lines — description, **the live evaluated result on the real device** (`92%`, `36°C`, `4315`), then the
formula. That is how the language gets learned, and it costs almost nothing once the evaluator exists: a fixture list
run through the same evaluator the widget uses.

---

## Render — one composable, two data sources

**The icon studio's standing hazard returns here in a worse form.** There, the baked and live paths could disagree.
Here the studio preview and the placed widget differ in *data* as well as context — the preview may bind sample
values where HOME binds real ones — so a widget can look right in the editor and be wrong on the grid, and the editor
structurally cannot show you.

The rule, and it is the one thing here that must not be traded away: **one composable, and the data is a parameter.**
`WidgetRender(recipe, data)` where `data` is a snapshot interface; the studio passes a fixture implementation and
HOME passes the live one. There is no "preview renderer". If a second render path ever appears, it appears with a
shared derivation between them — never a shared intention.

No baking. An icon is baked because it is drawn sixty times on a page and never changes; a widget is drawn once and
changes every minute. A baked widget is a screenshot.

---

## Persistence

| What | Where | Per-orientation |
|---|---|---|
| The recipe | `widget_design` — one row, one serialized blob, like a detached icon | no |
| Its position on HOME | `widget_design_placement` — keyed `designId + orientation`, carrying `zone` + `@Embedded GridPlacement` | **yes** |
| Global *values* for a placed instance | on the placement row or its own table (WS6 decides) | no |

This mirrors `widget`/`widget_placement` exactly, which is the point — a Morphic widget is a HOME item of the same
kind as a hosted one, so it stores the same way. Coordinate placement, per the arrangement table in CLAUDE.md.

**The recipe and its instance values are different rows on purpose.** A design is authored once; the same design
placed twice with different global values is two instances of one recipe. That is what makes the same template
usable twice without a copy — and tier 1 depends on it.

---

## The editor

Take the reference's proportions, which are measured and good: **preview ~64% of the height, controls ~24%**, the
item tree reduced to a ~130px breadcrumb rail rather than a panel. Our `StudioChrome`, `StudioLayerRail`,
`StudioControls`, `StudioColorPicker` and the tool-panel pattern already exist in `iconstudio/` and are what to reuse
rather than re-derive.

**But the default view is the Style tab, not the item list** — that is the whole reframe in one sentence. Drilling
into an item is tier 2; the layer stack is tier 3 and reached behind an explicit "Advanced".

**The canvas is manipulable, and this is the biggest departure.** Tap a block on the preview to select it, drag to
move, pinch or handle-drag to size. Their preview is inert and every position is a stepper. We render our own tree so
hit-testing is ours, and `LauncherDragCell`'s lesson applies: an item's touch target is its **visible extent**, never
its bounding cell.

Two things to take from their preference rows and one not to:

- **Take** per-preference multi-select with `copy` and **`lock`** — lock is how a shipped template keeps looking like
  its author's.
- **Take** `mass move` on items.
- **Do not take** their back handling: two backs out of a module leaves the app.

An action's parameters are **revealed by the action** (`Launch App` shows exactly one further field), which is our
"absent, not disabled" rule already.

---

## Slices

The order front-loads the tier-1 path: **the product is usable and shippable at WS6**, before any authoring UI
exists. Everything from WS7 on raises the ceiling rather than making it work.

| # | Slice | Delivers |
|---|---|---|
| **WS0** | **The rename** | `data:widgets` → `data:appwidgets`; `WidgetInfo`→`AppWidgetInfo`, `WidgetCell`→`AppWidgetCell`, `widgetpicker/`→`appwidgetpicker/`; tables `widget`→`app_widget`, `widget_placement`→`app_widget_placement`. **Container types keep their generic names** — a container will eventually hold both kinds. |
| **WS1** | **The language** | `core:widgetscript`: lexer, parser, AST, evaluator over pure functions + `if`. `WidgetExpression.providers` present and empty. Heavy unit tests; this is the slice that earns them. |
| **WS2** | **The model** | `core:model/widget`: recipe, layer spec, Text/Shape/Image sources, `WidgetGlobal`, block declaration. Serialization round-trip test, as `WallpaperRecipeTest` does. |
| **WS3** | **The renderer** | `core:widget`: `WidgetRender(recipe, data)` for Text/Shape/Image in an Overlap container, anchor+offset placement. Test harness, no persistence. |
| **WS4** | **Providers + cadence** | `data:widgets`: clock, battery, system as `Flow`s; `CadencePolicy` folding `recipe.providers`. **Device-verified: a date-only widget does not wake per second.** |
| **WS5** | **Placement** | `widget_design` + `widget_design_placement`; added from the picker, placed, dragged, resized on HOME. First slice visible on the launcher. |
| **WS6** | **Templates + the Style tab** ⟵ *the tier-1 product* | ~12 finished built-in templates, each declaring its globals; a picker that shows them rendered with live data; the Style tab editing those globals; per-instance values. **After this a normal user can have a widget they styled themselves, and nothing else has to exist.** |
| **WS7** | **Blocks + direct manipulation** | The block library; add / remove / reorder; select-drag-size on the canvas. Tier 2. |
| **WS8** | **Primitives + item editor** | The layer stack, per-item tabs, Stack container, Progress and Series. `Progress` takes a `Custom` formula source from the start — theirs proves the presets are conveniences over one mechanism. Tier 3, behind "Advanced". |
| **WS9** | **Formula editor** | The field, the live-evaluated example browser, faves. The escape hatch, and deliberately the last authoring surface built. |
| **WS10** | **Touch** | A list of `gesture × action` per item. Gestures we have and they cannot: double tap, long press, swipe. No `Disabled (block touch)` — a workaround for a hit-testing model we do not have. |
| **WS11** | **Generated backgrounds** | A `core:graphics` design as a layer source, sharing the wallpaper's palette. **The differentiator** — the reference has nothing like it, and every good preset over there is a hand-drawn PNG. |
| **WS12** | **Distribution** | Export/import a recipe with authorship metadata; preference `lock`; named restore points (which the icon studio wants too). |

**Why this order.** The language first because every later slice consumes it and its `providers` API constrains WS4.
The renderer before the providers so there is something to look at. WS6 as early as the engine allows, because it is
the slice the goal is written about — and because a template library is the only honest way to find out whether the
engine can express what people want before an editor is built on top of it. WS11 late because it is the most fun and
least structural; it would otherwise absorb the schedule.

**WS6 carries a risk worth naming: twelve good templates is a design job, not an engineering one**, and a studio
whose templates are mediocre fails the three-minute test no matter how good the engine is. The wallpaper studio hit
exactly this — sixteen correct generators that looked wrong — and the recovery cost more than the generators did. Budget
it as its own work.

---

## Deliberately not built

- **Flows** (their trigger/action rule engine: Manual/On-load/Cron/Formula → Delay/Set-global/Set-local/Fetch/…). It
  is what you build when your render target is a static bitmap pushed once per tick. Compose animates state directly
  and `LauncherTheme` already carries `MotionScheme.expressive()`. The half worth keeping is *formula as a trigger*,
  which is a `snapshotFlow` here and needs no vocabulary of its own.
- **AppWidget export**, per locked decision 1.
- **Shell command** as a formula function. Arbitrary shell out of a launcher's widget, in a format designed to be
  shared, is a distribution channel for something other than widgets.
- **A user-visible update-rate setting.** If cadence needs a setting, the derivation is wrong.
- **A blank-canvas entry point.** "New empty widget" may exist behind Advanced; it is not on the way in.
- **`Disabled (block touch)`** — see WS10.

---

## Open questions

- **How does a widget declare its size, and what does resizing do?** A hosted AppWidget states
  `targetCols`/`targetRows`; ours is authored, so the recipe carries a default span — but does resizing on the grid
  re-lay the recipe (anchors re-resolve, which is what anchor+offset is for) or scale it? Their `Scale` knob suggests
  they scale. Ours should re-lay. Needs deciding before WS5.
- **What is the template *picker* — a grid of thumbnails, or the wallpaper studio's swipe-through-designs?** The
  latter is already built next door and shows each design with live data, which a thumbnail cannot.
- **Is a 1×1 widget worth designing for?** Nothing stops it once we render — a live tile in the icon grid (a date, a
  battery ring, a next-event chip) is a real idea the AppWidget-shaped framing hides, and it may be the *most*
  tier-1 thing here. Cheap to allow, expensive to retrofit if the editor assumes a large canvas.
- **Weather needs a provider and a key.** Every reference implementation ends up on a paid tier. Deferred past WS4,
  but it is the most-asked-for widget content and the answer is not technical.
- **Notification and calendar access are runtime permissions with real friction** (`NotificationListenerService` is
  a settings-screen grant). Which providers are worth the prompt, and when it is asked, is a product decision WS4
  should not quietly make — and for a tier-1 user, a template that needs a permission before it renders is a template
  that fails the three-minute test.
