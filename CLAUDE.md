# CLAUDE.md

Guidance for Claude when working in **Morphic Launcher 2** — an Android launcher, mid-rewrite.

## What this project is

A **ground-up, bottom-up rewrite** of Morphic Launcher, with two intertwined aims:
1. the author (a **junior Android dev**) understands every layer, and
2. the codebase comes out **clean**.

Launcher 2 is a **refactor, not a re-type**. The full plan, phase checklist, and per-module
build map live in **[docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md)** — read it before doing
anything structural; it is the source of truth for *what* to build and *in what order*.

## How to work here (read this first)

This is the part that isn't derivable from the code:

- **Claude writes the code; the author reviews to learn.** The work is deliberately split into small,
  self-contained parts. For each part: Claude implements it, then the author joins, reads, and gives
  improvements before moving on. Keep each part small enough that the author can fully read and
  understand it — the learning aim still stands (the author must understand every layer), it's just
  reached via review rather than by typing. So: explain the *why* of each change (in KDoc and in the
  summary), call out the design decisions you made and any alternatives you rejected, and prefer several
  small reviewable commits over one large drop.
- **Never port Launcher 1 verbatim.** The original (aka "L1", root Gradle project `Launcher`) is the
  **reference / answer key**: it runs, but it's fragile and smell-ridden (duplication, poor
  separation, logic in the wrong layer). Never delete it; compare against it, then do it *better*.
  - **Its folder name differs per machine, so look before assuming one.** It is a **sibling of this repo**, named
    `../Morphic-Launcher` on the home machine and `../launcher` on the work machine. Run
    `ls ../Morphic-Launcher ../launcher` (or `ls ..`) once, at the point L1 is first needed — a hardcoded guess is
    wrong on one machine every time, and the failure is silent in the worst way: a missing directory reads as "L1
    has nothing on this", so the comparison this whole rule exists to force gets skipped rather than reported.
  For each piece: understand what L1 does and *why* → question the design (duplicated? honest name?
  right module/layer?) → fix the smell in L2. Worked examples of this mandate:
  `GridBlueprint` (centralized scattered grid config; dropped a wrong interface abstraction + dead
  `max` fields); `DeviceConfiguration` (split the pure enum into `core:model`, detection stays in
  `core:designsystem`); `GridPlacement` (merged near-duplicate `GridRect` + `AppPosition` into one
  type — rejected a `Vector` name because it carries spans, so it's a box, not a vector); the grid blueprints
  (`Drawer{Classic,Pager,Grouped}Grid` → `Apps{Scroll,Pager,Category}Grid` — "drawer" is vocabulary the surface
  taxonomy abolished, and `feature:apps` would have re-imported it the moment it consumed one).
- **KDoc is mandatory.** Every class / interface / enum / top-level gets a KDoc stating what it is
  and what it's for. Document non-obvious members too (params, edge cases, units). Explain *purpose*,
  not line-by-line narration. (This deliberately differs from L1's "docs on request only" rule.)
- **No model in a vacuum.** Build a module/type only when a consumer needs it, so it has context.
- **Add dependencies as needed.** Each module's `build.gradle.kts` gets deps added *as the code that
  needs them is written*, not up front.
- **Commit straight to `master`. Never create a branch unless the author asks for one** — no feature branches, no
  "session" branches, no branch-then-merge for a piece of work. This overrides any general habit of branching off
  a default branch, and the reason is that the conditions that habit protects against are all absent here: one
  developer, nothing released, no CI/CD, and so no `master`/`dev` split to keep honest. A branch here buys nothing
  and costs a merge the author did not ask for. **Small, self-contained commits are still the rule** — that is the
  review workflow above, and it is a property of how the work is *split*, not of where it lands.

## Architecture at a glance

Multi-module Gradle build. Package root `inkspire.morphic.*`; appId `inkspire.morphic.launcher`;
root Gradle project name `Launcher2`.

- **`core:*`** — `model` (plain Kotlin data shapes), `common` (DI + coroutine plumbing), `database`
  (Room), `icon`, `designsystem` (Compose), `navigation`.
- **`data:*`** — `apps`, `icons`, `layout` (the highest-logic module — placement engine), `settings`,
  `widgets`. Each exposes repositories the UI consumes.
- **`feature:*`** — `home`, `apps`, `settings`, `shell`. One module **per surface**, not per look: `apps` is the
  whole APPS surface and picks its arrangement from `AppsLayout` internally, which is why L1's separate
  `appdrawer` + `applibrary` modules are gone rather than ported.
- **`app`** — the launcher application shell.
- **`build-logic/convention`** — custom Gradle convention plugins (see the module→plugin table in
  the rewrite plan). Versions are centralized in `gradle/libs.versions.toml`.

Stack: Kotlin, Jetpack Compose, Room, Koin (DI), coroutines. Typesafe project accessors are enabled.

## Core domain model — the "surface" taxonomy

The `core:model` layer is deliberately named with **one suffix per concept**; keep any new model
consistent with this. See [core/model/.../Surface.kt](core/model/src/main/kotlin/inkspire/morphic/core/model/Surface.kt).

- **`Surface`** — a full-screen destination gestured between: `HOME` (center) + side surfaces like
  `APPS`. All values are peers.
- **`HomeEdge`** — `LEFT/RIGHT/TOP/BOTTOM`; the edges of HOME you swipe from to reach a side surface.
  Each edge is bound independently; the binding config lives in the **settings layer, not the model**.
- **`HomeLayout`** — coupled main-area + side-zone combos (`PAGER_WITH_DOCK`,
  `LIST_WITH_WIDGET_AREA`). Modeled as one enum so illegal pairings are unrepresentable.
- **`HomeZone`** — placement regions within HOME: `MAIN`, `DOCK`, `WIDGET_AREA`.
- **`AppsLayout`** — how the APPS surface renders (unifies old "drawer" + "library"; layout alone
  decides the look).

When adding model, prefer: make illegal states unrepresentable, one honest name per concept, and
pure enums/data in `core:model` with detection/logic pushed to the appropriate layer (e.g.
`DeviceConfiguration` is split — pure enum in `core:model`, detection in `core:designsystem`).

## Layout & arrangement persistence (locked 2026-07-23)

How each surface stores *where its items go*. **Two primitives cover everything** — pick the right one
when adding any new placement:

- **Coordinate** — item → `GridPlacement` (page/row/col/spans), stored **per orientation**, gaps
  allowed. Used **only by HOME** (pager main, dock, widget area) and home folders/containers. Lives in
  Room `*_placement` tables keyed by owner + orientation, each row carrying a **`zone: HomeZone`**
  (MAIN/DOCK/WIDGET_AREA). The zone is a *column, not part of the key* — which is what lets a drag between zones
  re-stamp the same row instead of needing a new op or a migration.
- **Order** — item → an ordinal within a bucket (1-D flow); the render layer re-paginates it. Used by
  **everything else**.

| Surface / layout | Kind | Store | Per-orientation |
|---|---|---|---|
| HOME pager / dock / widget area | coordinate | `*_placement` + `zone` | yes |
| HOME vertical list | order | `home_list_item` | no |
| APPS vertical list / grid | derived (A–Z) | none | — |
| APPS pager | paged order (page + in-page slot) | `apps_pager_item` | yes (two lists) |
| APPS pager-w/-category + category card | order within category | `category` + `category_item` | no |
| Folder contents | order (dense) | `folder_item.sortOrder` | no |

Key rules:
- Only HOME **coordinate** placements and the **APPS pager** are per-orientation; everything else is a
  single orientation-independent list.
- **APPS pager** stores an explicit page + in-page slot — pages are hard boundaries (a move compacts
  only the source page; overflow cascades forward). It keeps **two saved lists** (portrait + landscape),
  re-paginated in **repository logic** on first rotate — the DB just holds both lists.
- The two category layouts **share** one `category` + `category_item` store.
- **Categories (defs + membership) live in Room**, not the settings blob — users create custom ones.
- L1's conflated `surface` column became `zone`; L1 `Surface{HOME,DOCK,WIDGET_AREA}` split into L2
  `Surface{HOME,APPS}` + `HomeZone`.
- **HOME's vertical list is a store of its own, seeded from the grid — not a view of it.** `HomeListRepository`
  (`data:layout`, the third repository beside `LayoutRepository` and `AppsOrderRepository`) owns `home_list_item`,
  and the split is what makes HOME's two pairings independent. L1 derived its list *live* from the pager's
  placements, flattened by (page, row, col), so one drag in the list wrote `MoveApp(page = 0, row = i, col = 0)` for
  every app and destroyed the grid arrangement permanently. The good half of that idea survives as
  `seedIfEmpty(readingOrder(...))`, run when the pairing is first chosen: switching to the list hands the user their
  apps in the order they already recognize, rather than a blank screen with no picker to fill it. Membership is fixed
  by `setOrder`, which **reconciles against what is stored** (`reconcileReportedOrder`) — the guard is in the store
  rather than at the call site, as `AppsCategoryChange.Reorder`'s is, because the caller has nothing true to
  reconcile against.

**Settled for the APPS pager: it holds folders, and a folder's slot *is* its row.** `apps_pager_item` was keyed on
`component`, so it could only hold an app, and an APPS-hosted folder had nowhere to store its position. Both were
answered by one reshape (DB v2): the row became **exactly one of** app-or-folder — `IconContainerItemEntity`'s
shape, which `IconItem`'s KDoc had already predicted by naming "the `Surface.APPS` pager and an `IconContainer`"
as its two holders. There is no `apps_pager_placement` table and should not be: an ordered surface stores a slot,
not a coordinate. One difference from `icon_container_item`, and it is silent when wrong — the unique indices are
scoped **per orientation**, since the pager keeps two saved lists and an app appears once in each.

**Settled: neither category layout holds folders, so `category_item` stays keyed on `component`.** The reshape the
pager needed is **not** owed here — no migration. The **pager** (`PAGER_WITH_CATEGORY`) has one reason: a category
*is* the grouping, so a folder inside one would be a second, redundant one. Its pages are dragged between (carrying
an app to another page is how it changes category) and its cells split into **halves** with no center merge ring,
since there is nothing to merge into — which is what `CategoryPagerPlayground` prototypes. The **card**
(`CATEGORY_CARD`) has that reason *plus* one of its own: a card is already a folder in everything but name — a
titled tile previewing a collection, which opens into a bounded grid — so a folder on a card nests a grouping inside
something that looks identical to it, and its 2×2 preview tile would have to render inside one of four preview slots
that are themselves 2×2, at ~20dp a side. Two independent reasons, either sufficient.

Full rationale: [docs/REWRITE_PLAN.md](docs/REWRITE_PLAN.md) → "Arrangement persistence model".

## Containers (icon & widget)

Both are **standalone items on the HOME grid** (each occupies a `*_container_placement` slot); their
*contents* are **ordered** within the container (`sortOrder`), not individually grid-placed. Both are added
from the **widget picker** and both start **empty** — just a "+" button.

- **Icon container** — holds apps and/or folders. Fill it via the "+" button (opens an app picker) or by
  dragging an app/folder onto it. Each membership row is **exactly one** of app-or-folder, and an app/folder
  lives in **at most one** icon container (mirrors how a folder holds an app once). See
  [IconContainerItemEntity.kt](core/database/src/main/kotlin/inkspire/morphic/core/database/entity/IconContainerItemEntity.kt).
- **Widget container** — holds widgets. Created two ways: from the widget picker (empty → "+" opens widget
  setup), or by **dropping one widget onto another** (combines both into a new container). Each widget lives
  in at most one container.

The "combine" and "extract" flows (drop-to-merge, empty-container placement, moving contents in/out) are
**repository logic**, not entity structure — the entities just express membership + ordering + the container's
own grid placement.

## Icon feature — layer-based editor + baked display (locked 2026-07-23; **built 2026-08-11**)

The icon system is a **layer editor** (like a drawing app) whose output is a **single flat bitmap** shown on
every surface. Distilled from L1's `ICON_LAYER_STUDIO_PLAN` — adopt its end-state, skip its flat-column churn.

**This is now built, S1–S7 of [docs/ICON_STUDIO_PLAN.md](docs/ICON_STUDIO_PLAN.md)** — one plan replacing L1's
*five* icon docs, which read in date order are a churn log rather than a spec (its persistence model reversed
three times inside one document, at a cost of four destructive schema bumps on one table). What is left from *that*
plan is **icon packs** and **presets**. The studio has since outgrown it: a second plan,
[docs/ICON_EFFECTS_PLAN.md](docs/ICON_EFFECTS_PLAN.md), takes the effect list from two to thirteen and is **complete** — the effect **pipeline**, the effect **panel**, the **filter** library, the
**layer rail**, **Bloom**,
**Gloss**, **perspective**, **Pattern**, **Extrude** and **Chromatic split**, plus **whole-icon effects**, which that
plan had not noticed it needed.
The **six remaining effects are all blocked on one mechanism** — the bake-backed preview — which is also what
un-defers the shadows this file has been holding back since B3; see that note below. The rest of
this section describes what exists, and flags the places the built thing differs from what was locked here.

**Source & parsing.** App icons come from the `LauncherApps` API. Each is parsed into **two permanent,
non-deletable layers**: a **background** and a **foreground** (fg always renders above bg). Parsing never
splits the foreground further — a legacy raster and a modern adaptive foreground both just *are* fg content
(no glyph matting; it's unreliable). All backgrounds land in the bg layer, **even when empty** (the empty bg
slot still exists for the user to fill).
- **Legacy icons**: the whole bitmap → fg layer; sample the edge ring and, if it is one flat opaque color,
  resolve the bg layer to it; busy/transparent edges → leave bg empty. **L1 has no implementation to port** — its
  `parseLegacy` uses a hardcoded plate color and the sampling never left its plan — so the thresholds are ours,
  and building it corrected this rule's own claim. "Invisible until the foreground is shrunk" is not a property of
  the fill, it is a property of *which icons are accepted*: a rounded legacy icon has transparent corners, so
  painting its plate color behind it would **square the icon off**, and a drop shadow's soft edge would fill the
  gap the shadow leaves. So the solid-fraction threshold is **near-total (95%)** rather than a majority — those
  cases are declined, and for the ones accepted not one pixel moves until the user moves the foreground. The
  color is **resolved, never written into the recipe**: the app still reads "app default", so Reset and
  inheritance behave normally and an app that updates its artwork gets re-detected instead of keeping a frozen
  color. `LegacyBackground` is the pure decision (unit-tested; **the refusal tests are the ones that matter**),
  `DrawableParser` the rasterizing.

**Layer content** is a small sum type, not always an image: **app-default (parsed image or color)**,
**custom image**, or **solid-color fill** (a color-only background is a `SolidFill` bg).

**Foreground monochrome — one control, and `IconLayerResolver` decides what it means.** An app may ship a real
**monochrome icon** (the OS themed-icon layer), stashed aside at parse time as an alternate fg source rather than
becoming a third stack layer. The fg offers a **toggle** for it, and **which of two mechanisms fires is resolved per
app at render time**: an app that ships one gets it; an app that does not gets its foreground drained of color
(`saturation = 0`, folded into the spec the resolver hands back, so both render paths get it with no change of their
own). **This departs from what was locked here**, which said the toggle appears only for an app *with* a monochrome
icon. That was written before the global studio, where one recipe covers apps that differ, so `hasMonochrome` has no
single answer — and the old fallback drew the *unfiltered* foreground, making the choice a silent no-op on every app
without a themed layer. Two consequences worth keeping straight:
- **The two monochromes are different mechanisms and only one gets a button.** `LayerSource.AppDefaultMonochrome`
  swaps in *different artwork*; `LayerEffect.Color(saturation = 0)` recolors *whatever a layer already holds*. The
  source one is the toggle; the filter one is the Saturation slider, which is what it is. A "Monochrome" toggle beside
  that slider was built and removed — it is a lossy alias (switching it off has to invent a value to return to), and
  it would make one word mean two mechanisms visibly.
- **It is a refinement of a source, not a source of its own** — so in `SourceControls` it is a row *under* the tile
  row, shown only on the foreground while the app's own artwork is chosen, exactly the shape the pack "choose a
  different icon" row already has. As a fourth tile it would read as a peer of a pack and an image, and would appear
  on one layer only, so the row would change length as the selection moved. **The background gets neither form**: the
  platform ships one silhouette and it is for the fg slot, so the source has nothing to resolve there, and a gray
  plate is already the Saturation slider's. The themed *look* — flat plate behind a tinted glyph — is `SolidFill` on
  the bg plus this layer's own tint, three controls that already exist.

**`TintMode` — app-shipped themed layers do not agree with each other, and a multiply cannot fix it.** On a real
device they arrive black, white, colored, or not a silhouette at all; the platform's contract is that **only their
alpha is meaningful** and the consumer tints them. `LayerEffect.Color.tintArgb` was a pure multiply (`scaleMatrix`),
and black times any tint is still black — so a black glyph could never be made white and the inconsistency was
unreachable from the UI. `TintMode.SOLID` replaces the color and keeps the alpha, which is `SRC_IN` tinting expressed
as a **color matrix**, so it stays one shared `FloatArray` and neither renderer learned a second kind of filter. The
control is "Tint style: Shaded / Solid", shown only once a tint exists. Four things to know:
- **The fifth column is a translation on a 0..255 scale, not 0..1.** Every other matrix in `LayerFilter` leaves it at
  zero, so the question had never arisen; a 0..1 value there comes out visually black, silently. Pinned by a test.
- **Solid spends the recoloring before it** — hue, saturation and brightness all act on channels it overwrites. That
  falls out of the matrix having no color coefficients rather than being special-cased, and it is correct: a flat
  color has no shading left. Those three sliders stay visible under it (the flip is one tap, and hiding three
  controls behind a toggle makes the section jump) — the one place this file's "a control that changes nothing is
  worse than a missing one" rule is knowingly not applied.
- **The monochrome *fallback* downgrades SOLID to MULTIPLY, and that is the one place the renderer overrides the
  user.** A solid tint keeps only alpha, which is right over a themed silhouette and disastrous over an ordinary
  foreground — an adaptive foreground's alpha is usually a large blob, so the icon becomes a colored splodge. It
  matters most globally, which is the whole point of the setting: "every icon a flat white glyph" is one edit there,
  and without the downgrade it would silently produce blobs for every app with no themed layer. The tint is kept and
  only its mode changes, so the chosen color still shows as a tinted grayscale.
- **Additive, no schema change** — a defaulted field with `encodeDefaults = false` on both stores, so stored recipes
  read back unchanged and new ones do not grow. Same deal the sealed effect list already gives new effects.

**Editor.** fg/bg are the base; the user inserts **custom layers below bg / between fg&bg / above fg**. The
only ordering rule is **fg stays above bg** (customs are otherwise free), and **reorder is buttons, not drag** —
L1 locked buttons for the right reason and then reversed itself in a later plan; a *disabled button* says which
move is illegal before it is attempted, where a refused drag does nothing and cannot explain itself. The buttons
are disabled by asking the model (`editing.moveUp(i) !== editing`), so they cannot drift from the rule the set
enforces. Per layer:
- **transform** — X/Y (in a normalized square frame), zoom, rotation, and **tilt** (X/Y, leaning the layer out of the
  plane). Tilt is a *transform* and not an effect because it says where the layer sits — as an effect, one rotation
  would be orderable against a color matrix while the in-plane one was not. See the perspective note below for what it
  cost the live path.
- **shape** — an `IconShape`, **on any layer**. *(Differs from what was locked here: this said fg & bg only,
  with custom layers keeping their own alpha. The renderer masks whatever it is given, so the restriction would
  have been one the UI invented — and a shaped custom layer is obviously useful, since a color fill trimmed to a
  circle is how a colored disc goes behind a legacy icon.)* A shape is **backed by a vector drawable** (prepared
  as a resource) and referenced by a stable id; the clip mask is built from that drawable's silhouette, so adding
  a shape = drop in a drawable, no path math in code.
  - **A shape is cut against one of two frames, and `ContentAnchor` is which.** `BOX` (the model's default, and what
    the mask
    always did) fills the icon's square and stays put, so the transform slides the *content* under a fixed
    silhouette — the plate reading. `CONTENT` fits the shape to the layer's **artwork** and hands it the layer's own
    transform, so it lands on the ink and zooms, rotates and moves with it — the trim reading, which the box frame
    could not express at all: artwork sitting small and off-center is cropped by a shape it does not touch. An enum
    rather than the `shapeFollowsArtwork` boolean it was asked for, on `SideZoneEdge`'s grounds — a mask is always
    anchored somewhere, so both states are real and neither is "off".
  - **Picking a shape lands on `CONTENT`, and only the *model's* default is `BOX`.** The two are different questions
    and were briefly answered by one value: a spec carrying no shape has to mean the box (it is what every stored
    recipe was written against), but someone opening the Shape section wants the icon they can see trimmed to that
    outline, and against the box an app whose artwork sits small and off-center is cropped by a silhouette that never
    touches it — which reads as the control being broken rather than as a frame being wrong. So `pickShape` writes the
    anchor with the shape, and the switch beneath is how the plate reading is asked for. Clearing the shape leaves the
    anchor alone: there is nothing to anchor, so writing would only forget what to return to.
  - **The two are made to agree by going through the same matrix, not by matching arithmetic.** `ShapeMask` is a
    sixth shared derivation beside the five below, and a content-anchored silhouette is positioned *in the artwork's
    frame* and then carried by `LayerTransform` — the same one the content took — so it cannot drift off the ink
    under any transform. `ShapeMask.inkFit` (the decision) is split from `matrixOf` (the assembly) so the part that
    would be **silently** wrong is unit-testable on the JVM, the split `ContentMetrics` and `LegacyBackground`
    already make; `android.graphics.Matrix` stubs to no-ops in a JVM test, which is why `LayerTransformTest` leaves
    `toMatrix` alone too.
  - Three properties worth knowing. The fit is the ink's bounding **square** (`longestSide`), never its rectangle —
    stretching would turn a circle into an ellipse. It **rotates with the layer**, which is what "follows the
    transform" means and the whole difference from `BOX`. And **unmeasured content degrades to the box**: only the
    app's own artwork is measured (measurement and normalization share a scope, deliberately), so a pack drawable,
    an imported image or a flat fill has no ink bounds — it still follows the transform, so the control is never
    inert, it just cannot trim to something unmeasured. Measuring those would mean the injected `customImage` /
    `packImage` lambdas returning metrics rather than a bare `Drawable`; that is the seam if it is ever wanted.
    With `normalize` on the two anchors **coincide** at zoom 1, which falls out rather than being special-cased —
    normalizing *is* rescaling the ink to fill the box, so the two frames become one.
  - Additive: a defaulted field with `encodeDefaults = false`, so stored recipes read back rendering exactly as they
    did and the test pinning `IconLayerSet.Base`'s stored JSON still passes. Same deal the sealed effect list gives.
- **opacity + blend mode** — `IconLayerSpec` **fields**, not effects, because they describe how a layer *joins
  the stack* rather than what it is: every layer has both, always, with a meaningful default.
- **effects** — a sealed list, never columns, and an **ordered pipeline** rather than a bag (see the pipeline note
  below). `LayerEffect.Color` (hue → saturation → brightness → tint, composed into **one** matrix, so monochrome is
  `saturation = 0` plus a tint rather than a variant of its own), `LayerEffect.Bloom` and `LayerEffect.Gloss` (light
  spilling across the layer, and light struck across it with an edge), `LayerEffect.Vignette` (light gathering in
  from the edges), `LayerEffect.Pattern` (a tiled texture),
  `LayerEffect.Extrude` (the silhouette repeated behind itself), `LayerEffect.ChromaticSplit` (the colour channels
  displaced), `LayerEffect.Outline` (a hard band following the silhouette),
  `LayerEffect.Bevel` (the silhouette read as a raised surface and lit),
  `LayerEffect.Glow` and `LayerEffect.Shadow` (the silhouette blurred behind it),
  `LayerEffect.InnerShadow` and `LayerEffect.InnerGlow` (the silhouette's complement blurred *inside* it, laid on
  or screened), `LayerEffect.Ripple`
  `LayerEffect.Grain`, `LayerEffect.Pixelate` and `LayerEffect.ProgressiveBlur` (waves, noise, cells and a masked
  blur), `LayerEffect.Filter` (one of the built-in looks, by id) and
  `LayerEffect.Duotone` (the tonal range mapped onto two chosen colours). **Ten of the nineteen do not draw live** —
  everything that needs a blur or a per-pixel pass — which is what `drawsLive` and the bake-backed preview exist for.
  **All thirteen the plan set out are built, and so are all six of phase 2**; see the notes below for each, and
  [docs/ICON_EFFECTS_PLAN.md](docs/ICON_EFFECTS_PLAN.md) — whose **§8 is the phase-2 assessment**: six more effects
  checked against the built code, of which four are re-pointing what already exists, plus a per-effect mask that is
  deliberately *not* the "extract the falloff" the proposal asked for.
- **source** — including a **custom image** on any layer, which is how an app's own artwork is replaced outright.

**Rendering — hybrid:**
- **Display** (home, drawer, folders, pickers): the resolved layer set is **composited to one flat bitmap**,
  cached by `IconId(component, resolvedLayerSet, sizePx)` (value-equality key → correct invalidation for
  free), baked off the main thread. Surfaces draw one `Image`.
- **Editor**: layers render **live** (`IconLayerStack` — each a Compose node, transform via `graphicsLayer`,
  effects via a color filter and blend on a `saveLayer`) so slider drags respond instantly with no per-frame
  bake. **A commit does *not* invalidate the baked entry**, correcting what this said: `IconId` carries the layer
  set, so an edited icon simply *is* a different key — it misses, re-bakes, and the superseded bitmap ages out of
  the LRU. Calling `invalidate` would also bump `generation`, whose whole job is the one input the key cannot see
  (an app replacing its own artwork) and which recomposes every icon on screen.

**Two renderers is the standing hazard, and the shared derivations are what keep them honest — nine of them now.** An
icon that looks right while being edited and wrong on every surface is a bug the editor structurally cannot show you,
so the agreement is made of shared *things* rather than shared intentions: `ParsedIconLoader` (what the layers are),
`IconLayerResolver` (which draw, what each means, **and which drawable instance each render owns**), `LayerTransform`
(where they sit, including the perspective
matrix both paths take rather than each configuring its own camera), `LayerFilter` (the
color matrix — free to share, since Android's and Compose's `ColorMatrix` are each a row-major `FloatArray(20)`),
`IconFilters` (the table of built-in looks), `LayerGradient` (which way an angle runs, and the frame a bloom or a
gloss is laid out in), `ShapeMask` (where the silhouette sits — which stopped being "the
box" the moment `ContentAnchor` existed, and so became arithmetic rather than a constant), `LayerPattern` (a tile's
size, its matrix and how a stencil becomes colored marks), `LayerExtrude` (how many copies and how far apart) and
`LayerChromatic` (which channel leads).

**And a `Drawable` is mutable state, which is a hazard of a different shape: the two paths sharing one *object*
rather than disagreeing about arithmetic.** Drawing one is `setBounds(0, 0, sizePx, sizePx)` then `draw`, and the
bounds live on the instance — so while a `ParsedIcon` was parsed once per app and handed to every consumer, the studio
canvas, each tile in the layer rail and any surface icon baking at that moment were all writing their own size into
the same object, three of them on background threads. The symptom is a picture rather than an exception, which is why
it survived: a bake at 768 overwritten by a tile's 128 draws the artwork at 128, and a drawable draws from its bounds'
origin, so the icon lands at a sixth of its size in the **top-left corner** of an otherwise empty square — then a
whole-icon shape masks the box it was told about rather than the artwork, and a blur spreads out of it into the space
where the icon should have been. `IconLayerResolver.owned()` gives each resolution its own instances
(`newDrawable().mutate()`, passing through when there is no constant state), and shape and pattern drawables get
`mutate()` for the same reason one step down: `getDrawable` hands back a fresh instance over a **shared** constant
state, and a `VectorDrawable` caches a rendered bitmap in there. It goes in the resolver because that is the one seam
both paths already pass through — fixing one renderer would leave the other writing to the shared object, which is the
same race with one fewer participant. **It was latent for months and a real blur is what surfaced it**: the window is
however long the artwork takes to draw, so two `Bitmap.scale` calls rarely lost the race and three box passes over
half a million pixels lose it every time.

**What each new one is for is worth reading as a group, because the pattern repeats**: an effect earns a derivation
exactly when its two implementations would differ in something *invisible*. A tile at half the intended scale is
still a texture; an extrusion built from twelve copies instead of forty is still an extrusion; a red fringe on the
left is as plausible as one on the right. None of those fail, and none of them look wrong until the editor and the
home screen are seen together — which is the whole argument.

Only the drawing API differs, which is unavoidable and is exactly why those nine exist. The per-layer order is **content → shape mask →
effects, in list order → composite**, the same on both sides for different-looking reasons — statement order in
one, modifier nesting in the other. Two consequences: the live stack must composite **offscreen** (or a `MULTIPLY`
on the bottom layer blends against the studio canvas rather than against nothing), and the `IconLayers` dev-harness
playground draws one set both ways side by side, because comparing pixels needs instrumentation this project has no
setup for.
- **`effects` is a pipeline now, not a bag, and that is what makes room for more than two.** Both renderers used to
  read `spec.color` and `spec.gradient` *by name* and apply them in a sequence each hardcoded — gradient into the
  layer, color matrix onto the paint that joined it to the stack — so the list's order meant nothing. They now walk
  `IconLayerSpec.activeEffects` front to back. **The live path folds the reversed list**, because a modifier written
  earlier *wraps* the ones after it, so the first effect has to end up innermost; getting that backwards still draws
  an icon, just a differently-colored one, on the one axis neither renderer can check against the other.
  `LayerEffectPipelineTest` pins the order and the filtering.
  - **What stayed on the composite is what an effect cannot be ordered against**: opacity and blend, which describe
    how the finished layer *meets the layers beneath it*. Moving the color matrix off it changes nothing for a layer
    that only recolors — a color filter is per-pixel, so filtering into the buffer and then compositing is the same
    pixels as compositing through the filter.
  - **Two kinds of effect, and the difference is a buffer.** An *overlay* (gradient) paints onto what is there; a
    *filter* (color) transforms pixels already drawn, which a canvas cannot do in place, so it costs one bitmap in
    the bake and one `saveLayer` in the live path. Every effect added has to say which it is.
  - **`enabled` is the user's switch, `isIdentity` the effect's own "I would paint nothing", and both are filtered
    in `activeEffects`** so no renderer asks either question twice. `enabled` is persisted and defaults true, so with
    `encodeDefaults = false` an effect nobody switched off costs nothing on disk. **`drawsLive` is not persisted** —
    it says whether the *live* path can draw the effect at all, and a layer with any effect that cannot falls back to
    previewing from its bake (the bake has no such limit at any API).
  - **Only the renderers may ask `isIdentity`, and that took two corrections to get right.** `effectOrNull` (the
    editor's view) and `withEffect` (the writer) both used to drop an identity effect as well — the second so an
    untouched recipe stayed empty on disk, a real goal bought at the wrong moment. Applied on *every edit*, it made
    "drag a slider to its floor" mean **delete this effect**: a bloom's color, angle, radius, falloff and anchor went
    with it, the panel's switch greyed out mid-gesture, and dragging back up produced a *fresh* effect at defaults
    rather than the one being edited. Identity is a statement about what would be painted and the editor is not
    asking it. Storage stays small the honest way instead — nothing writes a record until the user asks for one.
    `withEffect` also **keeps an existing effect's position**, the list being the pipeline order: appending an edited
    one would move it past everything after it, so a tint that used to recolor a bloom would silently stop, on an
    edit about neither.
  - **`withEnabled` is the one way to flip a switch**, an exhaustive `when` in the model beside the interface. The
    studio had a forty-line `when` over `EffectSlice` whose **`else` arm meant Bloom**, so a new effect added without
    an arm would have toggled the wrong effect's switch. Over a sealed type the compiler refuses to let one be
    forgotten.
  - **One behavior change, accepted:** a stored recipe whose list reads `[Color, Bloom]` — what setting a tint
    before an overlay produced — now renders in that order, so its tint no longer recolors its bloom. Nothing has
    shipped, and the alternative is a canonical order no reorder control could override. Full plan for the thirteen
    effects this unblocks: [docs/ICON_EFFECTS_PLAN.md](docs/ICON_EFFECTS_PLAN.md).

**Effects apply to a *layer* or to the *whole icon*, and the second is a capability rather than a convenience.**
`IconLayerSet` carries its own `effects`, run over the finished composite — the same `LayerEffect` type, the same
pipeline, in both renderers. Per-layer simply *cannot express* six of the planned thirteen: a glow derives from the
finished silhouette, so on the foreground it glows inside the background plate where nobody can see it; grain, ripple
and pixelate applied per layer give independent distortion fields that visibly shear apart at the edge of the glyph;
and even a color matrix differs before and after compositing once opacity or a blend is in play. Additive (defaulted
empty, `encodeDefaults = false`), and `IconId` already keys on the whole set, so invalidation was free.
- **The layer rail is the scope control, so this cost one tile and no new vocabulary.** Selection there already meant
  *"the thing every tool acts on"* — the reason the `LAYERS` bar entry was deleted — so the composite is a tile at the
  **head** of the rail (above the top layer, since that is where it sits) and `StudioTarget` is a sum type over
  *composite or layer index*. The studio **opens on the composite**: the layers are permanently on screen, so picking
  one is an obvious tap, where discovering that effects can apply to everything is not. A *"this layer / whole icon"*
  switch inside the Effects panel was the alternative and is a second answer to a question the rail already answers —
  you would be editing the composite while the rail highlighted a layer.
- **The bar shrinks with the selection, and five of the six tools survive.** `StudioTool.appliesTo` — **Source** is
  the only one a composite cannot answer at all, being what the layers make; Effects applies to both, which is the
  point; **Shape** does too (`IconLayerSet.shape` is a real stack-level mask), and so does **Transform**, though what
  it offers there is the angles alone — see the perspective note below for that rule. Presets and More were never
  per-layer. So no "a bar of one is not a bar" special case was needed.
- **Opacity and Blend drop from the grid for the composite, by the rule slice 1 already settled.** They describe how
  something *joins a stack* and the composite joins nothing — which is exactly `EffectSlice.ownsEffect`, the same
  predicate that decides which entries carry a switch. So a new effect is offered on both targets for free.
- **The composite tile draws the real stack with nothing hidden**, where a layer tile hides every layer but one — a
  small copy of the canvas, which is correct: it is the thumbnail of the thing being edited. It gets **no quick menu**,
  since move/hide/delete are all about a place in a stack it is not in; four disabled rows would say less than none,
  and that is the one place the "disable, never omit" rule does not apply, because these can never become legal.
- **`activeEffects`, `effectOrNull` and `withEffect` moved onto `List<LayerEffect>`** and the six named per-effect
  members came off `IconLayerSpec`. Not tidying: "which of these draw?" has to have **one** answer for a layer and for
  the whole icon, and two holders with their own copies of the filter is a difference nobody would think to look for.
- **The trap, and it is silent:** anything rebuilding the stack must `copy(layers = …)`, never `IconLayerSet(layers)` —
  the constructor takes the whole icon's angles, mask and effects too, so a positional rebuild drops all of them the
  moment a layer moves. Pinned by a test.
- Rejected: a Photoshop-style **adjustment layer** at any height. The bake would manage it; the live path cannot sample
  its siblings without restructuring the whole stack into nesting, which is the two-renderer hazard at its worst. The
  composite is the one position that is cheap on both sides.

**And the composite has a *shape* now — `IconLayerSet.shape`, the second thing that turned out to be per-icon rather
than per-layer.** It is what makes "put every icon in a squircle" one control instead of the same shape set on each
layer in turn, and the two are not the same picture: a per-layer mask trims each layer *before* it joins the stack, so
a bloom or a blend reaching past that layer's own silhouette escapes it, where a stack mask catches everything. Same
terms as the effects above — additive (defaulted null, `encodeDefaults = false`), keyed by `IconId` for free, run in
**both** renderers as *mask, then effects, then the mask again*. Four things:
- **The third step is the one a layer does not take, and "catches everything" is only true because of it.** A layer's
  shape sits before that layer's effects deliberately — an outer halo must escape it. The stack's is the icon's
  *boundary*, and half the effect list grows alpha outward, so applied once it was escaped exactly as a layer's is:
  a rounded icon carrying a blur came out ringed by squared-off haze, the spread stopped by the only edge left, the
  **box**. Both passes are load-bearing and for different reasons — before, so that anything derived from a
  silhouette (an outline, a bevel, an inner shadow) reads the shaped icon rather than the square it was cut from;
  after, so nothing the pipeline grew reaches outside it. `IconLayerSet.effectTrimShape` is which shape and when
  there is nothing to trim, shared rather than decided twice: a renderer that forgot the second pass would draw a
  perfectly plausible icon, and the effects needing it most are the ones the live path cannot draw at all, so the
  studio structurally could not show the difference. Null with no effects, so an unedited icon pays for one mask and
  its antialiased edge is not multiplied by the silhouette twice.
- **No `ContentAnchor`, and that is the composite rather than a control left out.** An anchor chooses between the box
  and *the layer's artwork carried by its transform*; the composite has no measured ink to fit to, and its own lean is
  not a frame anything can be laid out in — the same fact that already sends its content-anchored effects to
  `InkFit.Box`. So `ShapeControls` takes a nullable
  anchor and the switch is simply absent — one section for both targets, because a duplicated shape grid is how two
  shape lists end up disagreeing about which shapes exist.
- **A layer tile drops it**, as it already drops the whole-icon effects, and here the reason is sharper than "it
  obscures which layer this is": a stack mask trims every tile identically, so a custom layer sitting near a corner is
  cropped to nothing and its tile goes blank — a layer nobody can see is one nobody can select, and the tile is the
  only way to reach it. The layer's *own* shape stays, being what that layer genuinely looks like.
- **`Modifier.shapeMask` stopped taking a `ResolvedLayer` and takes a shape plus a `matrixOf` lambda.** The composite
  has no resolved layer to hand it and its matrix is always null (the box), so one masking node serves both rather
  than a second one that could drift in how it applies the same silhouette. What each caller supplies is only
  *where*, which stays `ShapeMask`'s answer.

**`LayerEffect.Bloom` is what `Gradient` became, and it is one color fading out rather than two stops.** Light spilling
across the layer, painted source-atop. The rename is the rule the rest of the grid follows — every other entry names a
*look* where "gradient" named a shader. The color change is the load-bearing half: with two opaque stops, source-atop
*replaces* every pixel it covers, so a default white→black bloom at full strength obliterated the artwork it was meant
to light. Four things worth knowing:
- **The far end is the same color with its alpha dropped, never `Color.TRANSPARENT`.** Transparent black drags a white
  bloom through gray on the way out — a dirty edge that reads as a rendering fault. `LayerGradient.fadeOut`, shared
  because it is exactly the detail one renderer would get right and the other would not.
- **`Falloff` swaps one control for another rather than adding one.** A linear ramp spans its frame at every angle
  so it has no reach to set; a disc has no direction to run in. The panel shows Angle *or* Radius — the same rule that
  gates the tint-style control on a tint existing. It was `BloomFalloff` until progressive blur wanted the same pair.
- **It takes `ContentAnchor`, through the same `InkFit`**, so a bloom and a shape anchored to content on one layer land
  on the same square. That second consumer is what renamed the enum from `ShapeAnchor` — a frame is a frame whether a
  silhouette or a light is laid out in it, and the holder's own field (`shapeAnchor`, `anchor`) still says which. The
  entries carry no `@SerialName`, so nothing moved on disk.
- **`LayerGradient` places it without a `Matrix`**, unlike `ShapeMask`: a gradient is placed by handing endpoints or a
  center to a platform constructor, so the whole frame derivation is float arithmetic and therefore JVM-testable — which
  is where the anchored cases are pinned, since drift there is invisible in an editor drawing it the same wrong way.
- **The `@SerialName` stays `"gradient"` deliberately.** An unknown *key* is skipped, but an unknown polymorphic *type*
  throws, and `IconLayerSetCodec` drops the **whole recipe** on a throw — so renaming it would cost a user every
  customized icon rather than one effect's colors. That is why the settings layer's "the key name is the seam for a
  semantic break" rule does not transfer here. Stored blooms lose their two stops and keep everything else.

**`LayerEffect.Gloss` is a sheen, and the *edge* is what makes it its own effect rather than a bloom preset.** A bloom
is a ramp or a disc — light with no boundary; a gloss has a lit region, an unlit one, and an arc between them. It is
still the same radial fill, with the disc pushed **outside** the frame so only its rim lands on the artwork, which is
what "signed radius bending the sweep" turns out to mean: the whole of the control is how big that disc is.
- **One signed slider doing two things, on purpose.** `curve`'s magnitude is how tightly the edge bows (0 is very
  nearly straight); its **sign** is which way — the lit region bulging out, or the arc cutting into it. The light stays
  on the side the angle names either way, so the sign can never be mistaken for a 180° turn. That was the test it had
  to pass to stay one control rather than becoming a second angle.
- **Four stops, not two, and it is load-bearing.** With a two-stop ramp over the whole radius, a large disc leaves the
  frame in an almost flat part of it — so flattening the curve would fade the sheen away, a control undoing itself.
  `LayerGradient.sweep` places them so the boundary lands on the frame's center and the soft band is a constant share
  of the frame at every curve. `colorsOf` is a member of `Sweep` rather than each renderer's own two lines, because
  the stop *order* is the whole of what `litInside` means and reversing it draws a plausible sheen lit on the wrong
  side.
- **No position pad**, unlike Bloom: a sheen is placed by the direction it is struck from and the way its edge bows,
  and a third control moving the same band would be a second answer to what the angle already settles. `Frame.movedBy`
  is split from `frameOf` for exactly that — not every effect placed against a frame has a position of its own.

**Perspective cost the live path its `graphicsLayer`, and that is the whole story of the slice.** `tiltX`/`tiltY` are
`IconLayerSpec` fields resolved through `LayerTransform`, not a `LayerEffect` — leaning a layer out of the plane says
where it *sits*. What the plan called "no new render machinery" was wrong in one place: **Compose and the platform use
different camera units** (`graphicsLayer.cameraDistance` is a density-scaled dp; `android.graphics.Camera`'s z is in
72-pixel units), so the two paths could not be made to foreshorten identically by matching numbers. Matching two camera
models by eye is exactly the agreement `LayerTransform` exists to remove, so instead **the live path stopped reading the
transform's fields into a `graphicsLayer` and now takes the same `Matrix` the bake takes**. One derivation, no unit
question, and the shared thing got stronger rather than a seventh unverifiable one being added.
- **Content is now drawn *through* the matrix** rather than rasterized and then transformed, which is a small
  improvement as well as a change: a zoomed vector drawable re-rasterizes at its final scale instead of being
  stretched from a texture. Everything else about the node stack is unchanged — the mask and the composite still sit
  outside it.
- **The camera depth is a multiple of the box** (2.5×), for the reason offsets are fractions: a constant pixel depth
  would make one recipe read as mild baked at 96px and violent at 288px. Android's own View default is ~1280dp, which
  on a 48dp icon is nearly orthographic and so invisible.
- **`isTilted` keeps the untilted case free of camera work**, which is every layer of every unedited icon. Tested,
  because a tilt dropped from `isIdentity` would leave `toMatrix` skipping the camera and drawing flat with no error.
- **The composite has the angles now** (`IconLayerSet.rotation`/`tiltX`/`tiltY`), which closes the gap this line used
  to record. Five things:
  - **The composite gets the values that say *which way it faces*, and not the two that say where it is and how
    big.** Rotation and tilt are the same kind of statement, so splitting them across scopes would make one rotation
    two kinds of thing — the argument `IconLayerSpec.tiltX` already makes one scope down. **Zoom** is the icon-size
    setting (`IconSizing`), per surface in `data:settings`, and a second scale here is applied on top of it, so one
    recipe would come out a different size on every grid. **Offset** can only ever slide the icon *out* of the one
    square there is, and under a stack shape it is worse — the mask stays put, so what appears is a crescent of
    missing icon.
  - **Per-layer angles substitute for neither, and they fail differently.** A tilt cannot be composed at all: layers
    at different depths each get their own vanishing point, so a foreground slides off its background as the angle
    grows. A rotation *can* — it is affine — right up until a layer has an offset, since a layer rotates about the box
    center and *then* translates, so an arranged layer's position does not travel with the angle. It stops being
    equivalent exactly when the recipe is interesting. (An earlier note here claimed rotation was excluded because it
    would be cropped; that reason does not survive, since a *layer* has been croppable that way all along.)
  - **So `StudioTool.appliesTo` settled a rule rather than gaining a value**: a tool applies when it has *something*
    to offer, not only when it offers everything. Hiding Transform because two of its four controls are meaningless
    would leave the two that are not with nowhere to be. `CompositeTransformControls` is a panel of its own rather
    than the layer's with controls disabled, per the sections' own "absent, not disabled" rule; the three angles are
    one shared `OrientationSliders` written once and shown by both scopes, and one target-dispatched command
    (`setOrientation`) writes all three together, so neither holder is ever handed a partial update to merge.
  - **The layers are drawn *through* them, in both paths** — a matrix on the bake's canvas before the loop, a native
    `concat` around `drawContent()` live — rather than the composite being flattened and re-sampled. Cheaper, sharper,
    and it cannot separate the layers from each other since they all take the one matrix. `LayerTransform.of` gained
    an `IconLayerSet` overload for it, with **no `sizePx`**: the only size-dependent part of a transform is the
    offset, which the composite has not got. The live path gates on **`isIdentity`, not `isTilted`** — a tilt test
    would skip the matrix for an icon that is only turned, drawing it upright with nothing to say it went wrong.
  - **Mask and effects sit outside the angles**, the per-layer order one scope out — so a turned or leaning icon
    slides under a silhouette that stays put, which is what a box-anchored mask means and exactly the look a fixed
    icon shape is for. A **layer tile drops them** as it drops the stack mask and the whole-icon effects: they apply
    to every tile at once, costing the artwork the room it needs at 44dp while saying nothing about which layer it is.
  - The bound worth knowing: a steep angle pushes the picture past the box and the output crops it — the same bound a
    turned or leaned *layer* already has, visible while it is being set rather than a surprise later, and absent
    entirely under a stack shape that keeps the icon clear of the corners.
- **A content-anchored bloom or gloss does not follow a tilt.** `LayerGradient.Frame` carries a 2D rotation, so it
  tracks zoom, offset and in-plane rotation but has no perspective term. The light stays flat on a leaning layer.

**`LayerEffect.Pattern` tiles a texture over the layer, and its assets are a library of their own.** `IconPattern` is
`IconShape`'s exact shape and deliberately **not** its list — sharing one catalog was considered and rejected, because
a shape is a silhouette whose *alpha is a mask* stretched once to the box, where a pattern is artwork whose *marks are
drawn*, tiled at a scale and an angle. Half of each list would be nonsense in the other role. What they share is the
pipeline: drop a drawable in, add an id, the id is the on-disk contract, an unknown id draws nothing.
- **The tile is a *stencil*, which is the fact everything else falls out of.** Marks are authored white on
  transparent and `argb` is what they come out in, so one asset serves every color — and `invert` is a `DST_OUT`
  punch rather than a second library. A tile carrying its own colors would need both.
- **`LayerPattern` is the seventh shared derivation, and a tiled shader earns one.** There are *three* things the two
  paths must agree on and each is invisible alone: the tile's pixel size, the matrix that turns it, and how the
  stencil becomes colored marks. It hands back a **bitmap** rather than a shader, because that is the last point they
  can share — one wraps it in `BitmapShader`, the other in Compose's `ImageShader`.
- **Every asset is authored to repeat, and nothing checks that.** A mark crossing an edge is drawn again on the
  opposite one, or drawn whole and centered *on* the edge so the drawable clips it and the neighbor completes it
  (`pattern_dots` does this at all four corners; `pattern_grid` draws only two of its four edges, since drawing all
  four would double every interior line). A mistake shows as a seam every tile, which reads as a rendering fault
  rather than as a bad asset.
- **The live path remembers the drawable and rebuilds the tile per frame**, which is the one place it does more work
  than it looks: the tile's size depends on the node's, which composition does not know. The alternative is plumbing
  the measured size back out of layout for a bitmap a few pixels square.
- **Scale is a fraction of the box**, for the reason offsets are — a quarter puts four tiles across the icon at every
  bake size — with a pixel floor, since a shader repeating a one-pixel bitmap is a flat wash that costs a texture.
- **No `ContentAnchor` and no randomize.** A pattern is a texture laid *over* the icon and its own angle orients it, so
  the anchor is additive if wanted; and what the reference's randomize button randomizes cannot be read off a
  capture, where a button writing a random number into a slider the user can drag is a novelty rather than a control.

**`LayerEffect.Extrude` is the layer's silhouette repeated *behind* itself, and the first effect whose live cost
scales with a slider.** An extrusion is the union of a silhouette with a line segment and nothing draws that
directly, so it is N copies — and N is the whole of the cost. The bake blits a bitmap it already holds; the live path
re-runs the layer's own **content** per copy, per frame, at preview size.
- **`LayerExtrude` caps the count at 48 and grows the per-step offset to compensate**, so the slab reaches the depth
  asked for whatever the cap does to its smoothness. That second half is the one a fixed step size would get wrong,
  and the symptom — a depth slider that quietly stops partway — is not something anyone would attribute to a step
  limit. Pinned by a test at a bake size where the cap actually binds.
- **It is the first candidate for `drawsLive = false`.** Left true only because the bake-backed preview is not built,
  which is exactly the situation that flag was added for.
- **`LayerFilter.solidMatrixOf` came out of `ColorMatrices.solid` gaining a second consumer** — an extrusion is the
  layer's silhouette in one color, which is the operation a `TintMode.SOLID` tint already performs. Both now pull the
  channels out of an int in one place, and that place is the fifth column, which is silent when wrong.
- **Each copy takes its own `saveLayer` in the live path**, not one over the whole slab: the color matrix has to see
  each *copy*, where filtering the finished slab would flatten it correctly and then composite it as a single
  translucent sheet, so the overlaps would show through one another.
- The bake's effect loop stopped being "a color matrix, or an overlay": Extrude produces a new buffer without being a
  matrix, so the `when` now says plainly which effects replace the buffer and which paint into it.

**`LayerEffect.ChromaticSplit` is the layer's three color channels displaced and added back together**, and it needed
no new arithmetic — a channel isolation is `ColorMatrices.mix` with a single one in each row, which is what that
builder exists for and what `scale` structurally cannot express. What `LayerChromatic` contributes is the
**convention**, and that is precisely what is worth sharing: red leads, blue trails, green stays put. Either direction
looks like a lens, so nothing would fail if the two renderers disagreed — it would simply be wrong in one place.
- **Green holds still on purpose.** The eye reads luminance mostly from green, so displacing it would shift the whole
  icon rather than fringe it.
- **Additive, not layered.** `PorterDuff.ADD` in the bake, `BlendMode.Plus` live, each inside one isolating layer so
  the sum starts from nothing rather than from whatever the pipeline had drawn. Ordinary source-over would stack three
  colored silhouettes and the last would win.
- **The only effect with no strength slider**, and that is the honest shape: the effect *is* a displacement, so an
  offset of nothing already means "not split" — `isIdentity` falls out of it, and a second knob would be a second way
  to reach the same state.
- **It is the only effect that draws the content *instead of* over it**, so the layer's own pixels never appear.
- **`PositionPad` gained a range parameter** for it: a fringe is a couple of percent of the icon, so at the pad's own
  travel the whole useful span would sit under the thumb. Everything else keeps `PositionRange`.
  - **The Effects section is a paged grid of entries you open, and one entry maps to one `LayerEffect`.** It briefly
    split `LayerEffect.Color` into *Recolor* and *Tint*; the switch overturned that, because two entries sharing one
    record can express "tint off, recolor on" — a state the model cannot hold. Splitting `Color` in the *model* is
    worse still: its four numbers compose into one matrix in a fixed sequence, so as separate entries their list
    order would silently change the result. Every slider goes through `SliderControl` — name, value readout, and a
    **reset** disabled at the default, so the row doubles as "have I changed this?".
  - **An entry is an *adjustment* or an *addition* (`EffectKind`), and nearly everything else falls out of that.** An
    adjustment transforms pixels already there — `Opacity`, `Blend`, `Color`, `Filter`; an addition puts new ones in
    — the other eleven. Deliberately **not** the same question as `ownsEffect` ("is there a stored record?"), which
    is what decides whether the *composite* offers an entry at all; `Color` and `Filter` are the pair that separates
    them, owning records while being adjustments. Three consequences:
    - **Only additions carry a switch**, the line being *can this be off in a way its own controls cannot express?*
      An addition's off is its absence and its sliders only say how much. An adjustment's off **is** a value its
      controls reach and name — Color rests at hue 0 / saturation 1 / brightness 1 / no tint, each with a reset
      already disabled at exactly that value, so the switch was a fifth control repeating four; and Filter's list
      *contains* "None", so a switch is a second way to pick the same entry, exactly as "no shape" is the first tile
      in the shape grid rather than a toggle beside it. What that costs is non-destructive A/B on an adjustment; if
      it comes back it belongs to the whole icon as a press-and-hold, not to one entry.
    - **Opening an addition seeds it at its own defaults, and every default is visible.** Nothing applied them
      before — the panel showed sliders against an icon they had not been written to — so tapping an effect changed
      nothing and taught nothing. `Pixelate.cellSize` and `ProgressiveBlur.radius` both rested at their own identity
      and had to be given real values; the other nine already had them. **Backing out of an entry you never touched
      removes it again**, so browsing all eleven costs nothing: the seed is uncommitted, the first real edit is what
      records, and that one history entry covers the seed and the edit together. It lives in a `DisposableEffect`
      because there are four ways out of an entry — its own back button, the system gesture, the target changing
      under it, and the whole panel being closed — and only disposal catches all four.
    - **A tile marks itself from the *switch* for an addition and from `isIdentity` for an adjustment.** It read
      `activeEffects` for both, which folds the two together — so a tile unmarked itself as a slider passed through
      its floor while the switch beside it still said on, two controls contradicting each other on the one gesture
      that reaches the floor by accident. An adjustment has no switch to contradict, so identity is the only
      meaningful answer there.
    - `Filter` sits **beside `Color`** on the first page rather than last, where it had landed by being built last:
      the two are the same question asked twice and are what a user moves between while grading a layer.
  - **`LayerEffect.Filter` is the first effect the pipeline was built for, and it is a fixed vocabulary rather than
    curated content** — the opposite call from icon *presets*, and the difference is what each thing is. A preset is
    a whole recipe whose quality depends on the artwork it lands on, so curating one is design work with no end; a
    filter is one 4×5 matrix that does the same thing to every icon, and choosing twenty numbers and a name is the
    same act as adding a value to `LayerBlend`. So it takes **`IconShape`'s exact shape**: `IconFilter(id)` in
    `core:model.icon`, the id → matrix table as `IconFilters` in `core:icon` beside the renderer that applies it, and
    an **unknown id resolving to no matrix** so a recipe from a later build degrades rather than failing. Names
    describe the look — never a person or a film, since a filter's name is shipped, stored and user-visible.
  - **`ColorMatrices` is the arithmetic, `LayerFilter` the policy.** The builders came out of `LayerFilter` when the
    table arrived, because authoring dozens of looks as raw `floatArrayOf` is unreviewable — a look composes as
    `saturation(0.9).then(contrast(1.12))…`, which says what it *is*. `LayerFilter` kept the one thing that is about
    the four sliders: the order they compose in. Three builders are new: **`contrast` pivots about mid-grey** (without
    the offset it is a brightness control that also steepens, the usual way this is written wrong), **`mix`**
    weights each output channel across all three inputs, which is what a true sepia needs and what `scale`
    structurally cannot express, and **`duotone`** maps the tonal range onto a two-colour ramp. The fifth column is a
    translation on 0..255, which is silent when wrong.
  - **A filter swatch shows the look, not the icon** — one fixed reference gradient under each filter's matrix.
    Previewing on the real icon is a bake per tile, and an icon that happens to be black says nothing about a warm
    grade; every tile being the same strip is what makes them comparable.
  - **The library grew to 46 looks in seven categories, drawn from captures of the same reference studio the
    effects came from**, and three things about the expansion are worth keeping:
    - **`duotone` is the one piece of new arithmetic, and it is a whole family.** `out = dark + luma × (light −
      dark)`, which discards hue entirely and keeps only how light each pixel was — *not* a tint, which attenuates
      the colours already there and so leaves a red icon and a blue one different. Discarding the hue is exactly
      what makes a screenful of icons drawn by different hands read as one set. The span is divided by 255 while
      the weights are not, and getting that backwards produces a blown-out picture rather than an obviously broken
      one; **the test caught it in the authored version of this**, which is why it is a shared builder with a test
      rather than a matrix written out per entry.
    - **A matrix cannot quantize, and that is the bound on the whole file.** The reference's retro-hardware looks
      snap colours to a fixed palette, which is not a linear map at any size — so those entries here are the *ramp
      between the palette's two ends* (a duotone), not a stepped approximation pretending to be the same thing. A
      real one would be a `LayerEffect` with a per-pixel pass, like Pixelate.
    - **The names are ours.** The reference has a "Tarantino", an "iOS" and a "MIUI"; a filter's name is shipped,
      stored and user-visible, so borrowing one makes the launcher's vocabulary depend on somebody else's
      trademark for no gain in clarity. Same rule, now with three worked examples.

**`LayerEffect.Duotone` is the fourteenth, the first of the phase-2 six, and the one the filter library had already
built.** The layer's tonal range mapped onto a ramp between two *chosen* colours — `ColorMatrices.duotone` exactly,
which eight of the 46 authored looks already run on. It is **not a tint**, and that distinction is the whole reason it
exists: a tint attenuates the colours already there, so a red icon and a blue one stay different, where this discards
the hue entirely and keeps only how light each pixel was — which is what makes a screenful of icons drawn by different
hands read as one set. Five things:
- **Named for the look, not the mechanism** — the plan called it a *gradient map*, and a gradient map has arbitrary
  stops where this deliberately has two colours and no midpoint. Same rename Bloom took from `Gradient`.
- **No midpoint or bias slider, and that is a bound rather than a control left out.** Shifting the balance between the
  ends is a non-linear remap of luminance *before* the interpolation, which a 4×5 matrix structurally cannot hold — so
  a bias would demote the effect to a per-pixel pass and cost it both its live path and the composability that lets it
  stack with everything. If it is ever wanted it is a second effect.
- **`strength` is an interpolation of the *matrix*, not of two drawn copies** (`ColorMatrices.towards`). Applying a
  matrix is linear in the matrix, so `(1−t)·A + t·B` applied to a pixel *is* the cross-fade of the two results — which
  is what let a partial grade cost no second buffer. The fifth column needs no special case there, unlike in `then`,
  being a term of the same linear expression rather than something multiplied through.
- **`LayerFilter.duotoneMatrixOf` is the extraction the second consumer earned**, the exact move `solidMatrixOf` made:
  `IconFilters` had been unpacking two ARGB ints into six channels privately, and a *user* picking the same two
  colours was about to do it again — on the **fifth column**, at 0..255, where a 0..1 value is visually black rather
  than obviously broken. The table keeps a two-colour alias because a table of looks reads better in colours than in
  channels, which is what its old note was really about.
- **An addition rather than an adjustment**, which looks arguable and is not: `carriesSwitch`'s test is whether the
  entry's *resting* state is its off state, and this arrives at the full ramp because that is what makes it legible.
  So zero strength is not where it sits untouched, and its "off" is its absence — which is a switch. And the
  library's own DUOTONE category is not a duplication for the reason `Color` and `Filter` are not: one is a fixed
  vocabulary somebody authored, the other is *this* icon's two colours.

**`LayerEffect.Vignette` is a bloom's radial ramp run the other way, and the second phase-2 effect.** Colour
gathering in from the edges with the middle left clear, source-atop like the other two overlays — so it follows a
rounded plate's own corners instead of squaring the icon off with a rectangle. Its own effect rather than a flag on
Bloom for Gloss's reason: they are different *looks*, a user goes looking for this one by name, and at most one
effect of a type is meaningful, so folding them would mean an icon could carry a light or a vignette and never both.
Four things:
- **`LayerGradient.rampStops` is the extraction it earned**, moved off `LayerProgressiveBlur` on its second consumer.
  The disc always spans the frame to its corners (`radial` at 1) and what the controls move is the *stops*, which is
  precisely what the focus ramp had been doing — and it is not a blur's question, so leaving it there would have had
  an effect that is not a blur importing a file named for one. The crash guard came with it: a clear area of 1 asks
  for a band from 1.001 to 1, and `coerceIn` throws outright on an inverted range.
- **Reach is inverted in the *model*, not in each renderer** — `Vignette.clearArea`, which is `Bloom.placementX`'s
  arrangement and its reason. A projection of the model's own field is the model's arithmetic, and two paths each
  doing it are two chances to do it once; backwards it draws a perfectly plausible picture lit in the middle, on the
  one axis neither renderer can check against the other.
- **No falloff and no position, and that is the effect's shape rather than controls left out.** A ramp with an angle
  arrives from one side, which is a bloom; an off-centre disc is a bloom placed. Either would make this the entry
  beside it with a switch on.
- **It anchors to the artwork by default**, like a bloom and unlike a shape mask: box-anchored on a small glyph the
  ramp gathers at corners the glyph never reaches and source-atop clips it to nothing, so the control would open on
  no visible change. `ContentAnchor.BOX` is how the icon's own frame is asked for, and on a background plate filling
  the box the two coincide.

**Blur is one kernel now — `core:graphics`'s `BitmapBlur` — and it was two, one of which was not a blur.** Three
separable box passes with a sliding window, which is a close gaussian approximation and, being O(pixels) *independent
of the radius*, is cheap enough that nothing has to be reduced first. Its own module because `core:icon` and
`data:wallpaper` both blur and neither may depend on the other. Four things:
- **The icon renderer was faking it.** `progressivelyBlurred` scaled the layer down and back up with bilinear
  filtering, and that is not the same operation: bilinear downscaling reads a 2×2 neighbourhood, so a 30× reduction
  throws away almost every pixel it is supposed to average. The Focus effect came out visibly *terraced* —
  stair-stepped edges and upscale blocks — and its KDoc asserted the opposite. Found by looking at a device.
- **The wallpaper backdrop's reduction is now proportional to the blur, where it was a constant eighth.** That
  constant is why a frosted surface looked like a low-resolution copy of the wallpaper *at every strength, including
  zero* — where no blur is applied at all, so nothing about the blur could have been at fault.
  `BitmapBlur.downscaleFor` keeps enough radius on the reduced bitmap for the passes to be doing the smoothing rather
  than the upscale, and the reduction is taken **entirely in the decode** (`inSampleSize` is free where a later `scale`
  is not) — so it is the largest power of two that number allows, and the radius is measured against the reduction
  *actually* taken. Splitting it between a power-of-two decode and a residual `scale` is what the first cut did, and
  integer division threw the residue away every time, so the radius was computed for a bitmap smaller than the one it
  ran on and every strength between two powers of two quietly under-blurred. Invisible while one caller asked for one
  strength; not invisible once a slider reached it.
- **The blur is premultiplied, which the wallpaper's version never needed and an icon cannot do without.**
  `getPixels` hands back un-premultiplied ARGB and a transparent pixel is almost always transparent *black*, so
  averaging the channels directly drags black into everything near an edge — `LayerPixelate.averageArgb`'s trap, one
  operation over. A wallpaper is opaque, so this costs it nothing and it is not two code paths.
- **It is not an argument for `minSdk` 31.** `RenderEffect` was considered for both and fixes neither: the icon path
  is `IconRenderer`, a *software* bitmap pipeline running off the main thread, where a `RenderNode` blur means a
  `HardwareRenderer` and a readback; and the backdrop's worst artifact appears at a strength where there is no blur
  to improve. Both were resolution and kernel choices, and both are fixed at `minSdk` 26.

**A layer's blend mode is arithmetic now, not a `PorterDuffXfermode` — `LayerComposite`.** The bake handed
`LayerBlend` straight to a `PorterDuffXfermode`, and one of those five is not the blend of the same name:
`PorterDuff.Mode.MULTIPLY` is `[Sa × Da, Sc × Dc]`, so the result **alpha is the product too**. A foreground set to
multiply therefore multiplied the alpha of everything beneath it by zero wherever the foreground was transparent, and
on a device **every app's background plate vanished from the home screen** — only the apps whose artwork fills its box
kept one. Five things:
- **The live path was correct throughout**, Compose's `BlendMode` being a true separable blend. So the studio showed
  the icon intact and only the *baked* icon was wrong: the two-renderer hazard in the worst form this codebase has hit,
  and the one kind of divergence the editor structurally cannot show you. Found by driving the device, not by reading.
- **`MULTIPLY` was the only one broken**, which is worth knowing before assuming the rest: `SCREEN`, `OVERLAY`,
  `DARKEN` and `LIGHTEN` all document the union alpha `Sa + Da − Sa·Da` and the proper separable colour formula. The
  fix routes all five through one implementation anyway, because a mode-by-mode judgement about which platform
  constant is trustworthy is exactly the thing that goes stale.
- **No API fork, which is the point.** `Paint.setBlendMode` is API 29 against a `minSdk` of 26, so the obvious repair
  would have been two implementations of the thing that had just proved it goes wrong when there are two. Instead the
  bake does the blend itself at every API, from the **W3C compositing formulas** that `android.graphics.BlendMode` and
  Compose's `BlendMode` both implement — so agreeing with the spec is what makes the two paths agree with each other.
- **Only a blended layer pays for it.** `LayerBlend.NORMAL` — every layer of every unedited icon — still goes onto the
  canvas in one `drawBitmap`. A blended one is placed through the whole-icon matrix into a scratch first, because a
  per-pixel blend has no canvas to inherit that matrix from.
- **The two failure directions are the two tests that matter**: a transparent *source* must leave the backdrop exactly
  as it was (the erasure), and a transparent *destination* must leave the source standing rather than the bottom layer
  of a stack vanishing for want of something to blend against.

**`LayerEffect.Bevel` is the sixth phase-2 effect and the only one that was not made of parts already here.** The
layer's own alpha, blurred, read as a **height map**; the slopes near its edges catch or miss a light; what they catch
is painted as a highlight and a shadow. Every parameter is about a *light* rather than a shape. Five things:
- **It does not fit `resample`, and the plan predicted that correctly.** That helper asks which single pixel an output
  reads and answers with a bilinear sample; a Sobel reads a *neighbourhood* and answers with a colour. What the two do
  share is the row split, so **`overRows` came out of `resample` on this second consumer** and the per-band scratch
  became per-row — a `FloatArray(2)` per row is nothing beside the pixels.
- **There is no depth control, which is the one departure from what was asked for.** A depth slider scales the slope
  where the two strengths scale the bands, and the picture cannot tell those apart — halving one and doubling the
  other lands in the same place. What depth is genuinely for is guaranteed rather than offered: `LayerBevel.slopeScale`
  cancels the blur radius out of the gradient, so Size moves the bevel's *reach* and nothing else. Without it, size
  would have been an intensity control too and backwards, since a blurred edge's gradient falls as it widens.
- **The lighting is measured against the flat case**, and that subtraction is what confines the effect to the edges. A
  plain Lambert term lights every surface facing the viewer, so the icon's flat interior would come out uniformly
  brightened and the whole thing would read as a brightness control with an odd rim.
- **The two bands are blended per pixel, and that is a fix rather than an economy.** A slope facing the light is
  *screened* and one facing away is *multiplied*; the obvious way to get that — two band bitmaps drawn with
  `PorterDuff.Mode.SCREEN` and `MULTIPLY` — **erased the icon on device**. Those modes are not the blends of the same
  name: multiply is `[Sa × Da, Sc × Dc]`, so the result *alpha* is the product too, and a band transparent across most
  of the artwork multiplies its alpha to nothing. What was left was the shaded slopes alone on an empty canvas.
  `LayerBevel.lit` does both blends per channel, keeps the artwork's alpha by construction, and needs no band buffers
  and no trim — where the canvas fix would have been `BlendMode`, API 29 against a `minSdk` of 26.
  - **The same trap was live in `LayerBlend` and is now fixed** — see `LayerComposite`.
- **The altitude control was documented backwards until a test caught it.** Overhead light does not flatten the relief
  away; it removes the *sidedness*. A tilted surface still catches less of an overhead light than a flat one, so every
  slope shades equally and what is left is the uniform rim of a pillow emboss — a real look, so the slider runs the
  whole way up. Pinned, because the obvious reading is the wrong one and nothing about the picture would say so.

**`LayerEffect.Outline` is the fifth phase-2 effect and cost no drawing code at all.** A hard band following the
layer's finished silhouette — what separates an icon from a busy wallpaper when nothing softer will. Every piece was
already there once inner glow had extracted them: an **outside** stroke is `haloed` with a null radius, an **inside**
one is `insetHaloed` with a null radius, and a **centred** one is both. The dilation each of those performs *is* the
stroke once nothing softens it. Four things:
- **Inward first for the centred case, and the order is load-bearing.** `insetHaloed` trims its band to the artwork,
  so it changes no alpha — which leaves the silhouette `haloed` then grows outward still the *artwork's* own edge.
  The other way round the outward band fattens the silhouette first and the inward one is measured from the stroke's
  edge, putting the whole thing a width too far out.
- **`perSideWidth` halves the total for a centred stroke, in the model** on `Vignette.clearArea`'s grounds. `width`
  is the thickness a user sees whichever position is chosen, so switching moves the band without also changing its
  weight; done in the renderer, the failure would read as the position control secretly being a width control.
- **`drawsLive` is false for a new reason — there is no blur here.** An outside stroke *could* draw live, being what
  `Extrude` already accepts the cost of. The inside one cannot: its complement must be built in a buffer larger than
  the layer (see `LayerShadow.innerMarginPx`) and a Compose node cannot reliably draw beyond its own bounds, so a
  full-bleed plate would be stroked on the sides its artwork happened not to reach and left bare on the rest. That is
  the two-renderer hazard at its worst — not a *missing* effect, which is noticed, but the same effect drawn
  correctly in one place and subtly incompletely in the other. One answer for all three positions, since a control
  whose live-ness changed as it was switched would flicker the preview between mechanisms.
- **No softness control**, which is the one a user might look for and the one that would duplicate: softened outside
  is `Glow`, softened inside is `InnerGlow`, and both offer a choke this could not. Hard is what makes a stroke a
  stroke.

**`LayerEffect.Glow` and `LayerEffect.Shadow` are the same halo twice, and the first two effects that do not draw
live.** Both are a blurred copy of the layer's *finished* silhouette drawn behind it — after the transform and the
mask, since an outer halo must escape the shape. The bake holds that as a bitmap and can blur it at any API; the live
path only has it as nodes, and Compose's only blur is `RenderEffect`, API 31+ against a `minSdk` of 26. **Gating was
considered and rejected** — it would deny both to every device below Android 12 to solve a problem only the *editor*
has — so they answer `drawsLive` false and `IconPreview` routes an icon carrying either to the bake. They are what
retires the deferral this file carried from B3, and the first real exercise of slice 8.
- **Two effects rather than one, despite one mechanism.** At most one effect of a type is meaningful, so a single
  record would mean a layer could carry a glow *or* a shadow — and a glowing icon casting one is ordinary. The
  parameters differ honestly too: a glow is centered so it has a **spread** and no offset; a shadow is thrown so it
  has an **offset** and no spread. Same shape of argument as Bloom and Gloss.
- **Spread is a dilation, and a dilation is the silhouette swept around a circle** — `LayerExtrude`'s "nothing draws
  this directly" problem one dimension over. Cheap in a way that one could not be, precisely *because* this never
  draws live: the copies are blits of a bitmap the bake already holds rather than re-runs of a layer per frame. It
  earns its place because a blur alone leaves the halo at half strength right at the edge, so a glow built from
  radius alone reads as a smudge — spread is what gives the fade a solid ring to start from.
- **`LayerShadow.radiusPxOrNull` is nullable and that is load-bearing.** `BlurMaskFilter` rejects a non-positive
  radius, so a slider at its floor would throw rather than draw. Null means "skip the blur", which is a hard-edged
  shadow — a real look, and the one a long shadow is built from.
- **`LayerShadow` is the first shared derivation extracted *not* for two renderers to agree**, since only one path
  draws these. It is separated for the other half of the reason the `render` package is shaped this way: pulled out
  of `IconRenderer` the arithmetic is unit-testable, where every line of that class needs an emulator.
- **The halo is clipped to the icon's box**, which is inherent rather than an oversight — the output is one square
  and always was. A radius large enough to reach the edge is one the user can see reaching it.
- `minSdk` reaching 31 would retire the fork for these two; 33 would retire it for the three still to come.

**`LayerEffect.InnerShadow` is `Shadow` turned outside in, and the third phase-2 effect.** Everything *outside* the
layer, blurred, thrown, and laid back **inside** its own silhouette — so the artwork reads as pressed into the surface
rather than sitting on it. Its own effect on `Glow`/`Shadow`'s precedent: at most one effect of a type is meaningful,
and an icon that both casts a shadow and is recessed into its own plate is ordinary. Four things:
- **The alpha inversion needed no matrix, which overturns the plan's own prediction.** `punchPaint` is `DST_OUT` over
  a filled buffer, leaving `dstAlpha × (1 − srcAlpha)` — the complement, in two canvas calls. A colour matrix would
  have had to reason about premultiplication to invert an alpha channel, where this simply does not. Outline's
  erosion is the same op run twice, so that effect owes no new primitive either.
- **The complement is built in a *padded* buffer, and this is the part that is silently wrong without it.** An inner
  shadow is cast by what surrounds the artwork; a layer reaching the icon's box has nothing surrounding it within the
  bitmap, so the shadow would fade in from nothing along exactly those edges — and a full-bleed background plate,
  which reaches all four, is the commonest thing anyone recesses. `LayerShadow.innerMarginPx` sizes it from the three
  ways the complement's edge travels inward: the blur spreads it, the choke grows it, the throw slides it.
- **Source-atop is what puts it inside**, with the layer already drawn as the destination — so its alpha decides
  where the shadow lands and no second masking pass exists to disagree with the first.
- **The band appears opposite the throw, and that is geometry rather than a sign error.** Displacing the outside down
  and right slides it over the artwork's top-left interior, which is where a light from the top-left leaves a recess
  dark — so this and `Shadow` agree about where the light is while their bands sit on opposite edges, which is what a
  real light does to a bump and a dent. It is labelled **"Inset"** in the studio, on `ProgressiveBlur`/"Focus"'s
  precedent that four columns is one short word.

**`LayerEffect.InnerGlow` is that one's twin, and where the inner halo became one function.** Light gathering along
the inside of the edge — the complement blurred and trimmed as a recess is, then **screened** onto the artwork rather
than laid over it, so it brightens the colours already there instead of covering them with a band. Two effects rather
than one on `Glow`/`Shadow`'s precedent, and the parameters differ the same way: a recess is thrown so it has an
offset, a rim is centred on the edge it lights so it has none. Three things:
- **`IconRenderer.insetHaloed` is the extraction the second consumer earned**, and the two differ in exactly two
  arguments (the offset, and the blend). Everything between the complement and the trim is identical, which is
  precisely the near-copy that drifts when written twice.
- **The trim moved into the halo's own buffer, and that is what made one function possible.** The first cut leaned on
  source-atop to clip *and* composite at once — correct for a shadow, impossible for anything that adds light, since
  the mode is then spent. Destination-in first, any mode after.
- **No "edge or centre" toggle**, dropped from the proposal and confirmed by building it: a glow radiating from the
  middle of the artwork is `Bloom(falloff = RADIAL, anchor = CONTENT)`, already built and additionally offering a
  position and a falloff this could not. Labelled **"Rim"**, on "Inset"'s precedent — light along an inside edge is a
  rim, and that names the look rather than the mechanism.

**`LayerEffect.Ripple` is the first *per-pixel* effect, and the first that leaves the canvas entirely.** Concentric
waves push each output pixel to read from somewhere else along its own radius — arithmetic over an `IntArray`, which
the bake does at any API and Compose needs AGSL and API 33 for. Four things:
- **The plan grouped it with Pixelate and Grain as "one loop with three answers", and that is two-thirds right.**
  Ripple and Grain are resamplings; **Pixelate is not** — as the reference draws it the cells have gaps and rounded
  corners, so it *redraws* the layer as a field of shapes with one color sampled per cell. A coordinate-quantising
  pixelate would give solid blocks and could express neither control. So Ripple went first, against the plan's order,
  to put the displacement pass under its natural first consumer rather than under the odd one out.
- **The pass is not extracted yet**, which is this codebase's own extract-on-the-second-consumer rule applied rather
  than the plan's anticipation: the loop is six lines and what Ripple and Grain share is not yet known to be the same
  six. `LayerRipple` holds only the part that can be silently wrong — the displacement as a pure function of distance.
- **Outside the box reads as transparent, not clamped.** Clamping would smear the outermost row outward wherever a
  trough reaches past the box, which looks like a smudge; an icon *is* transparent out there, so nothing is the
  truthful sample.
- **No color**, unlike every other effect in the panel: a ripple moves the layer's own pixels rather than adding any,
  so there is nothing to tint. `waves` steps by one, since it counts crests and 8.37 of them is a precision the
  picture cannot show.

**`LayerEffect.Grain` is the second resampling, and it is what made the loop worth sharing.** Noise pushes each pixel
somewhere else, tearing the artwork into pieces rather than distorting it smoothly. `IconRenderer.resample` came out
on this second consumer — a private helper taking a per-pixel `sourceOf`, not a new file or a public type, which is
the right size for two call sites in one class. It also settled **transparent, never clamped** in one place rather
than two: clamping smears the outermost row wherever a displacement reaches past the box, and an icon genuinely *is*
transparent out there.
- **The noise has to be *smooth*, and that is the whole effect.** A hash per pixel scatters the artwork into confetti;
  a field interpolated between lattice points a grain-size apart moves neighbors together, which is what tears it
  into pieces still recognizable as pieces of it. The test that catches this is the only one that would — a
  discontinuous field passes every other assertion and simply looks wrong.
- **Deterministic and in fractions of the box**, for the reason everything else here is: a field that varied between
  bakes would make the icon shimmer as the studio re-rendered, and a draft would not predict the full-size result.
  That is also why there is **no seed** — a hash *of position* is the randomness, and a seed would be a second
  control offering nothing the grain size does not.
- **Strength and Grain size sound alike and are not.** Strength is how far a piece moves; grain size is how big a
  piece is. Turning the second up makes the tearing coarser rather than stronger.

**Then it was rebuilt against captures of the reference studio's own grain, and four of the five faults were things
that pass every test while looking cheap.** Worth reading as a group, because each is a different way for correct
arithmetic to produce a poor picture:
- **The resample rounded to a whole pixel, which is the big one.** At small amplitudes *the whole displacement is
  the fraction*, so rounding it away turned fine grain into hard aliased specks and a shallow ripple into steps.
  `LayerSample.bilinear` is the fix and is shared by both effects — and it is **alpha-weighted**, `LayerPixelate`'s
  lesson exactly: an icon is mostly transparent, a transparent pixel is almost always transparent *black*, so
  blending by color alone drags every displaced edge toward black. That version passes everything except the one
  test written for it.
- **Value noise put a square grid through the field.** Its extremes land *on* the lattice, so the artwork tore into
  axis-aligned chunks at every setting. Gradient noise reads zero at the lattice and does its varying between,
  which is pinned by the one assertion that would catch a silent revert. The fade is quintic rather than a
  smoothstep, so the field's *rate* of change is continuous too — with a smoothstep the second derivative jumps and
  a displacement makes that visible as a crease along every lattice line.
- **One octave is one size of detail**, which is what made the old field read as blobs. Three, at doubling
  frequencies and halving amplitudes, is what gives it dust and clumps at once.
- **The grain-size slider's useful half was unreachable.** The value was the cell fraction *directly* and the sizes
  worth having are bunched near the bottom of it, so everything from dust to small clusters lived in the first four
  percent of the travel. It is now a 0..1 *control position* mapped **geometrically** onto the fraction, so equal
  movements of the finger are equal ratios of piece size. What is rendered is still a fraction of the box, so
  size-independence is untouched. One consequence: a grain size of zero is the *finest* setting rather than an
  identity, so `isIdentity` is amplitude alone — as the second clause it would have deleted the effect at one end
  of a slider.
- **`GrainDrift` became a continuous `directionality`, and the note this replaces was wrong.** It argued there is
  "no continuum between two fields and one". There is: the displacement is a vector, so decompose it along and
  across the angle and scale the across-part by `1 − directionality`. Zero is the old scatter, one is the old
  directed, and every value between is the wind-blown look neither could express. Free on disk — an unknown *key* is
  skipped by `ignoreUnknownKeys`, unlike an unknown polymorphic *type*, so a stored `drift` is dropped and the rest
  of the recipe reads back.
  - **Its angle control is *disabled* rather than absent, which is this rule's one live exception and the reason is
    the gate.** "A control that changes nothing is worse than a missing one" holds where the gate is a discrete
    choice made elsewhere — a shape picked, a tint set — because the layout settles before the finger arrives. Here
    the gate is the **continuous slider directly above it**, so hiding the row made it appear and vanish *under the
    finger dragging that slider*, moving everything below mid-gesture. `SliderControl` gained an `enabled` for it:
    dimmed, unpressable, value still legible.
- **Strength reaches nearly half the box** where it reached a seventh: at the old ceiling the icon merely frayed, so
  the state a user is at maximum *for* — the artwork dispersed into a cloud of its own colors — was not on the
  control at all.
- **No AGSL path — and when the jank was raised, two cheaper levers were taken first.** A shader is a *third*
  implementation of these six, and unlike every other fork in this codebase it could not be made honest by a shared
  derivation: AGSL is another language, so `LayerGrain` can only be **transcribed** into it, not shared, and for
  these effects the arithmetic *is* the effect. On an API 33+ device the studio would then be editing against a
  picture no home screen draws. So the standing answer is the bake, and the levers are:
  - **`resample` splits its rows across cores** (`BakeBands`, one fewer than the cores, capped at four). Every
    output pixel reads only the source buffer and writes only its own slot, so there is nothing to coordinate. This
    is the one optimisation that also speeds up **baking real icons**, where a shader would only ever have helped
    the editor. One trap it introduces: a `sourceOf` lambda closing over mutable scratch is now shared by every
    band — `grained` writes through `resample`'s own per-band out-parameter for that reason.
  - **Its callbacks are `fun interface`s, not function types, and that is arithmetic rather than style.** Kotlin's
    function types are generic and never specialised over primitives, so `(x: Int, y: Int, into: FloatArray) -> Unit`
    **boxes both `Int`s at every call** — in the innermost statement of the hottest loop in the renderer, which is
    over a million allocations and some nineteen megabytes of garbage per settled bake, paid twice over when the
    collector then takes the main thread's cores. Also speeds up real icons rather than only the editor.
  - **Anything a bake reads once per recipe must be resolved once per bake.** `LayerGrain.displace` computed
    `sin`/`cos` per *pixel* from an angle that cannot change within a bake; `driftOf` resolves it up front. That is
    the same mistake one function along from the one `dot`'s KDoc records as "the whole of why a preview took
    seconds to arrive", so it is worth treating as a category rather than an incident.
  - **`IconPreview` caps the settled bake at `MaxPreviewPx`** and drafts at a fixed `DraftPx`. It is a cap on work
    rather than on quality, and scoped to exactly the icons that need one: this path runs only for a recipe the live
    renderer cannot draw. **The reasoning that a halo and a dot grid "look the same scaled up" does not extend to
    grain, and that claim was wrong here for months** — grain's lattice has a pixel floor, so a small enough draft
    cannot represent a fine setting at all and comes back identical across a whole stretch of the slider. Which is
    why `DraftPx` is a floor rather than a fraction; see the grain notes below. A sharp recipe draws live and never
    reaches the cap.

**And then it was slow and, on a home icon, invisible — three faults that only a device showed, each with a
different cause.** Worth keeping together, because none of them is about the look:
- **`cos`/`sin` per lattice corner is forty-eight transcendental calls per output pixel** — four corners, three
  octaves, two fields, two calls each — which is tens of millions per bake on a studio canvas and the whole of a
  four-second preview. A sixteen-entry gradient table built once replaces them. The KDoc that argued for the angle
  ("a table leaves a handful of preferred directions") is true of one octave and not of three summed into two
  fields.
- **Nothing was cancellable, so `IconPreview`'s whole design was inert.** Its throttle *was* cancellation outright —
  a newer recipe killed the bake in flight — but cancellation is cooperative and a loop over half a million pixels
  cooperates in nothing. (That throttle has since been corrected in a second way: only the *full-size* pass is
  abandoned now, because cancelling the draft too starves whenever one costs more than the gap between two slider
  emissions. See the preview notes below.) Every frame of a drag queued a draft *and* a full bake and every one ran to completion,
  so the preview arrived as a backlog after the finger lifted and the studio starved every other icon on the same
  dispatcher. **`IconRenderer.render` is `suspend` now**, captures its context, and the two per-pixel loops
  `ensureActive()` once a row. Being suspend is what makes the context reachable without callers remembering to
  pass one. An abandoned bake leaves its buffers to the collector rather than recycling them.
- **Gradient noise is zero *at* the lattice, and the renderer was sampling pixel *corners*.** So every `cellPx`-th
  sample landed exactly on a zero — a quarter of them at a four-pixel cell, all of them at one — which made the
  finest setting vanish on any small bake: a 144px home icon grained not at all while the ~670px studio canvas
  escaped it and showed what the surface would never draw, the two-renderer hazard's shape reached through a bake
  size instead. `LayerGrain.latticeAt` samples the **centre** (offset half a *pixel*, not half a cell, since the
  correction is about where a pixel is), which removes the coincidence and lets the floor be **two pixels** rather
  than four. Two rather than one because at a one-pixel cell every sample sits at the centre of its own cell, so
  neighbours share nothing and the field is per-pixel confetti — the look the whole smooth-field construction
  exists to avoid.
- **The size ramp is *derived* from that floor, which is what retired a slider whose bottom third did nothing.**
  `FinestCell = MinCellPx / GrainFidelityPx` — the finest grain any real bake can draw — where it used to be a
  chosen `0.006`, four tenths of a pixel on a home icon, so every setting below ≈0.35 clamped to one cell and drew
  the *same picture*. On a device the control was inert across a third of its travel; in the studio the preview
  stopped responding down there, which reads as the preview having frozen rather than as a slider with nothing to
  say. Derived, the promise this file rests on — one recipe grains the same at every bake size — holds as a
  fraction of the box everywhere from `GrainFidelityPx` up.
- **`GrainFidelityPx` is 144 because three sizes coincide there, and the rule is the largest of them**: the smallest
  bitmap a surface bakes, the size the studio *drafts* at, and the finest grain offered. Raising it to 288 was tried
  — it bought genuinely finer grain and immediately made the bottom sixth of the slider inert **in the draft**,
  i.e. under the finger. That is the same defect one paragraph up, reintroduced by reasoning about the cost to home
  icons while forgetting the preview is the same size. Finer grain is still available and its real price is a larger
  draft, which is a drag-latency decision rather than a noise one. `LayerGrainTest` reads `IconPreview.DraftPx`
  rather than repeating 144, so moving one without the other fails a test instead of reaching a device.

**`LayerEffect.Pixelate` is the odd one of the three per-pixel effects, and shares nothing with the other two.** It
samples one color per *cell* and then **draws** a shape — so the gaps between dots and their rounded corners are
things painted rather than sampled, which is why it does not go through `resample`. Drawn on a canvas the corners
come out antialiased for free, where an `IntArray` would owe its own coverage arithmetic.
- **The averaging is the part that is silently wrong if done naively**, and it is the reason `LayerPixelate` exists.
  Straight ARGB averaging counts a transparent pixel's color equally with an opaque one — and a transparent pixel is
  almost always transparent *black* — so every cell straddling the artwork's edge comes out dark and the icon gains a
  fringe that reads as a rendering fault. `averageArgb` weights by alpha and divides by the alpha total, which is
  premultiplying and un-premultiplying. Pinned by the one test that would catch the naive version.
- **Size is the switch**, since cells with no size are the layer itself. Same shape as the chromatic split's offset,
  reached from the other direction — so no separate strength, and one fewer way to express the same state.
- **Fill and Roundness are what make it a panel of lights rather than a mosaic.** At full fill the dots touch; below
  it the gaps open, and roundness then decides whether what is left reads as tiles or as pixels on a display. The
  radius is a *fraction of the dot*, so full roundness stays a circle at every fill and every bake size.
- A cell whose average is fully transparent is skipped, which keeps the artwork's outline made of dots rather than of
  a square block of them.

**`LayerEffect.ProgressiveBlur` is the thirteenth and the only one built from two mechanisms** — a blurred copy *and*
a ramp deciding how much of it shows. Both pieces already existed (`LayerGradient` places the ramp exactly as it does
a bloom's), so what was new is the joining.
- **The blur is `BitmapBlur`'s, and the thing it replaced was not a blur at all.** It shipped as a `Bitmap.scale`
  down followed by one back up, on the reasoning — written into its own KDoc — that bilinear filtering is "the
  platform doing the same averaging in two calls". It is not: bilinear *downscaling* samples a 2×2 neighbourhood per
  output pixel, so the 30× reduction a mid-slider radius asked for discarded almost everything it was meant to
  average. On a device it came out **terraced** — aliased stair-stepping along every edge and the tent-shaped blocks
  of the upscale. The excuse for it was that `core:icon` could not reach `data:wallpaper`'s kernel; the answer was to
  give the kernel a home neither owns (`core:graphics`), which also costs *less*, a sliding-window box pass being
  O(pixels) and independent of the radius.
- **The ramp is masked onto the *blurred* copy, `DST_IN`, with the sharp one underneath.** Masking the sharp copy
  instead would leave the two overlapping at every partial alpha and the icon looking doubled rather than blurred.
- **`BloomFalloff` became `Falloff`** on this second consumer, since the blur asks the identical linear-or-radial
  question. Renaming the *type* costs nothing on disk — the `@SerialName`s are the contract and each effect's field is
  still called `falloff` — which is what made it worth doing here rather than leaving a duplicate enum.
- **The first stop is capped short of the end**, and that is a crash rather than a nicety: a sharp area of 1 asks for
  a band from 1.001 to 1, and `coerceIn` throws on an inverted range. A slider dragged to its own top would have taken
  the bake down. Found by the test, not on device.
- Labelled **"Focus"** in the panel, since what a user is choosing is what stays in focus — the blur is how that is
  expressed. It is also the one name that would not fit a tile at four columns.

**Persistence — one serialized `IconLayerSet` blob, NOT flat columns. Done.** (L1 burned four destructive DB
bumps learning this.) `icon_override` is now `component` + a JSON `layerSet` blob (**DB v2 → v3**, destructive,
free pre-launch), and the global default is a fifth `data:settings` slice under `icon_layer_set` — the bare
`IconLayerSet`, `BackdropEffect`'s shape, because the recipe *is* the whole setting. Editing an app **snapshots
the default and detaches** (Reset re-attaches) — no field-merge, no variable-length-list diffing. Three things
worth knowing:
- **The model lives in `core:model.icon`**, not `core:icon` — it is pure data describing what an icon should
  look like, where turning that into pixels is the renderer's job. Third cut of the same kind after
  `BackdropEffect` and `DeviceConfiguration`, and what forces it is that *two* modules store a recipe and neither
  should depend on a module that allocates bitmaps. `IconShapes` stays behind (it maps ids to `R.drawable`), and
  the move took the serialization plugin out of `core:icon` entirely.
- **An unreadable row is skipped, not deleted.** It falls back to the global default, which is visible and
  fixable; deleting would throw away a recipe a later build could read. Same position `data:layout` takes on an
  unresolvable placement. Two things reach that path — a corrupt blob, and a well-formed one describing an
  *illegal* stack, which `IconLayerSet`'s own `init` rejects.
- **Adding an effect is not a schema change**, which is the whole point of the sealed list: the spec gained
  `opacity` and `blend` and the test asserting the exact stored JSON of `IconLayerSet.Base` still passes, because
  defaults are not encoded.

**Custom images: nothing is written until Save.** `CustomIconStore` splits decode from write — the path is
*reserved* up front so the recipe can refer to an image that does not exist yet, the preview draws it from
memory, and backing out leaves nothing behind. That is the fix for the orphan leak L1 recorded and accepted. On
save the images go down **before** the recipe (a recipe pointing at an unwritten file renders as a missing layer;
a written file nothing points at is collectable), and orphans are **swept** — `retainOnly` asks what any recipe
still refers to, against per-action deletes that must be right at every site that can drop a reference and leak
invisibly when one is missed. **No crop screen, unlike L1**: a layer already has offset, zoom and rotation, so a
crop would be a second and destructive way to do the same thing; images are fitted into a transparent square on
the way in, which also spares both renderers an aspect-ratio special case they could disagree about.

**Icon packs are a `LayerSource`, not a mode**, which is what makes "apply a pack to everything" not a feature:
it is setting the global default's fg source, and it then goes through the same commit, cache key and
invalidation as any other edit — and every decoration layer is untouched by construction, since a pack only ever
occupies the slot it is put in. `IconPackManager` is L1's, ported: packs are found by the de-facto **theme
intents** they declare and mapped through an `appfilter.xml` keyed on `ComponentInfo{pkg/cls}`, both conventions
rather than choices. **One thing L1 got away with and we cannot** — `queryIntentActivities` is subject to package
visibility filtering on API 30+, so detection returns an *empty list* on every modern device without a narrow
`<queries>` block; L1 was covered by `QUERY_ALL_PACKAGES`, which this launcher does not request. That block lives
in `data:icons`' own manifest and **must stay in step with `IconPackManager.ThemeActions`**. `core:icon` reaches
it through `IconPackImages`, a seam declared on the consumer side like `RawIconSource`, so the render modules
never learn what a pack is.

**A pack's drawables can also be browsed**, to give one app a *named* icon rather than the one `appfilter.xml`
assigns it. The list needs no separate "drawable lister": that file's **values** are drawable names, so browsing
is a projection of what a pack already loads. **Individual mode only** — a named drawable on the global default
would be inherited by every app — and the grid decodes only cells that scroll into view, canceling on a flick,
over a bounded LRU. **Deferred:** drawables the author mapped to no app, and `drawable.xml`'s categories; shadows (above); skin/backing-plate (L1's separate live-Compose backdrop, distinct from the baked stack).

**Presets are a named `IconLayerSet`** — the recipe plus a name, no separate format, and stored as a
`data:settings` slice rather than a Room table because a library is a handful of documents read whole, where
per-app overrides are a row per customized app read one at a time. **Applying one is opening the studio loaded
with it, never a write**: a preset restyles every icon that inherits the default, which is not something to do
from a list row with no way to look first — so the dashboard row navigates, the session opens *dirty*, and Save
commits. A preset is a **copy, not a link**: loading is an ordinary undoable edit and deleting touches nothing it
was applied to. Built-in curated presets stay out, being a content decision rather than an engineering one.

**The studio is a full-screen destination, and the settings pane above it is a hub.** L1's icon settings *were*
the editor, hosted in the detail pane and built out of settings-list vocabulary, and its own docs conclude that
was the whole problem; there is a second reason here it did not have, which is that a pane shares the screen with
the section list on a tablet and a creative workspace cannot have half a screen. So `SettingsSection.ICONS`
returns — the name this file has been holding back for it — as **Edit all icons / Edit specific apps / a Presets
placeholder**, and the editing happens in `IconStudioRoute`. Reached from there, or from **"Edit icon"** on any
app's context menu. Five things about it:
- **`IconStudioRoute` is a sealed pair** (`Global` / `App(component?)`), not L1's mode-beside-a-nullable-component
  — that shape can express a global route carrying an app. `App(null)` is a real state: arrive at the picker.
- **The edited set is read once and then owned by the screen.** A live editor diverges from the store the moment
  a slider moves, so projecting the repository flow would mean writing every frame of a drag or having the next
  emission overwrite the user. It is the same snapshot-detach the persistence layer runs on, one layer up — and
  it makes a freshly opened *inheriting* app correctly `dirty`, since saving is what detaches it.
- **Undo is punctuated, and nearly free.** The live path records nothing; `commitEdit` (a slider's
  `onValueChangeFinished`) lands one history entry per gesture, so undo steps *over* a drag rather than back
  through a hundred frames of it. History is a `List<IconLayerSet>` and a step is an index — L1 left undo an open
  feasibility question because its equivalent state was a bag of mutable flat fields with nothing to snapshot.
- **Save is explicit in both modes**, departing from L1's live-committing global studio: a slice is one JSON blob,
  so a live-committing slider rewrites the whole document per frame, and a global edit restyles every icon on the
  device — not a thing to do continuously while someone is still deciding. The *preview* is live either way,
  which is all "live edit is non-negotiable" ever meant.
- **There *is* a "this layer / whole icon" split now, and it is a tile in the rail rather than a scope toggle.** This
  used to say there was none, and that was right while every one of L1's six whole-icon tools had somewhere else to go:
  the tile shape became a per-layer shape *and* — since `IconLayerSet.shape` — a stack-level one, the background is the background
  layer's source, theming is `AppDefaultMonochrome`, sizing is `data:settings` and another screen, the skin is
  deferred, and a pack is a per-layer source. **Effects are the one that had nowhere to go** — see the whole-icon
  effects note above — so the composite became a selectable target, which is a different answer from L1's open question
  rather than a reversal of this one: the scope is chosen by *selecting a thing*, not by a mode switch inside a panel.
- **The stack is a rail down the canvas edge, not a bar entry** (`StudioLayerRail`), and the `LAYERS` tool is gone.
  The bar had swallowed the one thing that must never need opening — `StudioToolPanel`'s own KDoc recorded it:
  *"while the stack was permanently on screen, 'which layer am I editing?' was answered by looking at it"*. Once the
  rail also reordered, hid and deleted, the entry's only remaining job was *add*, and an entry that is one button is
  a button — it belongs where the layers are, as the `+` at the end of the rail.
  - **The whole icon is the tile at its head**, which is what makes the rail the studio's scope control rather than
    only its layer list — see the whole-icon effects note above.
  - **Tap selects; long-press selects *and* opens the quick menu.** Selecting first is what lets one set of commands
    serve every tile — the rule the old eye button already followed, since an action on an unselected row silently
    acts on a different layer. So the menu reads `state.canMoveUp` and friends, which are answers about the
    *selected* layer, and no per-index variant of any of them had to exist.
  - **Every menu row that would do nothing is disabled, never absent** — the reason reorder was buttons and not a
    drag: a disabled row says which move is illegal *before* it is attempted. The answers come from the model
    (`editing.moveUp(i) !== editing`), so they cannot drift from the rule the set enforces.
  - **A tile is the real render path with every other layer hidden.** `IconLayerSet`'s `init` forbids a set without
    a foreground and a background, so a one-layer set is unrepresentable — but visibility is per layer, so hiding
    the rest says the same thing through `IconLayerStack`. The tile therefore shows that layer's transform, shape,
    effects and source exactly as the icon will, with no second way to draw a layer that could drift. It sits on the
    canvas's own **checkerboard**, because most layers are mostly transparent and a dark glyph on nothing is an
    empty tile on dark glass.
  - **Drawn top layer first**, matching the list it replaced and the order layers are drawn on screen. That
    reversal is load-bearing beyond the UI: `IconStudioViewModel.removeSelected` moves the selection *down* an index
    to keep the highlight on the same tile, and only makes sense while they are drawn this way round.
  - **The icon bound shifts toward the start to clear it** (`IconBoundShift`, the horizontal twin of
    `IconBoundLift`) — a fraction of the canvas rather than the rail's width in dp, because `drawBackdrop`
    reproduces that square from its own draw-time size and anything it cannot derive would have to be threaded in
    and kept in step.

**The studio is the one screen with a second blur system, and the two do not overlap.** `wallpaperBackdrop`
samples a *pre-blurred wallpaper bitmap* by position and can only ever show the wallpaper; the studio's canvas is
deliberately **not** the wallpaper (black / white / a checkerboard, plus the icon being edited), so it is the only
screen whose backdrop is content the launcher itself draws and the only one that blur structurally cannot serve.
**Haze** blurs whatever is really beneath a node, and that "no wallpaper" decision is what *guarantees* it works —
Haze needs a real drawn node, and the `BlendMode.Src` punch every settings preview uses would leave it nothing.
One shared `studioSurface` modifier is the material, so a new panel cannot arrive looking different; its content
color is **fixed white**, the one place the studio departs from the theme, because the thing behind the glass is
a canvas the *user* switches between black and white.
- **Two `HazeState`s, not one, because the layer rail is a surface *and* something to see through.** Haze samples
  what is behind a node, so one shared state has the rail sampling **itself**, and has the panel — which overlaps
  the rail's lower half the moment it opens — sampling a rail with nothing behind it, which reads as an opaque edge
  the panel disappears under. `canvasHaze` is the work alone and the rail's own glass and quick menu read it;
  `screenHaze` is the work *and* the rail, read by everything floating over both. The rule is one line: **the rail
  samples the canvas; everything above the rail samples both.**
  - **A node can register with several states, which is what makes this cost a line rather than a redesign.**
    `HazeInput.Sources` takes exactly one state, so a *consumer* can never combine two — but `hazeSource` is
    `this then HazeSourceElement(...)`, one modifier node per call, so a *source* can belong to as many as it likes.
    The canvas simply carries two. The z-indices are stated rather than inferred from draw order, so a reshuffle of
    the screen's `Box` children cannot silently reorder what the panel sees.

## Design system (`core:designsystem`)

- **Keep Expressive *motion*, drop Expressive *visuals*.** New/reworked UI uses Material 3 **Expressive
  motion** but not its look. `LauncherTheme` sets `motionScheme = MotionScheme.expressive()` on the base
  MaterialTheme; components get the expressive spring choreography by consuming `MaterialTheme.motionScheme`.
  The Compose BOM does **not** carry the Expressive APIs, so `material3` is pinned to `1.5.0-alpha22` in the
  version catalog; opt in per-usage with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where the
  compiler asks.
- **Monochrome palette.** Grayscale chrome so the wallpaper + app icons carry the color. `accent` is a
  high-contrast grayscale *emphasis* (not a hue) — selection/active read by contrast; **red is reserved for
  `error`** only. **Both light and dark are first-class** (dark mode is an accessibility barrier for some
  users). Semantic tokens live in [theme/MorphicColors.kt](core/designsystem/src/main/kotlin/inkspire/morphic/core/designsystem/theme/MorphicColors.kt).
- **Theme layering + monochrome M3 bridge.** `MorphicTheme` provides our colors only (`LocalMorphicColors`).
  `LauncherTheme` = M3 base + expressive motion + `MorphicTheme`, and it feeds MaterialTheme a **monochrome
  M3 `ColorScheme` bridged from `MorphicColors`** (`MorphicColors.toM3ColorScheme(dark)`), so stock M3
  components render grayscale *and* keep Expressive motion. Use `LauncherTheme` as the app wrapper.
- **Build components *on* M3, restyle — go fully custom only where M3 has no equivalent.** Because the scheme
  is bridged monochrome, wrap the real M3 component and get its native Expressive motion for free:
  `MorphicButton` = the M3 button family + `ButtonDefaults.shapes()` (press shape-morph); `MorphicSlider` =
  M3 `Slider` with a custom `thumb` slot over M3's own track (our grow-on-press thumb, M3's track + grab). Reserve fully-custom `Row`/`Canvas`
  for controls M3 lacks — the **2D pad**, the **segmented control** and the **switch**. The **range slider** is built on M3
  `RangeSlider` (custom thumbs, M3 track); a **vertical slider** (custom Canvas — M3 has none) is deferred
  until a consumer needs it.
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
    **slider's** `trackInactive`/`trackActive`/`thumb` roles so the two controls are made of the same greys, with
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
- **Modern state APIs behind convenient facades.** Components sit on the **state-hoisted** M3 APIs internally
  (`Slider(state = …)`/`RangeSlider(state = …)`) — not the value-based overloads (deprecation path). But
  they expose a plain `value`/`onValueChange` API and create + bridge the state *inside* (`LaunchedEffect(value)`
  in, `snapshotFlow { … }.drop(1)` out), so call sites need no `remember*State`. **Exception — the text field
  keeps a hoisted `TextFieldState`** (`rememberTextFieldState()` at the call site): its config-change survival
  needs the caller to own the state, so hiding it would throw that benefit away.
  - **The sliders do *not* use `rememberSliderState`/`rememberRangeSliderState`, and must not be "fixed" back to
    them.** Those factories are `rememberSaveable(saver = …) { State(…) }` — an init block that never re-runs — so
    they freeze three arguments at first composition. Two of them matter here: `onValueChangeFinished` (a `var` the
    factory never re-assigns, so a facade hands the state the *first* composition's closure forever — which is what
    made `SettingsCommitSlider` commit its first drag and then re-commit that same value on every later one), and
    `valueRange`/`steps` (`val`s, so a slider whose range legitimately moves — the APPS row height is bounded by the
    icon guardrails — keeps mapping the finger through the range it was born with). So the state is a `remember` keyed
    on `steps`/`valueRange`, with the callback pushed in by `SideEffect`. What is given up is restore across a
    configuration change, and it is not load-bearing: `value` is the caller's, so the state is re-seeded from it.
- **The text field wraps `BasicTextField` (foundation primitive) + a `decorator`, not M3's `TextField`.** M3's
  styled field is too opinionated about focus/label/indicator; the primitive gives full focus control — our
  own `onFocusChanged` state, the focus ring, placeholder-behind-field, and clearing focus when the IME is
  dismissed (`WindowInsets.isImeVisible`).
- **Settings vs launcher color = one theme, two "is-dark" inputs** (not two palettes). Settings feeds
  `darkTheme = isSystemInDarkTheme()` (our controlled surface); the launcher feeds a **wallpaper-brightness**
  signal (chrome must contrast the wallpaper — bright wallpaper → Light scheme/black tint, dark → Dark/white).
  Apply the theme per **zone boundary** (launcher shell vs settings graph), not per nav destination; a nested
  `LauncherTheme` overrides its subtree. **The brightness half is built, and so is the frosted backdrop** (see below);
  `FrostedTextField` and the rest of the frosted-chrome subsystem stay deferred; settings needs none of it. **The window
  half has landed**: `app`'s theme carries the platform's own `Theme.Wallpaper` recipe (`windowShowWallpaper` over a
  transparent `windowBackground`, `colorBackgroundCacheHint` null), which is what makes this a launcher's window rather
  than an app's — and what capture, the icon preview's `BlendMode.Src` punch-through and the frosted backdrop were all
  waiting on. Home paints **no background** as a result (it *is* the wallpaper, and its cell labels already carry a
  shadow); **APPS and the folder are transparent too, now that the frost they promised exists** — see the
  full-screen-frost bullet below. The note here used to say "APPS stays opaque, which is legibility rather than
  inconsistency", naming L1's frosted backdrop as what would replace it; that is what `SurfaceBackdropLayer` is.
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
- **The frosted backdrop is `core:designsystem/backdrop`, and it samples by *position*.** `Modifier.wallpaperBackdrop`
  draws the crop of the pre-blurred wallpaper that sits behind wherever the node currently is, so a surface that moves
  slides *over* the picture rather than carrying a patch of it — which is the whole difference between glass and a
  texture. `BackdropState` is **shared images plus a mapping, not a bitmap per surface**, so two frosted surfaces
  side by side continue each other and the cost is a blur for the screen rather than one per node. There are **two**
  pictures, and the split is `BackdropRole`: a *panel* samples the wallpaper blurred at the user's own strength (the
  effects section's slider), the **full-screen film** samples it at the fixed strength `fullScreenFilm` names. One image
  cannot be both — at a panel blur of zero it is the sharp wallpaper, and a sharp sheet occludes nothing. **The picture
  and its mapping are one type (`BackdropImage`) for a reason that is invisible when broken:** `downscaleFor` reduces in
  proportion to the blur, so the two are routinely *different sizes*, and a mapping applied to the wrong one draws a
  crop at the wrong scale — which reads as the wallpaper sitting slightly off behind the glass rather than as a
  mismatched pair of arguments. It is a `Modifier.Node` and not a
  `drawBehind` because of exactly that motion: the outline and clip `Path` are cached against size and shape, so a
  position-only change rebuilds nothing. Ported from L1's `Backdrop.kt`, with four differences:
  - **Every effect blurs; what they differ in is the *wash* — which is why `None` is now `Plain(strength)`.** The model
    used to let an effect decline to sample the wallpaper at all, and the full-screen frost overturned that: a surface
    arriving over HOME has to occlude it whatever decoration the user picked, so the only choice ever really on offer
    was *which wash*. `blurStrength` is therefore total, and "nothing to sample" means one thing — `LocalBackdrop` being
    null, i.e. the launcher has no wallpaper it may read. The `@SerialName` stays `"none"`, so no stored blob moved.
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

**Wallpaper — `data:wallpaper` (B7b) exists, with the static image in it, and a section that drives it.** Its own module rather than a slice of
`data:settings`, because it decodes bitmaps, writes files and calls `WallpaperManager` — a *service*, where settings is a
store. **It keeps its own one-key DataStore too**, which is a correction to the plan's original line ("borrowing settings
to persist path pointers"): a path to a file we wrote and the id the system gave the wallpaper we set is bookkeeping, and
S0 had already refused that on the way in. The **effect params** (`BackdropEffect`) are the genuinely preference-shaped
half and stay in `data:settings`. State is **four fields where L1 had six**, and two of the four are the
ones L1 had for the frosted backdrop: a **snapshot copy** of the image as applied to home (`appliedHome`) and a latch
saying whether the current pick is that image (`imageApplied`). `appliedSystemId` is an id rather than a boolean because
it also detects a wallpaper set outside the launcher. Built: **all three sources** — pick from a `Uri`
and frame it on the crop screen, **capture** a screenshot of the wallpaper itself, or set the **rotating pair**, one
image per orientation — each sample-decoded, scaled to the screen and stored through one write path, plus apply to
HOME / LOCK / BOTH. `WallpaperSource` is what separates them: a capture is a
picture *of* the wallpaper, so `apply` declines it and it exists only for the effects to sample (it is the one way to
read a **live** wallpaper). Capture landed before its consumer on purpose — an effect has to answer "which image do I
sample?", and answering that once against every source beats re-answering it per source. Nothing invents a crop any more — `setImage` takes a
`NormalizedCropRect` and the screen passes the region the user framed, against the viewport it also passes as the size
to store at, so the rectangle and the result share one coordinate space. **The reading half is `brightness`,
`accentColor` and `backdrop`** — the three questions anything drawing over the wallpaper has: how bright is it, what
color is it, and what does it look like blurred. All three share one change signal and one "is our file what is on
screen?" gate, deliberately, because three answers that could disagree about *which image* they read would be worse
than any one of them being slightly off. Each also asks the system before it decodes anything: `getWallpaperColors`
answers the first two over the wallpaper *actually displayed*, and `Blur.kt`'s `dominantColor` is only the API-26
fallback for the second. One L1 bug not carried: its repository read-modified-wrote its state *outside* any
transaction, so picking an image while an apply was finishing could lose one of them.
**`backdrop` answers "which image does an effect sample?" once, for all three sources** — the question the whole
sources-before-effects ordering was arranged around. Our rotating service active → that orientation's half; a
**capture** → always (it *is* a picture of what is displayed, and gating it on being applied would reject it forever);
a picked image → only if `appliedSystemId` still matches the live wallpaper id; otherwise nothing, and every frosted
surface falls back to its scrim. **That third test needs both halves, and this file claimed for a while that it needed only
one.** L1 froze a snapshot of the applied image (`appliedSingle`) so an edited-but-unapplied pick could not
desynchronize the backdrop, and the id comparison was taken to replace it. It does not: the id answers *"is the
wallpaper on the system still the one we set?"* — which the snapshot could not, since a wallpaper changed outside the
launcher left L1's copy claiming to match — but it says nothing about whether **we still have that picture**, and
`WallpaperFiles.IMAGE` is one fixed name that every pick overwrites. So `appliedHome` is a real copy, taken after a
successful apply that included `FLAG_SYSTEM`. Three things went wrong without it, and the third is what surfaced it:
picking an image killed the backdrop until it was applied; `setImage` built a **whole new `WallpaperState`**, so a pick
also erased the rotating pair's references and left its files orphaned; and "pick, then apply to the *lock* screen" —
which cannot change the home screen at all — erased the frost outright. **Which is also why the pick's own
applied-ness is its own field**: `appliedSystemId` stays true across a new pick, so it must not be reset by one, and the
section's "Apply" / "Re-apply" reads `imageApplied` instead (L1's `singleDirty`, inverted). It is the **same gate**
`brightness` uses, deliberately: "is our file what is on screen" is one question, and two answers to it would drift.
**Two traps in that gate, both fixed and both silent while they were live.** The id is only recorded when an apply
actually included `FLAG_SYSTEM` — recording it unconditionally meant a *lock-only* apply wrote down the id of a home
wallpaper it had not touched, so the gate compared that id against itself and answered true on no evidence, leaving the
frost blurring an image that was not on screen and the brightness fallback theming against it (L1 reads the same id
regardless of its own `which`). And the backdrop keys on the file's **identity** — path plus `lastModified` plus
`length` — not on its name: the stored images sit at fixed paths and are overwritten, so a capture over a capture, or a
rotating half replaced while that pair is live, changed the picture and nothing else. That one needed the internal state
signal to stop de-duplicating too (`storedState` vs the public `wallpaper`), since a repeated capture produces a
byte-identical `WallpaperState` — de-duping a *value* is right for a consumer that renders it and wrong for one whose
real subject is a file the value only names.
It is also a **flow** where L1's was a one-shot read, because two of those four answers change with no action from us.
**The rotating pair is a *live* wallpaper, and that shapes three things.** Android has no per-orientation static
wallpaper — `setBitmap` takes one image and the system crops it — so drawing a different picture in landscape means being
the renderer: `RotatingWallpaperService` lives in `data:wallpaper` beside the files it reads, where L1 put it in its
settings feature and left its data layer unable to name its own service. It cannot be applied silently either — the
platform insists the user confirm in its own preview — so the section opens that chooser and then **asks on resume**
whether ours ended up active; `WallpaperManager.wallpaperInfo` is the answer, so nothing is latched and there is no
reconciler, where L1 stored `appliedMode` and needed `reconcileLiveWallpaper` to repair it. And the crop screen frames
the landscape half **letterboxed** rather than pinning the activity's orientation as L1 did, which it can afford because
the frame decides the *shape* while the target screen decides the *resolution*. And the service **publishes its
colors**, which L1's did not — see the design-system note above; it is what lets the rotating pair answer the
brightness question through the same system API as every other wallpaper.
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
- **An item's touch target is its visible extent, never its cell.** A cell is a *layout* footprint, usually much
  bigger than what is drawn in it (a home cell is a 2×2 visual slot around one icon + label). `LauncherDragCell`
  therefore hands `itemGestures` **down to its content**, which decides what is touchable: `IconLabelCell` puts it
  on the icon+label group; content that genuinely fills its cell (a widget, a harness tile) `.then()`s it onto its
  root. The slack must stay free — otherwise a full page of icons leaves nowhere to press-and-hold for the
  *surface's* menu. Works because `launcherItemGestures` never consumes a down. Consequence: cells (`AppCell`,
  `FolderCell`) carry **no `onClick`** — taps arrive through the one gesture contract. See
  [docs/DRAG_AND_DROP_DESIGN.md](docs/DRAG_AND_DROP_DESIGN.md) §5.
- **A measured position is not trustworthy once the node is inside a scroller — reconstruct it.**
  `onGloballyPositioned` does **not** reliably re-fire when scrolling moves a node, because the scroll moves it through
  its *parent's* placement without re-running the node's own layout. So anything doing finger→cell maths against those
  bounds silently drifts by the scroll distance. Publish from a **stable anchor plus the scroll offset** instead: the
  scroll *viewport*'s position genuinely doesn't move (put `onGloballyPositioned` **outside** `verticalScroll` in the
  chain), and `scrollState.value` is snapshot state, so `viewportTop - scrollState.value` is the content origin and it
  republishes every frame for free. `CategoryPage` in `feature:apps` is the worked example. Two corollaries:
  - **The symptom is a *constant* offset, which is how you tell it from a timing bug.** With a stale origin at offset
    `S0` and a real offset `S`, a finger at `y` resolves to a cell drawn at `y - (S - S0)` — so the drop footprint sits
    a fixed distance from the finger, tracks it while dragging, and snaps into place the moment the content returns to
    `S0`. A lagging or jittering offset is something else; a rigid one is staleness.
  - **And the rectangle it reports must be *unclipped*, which `boundsInRoot()` is not.** That call clips to every
    ancestor's bounds, and a scroller clips — so a node below the fold reports an **empty** rectangle and one half
    off the top reports half of itself. Anything hit-testing against it then silently refuses, which is not a
    misplacement you can see: it reads as "that target just doesn't work". Use `positionInRoot()` + `size` for a node
    *inside* a scroller; `boundsInRoot()` stays right for a **viewport**, which is above the clip it applies. The
    APPS category card is the worked example, and it only became reachable when that grid stopped being lazy —
    while it was lazy an off-screen card did not exist, so the difference between "not composed" and "composed and
    lying" never arose.
  - **A correct geometry that nothing re-reads changes nothing.** `DragCoordinator` resolves a plan only in `moveTo`,
    i.e. when the *finger* moves — while auto-scroll (`DragAutoScrollEffect`) exists precisely to move content under a
    finger held still. A surface that auto-scrolls must therefore **re-send the same finger position** after
    republishing, and in that order (a `snapshotFlow` on the scroll offset fires *before* the recomposition that
    derives the new geometry, so it stays a step behind — put both in one `SideEffect`).
- **Don't invent a dimension nothing owns yet.** Launcher surface metrics (dock extent, home padding, icon size, grid
  rows) are **settings-driven by design**. Until `data:settings` exists, use the plainest possible stand-in — a named
  dp constant, or "fill the rest" — and KDoc it as a placeholder naming the setting that replaces it. Do **not** derive
  it from something else to make it look principled: derived-looking arithmetic reads as a decision when it is really
  a guess, and it hides that the value is still unowned (worse, it can invert the real dependency — the dock's row
  count comes *from* its height, so sizing its height from a row count is backwards). `RowHeight` in
  `AppsVerticalList` is the live example; `DockHeight` in `HomeScreen` was the worked one until S4c gave it an owner,
  and the placeholder deleted cleanly precisely because nothing had been derived from it.
- **But a dimension that is a *consequence* of one the user owns must be derived, not stored** — the other half of the
  rule above, and the two are told apart by whether a formula exists. A scrolling grid's cell **height** is the case:
  its columns fix the cell width, and what is left is exactly what the icon and its label need, which is
  `IconLabelCell`'s own arithmetic run forwards (`derivedCell` in `core:designsystem/grid/CellFit.kt`, ported from L1's
  `gridCellHeightDp`). Storing it *as well* would let two settings disagree — enlarge the icons and get bigger icons in
  cells that stayed the same height — where deriving it makes S3's icon sliders visibly move the grid, as they do in
  L1. The list's row height is the opposite case and the reason the distinction is worth stating: a list has no cell
  width to derive from, so nothing determines the row and it is genuinely the user's to set, with the icon a fraction
  of *it*.
  - **A fraction spent on a derived height must not be spent again by the cell**, which is why `derivedCell` hands back
    a height **and** the metrics to draw with (`iconPercent = 1f`) and why its callers must use both. The height *is*
    `iconPercent` of the inner width plus the chrome, so a cell given the original metrics resolves its icon as
    `iconPercent × min(innerWidth, iconArea)` where `iconArea` is already that product — `iconPercent²` of the width,
    which lands on the lower guardrail inside a row built for something larger (at 50% on a 4-column phone: a 24dp icon
    in a row sized for 41). L1 had the same double-application (`gridCellHeightDp` × `resolveIconSize`) and this port
    inherited it; nothing showed until the icon defaults reached 100%, where the two agree. So on these grids the user's
    fraction chooses the **cell height**, and the icon then fills exactly what that height bought — one job each.
- **Insets are one expression, `uiInsets` (`insets/UiInsets.kt`), and a surface paints *through* them.** L1's helper,
  finally ported: `systemBars ∪ displayCutout` — deliberately not `safeDrawing` (nothing on a launcher surface takes
  text, so reserving an IME that never appears is a permanent gap) and deliberately not `systemBars` alone (a notch is
  not a system bar). It had been written out longhand in eleven files, which is how a launcher ends up with home
  honoring the cutout and its settings screen not. `Modifier.uiInsetsPadding(sides)` is the padding form —
  `@Composable`, not L1's deprecated `composed { }`.
  - **The bars are *content* padding, never layout padding, on any surface with a background.** The window is
    transparent under this launcher's theme (`windowShowWallpaper`), so a container that stops short of the window edge
    does not leave a blank strip — it leaves a **hole through to the wallpaper**. That is what the settings shell did:
    `Scaffold`'s default `contentWindowInsets` reserved the navigation bar, and with `containerColor = Transparent`
    (which the icon preview's `BlendMode.Src` punch requires) the reserved strip showed the wallpaper. So both scaffolds
    zero `contentWindowInsets`, each pane fills to the window edge and paints itself, and the insets are applied *inside*
    — `contentPadding` on `SettingsList` (so rows scroll under the bar) and `uiInsetsPadding` inside `PunchThroughPane`.
    The FAB then pads itself, since the scaffold's reservation is what normally lifts it clear. L1 zeroed the same two
    fields, on exactly the sections whose detail shows the wallpaper.
  - **Which sides is the shell's answer, not the pane's** — a pane beside another pane must not inset the edge its
    neighbor covers, or the gap opens in the middle of the screen. So `insetSides` is a parameter on both `SettingsList`
    and `PunchThroughPane`: horizontal+bottom on a phone, start+bottom / end+bottom either side of the tablet divider
    (L1 passed the same two to its two-pane list). The **top** is nobody's: the app bar covers the status bar and
    `consumeWindowInsets(innerPadding)` says so, which is why the panes below can ask for every side and get nothing
    back on top.
  - The **top app bar** takes `uiInsets` too, because M3's default (`systemBars` only) would seat its back button under
    a landscape notch. `MainActivity` sets both bars scrimless before `super.onCreate` and turns
    `isNavigationBarContrastEnforced` off on API 29+ — `enableEdgeToEdge` leaves it on, and it lets the system paint a
    translucent scrim over the wallpaper. L1 did both, in that order, in that place.
- **Packaging discipline (unlike L1):** `component/` holds *only* the generic `Morphic*` UI primitives;
  colors/theme live in `theme/`; launcher-specific icon cells (`AppCell`/`IconMetrics`) get their own
  package. Do **not** mix generic components and app-icon widgets in one package like L1 did.
- **Port per-consumer.** L1's `core:designsystem` is ~50 files / 5.4k LOC — pull a group only when the
  screen that needs it is built, not up front.
- A **dev gallery** (`app` → `dev/DevGalleryScreen`) hosts every `Morphic*` component + the palette under a
  light/dark toggle; add each new component to it.
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

## Feature & presentation architecture (locked 2026-07-27)

**Plain MVVM — no MVI, no reducer.** This is a hard rule precisely because L1 *claimed* "MVVM + MVI + clean
architecture" and drifted into monolith ViewModels (a 500-line sealed-`HomeEvent` + `when(event)` block). We do
**not** repeat that. The pattern for every screen:

- **A `ViewModel` per screen** (`androidx.lifecycle.ViewModel`), not a hand-rolled singleton. It exposes **one
  immutable `StateFlow<XxxState>`** (the "Model") and **plain typed methods** for events (`launch()`,
  `applyChanges()`) — the Now-in-Android style. **No sealed `Intent`/`Event` hierarchy and no single `onEvent`
  dispatcher**; that ceremony is what rotted L1. Unidirectional flow (UI → method → state → UI) with a single
  state object is the whole of the "MVI" benefit, and we already have it without the machinery.
- **Coroutines run on `viewModelScope`** (not an injected `ApplicationScope`), so work cancels with the screen.
  Bind with Koin's `viewModel { }` DSL (`org.koin.core.module.dsl.viewModel`) and inject with
  `koinViewModel<XxxViewModel>()` — this scopes the instance to the screen's `ViewModelStore` (survives
  rotation, cleared with the screen).
  - **That last clause is only true because `NavDisplay` is given `rememberViewModelStoreNavEntryDecorator()`**,
    and it was false for a long time. `navigation3-runtime` ships only the saveable-state decorator, so by default
    every `NavEntry` shares the **Activity's** store: a `koinViewModel<T>()` keys on the type, so the first entry
    to ask for a given ViewModel created it and every later entry — different destination, different arguments —
    was handed that same instance, with `viewModelScope` outliving the screen it belonged to. It stayed invisible
    because no ViewModel took a **per-instance parameter** until the icon studio's route; every other one reads the
    same repositories and rebuilds the same state, so a shared instance was indistinguishable from a fresh one.
    Supplying the decorator list **replaces** the defaults, so the saveable-state one has to be named again or
    every entry loses its `rememberSaveable` state.
- **Keep logic out of the composable.** The composable reads `state` (via `collectAsStateWithLifecycle()`) and
  calls methods; assembly, persistence, and optimistic state live in the ViewModel so it stays unit-testable.
- **Reference:** [feature/home](feature/home/src/main/kotlin/inkspire/morphic/feature/home) — `HomeViewModel`
  (StateFlow + `launch`/`applyChanges`), `HomeScreen`, `HomeState`, `di/HomeModule`.

**Feature modules own their screens; `app` only assembles.** Each screen lives in its own `feature:*` module (not
in `app`), applying the `launcher.android.feature` convention plugin — which is why that plugin pre-wires
core:model/common/designsystem + lifecycle-viewmodel + koin-compose + Compose. A feature adds only the extra
deps it needs (e.g. `feature:home` adds `data:apps` + `data:layout`). The `app` module is the shell: it starts
Koin with every module's DI graph and hosts the entry points (currently the `dev/DevRootScreen` harness). New UI
starts in a `feature:*` module from day one — do **not** prototype it in `app` and "extract later".

**Repository vs command split.** A repository is *read/refresh* access to data (e.g. `AppRepository` streams the
app cache). A side-effecting *command* gets its own honest type rather than being bolted onto a repository — e.g.
launching is `AppLauncher` (`data:apps`), a one-method interface, not a `launch()` on `AppRepository`. (L1 folded
launch onto the repository; we don't.)

## Current status

Foundations: **P0 done; P1 Core done** — `core:model` (B0), `core:common` (B1), `core:database` (B2). **B3
`core:icon` done** (parse → layer model → render/bake → `IconRenderManager` → `LauncherIcon`), and since the icon
studio it also holds the **live** render path (`IconLayerStack`) plus the shared derivations that keep the two
honest — the layer *model* moved out to `core:model.icon` on the way. **B9 `data:icons` done** for everything but
icon packs: `IconOverrideRepository` over the collapsed `icon_override`, and `CustomIconStore`. **B6 `data:apps`
partial** (LauncherApps wrapper, `AppRepository` + Room cache,
`RawIconSource`, and **categorization** — `AppCategorizer` folds a curated asset → platform
`ApplicationInfo.category` → keyword heuristics into a `CategoryGroup` id, ported from L1 but narrowed to *one app
in, one id out*: ordering and overrides are the category store's job, not the classifier's).

**The app cache is a mirror, and it keeps itself in step** — L1's `AppEvent` half, no longer deferred, and the fix
for an uninstalled app staying on every surface. Three parts:
- **`refresh` replaces rather than upserts.** An additive refresh cannot express "this app is gone", and *every*
  surface resolves its items through this cache — home's placements and list through `HomeState.appInfo`, the APPS
  order stores through the installed set their sync prunes against, the derived APPS layouts directly — so a row
  that never disappears is an icon that never disappears, everywhere at once. `AppInfoDao.replaceAll` is one
  `@Transaction` for `HomeListItemDao.replaceAll`'s exact reason: a separate `clear()` is observable, so every
  refresh would blank the home screen and the drawer for a frame.
- **`LauncherAppsWrapper.packageChanges()`** is a `callbackFlow` over `LauncherApps.Callback`, registered with an
  explicit **main-Looper `Handler`** — the argumentless `registerCallback(callback)` builds a `Handler()` for the
  *calling* thread, which throws when that thread has no Looper, and this flow is collected on `ApplicationScope`
  (`Dispatchers.Default`), which never has one. The throw dies in the scope's `CoroutineExceptionHandler`, so the
  symptom is not a crash but a listener that was never registered and an uninstall that updates nothing. It
  reports **which packages** moved and never what happened to them — "what is installed now?" has one answer and it
  is `queryActivities`, so adds and removes here would be a second source of truth about it, while *which* package
  moved is a question no re-read can answer (see the baked icons below). The
  repository collects it on `ApplicationScope` from its `init`, which is the exception the "coroutines run on
  `viewModelScope`" rule reserves: a cache that must mirror the device cannot stop mirroring it because the screen
  watching it went away. One collector rather than a flow handed to both ViewModels, so there is one answer to
  "when should the cache be re-read?".
- **An empty query is treated as a failed read**, not as an empty device: every device has at least this launcher
  installed, so nothing back means a profile mid-unlock or a binder hiccup, and replacing with it would empty every
  surface at once. That risk is the price of the mirror being authoritative, and it is the one thing an upsert
  could never do.

**And the baked icons go with it** — `BakedIconInvalidator`, the other half of making an update visible. The two are
separate because they go stale for different reasons: the *cache* goes stale when the set of installed apps changes
and a re-read fixes it, while a *baked icon* goes stale when an app replaces its own artwork, which changes nothing
the cache can see (same component, same label, same row). That is why `packageChanges` reports **which packages**
and never what happened to them: "what is installed now?" has one answer and it is `queryActivities`, but *which*
package moved is a question no re-read can answer. Two consequences worth knowing:
- **`IconId` cannot capture it, which is what `IconRenderManager.generation` is for.** That key was built so
  invalidation is automatic — any change *we* make produces a different key — and the app's own artwork is the one
  input it cannot see. `LauncherIcon` folds the generation into its bake keys, because evicting alone would not
  redraw anything: the three existing keys are unchanged by an update, so the stale bitmap would sit there until
  something else happened to recompose that cell. A bump that evicted nothing is skipped, so a first install does
  not recompose every icon on screen to discover there was nothing to do.
- **It lives in `data:apps`, not in `core:icon` and not in the Activity.** `core:icon` bakes icons and must not
  learn about package events; and `MainActivity`'s KDoc already names icon-cache invalidation inside `setContent`
  as one of the L1 mistakes it exists to avoid. It has no methods — constructing it starts it — so Koin builds it
  eagerly.

**What is deliberately *not* pruned is the layout.** A placement, a folder membership or a list ordinal for an app
that has gone stays in Room: it renders as nothing (every join is a `mapNotNull` through the cache), the planner
sees a free cell, and reinstalling puts the app back where it was. That is the same position `reconcileReportedOrder`
takes — an app the UI could not resolve must not be deleted on that evidence — and it is what makes an app briefly
unavailable (SD card, locked work profile) survive rather than lose its place.

**B4 `core:designsystem` — well along.** Theme + `Morphic*` components, plus the interaction primitives, all
validated in the `app/dev` harness (`DevRootScreen`): the drag toolkit (`DragCoordinator`, `FreeGridPlanner`,
MovingGap, `launcherItemGestures`, `DropFootprint`/`FloatingDragIcon`), `LauncherPager`, `SurfacePager`
(HOME↔side-surface pan; per-edge one/two-finger `OneFingerSwipe` policy — `ALWAYS`/`AT_EDGE`/`NEVER`, with
`AT_EDGE`'s nested-scroll hand-off wired — see the surface-swipe rules below), and the
grid (**grid plan G1–G5 + extras**, see [docs/GRID_LAYOUT_PLAN.md](docs/GRID_LAYOUT_PLAN.md)): `LauncherGrid`
(FIXED_PAGER + SCROLL_GRID sizing), the `coordinateItems`/`flowItems` placement-strategy DSL, `animatePlacement`,
and the extracted `GridGeometry` seam. Only grid **G6** (full-harness regression gate) is unticked — treat as
done-on-device. **Built for the home since:** the shared **coordinate drag surfaces** — `CoordinateDragGrid`
(single zone) + `CoordinateDragPager` (paged, viewport zone + edge-flip) + the shared `LauncherDragCell`
(per-item drag wiring), all reused by the harness `GridSurface`; the `AppCell`/`FolderCell` launcher cells (via
`IconLabelCell` + `cellLabelHeight`, with `FolderCell`'s 2×2 tile extracted as `IconPreviewPlate` once the APPS
category card needed the same tile for its overflow cluster); and the full **app-collection subsystem**
(`core:designsystem/collection/`) — `AppCollectionOverlay`, `appCollectionInnerSize` (a `@Composable` facade over pure
sizing arithmetic), the `AppCollectionReorder` MovingGap, `AppCollectionDragDelegate`, and
**`AppCollectionHostState`/`AppCollectionPhase`** (the open/leave/enter lifecycle any collection-hosting surface
reuses — **generic in the collection id**, `Long` for a folder and `String` for an APPS category; 21 unit tests). Item
gestures are scoped to the icon+label group, not the cell — see the design-system rules above.

**B8 `data:layout` — first cut done.** Geometry engine (`FreeGridPlanner`/`GridReflow`/`GridOccupancy`/
`FreePush`) plus the command + persistence layer: `LayoutChange` (L1's 19 ops → 13), `LayoutRepository` (slim
~30 → 6: one `placements` flow keyed by `GridItem` with `HomeZone` in the value, four definition flows, one
`apply` sink), and a complete Room-backed `LayoutRepositoryImpl` (five placement tables + folder / icon-container
/ widget-container / widget definitions; `apply` exhaustive over all 13 ops; twelve DAOs bundled in `LayoutDaos`).
`CreateFolder`/`AddToFolder` also delete the folded apps' grid placements (an app lives in one place); folder
delete cascades its membership + placement rows. The APPS pager/category/list **order** stores get their *own*
repository (not built). Deferred: cross-orientation rotate-seeding (empty-folder auto-dissolve now done, in the
home layer).

**Home surface — two *pairings*, chosen in one place.** `HomeScreen` is a `when` over `HomeLayout` above shared
wiring (the ViewModel, the device report, the state), which is deliberately `AppsScreen`'s shape and the same
argument: both arrangements render the same apps with the same launch behavior, so "which pairing?" is answered
once, above everything both need. The arms are **`HomePagerSurface`** (`PAGER_WITH_DOCK`) and **`HomeListSurface`**
(`LIST_WITH_WIDGET_AREA`); the unbuilt-arm-behind-`else` trap is avoided by listing both, so a third value fails to
compile until it is drawn. L1 answered this with a `HomeSurfaceRegistry` of `HomeSurface` implementations, each
declaring a `HomeGestureSet` (`hasPager`/`hasDock`/`hasWidgetArea`/`dropPolicy`/`longPress`) its shell interpreted —
indirection that made the set of layouts *open* while every consumer still branched on which one it had, so
`isVerticalListHome` appears nine times in its home screen alone, each occurrence re-deciding which store to read.
- **`HomeZoneScaffold` is the shared half**, and the only one: a `Column` under a strip, a `Row` beside a rail, the
  side zone first or last as `SideZoneEdge.isLeading` says, the main area taking the remainder, `uiInsets` on the
  pair and each zone's own horizontal margin on *its own* modifier (which is what keeps drag geometry correct for
  free — both drag surfaces publish bounds from an `onGloballyPositioned` placed after the caller's modifier). L1's
  `Shell(dockLandscape, dockThickness, dockAtStart)` did the same job with two booleans.
- **The two surfaces share nothing else, and that is not an omission**: a coordinate surface asks "which cell, and
  who gets shoved aside?" where an ordered one asks "which index?", so there is no planner, no store and no gesture
  in common.
- **`HomeLayout.mainSlot`/`sideSlot`/`sideZone`** (`core:model/HomeLayoutGrids.kt`) are what make every *other*
  consumer layout-agnostic — the ViewModel, both settings sections and the icon-sizing block ask for "the main slot"
  rather than naming `HOME_MAIN`/`HOME_DOCK`. Three exhaustive one-liners, so a third pairing cannot be added
  without saying what it is made of.
- **`HomeState.main` is a sum type** (`HomeMainSizing.Pager` / `.List`), because the two pairings configure a
  *different quantity* rather than the same one differently: a pager divides the space it is given (counts), a list
  is one lane with nothing to divide (a row height). Neither could supply the other's value. `DockSizing` became
  `SideZoneSizing`, one type for both zones, since a dock and a widget area are the same *kind* of thing.

**`PAGER_WITH_DOCK` — real-sized, two zones (pager + dock), with a full folder subsystem.**
Its own module (`feature:home`, `inkspire.morphic.feature.home`); plain-MVVM `HomeViewModel` (screen-scoped
`ViewModel`, optimistic placement state, logic out of the UI) joins `LayoutRepository.placements` + `AppRepository`
apps + `LayoutRepository.folders`. The main area renders on the paged `CoordinateDragPager` at the **real blueprint
size** (`HomePagerGrid.toGridConfig(device)` — device-aware, `cellMultiplier = 2`, apps are 2×2 logical footprints
snapped to the visual lattice; device detected in the UI, fed to the VM for seeding). Portrait only. Hosted as
the default `DevRootScreen` screen.
- **Launch** on tap → `AppLauncher` (`data:apps`, a one-method command separate from `AppRepository`'s reads;
  resolves the per-profile user from `ComponentKey.userSerial`, unlike L1's hardcoded personal user).
- **Drag-to-rearrange** persists through `LayoutRepository.apply` (optimistic → no flicker; `FreeGridPlanner`
  push with directional-push + merge-ring partition, dwelled preview, edge-flip pages, trailing empty page mid-drag).
  Seed leaves a free row so a full grid stays rearrangeable.
- **Folders.** Dropping an app on another (center merge ring) creates a folder; folders render as a `FolderCell`
  (2×2 icon preview). Tapping opens `AppCollectionOverlay` — two zones (a full-screen `SurfaceBackdropLayer` that **fades in**,
  plus a transparent bounded card sized live by `appCollectionInnerSize` per device/orientation, inset to
  `systemBars ∪ displayCutout`), a **dense-flow pager** of the folder's ordered apps, launch on tap, in-folder
  **MovingGap reorder** (persist `ReorderFolder`), and a border outlining the inner zone while dragging. The frost is
  its own node *under* the card rather than the card's parent, so the two can be given different entrances later; the
  card's alpha and the frost's share one driver today. Going through the shared layer is also what fixed a real fault —
  calling `wallpaperBackdrop` directly meant liquid glass tried to draw its refraction rim across the whole screen.
- **A folder is a place one drag passes *through*, not a destination it commits to.** This is the whole model, and the
  rest of the folder rules fall out of it. **Enter**: hold a dragged app on any folder's merge ring (~1s) and it opens
  mid-drag with the drag carrying on inside, so the app lands at a *chosen* slot. **Leave**: hold outside the card
  (~1s) and the folder **closes**, with the same drag carrying on over the grids beneath. Both directions are
  **repeatable, in any order, over any number of folders — including re-entering one already visited**, because
  *neither half writes anything*: membership is decided **only at the drop**. It is one continuous gesture on a
  **single shared `DragCoordinator`** (home + dock + folder zones on it, planner/drop dispatch by zone, the folder
  publishes an `AppCollectionDragDelegate`). The dwells are **equal by design** (`LeaveDwellMs` == `OPEN_COLLECTION_DWELL_MS`):
  opposite halves of one gesture, so a user who learned one hold has learned both.
  - **Leaving must genuinely close the folder, not hide it.** An earlier cut kept an `AppCollectionPhase.Extracting` whose
    folder was still "open" (faded to alpha 0) and latched an `extracting` flag until the drag ended. That made every
    folder a one-shot: the folder you left could never be re-opened, and re-presenting it rendered nothing and
    registered no drop zone. Nothing in the overlay may be scoped to the **drag** when it belongs to a **visit** —
    the arming flag below is the live example.
  - **Leaving is armed only after the finger has been over the inner grid, per visit.** A folder that opens mid-drag
    does so under wherever its cell sat, which near a screen edge is already the outer zone — un-armed, the same held
    finger that put the app in would instantly eject it (and with a per-*drag* flag, re-entry would eject instantly
    too). A drag that starts inside is armed on its first frame, so one rule covers arriving, leaving, and returning.
  - A drag out of a folder can land on a **merge ring** as well as an empty cell (`mergeExtractedApp`) — so a *quick*
    folder→folder move needs no dwell at all; dropping on the folder it came from is a **no-op** (still a member,
    nothing written). Every landing shares one `leaveFolderChanges` (remove + auto-dissolve), so the paths can't
    disagree about what leaving a folder means. Removing the second-last app **auto-dissolves** it (last app inherits
    its cell).
  - **Releasing outside the open folder cancels** (close it, write nothing). Leaving is a deliberate dwell, so a
    release out there reads as "never mind" — and it *cannot* be honored anyway: an app being carried inside a folder
    has no grid placement, so "placing" it would leave it in the folder **and** on the grid.
- **What the drag owes is fixed at lift: `AppCollectionHostState.dragSourceCollectionId`.** The folder a drag *started in* (null
  if it started on a grid), captured at `onDragStart` and held until release, whatever it visits in between. It answers
  two questions at once, and they are the same folder for the same reason — the gesture began on one of its cells:
  that overlay must **stay composed** (an in-flight pointer stream **cannot** be handed to another node — see the rule
  in `launcherItemGestures`; a root-level pointer overlay was tried and rejected because it swallows item events), and
  it is the one **owed a removal** wherever the app lands. It is deliberately *not* an `AppCollectionPhase` field: the phase
  names whichever folder is *on screen*, which after one hand-off is no longer the one owed anything.
  **Capturing it at lift rather than at the first hand-off is what makes re-entry work** — an app carried *in* from a
  grid and back out owes that folder nothing, so nothing pins it and nothing bars re-opening it. `AppCollectionOverlay`'s
  `presenting = false` is the pointer-holder role: invisible, no back handler, no delegate, no drop zone, no proxy —
  and reversible, since a drag can come back.
  **Three traps if you touch this:** both overlays must be emitted from **one keyed call site** (a second call site is
  a different composition position, so Compose disposes the folder and kills the drag it was preserving); an app with
  no placement must be resolved through `HomeState.appInfo` (**placed apps *and* folder contents**) or it is
  unrenderable — both as a folder's `incoming` and as home's floating **proxy**, which home takes back the moment a
  folder closes; and the folder drop zone is **one shared `ZoneId`**, so only the *presenting* overlay may register or
  unregister it (an unguarded `onDispose` on the holder tears the zone out from under the folder on screen).
  The whole open/leave/enter lifecycle lives in `AppCollectionHostState` (`core:designsystem`), not the screen; home
  supplies only the surface-specific *"which folder does this merge plan target?"* lambda (a **zone + placement**
  match — placement alone matches the other zone's folder at the same cell) and the commit calls. Two guards worth
  knowing: `reconcileReportedOrder` (`ReportedOrder.kt`) folds a UI-reported order
  back onto real membership, because `ReorderFolder` replaces membership wholesale and the UI can only report
  members it could render — writing its list verbatim **deleted** anything unresolvable (an uninstalled app,
  B6 pruning still deferred); and `AppCollectionOverlay` is wrapped in `key(folderId)` so switching folders doesn't
  inherit the previous one's pager position or reorder gap.
- **Dock — a second coordinate zone, a peer of the main area.** A single non-paged `CoordinateDragGrid`
  (`DockGrid.toGridConfig(device)`) below the pager, registered as a second zone (`DockZoneId`) on the **same shared
  `DragCoordinator`**. Because one coordinator hit-tests every zone in one space, **pager↔dock drag is not a feature
  at all** — it is one gesture whose drop reports the zone it landed in, and `homeZoneOf(zoneId)` turns that into the
  `HomeZone` the `Move` writes. It needs **no new op and no schema change**: `LayoutChange.Move` already carries
  `zone`, and the `*_placement` tables key on *item + orientation* (not zone), so the same row is re-stamped.
  Everything else is zone-generic rather than duplicated: one `planCoordinateDrop` serves both grids (differing only
  in geometry/config/occupants/page — deliberately **not** L1's `resolveDockDrop`, a drifted near-copy of its home
  resolver), one `handleDrop`, one `HomeItemCell`. The dock takes apps, folders and (later) widgets, merges into
  folders, and hosts folders that open, reorder, and hand apps in and out like any other — the folder hand-offs are
  continuous across it (closing a folder drops its zone, so the drag lands on whichever grid is beneath).
  **The dock starts empty** — an app lives in exactly one place, so seeding it would carve apps out of the main
  area, and *which* apps belong there is a default worth a picker; fill it by dragging.
- **An item carries its zone; a placement alone is not a location.** Each zone is its own coordinate space, so dock
  cell (0,0) and main cell (0,0) are the same `GridPlacement` value in different places. `HomeItem.zone` (mirroring
  `PlacedItem`) is what disambiguates them, and the zone is part of *identifying* a target, not just of writing the
  result — every per-zone derived list (grid contents, planner occupants, page count) must be built from one zone's
  items alone. Two bugs came from getting this wrong, both worth not repeating: matching a merge target on placement
  alone resolved a folder in the wrong zone, and scoping the *open-folder* lookup to MAIN left a tapped dock folder
  with its id set on the host and nothing to render (opening is zone-independent; merge *targets* are per-zone).
- **Where a side zone sits is `SideZoneEdge`, and it is a rule rather than a setting.** Four values, because HOME's
  two pairings put their zone on opposite ends: a **dock** is a bottom strip or a trailing rail, a **widget area** is
  a top strip or a leading rail — one is the thing you reach for, the other the thing you look at.
  `DeviceConfiguration.sideZoneEdge(layout)` is the whole rule, and `isStrip`/`isLeading`/`opposite` are what every
  consumer reads instead of re-deriving it. This was `DockEdge` with two values while the dock was the only side zone;
  L1 could not express it at all and passed a `landscape` boolean *and* a `dockAtStart` boolean side by side. The dock
  half of the rule is unchanged: a **bottom strip** on phone portrait,
  tablet portrait and tablet landscape; a **rail on the trailing edge** on phone landscape — the one posture that is
  short and wide, where a bottom dock would take a third of the height for one row of icons. `END` rather than `RIGHT`
  because a `Row` places it last, which is the right side in LTR and the left in RTL. It keys on the whole
  `DeviceConfiguration` and not on `isLandscape`: a tablet in landscape has the height to spare.
  One fact with four consequences, which is why it is a type rather than a `landscape` boolean threaded separately
  through each (L1 passed one to `homeGridArea`, `HomeGridEditor`, `DockGridEditor` and its extent slider, and each
  re-derived what it meant): home stacks its zones in a `Column` or a `Row`, the dock's extent is a **height** or a
  **width**, the extent bounds the **rows** or the **columns**, and the grid editor draws its companion zone below or
  beside. The blueprint's phone-landscape default is the transpose for the same reason — `1 × 4` where the others are
  `4 × 1`. `GridArea.splitForDock` is the one expression that divides the window, returning **both** halves together
  (like `DerivedCell`) because three callers depend on them agreeing and cannot check each other: the surface draws
  them, and both settings sections bound their grids against them.
  - **Rotation must not re-fit what it is not drawing.** Placements are keyed by `Orientation` and home reads
    `PORTRAIT` only (home orientation is unbuilt), so `fitMainTo`/`fitDockTo` are gated on
    `HomeViewModel.drawsStoredPlacements`. Without it, rotating a phone would settle a portrait arrangement against a
    landscape grid — and against the **rail**, which is the transpose, so nearly every dock item would be evicted to
    home and would not come back. A grid drawn out of bounds for as long as a rotation lasts is cosmetic and reverses
    itself; the write does not. The guard becomes vacuous the day placements are stored per posture.
- **`cellMultiplier` is a *placement* subdivision, and the snap has to honor it or it buys nothing.** HOME's three
  free-placement grids (pager, dock, widget area) declare `cellMultiplier = 2`: a 4×5 grid of visible cells really is
  8×10 logical ones, and an app is a 2×2 logical footprint. The user is never shown that — they see 4×5 cells with
  one icon each — and what the subdivision buys is that an icon can come to rest **straddling** two visible cells,
  because its top-left may be any *logical* cell. The offsets between the cells are reachable, which is the only
  reason to subdivide.
  `planCoordinateDrop` passed `step = cellMultiplier` to `GridGeometry.snapTopLeftCell`, rounding the top-left back
  onto the visual lattice — so a grid declared at 2 behaved in every observable way like one declared at 1, and the
  subdivision cost twice the occupancy bookkeeping for nothing. The `step` parameter is now **gone rather than
  defaulted**: its only ever use was that mistake, and a parameter is an invitation to repeat it. L1 resolves the
  hovered cell at logical granularity and centers the footprint on it, which is what this now does.
  - **The lattice is shown while dragging** (`gridSnapMarkers`, `core:designsystem/grid`), which is the half that
    makes the freedom legible — L1's `GridLinesCanvas`, ported: a concave-diamond marker at every **visual** cell
    corner, fading in around the dragged footprint's *edges* and out again as it leaves. Visual corners rather than
    every logical intersection deliberately: they are the grid the user thinks in and the reference a half-cell
    offset is read *against*, where marking every logical cell would double the dots and put half of them in the
    middle of a cell. A `drawBehind` taking lambdas, so a drag re-runs the draw phase and nothing else.
- **Layout: the dock has an extent of its own, the pager takes the rest, and there is no padding anywhere.** The dock's
  extent is a **setting** (`SurfaceMetrics.extentDp[HOME_DOCK]`, defaulting to `DockGrid.extentDp`) *and* its rows and
  columns are stored counts — the extent does not replace a count, it **bounds** the one it divides, since a cell is
  `extent ÷ count`. It is an *extent* and not a height because `SideZoneEdge` decides which dimension it names; one
  value per device serves both, since a user configuring a phone in landscape is configuring the rail.
  `CellFit.fitGridConfig` resolves a stored size against what an area can actually hold, from the two
  inputs only the surface knows (the measured width, and the type scale behind a label row) — and it needs **no branch**
  for the rail, because the extent is simply in the area's width there and each axis is fitted to the dimension it is
  given. Deriving the counts from
  the extent was specified and abandoned on both axes: rows read as an editor missing half its buttons, and at the
  default icon guardrail a derived phone dock is eleven columns wide against the four it has today. **The count on the
  axis the extent does *not* divide is clamped on read and never written back** (shrink the icons again and it
  returns); **the one it does divide *is* reduced in storage**, at the moment an extent commit invalidates it — the
  asymmetry is that the extent was a deliberate change,
  where an icon-size change is not about the dock at all. L1 wrote every clamp back from a `LaunchedEffect` *inside
  its dock settings screen*, which destroyed the preference and only ran while that screen was open. Home carries
  a **horizontal margin per zone** (S4g, defaulting to 0 — so an unconfigured launcher still runs edge to edge). The applied
  inset is `uiInsets` (`systemBars ∪ displayCutout`), one value feeding both the padding and the width
  the dock is fitted against — L1 kept two derivations of that area (`homeGridArea` from settings,
  `pagerBoundsInWindow` measured) and they could disagree.
- **A dock shrink spills onto home; it does not delete.** Fewer rows means cells that may hold items, and the strip is
  a *single page*, so there is no next page to push them to. `HomeViewModel.fitDockTo(config)` runs
  `GridReflow.reflow(…, Overflow.EVICT)` — which reports what a single-page grid cannot keep instead of inventing a
  page 1 nothing draws — and hands the evictions to `GridReflow.admit(…)`, which finds each a cell on the pager.
  L1's `DockGridEdit` **dropped** them (`droppedApps`/`droppedFolders`/`droppedWidgets`, deleted by the caller).
  It is a **command on the ViewModel, called when the config changes**, and idempotent, so no caller needs a "did it
  shrink?" test; L1 instead reflowed *inside the `combine` that assembles its home state* and launched the persist
  from within that transform, so the write was a side effect of reading state and raced itself.
- **The dock's extent is subtracted from home, so growing it can invalidate home's counts too** — the same invalidation
  one grid over, on whichever axis the subtraction happened (a bottom strip takes home's height, a rail takes its
  width), and it is answered in the two halves this codebase already splits such things into. The **surface**
  fits the pager to what is left (`CellFit.fitGridConfig` over `splitForSideZone`'s main half, the same expression the
  Home settings section computes its bounds from) and re-homes the displaced items to a further page
  (`HomeViewModel.fitMainTo`, the pager's `fitDockTo`); the **count itself is written down** only by the dock
  section's extent commit, which is the deliberate change that caused it. That is the dock's own asymmetry applied
  outward: a count invalidated by a committed extent is written, one invalidated by an icon-size change is clamped on
  read and returns. L1 did the clamp from the home *surface* (`LaunchedEffect` on its measured pager bounds — its
  comment names "the dock is turned back on") and wrote it on **every** cause, so an icon tweak permanently destroyed
  a row count that had nothing to do with it. L1 also measured home's area two ways (`pagerBoundsInWindow` on the
  surface, `homeGridArea` in settings) which could disagree; L2 has one expression. Note it takes a *large* dock or
  *large* icons to bite at all: home's smallest usable cell is ≈52dp at the default guardrails, so even a 320dp dock
  leaves room for more rows than the five it stores. **Neither zone settles until the store has answered for it** —
  a blueprint fallback is a smaller grid than one the user has grown, and settling against it would make a transient
  first frame a permanent write.

**`LIST_WITH_WIDGET_AREA` — a widget area at the leading edge, a hand-ordered list of apps filling the rest.**
`HomeListSurface`, the second arm of `HomeScreen`'s `when`, and structurally the dock pairing's mirror: the same
`HomeZoneScaffold`, the same "extent bounds the count it divides" rule, the same settings sections. What differs is
what each zone *holds*, and every difference below follows from that rather than being a choice.
- **The widget area is the dock in every way but its contents**, which is one field: `WidgetAreaGrid.icon` is
  **null**, because a widget is not an icon in a cell. That null is load-bearing three times over — `CellFit` fits it
  by `WidgetMinCell` (48dp square, L1's `MIN_WIDGET_DP`) rather than by inverted icon guardrails, the ViewModel skips
  it when resolving icon sizing (`SettingsRepository.iconSizing` rightly throws for it), and its settings section
  draws no icon group at all. That last one is exactly the shape L1's `DockSettingsDetail` took in its `minimalist`
  branch, returning early before `IconLayoutControls`. Defaults are L1's `WidgetAreaSettings`: 280dp, 4×3 portrait,
  3×4 landscape — far thicker than a dock's 96dp, which is the difference between the two zones stated as a number.
- **It renders empty today, and that is a missing feature rather than a missing surface.** Widgets are unbuilt
  (`GridItem.Widget` has no cell), so nothing can be placed in it — but the zone is real: measured, fitted, and
  registered as a `CoordinateDragGrid` drop target that **accepts widgets only**. That is L1's `areaReject` rule
  expressed as `DropZone.accepts` instead of as a check at drop time, so an app carried over it falls through to the
  list beneath rather than being rejected on release.
- **Two things the widget area is still owed, both gated on widgets existing.** A shrink does not re-home what no
  longer fits (`settleDock` evicts to a *coordinate* main area, and this one sits beside a list, which has nowhere to
  put a widget) — deliberately not L1's answer, which deleted them. And nothing seeds it.
- **The list is ordered, and dragging it reorders it — nothing else.** No merge ring (a list of apps holds no folders,
  as L1's does not), no page to carry an item onto, no coordinate to write: a drop is an index, committed through
  `HomeViewModel.reorderList`. The preview is **MovingGap**, the same model the APPS pager and every folder use;
  `OrderedFlow` gained `cellFractionY` for it, because a list flows *down*, so "insert before or after?" is the top
  half of a row rather than the left half of a cell.
- **A row is a `LauncherDragCell`, and taking the shared cell rather than its parts is what made the drag feel
  right.** The first cut hand-rolled three of its four jobs — `launcherItemGestures`, the `alpha = 0` on the lifted
  row, the drop wiring — and silently dropped the fourth, `animatePlacement`. A `Column` places children in order, so
  reordering them *moves* each row: with the modifier the rows glide as the gap migrates, and without it they jumped
  between positions. The lifted row must also **drop** the modifier (which the shared cell does), or it flies back
  from its old slot on release instead of landing where it was put.
- **Three things about the commit, each of which read as "the drop failed" when it was wrong.**
  - **The gap is cleared when the drag ends, never by the code that reads it** — a `LaunchedEffect(isDragging)`, the
    APPS pager's shape. An earlier cut reset it at the top of `handleDrop` and then built the committed order from it
    two lines later, so every drop wrote `movingGapDisplayOrder(order, app, 0)` and sent the app to the top.
  - **The list leads its store**, exactly as `placements` does one field over, and here it is not optional: the
    MovingGap preview lives on the *drag*, so it is gone the instant the finger lifts. Rendering the stored order
    across the write meant the dropped row visibly returned to where it started and jumped onward a frame or two
    later. `HomeViewModel.listOrder` + `listWritesInFlight` closes that window; the store's echo afterwards is also
    the correction, since `setOrder` reconciles what the UI could render against real membership.
  - **`HomeListItemDao.replaceAll` is one `@Transaction` for a reason that is invisible until it is removed.** As a
    `clear()` then an `upsert()` the clear is *observable*: the DAO's flow re-runs on that invalidation and emits an
    **empty** list, so the surface blanks mid-reorder. It is the one transaction in `data:layout`, and it is not the
    general fix its siblings still need — those should take the database and use `withTransaction`, together.
- **The drag proxy keeps the list's left edge and follows the finger in y only.** Every other surface centers its
  proxy on the finger because a proxy is one cell there — roughly square, smaller than the finger's travel. A row is
  the full width of the list, so centering it swings the whole row sideways with the thumb.
- **A `Column` in a `verticalScroll`, deliberately not a `LazyColumn`** — the opposite call from `AppsVerticalList`,
  for two reasons that are properties of this list rather than preferences. A lazy list disposes rows that scroll
  away, and the lifted row **owns the pointer stream driving the drag**, so auto-scrolling far enough would kill the
  gesture (this is what the APPS pager needs `keepAllPagesPlaced` for). And a home list is the handful of apps the
  user chose where the drawer is every app installed, so composing it whole costs nothing. It also makes the drag
  geometry the documented one: the viewport's `onGloballyPositioned` sits **outside** the scroller and
  `viewportTop - scrollState.value` is the content origin, republished every frame — plus the re-send after
  auto-scroll, in one `SideEffect`, since the coordinator only re-plans when the *finger* moves.
  The cost of the scroller is that **lifting a row needs a still finger**: movement during the long-press scrolls
  instead, since the scroll gesture is the parent's. That is ordinary for a reorderable list — L1 sidestepped it with
  its library's drag *handle* — and it is the thing to change if the lift ever feels finicky, rather than a symptom
  of anything above.
- **Not built**: the "Add apps" row (a picker), which is L1 behavior. Without one its contents are whatever the seed
  put there — and it is the reason the list's own menu verb is *Remove* rather than a pair: an app can be taken off
  the list but there is still no way to put one back. That menu is the shared item menu with one contribution, and
  **the contribution is neither `RemoveFromGrid` nor a reorder**: this list is an order store of its own, so removing
  is a *membership* write (`HomeListRepository.remove`). Writing the order without the app looks equivalent and is
  not — `setOrder` reconciles a reported order against real membership, so the app is treated as one the surface
  could not render and is appended straight back at the end. That guard is right, which is exactly why removal needs
  an op that says what it means rather than one that hopes to be inferred.

**APPS surface — one module for every layout; the vertical list is the first.** `feature:apps`
(`inkspire.morphic.feature.apps`) is the whole surface: L1's `feature:appdrawer` + `feature:applibrary` were
deleted, not ported, because they rendered the same apps with the same launch behavior and differed only in
arrangement — the model had already collapsed that into `Surface.APPS` + `AppsLayout`, and two modules made
"drawer or library?" a question that had to be answered before, and separately from, "which layout?".
- **`AppsScreen` is the one place a layout is chosen** — a `when` over `AppsLayout` above shared wiring (ViewModel,
  ordering, theme, background), so no layout can disagree with another about what the app list *is*. The unbuilt
  arms are listed individually rather than behind an `else`, so a new `AppsLayout` value fails to compile until it
  is rendered. `layout` is a **parameter with a default** — the real choice is per-binding user preference and
  belongs to `data:settings` (B7).
- **One `AppsViewModel` per surface, not per layout** (switching layout must not reload anything), exposing one
  `AppsState`. It has **no write path at all**: the list is a *derived* layout, so its order is a function of the
  app cache and nothing else. Ordering is a locale-aware `Collator` + a component tie-break, deliberately not L1's
  `sortedBy { label.lowercase() }` — that compares raw UTF-16, so every accented label sorts after `Z` and a
  Vietnamese or French list breaks into two alphabets.
- **Both *derived* layouts are built** — the vertical list and the vertical grid. They came first together because
  neither stores anything: each re-renders straight from the app cache, so between them the surface is proven end
  to end (repository → ordering → cells → launch) without the APPS **order** repository, which is what every
  remaining layout is blocked on.
  - **`AppsVerticalList`** — a `LazyColumn` of `AppRowCell` (`core:designsystem/cell`, the horizontal sibling of
    `AppCell`); **row height a flat placeholder**, icon sized from the list's own `IconMetrics`
    (which fills the row — a list's label sits *beside* the icon, so there is no label row to leave height for).
  - **`AppsVerticalGrid`** — a `LazyVerticalGrid` of `AppCell`; **columns from the `AppsScrollGrid` blueprint**
    (`colsFor(device)`, since a `SCROLL_GRID` blueprint has no rows and so can't go through `toGridConfig`), cell
    height a flat placeholder. It is **not** `LauncherGrid`'s SCROLL_GRID mode:
    that composes every child at once, which is right for the bounded per-category page it was built for and wrong
    for hundreds of icon-baking cells. This is the grid plan's *right tool per surface* rule, and it costs nothing
    because a derived layout is never dragged **within** itself — so it needs no shared lattice and no published
    `GridGeometry` (a drag *out* is `EjectToHome`, which reads the finger).
- Cells go through the shared `launcherItemGestures` contract rather than a `clickable`, so APPS cannot drift from
  the rest of the launcher on long-press timing or slop — exactly what L1 did, hand-rolling a recognizer (plus a
  click-suppression flag) inside its list composable. The tap-only wiring lives in one `appsItemGestures` shared by
  both layouts, so a layout can't half-wire it and there was one file to change when the menu and `EjectToHome`
  landed — both now have. **A row's touch target is its icon and its label, not the strip they sit in** — the same "visible extent"
  rule as a grid cell, and `AppRowCell` applies it the same way, by hanging the gestures on a wrap-content group.
  This reverses an earlier reading ("a row's visible extent *is* the full-width strip, so a list leaves no slack"),
  which conflated the row's **footprint** with what is drawn in it: a row paints no background, so the width past
  the end of a short label is exactly the slack a grid cell has around its icon, lying on the other axis. So a list
  *does* leave room for a surface long-press. The bill is that a tap out there launches nothing either — one
  contract covers both — which is already true of the slack around a grid icon. **Both lists keep the slack free**,
  including this one — and the surface press is now what it is free *for*, with the alphabet strip and search still
  below: a
  target narrowed once is worth more than one narrowed again later, when users have learned the wider one.
- **The pager is the first layout that stores an arrangement** (`AppsLayout.PAGER`, `AppsPager`) — pages of
  `LauncherGrid` in FIXED_PAGER mode at `AppsPagerGrid`'s size, drawn from `AppsOrderRepository` rather than
  re-derived. Page capacity is a UI read (device → blueprint), pushed to the VM via `setPagerGrid`, exactly as
  home pushes its `GridConfig`. `AppsState` gained `pagerPages: List<List<AppsItem>>` alongside `apps`: both
  shapes are always maintained so switching layout reloads nothing, and one collector keeps the store in step
  with what is installed (first run, install/uninstall and a capacity change are all the same sync).
- **The pager drags by MovingGap, not push** — an ordered surface migrates a gap and lets the flow densify, where
  a coordinate one shoves occupants aside. Crossing pages reuses home's shape exactly: one drop zone is the whole
  viewport, page-swipe is gated off while an item is in flight, an edge dwell flips the page, and
  `keepAllPagesPlaced` keeps the source page composed so the lifted cell keeps its pointer stream. Two consequences
  worth knowing: the gap is an index **within one page** (pages are hard boundaries, so arriving on a page seeds a
  fresh gap rather than continuing the old), and the dragged cell **stays composed on its source page** even after
  the finger carries it elsewhere — both copies are invisible, the far one only reserving the gap, because
  disposing the near one would kill the drag.
- **Folders on the pager work exactly as they do on home**, because the lifecycle is the same `AppCollectionHostState`:
  tap to open, dwell on a merge ring to enter mid-drag, dwell outside the card to leave, drag an app back out onto
  a page or straight into another folder, auto-dissolve at the second-last app. The surface supplies only the one
  answer the host can't know — *"which folder does this merge plan target?"* — as a **slot** match, which is the
  ordered half of the split that lambda was built for. A merge plan's footprint is meaningful (it names the hovered
  cell) where a reorder plan's is not, which is what makes the slot resolvable. Cells are **three zones** here
  (outer thirds insert, center merges), unlike the folder's and the category pager's halves.
- The pager's op set composes rather than special-cases: `RemoveFromFolder` takes an app out of membership and
  *nothing else*, so a landing pairs it with a `Move` (onto a page) or an `AddToFolder` (into another folder), and
  one batch commits both. `CreateFolder`/`DissolveFolder` name a **neighbor** instead of a slot — "this takes that
  thing's place" — because a slot index shifts as the folded apps leave the list. `reconcileReportedOrder` moved to
  `data:layout`, beside the whole-order ops it guards, now that several surfaces owe their writes that guard.
- Still to come on the pager: a page indicator, and an optimistic layer (a drop currently waits for the write).
- **The category pager** (`PAGER_WITH_CATEGORY`, `AppsCategoryPager`) — **one page per category**, each page a
  header plus a vertically-scrolling grid at `AppsCategoryGrid`'s column count. It uses `LauncherGrid`'s
  **SCROLL_GRID** mode where the vertical grid deliberately uses `LazyVerticalGrid`: same *right tool per surface*
  rule, opposite answer, because a category page is tens of apps and the vertical grid is all of them. It asks the
  VM for **nothing** — no capacity to report, since a category is one list — which is the difference between the two
  stores showing up in a signature. Empty categories keep their page, so one emptied by dragging can be refilled.
  Classification comes from `data:apps`' `AppCategorizer` (curated asset → platform category → keyword heuristics),
  run in the VM off the main thread and handed to `syncCategories` as `component → category id`.
- **Dragging on the category pager re-files by page.** Reorder within a page and move to another are one `Move` op:
  a page *is* a category, so the destination id carries the difference and there is no separate "change category"
  path. Cells split into **halves** (no merge ring — nothing to merge into). Two things differ from the APPS pager's
  drag and both come from the page scrolling: **geometry is published per page from its grid**, not once from the
  viewport, because the grid slides under the viewport and a finger→cell read against viewport bounds would name the
  cell that *used* to be at that height; and **the page's scroll is gated off mid-drag** the way page-swipe already
  is, since two vertical gestures otherwise fight over one finger. Content past the fold is reached by holding the
  dragged app near the top or bottom edge, which scrolls it — `DragAutoScrollEffect` (`core:designsystem/drag`), the
  vertical counterpart of `EdgeFlipEffect`, with speed ramping by how deep into the band the finger is so the
  boundary doesn't feel like a trapdoor.
- **The rule the category store lives by: a filed app keeps its category even when the classifier disagrees.**
  Classification runs on every launch, so treating it as authoritative would silently undo every drag at startup —
  an assignment is only ever a *first* answer, for apps with no answer yet. That also makes a user override free: it
  is simply the row already being there. Pages are the `CategoryGroup`s; the fine `AppCategory` taxonomy is how
  classification *reasons*, not what is displayed — which is what lets a new fine category be added without adding a
  page. **Rebalanced from 6 groups to 12** after the 6 proved lopsided on a real device (`INTERNET` and `UTILITIES`
  each absorbed 9 fine categories, so two pages held most apps and "Internet" was where you looked for your bank).
  The widest is now `UTILITIES` at 5, deliberately: it and `SYSTEM` are where the unclassifiable goes, and a user
  expects those to be broad.
- **A category that no longer exists cannot hold apps** — the bound on the rule above. `dropUnknownCategories`
  unfiles anything under an unrecognized id and deletes its definition row, so the classifier can place those apps
  again. Without it a rebalance strands them: the read is driven by the definitions table, so they would render
  nowhere while still occupying a row that `syncCategoryItems` counts as filed. It is the same path a user-deleted
  category will take once `known` grows to include user-created ids (L1's `u1`/`u2` prefix is the pointer).
- **The category card** (`CATEGORY_CARD`, `AppsCategoryCard`) — **the fifth and last APPS layout**: a lazy 2-column
  grid of square cards, one per category, each a header plus a 2×2 preview, tapped open to reach the rest. It shares
  the pager's store, so there is nothing to seed or classify here and switching between the two layouts shows the
  same categories with the same apps. Lazy (unlike the category *pager*'s `LauncherGrid` pages) because a card
  composes up to seven baked icons, so the card *count* is small but the icon count is not — the vertical grid's
  argument, not the pager page's.
  - **The expansion is a real `AppCollectionOverlay`, not a lookalike.** That type's parameters were already a label and
    a list of apps with no folder id in them, because what it renders is *an ordered collection of apps opened over a
    surface* — and the grid it sizes itself from is `FolderGrid`, whose KDoc has always called itself the "folder /
    category-card grid". Reuse brings the paging, dots, MovingGap reorder and scrim for free. This is what **named**
    the type: it was `FolderOverlay` in a `folder/` package, and the second case is what proved the name described a
    *case* rather than the thing. No `AppCollectionHostState` here: that machine exists for the phases an app passes
    through while its *membership* changes mid-drag, and a tap-opened expansion changes none.
  - **`AppsCategoryChange.Reorder` is the second category op, and it changes order only.** The expansion reports a
    whole list (it is the same overlay a folder uses), which a `Move` can't express without guessing which app the
    user dragged. Its guard sits **in the store**, not at the call site as `ReorderFolder`'s does — and that
    asymmetry is the point: a folder's membership reaches the UI intact (it *is* the folder definition), so the
    caller has something true to reconcile against, while a category's never does (the UI only sees what the app
    cache resolved). So the op is made incapable of changing membership instead. `reconcileFolderOrder` was renamed
    `reconcileReportedOrder` for the same reason.
  - **Two tap targets per card, with no overlap** — the "touch target is its visible extent" rule applied to a
    container: preview icons launch; the **header row** and the **overflow cluster** open the category. The empty
    slots and the padding stay free. The header is not a fallback for the cluster — a category of ≤ 4 apps has no
    cluster, and without it could never be opened. The cluster tile *is* `IconPreviewPlate`, extracted from
    `FolderCell` when this second consumer arrived so a folder tile and a cluster tile can't drift apart.
  - **Dragging between categories is the folder↔home gesture, on cards** — the same `AppCollectionHostState`, so the rules
    are that machine's and not this layout's: dwell on a card (~1s) to expand it mid-drag and place the app at a
    *chosen* slot, dwell outside an expansion to close it with the drag carrying on over the cards, drop straight on a
    card to append with no dwell, repeatable in any order including re-entry, membership decided only at the drop.
    Reaching a card past the fold is `DragAutoScrollEffect` (widened to `ScrollableState` so a lazy grid can use it);
    manual scroll is gated off mid-drag as on every other dragging surface.
    Three things differ from home, all of them properties of the surface rather than choices:
    - **A drag starts inside an expansion, never on the card grid.** Home has loose apps to pick up; here every app is
      filed in exactly one category, so nothing sits *on* the surface — expansion→card *is* the whole gesture, the
      analog of home's folder→folder. Preview icons stay tap-only (a folder's preview tile isn't draggable per-icon
      on home either), which is also what lets the grid stay **lazy**: nothing on it owns a live pointer stream, so a
      card disposed by auto-scrolling can't kill the drag. Making previews draggable would pin the source card in
      composition, which a lazy grid cannot honor.
    - **There is no "empty cell" landing.** Off a card there is nowhere for an app to be, so the planner reports *no
      plan* and a release there is a cancel — which is why `MERGE` is the only intent this surface ever reports.
    - **Landing owes the source category nothing.** `Move` unfiles the app from every other category as part of filing
      it, so one op is the whole re-file, where the pager pairs `RemoveFromFolder` with a `Move`. Dropping back on the
      category the app came from is a no-op, as on home.
    Hit-testing is a **per-card bounds map**, not a `GridGeometry`: cards are lazy, square and separated by spacing
    that belongs to no cell, so there is no lattice to compute from. Entries are added as cards lay out and removed as
    they scroll away.
  - **`AppCollectionHostState`/`AppCollectionPhase` became generic in the collection id** (`Long` for a folder,
    `String` for a category) so this surface could *use* the open/leave/enter machine instead of growing a near-copy of
    it — the L1 `resolveDockDrop` mistake this codebase keeps un-making. Nothing in the lifecycle reads an id beyond
    comparing it, which is what made the parameter free; a unit test pins that with `String` ids. **The naming caught
    up in its own mechanical commit**: `core:designsystem/folder/` is `collection/`, and `Folder{Overlay, HostState,
    Phase, DragDelegate, ReorderPlan}` / `folderInnerSize` are `AppCollection*` / `appCollectionInnerSize` — the
    vocabulary the KDoc had already settled on. Deliberately **not** `IconCollection*`, which would read as a sibling
    of `IconContainer` (a grid item holding icons) when the two are unrelated. What stayed `Folder` is everything that
    really is one: the Room tables and ops, `GridItem.Folder`, `FolderCell`, the Folders settings section, and
    `GridSlot.FOLDER`/`FolderGrid` — that last one because the slot name is a **stored settings key**, so renaming it
    would reset every per-folder icon and grid override.
  - Still to come: an optimistic layer (a drop waits for the write, so a re-file lands a frame or two late — the
    `Injected` phase is what stops the app blinking out meanwhile).
- Not built: the alphabet filter strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing
  helpers into the list file — three concerns in one composable), search, drag-out-to-home (`EjectToHome`), and
  category **management** (create/rename/delete/reorder — a `feature:settings` concern, which is also why a card
  carries no menu and cannot be dragged).

**The icon effects expansion is complete — every slice of
[docs/ICON_EFFECTS_PLAN.md](docs/ICON_EFFECTS_PLAN.md), all thirteen effects — and phase 2 has started.** That
second list is **§8** of the same plan: six more effects and one architectural item, assessed against the built code
rather than against captures. **Four of the six are re-pointing what already exists** — `ColorMatrices.duotone` *is* a
gradient map, `LayerGradient.radial` places a vignette, `IconRenderer.haloed` is an inner shadow inverted, and
`dilated` is an outline's outward half — so the only genuinely new kernel is **bevel & emboss** (a Sobel over a
blurred alpha, which does not fit `resample` because that helper samples one point where a Sobel reads a
neighbourhood). The one primitive missing is an **alpha-inverting matrix**, which three of the six want. The
architectural item was proposed as "extract the falloff onto the base effect"; it is **neither an extraction nor a
falloff** — Bloom's falloff is the light's own geometry, so nothing moves, and what is being asked for is a per-effect
**mask**, which is ~20 lines in `applyEffects` (run the effect into a buffer, composite it back through a ramp's
alpha) plus a restructure of each live-drawable effect. It goes **last**, because its cost multiplies by the number of
effects. **Built so far: `LayerEffect.Duotone`, `LayerEffect.Vignette`, `LayerEffect.InnerShadow` and
`LayerEffect.InnerGlow`, `LayerEffect.Outline` and `LayerEffect.Bevel`** — **all six are built** (see their notes
above). What is left of §8 is the **effect mask**, which was always last because its cost multiplies by the number
of effects. **One plan claim is already overturned** — the
"missing alpha-inverting matrix" was never needed: destination-out over a filled buffer *is* the inversion, and
outline's erosion is that same op twice. One number worth watching: four of the six do not draw live, taking the total to ten
of nineteen, and `drawsLive` is all-or-nothing per icon — so most recipes worth making will preview from the bake, and
the live path narrows to the plain ones.

Thirteen effects were drawn from captures of another icon studio, and the plan's whole finding is that
**only the *live* path has API restrictions**: the bake owns a software bitmap, so a blur is a `BlurMaskFilter` and
a displacement is arithmetic over an `IntArray` at every API level. Gating six effects to API 31/33 was considered
and rejected — it would deny glow and drop shadow to every device below Android 12 to solve a problem only the
*editor* has. Instead the studio will preview **from the bake**, downscaled and throttled during a gesture, which is
why `LayerEffect.drawsLive` exists now with nothing yet answering it `false`.

**The bake-backed preview is built (slice 8), so the remaining six are unblocked.** `IconPreview` is the studio's one
entry point: it draws through `IconLayerStack` where the live path can manage it and from `IconRenderer` where it
cannot. Every studio preview goes through it — canvas, layer tiles, composite tile — because a call site that could
forget to ask is one that silently shows a lie. Five things worth knowing:
- **The whole icon falls back, never one layer**, which reverses what the plan's §2 assumed. A per-layer fallback is a
  *hybrid stack* — one layer from a bitmap, the rest live around it — so the two halves must agree about geometry at a
  seam **inside one icon**, which is the two-renderer hazard in its worst form: no longer two whole pictures that can
  be held against each other. It also costs nothing, the bake rendering a whole set either way and its expense being
  the effect rather than the layer count. `IconLayerSet.drawsLive` is the one question asked.
- **`IconLayerSpec.drawsLive` keeps a real job** and is not made vestigial by that: a *layer tile* in the rail solos
  one layer, so it falls back on that layer's own effects. One property, two scopes, each asking about what it draws.
- **Draft first, then full — the throttle *and* the resolution split in one mechanism — and the draft is never
  abandoned.** Every recipe is baked downscaled immediately and then, once nothing newer has arrived for `SettleMs`,
  at full size. **Only the full-size pass is cancelled by a newer recipe**, and that asymmetry is a correction: the
  bake used to live in a `LaunchedEffect` keyed on the recipe, so anything newer killed whatever was running.
  Conflating by cancellation works only while the work is shorter than the gap between two emissions, and nothing
  checked that — a slider thumb emits per pointer event, **about 7ms apart on a 144Hz phone against a measured 15ms
  draft**, so for any effect heavy enough to reach this path *no draft ever finished and the preview did not move at
  all until the finger lifted*. It was reported as the preview freezing on a drag while the +/- buttons worked, which
  sounds like two bugs and is one: a discrete step leaves a gap long enough for a draft to land. Letting the draft
  finish and then taking the newest recipe gives the property actually wanted — as fast as the machine can draft,
  never slower and never not at all — and still coalesces, since everything emitted mid-draft collapses into one
  value. **The plan's gesture-in-flight signal turned out to be unnecessary** — "nothing newer has arrived" *is* what
  settled means, where `onCommit` is a proxy any non-slider edit would answer differently.
- **Deliberately not `IconRenderManager`.** Its cache is keyed on the resolved layer set, which is exactly what changes
  every frame of a drag — a preview going through it would evict every real icon on the device within seconds. The
  editor wants one slot and has one. Its coalescing and concurrency cap are moot here too: there is one bake in flight
  by construction.
- **`IconRenderer.render` gained a `customImage` lambda**, defaulting to the disk read it always did. That default is
  right for every surface and **wrong for the studio**, whose whole point is that a freshly picked image previews
  before anything is written (see `CustomIconStore`) — so the editor passes the same lambda it gives the live path and
  the two draw the same picture. `IconRenderManager`'s call had to name `packImage` as a result: a trailing lambda now
  binds to the new parameter, silently and only for pack layers.
- **A layer tile drops the whole-icon effects**, which it did not before the composite had any. A tile answers *which
  layer is this?*, and a grain belonging to the icon obscures exactly that at 48dp — it would also drag every tile onto
  the baked path for something none of them show. The composite tile is where those are seen.

What is **not** built is the *"working"* hint for a bake that runs genuinely long: it wants measuring against the
heaviest effect on the slowest device to hand, and the draft is what makes its absence survivable since something
always lands quickly. **The draft is one number, `DraftPx`, and it was three that contradicted each other** — a
fraction of the settled bake, a floor and a cap, where the floor asked for 144 and the cap pulled it straight back to
128, so the floor was dead code and the paragraph explaining it was false. It is 144 rather than as small as possible
because a draft can be too small to be *true*: roughly the smallest bitmap a surface bakes, so the draft can represent
anything a surface can. Measured on a Snapdragon-class phone, one grain bake: **~15ms at 128px, ~19ms at 144, 332ms at
768**.

Built so far: the ordered effect **pipeline** (slice 0), the paged effect **panel** with per-effect switches and
`SliderControl` (slice 1), the **filter** library, now 46 looks in seven categories (slice 2), the **layer rail** that replaced the
`LAYERS` tool entry (slice 3), slice 4 — **`LayerEffect.Bloom`** and **`LayerEffect.Gloss`**, plus **whole-icon
effects**, which that plan had not noticed it needed (six of the thirteen are only correct over the composite) — and
slice 5, **perspective**, which turned out to need the live path to give up its `graphicsLayer`, slice 6, **Pattern**,
with an asset library of its own, and slice 7, **Extrude** + **Chromatic split**. That is all of tier 1; everything
remaining — Glow, Drop shadow, Pixelate, Ripple, Grain, Progressive blur — waits on the **bake-backed preview**, which
Extrude has already given a second reason to build.

**Also still open: icon packs (S8)** — the last piece of the icon studio proper. A pack is one more `LayerSource`
variant rather than a mode, so "apply a pack to everything" is setting the global default's fg/bg source and goes
through the same commit, cache key and invalidation as any other edit. What has to be built with it is pack
*detection* (theme-intent actions), `appfilter.xml` parsing, and — for browse and search — a drawable lister, which
L1 never finished either.

**The effects sequence below is done and is kept as the record of how S5f was split.** S5f split
into three when it was costed, because as one slice it was `BackdropEffect` + the params slice + `Blur.kt` + a 350-line
`Modifier.Node` + an AGSL shader + a settings section + three consumers:
- **S5f-1 — the brightness signal. Done.** The shell's hardcoded `darkTheme` is gone; see the design-system notes. It
  turned out to need *neither* `Blur.kt` half, which is why it was worth taking first — it is the one reading of the
  wallpaper that is a system call rather than image processing.
- **S5f-2 — the frosted backdrop. Done.** `BackdropEffect` (which B0 had already built, unconsumed) became a
  `data:settings` slice, `Blur.kt`'s box blur landed in `data:wallpaper` beside the cropping and scaling it belongs
  with, `wallpaperBackdrop` + `BackdropState` + `LocalBackdrop` landed in `core:designsystem/backdrop`, and the folder
  overlay's opaque black sheet became the first frosted surface. See the design-system notes above for the four
  departures from L1's version, and the wallpaper notes for how the sampled image is chosen.
- **S5f-3 — liquid glass and the effects section. Done.** The AGSL shader (see the design-system notes) and the
  seventh settings section, which is also the slice's first writer. **S5 is now complete.**
- **S5f-4 — the full-screen frost. Done, and it was not in the plan.** APPS and the folder went transparent behind one
  shared `SurfaceBackdropLayer`, which is what the "APPS stays opaque" note had been promising since the window learned
  to show the wallpaper. It reshaped the model on the way through (`None` → `Plain`; every effect blurs), fixed a
  full-screen refraction rim in the folder, and left the effect *sliders* dormant — see the design-system and effects
  notes for all four. What it is still waiting on is a frosted **panel**, which is where the sliders and the rim both
  come back.

**Widgets are built: the picker, the host, the cell.** `data:widgets` holds two types with deliberately different
jobs — `WidgetCatalog` answers "what *could* be added?" from `AppWidgetManager` with no host at all (a live read,
not a cache, for `AppShortcuts`' reason), and `AppWidgetHostController` owns the one `AppWidgetHost` per process.
The distinction runs all the way through: `WidgetProvider` has no id and `BoundWidget` does.
- **An allocated `appWidgetId` is a resource, not a value.** It outlives the process, so a widget whose id is
  allocated but never placed is a leak the user can neither see nor clear. Every path that abandons the add gives
  it back, which is why `WidgetAddFlow`'s callback returns a **Boolean** — the caller says whether it *kept* the
  widget. Removing one is likewise two halves that must both happen (`HomeViewModel.removeWidget`): the layout
  rows, and the id.
- **`LayoutChange.PlaceWidget` writes the definition *and* the placement.** A widget cannot be `Move`d onto the
  grid the way an app can: an app is a component the cache already knows, while a widget's provider and label live
  in a row only this op writes, so a bare `Move` would leave a placement resolving to nothing.
- **The add flow decides nothing about placement.** L1's controller held the home state, both grid configs, four
  cell sizes and the surface kind so it could place the widget itself — fourteen mutable fields reassigned every
  composition. Here it owns only the activity-result choreography (silent bind → system dialog if refused → the
  provider's configuration screen if it has one) and hands back a `BoundWidget`.
- **`startListening` is scoped to the launcher being on screen**, from `LauncherShell` — a provider only pushes to
  a listening host, so a clock stops ticking without it. Not `MainActivity`, which also hosts settings.
- **A widget's footprint is the item's, not the grid's** — and assuming otherwise was one bug with three faces.
  `planCoordinateDrop` hardcoded one visual cell, so a big widget got a 1×1 shadow *and* a `Move` that resized it
  on drop; the drop shadow separately derived its own size from a cell count rather than from the plan that had
  already resolved one. Both now read the item's own span, and the shadow reads `plan.footprint`.
- **The drag proxy is a *snapshot*.** Every other dragged thing is a cell the launcher can re-draw; a widget's
  content is another app's `RemoteViews`, and a second `AppWidgetHostView` for one id would be a second live
  instance. `AppWidgetHostController.snapshot` draws the on-screen view into a bitmap — L1's `captureBitmap`, for
  exactly this.
- **A widget's tap is not ours to suppress, so the view is *told* the gesture was taken.** `AppWidgetHostView`
  receives the same touches we do and fires its own click on `ACTION_UP`, which is why long-pressing a widget and
  releasing used to trigger it. The gesture machine cannot help — it only decides whether *we* open the item — and
  consuming the release does not either, because the interop layer hands the up to the view on the Initial pass,
  before a Main-pass node could consume. `WidgetCell` sends a synthetic **`ACTION_CANCEL`** instead, driven off
  two signals it already has (`LocalMenuHost.request`, the coordinator's `isDragging`) rather than a second
  long-press timer to keep in step with ours. AOSP's Launcher3 does the same from a `CheckLongPressHelper`.
  `ItemGesturePhase.ownsFinger` came out of the same investigation and is a separate, smaller correction: the item
  now consumes once it owns the finger rather than only while swiping or dragging.
- **And a widget's *swipe* is not ours to take, which is what `EmbeddedViewTouchFrame` is for** — the fix for a list
  widget that could not be scrolled at all, because the surface pan claimed the finger first every time. It is
  structural rather than a missed check: `surfacePagerGesture` runs on `PointerEventPass.Initial`, which runs parents
  **before** children, so the hosted view is always behind it — and while that view can still be intercepted Compose's
  `PointerInteropFilter` hands it moves on the **`Final`** pass, two passes *after* the pan has decided on the same
  event. `AndroidView` does wire the platform's own `requestDisallowInterceptTouchEvent` through
  (`AndroidViewHolder` → the filter), but all that changes is *when* the filter dispatches; it cannot outrank an
  ancestor on Initial. Worth knowing that the *other* two gestures were already correct for the same reason inverted:
  the filter sets `suppressMovementConsumption` while the view has merely handled the down, so `LauncherPager` still
  pages over a clickable widget and stops only once the widget genuinely claims. The pan was the one gesture the
  signal could not reach.
  - **So the claim is made in reverse, and the pan learned to wait.** The frame takes a `SurfaceGestureLock` claim at
    the **down** — on the chance the view wants the gesture — and hands it back once the view has had *its own* touch
    slop's worth of movement without asking to keep it. `surfacePagerGesture` now treats a claim at slop as *"not
    yet"*, deciding on the first event at which nothing is claiming, where it used to hand the swipe back for the
    whole gesture. Both halves are needed: a pan that broke off would make every interactive widget a dead zone for
    surface switching, which is the cheaper fix and was rejected for that reason. The bill is **one event of latency**
    for a pan that starts on a widget, and nothing else — no other claimant ever releases mid-gesture, so none of them
    can observe the difference.
  - Two details that are silent when wrong. `super.dispatchTouchEvent` must run **before** the verdict, since a child
    claims from inside its own touch handling and so arrives on the very event being judged. And a view that does not
    handle the **down** receives nothing further (the filter records that return value and stops dispatching), so it
    can never claim and the frame must answer for it there — otherwise the claim outlives the gesture and locks the
    swipe for the session.
- **Still to come**: resize, widget containers, and the widget area's shrink eviction (it cannot evict to a list).

**Next after that: HOME's second pairing is built, so widgets are the thing it is waiting on.** `LIST_WITH_WIDGET_AREA`
renders, is configurable, and reorders — and its widget area is an empty, correctly-sized, correctly-refusing drop
zone until `GridItem.Widget` has a cell. Two things become owed the moment it does: re-homing what a shrink evicts
(the widget area's `settleDock`, which cannot evict to a list), and seeding it.

Also open: on the surface swipe, **the five transitions past SLIDE** are all that is left of L1's `CrossPager` — the
nested-scroll hand-off, the frosted backdrop and `EjectToHome`, the other three things tangled into it, have all
landed. The home long-press → options menu now exists and is what the free cell space was being kept for; what it is
missing is verbs, each waiting on its own feature (an **app picker**, **widgets**, **page management**). The vertical
list's **"Add apps" picker** is the same gap seen from its own surface — without one, its contents are whatever the
grid seeded. Home **orientation**, or
widgets/containers on the grid. On APPS, **all five layouts render, all the
arrangement-owning ones drag, and every one of them can drag an app out onto HOME**; what is left is the surrounding
behavior: the alphabet filter strip, search, an optimistic layer for both the pager and the card (a drop waits for
the write) + the pager's page indicator. The queued **mechanical** job is done: `core:designsystem/folder/` is now
`collection/` and its types are `AppCollection*` (see the card's notes). Folder
follow-ups: rename, add-via-picker, cross-page reorder, onto-an-app open-then-create.

**P9 is flipped: this is a launcher.** `app`'s manifest declares `category.HOME` + `category.DEFAULT`, which is what
puts Morphic in the system's home-app chooser and lets the home button resolve to it. Four attributes come with the
role and each answers something an ordinary activity never faces — `launchMode="singleTask"` (home must *return* to
the running instance, not stack a second), `stateNotNeeded` (the system kills and restarts a home app freely, and its
state is its stores rather than a `Bundle`), `resumeWhilePausing` (home may resume while the app being left is still
pausing, which is what makes the button feel immediate), and `configChanges` covering everything (Compose re-reads
the window; recreating on a rotate would tear down the drag, the open folder and every baked icon). **One filter
where L1 had two** — its second `MAIN`+`LAUNCHER` filter is redundant, since an intent matches a filter whose
categories are a superset of its own. Permissions stay where L2 puts them, with the module that needs them:
`QUERY_ALL_PACKAGES` is still not requested anywhere, `data:apps` requests **`REQUEST_DELETE_PACKAGES`** (see the
menu notes — without it Uninstall silently does nothing, and L1 only got away without it because
`QUERY_ALL_PACKAGES` covered it), `data:wallpaper` requests **`READ_MEDIA_IMAGES`** (plus the pre-33 `READ_EXTERNAL_STORAGE`
capped at 32) because watching the image collection is the only way a screenshot can be noticed — those two were
declared with the capture flow and then **deleted by the commit that added the rotating service**, which rewrote that
manifest to make room for it, and nothing failed loudly: `RequestPermission` on an undeclared permission is refused
immediately and without a dialog, and the capture screen treats a refusal as a cancel, so Start bounced back and read as
a dead button — and the wallpaper section's live-wallpaper shelf got the narrow `<queries>` it
had been silently missing (without it that query is filtered to this app's own services, so
the shelf renders permanently empty — being the home app exempts `LauncherApps` from visibility filtering, not
`PackageManager.queryIntentServices`). One consequence worth knowing: **shortcuts only exist once we are the active
home app** — `hasShortcutHostPermission()` is false otherwise, so before the flip the menu's first stage would have
been empty for every app on every device.

**Settings — `data:settings` (B7) is real, and seven sections are live.** Storage is **one `@Serializable` JSON blob per
slice** under one DataStore key (`SettingsSlice`, pure and unit-tested), not L1's ~265 flat keys behind a 693-line codec;
per-slice flows, not one god flow; and a slice carries no version because `ignoreUnknownKeys` + fully-defaulted fields
make additive change safe both ways, with the **key name** as the seam for a semantic break. Four slices exist:
`SurfaceRegister` (HOME's layout, per-edge `SideBinding`, transition), `SurfaceMetrics` (per-grid **icon** and **grid**
overrides, plus **`extentDp`** and **`rowHeightDp`**), `AppsChrome`, and `SurfacePaging` (per-pager infinite scroll —
see the surface-swipe rules above for why it is not in `SurfaceMetrics`). **`extentDp` and `rowHeightDp`** are **slot-keyed**, which reverses an earlier call
worth keeping: each was a bare `Map<DeviceConfiguration, Int>` named for the one grid that could answer it
(`dockHeightDp`, `listRowHeightDp`), on the grounds that a slot-keyed map would make most keys meaningless. HOME's
second pairing changed the arithmetic — a widget area is a fixed-extent strip too, and a home list declares a row
height too — so there are now *two* grids per question and a second named map would have been the same pair of
functions twice. The unrepresentable stays unrepresentable one layer out: `SettingsRepository.extent`/`rowHeight`
refuse a slot whose blueprint declares neither, exactly as `updateGrid` refuses one with no `editRange`, because the
blueprint is where "does this grid have one" is declared. **The bill is a storage break**: the old keys are dropped by
`ignoreUnknownKeys`, so a stored dock extent or APPS list row height resets to its blueprint default once. Acceptable
only because nothing has shipped — the key name is this slice's seam for a semantic break, and this is one. Overrides are **sparse and doubly so** — keyed `GridSlot` × `DeviceConfiguration`, nullable per field, and an
emptied entry is *removed*, which is what keeps "a default lives in exactly one place" literally true (the blueprint) and
makes "reset" a plain write of nulls. Reads are **resolved in the repository** (`iconSizing`/`gridConfig`/`gridCols`), so
no surface sees the keying. `GridSlot` names the launcher's ten grids and lives **on** `GridBlueprint` so the two
cannot drift; `GridBlueprints` proves the mapping total. `feature:settings` is **one destination whose sections are panes**, ported from L1's shell: a section list beside a
detail on a tablet, sliding between the two on a phone, with `SettingsSection` as ordinary state inside
`SettingsScreen`. An earlier cut gave each section its own `NavKey`; that was reversed because a pane which shares the
screen with another is not a destination. L1's *actual* mistake is still avoided — it declared that enum in the
**navigation module**, so `feature:home` could import `SettingsSection.WALLPAPER`; ours never leaves the feature.
**The surface register is a cross, because the setting is spatial** — L1's `SurfaceRegister`, ported: HOME in the
middle, the four edges around it, five screen-shaped cards in a plus. Which edge opens what is a fact about *where
things are*, and the four labeled chip groups this replaced made a reader rebuild that arrangement in their head. It
also reverses this section's own earlier reasoning, which is worth keeping: chips beat the segmented control because
"an edge offers six options", and what that missed is that the **edges** were the part with a shape, not the options —
so the options moved into a modal (`SideBindingPicker`, L1's dialog with its radio-and-plain-text body replaced by the
same mockups) and the edges got the picture.
- **A card names what is bound; it does not draw it** — L1's icon-and-label card, kept. A cut that filled each card
  with the layout's own mockup (reusing `AppsEditorPreview`, the drawing the grid editor already owns) was built and
  **reversed at the author's call**: at 88dp a mockup is a smudge, five at once turn a picker into a wall of texture,
  and what a reader scans this screen for is *which edge has something on it*, which a glyph and a name answer at once.
  The picture belongs where a layout is chosen or sized, not where four of them are placed. The picker is L1's radio
  list for the same reason.
- Every side shows the **same** glyph, because every binding is the same *surface* and the label carries which
  arrangement. L1 varied it because its two side surfaces were two modules; the surface taxonomy's collapse of
  drawer + library into APPS shows up right here.
- **The gear is L1's, and it lands on the layout, not just the section.** A card is two targets divided by a rule —
  the body *changes* what is bound, the gear *configures* what is bound already — and since the APPS section edits one
  layout at a time, the jump carries which one (`onOpenSection(section, layout)`; `SettingsScreen` holds it as a second
  saveable enum beside `selected`, because `SettingsSection` is the list's vocabulary and a payload one section can
  carry does not belong inside it). `AppsDetail` applies it in a `LaunchedEffect(Unit)` — once per *arrival* at the
  pane, not per distinct value, or gearing the same layout twice in a row would silently not re-select it.
  **No gear where there is nothing to open**: the gear is drawn only for a layout in `ConfigurableLayouts` — L1's
  `settingsSection` is nullable for exactly that reason. That list is **total today**, since the category card gained
  its own chip, so the branch never takes its `else`; it is kept because the condition states when a gear belongs
  rather than working around one layout, and an arrangement with no editable grid would otherwise land on a pane with
  no chip for it.
- The card is the shape of **this** device, from `usableWindowArea` rather than L1's `LocalConfiguration`, with L1's
  fixed long side (176dp) and the short side following the ratio — the same rule `GridEditor` sizes its mockup by, and
  for the same reason a fraction of the pane was rejected there.
- `AppsLayout.label` is now **one** vocabulary (`feature/settings/LayoutLabels.kt`). It existed twice with *different*
  strings — "Pages"/"Category pages"/"Category cards" against "Pager"/"Pager + category"/"Cards" — each promising in
  KDoc to move when a second screen needed it; the picker was the third.

**Two sections follow HOME's pairing, and one control switches it.** The Home section edits the pager's rows and
columns *or* the list's row height; the Dock section becomes the **Widget area** section — its title, its subtitle,
its extent range (L1's `120..480` against the dock's `80..320`), and whether it has an icon group at all. The list
rows and the app-bar title rename with them (`SettingsSection.meta(homeLayout)`, read once by the shell through
`SettingsShellViewModel` so the list and the bar cannot disagree). Three things carry that:
- **`HomeLayout.mainSlot`/`sideSlot` again** — both ViewModels resolve *the slot*, so one `IconSizingEdits`, one
  extent control and one `GridEditor` serve both pairings. `GridSizeState.main` is a sum type (`MainAreaSize.Grid` /
  `.Rows`) for `HomeMainSizing`'s reason, one layer down.
- **The switch is HOME's card in the surface register** (`HomeLayoutPicker`, `SideBindingPicker`'s twin), which
  reverses that section's "HOME is not a choice, so its card does not take a tap" — true only while there was one
  pairing. The card is already two targets (body changes what is there, gear configures it); the body was simply
  null. L1 put the same choice in its Home *section* as a scroll row of two mockup cards labeled "Classic" and
  "Minimalist"; those name eras of that launcher rather than what you get, and the register cross had already decided
  not to draw mockups at card size. The rule this section states — a control appears when the thing it configures
  exists — is unchanged: `transition` still has no control, because `SurfacePager` still implements only `SLIDE`. The
  **infinite-scroll switch** is that rule's newest application in the other direction: it appears on the pager pairing
  and on the two paging APPS chips, and nowhere else, because only those grids have the setting at all.
- **Switching is non-destructive**, which is what makes it one tap with no confirm: each pairing's zones have their
  own stored sizes and their own stored contents, so the one going off screen keeps everything it had.

**A section belongs to a surface and holds everything about it**, layout controls *and* icon sizing, exactly as each
of L1's five details embedded `IconLayoutControls` under its layout section. **All five surfaces now have theirs** —
Home, Dock, Apps and Folders — and the standalone icon-sizing screen is **gone**, because the folder section took the
last grid out of it and a heading with nothing under it is not a section. `IconSizingControls` shares the UI and
`IconSizingEdits` the write commands, so a section costs neither; a section with one fixed grid supplies a constant slot
where APPS supplies its chip's.
The **folder section** is the smallest and is L1's shape exactly: its `FolderSettingsDetail` has an *empty* layout group
and its icon controls are the whole screen, because `FolderGrid` declares no `editRange` — a folder's card is sized to
the screen, so its rows and columns follow rather than being picked. It states the resolved page size as a fact instead,
and states that it also governs the **category card's expansion**, which is the same `AppCollectionOverlay` on the same grid.
**The name `Icons` has returned with the icon studio** (per-app: shape, background, layers), which is what L1's
`Icons` section actually is — not grid sizing, which L1 never kept there either. It is the **eighth** section and
the third in Personalization, and unlike every other one it is a **hub rather than an editor**: two actions and a
Presets placeholder, with the editing in a full-screen destination. See the icon-feature section for why.
**The wallpaper section is the sixth, and the first that is not about a surface** — which is why the list is now two
named groups, Personalization and Layout, as L1 had it. It is a full port of L1's `WallpaperTab` layout: a **two-page
pager of *modes*** ("Single wallpaper" / "Wallpaper rotate") over **three browse shelves** ("My wallpapers", "Backdrops
(By Unsplash)", and a `LazyRow` of installed live wallpapers queried from the package manager). Paging the modes rather
than stacking them is the part that carries meaning — only one of them is ever the wallpaper, and two headings do not
say that. One shared `WallpaperModePage` anatomy serves both (title + status line, apply control on the right, a
preview band, two tonal icon buttons), where L1 hand-wrote its two pages — the same reason one `GridEditor` serves home
and the dock. Three places it does not copy L1, and one earlier note that is now wrong:
- **One button and a chevron, not `SplitButtonLayout`** — both halves of L1's ran `expanded = true`, so the split was
  decoration over a single action (applying always asks *where*). The chevron stays; the seam does not.
- **Previews keep the screen's ratio inside L1's band.** The stored file is already cropped to this screen, so
  stretching it across a landscape band would show a crop the device never displays. `fitAspect` picks which axis to
  fill, which is also what lets the portrait and landscape rotate slots be one composable rather than two identical
  rectangles.
- **Our own `RotatingWallpaperService` is filtered out of the live-wallpaper shelf.** It genuinely *is* one, so the
  query returns it — but the rotate page already owns it, and the card would be the one route with no guard: that
  page's Apply stays disabled until an orientation exists, where a card would open the chooser for a wallpaper with
  nothing to draw. Excluded by the component the repository states, not by our package name.
- **Reversing an earlier call**: this used to say the shelves were absent (empty-state hints for sources that do not
  exist, plus a duplicate of the system chooser) and that the installed-live-wallpaper browser was not carried. Both
  are in, at the author's call — the shelves are where the future *sources* go, so they are the shape rather than the
  filler.

**The effects section is the seventh, and the second in Personalization** — L1's `EffectsTab`, and structurally the
same screen: a chooser, then the sliders belonging to whatever is chosen. It is also the first thing to *write*
`backdropEffect`, which S5f-2 left read-only on purpose. Five things worth knowing:
- **Every slider is live, and the last one to become so was Blur.** They were written against a frosted **panel** that
  did not exist yet, and kept rather than cut on the author's call; the context menu, the home bottom sheet and the
  container panel are that panel. Tint, and every liquid-glass parameter, worked from the moment those landed — they are
  draw-time reads of the stored effect. **Blur was the one that could not**, because the *picture* every surface sampled
  was blurred at the film's fixed strength: the slider moved a number nothing rendered. `ShellViewModel` now asks the
  repository for the wallpaper at **both** strengths and `BackdropRole` says which surface takes which, so the slider
  governs panels while the frost stays fixed. `Plain`'s slider lost its subtitle back when it had nothing to say; the
  control itself is now the honest one in the section.
  - **The frost is still not tunable, and that is the whole point of two pictures.** A surface arriving over HOME has to
    occlude it whatever decoration was picked, so `fullScreenFilm` replaces both parameters — and the layer names
    `BackdropRole.FILM` on the line below the one that names the fixed effect, because those are one decision made
    twice: the strength it renders at and the strength its picture was blurred at have to be the same number.
  - **A panel strength commit costs one decode, the panel's** — each picture has its own subscription keyed on its own
    strength, so the film's is baked once and again only when the displayed wallpaper changes. Keyed on the *pair*, as
    the first cut was, moving the slider restarted both and the film re-blurred at a strength that cannot move; the rule
    is to **de-duplicate where the value is owned**, since a key made of several things cannot say which of them changed.
    At a panel strength of **exactly zero** that picture is the whole
    screen — which is what "no blur" means, and the only strength that reaches full resolution. Everything the launcher
    actually blurs is halved first (`MIN_BLURRED_DOWNSCALE`): a blur wide enough to see has destroyed detail at its own
    radius, so the halving is free to the eye, and it is what keeps the four live buffers of a full-resolution blur —
    decode, pixels, scratch, result, 13MB each on a 1216×2688 screen — out of the first few percent of the slider, where
    the effect is least visible. The film's picture is an eighth of the screen and rounds to nothing beside it.
  - **The orientation reaches `WallpaperRepository.backdrop` as a *flow*, and that is a measured fix rather than a
    style.** Only the rotating pair is two files; for a picked or captured image the path is the same string whichever
    way the phone is held. As a value it was a subscription key, so every rotation restarted the collection, the
    "did the source change?" comparison had nothing to compare against, and the launcher re-decoded and re-blurred a
    picture identical to the one it held — two decodes per turn of the device, one of them nearly full-screen. Passed as
    a flow it reaches that comparison instead.
- **The sliders come from the sealed variant, not from a ten-field bag.** L1 held every parameter of every effect at
  once; here the `when` is over `BackdropEffect` itself, so the compiler checks the mapping is total. The bill: a
  write is a **whole-value** write, and switching *between* variants discards the previous one's parameters. Within a
  variant nothing is lost — flipping a blur's tone keeps its strength and tint, which is the comparison users make.
- **`BackdropOption` is the chip vocabulary, and it is not the stored enum coming back.** "Light blur" and "Dark blur"
  are two things to pick between but one model variant with a `tone`, so the split lives in the section and never
  reaches storage. Its first chip is `PLAIN`, not `NONE` — see the model note below.
- **Liquid glass is hidden, not disabled, below API 33**, with L1's sentence explaining why. An effect that silently
  comes out as a plain blur is worse than one that is not offered.
- **No live icon preview**, unlike every surface section. Those preview a *cell*, which a pane can draw on its own; an
  effect previews a frosted surface over the wallpaper, and the settings pane deliberately has no backdrop — that is
  the shell's, one zone over. Faking one would mean the second provider L1 ended up with. L1 had no preview here.

The `busy` flag is L2's own rather than a port, because L1's picker went to its crop screen and the work happened
behind that. "Choose image" is `PickVisualMedia` and opens the **crop screen** — `feature:settings`' own `NavKey`
mapped by `app`, a destination rather than a pane, because back out of it means "not that image".
**Every section has L1's live icon preview**, between its layout group and its icon group: a real `AppCell` (or
`AppRowCell`) at the **real cell size** that section computed, with the cell and both icon guardrails outlined over it,
tracking the sliders per frame (`onPreview`) rather than on release. It is what makes the icon controls legible — a
fraction and two dp bounds say nothing about *this* cell, and which of the three is binding is the whole question while
dragging. The geometry is **asked for, not copied** (`cellIconLayout` in `core:designsystem/cell`, so a guide cannot
drift from the cell it is drawn over — L1 restated the cell's padding under a "keep in sync" comment, and it publishes
the cell's **inner box** as well, since a guardrail larger than the cell must stop where the icon really can, not on the
outer ring); each section
supplies its own cell size, which is the part that cannot be shared (home divides its area, the dock divides its height
setting, APPS branches on layout, the folder asks `appCollectionInnerSize`); and the guardrails are **grayscale by stroke**
(solid = cell, dashed = upper, dotted = lower) because L1's green/red cannot survive a palette that reserves red for
`error`. **The wallpaper behind it is L1's trick and it has landed** — the cell box composites with `BlendMode.Src`,
punching through the pane (`PunchThroughPane` composites the detail offscreen with `withSaveLayer`) to the window,
which shows the wallpaper because `app`'s theme now carries `Theme.Wallpaper`.
**The four surface sections share one arrangement, `SurfaceDetail`, and it is L1's** (`IconDetailPortrait` /
`IconDetailLandscape`, which its five details all went through — an earlier note here said those scaffolds were "not
carried", and that was reversed). Three things about it are load-bearing rather than decorative:
- **The grid editor is first, then the sliders that constrain it** — the editor is the picture of the surface, so it is
  what a user arrives looking for, and the margin / height / row-height sliders under it are adjustments *to* that
  picture, each previewing live into it. L1 orders all five details this way (editor, then extent, then padding). Where a
  section chooses *what* it is configuring — the APPS chip row, L1's home-surface cards — that comes above the editor,
  because it decides which editor you are looking at.
- **The icon heading and the preview pin together** in a `stickyHeader`, so the preview stays on screen while the
  controls scroll under it. That is the whole reason these panes are a `LazyColumn` rather than a `Column` +
  `verticalScroll`, and it is what makes the icon controls legible at all: their three numbers are read *through* the
  preview. `IconSizingPreview` is the body only and `IconSizingControls` emits no heading — both belong to the pinned
  block now, so neither states it a scroll apart.
- **Landscape is a different arrangement, not a narrower one**: the layout group scrolls away, the heading pins, and the
  icon group fills the viewport as a final full-height item — controls scrolling on the left, preview fixed at a
  **fixed** 220dp on the right (L1's number, and fixed because the preview draws a cell at true size, so the column has
  to hold one rather than take a share of the pane). A phone in landscape has room for a cell beside its sliders and none
  above them.
What is *not* carried from L1's scaffolds is the machinery they wrapped around the punch — the offscreen layer, the pane
background, the insets and the disabled overscroll are all `PunchThroughPane`'s here, because L1 had no shared pane and
so repeated them in both. Overscroll being off is what makes a lazy list safe with the punch at all: a stretch
re-composites the scrolling content and the punch stops reaching the window for as long as it lasts.
**The APPS section is one section with a chip per layout** — the settings mirror of one `feature:apps` for five
layouts, and the same argument: they differ only in arrangement, so what a user configures is "the paged one" or "the
list". Selecting a chip writes nothing (which layout you *get* is per home edge, in the register). Its resize is **one
write** where home's is two, and that is the ordered/coordinate split reaching settings: every APPS grid is ordered or
derived, so the flow re-densifies and there is nothing to displace — the pager's store even re-paginates itself, since
a capacity change is the sync it already runs for installs. Only the edge's *axis* is read, as in L1's drawer editor.
The **list** is the odd one: one lane, so it has no grid to edit and its size *is* its row height
(`AppsListGrid.rowHeightDp`, the third way a cell gets a height — see the derive-vs-store rule in the design-system
notes). **Its slider's bounds are the icon guardrails plus the row's own inset** (`rowHeightRangeDp`), so the icon range
slider *governs* the row-height slider: a row shorter than `minIconDp` + padding cannot honor the smallest icon
allowed, and one taller than `maxIconDp` + padding is height the largest cannot fill. So the way to ask for a taller row
is to raise the upper guardrail. `iconPercent` is deliberately not in it — the same rule as the grid, and for the same
reason: dividing by it inverted the control, so asking for *smaller* icons pushed the row **taller** (a 56dp row clamped
up to 72dp at 50%). A stored height outside the range is clamped on read by `fitRowHeightDp` — in the list *and* in the
slider, so the control never sits at a height the surface isn't using — and never written back, so the user's number
returns when the guardrails widen. **With icons off, no guardrail applies and the range changes shape**: the floor
becomes the *label's* own height (a row cannot be shorter than its text — `rowLabelHeight`, the row twin of
`cellLabelHeight`, since a row styles its label from `bodyLarge` where a cell uses `labelSmall`) and the ceiling **opens
up** to `IconSizingRanges.IconDp`'s own ceiling. Both ends then stop following the guardrails, which is the point: they
describe an icon that isn't there, and bounding a pure-text row by one forbade a compact list to anyone who had set
chunky icons before switching them off. **Chrome is a third slice, `AppsChrome`** — `SearchPlacement` (which `core:model` had built and nothing consumed) plus
the tab bar's `VerticalEdge`, whose KDoc had named this consumer since B0. It is the one setting whose **only current
consumer is the editor preview**: search is unbuilt on the APPS surface and neither pager draws a tab bar, so this is a
deliberate exception to "no model in a vacuum" — a preview is a real consumer with a real question, and L1's editor
draws both from its own stored pair. Its search default is `Hidden` where L1's is `TOP`, because a default that drew a
search bar the launcher has not got is the one thing a preview must not do. **Which options are offered is
layout-dependent**, which is `SearchPlacement`'s whole shape: a standalone layout pins to an edge, the category pager
embeds in its header. L1's flat `SearchPosition` let a user pick a state their layout could not draw.
**The category card has a chip now, and it closed the one gap this section had.** It was held back because a card is a
*tile*: how narrow one may get is not an icon guardrail, its blueprint declares no icon sizing, and picking a ceiling
by hand was what the rest of this port had avoided. Two things resolved it. The card's settings **already existed** —
`AppsCardGrid` declares an `editRange` and per-device lane defaults, and `AppsScreen` was already reading both the lane
count and the margin — so the gap was a value the user could not reach rather than a feature nobody had built. And the
ceiling belongs where every other floor is stated: **`CellFit`**, as a `MinCell` beside `WidgetMinCell`, which
is the case that pair of overloads exists for.

**Then the card gained real settings, and that retired the constant.** Two flat numbers were picked by eye and both were
wrong — 96dp let a 393dp phone draw four lanes of unreadable dots, 120dp only looked right because it absorbed chrome it
could not see. The fix was to stop guessing: `AppsCardGrid` now declares `icon = IconSizing(showLabel = false)`, so
`CellFit.cardMinCell` *derives* the floor as `2 × minIconDp` + the card's two paddings + the gap between lanes — the same
inversion `minCellWidthDp` performs for an icon cell, applied to a tile holding four icons. Ask for larger icons and the
lane count comes down on its own. The one thing to keep straight is that a column fit divides the grid's **raw** width, so
the floor must describe a *lane* (card + spacing) and the callers must subtract the gutter first; getting that wrong is
exactly how both constants went wrong.

**The tile is the square, and the label is outside it** — iOS's App Library shape, reached in two corrections. The card
was originally the square with the title *inside* it, eating into it from the top: the leftover box came out wider than
tall, the slots sized themselves from its *height*, and the icons ended up the smallest thing on a tile whose whole job
is to make them recognizable. Making the icon area the square fixed the icons but left the title sharing the fill, which
reads as a header bar rather than a label. Now the background, corner and padding are all the **tile's** and the name
sits under it, centered — so the fill traces the icons exactly and a card is a square plus one line of text.

**`CardChrome` is the tile's own settings** (`core:model`, stored as a fifth `SurfaceMetrics` map keyed slot × device,
sparse like `IconOverride`): title scale, corner radius, and the icon area's **outer** and **inner** padding. All three dp
values **start at zero** — a card begins as a plain rectangle of edge-to-edge icons, and every bit of decoration is
something a user turned on. That is deliberate, after a version where the inset, gap and corner were hardcoded numbers no
control could reach. The outer padding insets the *icon area* only; the title keeps a fixed inset, because a title against
the corner reads as a rendering fault rather than a choice. The section therefore shows lanes, margin, an icon group with
**no text controls** (`APPS_CARD` joins `APPS_LIST` in that gate — the slots carry no labels), and the four chrome
sliders; still no rows (`minRows = null`) and no infinite scroll (declares no `wraps`). The expansion's sizing stays the
**Folders** section's, since an expansion *is* that overlay on `FolderGrid`.

**A card slot draws `CategoryPreviewIcon`, not an `AppCell`** — the icon alone, sized against the *whole* slot. A cell
wraps `IconLabelCell`, which insets by `CellPadH`/`CellPadV` and reserves a label row, so two adjacent slots kept an 8dp
gap however far the spacing slider was dragged down: a control unable to express the thing it is named for. A slot has no
label and no chrome; it *is* the icon's box, which is what `iconPercent = 1f` means literally here. The **overflow
cluster** is sized by the same expression (`CategoryClusterTile`): it stands in for one of the four apps, so given the
raw slot it stayed full-size while its neighbors shrank with the slider. It also draws **no backing plate**
(`IconPreviewPlate(backing = false)`, which drops the inset with it, since the inset only exists to keep icons off the
plate's rounded edge) — a cluster sits inside a tile that already has a fill, so a plate there is a box within a box and
made it the one slot on the card with a visible container. A folder on a *grid* keeps its plate: loose among plain
icons, that is what makes it read as one object. The preview draws the cluster too, off the
shared `categoryOverflowCluster` split — a preview that divided the apps differently from the surface would be worse
than none, and four-apps-no-cluster is the one arrangement a category big enough to need this screen never has. Relatedly, `APPS_CARD`
had to join `AppsViewModel.IconSlots` — leaving it out was not a skipped lookup but a silent substitution, since an
unresolved slot falls back to `LocalIconMetrics`, which inside `AppsCategoryCard` is the **folder's**: the surface drew
labels the card grid explicitly turns off while the settings preview drew none.

**The preview's fill is the one number that does not match the surface** — `PreviewFillAlpha` at 0.5 against the
surface's `CardAlpha` of 0.10. A card on the launcher sits on the *frosted* backdrop, so a faint tint reads clearly;
the settings punch reveals **raw** wallpaper, where 10% all but disappears and the corner-radius slider has no visible
corner to act on. Two subtler fixes were tried first and both failed, which is why the number is written down rather
than reverted: a wash behind the card *cut* the contrast (in a dark theme the wash and the card are both near-black —
the real frost works because it **blurs**, not because it washes), and an outline blended into the wallpaper it was
drawn over. Everything the controls actually change still matches the surface exactly; only the opacity is exaggerated
so those are legible.

**Its preview is a whole card, where every other section previews a cell** — `CategoryCardFace`, extracted to
`core:designsystem/cell` for exactly this: `feature:settings` cannot depend on `feature:apps`, and a second card
hand-rolled beside the sliders would drift from the one the surface draws. Same extraction, same reason, as
`IconPreviewPlate`. Half of what the card's controls shape is the tile *around* the icons, which a lone cell cannot show.
It draws a **made-up category filled with installed apps**, never one of the user's: a real category would show whatever
that phone happens to hold, and one holding two apps leaves half the slots empty — which is exactly the state in which
the spacing and padding sliders show nothing. A preview has to draw the full case for its controls to be readable. It
**punches through to the wallpaper** like the cell previews (`BlendMode.Src` over `PunchThroughPane`), since a card is
translucent by design and judging its fill against a flat panel judges it against something it never sits on. And it is
drawn at the width one card is *really* given — the lane less its share of the spacing between lanes — with
`requiredWidth`, because the pinned header hands down a fixed full-width constraint that a plain `width` is coerced into:
the card rendered at the pane's width, silently, which is the one thing a size preview must not do. The card's four
sliders sit **below** it in the pinned group with the icon controls, not above it in the scrolling layout group — a
control that scrolls its own preview off the screen cannot be judged.
**Resizing a grid names an edge, not a count**, because that is what decides where the items go — removing the *left*
column shifts everything left, removing the right one drops what sat there. So a press is **two writes**: the count
(`updateGrid`) and the placements it displaces (`GridReflow.edit` → `LayoutRepository.apply`), ordered grow-first for
an add and place-first for a remove so no observer sees a grid too small for its contents. That is why
`feature:settings` depends on `data:layout` at all: only the button press knows the edge, and a surface re-reading the
new size later cannot recover it. **The dock's version spills onto home** (`settleDock`, shared with
`HomeViewModel.fitDockTo` so the two triggers cannot disagree), never deleting as L1 did. The **companion zone can be on any of four sides** (`CompanionSide.of(edge)`, bridging `SideZoneEdge`), which is what
makes a top-strip widget area draw above its list rather than below it — and a caller-supplied `preview` now sits
**inside** the companion split rather than replacing the whole screen, so HOME's list shows its lanes *and* the widget
area over them. (Passing a preview replaced everything while only the APPS layouts passed one; they have no companion,
so nothing changed for them.) `LanePreview` moved to `component/` when the home list became its second consumer, as
`IconPreviewPlate` did. One `GridEditor` composable
serves every grid — a screen-shaped mockup at a **fixed size per posture** (L1's four numbers; a fraction of the pane
made the preview a different size on a tablet and, at a tall phone ratio, taller than the section holding it), ringed
by L1's own button arrangement, in **three shapes it takes from L1 as well** — both axes editable gives the plus-shape
(**removes along the top and left, adds along the right and bottom**, each pair spread to the preview's extent so a
button sits at the corner it acts on); *columns only* drops the top and bottom rows and gives each side rail **− above
+ for its own edge** (L1's `showRowControls = false`, and better than hiding the rails, which left the controls
furthest from the columns they change); *neither* draws the frame alone. `colBounds` is nullable to say the third,
symmetric with `rowBounds` — both mean "this axis is not the user's to set" — and the caption follows, down to being
omitted entirely when there is no axis, since a list showing "1 column" would be a count masquerading as a choice. The
companion zone is drawn at its real proportion **and on the side it really occupies** — `EditorCompanion` carries a
`CompanionSide`, four values where `DockEdge` has two because each section sees the split from its own end (home's
companion *is* the dock, bottom or end; the dock's is the pager, top or start), and the mockup splits with a `Column` or
a `Row` to match. Without that, a phone-landscape dock would draw as a thin strip along the bottom while the surface
drew a rail.
L1 had two ~220-line near-copies of this. **The APPS editor takes a `preview` slot**, as L1's drawer editor does, so
each layout shows the surface it configures: the three scrolling grids draw `ReflectivePreview` — cells at their
*derived* aspect, filling downward and clipped at the fold, which is the only mockup that makes adding a column
visibly gain rows — while the pagers and the card grid keep the even lattice, because a FIXED_PAGER really does divide
its page evenly and a card's height *is* its width. The category pager additionally draws L1's header + tab row, and **the list gets an editor with no buttons on it** —
one lane and a declared row height means nothing to press, but two things to see: `LanePreview` draws full-width lanes
at `rowHeight ÷ width` (icon square beside a label bar, the one structural fact separating a list from a one-column
grid at this size), and the search edge sits above or below them. Deliberately *not* `ReflectivePreview`, which derives
height from width: that is what the scrolling **grids** do, where a list's height is declared — the three-way split in
`GridBlueprint.rowHeightDp`, reaching the preview layer. **Every slider that moves a preview previews live**
(`onPreview`), including the dock's *height*, where the shown value feeds the companion split, the fitted grid, the
editable range, the caption and the icon preview's cell height together — a preview showing the new proportion but the
old row count would be worse than one showing neither, since the row count is what the height decides.
**A grid's horizontal margin insets the lattice only, never the companion zone**: home's pager and dock store separate
margins, so insetting both from one number would show a dock narrowing because the pager's slider moved (L1 passes
`insetFraction` to its `GridPreview` and none to its `NonGridPreview`). **What is not carried is the color** — it tells add from remove by red vs
green, which the palette forbids — and that costs nothing, because in L1 the *position already encodes the action* and
the color was reinforcement. An earlier cut mistook the color for the signal and centered a −/+ pair on each edge
instead; the arrangement above replaced it.
**A grid editor shows the grid that is *drawn*, not the one in storage** — and that is what makes the icon controls under
it move it. Both halves come out of one formula: the icon **guardrails** set the smallest usable cell, and dividing the
area by it gives both the *bounds* the buttons offer and the *counts* the preview draws (`fitGridConfig` for home and the
dock, `fitCols` for the APPS scrolling grids, whose surfaces apply the same clamp to their own measured width).
**Only the guardrails, plus the label controls on the row axis — never `iconPercent`.** A cell's floor is
`minIconDp + cellPadding`, because `resolveIconSize` clamps *up* to the guardrail and so a cell overflows exactly when
the guardrail exceeds its inner width; the fraction scales the icon *within* those bounds and cannot make a cell
unusable. Dividing the guardrail by the fraction (L1's `scrollingMaxColumns`, adopted by the first port) answers a
different question and inverts this one — at 30% a 28dp guardrail demands a 101dp column, so shrinking the icons reports
*fewer* columns. L1's home editor used `gridMaxima`, which leaves the fraction out and is the right shape; it just used
the bare guardrail as the whole cell and forgot the inset. A press
then counts from the drawn number, so `−` on a four-row home writes three instead of writing four because storage still
remembers five. The clamp is still **never written back** — shrink the icons and the row returns; only a press writes,
the dock's height commit staying the one deliberate exception. L1 reconciled it the other way, from a `LaunchedEffect`
inside its home detail that wrote clamped counts into storage on *every* cause, so an icon tweak destroyed a row count
for good and only while that screen was open. **The APPS pager is the one grid whose fit could not stay in a UI**: its
rows × cols is also the page *capacity* `AppsViewModel` paginates the store against, so a count clamped where it is drawn
would describe pages the store never made, and a drop would compute its slot against a capacity the store does not
apply. So the fit is *reported* instead — `AppsScreen` measures, fits the stored grid, and calls
`AppsViewModel.setPagerFit`, which becomes the capacity everything downstream reads. Two properties of that make it
safe: the report is **gated on the store having answered** (paginating against a blueprint placeholder would write pages
nobody chose and then rewrite them — pagination *writes*), and it is a **runtime bound** rather than the removed
`setPagerGrid`'s blueprint-derived default, which is the distinction that keeps `setDevice` the input it was made.
**One set of icon defaults, taken by every grid** (`IconSizing()` unmodified everywhere): the icon **fills its cell**,
capped at **48dp**, never below **24dp**, with `IconSizingRanges.IconDp = 24..120`. At 100% the *upper guardrail is the
icon size* on any cell bigger than it, so icon size is one number in dp rather than a fraction of a cell the user has to
picture — the per-grid fractions this replaced (home 88%, app grids 75%) were the fraction doing that job, and
double-counting density while at it, since a narrower cell already gives a smaller icon at 100%. The **lower** guardrail
is a *cell* floor (`CellFit` inverts it), which is why its bound is 24 and not lower: at 16 the arithmetic offered a 24dp
cell — thirteen rows in a 320dp dock — that nothing could be tapped in. 24 is also L1's own unused `MIN_CELL_DP`
("press-area floor"). `IconMetrics`' Compose-side defaults mirror `IconSizing`'s and **must keep doing so** — same record,
two type systems, so a difference would surface as a jump the moment the store answered.
`core:designsystem/grid/CellFit.kt` answers "how large can this grid be": `boundsIn`/`editableRangeIn` as a **ceiling**
for a grid whose counts a user picks, and `fitGridConfig` reading the same maximum as a **value** for the dock's rows.
Beside it, `usableWindowArea` is the **one place the launcher measures the screen** — home, the APPS surface and every
settings section read it, because a settings screen cannot measure the surface it configures and so must measure the same
window the same way (L1 kept `homeGridArea` in settings and `pagerBoundsInWindow` on the surface, which could disagree).
The **dock section** (S4c, done) is the first consumer of both and the pattern the rows/cols editor follows — its
bounds are computed where the window is measured (one usable cell up to a third of the screen) rather than stored,
because a store cannot check either without measuring. **Every surface now reports its `DeviceConfiguration` to its ViewModel** (`setDevice`), which *replaced* the
narrower `setGridConfig`/`setPagerGrid`: pushing the input down means page capacity and icon sizing both derive there.
Full plan, phase state and the settled dock spec: [docs/SETTINGS_PORT_PLAN.md](docs/SETTINGS_PORT_PLAN.md).

**Navigation + shell (B5) done.** `core:navigation` holds `HomeRoute` and a two-method `Navigator`; feature
vocabulary stays *out* (L1 exported an 11-value `SettingsSection` to every consumer), which is why `SettingsRoute`
itself lives in `feature:settings` now that it carries a section — see the surface-menu notes. `app`
declares its own dev-harness key, since `entryProvider` is a mapping and not a registry. `feature:shell`'s
`LauncherShell` is the launcher — `SurfacePager` with `HomeScreen` center and side surfaces from the register — and it
owns the **launcher theme boundary**, which is why `HomeScreen`/`AppsScreen` no longer theme themselves. It also owns
the **full-screen frost**, which `SurfacePager` takes as an `overlay` slot between the center and the sides: not panned
with either, and driven by `SurfacePagerState.progress` (the pan collapsed to "how far in is the other surface",
unsigned and edge-agnostic) so the content slides while the frost fades. The launcher
boots into it; the dev harness (all playgrounds + the component gallery) is kept as a peer destination reached from a
row in settings, so no dev chrome ships on a real surface. A gear chip over HOME is the admitted scaffolding standing in
for the P7 long-press menu.

**The drag is the launcher's, not a surface's — so an app can be dragged from APPS onto HOME.** `feature:shell` hosts
the **one** `DragCoordinator` and provides it through `LocalDragCoordinator`; every surface reads it. Each surface used
to remember its own, which was indistinguishable from this while no drag crossed a boundary. Three changes carry it,
and each removed something rather than adding a layer:
- **A `DropZone` answers for itself, end to end** — it carries its own `planner` *and* its own `onDrop`, and
  `DragCoordinator.drop()` dispatches the landing to the zone the finger came to rest in before returning. That is
  docs/DRAG_AND_DROP_DESIGN.md §10's *"behavior travels with the destination zone"* made structural, and it is
  **required** rather than tidy: an app lifted in the drawer is released by a cell in `feature:apps`, and the thing
  that must commit it is *home's* grid. It deleted the `when (zone.id)` every multi-zone surface repeated in its
  planner and its drop, and with it the `AppCollectionDragDelegate` hand-off and the construction-order squeeze three files
  documented (the delegate had to exist before the coordinator, which had to exist before the folder host). A cell's
  `onRelease` is now only what the *source* surface knows — that a drag has left it.
- **`RegisterDropZone` + `LocalSurfacePresented`.** A slot stays composed for the whole of a pan and, when a drag was
  ejected from it, for the whole of that drag — which is what satisfies the "keep a source surface composed while a
  drag from it is in flight" rule and is why an ejected drag survives with no re-tracking. The bill is that *composed*
  is no longer *on screen*: an off-screen surface's `onGloballyPositioned` does not reliably re-fire as the pan moves
  it, so bounds it published while it *was* on screen would sit in the registry claiming the finger from off-stage. So
  registration moved out of the layout callback into a composable gated on presence (defaulted from the local, so it
  cannot be forgotten), and teardown removes a zone **by instance**, which is what lets two folder overlays hand
  `ZoneId("folder")` over inside one composition. The same local gates each surface's floating proxy, so two never
  appear under one finger.
- **`EjectToHome` is one method** — close this surface — because that is all that was left to do. Compare L1's
  `HomeDragBridge`, which passed the app, the finger in window space and a grab offset, because its `CrossPager`
  stopped delivering pointer events to either subtree as it collapsed and the drag had to be re-tracked at the
  ancestor. **Two triggers, and the split is a property of the layout rather than a preference:** the **derived**
  APPS layouts (A–Z list, A–Z grid) eject *on lift*, since they own no arrangement and a drag on one can only mean one
  thing; the layouts that **store** one (pager, category pager, category card) eject when the finger reaches
  `TopActionZone` — see the band's own rules below. HOME then takes the app wherever its zones do: the pager and the dock **place** it (`Move` is
  an upsert, so an app with no placement needs no separate path — `HomeState.catalog` is the one addition, so home can
  draw an icon for an app it has never placed), and the vertical list **appends** it (MovingGap migrates a gap from
  where an item already *is*, and a stranger is nowhere). Nothing is removed from the drawer on the way: an app lives
  in the APPS arrangement *and* may sit on home.
- **The category card's grid stopped being lazy**, which reverses its own note. Its preview icons are draggable now —
  a preview is the only part of a card that names one app, so it is the only place a re-file can start without opening
  the category first — and a lifted cell owns the pointer stream, so a card disposed while auto-scrolling toward the
  target would kill the gesture. `HomeListSurface` made the same trade for the same reason. One consequence to know:
  a card's measured rectangle is no longer trustworthy inside a scroller, so each entry in the hit-test map records
  the **scroll offset it was measured at** and the correction is subtracted at hit-test time — self-contained, and
  zero-cost if `onGloballyPositioned` does re-fire.
- **The band has two states and two modes, and the states are the design.** `TopActionZone` is a full port of L1's,
  not the always-open banner the first cut drew. **Collapsed** it is exactly the status-bar inset deep — a hint that
  costs no screen and cannot be hit on the way to home's top row. **Expanded** (96dp) it has committed to being a
  target and names what it will do in words, which is the only way to tell *remove* from *uninstall* and the reason a
  drop shadow could never do this job. The threshold between them is **asymmetric** (status bar to arm, 96dp to
  disarm), which is what stops it chattering while the finger sits near the boundary — L1 spelled the same rule as two
  named thresholds. `rememberTopActionState` owns the timing; `DropIntent.REMOVE` is the second value with no cell
  behind it, added on `REORDER`'s terms rather than letting the band return a `PLACE` plan whose footprint is a lie.
  - **The two modes commit at different moments, and that is not a preference.** `ADD_TO_HOME` opens *at once* and
    fires on a **dwell** (~700ms), because its whole point is to get the drawer out of the way *while the finger is
    still down* — a release would end the gesture it exists to continue. `DELETE` opens after a shorter dwell
    (~300ms) and fires on **release**, because a destructive action that armed itself under a held finger would be a
    trap, and fingers cross the top of the screen on their way elsewhere.
  - **Firing collapses the band, and the next opening waits.** After the hand-off the mode flips to `DELETE` under a
    finger that may not have moved, so re-opening immediately would swap "Drop to home" for "Remove | Uninstall"
    in place — which is how a user ends up hovering a delete target they never went looking for. L1's
    `TOP_ACTION_SWITCH_GRACE_MS`: act → shrink away → pause → open again, offering something else.
  - **It is registered as a drop zone in *both* modes**, and in `ADD_TO_HOME` that is for the **masking** rather than
    the drop: while the finger is up there it must stop being the drawer's, or the pager's planner keeps migrating its
    reorder gap and a release before the dwell lands the app at the top-left instead of canceling. L1 blanked
    `hoverTarget`/`pendingGap` explicitly for the same reason; being the topmost zone says it structurally.
  - **Both targets are the shell's**, because the band spans every surface and the item under the finger may have been
    lifted in the drawer and never placed at all. `ShellViewModel.removeFromHome` is a plain `RemoveFromGrid`, which is
    also why it needs no "is it placed?" test — on an unplaced app it deletes no rows, so Remove *is* the cancel.
    `AppUninstaller` (`data:apps`, the sibling of `AppLauncher`) only opens the platform's uninstall prompt, which
    confirms and acts for itself; nothing is removed from the layout first, since a declined uninstall would otherwise
    leave an installed app the user can no longer see. **One bound:** an app dragged out of a home *folder* onto
    Remove goes back to that folder — it has no grid placement to remove, and the shell cannot see folder membership.
    L1 behaved identically, for the same reason.
- **The item context menu is the shell's too, and for the band's exact reason** (P7, `core:designsystem/menu`). One
  `ItemMenuHost` provided through `LocalItemMenuHost`, one `ItemMenuOverlay` above every surface — because the verbs
  on an item's menu belong to the **item**, and the same app is reachable from home, from the drawer and from inside
  a folder. L1 answered it per surface (home's `ItemContextMenu`, a near-copy `SideContextMenu` for the drawer and
  library) and wrote the two stages, the loading state and the toggle twice. The shell binds the three app commands
  once (`AppInfoOpener`, `AppUninstaller`, `AppShortcuts`); a surface contributes only what it owns — home's
  *Remove*, the drawer's nothing. It changed none of the drag wiring, exactly as this note predicted.

**Context menus (P7) — `core:designsystem/menu`, one host at the shell.** Long-press an **item** and its menu opens
under the finger; move on and it becomes a drag, exactly as `ItemGestureMachine` has modeled since B4. Long-press
**empty space** and the surface's own menu docks to the nearest screen edge. Ported from L1's `InlineContextMenu` +
`ItemContextMenu` + `ContextMenuPopup`, with these differences:

- **The menu outlives the finger, which meant changing the gesture machine.** `MenuOpen` + `Up` used to emit
  `DismissMenu` — written before there was a menu, and unusable once there was: a row can only be tapped after the
  finger is off the item, so the *release* is how the user reaches the menu. It now closes on a choice, on a tap
  away, or on the drag that may follow. A **cancel** still dismisses (the pointer was taken away, not given up).
  Nothing depended on the old behavior: every `onDismissMenu` in the tree was `{}`, which is also why that parameter
  is gone — the contract dismisses the host itself, on `launcherItemGestures`' "wiring that cannot be forgotten
  belongs in the one place every caller already goes through" rule.
- **It renders inline, never in a `Popup`** — L1's own conclusion, and the reason its two implementations exist. The
  menu opens *while the finger is down*, and a `Popup` is a separate platform window: raising one mid-gesture takes
  focus and can cancel the pointer stream the drag depends on.
- **The anchor is reported by the gesture, not reconstructed by the surface.** `onShowMenu` now carries the
  rectangle the modifier is attached to — which, by the touch-target rule, *is* the item's visible extent. L1 rebuilt
  it three ways (cell centers on the grid, an icon half-width plus a Y offset in a folder, a row's bounds in the
  list) and each could drift from what was drawn. `positionInRoot() + size`, never `boundsInRoot()`, for the
  clipped-inside-a-scroller reason stated elsewhere in this file.
- **Placement is pure and unit-tested** (`MenuAnchoring.kt`): a tall frame stacks the menu above/below and a wide one
  puts it beside, each flipping toward the half with room, then clamped into the frame with one gap doing both jobs
  (off the item, off the edges). Two departures — the halves are judged against `uiInsets`' usable area rather than
  the raw window, and `MenuPlacement` is **four values** where L1 had `(vertical, towardEnd)`, the same correction
  `SideZoneEdge` made to a pair of booleans.
- **Two stages: the app's own shortcuts first, the launcher's actions behind a chevron.** That order is L1's and it
  is right — shortcuts are what the *app* offers and are what a long-press is usually for. An item with no shortcuts
  (a folder, an app publishing none) collapses to one stage with **no toggle**, so the second stage never announces
  itself as missing. The load is a **suspending lambda the menu owns**, where L1 ran that `LaunchedEffect` in each of
  its three surfaces; it is called from the menu's composition, so it cancels when the menu closes.
- **It is modal, and the overlay's own full-screen tap-catcher is what makes it so.** Compose hit-tests siblings
  in reverse draw order and stops at the first hit, so a press anywhere reaches the catcher and never reaches the
  surface beneath it — no consumption involved, which is why the items' deliberate `…IgnoreConsumed` reads do not
  defeat it. The overlay also holds `SurfaceGestureLock` for as long as it is up, so nothing may pan out from under
  a menu, and back dismisses it before anything else answers.
  - `launcherItemGestures` briefly carried a second guard — ignore a new press while a menu is open — added on the
    reasoning that consumption could not stop a tap "away from the menu" from also launching the icon it landed on.
    That reasoning was wrong about *which* mechanism blocks it, and the widget picker's scrim demonstrated as much:
    a long-press over an icon behind it produces nothing from the icon. The guard was removed as dead code. The one
    case sibling hit-testing genuinely does not cover is a press already **down** when an overlay appears, since a
    pointer keeps the hit path it was assigned at DOWN — but that is the gesture that opened the menu, which is
    supposed to keep running.
- **Colors come from the theme and the panel is frosted.** L1 hardcoded `Color.White` throughout, which would be the
  one place in the launcher ignoring the wallpaper-brightness signal the whole theme is built on. `wallpaperBackdrop`
  with `refracts = true` makes this **the first frosted panel**, so the effects section's sliders and liquid glass's
  rim finally have a consumer.
- **One width for every menu** (248dp), where L1 sized each to its widest row: a row can then `fillMaxWidth`, so the
  whole row is the tap target rather than the text on it, and a menu stops changing width between icons.
- **Unbuilt verbs are absent, not disabled.** L1 showed "Rename" and "Edit icon" grayed out; the settings sections'
  own rule is that a control which changes nothing is worse than a missing one. So a home folder's menu is
  *Remove folder* alone, an APPS-pager folder and a category card get **no menu at all**, and rename returns with its
  op. **"Edit icon" is no longer one of them** — it is bound at the shell like the other app commands, and routed
  through `app` so `feature:shell` never learns the icon studio exists.
- **What each surface adds is only what it owns**: home's *Remove* (`RemoveFromGrid`), the home list's *Remove* (its
  own order store, not a placement), an app **inside a folder** nothing — it has no placement, so a Remove row there
  would do nothing, and taking it out is the drag this menu's long-press already leads into. `data:apps` gained the
  three commands behind it — `AppInfoOpener`, `AppShortcuts`, and the wrapper reads they need — each resolving the
  profile from `userSerial` like `AppLauncher`, where L1 hardcoded `Process.myUserHandle()` and `AppInfoOpener` uses
  `LauncherApps.startAppDetailsActivity` rather than L1's package-only settings intent for exactly that reason.
- **Uninstall needed two things L1's byte-identical intent did not**, and without either it does nothing *visibly*:
  **`REQUEST_DELETE_PACKAGES`** (an app targeting API 29+ must hold it to ask for a package to be removed — the
  uninstaller activity otherwise finishes the instant it opens, with no dialog and no exception to catch; L1 was
  covered by `QUERY_ALL_PACKAGES`), and **`Intent.EXTRA_USER`**, so a work-profile app is removed *in its profile*
  — the same per-profile correction the other three commands make. AOSP's Launcher3 sends exactly this pair.
  Diagnosing it turned up a broader gap: **nothing had ever planted a Timber tree**, so every `Timber.w` in the
  codebase wrote nowhere and this failure had no way to report itself. `LauncherApplication` now plants one on
  debuggable builds, read from `ApplicationInfo.FLAG_DEBUGGABLE` rather than `BuildConfig.DEBUG`, which only exists
  where that build feature is switched on.

**The surface menu is the same panel with a different anchor, and the anchors are a sum type.** `MenuAnchor.Item`
carries the item's bounds; `MenuAnchor.Press` carries the point a long-press landed on. That is L1's two position
providers named as one thing rather than two composables a caller had to pick between correctly, and the difference
is not decoration:
- **An item menu points at a thing, so it sits beside it and scales out of the edge nearest it.** A surface menu
  points at nothing, so it **docks flush to whichever vertical edge the press was nearer** and slides in from it,
  vertically centered on the finger — L1's `EdgeDockedPopupPositionProvider`. Planting it on the press point would
  claim a relationship with whatever patch of wallpaper it covered, and hugging the edge leaves that wallpaper
  visible beside it. `ResolvedAnchor` computes the side **once**, because the reveal needs it in composition and the
  placement needs it in measurement.
- **A surface menu has no header**, as L1's had none: there is no honest title for "the home screen" that is not a
  word taking a row.
- **`surfaceMenuGestures` is how a press reaches it, and it needs no geometry.** It goes on a surface's *root*, so
  it sees presses that land on icons too (`launcherItemGestures` never consumes a down) — answered twice: the item
  is given a **head start** (`longPressTimeoutMillis` + 120ms, so which timer wins is a fact rather than a coin
  toss), and then the gesture **asks `SurfaceGestureLock`**, which is already exactly "something owns this finger".
  L1 instead ran one root recognizer that resolved the cell and branched on `isOnIcon` — which works, but makes the
  surface responsible for knowing where every item drew its icon, the very decision this codebase hands down to the
  cell's content. Asking the lock settles a case nobody wrote down for free: an open folder holds it, so pressing a
  folder's backdrop cannot open the menu of the surface underneath.
- **One detector per surface where L1 had three** (home, dock, widget area). L1's three differed because their
  *actions* did — "Widgets" on home, "Add widget" on the widget area, nothing on the dock. Ours all resolve to the
  same row, so three would be three ways to say one thing; the split returns with the first verb that is not
  launcher-wide.
- **HOME's menu is one row — Settings — and that is the honest state rather than a stub.** Every verb L1 put above
  it waits on something unbuilt: "Add app" and "Widgets" need pickers, "Remove page" needs page management. L1's
  *Wallpaper* row is no longer blocked — see the route note below — and is now a one-line decision rather than an
  architectural one; it is left out until someone asks for it.
- **Deep-linking into settings moved `SettingsRoute` out of `core:navigation` and into `feature:settings`**, which
  is the decision that file's KDoc reserved ("whether a section becomes a route argument or its own `NavKey` is a
  decision for the port that introduces them"). Three parts: a **route argument**, because settings is one
  destination whose sections are panes and a pane is not a place on the back stack; **declared in the feature**,
  which is what lets the argument *be* a `SettingsSection` — L1 put that enum in its navigation module purely
  because its route carried one, and every module touching navigation could then import the whole taxonomy;
  and `core:navigation` keeps only `HomeRoute` and the `Navigator`, which is the shape it was always arguing for.
  **`app` is still the only layer that names a section**: the APPS surface says "open my settings" and passes the
  `AppsLayout` it is showing (`core:model`, shared by everyone), and the mapping from that to a pane happens in
  `LauncherNavHost` where both are already visible.
- **It replaced the dev gear chip**, which `app` had been carrying as admitted scaffolding "until the P7 long-press
  menu exists". Navigation reaches the shell as an `onOpenSettings` action rather than through `LocalNavigator`,
  which is `SettingsScreen`'s own `onOpenDevHarness` pattern: `app` owns the back stack, so `app` says where a verb
  goes and no feature module learns that a destination exists.
- **APPS gets one too, which reverses L1's call** ("no context menu on empty space in side surfaces") — at the
  author's direction, and the reason is a property of L2 rather than a preference. L2's touch targets are narrower
  by construction: an item's gestures cover its icon and its label, never its cell or its row, so every APPS layout
  has real free space between items and none of it did anything. Its one verb is **"Apps settings"**, which opens
  the settings for the *arrangement being looked at* — the APPS section has a chip per layout, and without this
  reaching the one you are using is a long-press on home, then a section, then a chip. One detector on
  `AppsScreen`'s root, so all five layouts get it identically and a new one cannot forget it.
- The host is `LauncherMenuHost` / `LocalMenuHost` / `MenuOverlay` — renamed from `ItemMenuHost` in the same change
  that gave it a second kind of menu, since **one host** is what keeps "both open at once" unrepresentable.

**A side slot is composed only while it is needed, and that is an ANR fix rather than a tidy-up.** `SurfacePager`
used to compose every bound slot at all times. With one binding that was invisible; with **four** it was five seconds
of dropped input on a weak device — four whole APPS surfaces, four sets of cells, all baking icons at once. Three
things caused it together and all three are fixed, which is worth knowing because only the first is about the pager:
- **The slot gate.** Composition begins the instant a swipe moves off HOME (`SurfacePagerState.engagedEdges`, which
  flips at *zero* where `openEdge` flips at a half — content has to exist before it can be seen) and ends when the pan
  settles back. `retainedEdges` is the exception the drag needs: the shell pins the edge an eject came from,
  synchronously inside `EjectToHome` — the one instant the answer is both needed and still true — and releases it when
  the drag ends. Each slot is wrapped in a `SaveableStateHolder.SaveableStateProvider`, so a drawer closed and
  reopened is on the page it was left on; without that the gate would be paid for in the thing a launcher is judged on.
  The cost that remains is real and accepted: the first frame of a swipe now composes a surface, where before it was
  already there.
- **`IconRenderManager.get` coalesces concurrent bakes.** A plain `cache.get() ?: bake()` is a thundering herd — every
  caller arriving before the first bake finishes repeats the whole load-parse-composite and allocates a bitmap the
  next `put` immediately makes garbage. That allocation *was* the `HeapTaskDaemon` at 57% in the trace. It is now
  `suspend`, and duplicate callers await the first one's result rather than holding a thread.
- **Baking is capped at half the cores, never more than three.** `Dispatchers.Default` is sized to the core count, so
  a screenful of cells launching a coroutine each will take every core and leave none for the main thread. Leaving
  cores idle is the point. `LauncherIcon` names no dispatcher any more — where baking runs belongs to the thing that
  bakes.

**The surface swipe is nested-scroll aware, and one fact answers it from both ends.** A one-finger swipe crossing a
surface boundary belongs to whatever scrolls under it until that content runs out — so `OneFingerSwipe.AT_EDGE` is now
genuinely different from `ALWAYS`, which is what `LauncherShell`, `SurfacePager` and the playground all said was
deferred. It is two types, deliberately split by what changes:
- **`ScrollAxes`** (static) — what a layout scrolls on each axis (`AxisScroll.NONE`/`BOUNDED`/`INFINITE`), which is a
  function of the **infinite-scroll setting** for a paged axis (`AxisScroll.ofPager`).
  `ScrollAxes.oneFingerSwipe(edge)` is the whole derivation of the policy, and it is the surface-pager playground's
  private `Scroll.toSwipe()` promoted to a real type once the real layouts existed to answer it. Each feature declares
  its own — `HomeLayout.scrollAxes` in `feature:home`, `AppsLayout.scrollAxes` in `feature:apps` — because the shell
  owns the *question* (it is the only layer seeing both sides of an edge) and each module owns its answer.
- **`ScrollEdges`** (live) — where that content is resting right now, four booleans keyed by `HomeEdge`. Published by
  each layout through `ReportScrollEdges` into a per-slot `ScrollEdgeSlot` that `SurfacePager` provides, and read by
  the gesture.

Five things about it are load-bearing:
- **The gesture runs on `PointerEventPass.Initial`, and nothing above works on any other pass.** `positionChange()`
  returns `Offset.Zero` once a change is consumed, so a parent on the Main pass — which sees its children's events
  *after* them — accumulates zeros over any scrolling content, never crosses slop, and never reaches the claim block
  at all. That is exactly how this landed the first time: every piece was wired and none of it ran. Initial gives the
  pan first refusal on the raw delta and it **consumes only if it claims**, so declining costs the child nothing.
  L1's `CrossPager` used Initial for the same reason, and taking the default pass was the one part of it not ported.
  Two consequences fall out, both of which were bugs until they were answered:
  - **A claim must never be idle.** A swipe pressed against a bound the pan is already clamped at moves nothing while
    consuming the finger, which on Initial eats every forward page-swipe and downward scroll the open surface's own
    content needs. So closing is gated on the finger genuinely traveling toward the edge the surface came in from.
  - **`launcherItemGestures` reads the finger with `positionChangedIgnoreConsumed()`**, the twin of the
    `changedToUpIgnoreConsumed` beside it. The pan claims at the platform slop (~8dp) and an item needs 20, so with
    the consumption-sensitive read a swipe begun on an icon went `Down` → `Up` with no `Move`, stayed in `Pressed`,
    and **launched the app**. Where the finger is is never in dispute; whether the item acts is the machine's
    decision, and `ReleasedToParent` is the phase that says so.
- **Opening and closing are the same question from opposite ends.** Opening edge E asks HOME's content about the edge
  facing E; closing asks *that surface's* content about `E.opposite`, because closing drags it back the way it came.
  One expression each, so they cannot drift. **L1 had two differently-shaped types for this** — `SurfaceScrollEdges`
  for side surfaces and `HomeGestureRelease` (`swipeRightOpensLeft`/`swipeUp`/…) for HOME, which conflated the live
  edge with the policy and could not express a scrolled home list at all: its list home was a flat `swipeUp = false`,
  forbidding the crossing rather than handing it off at the end of the list.
- **The slot holds a lambda, not a value, and the gesture invokes it from a pointer callback.** The question is asked
  once per gesture, so reading the scroll states *there* is free and never stale — where publishing a value means
  reading `canScrollForward` in composition, and for a `ScrollState` that is a raw read of `value`, i.e. a
  recomposition of the whole surface per scroll frame. That is the trap `CategoryPage`'s geometry comment already
  warns about on the same surface, and L1 fell into it (`PlainGridLayout`, `IosCategoryGridLayout`).
- **One report per surface, not one per scroller** — a second call in the same slot replaces the first rather than
  combining with it. `AppsCategoryPager` is the case: it owns both axes, so it builds one value from its pager *and*
  the current page's scroller, which `CategoryPage` hands up beside its geometry (only the surface knows which page is
  current, and asking in composition would subscribe the layout to the pager's animated position).
- **The decision is made once, at slop, and stands for the whole gesture.** Scrolling a list to the bottom and
  carrying straight on does not start panning; a second swipe from the bottom does. L1 behaves the same way. The
  honest limit of a hand-off decided by a claim rather than by leftover deltas — continuing would mean real
  `nestedScroll` connections on every surface. Two fingers skip the question entirely.
  - **One qualification: a `SurfaceGestureLock` claim at slop postpones the decision rather than ending it.** The loop
    keeps accumulating and decides on the first event at which nothing is claiming. That exists for
    `EmbeddedViewTouchFrame` (see the widget notes) — an embedded Android View cannot be *asked* whether it wants a
    gesture, so it claims pre-emptively and releases when it declines. Nothing else notices, because every other
    claimant holds its claim until its own gesture is over.

**Infinite paging is a setting again, and it is per pager.** `SurfacePaging` is the fourth slice — one sparse
`Map<GridSlot, Boolean>`, read resolved through `SettingsRepository.pagerWraps`. Its own slice rather than a field in
`SurfaceMetrics` because every map in that one is a *size* keyed `slot × device`, and wrapping is a behavior with no
device dimension: turning the phone on its side is not a reason for the pages to stop looping. The default lives on
`GridBlueprint.wraps`, null meaning "not a pager whose wrapping is the user's" — the same convention `extentDp` and
`rowHeightDp` use, and what lets `setPagerWrap` refuse a slot rather than every caller checking. Exactly three grids
declare one: `HOME_MAIN`, `APPS_PAGER`, `APPS_CATEGORY`. A folder pages too and is deliberately not askable — its
pages are a handful of apps in a card, bounded by construction.
- **Per pager, where L1 had one global flag.** L1's `pager.infiniteScroll` was one boolean read by home's pager *and*
  both drawer pagers, with its only control in the **Home** screen — so turning it on to make the home pages loop
  silently changed the app drawer, and a user configuring the drawer had no control to find. Here each pager owns its
  answer, so the toggle can live in the section that configures that surface: Home's in the Home section (gated on the
  pager pairing, as L1's was), and APPS' in the APPS section, where **it follows the chip** — selecting the category
  pager selects that grid's setting.
- **It defaults to off, where L1's defaulted on**, and that is the reversal the swipe rules force. A wrapping pager
  never reaches an end, so there is no edge to hand off at: `AxisScroll.INFINITE` → `OneFingerSwipe.NEVER`. On by
  default would mean a horizontal edge binding could only be opened with **two fingers** out of the box, with nothing
  on screen to explain it. Both switches say so in their subtitle, because a control that takes a gesture away has to.
- **`HomeLayout.pagerSlot`/`AppsLayout.pagerSlot` are what keep every consumer from re-deciding which grid is meant**
  — `core:model`, because `feature:settings` needs the APPS answer and does not depend on `feature:apps`. Null on the
  layouts that do not page, which is also what both settings states carry (`wraps: Boolean?`) so a pane draws the
  control from its state rather than testing the layout a second time.
- The one genuinely awkward consequence: `HomeMainSizing.Pager` now carries `wraps`, so a type named for sizing holds
  a behavior. Kept there because it is a setting only a *pager* can have — a `List` given one would be meaningless —
  and a nullable field beside the state would make exactly that expressible, which is what the sum type exists to
  prevent.

Two things L1 did here are **not** carried. Its reporters blanked every field to false while an item drag was in
flight, so the pan could not claim mid-drag; L2 answers that one layer up with `SurfaceGestureLock`, which gates the
whole gesture instead of lying about where the content is. And L1 hoisted four `mutableStateOf`s plus four reporter
lambdas into its home screen; the pager composes the slots, so the pager owns them and the shell wires nothing.
The report is **one answer for a whole surface**, so a vertical swipe starting inside the dock or the widget area is
treated as if it were over the main area — L1's own simplification, and refining it would mean a per-region answer to
a question decided once, at slop.

**Known gaps, deliberate:** the item context menu offers **nothing for a folder on the APPS pager** and nothing for
a category card — rename and dissolve have no ops on the APPS order store, and category management is a
`feature:settings` concern, so those long-presses show no menu at all rather than a menu of disabled rows. The
**rename** is the one verb the menu is still owed on home, the icon studio's "Edit icon" having landed. A drag ejected from APPS draws
**no floating proxy** for the frames the surface takes to close: the drawer stops painting one the moment it is no longer presented
and home starts once it is, and neither owns the gap in between (the drop itself is unaffected). The effects section's five sliders are **live again**: the context
menu is the launcher's first frosted *panel*, so it reads the stored strength and tint and it is the first surface
with edges of its own for liquid glass's rim to bend light at. The full-screen frost stays fixed per variant by
design, so those two now genuinely differ.
No item is reachable by an accessibility service — `launcherItemGestures` is raw
`pointerInput` with no `semantics { onClick { … } }` (P7 gestures). No formatter in the build (no
ktlint/spotless/detekt), so style drift isn't caught. The Gradle **wrapper is missing** from the repo
(`gradle/wrapper/gradle-wrapper.properties` is tracked but `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` are not),
so there is no CLI build from a fresh clone — **but a Gradle 9.6.1 distribution is already cached**, so
`~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle :app:assembleDebug` builds and tests from the
CLI today. `feature:settings` and `feature:apps` declare junit but have **no tests**.

## Conventions summary

- Kotlin + Gradle Kotlin DSL (`.kts`) throughout.
- Follow the existing module boundaries and the build order in the rewrite plan.
- Match surrounding style; keep KDoc current when changing a type's purpose.
- **`delay` takes a `Duration`, not a bare `Long`** — `delay(FooDwellMs.milliseconds)`, never `delay(FooDwellMs)`
  (`kotlin.time.Duration.Companion.milliseconds`). The IDE flags the `Long` overload, and this codebase is full of
  dwell/timeout constants, so the unit belongs at the call site where a wrong one would otherwise be invisible. The
  constants themselves stay `Long` with an `Ms`/`_MS` name; only the call converts.
