package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Grain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.model.icon.Falloff
import inkspire.morphic.core.model.icon.IconPattern
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect

// The controls for the effects that rework a layer's own pixels: a tiled pattern, an extrusion, a chromatic
// split, and the four per-pixel passes — ripple, grain, pixelate and progressive blur.

/**
 * How soft the blurred end gets, how much stays sharp, and where the sharp part is.
 *
 * **Labeled *Focus* rather than "Progressive blur"**, which is the reference's name for the mechanism rather than
 * for the look. What a user is doing here is choosing what stays in focus; the blur is how that is expressed. It is
 * also the only entry whose name would not fit a tile at four columns.
 *
 * **The falloff swaps the placement control**, [BloomControls]' rule and the second use of the same enum: a band
 * across the layer is placed by the direction it runs, a disc within it by where its center sits, and neither value
 * can answer the other's question.
 *
 * **Softness of zero is a real answer**, not a degenerate one — a hard edge between sharp and blurred is a look, and
 * the renderer keeps the two stops a hair apart rather than refusing it.
 */
@Composable
internal fun ProgressiveBlurControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val blur = effects.effectOrNull<LayerEffect.ProgressiveBlur>() ?: LayerEffect.ProgressiveBlur()

    // The defaults of whichever falloff is showing — `BloomControls`' lookup and its reason: both profiles start
    // identical, so writing `.radial` directly would be right today and silently wrong the moment the two are given
    // different arrival values.
    val defaults = if (blur.falloff == Falloff.LINEAR) {
        ProgressiveBlurDefaults.linear
    } else {
        ProgressiveBlurDefaults.radial
    }

    LabeledControl("Falloff") {
        MorphicSegmentedButtons(
            options = listOf("Linear", "Radial"),
            selectedIndex = if (blur.falloff == Falloff.RADIAL) 1 else 0,
            onSelect = { index ->
                val falloff = if (index == 1) Falloff.RADIAL else Falloff.LINEAR
                onUpdate { it.withEffect(blur.copy(falloff = falloff)) }
                onCommit()
            },
        )
    }

    SliderControl(
        label = "Blur",
        value = blur.radius,
        valueRange = 0f..BlurReach,
        default = defaults.radius,
        onValueChange = { value -> onUpdate { it.withEffect(blur.withActive { p -> p.copy(radius = value) }) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Sharp area",
        value = blur.sharpArea,
        valueRange = 0f..1f,
        default = defaults.sharpArea,
        onValueChange = { value -> onUpdate { it.withEffect(blur.withActive { p -> p.copy(sharpArea = value) }) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Softness",
        value = blur.softness,
        valueRange = 0f..1f,
        default = defaults.softness,
        onValueChange = { value -> onUpdate { it.withEffect(blur.withActive { p -> p.copy(softness = value) }) } },
        onValueChangeFinished = onCommit,
    )

    when (blur.falloff) {
        Falloff.LINEAR -> SliderControl(
            label = "Angle",
            value = blur.angleDegrees,
            valueRange = 0f..360f,
            step = AngleStep,
            default = defaults.angleDegrees,
            format = { "%.0f°".format(it) },
            onValueChange = { value -> onUpdate { it.withEffect(blur.withActive { p -> p.copy(angleDegrees = value) }) } },
            onValueChangeFinished = onCommit,
        )

        Falloff.RADIAL -> LabeledControl("Center") {
            PositionPad(
                x = blur.centerX,
                y = blur.centerY,
                onValueChange = { x, y -> onUpdate { it.withEffect(blur.withActive { p -> p.copy(centerX = x, centerY = y) }) } },
                onCommit = onCommit,
            )
        }
    }
}
/**
 * How soft the blurred end may get, as a fraction of the box.
 *
 * A tenth already scales the layer down to ten pixels a side before growing it back, which is past the point where
 * anything of the artwork survives — the ceiling is where the control stops doing more rather than where it breaks.
 */
private const val BlurReach = 0.1f
/**
 * How big the dots are, how much of their cells they fill, and how round they come out.
 *
 * **No strength slider, and the size is why.** Every other effect here needs a separate knob because it *adds*
 * something at an intensity; this one replaces the layer with a grid, and cells with no size are the layer itself.
 * So Size is both the control and the switch — the same shape the chromatic split's offset has, reached from a
 * different direction.
 *
 * **Fill and Roundness are what make it a panel of lights rather than a mosaic.** At a fill of 1 the dots touch and
 * the effect is a plain mosaic; below it the gaps open, and the roundness then decides whether what is left reads as
 * tiles or as pixels on a display.
 */
@Composable
internal fun PixelateControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // The model's own default is already zero-size — identity — so an absent effect needs no special seeding here,
    // unlike the ones whose natural resting value is something visible.
    val pixelate = effects.effectOrNull<LayerEffect.Pixelate>() ?: LayerEffect.Pixelate()

    SliderControl(
        label = "Size",
        value = pixelate.cellSize,
        valueRange = 0f..PixelateReach,
        default = PixelateDefaults.cellSize,
        onValueChange = { value -> onUpdate { it.withEffect(pixelate.copy(cellSize = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Fill",
        // Floored above zero: dots covering none of their cells is identity, so a slider reaching it would silently
        // delete the effect being tuned.
        value = pixelate.fill,
        valueRange = UnitFloor..1f,
        default = PixelateDefaults.fill,
        onValueChange = { value -> onUpdate { it.withEffect(pixelate.copy(fill = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Roundness",
        value = pixelate.roundness,
        valueRange = 0f..1f,
        default = PixelateDefaults.roundness,
        onValueChange = { value -> onUpdate { it.withEffect(pixelate.copy(roundness = value)) } },
        onValueChangeFinished = onCommit,
    )
}
/**
 * How large a cell may get, as a fraction of the box.
 *
 * A fifth puts five dots across the icon, which is already past the point where an app is identifiable — the ceiling
 * is where the control stops being useful rather than where the arithmetic stops working.
 */
private const val PixelateReach = 0.2f
/**
 * How far the noise pushes, how big the pieces are, and whether they scatter or all slide one way.
 *
 * **Two sliders that sound alike and are not**, which is the thing to get straight before touching this: *Strength*
 * is how far a piece moves, *Grain size* is how big a piece is. Turning the second up makes the tearing coarser
 * rather than stronger, and at a large size with a small strength the artwork barely moves while visibly breaking
 * into a few large chunks.
 *
 * **Drift is a choice rather than a slider**, for [BloomControls]' reason: an angle means nothing to noise that
 * pushes every way at once, so a continuous "directionality" would leave the angle inert at one end and the panel
 * changing height as the slider crossed zero. A segmented control changes it once, deliberately.
 */
@Composable
internal fun GrainControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val grain = effects.effectOrNull<LayerEffect.Grain>() ?: LayerEffect.Grain(amplitude = 0f)

    SliderControl(
        label = "Strength",
        value = grain.amplitude,
        valueRange = 0f..GrainReach,
        default = GrainDefaults.amplitude,
        onValueChange = { value -> onUpdate { it.withEffect(grain.copy(amplitude = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Grain size",
        // The whole range, floor included: zero is the *finest* setting now rather than a lattice with no spacing,
        // so there is nothing here to guard against — see `LayerEffect.Grain.grainSize` for what a position on this
        // control means and why it is not the fraction it maps to.
        value = grain.grainSize,
        valueRange = 0f..1f,
        default = GrainDefaults.grainSize,
        onValueChange = { value -> onUpdate { it.withEffect(grain.copy(grainSize = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Directionality",
        value = grain.directionality,
        valueRange = 0f..1f,
        default = GrainDefaults.directionality,
        onValueChange = { value -> onUpdate { it.withEffect(grain.copy(directionality = value)) } },
        onValueChangeFinished = onCommit,
    )

    // **Present but spent when there is no direction to name, rather than absent** — the one place in this panel
    // where the studio's "a control that changes nothing is worse than a missing one" rule is knowingly not applied,
    // and the gate is why: it is the *continuous slider directly above*. Hidden, this row appeared and vanished
    // under the very finger dragging that slider, shifting everything beneath it mid-gesture. Grayed out it states
    // the dependency and the panel never moves. Where a gate is a discrete choice made elsewhere — a shape picked, a
    // tint set — absent is still right, because the layout settles before the finger arrives.
    SliderControl(
        label = "Angle",
        value = grain.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = GrainDefaults.angleDegrees,
        format = { "%.0f°".format(it) },
        enabled = grain.directionality > 0f,
        onValueChange = { value -> onUpdate { it.withEffect(grain.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
}
/**
 * How far the noise may push a pixel, as a fraction of the box.
 *
 * Deliberately more generous than [RippleReach]: a ripple past a tenth stops reading as water, where grain *wants*
 * to reach the point of tearing the artwork apart — that is the look, rather than the failure of it.
 *
 * **Nearly half the box, where it was a seventh.** At the old ceiling a full-strength grain frayed the edges and
 * left the shape plainly readable, so the top of the slider was the middle of the effect: the state a user is
 * reaching for at maximum — the icon dispersed into a cloud of its own colors — was not on the control at all.
 */
private const val GrainReach = 0.45f
/**
 * How far the waves push, how many of them, and where they start.
 *
 * **No color**, unlike every other effect in this panel — a ripple moves the layer's own pixels rather than adding
 * any, so there is nothing to tint. It is the first entry here whose whole subject is *where* the artwork is instead
 * of what color it comes out.
 *
 * **Waves steps by one**, because it counts crests: a slider that could land on 8.37 of them would be offering a
 * precision the picture cannot show.
 */
@Composable
internal fun RippleControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at no amplitude when absent, which is also this effect's own "off" — the waves are described before
    // they displace anything, so the first drag brings a coherent ripple into being rather than an arbitrary one.
    val ripple = effects.effectOrNull<LayerEffect.Ripple>() ?: LayerEffect.Ripple(amplitude = 0f)

    SliderControl(
        label = "Strength",
        value = ripple.amplitude,
        valueRange = 0f..RippleReach,
        default = RippleDefaults.amplitude,
        onValueChange = { value -> onUpdate { it.withEffect(ripple.copy(amplitude = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Waves",
        value = ripple.waves,
        valueRange = 1f..30f,
        step = 1f,
        default = RippleDefaults.waves,
        format = { "%.0f".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(ripple.copy(waves = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Center") {
        PositionPad(
            x = ripple.centerX,
            y = ripple.centerY,
            onValueChange = { x, y -> onUpdate { it.withEffect(ripple.copy(centerX = x, centerY = y)) } },
            onCommit = onCommit,
        )
    }
}
/**
 * How far a crest may push a pixel, as a fraction of the box.
 *
 * A tenth is already extreme — past it the crests overlap far enough that the artwork stops being recognizable and
 * the effect reads as damage rather than as water.
 */
private const val RippleReach = 0.1f
/**
 * How far the channels are displaced, and in which direction — which is the whole effect.
 *
 * **One control, because the effect is one quantity.** Every other panel here pairs a look with a strength; a
 * chromatic split *is* a displacement, so an offset of nothing already means "not split" and a strength slider
 * beside it would be a second way to reach the same state.
 *
 * **The transform section's pad, at a tenth of its travel.** The value is a point and is found by dragging, which is
 * exactly what that control is for — but a fringe is a couple of percent of the icon, so at the pad's own range the
 * whole useful span would be a few pixels under the thumb. [PositionPad] takes the range for that reason.
 */
@Composable
internal fun ChromaticControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at no offset when absent, which is also this effect's own "off" — so the pad opens centered and the
    // first drag is what brings the effect into being.
    val split = effects.effectOrNull<LayerEffect.ChromaticSplit>()
        ?: LayerEffect.ChromaticSplit(offsetX = 0f, offsetY = 0f)

    LabeledControl("Offset") {
        PositionPad(
            x = split.offsetX,
            y = split.offsetY,
            onValueChange = { x, y -> onUpdate { it.withEffect(split.copy(offsetX = x, offsetY = y)) } },
            onCommit = onCommit,
            range = ChromaticRange,
        )
    }
}
/**
 * How far a fringe may reach, either way.
 *
 * A tenth of [PositionRange]: past a few percent of the icon the three channels stop reading as one object with
 * colored edges and start reading as three overlapping icons, which is a different — and much less useful — effect.
 */
private val ChromaticRange = -0.05f..0.05f
/**
 * The slab's color, how deep it runs, which way, and how strongly.
 *
 * **Depth is bounded well short of the box**, and that bound is about cost rather than taste: the extrusion is drawn
 * as many copies of the layer, `LayerExtrude` caps how many, and past the cap the copies spread far enough apart for
 * the edge to stair visibly. A quarter of the box is deep enough to read as a slab and still inside the cap at every
 * bake size.
 *
 * **Black by default**, unlike every other overlay here, because an extrusion is a *shadowed* side rather than a
 * light — it is the one place in this panel where the resting color is the dark one.
 */
@Composable
internal fun ExtrudeControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at zero strength when absent, as the other overlays are, so the controls describe a coherent slab
    // before it is turned on rather than jumping the moment strength leaves zero.
    val extrude = effects.effectOrNull<LayerEffect.Extrude>() ?: LayerEffect.Extrude(strength = 0f)

    LabeledControl("Color") {
        ColorField(argb = extrude.argb) { argb ->
            onUpdate { it.withEffect(extrude.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = extrude.strength,
        valueRange = 0f..1f,
        default = ExtrudeDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Depth",
        value = extrude.depth,
        valueRange = UnitFloor..0.25f,
        default = ExtrudeDefaults.depth,
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(depth = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Angle",
        value = extrude.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = ExtrudeDefaults.angleDegrees,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
}
/**
 * Which texture, in what color, how large, which way round, and how strongly.
 *
 * **The tiles are chosen from a row and the rest is sliders**, which is the filter panel's arrangement rather than
 * the shape chooser's paged grid: six entries fit a scroll, and unlike shapes they are not the *whole* control —
 * scale and angle change the look at least as much as which tile it is.
 *
 * **"None" is the first tile rather than a clear button**, the shape and filter choosers' shared answer: having no
 * texture is a choice among the same set, and the one every layer starts on.
 *
 * **A swatch draws the tile itself, tiled** — the same thing the icon will get, at the same stencil-into-color
 * treatment, so what is being chosen is visible rather than named. That costs one small bitmap per tile, which is
 * what makes it affordable where a filter's seventeen live icon previews were not.
 *
 * No *randomize* button, unlike the reference this was drawn from. What it randomizes there cannot be read off a
 * capture — an angle, an offset, a per-tile scatter — and a button that writes a random number into a slider the
 * user can already drag is a novelty rather than a control.
 */
@Composable
internal fun PatternControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val current = effects.effectOrNull<LayerEffect.Pattern>()
    // Seeded from whatever is stored so switching tiles keeps the scale, angle and color already tuned — the tile
    // is one field of the effect, not a different effect.
    val pattern = current ?: LayerEffect.Pattern(pattern = IconPatterns.Dots)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledControl("Texture") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FilterTileGap),
            ) {
                PatternTile(
                    pattern = null,
                    argb = pattern.argb,
                    selected = current == null,
                    onClick = {
                        onUpdate { it.withEffect<LayerEffect.Pattern>(null) }
                        onCommit()
                    },
                )
                IconPatterns.All.forEach { entry ->
                    PatternTile(
                        pattern = entry,
                        argb = pattern.argb,
                        selected = current?.pattern == entry,
                        onClick = {
                            onUpdate { it.withEffect(pattern.copy(pattern = entry)) }
                            onCommit()
                        },
                    )
                }
            }
        }

        LabeledControl("Color") {
            ColorField(argb = pattern.argb) { argb ->
                onUpdate { it.withEffect(pattern.copy(argb = argb)) }
            }
        }

        SliderControl(
            label = "Strength",
            value = pattern.strength,
            valueRange = 0f..1f,
            default = PatternDefaults.strength,
            onValueChange = { value -> onUpdate { it.withEffect(pattern.copy(strength = value)) } },
            onValueChangeFinished = onCommit,
        )
        SliderControl(
            label = "Scale",
            value = pattern.scale,
            // Floored well above zero: the tile is floored in pixels anyway, so a smaller number would stop
            // changing anything while the slider went on moving.
            valueRange = 0.05f..1f,
            default = PatternDefaults.scale,
            onValueChange = { value -> onUpdate { it.withEffect(pattern.copy(scale = value)) } },
            onValueChangeFinished = onCommit,
        )
        SliderControl(
            label = "Angle",
            value = pattern.angleDegrees,
            valueRange = 0f..360f,
            step = AngleStep,
            default = PatternDefaults.angleDegrees,
            format = { "%.0f°".format(it) },
            onValueChange = { value -> onUpdate { it.withEffect(pattern.copy(angleDegrees = value)) } },
            onValueChangeFinished = onCommit,
        )

        MorphicSwitchRow(
            label = "Invert",
            supportingText = "Draws the gaps instead of the marks.",
            checked = pattern.invert,
            onCheckedChange = { on ->
                onUpdate { it.withEffect(pattern.copy(invert = on)) }
                onCommit()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
/**
 * One texture, drawn as itself over the checkerboard the layer tiles use.
 *
 * A null [pattern] is the "None" tile and draws the ground alone, which is what makes it comparable — the others are
 * that same square with a texture on it.
 */
@Composable
private fun PatternTile(pattern: IconPattern?, argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PatternTileSide)
            // Outside the clip — see [FilterTile]. It is outside the ripple too, which is inside the clip with the
            // ground, so a press tints the swatch and leaves the ring reading as the selection.
            .then(if (selected) Modifier.border(SwatchRingWidth, StudioContentColor, EffectSwatchCorner) else Modifier)
            .clip(EffectSwatchCorner)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        pattern?.let {
            Image(
                painter = painterResource(IconPatterns.drawableResOrNull(it) ?: return@let),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color(argb)),
                // The drawable is one tile, and the swatch shows four of them — enough to read as a repeat rather
                // than as a single mark, which is the whole difference between choosing a texture and choosing a
                // shape.
                modifier = Modifier.fillMaxSize().scale(2f),
            )
        }
    }
}
private val PatternTileSide = 56.dp
