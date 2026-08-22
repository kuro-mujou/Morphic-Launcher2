package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.model.icon.ContentAnchor
import inkspire.morphic.core.model.icon.Falloff
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect

// The controls for the effects that lay light or color *over* a layer: duotone, bloom, gloss and vignette. All
// four are additions, so each opens at its own defaults and carries a switch.

/**
 * The two ends of the ramp, and how far the mapping is taken.
 *
 * **Two colors and no midpoint**, which is `LayerEffect.Duotone`'s own bound rather than a control left out: shifting
 * the balance between the ends is a non-linear remap of luminance, and a color matrix cannot hold one — so a bias
 * slider would cost this effect its live drawing to add a knob nobody named.
 *
 * **Neither field is clearable.** A duotone must have both ends to be a ramp at all, and "no dark end" has no
 * meaning the mapping could act on. Strength is where it is turned down and the header's switch is where it is
 * turned off — the same division every other addition here uses.
 *
 * **The swatch rows are the whole of the picture, so there is no tile grid like [FilterControls]'.** A filter is
 * chosen from a fixed table and has to be *shown* before it can be picked; this one is described by two colors the
 * user already sees on the canvas, so a preview strip would be a second, smaller copy of the icon behind it.
 */
@Composable
internal fun DuotoneControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // The effect's own defaults when absent, so the frame before the seed lands shows the duotone that is about to
    // arrive rather than a different one — `BloomControls`' arrangement and its reason.
    val duotone = effects.effectOrNull<LayerEffect.Duotone>() ?: LayerEffect.Duotone()

    // Dark first, because that is the end the ramp is measured from and the order the arithmetic reads in.
    LabeledControl("Shadows") {
        ColorField(argb = duotone.darkArgb) { argb ->
            onUpdate { it.withEffect(duotone.copy(darkArgb = argb)) }
        }
    }

    LabeledControl("Highlights") {
        ColorField(argb = duotone.lightArgb) { argb ->
            onUpdate { it.withEffect(duotone.copy(lightArgb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = duotone.strength,
        valueRange = 0f..1f,
        default = DuotoneDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(duotone.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * The bloom's falloff, its color, and how strongly it is laid on.
 *
 * **Strength doubles as the on/off switch**: at zero the effect is identity and `withEffect` drops it from the list
 * entirely, so there is no separate toggle to disagree with the slider. That is the same shape the recoloring
 * controls have — an effect at its defaults is simply not stored.
 *
 * **The falloff swaps one slider for another rather than adding one**, which is the whole reason it is an enum: a
 * linear ramp spans the box at every angle so it has no reach to set, and a centered disc has no direction to run
 * in. Showing both and letting one do nothing is the thing this file's own rule forbids — see the tint-style
 * control above, which appears only once a tint exists, for the same rule in its other form.
 */
@Composable
internal fun BloomControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // The effect's own defaults when absent, which is what opening this entry seeds — so the one frame before the
    // seed lands shows the bloom that is about to arrive rather than a different one. It read `strength = 0f` back
    // when nothing was seeded and the panel had to stay invisible until asked for; that is `EffectSlice.seeded`'s
    // job now, and the two must agree.
    val bloom = effects.effectOrNull<LayerEffect.Bloom>() ?: LayerEffect.Bloom()

    // **The defaults of whichever falloff is showing.** Both profiles start identical, so today this is one value
    // either way — written as a lookup rather than as `BloomDefaults.linear` so that giving the two falloffs
    // different arrival values stays the one-line change `BloomProfile` promises, instead of silently leaving every
    // reset in this panel pointing at the ramp's.
    val defaults = if (bloom.falloff == Falloff.LINEAR) BloomDefaults.linear else BloomDefaults.radial

    LabeledControl("Falloff") {
        MorphicSegmentedButtons(
            options = listOf("Linear", "Radial"),
            selectedIndex = if (bloom.falloff == Falloff.RADIAL) 1 else 0,
            onSelect = { index ->
                val falloff = if (index == 1) Falloff.RADIAL else Falloff.LINEAR
                onUpdate { it.withEffect(bloom.copy(falloff = falloff)) }
                onCommit()
            },
        )
    }

    // **One color, not two** — the far end is that color with its alpha gone, so the light fades out of the picture
    // instead of into a second one. Not clearable, since a bloom must be *some* color; strength is how it is turned
    // off, and at zero the effect is dropped from the list entirely.
    LabeledControl("Color") {
        ColorField(argb = bloom.argb) { argb ->
            onUpdate { it.withEffect(bloom.withActive { p -> p.copy(argb = argb) }) }
        }
    }

    SliderControl(
        label = "Strength",
        value = bloom.strength,
        valueRange = 0f..1f,
        // The bloom's own strength, which is what "untouched" means for a *seeded* effect — see [BloomDefaults].
        // It was 0, so this button was lit the moment the panel opened and pressing it made the light invisible.
        default = defaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(bloom.withActive { p -> p.copy(strength = value) }) } },
        onValueChangeFinished = onCommit,
    )

    when (bloom.falloff) {
        Falloff.LINEAR -> SliderControl(
            label = "Angle",
            value = bloom.angleDegrees,
            valueRange = 0f..360f,
            step = AngleStep,
            default = defaults.angleDegrees,
            format = { "%.0f°".format(it) },
            onValueChange = { value -> onUpdate { it.withEffect(bloom.withActive { p -> p.copy(angleDegrees = value) }) } },
            onValueChangeFinished = onCommit,
        )

        // Floored just above zero rather than at it: a disc that reaches nowhere is identity, so a slider that
        // could land there would silently delete the effect the user is in the middle of tuning.
        Falloff.RADIAL -> SliderControl(
            label = "Radius",
            value = bloom.radius,
            valueRange = UnitFloor..1.5f,
            default = defaults.radius,
            onValueChange = { value -> onUpdate { it.withEffect(bloom.withActive { p -> p.copy(radius = value) }) } },
            onValueChangeFinished = onCommit,
        )
    }

    BloomPosition(bloom = bloom, onUpdate = onUpdate, onCommit = onCommit)

    // The shape section's control, on the same enum and through the same derivation — see `ShapeMask`. A bloom and a
    // shape anchored to content on one layer therefore sit on the *same* square, which is what a user putting a
    // highlight inside a trimmed silhouette is relying on without being told.
    MorphicSwitchRow(
        label = "Fit to artwork",
        supportingText = bloom.anchor.bloomHint,
        checked = bloom.anchor == ContentAnchor.CONTENT,
        onCheckedChange = { on ->
            onUpdate {
                it.withEffect(
                    bloom.withActive { p -> p.copy(anchor = if (on) ContentAnchor.CONTENT else ContentAnchor.BOX) },
                )
            }
            onCommit()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Where the light sits — **the transform section's pad for a disc, one slider for a ramp**, because a linear
 * gradient is constant along its own perpendicular and so genuinely cannot see half of a 2D move.
 *
 * The radial case is [PositionPad] unchanged, arrows and center button included: the value is the same *kind* of
 * thing a layer's own offset is, so it should be found and landed on the same way. That is what made the pad worth
 * extracting rather than copying.
 *
 * The model stores a point either way ([LayerEffect.Bloom.offsetX]/`offsetY`, again the layer offset's vocabulary).
 * The linear control writes the **projection** onto its angle and zeroes the rest, so what is stored is always
 * somewhere the ramp could have been put — rather than carrying an invisible sideways component that would appear
 * out of nowhere on switching to radial.
 */
@Composable
private fun BloomPosition(
    bloom: LayerEffect.Bloom,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    when (bloom.falloff) {
        // A pad carries no name of its own, so it takes one; the slider below labels itself.
        Falloff.RADIAL -> LabeledControl("Position") {
            PositionPad(
                x = bloom.active.offsetX,
                y = bloom.active.offsetY,
                onValueChange = { x, y ->
                    onUpdate { it.withEffect(bloom.withActive { p -> p.copy(offsetX = x, offsetY = y) }) }
                },
                onCommit = onCommit,
            )
        }

        // **Writes `along` directly, where it used to write the projection into the disc's own point.** That was the
        // whole of the shared-state bug: the panel did the trigonometry and stored a 2D result, so flipping to
        // radial and back destroyed whatever position had been set there — and flipping the other way handed the
        // ramp a distance it never chose. `LayerEffect.Bloom.placementX` does the projection now, which is where it
        // belongs; this control is one number writing one field.
        Falloff.LINEAR -> SliderControl(
            label = "Position",
            value = bloom.active.along,
            valueRange = PositionRange,
            // The ramp's own, named directly: this arm *is* the linear one, so there is no active profile to look up.
            default = BloomDefaults.linear.along,
            onValueChange = { along -> onUpdate { it.withEffect(bloom.withActive { p -> p.copy(along = along) }) } },
            onValueChangeFinished = onCommit,
        )
    }
}

/**
 * The sheen's color, how hard it is struck, where from, and how its edge bows.
 *
 * **The curve slider is signed and rests at zero**, which is the whole of what separates a gloss from a bloom in the
 * controls: zero is a straight edge, and dragging either way bows it the two opposite directions. A reset at zero
 * therefore means "a flat edge" rather than "no effect" — strength is what switches it off, as everywhere else here.
 *
 * No position pad, unlike the bloom. A sheen is placed by the direction it is struck from and the way its edge bows;
 * a third control for moving the same band across the icon would be a second answer to a question the angle already
 * settles.
 */
@Composable
internal fun GlossControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at zero strength when absent, as the bloom is — so the controls describe a coherent sheen before it is
    // turned on rather than jumping the moment strength leaves zero.
    val gloss = effects.effectOrNull<LayerEffect.Gloss>() ?: LayerEffect.Gloss(strength = 0f)

    LabeledControl("Color") {
        ColorField(argb = gloss.argb) { argb ->
            onUpdate { it.withEffect(gloss.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = gloss.strength,
        valueRange = 0f..1f,
        // Nothing, not `Gloss()`'s own default: reset means "as if untouched", and an unconfigured sheen is the one
        // this panel seeds at zero so it stays invisible until asked for.
        default = GlossDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Angle",
        value = gloss.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = GlossDefaults.angleDegrees,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Curve",
        value = gloss.curve,
        valueRange = -1f..1f,
        default = GlossDefaults.curve,
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(curve = value)) } },
        onValueChangeFinished = onCommit,
    )

    MorphicSwitchRow(
        label = "Fit to artwork",
        supportingText = gloss.anchor.glossHint,
        checked = gloss.anchor == ContentAnchor.CONTENT,
        onCheckedChange = { on ->
            onUpdate { it.withEffect(gloss.copy(anchor = if (on) ContentAnchor.CONTENT else ContentAnchor.BOX)) }
            onCommit()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** @see bloomHint */
private val ContentAnchor.glossHint: String
    get() = when (this) {
        ContentAnchor.BOX -> "The sheen stays put; moving the layer slides the artwork under it."
        ContentAnchor.CONTENT -> "The sheen sits on the artwork and moves, zooms and turns with it."
    }

/** One line saying what the chosen anchor does — the shape section's rule, that a static one would look broken. */
private val ContentAnchor.bloomHint: String
    get() = when (this) {
        ContentAnchor.BOX -> "The light stays put; moving the layer slides the artwork under it."
        ContentAnchor.CONTENT -> "The light sits on the artwork and moves, zooms and turns with it."
    }

/**
 * The vignette's color, how far in it comes, how softly, and how strongly.
 *
 * **No angle and no position pad**, which is the shape of the effect rather than a control left out: a vignette is
 * symmetrical about its frame by definition, so a direction would make it a bloom and an offset would make it a
 * bloom placed off-center. That is the entry beside it, and the two would then be one effect with a switch.
 *
 * **No falloff either**, unlike a bloom's. A ramp with an angle *has* no edge to gather at — it arrives from one
 * side, which is a bloom again — so the linear form of this would not be a vignette at all.
 *
 * Reach and softness are the same pair a Focus reads, through the same `LayerGradient.rampStops`; what differs is
 * only which end the control names, and the model does that conversion so neither renderer has to.
 */
@Composable
internal fun VignetteControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // The effect's own defaults when absent, so the frame before the seed lands shows what is about to arrive.
    val vignette = effects.effectOrNull<LayerEffect.Vignette>() ?: LayerEffect.Vignette()

    // Not clearable: a vignette must be *some* color, and it is turned off by the header's switch. Black is what
    // the word means, and a light one lifting the corners is the same control used the other way.
    LabeledControl("Color") {
        ColorField(argb = vignette.argb) { argb ->
            onUpdate { it.withEffect(vignette.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = vignette.strength,
        valueRange = 0f..1f,
        default = VignetteDefaults.strength,
        onValueChange = { value -> onUpdate { it.withEffect(vignette.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Reach",
        value = vignette.reach,
        // Floored above zero for `UnitFloor`'s stated reason — reaching nowhere *is* this effect's identity, so a
        // slider dragged to the bottom should leave a very small vignette rather than a silently absent one.
        valueRange = UnitFloor..1f,
        default = VignetteDefaults.reach,
        onValueChange = { value -> onUpdate { it.withEffect(vignette.copy(reach = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Softness",
        value = vignette.softness,
        // From zero, unlike Reach: a hard ring is a real look, and `rampStops` keeps the two stops a hair apart so
        // asking for one cannot produce an undefined gradient.
        valueRange = 0f..1f,
        default = VignetteDefaults.softness,
        onValueChange = { value -> onUpdate { it.withEffect(vignette.copy(softness = value)) } },
        onValueChangeFinished = onCommit,
    )

    MorphicSwitchRow(
        label = "Fit to artwork",
        supportingText = vignette.anchor.vignetteHint,
        checked = vignette.anchor == ContentAnchor.CONTENT,
        onCheckedChange = { on ->
            onUpdate { it.withEffect(vignette.copy(anchor = if (on) ContentAnchor.CONTENT else ContentAnchor.BOX)) }
            onCommit()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** @see bloomHint */
private val ContentAnchor.vignetteHint: String
    get() = when (this) {
        ContentAnchor.BOX -> "Gathers at the icon's own edges, wherever the layer sits."
        ContentAnchor.CONTENT -> "Gathers at the artwork's edges and moves, zooms and turns with it."
    }
