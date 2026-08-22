package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.runtime.Composable
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.OutlinePosition
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect

// The controls for the effects derived from a layer's finished *silhouette*: glow and drop shadow outside it,
// inner shadow and inner glow within it, an outline tracing it, and a bevel reading it as a raised surface.

/**
 * The halo's color, how strong it is, how far it fades and how far it is grown first.
 *
 * **Spread and radius are two different things and both are needed**, which is the one non-obvious control here. A
 * blur alone leaves the halo at about half strength right at the silhouette's edge and fading immediately, so a glow
 * built from radius alone reads as a smudge; spread grows the silhouette *before* the blur, giving the fade a solid
 * ring to start from. Radius is how soft, spread is how big.
 *
 * **No offset**, unlike [ShadowControls]: a glow is centered on the silhouette by definition, and a halo pushed to one
 * side is a colored shadow — which is the other entry.
 */
@Composable
internal fun GlowControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val glow = effects.effectOrNull<LayerEffect.Glow>() ?: LayerEffect.Glow(strength = 0f)

    LabeledControl("Color") {
        ColorField(argb = glow.argb) { argb ->
            onUpdate { it.withEffect(glow.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = glow.strength,
        valueRange = 0f..1f,
        default = GlowDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(glow.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Radius",
        value = glow.radius,
        valueRange = 0f..HaloReach,
        default = GlowDefaults.radius,
        onValueChange = { value -> onUpdate { it.withEffect(glow.copy(radius = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Spread",
        value = glow.spread,
        valueRange = 0f..HaloReach,
        default = GlowDefaults.spread,
        onValueChange = { value -> onUpdate { it.withEffect(glow.copy(spread = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * The shadow's color, how strong it is, how soft, and where it is thrown.
 *
 * **A radius of zero is a legitimate answer here**, unlike a glow's: a hard-edged silhouette offset behind the layer
 * is a perfectly good shadow, and it is the one a long-shadow look is built from. The renderer reads that as "skip
 * the blur" rather than as nothing, since `BlurMaskFilter` refuses a radius of zero outright.
 *
 * The throw is the transform section's pad at a fraction of its travel, for [ChromaticControls]' reason — a shadow
 * thrown half the icon's width is not a shadow.
 */
@Composable
internal fun ShadowControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val shadow = effects.effectOrNull<LayerEffect.Shadow>() ?: LayerEffect.Shadow(strength = 0f)

    LabeledControl("Color") {
        ColorField(argb = shadow.argb) { argb ->
            onUpdate { it.withEffect(shadow.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = shadow.strength,
        valueRange = 0f..1f,
        default = ShadowDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(shadow.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Radius",
        value = shadow.radius,
        valueRange = 0f..HaloReach,
        default = ShadowDefaults.radius,
        onValueChange = { value -> onUpdate { it.withEffect(shadow.copy(radius = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Throw") {
        PositionPad(
            x = shadow.offsetX,
            y = shadow.offsetY,
            onValueChange = { x, y -> onUpdate { it.withEffect(shadow.copy(offsetX = x, offsetY = y)) } },
            onCommit = onCommit,
            range = ThrowRange,
        )
    }
}

/**
 * The recess's color, how strong it is, how soft, how far it is choked in, and where it is thrown.
 *
 * **[ShadowControls]' four controls plus a choke**, and the pairing is the honest one: a cast shadow has a radius and
 * a throw, and this has those *and* the spread its outer twin gives to a glow — because the region it is cast by is
 * the whole of the outside, so growing it is a meaningful thing to ask for where growing a cast silhouette is what a
 * glow already does.
 *
 * **The throw runs the other way visually, and that is not a sign to flip.** Displacing the outside down and right
 * slides it over the artwork's top-left interior, so the band appears there — which is where a light from the
 * top-left leaves a recess dark. Both effects therefore agree about where the light is while their bands sit on
 * opposite edges, which is exactly what a real light does to a bump and a dent.
 */
@Composable
internal fun InnerShadowControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val inset = effects.effectOrNull<LayerEffect.InnerShadow>() ?: LayerEffect.InnerShadow()

    LabeledControl("Color") {
        ColorField(argb = inset.argb) { argb ->
            onUpdate { it.withEffect(inset.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = inset.strength,
        valueRange = 0f..1f,
        default = InnerShadowDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(inset.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Radius",
        value = inset.radius,
        // From zero, like a cast shadow's and unlike a glow's: a hard band is the flat inset a stamped label has,
        // and the renderer reads no radius as "skip the blur" rather than as nothing.
        valueRange = 0f..HaloReach,
        default = InnerShadowDefaults.radius,
        onValueChange = { value -> onUpdate { it.withEffect(inset.copy(radius = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Choke",
        value = inset.spread,
        valueRange = 0f..HaloReach,
        default = InnerShadowDefaults.spread,
        onValueChange = { value -> onUpdate { it.withEffect(inset.copy(spread = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Throw") {
        PositionPad(
            x = inset.offsetX,
            y = inset.offsetY,
            onValueChange = { x, y -> onUpdate { it.withEffect(inset.copy(offsetX = x, offsetY = y)) } },
            onCommit = onCommit,
            range = ThrowRange,
        )
    }
}

/**
 * The rim's color, how strong it is, how far it reaches in, and how far it is choked.
 *
 * **[InnerShadowControls] without the throw**, which is [GlowControls]' relationship to [ShadowControls] one scope
 * in: a rim is centered on the edge it lights by definition, and light pushed to one side is a recess in a bright
 * color — which is the entry beside it.
 *
 * **No "edge or center" choice**, unlike the reference's. A glow radiating from the middle of the artwork outward is
 * `Bloom` with a radial falloff anchored to content, which is already built and offers a position and a falloff this
 * could not; a toggle reaching a state the model holds elsewhere is the second way to say one thing.
 */
@Composable
internal fun InnerGlowControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val rim = effects.effectOrNull<LayerEffect.InnerGlow>() ?: LayerEffect.InnerGlow()

    LabeledControl("Color") {
        ColorField(argb = rim.argb) { argb ->
            onUpdate { it.withEffect(rim.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = rim.strength,
        valueRange = 0f..1f,
        default = InnerGlowDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(rim.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Radius",
        value = rim.radius,
        valueRange = 0f..HaloReach,
        default = InnerGlowDefaults.radius,
        onValueChange = { value -> onUpdate { it.withEffect(rim.copy(radius = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Choke",
        value = rim.spread,
        valueRange = 0f..HaloReach,
        default = InnerGlowDefaults.spread,
        onValueChange = { value -> onUpdate { it.withEffect(rim.copy(spread = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * The stroke's color, how strong it is, how thick, and which side of the edge it sits on.
 *
 * **No softness**, which is the one control a user might look for and the one that would be a duplicate: a softened
 * stroke outside the edge is [GlowControls] and inside it is [InnerGlowControls], both of which offer a choke this
 * could not. Hard is what makes a stroke a stroke.
 *
 * **Width is the total thickness whichever position is chosen**, so switching between them changes where the band
 * sits and not how heavy it looks — the model halves it for a centered stroke, which is the arithmetic that would
 * otherwise make the position control secretly a width control too.
 */
@Composable
internal fun OutlineControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val outline = effects.effectOrNull<LayerEffect.Outline>() ?: LayerEffect.Outline()

    LabeledControl("Position") {
        MorphicSegmentedButtons(
            options = listOf("Inside", "Center", "Outside"),
            selectedIndex = OutlinePosition.entries.indexOf(outline.position),
            onSelect = { index ->
                onUpdate { it.withEffect(outline.copy(position = OutlinePosition.entries[index])) }
                onCommit()
            },
        )
    }

    LabeledControl("Color") {
        ColorField(argb = outline.argb) { argb ->
            onUpdate { it.withEffect(outline.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = outline.strength,
        valueRange = 0f..1f,
        default = OutlineDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(outline.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Width",
        value = outline.width,
        // Floored above zero for `UnitFloor`'s reason — no width *is* this effect's identity, so the bottom of the
        // track should leave a hairline rather than a silently absent stroke. The ceiling is a halo's, the two
        // reaching in the same units.
        valueRange = OutlineFloor..HaloReach,
        default = OutlineDefaults.width,
        onValueChange = { value -> onUpdate { it.withEffect(outline.copy(width = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * The thinnest stroke worth offering, as a fraction of the box.
 *
 * A tenth of [UnitFloor], because a stroke is measured against a *whole* icon where that constant's four consumers
 * are fractions of their own effects: at 0.05 the thinnest stroke on offer would already be heavier than the default,
 * so the useful half of the control would be missing entirely.
 */
private const val OutlineFloor = 0.005f

/**
 * Where the light is, and what each of the two slopes it finds is painted.
 *
 * **Six controls and no depth**, which is the one a user coming from a drawing program will look for. A depth slider
 * scales the slope where the strengths scale the bands, and the picture cannot tell those apart — halving one and
 * doubling the other lands in the same place. What depth is genuinely for is a bevel that stays as strong as it is
 * widened, and that is guaranteed rather than offered: `LayerBevel.slopeScale` cancels the width out, so Size moves
 * the bevel's reach and nothing else.
 *
 * **Altitude decides what *kind* of relief this is**, which is worth knowing because its name does not say so: a
 * low light rakes across the surface and throws strongly-sided bands, raising it takes the sidedness away, and
 * directly overhead every slope shades equally — the uniform rim of a pillow emboss, which is a look rather than an
 * absence. The strengths decide how strongly what it finds is painted.
 *
 * The two colors sit beside their own strengths rather than in a pair at the top, so each band reads as one thing
 * to set — which is what makes an asymmetric bevel, the difference between something raised and something carved,
 * an obvious thing to reach for rather than an arrangement to work out.
 */
@Composable
internal fun BevelControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val bevel = effects.effectOrNull<LayerEffect.Bevel>() ?: LayerEffect.Bevel()

    SliderControl(
        label = "Size",
        value = bevel.size,
        // Floored above zero for `UnitFloor`'s reason: no slope *is* this effect's identity, so the bottom of the
        // track should leave a tight bevel rather than a silently absent one.
        valueRange = OutlineFloor..HaloReach,
        default = BevelDefaults.size,
        onValueChange = { value -> onUpdate { it.withEffect(bevel.copy(size = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Angle",
        value = bevel.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = BevelDefaults.angleDegrees,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(bevel.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Altitude",
        value = bevel.altitudeDegrees,
        // All the way up, because overhead is a look rather than an off switch: the light stops favoring a side,
        // and what is left is every slope shading equally — the uniform rim of a pillow emboss. Pinned by a test,
        // since the obvious reading is that a bevel flattens away as its light is raised, and it does not.
        valueRange = 0f..90f,
        step = AngleStep,
        default = BevelDefaults.altitudeDegrees,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(bevel.copy(altitudeDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Highlight") {
        ColorField(argb = bevel.highlightArgb) { argb ->
            onUpdate { it.withEffect(bevel.copy(highlightArgb = argb)) }
        }
    }
    SliderControl(
        label = "Highlight strength",
        value = bevel.highlightStrength,
        valueRange = 0f..1f,
        default = BevelDefaults.highlightStrength,
        onValueChange = { value -> onUpdate { it.withEffect(bevel.copy(highlightStrength = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Shadow") {
        ColorField(argb = bevel.shadowArgb) { argb ->
            onUpdate { it.withEffect(bevel.copy(shadowArgb = argb)) }
        }
    }
    SliderControl(
        label = "Shadow strength",
        value = bevel.shadowStrength,
        valueRange = 0f..1f,
        default = BevelDefaults.shadowStrength,
        onValueChange = { value -> onUpdate { it.withEffect(bevel.copy(shadowStrength = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * How far a halo may reach, as a fraction of the box.
 *
 * A fifth is generous — the output is one square and anything past the edge is clipped, so a larger bound would only
 * offer travel that stops doing anything. Shared by the radius and the spread, which reach in the same units.
 */
private const val HaloReach = 0.2f

/** How far a shadow may be thrown. Past this it stops reading as cast by the icon and starts reading as a second one. */
private val ThrowRange = -0.15f..0.15f
