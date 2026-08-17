# Icon effects — expansion plan

Drawn from captures of another icon studio (`~/Downloads/effect from other icon studio app` — twelve files, named by
hash rather than by effect; the thirteenth, drop shadow, was never captured). This plan is **what each one actually
needs from our two renderers**, what has to change before any of them can land, and the order to build them in.

Status: **every slice done — all thirteen effects are built.** Where the
build diverged from this plan, §5 and §7 record it — the plan is kept as written so the reasoning that was wrong stays
visible next to what replaced it.

**The largest thing this plan got wrong is in §3**: it treats all thirteen as *layer* effects, and six of them are only
correct over the finished composite. See §5's whole-icon note.

---

## 1. What the captures show

Each panel in the reference has the same anatomy: a back chevron, the effect's name, a **variant** control
(2–3 segments, or a dropdown), sometimes a **color**, and a master **on/off switch** — then sliders, each with a
glyph, a numeric readout and its **own reset button**.

| Reference name | Ours | Parameters |
|---|---|---|
| Phối cảnh | **Perspective** | rotate-horizontal, rotate-vertical; 2 variants |
| Sáng chói | **Glow** | radius, spread X, spread Y, opacity, **color**; 2 variants |
| Pixelate | **Pixelate** | grid size, pixel size, corner radius; 3 variants (cell shape) |
| Quang sai | **Chromatic split** | offset X, offset Y; 2 variants |
| Grain displacement | **Grain** | intensity, grain size, directionality, angle; 2 variants |
| Progressive blur | **Progressive blur** | radius, sharp area, softness, position X/Y; radial \| linear |
| Ripple | **Ripple** | intensity, waves, position X/Y; 2 variants |
| Làm nổi cạnh | **Extrude** | height, intensity; 2 variants |
| Bóng mờ | **Bloom** | angle, radius, intensity; radial \| linear |
| Hoa văn | **Pattern** | pattern (6 tiles), scale, angle, opacity, **color**, randomize, invert; 2 variants |
| Bóng sáng | **Gloss** | angle, radius (signed), intensity; 3 variants |
| Các Bộ lọc | **Filters** | category (Cinema / Polychromatic / Mono / Enhance / Invert / Retro Tech) → named filter |
| — | **Drop shadow** | already deferred, belongs with Glow |

Two conveniences worth stealing regardless of which effects land: the **per-slider reset**, and the **numeric
readout as a field** rather than baked into the label the way ours is (`"Hue  180°"`).

---

## 2. The constraint that decides everything

**Two renderers, and `minSdk = 26`.** `IconRenderer` composites to a bitmap with an Android `Canvas`;
`IconLayerStack` draws the same layers live as Compose nodes so a slider responds per frame. Every effect has to be
drawable by *both*, or the studio lies about what the home screen will show — which is the bug the editor
structurally cannot reveal.

What each path can reach:

| | bake (`Canvas`, software bitmap) | live (Compose) |
|---|---|---|
| colour matrix | yes | yes |
| matrix / camera transform | yes | yes |
| tiled shader, gradients | yes | yes |
| **blur** | yes — `BlurMaskFilter`, and `Blur.kt`'s box blur | **API 31+** (`RenderEffect`) |
| **per-pixel** | yes — on the `IntArray` | **API 33+** (AGSL `RuntimeShader`) |

**Only the *live* column has restrictions.** The bake draws into a software bitmap it owns, so a blur is a
`BlurMaskFilter` or a box blur and a per-pixel effect is arithmetic over an `IntArray` — neither has an API floor.
Every one of the thirteen can be baked on API 26. That is the fact the plan turns on.

### The decision: the bake backs the preview, so nothing is gated

Gating the blur and per-pixel effects to API 31/33 was considered and **rejected**: it would deny six effects —
including drop shadow and glow, the two most-wanted — to every device below Android 12, to solve a problem only the
*editor* has. The home screen could have drawn them all along.

So: **when a layer carries an effect the live path cannot draw, the studio previews that layer from its baked
bitmap.** One mechanism, every API, no feature gated, and the bake becomes the single source of truth for exactly
the effects where two implementations would have been hardest to keep honest.

The cost is latency, and it is paid three ways rather than with a spinner:

- **Downscale while a gesture is in flight.** A studio preview is ~800px square; the heavy effects are O(n) in
  pixels, so baking at a quarter of that is ~16× less work and lands in single-digit milliseconds. Full size on
  release — which is `onValueChangeFinished`, the punctuation the studio already has.
- **Throttle, and never queue.** A drag emits far more frames than any bake can service; the preview should
  re-bake on a trailing interval and drop what it cannot keep up with, so the picture updates several times a
  second instead of falling behind by seconds.
- **A "working" hint only when a bake actually runs long**, as a fallback for the worst case.

**Not a spinner during the drag.** Freezing the preview while a slider moves is the one thing that cannot be
allowed: the whole reason the value is a slider is that you judge it by watching. A slightly stale picture that
keeps moving is far better than a correct picture that arrives after you let go.

### Settled: incremental, not collapse

The live path stays for the seven tier-1 effects; the bake backs the preview only for the layers it cannot draw.
`IconLayerStack` is not deleted.

The bill is that the studio now has **two preview mechanisms**, and the divergence risk the six shared derivations
exist to contain grows with every effect added. Two things keep that honest and both are cheap:

- **The choice is a property of the effect, not of the layer or the device** — one `drawsLive: Boolean` on each
  effect variant, so "can the live path draw this?" has exactly one answer and adding an effect forces it to be
  stated. A layer falls back if *any* of its effects says no.
- **The `IconLayers` dev-harness playground already draws one set both ways side by side**, which is the only
  pixel comparison this project has. Every tier-1 effect should land with its case added there.

Collapsing to a single renderer stays the fallback position if that divergence ever bites: slice 7 builds most of
what a collapse would need anyway, so the option is kept open rather than spent.

---

## 3. The thirteen, by what they need

### Tier 1 — buildable today, no new render machinery (7 of 13)

Both paths can already draw these on API 26 with what is in the file.

- **Filters** — a table of colour matrices, `LayerEffect.Filter(id)` in the model and id → matrix in `core:icon`.
  See §3a for why this is engineering rather than content. *Cheapest large win in the set.*
- **Bloom** — a radial or linear gradient laid over the layer. `LayerEffect.Gradient` is most of it already; it
  needs a radial variant and to keep its source-atop compositing.
- **Gloss** — a gradient through a shape, source-atop. Same machinery as Bloom with a signed radius bending the
  sweep.
- **Perspective** — Compose has `rotationX`/`rotationY`/`cameraDistance` on `graphicsLayer`; Android has
  `android.graphics.Camera` + `Matrix`. Both since forever. Fits `LayerTransform`'s existing job of being the one
  interpretation of where a layer sits.
- **Extrude** — N offset draws of the layer silhouette behind itself, darkened. No blur.
- **Chromatic split** — the layer drawn three times through channel-isolating colour matrices at offsets. Pure
  `ColorMatrix`, which both paths already share.
- **Pattern** — a tiled bitmap drawn source-atop. `BitmapShader(REPEAT)` on Android, `ShaderBrush` on Compose.
  Needs pattern assets, modelled like `IconShapes`.

### Tier 2 — blur (3)

- **Glow**, **Progressive blur**, **Drop shadow** (the standing deferral).

The bake does all three on any API — `BlurMaskFilter` is the simplest route for glow and shadow and needs no bitmap
processing at all. The studio previews them from the bake.

### Tier 3 — per-pixel (3)

- **Pixelate**, **Ripple**, **Grain**.

All three are displacement or resampling over the layer's pixels; the bake does them on the `IntArray` it already
holds, again on any API. Previewed from the bake.

**Neither tier is API-gated, and neither should be implemented twice.** Writing an AGSL version for the live path
above 33 would not just be extra work — it would be a *different algorithm* producing a different picture from the
bake on the same device, which is the standing hazard in a worse form than it has ever taken. One implementation,
in the bake.

---

## 3a. Filters are engineering, not content

The question this answers: CLAUDE.md deliberately keeps **built-in curated presets out**, "being a content decision
rather than an engineering one". A shipped list of named filters — Vintage, Autumn, Pop — looks like exactly the
same thing, so it needs the same test applied rather than assumed.

It comes out the other way, and the difference is what the thing *is*:

- A **preset** is a whole recipe — layers, sources, transforms, shapes, effects. It is open-ended, and whether one
  is any good depends on the artwork it lands on, so curating them is design work with no end and no right answer.
  That is why they stay out.
- A **filter** is one 4×5 colour matrix. It is bounded, self-contained, and does the same thing to every icon.
  "Curating" it means choosing twenty numbers and a name — which is the same act as adding a value to
  `LayerBlend`, or dropping a vector into `IconShapes`.

So filters are a **fixed vocabulary**, and they take `IconShapes`' exact shape:

- `LayerEffect.Filter(id: String)` in `core:model.icon`. The **id is the on-disk contract**; a stored recipe holds
  only that.
- The id → matrix table lives in **`core:icon`**, beside `LayerFilter`, which is the module that already owns what
  a colour matrix means. Unlike `IconShapes` it needs no `R.drawable`, so it could sit in the model — it does not,
  because a matrix is a *look*, and `core:model` holds what an icon is rather than what it looks like.
- An **unknown id renders unfiltered**, matching `IconShapes.drawableResOrNull` returning null: a stale recipe from
  a later build degrades instead of failing.
- The reference's two-level **category → filter** browse is presentation. A `category` field on each entry is
  enough; it is not a second concept.

Two consequences worth stating now:

- **The names must be ours.** "Tarantino" is a person; several of the reference's others are borrowed the same way.
  A filter's name is shipped, on-disk and user-visible, so it should be descriptive rather than a reference.
- **This does not open the door to user-defined filters.** If that is ever wanted it is a `data:settings` slice
  exactly like presets, and it composes with this rather than replacing it — the built-ins stay a table.

---

## 4. Four things that must change first

These are prerequisites, not effects, and each is small next to the payload it unblocks.

**1. The effect list must become a pipeline.** Today `IconLayerSpec.color` and `.gradient` are accessors that pick
the first of each type, and both renderers apply them in a hardcoded sequence — so `effects` is a bag whose order
means nothing. With thirteen effects the order *is* the result (a pattern over a glow is not a glow over a
pattern), so both renderers must iterate the list and apply each in turn. This is the single biggest change here
and it touches the one thing the two paths must agree on.

**2. An effect needs an explicit `enabled`.** Our current rule is that an identity effect is removed from the list,
which is elegant for two sliders and wrong for an effect with five parameters and a colour: the reference's master
toggle exists so you can switch an effect off *without losing what you set*. Proposal: `enabled: Boolean = true` on
each variant, with "absent from the list" meaning never configured. Costs nothing stored (`encodeDefaults = false`).

**3. Variants and colours are part of the effect.** Most of these carry a 2–3 way variant and several carry a
colour. Both are ordinary fields; the point is to decide the vocabulary once (an enum per effect, like `TintMode`)
rather than thirteen ad-hoc booleans.

**4. The Effects grid outgrows one page.** Five entries fit at 4 columns; thirteen-plus do not. The shape section
already answers this — a pager of grids, which keeps the section a fixed height however long the list gets and is
the arrangement that avoids a scroller inside the panel's own scroller. Two more things belong with it:

- **A switch in each effect's panel header**, so an effect can be turned off without losing what was set. That is
  what prerequisite 2's `enabled` is for, and `MorphicSwitch` now exists to draw it.
- **The per-slider reset**, and the numeric readout as a field rather than baked into the label the way ours is
  (`"Hue  180°"`). Both go in `SteppedSlider`, since every one of these thirteen panels wants them.

---

## 4a. The layer rail — and the Layers section goes away

The studio's own `StudioToolPanel` already admits the problem: *"The header names the selected layer, not just the
tool, and that is the one thing the bar cost us. While the stack was permanently on screen, 'which layer am I
editing?' was answered by looking at it; now the stack is behind its own entry."* A rail puts the stack back on
screen permanently, which is the whole point.

**Shape.** The preview icon shrinks and moves off-centre, freeing a strip down one edge of the canvas. The strip
holds one tile per layer — the layer's own content, thumbnailed — plus a `+` at the end. **Tap selects. Long-press
opens a quick menu** (move up, move down, toggle visibility, delete).

**The Layers section is deleted from the tool bar, and the rail owns layers entirely.** That is the call, and it
follows from what would be left rather than from taste: if the rail does select, reorder, visibility and delete,
the section's only remaining job is *add*, and a section that is one button is not a section. Keeping both would
also put layer management in two places at once, which is precisely the split this codebase spends its rules
avoiding. The bar drops 7 entries to 6, and every remaining one acts on the selected layer — which is what
`StudioTool`'s KDoc already claims it is for.

Five things to get right, each of which is a rule this codebase already holds somewhere else:

- **The `+` goes at the end of the rail**, so add is where the layers are. Nothing else moves out of the panel.
- **A disabled menu row, not a refused action.** The reorder buttons exist as buttons rather than a drag precisely
  because *a disabled button says which move is illegal before it is attempted*. Long-press rows must disable the
  same way — `editing.moveUp(i) !== editing` — or the rule that fg stays above bg becomes something you discover
  by failing.
- **Drawn top layer first**, matching the current stack list. That reversal is load-bearing beyond the UI:
  `IconStudioViewModel.removeSelected` moves the selection *down* an index to keep the highlight on the same row,
  and only makes sense while the rows are drawn this way round.
- **The rail sits on the *end* edge**, not "the right" — RTL-aware, for `SideZoneEdge`'s own reason. It must also
  stop clear of the tool panel, which grows to 320dp from the bottom.
- **Thumbnails need a plate behind them.** A layer is often a dark glyph on transparency; on the studio's dark
  glass that is an empty tile. The canvas already solves this for the icon (black / white / checkerboard) and a
  tile needs the same neutral ground. They are baked at ~40px, so they are cheap — and they are the first consumer
  of a *per-layer* bake, which slice 7 needs anyway.

**What this does not change:** the effect list stays in the Effects panel, as a 4-column grid, and a new effect is
one more tile in it.

---

## 5. Suggested order

Each slice is independently reviewable and leaves the studio working.

| # | Slice | Why here | |
|---|---|---|---|
| 0 | Effects list → ordered pipeline; `enabled` + `drawsLive` on variants | Nothing else is safe until draw order is real | **done** |
| 1 | Effect panel: grid paging, per-effect switch, slider reset + numeric field | The container for everything below | **done** |
| 2 | **Filters** | Largest visible gain, tier 1, no new machinery | **done** |
| 3 | **Layer rail**; delete the Layers section | Independent of the effects; do it once the bar is about to get busy | **done** |
| 4 | **Bloom** + **Gloss** | Reuse the gradient path; retire the "gradient" entry into them | **done** |
| 4a | **Whole-icon effects** | Not in the plan — see below | **done** |
| 5 | **Perspective** | Extends `LayerTransform`, which is already shared | **done** |
| 6 | **Pattern** (+ its own assets) | Tier 1, needs an asset pipeline of its own — see §6 | **done** |
| 7 | **Extrude** + **Chromatic split** | Tier 1 finishers | **done** |
| 8 | **Bake-backed preview** (downscale + throttle) | Unblocks everything left, on every API | **done** |
| 9 | **Glow** + **Drop shadow** | Retires the standing deferral | **done** |
| 10 | **Pixelate**, **Ripple**, **Grain** | Per-pixel, on the baked preview | **done** |
| 11 | **Progressive blur** | Hardest: blur *and* a mask | **done** |

### What the built slices settled that this plan did not

- **The entry list is one-per-`LayerEffect`, which cost a merge.** Slice 1 split `LayerEffect.Color` into *Recolor*
  and *Tint* on the grid, and the per-effect switch overturned it within the same slice: `enabled` belongs to the
  effect, so two entries sharing one record can express "tint off, recolor on" — a state the model cannot hold.
  Splitting `Color` in the *model* instead is worse, since its four numbers compose into a single matrix in a fixed
  sequence. The rule that came out of it: **an entry owning a `LayerEffect` gets a switch; one configuring a spec
  field (opacity, blend) does not.**
- **Effects are two *kinds*, and the difference is a buffer.** An overlay paints onto what is there; a filter
  transforms pixels already drawn, which a canvas cannot do in place — one bitmap in the bake, one `saveLayer`
  live. Every effect from here declares which it is. Slice 2's filters are the second member of the filter kind, so
  they cost one `when` arm each rather than a mechanism.
- **`ColorMatrices` came out of `LayerFilter`.** Authoring seventeen looks as raw `floatArrayOf` is unreviewable, so
  the builders were extracted and the table composes from them; `LayerFilter` kept the one thing that is about the
  four sliders, which is the order they compose in. `contrast` and `mix` are new — the first pivots about mid-grey
  (without the offset it is a brightness control that also steepens), the second is what a true sepia needs and what
  `scale` structurally cannot express.
- **A filter swatch shows the *look*, not the icon** — a fixed reference gradient under the filter's matrix.
  Previewing on the real icon is seventeen bakes, and an icon that happens to be black says nothing about a warm
  grade.
- **The rail forced a second Haze source**, which this plan did not foresee. Haze samples what is behind a node, so
  one shared state has the rail sampling itself *and* the panel sampling a rail with nothing behind it. `canvasHaze`
  is the work alone (the rail's own glass reads it); `screenHaze` is the work and the rail (everything above reads
  it). A node can register with both, so the canvas simply carries two.
- **Three things moved out of the rail's way**, none of them predicted: the icon bound shifts toward the start
  (`IconBoundShift`), the session buttons above the panel move to the leading end, and the tool bar wraps its
  contents rather than filling the width — at six entries a full-width bar ran edge to edge with a margin of
  nothing.
- **Still open from slice 1:** the numeric readout is a *readout*, not an editable field. Typing an exact value is a
  text field per slider with parse, clamp and commit semantics, which is its own slice.
- **Slice 4 split, and half of it was a capability this plan never noticed.** Bloom landed first: `LayerEffect.Gradient`
  renamed and grown (linear or radial falloff, a position, a `ContentAnchor` — which is what that second consumer
  renamed `ShapeAnchor` to), and **one colour fading to transparent**
  rather than two opaque stops — with two, source-atop *replaces* every pixel it covers, so a bloom at full strength
  obliterated the artwork it was meant to light.
- **Gloss is an *edge*, which is what makes it its own effect rather than a bloom preset.** A bloom is a ramp or a disc
  — light with no boundary; a gloss has a lit region, an unlit one, and an arc between them, which neither of a bloom's
  falloffs can produce. It is still the same radial fill, with the disc pushed **outside** the frame so only its rim
  lands on the artwork: the whole of "signed radius bending the sweep" is how big that disc is.
  - **One signed slider, doing two things on purpose.** `curve`'s magnitude is how tightly the edge bows (0 is very
    nearly straight); its **sign** is which way — the lit region bulging out, or the arc cutting into it. The light
    stays on the side the angle names either way, so the sign can never be mistaken for a 180° turn, which is the test
    that kept it from being a second angle control.
  - **Four stops, not two**, and it is load-bearing: with a two-stop ramp spanning the whole radius, a large disc
    leaves the frame in an almost flat part of it, so flattening the curve would fade the sheen away — a control
    undoing itself. The stops are placed so the boundary lands on the frame's centre and the soft band is a constant
    share of the frame at every curve. Pinned by a test.
  - **No position pad**, unlike Bloom: a sheen is placed by the direction it is struck from and the way its edge bows,
    and a third control moving the same band would be a second answer to what the angle settles.
- **Perspective is *not* an effect, and it cost the live path its `graphicsLayer`.** §3 says it "fits `LayerTransform`'s
  existing job", which is right and is why it landed as two `IconLayerSpec` fields (`tiltX`/`tiltY`) rather than a
  `LayerEffect`: leaning a layer out of the plane says *where it sits*, so as an effect one rotation would be orderable
  against a colour matrix while the in-plane one was not.
  - **What §3 got wrong is "no new render machinery".** Compose expresses perspective as `graphicsLayer.cameraDistance`
    and the bake as `android.graphics.Camera`, and **the two use different units** — Camera's z is in 72-pixel units,
    Compose's is a density-scaled dp. Matching two camera models by eye is exactly the agreement `LayerTransform`
    exists to make unnecessary, so instead the live path stopped reading the transform's *fields* into a
    `graphicsLayer` and now takes the same `Matrix` the bake takes. One derivation, no unit question, and the shared
    thing got stronger rather than a seventh unverifiable one being added.
  - Side effect worth having: content is now drawn *through* the matrix rather than rasterized and then transformed,
    so a zoomed vector drawable re-rasterizes at its final scale instead of being stretched from a texture.
  - The camera depth is **a multiple of the box** (2.5×), for the same reason offsets are fractions: a constant pixel
    depth would make one recipe read as mild at 96px and violent at 288px.
  - Not offered on the **composite** — `StudioTool.appliesTo` gives it no Transform panel — so tilting a whole icon
    means tilting its layers. That is a real gap rather than a decision, and the place to fix it is whether the
    composite gets a transform of its own.
- **Pattern confirmed §6's split and added one the plan had not stated: the tile is a *stencil*.** Its marks are
  authored white on transparent and the effect's `argb` is what they come out in — so one asset serves every colour,
  and `invert` is a `DST_OUT` punch rather than a second library. A tile carrying its own colours would need both.
  - **`LayerPattern` is a seventh shared derivation**, and a tiled shader earns it: there are *three* things the two
    paths must agree on and each is invisible alone — the tile's pixel size, the matrix that turns it, and how the
    stencil becomes coloured marks. It hands back a **bitmap** rather than a shader, because that is the last point
    they can share: one wraps it in `BitmapShader`, the other in Compose's `ImageShader`.
  - **Every asset is authored to repeat**, which is the part with no compiler behind it. A mark crossing an edge is
    drawn again on the opposite one, or drawn whole and centred *on* the edge so the drawable clips it and the
    neighbour completes it (the dots do this at all four corners). A mistake shows as a seam every tile, which reads
    as a rendering fault rather than a bad asset.
  - **No *randomize* button**, unlike the reference. What it randomizes there cannot be read off a capture — an
    angle, an offset, a per-tile scatter — and a button writing a random number into a slider the user can drag is a
    novelty rather than a control.
  - **No `ContentAnchor`**, unlike Bloom and Gloss: a pattern is a texture laid over the icon and its own angle already
    orients it. Additive if wanted.
- **Extrude is the first effect whose live cost scales with a slider**, and §3's "N offset draws of the layer
  silhouette" understates what that means on the live side. The bake blits a bitmap it already holds; the editor
  re-runs the layer's *content* per copy, per frame, at preview size. So `LayerExtrude` caps the count (48) and grows
  the per-step offset to compensate — the slab reaches the depth asked for whatever the cap does to its smoothness,
  which is the half a fixed step size would get wrong and nobody would attribute to a step limit.
  - **It is the first candidate for `drawsLive = false`** if it proves slow on device. Left true only because the
    bake-backed preview (slice 8) is not built, which is exactly the situation that flag was added for.
  - **`ColorMatrices.solid` got a second consumer**, so `LayerFilter.solidMatrixOf` came out: an extrusion is the
    layer's silhouette in one colour, which is the operation a `TintMode.SOLID` tint already performs. Both now pull
    the channels out of an int in one place, and that place is the fifth column — silent when wrong.
  - The bake's effect loop stopped being "a colour matrix or an overlay": Extrude produces a new buffer without being
    a matrix, so the `when` now says plainly which effects replace the buffer and which paint into it.
- **Chromatic split needed no new arithmetic at all**, which is what `ColorMatrices.mix` was extracted for: a channel
  isolation is that builder with a single one in each row, and `scale` structurally cannot express it. What
  `LayerChromatic` contributes is the **convention** — red leads, blue trails, green stays put — and that is precisely
  the thing worth sharing, because either direction looks like a lens and nothing would fail if the two paths
  disagreed.
  - **It is the only effect with no strength slider**, and that is the honest shape rather than an omission: the
    effect *is* a displacement, so an offset of nothing already means "not split", and a second knob would be a second
    way to reach the same state.
  - **`PositionPad` gained a range** for it. A fringe is a couple of percent of the icon, so at the pad's own travel
    the whole useful span would sit under the thumb.
  - Green holds still on purpose: the eye reads luminance mostly from green, so displacing it would shift the whole
    icon rather than fringe it.
- **Whole-icon effects (4a) — the thing thirteen effects actually needed, and §3 assumed away.** Every entry in this
  plan is written as a *layer* effect, and for six of them that is simply wrong: a glow derives from the finished
  silhouette, so per-layer it glows around the foreground *inside* the background plate where nobody can see it; grain,
  ripple and pixelate applied per layer produce independent distortion fields that visibly shear apart at the edge of
  the glyph; and even a colour matrix differs before and after compositing once opacity or a blend is in play. So
  `IconLayerSet` carries its own `effects`, applied to the composite — additive, defaulted empty, and reusing the
  **same** `LayerEffect` type and the same pipeline in both renderers rather than growing a second one.
  - **The UI cost one tile, because the rail was already the scope control.** Selection there has always meant "the
    thing every tool acts on" — the reason the Layers bar entry was deleted — so the whole icon is one more tile at the
    head of it, and the studio opens on it. A *"this layer / whole icon"* switch inside the Effects panel was the
    obvious alternative and is a second answer to a question already answered on screen.
  - **The composite offers three tools, not one.** Source, Transform and Shape are a layer's; Effects applies to both;
    Presets and More were never per-layer. So the bar shrinks with the selection rather than needing a special case.
  - **Two entries drop for it, by the rule slice 1 already established.** Opacity and blend describe *joining a stack*
    and the composite joins nothing, which is exactly `ownsEffect` — the same predicate that decides which entries
    carry a switch.
  - The Photoshop-style generalization — an **adjustment layer** at any height — was rejected: the bake would manage it,
    but the live path cannot sample its siblings without restructuring the whole stack into nesting, which is the
    two-renderer hazard in its worst form. The composite is the one position that is cheap live.

Slices 4–7 are the remaining tier-1 effects and need **no** change to the render architecture. The rail sat at 3
because it is independent of every effect and gets more valuable the longer the bar gets — and its thumbnails turned
out to be the first consumer of the per-layer render that slice 8 needs anyway, reached through `IconLayerStack`
with every other layer hidden rather than through a new path.

---

## 6. Open questions

- **How stale may the preview get before it needs to say so?** The threshold at which a "working" hint appears is a
  number nobody can pick from a desk; it wants measuring on the slowest device to hand, against the heaviest effect
  (progressive blur at full radius). §7 builds the preview without one and leaves the hint for that measurement.
- **How far down does a gesture bake?** The same shape of question, and the same answer: §7 states the mechanism and
  leaves the fraction as the one number to tune on device.
- **Does the composite want a transform of its own?** Perspective is a layer's, so tilting a whole icon means tilting
  each of its layers. Raised by slice 5 and not answered — it is the same question the whole-icon *effects* answered
  yes to, pointed at a different tool.

### Settled

- **Incremental, not collapse** (§2). The live path stays; `drawsLive` on each effect variant is what keeps the
  choice from drifting. Confirmed at slice 7 as the plan asked: all of tier 1 landed with both paths intact, and the
  six shared derivations grew to eight rather than the two paths growing apart.
- **A configured-but-switched-off effect reads as inactive**, and the grid has two states rather than three. A tile
  marks itself from `activeEffects`, which is the renderers' own list — so "is this doing anything to my icon?" is
  answered by exactly the thing that decides whether it draws. The lost distinction (never configured versus
  configured and off) is recoverable by opening the entry, where the switch says which.
- **The bake-backed preview is accepted** (§2) — downscaled and throttled during a gesture, not a spinner.
- **Filters are engineering, not content** (§3a). A fixed table, `IconShapes`' exact shape, our own names.
- **The layer rail replaces the Layers section** (§4a). The effect list stays a 4-column grid in the Effects panel.
- **Pattern gets its own assets, not `IconShapes`.** Two libraries, and the split is what each thing *is*: a shape
  is a silhouette whose **alpha is a mask** and which is stretched to one box; a pattern is artwork whose **colour
  is drawn** and which is tiled at a scale and angle. Sharing one library would mean every entry answering both
  questions, and half of each list would be nonsense in the other role. They share the *pipeline* — drop a drawable
  in, add an id, id is the on-disk contract, unknown id degrades quietly — which is the part worth copying.

---

## 7. Slice 8 — the bake-backed preview

All six remaining effects wait on this and nothing else, so it is worth designing before it is built. §2 settled
*that* the bake backs the preview; this is *how*, and the decisions it turns on.

### What falls back: the whole icon, never one layer

§2 says "the studio previews that **layer** from its baked bitmap". That is the wrong granularity, and the reason is
the hazard this whole document is arranged around.

A per-layer fallback means a **hybrid stack** — one layer from a bitmap, the rest drawn live around it — and the two
halves then have to agree about geometry *at a seam inside a single icon*. That is the two-renderer problem in its
worst form: not two whole pictures that can be compared side by side, but one picture assembled from both, where a
drift shows as a misalignment nobody can attribute. Whole-icon fallback keeps the paths as two complete pictures,
which is what the `IconLayers` dev-harness playground already exists to compare.

It also costs nothing. The bake renders a whole set either way, and its expense is the *effect* rather than the layer
count — so per-layer buys responsiveness on the layers that were never the slow part.

So: **`IconLayerSet.drawsLive` is the one question the canvas asks**, and it is `layers.all { it.drawsLive } &&
effects.drawLive`.

**`IconLayerSpec.drawsLive` keeps a real job**, which is worth saying because it looks vestigial after that. A layer
*tile* in the rail solos one layer, so it falls back on that layer's own effects — a glow on the foreground makes the
canvas and the foreground's tile bake, and leaves the background's tile live. One property, two scopes, each asking
about what it actually draws.

### Where it lives: one composable that chooses

A new `core:icon/compose` entry point — `IconPreview` or similar — picking between `IconLayerStack` and a baked
`Image`. **Not inside `IconLayerStack`**, which is the live path by definition and would otherwise need a renderer, a
scope and a cache; and **not at the three studio call sites**, because a call site that forgot to ask is a call site
that silently lies.

### Throttling is `collectLatest`, not a timer

§2 asks for "re-bake on a trailing interval and drop what it cannot keep up with". That is exactly what a
`MutableStateFlow<IconLayerSet>` collected with `collectLatest` does: a new value cancels the in-flight bake and
starts the current one, so the work conflates instead of queueing. No interval to pick, and no queue to bound.

**It must not go through `IconRenderManager`'s cache.** That cache is keyed on the resolved set, which is precisely
what changes every frame of a drag — so the preview would evict every real icon on the device within a second or two
of sliding. The studio wants its own single-slot state, and the coalescing and concurrency cap `IconRenderManager`
provides are not what this needs either: there is one bake in flight, by construction.

### Resolution: no signal at all, as it turned out

This section proposed threading a gesture-in-flight signal down from the studio — `onUpdate` without `onCommit` — and
**building it showed none is needed**, which is the one place slice 8 came out simpler than it was designed.

Every recipe is baked **twice**: downscaled immediately, then full size. `collectLatest` cancels the in-flight
collector when a newer recipe arrives, so during a drag the draft keeps landing and the full-size bake is cancelled
before it starts; when the finger stops, nothing newer arrives and the full-size one completes and replaces it.

So one mechanism decides both *what to skip* and *what resolution to skip it at*, and the two cannot disagree. It is
also the truer condition — "nothing newer has arrived" is what settled actually *means*, where a commit signal is a
proxy for it that any non-slider edit would answer differently.

The fraction is still the one number to measure on device (§6).

### What tier 2 and tier 3 will share

Worth naming now, because both groups are three effects that are one mechanism each:

- **Glow and Drop shadow are the same effect twice** — a blurred copy of the finished silhouette, placed behind, one
  centred and spread, the other offset. `Bitmap.extractAlpha(paint, offset)` with a `BlurMaskFilter` is the whole of
  it and needs no bitmap arithmetic at all. **Built (slice 9)**, and four things came out of it:
  - **Two effects rather than one**, despite the shared mechanism, because at most one effect of a type is
    meaningful — one record would mean a layer could carry a glow *or* a shadow, and a glowing icon casting one is
    ordinary. The parameters differ honestly too: a glow is centred so it has a spread and no offset, a shadow is
    thrown so it has an offset and no spread.
  - **Spread is a dilation, and a dilation is the silhouette swept around a circle** — `LayerExtrude`'s "no primitive
    draws this" problem one dimension over, and cheap here in a way that one could not be, since this effect never
    draws live: the copies are blits of a bitmap the bake holds rather than re-runs of a layer per frame. Without it a
    blur alone leaves the halo half-strength at the edge and a glow reads as a smudge.
  - **`radiusPxOrNull` is nullable and that is load-bearing**: `BlurMaskFilter` rejects a non-positive radius, so a
    slider at its floor would throw. Null means "skip the blur", which is a hard-edged shadow — a real look rather
    than a degenerate one.
  - **`LayerShadow` is the first derivation extracted *not* for two renderers to agree.** Only one path draws these,
    so nothing is competing with the arithmetic; it is separated for the other half of the reason — pulled out of
    `IconRenderer` the numbers are unit-testable, where every line of that class needs an emulator.
- **Pixelate, Ripple and Grain are one loop with three answers** — or so this said. Building Ripple first showed
  the grouping is **two and one**: Ripple and Grain are resamplings (for every output pixel, which input pixel does
  it read — a sinusoid and noise respectively), where Pixelate as the reference draws it is not a resampling at all.
  Its cells have gaps and rounded corners, so it *redraws* the layer as a field of shapes, one colour sampled per
  cell. A pure coordinate-quantising pixelate would give solid blocks and no way to express either control.
  - **So Ripple went first**, against this plan's order, to put the displacement pass under its natural first
    consumer rather than under the odd one out.
  - **And the pass was not extracted**, which reverses the note above: this codebase extracts on the *second*
    consumer (`IconPreviewPlate`, `AppPicker`, `PositionPad`), the loop is six lines, and what Ripple and Grain
    genuinely share is not yet known to be the same six. `LayerRipple` holds the part that can be silently wrong —
    the displacement as a pure function of distance — which is the `LayerShadow` precedent rather than the
    shared-derivation one, since only the bake draws any of these.
  - **Grain arrived and it was the same six**, so `IconRenderer.resample` exists now — a private helper taking a
    per-pixel `sourceOf`, not a new file or a new public type, which is the right size for two call sites in one
    class. It also settled the out-of-bounds question in one place rather than two: **transparent, never clamped**,
    since clamping smears the outermost row wherever a displacement reaches past the box and an icon genuinely *is*
    transparent out there.
  - **Grain's noise had to be smooth, and that is the whole effect.** A hash per pixel scatters the artwork into
    confetti; a field interpolated between lattice points a grain-size apart moves neighbours together, which is
    what tears it into pieces still recognisable as pieces of it. `LayerGrain` is deterministic and defined in
    fractions of the box for the reason everything else here is — a field that varied between bakes would make the
    icon shimmer as the studio re-rendered, and a draft would not predict the full-size result. That is also why
    there is no seed: a hash *of position* is the randomness, and a seed would be a second control offering nothing
    the grain size does not.
  - **`GrainDrift` is a choice rather than a directionality slider**, `BloomFalloff`'s shape and reason: an angle
    means nothing to noise pushing every way at once, so a continuous control would leave the angle inert at one end
    and change the panel's height as it crossed zero. It is also honest about the mechanism — scatter uses two
    independent noise fields, directed uses one and spends it along the angle, and there is no continuum between
    "two fields" and "one".
  - **Pixelate confirmed it is the odd one out**, and shares nothing with the two: it samples one colour per *cell*
    and then **draws** a shape, so the gaps and the rounded corners are painted rather than sampled — and drawn on a
    canvas they come out antialiased for free, where an `IntArray` would owe its own coverage arithmetic.
    - **The averaging is the part that is silently wrong if done naively.** Straight ARGB averaging counts a
      transparent pixel's colour equally with an opaque one, and a transparent pixel is almost always transparent
      *black* — so every cell straddling the artwork's edge comes out dark, and the icon gains a fringe that reads as
      a rendering fault. `LayerPixelate.averageArgb` weights by alpha and divides by the alpha total, which is
      premultiplying and un-premultiplying.
    - **Size is the switch**, since cells with no size are the layer itself — the same shape the chromatic split's
      offset has, reached from the other direction. No separate strength.
- **Progressive blur is last for a reason**: it is a blur *and* a mask ramp, so it is the only one that needs both
  mechanisms. **Built**, and three things came out of it:
  - **The blur is a downscale and an upscale**, not a box blur. A box blur would have been a second copy of the one
    in `data:wallpaper`'s `Blur.kt`, which `core:icon` cannot reach without depending on a `data` module — where
    scaling down and back up with bilinear filtering is the platform doing the same averaging in two calls, with no
    arithmetic to get wrong. It approximates a Gaussian rather than being one, which is invisible in the only place
    it is used.
  - **The ramp is masked onto the *blurred* copy, destination-in, with the sharp one underneath.** Masking the sharp
    copy instead would leave the two overlapping at every partial alpha and the icon looking doubled rather than
    blurred.
  - **`BloomFalloff` became `Falloff`** on this second consumer, since the blur asks the identical linear-or-radial
    question. Renaming the type costs nothing on disk: the `@SerialName`s are the contract and each effect's field
    is still `falloff`.

---

## 8. Phase 2 — six more effects, and one mechanism

A second list, proposed after the thirteen landed and checked against the built code rather than against the
captures. Six of the seven items are effects; the seventh is an architectural change and is the one whose design did
not survive the check.

**The headline is that four of the six are re-pointing code that already exists**, which is what §5's "no change to
the render architecture" bought: `ColorMatrices.duotone` already *is* a gradient map, `LayerGradient.radial` already
places a vignette, `IconRenderer.haloed` already is an inner shadow inverted, and `IconRenderer.dilated` is already
an outline's outward half. Only Bevel needs a kernel nobody has written.

### 8a. What each one needs

| Effect | Mechanism | Already built | `drawsLive` |
|---|---|---|---|
| **Gradient map** | one colour matrix | `ColorMatrices.duotone` | **yes** |
| **Vignette** | radial ramp, source-atop | `LayerGradient.radial` | **yes** |
| **Inner shadow** | inverted alpha, blurred, masked in | `haloed`, `dilated` | no — blur |
| **Inner glow** | the same, offset zero, screened | inner shadow's | no — blur |
| **Outline** | dilate, erode, difference | `dilated`, `LayerShadow.spreadSteps` | no |
| **Bevel & emboss** | Sobel over a blurred alpha, then lighting | `extractAlpha` for the height map | no — per-pixel |

**The one primitive genuinely missing is an alpha-inverting matrix.** `ColorMatrices.invert` flips the three colour
channels and leaves row 4 alone, which is right for a look and useless for a silhouette; inner shadow, inner glow and
an outline's inside position all need the alpha flipped instead. It is four lines, and it arrives with whichever of
the three is built first.

**Erosion is `invert → dilate → invert`**, which is why the outline's three positions cost one mechanism rather than
three: `dilated` grows a silhouette, and growing the *hole* is shrinking the shape. Outside is the dilation drawn
behind, inside is the difference against the erosion, centre is half of each.

**Bevel is the only one that does not fit `resample`.** That helper's `sourceOf` contract is "which input pixel does
this output pixel read", and it hardcodes `LayerSample.bilinear` on the single point returned — where a Sobel reads
a *neighbourhood* and the output is a lit colour rather than a sampled one. So it wants its own band-parallel loop
beside `resample` rather than a generalisation of it, on the same extract-on-the-second-consumer grounds that kept
the displacement pass private until Grain arrived. Its height map is free: `extractAlpha` with a `BlurMaskFilter` is
what the "Size" parameter means, and `haloed` already produces exactly that.

### 8b. Three things the proposal asks for that should not be built

- **Gradient map's bias/midpoint slider.** A 4×5 matrix cannot remap luminance non-linearly before interpolating, so
  bias demotes the effect from a matrix to a per-pixel pass — trading its live drawing, and the composability that
  makes it stack with everything, for a control the effect was not asked for by name. The two colours *are* the
  effect. If bias is ever genuinely wanted it is a separate, baked effect rather than a slider added to this one.
- **Gradient map's blend-mode dropdown and per-effect opacity.** Opacity and blend describe how a layer *joins a
  stack*, which is why they are `IconLayerSpec` fields and why the composite does not offer them (§5, slice 1). A
  per-effect copy is a second answer to the same question, and the two would disagree about which one switched a
  layer off. What the dropdown is *reaching* for — a partial grade — is `strength`, and a strength is expressible as
  a matrix: interpolating two 4×5 matrices interpolates their outputs, because applying one is linear in the matrix.
  So the effect keeps a strength slider, stays one matrix, and stays live.
- **Inner glow's "Edge vs Center" toggle.** *Edge* is the effect. *Center* — a glow radiating from the middle of the
  artwork outward, masked to its alpha — is `Bloom(falloff = RADIAL, anchor = CONTENT)` exactly, which is already
  built and already reachable. A toggle that reaches a state the model holds elsewhere is the second way to say one
  thing that this codebase keeps removing: the monochrome toggle beside the saturation slider, the strength slider
  beside a chromatic split's offset.

**What is kept from the proposal against the temptation to fold it**: inner glow stays a *separate effect* from inner
shadow despite being one mechanism, on Glow and Shadow's own precedent — at most one effect of a type is meaningful,
so folding them means an icon can have a recessed stamp or a neon edge and never both, and wanting both is ordinary.
The parameters differ honestly too, the same way those two do: a shadow is thrown so it has an offset, a glow is not
so it has none.

### 8c. The effect mask, and why it is not "extracting the falloff"

The proposal's architectural item is: lift the Linear/Radial falloff off Bloom and Focus onto the base effect, so any
effect can be masked, and evaluate every effect as `mix(original, effect, falloff)`.

**The bake mechanism is real and is about twenty lines in one place.** `applyEffects` already distinguishes overlays
(paint onto what is there) from buffer replacers (produce a new bitmap and swap it in). A mask is: run the effect
into a buffer, then composite that buffer back over the *pre-effect* one through a ramp's alpha. Overlays join the
same path by being given a buffer, which they do not have today only because they never needed one. `LayerGradient`
and `LayerProgressiveBlur.stops` already supply every parameter the proposal lists — centre, radius, softness,
invert — so there is no new arithmetic at all, and `IconLayerSet`'s own effects get it for free.

Four things about it are not as proposed:

- **It must not be called `Falloff`, and it is not an extraction.** On Bloom and Focus a falloff is the light's *own
  geometry* — a ramp has an angle and a disc has a radius, and neither can answer the other's question, which is what
  that enum's KDoc is about. A mask is a third thing that happens to reuse the same two shapes. Bloom's falloff
  cannot become one without changing what Bloom draws, so nothing moves; a field is *added*, to every variant.
- **`LayerEffect` is a sealed interface with no state**, so there is no base class to put it on. It is a
  `mask: EffectMask? = null` on each variant plus an exhaustive `withMask` beside `withEnabled` — free on disk under
  `encodeDefaults = false`, and the compiler refuses to let a variant be forgotten, which is that helper's whole
  point. The tempting alternative, a `Masked(inner, mask)` wrapper, keeps the variants untouched and breaks
  `effectOrNull<T>()` and `withEffect<T>()` at every call site instead.
- **`mix(original, effect, falloff)` is a fragment-shader framing and this pipeline is not one.** The bake's answer is
  the buffer composite above. The *live* path's is to restructure each of the seven live-drawable effects from one
  `saveLayer` into a two-layer masked composite — possible at every API, no gate, but per-effect work that grows with
  the list.
- **So it goes last, not first.** Its cost multiplies by the number of effects, so it is cheaper once the list has
  settled. Building it first would mean paying for it again on each of the six above.

### 8d. Order

By cost, cheapest first, which also happens to put the two that draw live at the front:

1. **Gradient map** — `duotone` exists; one matrix, one strength, live. **Built, and it landed as `Duotone`** — a
   gradient map has arbitrary stops, this deliberately has exactly two colours and no midpoint (8b), so the name that
   describes the look is the honest one. The rename is Bloom's rule reapplied: every entry in the grid names a look
   rather than a mechanism. Two things came out of building it. **`LayerFilter.duotoneMatrixOf` is the extraction the
   second consumer earned** — `IconFilters` had been unpacking two ARGB ints into six channels privately, and a user
   picking the same two colours was about to do it again, on the fifth column, where it is silent when wrong. And
   **`strength` is a matrix interpolation, not a blended copy** (`ColorMatrices.towards`): applying a matrix is linear
   in the matrix, so interpolating the entries interpolates the outputs — which is what let the effect carry a partial
   grade without a second buffer and therefore without losing its live path.
2. **Vignette** — `LayerGradient.radial` exists; a bloom's ramp run outward instead of inward, live.
3. **Inner shadow** — `haloed` with the alpha inverted; brings the alpha-invert matrix.
4. **Inner glow** — inner shadow's twin, no toggle.
5. **Outline** — dilate and erode; the shared silhouette helpers get extracted here, on their second consumer.
6. **Bevel & emboss** — the one new kernel; a slice on its own.
7. **Effect mask** — last, renamed, once the list is stable.

### 8e. The consequence worth watching

Four of the six do not draw live, taking the total to **ten of nineteen**. `IconLayerSet.drawsLive` is all-or-nothing
for the whole icon (§7), so most recipes worth making will preview through `IconRenderer` under the `MaxPreviewPx`
cap and the draft. That machine is built and it works — but the live path is the editor's alone, nothing else has
ever drawn through it, and each baked-only effect narrows what it is still used for. §6's settled "incremental, not
collapse" is not overturned by this; it is the number to keep an eye on, and the point at which it stops being
settled is when a *plain* recipe becomes the unusual one.
