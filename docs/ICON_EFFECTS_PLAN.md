# Icon effects — expansion plan

Drawn from 13 captures of another icon studio (`~/Downloads/effect copy from other icon studio app`), whose
filenames name the effect. This plan is **what each one actually needs from our two renderers**, what has to change
before any of them can land, and the order to build them in.

Status: **plan only, nothing built.** The effect model today is two variants — `LayerEffect.Color` and
`LayerEffect.Gradient` — plus a deferred shadow.

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

| # | Slice | Why here |
|---|---|---|
| 0 | Effects list → ordered pipeline; `enabled` + `drawsLive` on variants | Nothing else is safe until draw order is real |
| 1 | Effect panel: grid paging, per-effect switch, slider reset + numeric field | The container for everything below |
| 2 | **Filters** | Largest visible gain, tier 1, no new machinery |
| 3 | **Layer rail**; delete the Layers section | Independent of the effects; do it once the bar is about to get busy |
| 4 | **Bloom** + **Gloss** | Reuse the gradient path; retire the "gradient" entry into them |
| 5 | **Perspective** | Extends `LayerTransform`, which is already shared |
| 6 | **Pattern** (+ its own assets) | Tier 1, needs an asset pipeline of its own — see §6 |
| 7 | **Extrude** + **Chromatic split** | Tier 1 finishers |
| 8 | **Bake-backed preview** (downscale + throttle) | Unblocks everything left, on every API |
| 9 | **Glow** + **Drop shadow** | Retires the standing deferral |
| 10 | **Pixelate**, **Ripple**, **Grain** | Per-pixel, on the baked preview |
| 11 | **Progressive blur** | Hardest: blur *and* a mask |

Slices 2–7 are seven effects with **no** change to the render architecture. The rail sits at 3 because it is
independent of every effect and gets more valuable the longer the bar gets — and its thumbnails are the first
consumer of the per-layer bake that slice 8 needs anyway.

---

## 6. Open questions

- **Incremental or collapse** (§2). The one decision that changes the shape of the codebase rather than the size of
  the feature list. Answer it at slice 7.
- **How stale may the preview get before it needs to say so?** The threshold at which a "working" hint appears is a
  number nobody can pick from a desk; it wants measuring on the slowest device to hand, against the heaviest effect
  (progressive blur at full radius).
- **Does a configured-but-switched-off effect still mark its tile?** Three states exist once `enabled` does — never
  configured, configured and off, on — and the grid currently has two. Marking "on" only is the simple reading and
  loses the difference between the first two; a third treatment is cheap but is one more thing on a small tile.
  Worth settling when the switch lands, not before.

### Settled

- **Incremental, not collapse** (§2). The live path stays; `drawsLive` on each effect variant is what keeps the
  choice from drifting.
- **The bake-backed preview is accepted** (§2) — downscaled and throttled during a gesture, not a spinner.
- **Filters are engineering, not content** (§3a). A fixed table, `IconShapes`' exact shape, our own names.
- **The layer rail replaces the Layers section** (§4a). The effect list stays a 4-column grid in the Effects panel.
- **Pattern gets its own assets, not `IconShapes`.** Two libraries, and the split is what each thing *is*: a shape
  is a silhouette whose **alpha is a mask** and which is stretched to one box; a pattern is artwork whose **colour
  is drawn** and which is tiled at a scale and angle. Sharing one library would mean every entry answering both
  questions, and half of each list would be nonsense in the other role. They share the *pipeline* — drop a drawable
  in, add an id, id is the on-disk contract, unknown id degrades quietly — which is the part worth copying.
