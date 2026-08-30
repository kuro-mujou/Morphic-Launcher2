# Icon architecture — layer editor + baked display

*Locked 2026-07-23; built 2026-08-11. Split out of [CLAUDE.md](../CLAUDE.md) on 2026-08-20.*

The design record for the icon subsystem: the layer model, the two render paths and the shared
derivations that keep them honest, every effect in the pipeline, the studio, the plate, and
persistence. **Read this on demand when working on icons** — it is reference material, not a rule
every session needs loaded.

Companion plans: [ICON_STUDIO_PLAN.md](ICON_STUDIO_PLAN.md) (S1–S8, the studio itself) and
[ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md) (the effect expansion; its §8 is the phase-2 assessment).

---


The icon system is a **layer editor** (like a drawing app) whose output is a **single flat bitmap** shown on
every surface. Distilled from L1's `ICON_LAYER_STUDIO_PLAN` — adopt its end-state, skip its flat-column churn.

**One thing is drawn live and outside that bitmap: the plate.** A silhouette of blurred wallpaper behind the
artwork, which depends on *where the icon is* and so cannot be baked at all — see the plate note below. The stored
unit is therefore an `IconAppearance` (recipe + plate + zoom) rather than an `IconLayerSet`, and the bake key is
still the recipe alone.

**This is now built, S1–S7 of [docs/ICON_STUDIO_PLAN.md](ICON_STUDIO_PLAN.md)** — one plan replacing L1's
*five* icon docs, which read in date order are a churn log rather than a spec (its persistence model reversed
three times inside one document, at a cost of four destructive schema bumps on one table). What is left from *that*
plan is **icon packs**; presets are built, and so is the plate ("skin") that plan and this file both deferred.
The studio has since outgrown it: a second plan,
[docs/ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md), takes the effect list from two to thirteen and is **complete** — the effect **pipeline**, the effect **panel**, the **filter** library, the
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
  `LayerEffect.Extrude` (the silhouette repeated behind itself), `LayerEffect.ChromaticSplit` (the color channels
  displaced), `LayerEffect.Outline` (a hard band following the silhouette),
  `LayerEffect.Bevel` (the silhouette read as a raised surface and lit),
  `LayerEffect.Glow` and `LayerEffect.Shadow` (the silhouette blurred behind it),
  `LayerEffect.InnerShadow` and `LayerEffect.InnerGlow` (the silhouette's complement blurred *inside* it, laid on
  or screened), `LayerEffect.Ripple`
  `LayerEffect.Grain`, `LayerEffect.Pixelate` and `LayerEffect.ProgressiveBlur` (waves, noise, cells and a masked
  blur), `LayerEffect.Glass` (the layer read as a slab of glass and refracted through — Bevel's surface bent rather
  than lit), `LayerEffect.Dither` (the colors crushed to a coarse palette and the error diffused — the riso look),
  `LayerEffect.Filter` (one of the built-in looks, by id) and
  `LayerEffect.Duotone` (the tonal range mapped onto two chosen colors). **Twelve of the twenty-one do not draw
  live** — everything that needs a blur or a per-pixel pass — which is what `drawsLive` and the bake-backed preview
  exist for. **All thirteen the plan set out are built, and so are all six of phase 2**; two more —
  `Glass` and `Dither` — are the first harvested from the gart study ([docs/GART_HARVEST.md](GART_HARVEST.md)),
  `Glass` sharing `LayerSurface` with `Bevel`. See the notes below for each, and
  [docs/ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md) — whose **§8 is the phase-2 assessment**: six more effects
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
box" the moment `ContentAnchor` existed, and so became arithmetic rather than a constant; its Compose half,
`Modifier.shapeMask`, is public now because the **plate** is cut from the same list), `LayerPattern` (a tile's
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
on the bottom layer blends against the studio canvas rather than against nothing), and the two paths were compared
side by side in an `IconLayers` dev-harness page until that harness was deleted — so the check is now the studio
against a real surface, by eye, because comparing pixels needs instrumentation this project has no setup for.
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
    with it, the panel's switch grayed out mid-gesture, and dragging back up produced a *fresh* effect at defaults
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
    effects this unblocks: [docs/ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md).

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
  - **A page's height is derived from the type scale, and a short page top-aligns.** Both were guesses and both
    showed: the label band was a flat 20dp against the ≈22dp `labelSmall` really occupies at font scale 1 — and more
    at every accessibility scale — and since that number is the *pager's* height and a pager clips, the bottom row
    lost its descenders and then its word. `effectLabelBand` reads it off the type scale from the same three
    quantities the tile draws with. And `HorizontalPager` centers its pages by default, so the last page's single
    row floated in the middle of a band sized for the fullest one, which reads as the grid having moved rather than
    as a page being short. The shape pager had the identical default, latent at exactly one page.
  - **A selection ring goes *above* the clip it traces, never inside it.** `Modifier.clip` is a hardware outline
    clip with no antialiasing, so wherever its boundary runs along a ring's own antialiased outer edge it removes
    whole pixels: straight sides survive (axis-aligned, on the pixel grid) and the arcs come back thin and stepped.
    Every tile in this studio had `.clip(shape)` ahead of `.border(…, shape)` at the *same* shape — filter, blend and
    pattern swatches, the layer rail's tiles, source tiles, the color field's dots. Tuning radii cannot fix it and
    trying is informative: **different** radii cut (a larger radius removes more of a corner, so the outer clip bites
    into the ring), and **equal** radii still cut sub-pixel. What matters is that no clip runs along the ring. Where
    two rings coincide with two *different* clips — the color swatch's selection ring and its own faint edge — no
    ordering in one chain clears both, so the rings became a sibling with no clip of its own. On a circle this reads
    as "the ring is a bit thin" rather than as a corner defect, which is why it went unnoticed there.
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
    the four sliders: the order they compose in. Three builders are new: **`contrast` pivots about mid-gray** (without
    the offset it is a brightness control that also steepens, the usual way this is written wrong), **`mix`**
    weights each output channel across all three inputs, which is what a true sepia needs and what `scale`
    structurally cannot express, and **`duotone`** maps the tonal range onto a two-color ramp. The fifth column is a
    translation on 0..255, which is silent when wrong.
  - **A filter swatch shows the look, not the icon** — one fixed reference gradient under each filter's matrix.
    Previewing on the real icon is a bake per tile, and an icon that happens to be black says nothing about a warm
    grade; every tile being the same strip is what makes them comparable.
  - **The library grew to 46 looks in seven categories, drawn from captures of the same reference studio the
    effects came from**, and three things about the expansion are worth keeping:
    - **`duotone` is the one piece of new arithmetic, and it is a whole family.** `out = dark + luma × (light −
      dark)`, which discards hue entirely and keeps only how light each pixel was — *not* a tint, which attenuates
      the colors already there and so leaves a red icon and a blue one different. Discarding the hue is exactly
      what makes a screenful of icons drawn by different hands read as one set. The span is divided by 255 while
      the weights are not, and getting that backwards produces a blown-out picture rather than an obviously broken
      one; **the test caught it in the authored version of this**, which is why it is a shared builder with a test
      rather than a matrix written out per entry.
    - **A matrix cannot quantize, and that is the bound on the whole file.** The reference's retro-hardware looks
      snap colors to a fixed palette, which is not a linear map at any size — so those entries here are the *ramp
      between the palette's two ends* (a duotone), not a stepped approximation pretending to be the same thing. A
      real one would be a `LayerEffect` with a per-pixel pass, like Pixelate.
    - **The names are ours.** The reference has a "Tarantino", an "iOS" and a "MIUI"; a filter's name is shipped,
      stored and user-visible, so borrowing one makes the launcher's vocabulary depend on somebody else's
      trademark for no gain in clarity. Same rule, now with three worked examples.

**`LayerEffect.Duotone` is the fourteenth, the first of the phase-2 six, and the one the filter library had already
built.** The layer's tonal range mapped onto a ramp between two *chosen* colors — `ColorMatrices.duotone` exactly,
which eight of the 46 authored looks already run on. It is **not a tint**, and that distinction is the whole reason it
exists: a tint attenuates the colors already there, so a red icon and a blue one stay different, where this discards
the hue entirely and keeps only how light each pixel was — which is what makes a screenful of icons drawn by different
hands read as one set. Five things:
- **Named for the look, not the mechanism** — the plan called it a *gradient map*, and a gradient map has arbitrary
  stops where this deliberately has two colors and no midpoint. Same rename Bloom took from `Gradient`.
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
  colors was about to do it again — on the **fifth column**, at 0..255, where a 0..1 value is visually black rather
  than obviously broken. The table keeps a two-color alias because a table of looks reads better in colors than in
  channels, which is what its old note was really about.
- **An addition rather than an adjustment**, which looks arguable and is not: `carriesSwitch`'s test is whether the
  entry's *resting* state is its off state, and this arrives at the full ramp because that is what makes it legible.
  So zero strength is not where it sits untouched, and its "off" is its absence — which is a switch. And the
  library's own DUOTONE category is not a duplication for the reason `Color` and `Filter` are not: one is a fixed
  vocabulary somebody authored, the other is *this* icon's two colors.

**`LayerEffect.Vignette` is a bloom's radial ramp run the other way, and the second phase-2 effect.** Color
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
  arrives from one side, which is a bloom; an off-center disc is a bloom placed. Either would make this the entry
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
  `DARKEN` and `LIGHTEN` all document the union alpha `Sa + Da − Sa·Da` and the proper separable color formula. The
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
  reads and answers with a bilinear sample; a Sobel reads a *neighbourhood* and answers with a color. What the two do
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
one is `insetHaloed` with a null radius, and a **centered** one is both. The dilation each of those performs *is* the
stroke once nothing softens it. Four things:
- **Inward first for the centered case, and the order is load-bearing.** `insetHaloed` trims its band to the artwork,
  so it changes no alpha — which leaves the silhouette `haloed` then grows outward still the *artwork's* own edge.
  The other way round the outward band fattens the silhouette first and the inward one is measured from the stroke's
  edge, putting the whole thing a width too far out.
- **`perSideWidth` halves the total for a centered stroke, in the model** on `Vignette.clearArea`'s grounds. `width`
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
  a filled buffer, leaving `dstAlpha × (1 − srcAlpha)` — the complement, in two canvas calls. A color matrix would
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
  real light does to a bump and a dent. It is labeled **"Inset"** in the studio, on `ProgressiveBlur`/"Focus"'s
  precedent that four columns is one short word.

**`LayerEffect.InnerGlow` is that one's twin, and where the inner halo became one function.** Light gathering along
the inside of the edge — the complement blurred and trimmed as a recess is, then **screened** onto the artwork rather
than laid over it, so it brightens the colors already there instead of covering them with a band. Two effects rather
than one on `Glow`/`Shadow`'s precedent, and the parameters differ the same way: a recess is thrown so it has an
offset, a rim is centered on the edge it lights so it has none. Three things:
- **`IconRenderer.insetHaloed` is the extraction the second consumer earned**, and the two differ in exactly two
  arguments (the offset, and the blend). Everything between the complement and the trim is identical, which is
  precisely the near-copy that drifts when written twice.
- **The trim moved into the halo's own buffer, and that is what made one function possible.** The first cut leaned on
  source-atop to clip *and* composite at once — correct for a shadow, impossible for anything that adds light, since
  the mode is then spent. Destination-in first, any mode after.
- **No "edge or center" toggle**, dropped from the proposal and confirmed by building it: a glow radiating from the
  middle of the artwork is `Bloom(falloff = RADIAL, anchor = CONTENT)`, already built and additionally offering a
  position and a falloff this could not. Labeled **"Rim"**, on "Inset"'s precedent — light along an inside edge is a
  rim, and that names the look rather than the mechanism.

**`LayerEffect.Ripple` is the first *per-pixel* effect, and the first that leaves the canvas entirely.** Concentric
waves push each output pixel to read from somewhere else along its own radius — arithmetic over an `IntArray`, which
the bake does at any API and Compose needs AGSL and API 33 for. Four things:
- **The plan grouped it with Pixelate and Grain as "one loop with three answers", and that is two-thirds right.**
  Ripple and Grain are resamplings; **Pixelate is not** — as the reference draws it the cells have gaps and rounded
  corners, so it *redraws* the layer as a field of shapes with one color sampled per cell. A coordinate-quantizing
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
    is the one optimization that also speeds up **baking real icons**, where a shader would only ever have helped
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
  size instead. `LayerGrain.latticeAt` samples the **center** (offset half a *pixel*, not half a cell, since the
  correction is about where a pixel is), which removes the coincidence and lets the floor be **two pixels** rather
  than four. Two rather than one because at a one-pixel cell every sample sits at the center of its own cell, so
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
- Labeled **"Focus"** in the panel, since what a user is choosing is what stays in focus — the blur is how that is
  expressed. It is also the one name that would not fit a tile at four columns.

**Persistence — one serialized blob, NOT flat columns. Done.** (L1 burned four destructive DB bumps learning
this.) `icon_override` is `component` + a JSON `appearance` blob, and the global default is a `data:settings` slice
under `icon_appearance`. Editing an app **snapshots the default and detaches** (Reset re-attaches) — no
field-merge, no variable-length-list diffing.

**The stored unit is `IconAppearance`, not `IconLayerSet` — recipe + plate + zoom** (**DB v4 → v5**, destructive,
free pre-launch; the settings key changed with it). What forced the widening is one control: the finalize step
offers *"save as preset"* underneath the plate switch, so a preset carrying only the layer set would save half of
what the user was looking at — and once a preset holds a plate, so must every store a preset can be loaded into or
saved from. See the plate note below for why the plate cannot be part of the *recipe*.
- **The column and the key both changed name rather than being re-interpreted in place.** A stored recipe read
  back as an appearance decodes into one with **no layers** — every field unknown, so `ignoreUnknownKeys` drops
  them all — which is silent and total. Renaming makes it a reset instead, and this module's own rule already
  says the key name is the seam for a semantic break.
- **An untouched appearance encodes to `{}`.** `layerSet` has a default of its own, so `encodeDefaults = false`
  omits the recipe along with the plate and the zoom — widening the stored unit cost every un-plated icon on the
  device nothing at all. Pinned both ways (`{}` for `Base`, `{"plate":{…}}` for a plate-only edit), and it came
  out of a test written to assert the *old* JSON and failing.

Five things worth knowing:
- **The model lives in `core:model.icon`**, not `core:icon` — it is pure data describing what an icon should
  look like, where turning that into pixels is the renderer's job. Third cut of the same kind after
  `BackdropEffect` and `DeviceConfiguration`, and what forces it is that *two* modules store a recipe and neither
  should depend on a module that allocates bitmaps. `IconShapes` stays behind (it maps ids to `R.drawable`), and
  the move took the serialization plugin out of `core:icon` entirely.
- **`IconAppearance` is a data holder and `IconId` keys on the recipe alone**, which is what keeps the bake cache
  correct: the plate has no place in a key that has no screen position in it.
- **An unreadable row is skipped, not deleted.** It falls back to the global default, which is visible and
  fixable; deleting would throw away a recipe a later build could read. Same position `data:layout` takes on an
  unresolvable placement. Two things reach that path — a corrupt blob, and a well-formed one describing an
  *illegal* stack, which `IconLayerSet`'s own `init` rejects.
- **Adding an effect is not a schema change**, which is the whole point of the sealed list: the spec gained
  `opacity` and `blend` and the test asserting the exact stored JSON of `IconLayerSet.Base` still passes, because
  defaults are not encoded.

**The plate is built, and it is drawn *live* — the "skin" this file has carried as deferred since B3.**
`IconPlate` (enabled + an `IconShape`) is a silhouette of blurred wallpaper sitting **behind** the artwork, drawn by
`Modifier.wallpaperBackdrop` — so what it shows depends on **where the icon is**, and two cells showing the same app
show different pixels. That is the whole reason it is not a layer: a layer is baked, keyed by
`IconId(component, layerSet, sizePx)` with no position in it, which is exactly what makes that cache shareable. L1
kept its skin as a separate live Compose layer for the same reason and this file predicted the split ("distinct from
the baked stack"). Seven things:
- **`AppIcon` (`core:designsystem/cell`) is the seam every surface goes through now, not `LauncherIcon`.** The
  primitive stays a component-plus-recipe in, one bitmap out; the plate and the zoom are the *cell's*, one layer out,
  because `wallpaperBackdrop` lives in `core:designsystem` and a bake cannot hold a position. One place, so a new
  surface cannot draw an icon and forget its glass — `AppCell`, `AppRowCell`, `CategoryCardFace`, `IconPreviewPlate`
  and both container sites all call it.
- **It is masked with the renderer's own silhouette** — `Modifier.shapeMask`, moved out of `IconLayerStack` into its
  own file and made public at its third consumer, the first in another module. The alternative was a Compose `Shape`
  catalog for plates beside the vector one for icons, and two lists meant to look identical are two lists that will
  not: a plate cut to a squircle in front of an icon cut to a *slightly* different squircle is the kind of wrong
  nobody can point at.
- **No refraction on a plate**, which is the full-screen film's reasoning from the other end: liquid glass's rim is a
  rounded-rect SDF, so masked to a hexagon or a teardrop it traces an outline the plate has not got. What it renders
  instead is the blur plus `BackdropEffect.saturation`, at every API.
- **With no wallpaper to sample it draws a scrim** (`surface` at 45%) rather than nothing, so a switch someone just
  turned on says it did something. Same fallback every frosted surface has.
- **`zoom` scales the artwork inside its box and not the plate**, unclipped, and that is the job `IconSizing`
  structurally cannot do: an icon at 1f fills its box and so touches the plate's edge everywhere. A fraction for the
  reason every offset in the layer model is one — the same recipe at every bake size. Unclipped because an icon's own
  glow is meant to escape its box, so above 1 it spills, visibly, while it is being set.
- **The cost is paid only when a plate is on**: one backdrop node and one offscreen mask layer per icon, per frame.
  Real on a dense grid, and nothing at all for an appearance with no plate.
- **It is absent on a surface that is already frosted** — `LocalOverFrost`, which is APPS and any open collection. A
  plate is a piece *of* the wallpaper, and on a sheet of blurred wallpaper there is nothing for it to be a piece of:
  it renders as a sharper patch floating on the frost. Dropped outright rather than flattened to its scrim, which
  would be a gray blob behind every icon in the drawer — worse than a plate the user turned on not showing on the one
  surface that cannot host it. The plate stays on HOME, which is where an icon sits on the picture directly. See
  docs/DESIGN_SYSTEM.md → "The frosted backdrop and the full-screen frost".
  - **The zoom goes with it, and that half is the one that fails silently.** `zoom` is the artwork's size *relative
    to its plate*, so replaying it where no plate is drawn leaves an icon merely smaller than the ones beside it, for
    a reason nothing on screen shows — the plate's absence is visible, a 0.8 artwork in a full-size box is not. So a
    suppressed plate resolves the zoom to 1f and the icon fills its box, exactly as an un-plated one does. **Only
    when *this* surface suppressed it**: a zoom stored against a plate the user switched *off* is a plain size choice
    and stands wherever it is drawn, which is why the condition is the suppression rather than the absence.

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
over a bounded LRU. **Deferred:** drawables the author mapped to no app, and `drawable.xml`'s categories. (Shadows
and the skin/backing-plate were both here and are both built — the shadows with the bake-backed preview, the skin as
the **plate** above.)

**Presets are a named `IconAppearance`** — a whole look plus a name, no separate format, and stored as a
`data:settings` slice rather than a Room table because a library is a handful of documents read whole, where
per-app overrides are a row per customized app read one at a time. A preset is a **copy, not a link**: loading is an
ordinary undoable edit and deleting touches nothing it was applied to. Built-in curated presets stay out, being a
content decision rather than an engineering one.

**A library of looks is a library of pictures, not a list of names** — a grid of rounded squares, each drawing its
own recipe on a real installed app, in all three places the library appears. A preset *is* a look, so a list of
words was the one thing it could not be: two recipes differing in a bloom's angle read as two identical rows.
Rendering one costs almost nothing, since `AppIcon`/`LauncherIcon` take an explicit appearance and go through
`IconRenderManager` — a tile is one bake, on the same cache key every icon on the device already uses. Six things:
- **Tapping a tile in the Icons pane *applies* it, which reverses this section's own earlier rule** ("applying one
  is opening the studio loaded with it, never a write"). The tile's preview is what pays for it: the look is on
  screen before the finger lands, so "look before you restyle every icon" happens by reading rather than by
  navigating. The studio is still one tap away as **Edit** in the tile's menu, by exactly the route the old tap
  took.
- **The applied preset carries a ring**, compared by *value* — so it marks a look re-created in the studio as well
  as one that was tapped. Without it a tap changes every inheriting icon on the device and the pane shows nothing
  at all. There is no undo: the presets slice keeps no history, which is stated on `IconsViewModel.apply`.
- **The menu opens two ways** — a three-dot button *and* long-press, one menu with one verb list. The button
  because a settings pane teaches no gestures and Edit/Delete would otherwise be unreachable; the long-press
  because that is what every other menu in this launcher uses and it must not be wrong here.
- **The studio's own panel is the same tiles**, drawn through `IconPreview` so a preset the live path cannot draw
  previews from its bake exactly as it will on a surface. "Edit specific apps" is select-to-apply with **no menu at
  all** — absent, not disabled, because none of its verbs could ever become legal where the library is read-only.
  "Edit all icons" gets long-press → Rename / Delete, matching the layer rail's tap-selects/long-press-menus split.
- **That panel's tile menu draws *in the tile*, not in a `Popup`.** The panel is already floating glass, so a popup
  there is a window over a window sampling neither — and two verbs need no positioner, where the rail's menu
  machine exists because it has six rows and has to flip about an edge. Rename reuses the panel's existing name row
  rather than putting a field in the grid, which would reflow the grid under the finger that opened it; the tile
  being renamed carries a ring so the row is not editing an anonymous name.
- **`IconPresets.renamed` is position-preserving**, which is why it is an operation rather than a `without` plus a
  `with`. The name is a preset's identity, so a rename really is a delete and an insert — and `with` appends, so
  spelled that way correcting a typo would send the tile to the end of the library. Renaming onto a name already in
  use is an overwrite the user asked for by typing it, never two rows nothing can tell apart.
- **The studio's preset tiles deliberately do not draw the plate**, where the Icons pane's do: that canvas is not
  the wallpaper, so glass has nothing honest to show there.

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
- **Committing is explicit in both modes**, departing from L1's live-committing global studio: a slice is one
  JSON blob, so a live-committing slider rewrites the whole document per frame, and a global edit restyles every
  icon on the device — not a thing to do continuously while someone is still deciding. The *preview* is live either
  way, which is all "live edit is non-negotiable" ever meant. **Where the commit lives moved**: the tick is gone and
  Apply is on the finalize step — see below.
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

**The session has a last page — `StudioStep.FINALIZE` — and the tick is gone.** A forward pill leads to a step
showing **every icon the session is about to change, over the real wallpaper**, with the settings that belong to the
whole icon rather than to a layer (the plate, its shape, the zoom) and the two things you can do with them: keep the
look as a preset, or apply it. Eight things:
- **A step, not a destination.** The recipe being edited lives in the studio's own `ViewModel`, and a second
  `NavEntry` gets its own `ViewModelStore` — so reaching it across a navigation boundary would mean passing a whole
  `IconAppearance` as a nav argument, or scoping the ViewModel to the Activity. `StudioStep` says what is true
  instead: one editing session with a last page, where back is a step back and neither direction commits or discards.
- **It paints no background, and that is the whole trick.** The window carries `Theme.Wallpaper`, so a screen that
  paints nothing *is* the wallpaper — no punch-through, because a punch cuts a hole in something opaque and the
  studio's canvas is simply not drawn on this step. Which is also why the step has to exist: that canvas is
  deliberately not the wallpaper, so it is the one place a silhouette of blurred wallpaper cannot be judged.
- **The previews go through `AppIcon` with an explicit appearance**, so they are the same composable and the same
  bake cache every surface uses. No second render path to disagree with the home screen.
- **Who is listed is "apps that inherit", not "apps installed".** An app with a recipe of its own is detached and a
  global edit passes it by, so listing it would misstate what Apply is about to do. The individual route lists the
  one app it is editing — as a *list*, because that route is meant to gain a multi-app picker and this screen is
  already written against *the apps about to change* rather than against a subject.
- **Apply carries the signal the tick used to.** The pill is always enabled — a session with nothing changed still
  has somewhere to go, since this is also where a preset is saved and where the plate is switched on — and "is there
  anything to write?" moved onto the button that actually writes. `saved` widened to a whole `IconAppearance` for it:
  it held the layer set alone while the plate and the zoom merely rode along, which was correct then and wrong the
  moment a control could change one, since a plated icon would have read as clean.
- **The plate's wallpaper is provided here, and the scrim is what proved it necessary.** `LocalBackdrop` belongs to
  the shell and this is a destination beyond it, so every plate on this step drew its *scrim* — a flat gray square,
  on the one screen that exists to judge glass. The studio reads the panel-strength picture and the accent itself
  now; that is a **third** reader of the same repository question, and a fourth belongs beside `ProvideIconRecipes`
  in `app`, which is already where a launcher-wide read is assembled.
- **The previews and the panel are laid out together, never one padded around the other.** The first cut floated the
  panel and gave the grid a constant bottom padding to clear it — a number that has to be right at every screen size
  and was wrong at the first one it met: rotated, the panel was taller than the viewport, so it covered the previews
  it explains and its own buttons had nowhere to go. The shape grid is `ShapePage` (now `internal`, so a plate's
  silhouettes and a layer's come from one list) inside a **width cap**, for the effect grid's reason — equal shares
  of a wide panel are four huge squares. **Landscape is not arranged for**: cramped rather than broken, which is the
  honest state of a posture nobody has designed.
- **Turning the plate on seeds a rounded square**, because the model's default is no shape at all — right for a
  stored recipe, which is what every one of them was written against, and wrong for a control someone just switched
  on. `ContentAnchor`'s split, one screen over. And the three whole-icon controls are **not in history**: one tap or
  one drag each with the result on screen across every icon, and undo would have to step back through them from the
  editor, where the plate is not visible at all.

