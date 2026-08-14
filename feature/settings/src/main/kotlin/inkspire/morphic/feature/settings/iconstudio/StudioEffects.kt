package inkspire.morphic.feature.settings.iconstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.TintMode

/**
 * One entry in the Effects grid: a job the user can go and do to this layer, with the glyph and word the grid
 * offers it under.
 *
 * **These are jobs, not model types, and the difference is deliberate.** `LayerEffect.Color` is one record holding
 * hue, saturation, brightness and a tint — but "balance the color" and "pick a tint" are two different things to
 * want, done with different controls, so they are two entries. Nothing about storage changes: both still write one
 * `LayerEffect.Color` through `IconLayerSpec.withColor`, which is what keeps an all-default effect *removed* from
 * the list rather than stored as a row of 1s. Equally [OPACITY] and [BLEND] are spec *fields* rather than effects,
 * and a user adjusting how a layer reads does not care which side of that line a control sits on.
 *
 * The three sliders stay together in [RECOLOR] for the opposite reason: they compose into a single color matrix and
 * are judged against each other, so splitting them would mean leaving and re-entering to balance two of them.
 *
 * The deferred **shadow** is the next entry, and it costs one value here plus its controls — which is the whole
 * point of the grid over a column, since a column would have gained a sixth block of sliders instead.
 */
internal enum class EffectSlice(val label: String, val icon: ImageVector) {

    /** How much of the layer joins the stack at all. */
    OPACITY("Opacity", Icons.Default.Opacity),

    /** How it combines with everything beneath it. */
    BLEND("Blend", Icons.Default.FilterBAndW),

    /** Hue, saturation and brightness — the three that compose into one matrix. */
    RECOLOR("Recolor", Icons.Default.Tune),

    /** A color pushed through the layer, and whether it shades or replaces. */
    TINT("Tint", Icons.Default.Colorize),

    /** The two-stop overlay laid over the artwork. */
    GRADIENT("Gradient", Icons.Default.Gradient),
    ;

    /**
     * Whether this entry is currently doing anything to [spec] — which is what the grid marks, and it is a
     * requirement rather than a decoration.
     *
     * A single column showed every value at once, so "what have I changed?" was answered by looking. A grid hides
     * that behind five taps unless the tiles say it themselves, and a user who cannot see which effects are live
     * has to open all of them to find the one to undo. Marking the tiles gives the information back.
     *
     * Reading `spec.color`/`spec.gradient` is enough for three of these because those accessors already return
     * null for an identity effect — the model's own definition of "not doing anything", so this cannot disagree
     * with what is stored.
     */
    fun isActive(spec: IconLayerSpec): Boolean = when (this) {
        OPACITY -> spec.opacity != 1f
        BLEND -> spec.blend != LayerBlend.NORMAL
        RECOLOR -> spec.color?.let { it.hueDegrees != 0f || it.saturation != 1f || it.brightness != 1f } == true
        TINT -> spec.color?.tintArgb != null
        GRADIENT -> spec.gradient != null
    }
}

/**
 * How the layer reads: opacity and blend, recoloring, tint and the gradient overlay — as a **grid of entries you
 * open**, rather than every control at once.
 *
 * **The column this replaces was the whole problem.** Twelve controls stacked in a panel capped at 320dp meant the
 * section was always scrolling, the thing being adjusted was usually half off-screen, and finding a control meant
 * remembering its position in a list with no landmarks. That gets strictly worse with every effect added, and the
 * sealed effect list exists precisely so effects *are* added — the deferred shadow would have made it fifteen.
 *
 * A grid inverts both: five tiles fit with no scroll at all, adding an effect adds a tile rather than a screenful,
 * and each entry's controls get the whole panel to themselves when opened. It is the same trade the shape section
 * made one tool over — show the choices, then the thing chosen — and the arrangement the reference this was drawn
 * from uses for the same reason.
 *
 * **One section rather than the two tabs this used to be** is unchanged and is what the grid restates: splitting
 * effects across bar entries would grow the rail every time the sealed list grew, where a grid absorbs it. See
 * [StudioTool.EFFECTS].
 *
 * **No monochrome toggle here, deliberately.** Draining a layer of color is what Saturation does, and a toggle
 * beside it would be a lossy alias for it — switching one off has to invent a value to return to, discarding
 * whatever the user had. The word belongs to the *source* that swaps in the app's themed artwork, which is a
 * different mechanism with a different result; see [SourceControls].
 */
@Composable
internal fun EffectsControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Saveable, so a rotation does not drop the user back at the grid. It is *not* hoisted to the ViewModel: which
    // control is open is not part of the recipe, does not belong in undo, and nothing outside this panel asks.
    var open by rememberSaveable { mutableStateOf<EffectSlice?>(null) }

    // Back leaves the entry before it leaves the studio. Enabled only when there is somewhere to go back *to*, so
    // the studio's own handler still answers from the grid — nested handlers resolve innermost-enabled-first, which
    // is what makes this two lines rather than a shared piece of state.
    BackHandler(enabled = open != null) { open = null }

    when (val slice = open) {
        null -> EffectGrid(spec = spec, onOpen = { open = it })

        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EffectHeader(slice = slice, onBack = { open = null })

            // Exhaustive, so an entry cannot be added to the grid without controls behind it — the same reason the
            // tool panel's own `when` lists every section rather than falling through an `else`.
            when (slice) {
                EffectSlice.OPACITY -> OpacityControls(spec, onUpdate, onCommit)
                EffectSlice.BLEND -> BlendControls(spec, onUpdate, onCommit)
                EffectSlice.RECOLOR -> RecolorControls(spec, onUpdate, onCommit)
                EffectSlice.TINT -> TintControls(spec, onUpdate, onCommit)
                EffectSlice.GRADIENT -> GradientControls(spec, onUpdate, onCommit)
            }
        }
    }
}

/**
 * The grid of entries — [EffectColumns] across, wrapping.
 *
 * **Plain rows rather than a lazy grid**, for the shape page's reason: the entry count is a compile-time constant
 * and always will be, so laziness saves nothing and costs a scroller nested inside the panel's own. The short last
 * row is padded with empty weights, or its tiles would come out wider than the rest.
 */
@Composable
private fun EffectGrid(spec: IconLayerSpec, onOpen: (EffectSlice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EffectGridSpacing)) {
        EffectSlice.entries.chunked(EffectColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(EffectGridSpacing)) {
                row.forEach { slice ->
                    // **The cell takes the share; the tile takes a bounded slice of it.** A square tile in a
                    // column that grows with the panel is a square that grows with the panel, and this panel is as
                    // wide as the screen — so on a tablet two rows of them would be taller than the panel is
                    // allowed to be, and the grid would scroll for five entries. Capped, the tiles keep their size
                    // and the row simply spreads them out.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        EffectTile(
                            slice = slice,
                            active = slice.isActive(spec),
                            onClick = { onOpen(slice) },
                            modifier = Modifier.widthIn(max = EffectTileMax),
                        )
                    }
                }
                repeat(EffectColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One entry: a square plate with the glyph, the word underneath.
 *
 * **Labeled, unlike the shape tiles**, and the difference is what each is a picture *of*. A shape tile draws the
 * silhouette that will land on the icon, so the drawing is the answer; an effect has no single picture — "blend"
 * is six modes and "recolor" is three sliders — so a glyph here is a signpost and needs the word to be read.
 *
 * [active] brightens the plate on the same scale the shape tiles use for selection. The two mean slightly
 * different things — chosen there, in play here — but they are the same question to a reader scanning a grid,
 * which is *"which of these is doing something?"*, so they are worth answering the same way.
 */
@Composable
private fun EffectTile(slice: EffectSlice, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = if (active) 0.22f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = slice.icon,
                // The label below is the name, so repeating it here would have a screen reader say it twice.
                contentDescription = null,
                tint = StudioContentColor.copy(alpha = if (active) 1f else 0.7f),
                // A fraction of the plate rather than a dp, so a narrow screen shrinks the glyph with the tile
                // instead of leaving it marooned in a plate it no longer suits.
                modifier = Modifier.fillMaxSize(EffectGlyphFraction),
            )
        }
        Text(
            text = slice.label,
            color = StudioContentColor.copy(alpha = if (active) 1f else 0.7f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
        )
    }
}

/**
 * Which entry is open, and the way back to the grid.
 *
 * A breadcrumb rather than a replacement for the panel's own header: that still says "Effects", so the two read as
 * where you are and what you are in.
 */
@Composable
private fun EffectHeader(slice: EffectSlice, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back to effects",
            onClick = onBack,
        )
        Text(slice.label, color = StudioContentColor, style = MaterialTheme.typography.titleSmall)
    }
}

/** How much of the finished layer joins the stack. */
@Composable
private fun OpacityControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    LabeledControl("Opacity  ${"%.2f".format(spec.opacity)}") {
        MorphicSlider(
            value = spec.opacity,
            onValueChange = { value -> onUpdate { it.copy(opacity = value) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
}

/**
 * How the layer combines with what is beneath it.
 *
 * A press **commits**, which it did not before: a blend is a discrete edit with no gesture to punctuate, so
 * without it the change was live but absent from history and undo stepped straight past it. Same rule every
 * source tile follows.
 */
@Composable
private fun BlendControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    LabeledControl("Blend") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LayerBlend.entries.toList().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { blend ->
                        ChoiceChip(
                            label = blend.name.lowercase(),
                            selected = spec.blend == blend,
                            modifier = Modifier.fillMaxWidth(1f / row.size),
                        ) {
                            onUpdate { it.copy(blend = blend) }
                            onCommit()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Saturation, brightness and hue.
 *
 * **These write one `LayerEffect.Color`, never three**, via `IconLayerSpec.withColor` — which is why an
 * all-default effect is *removed* from the list rather than stored as a row of 1s. Three separate effects would
 * mean their order in the list silently changed the result.
 */
@Composable
private fun RecolorControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    val color = spec.color ?: LayerEffect.Color()

    LabeledControl("Saturation  ${"%.2f".format(color.saturation)}") {
        MorphicSlider(
            value = color.saturation,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(saturation = value)) } },
            valueRange = 0f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Brightness  ${"%.2f".format(color.brightness)}") {
        MorphicSlider(
            value = color.brightness,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(brightness = value)) } },
            valueRange = 0.2f..2f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Hue  ${"%.0f".format(color.hueDegrees)}°") {
        MorphicSlider(
            value = color.hueDegrees,
            onValueChange = { value -> onUpdate { it.withColor(color.copy(hueDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
}

/** A color pushed through the layer, and — once there is one — whether it shades or replaces. */
@Composable
private fun TintControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    val color = spec.color ?: LayerEffect.Color()

    LabeledControl("Tint") {
        // Clearable because a tint is the one recoloring that cannot be undone by returning a slider to its
        // middle — without a way off, picking one would be a one-way door.
        ClearableColorField(
            argb = color.tintArgb,
            onChange = { argb -> onUpdate { it.withColor(color.copy(tintArgb = argb)) } },
        )
    }

    // **Only once a tint exists**, which is the difference between a mode and a dead control: with no tint set there
    // is nothing for either option to do, and the pair would be two buttons that change nothing.
    //
    // *Shaded* keeps the layer's own light and dark and pushes it toward the color; *Solid* keeps only the shape and
    // fills it flat. Solid is what makes app-shipped themed icons agree with each other — they arrive black, white or
    // colored depending on who built them, and only their alpha is meant to be meaningful — and it is the one mode a
    // multiply cannot reach, since black multiplied by anything is still black. See `TintMode`.
    if (color.tintArgb != null) {
        LabeledControl("Tint style") {
            MorphicSegmentedButtons(
                options = listOf("Shaded", "Solid"),
                selectedIndex = if (color.tintMode == TintMode.SOLID) 1 else 0,
                onSelect = { index ->
                    onUpdate { it.withColor(color.copy(tintMode = if (index == 1) TintMode.SOLID else TintMode.MULTIPLY)) }
                    onCommit()
                },
            )
        }
    }
}

/**
 * The gradient overlay's two stops, its direction and how strongly it is laid on.
 *
 * **Strength doubles as the on/off switch**: at zero the effect is identity and `withGradient` drops it from the
 * list entirely, so there is no separate toggle to disagree with the slider. That is the same shape the recoloring
 * controls have — an effect at its defaults is simply not stored.
 */
@Composable
private fun GradientControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at zero strength when absent, so the sliders show a coherent gradient before it is turned on rather
    // than jumping to arbitrary values the moment strength leaves zero.
    val gradient = spec.gradient ?: LayerEffect.Gradient(strength = 0f)

    LabeledControl("Strength  ${"%.2f".format(gradient.strength)}") {
        MorphicSlider(
            value = gradient.strength,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(strength = value)) } },
            valueRange = 0f..1f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("Angle  ${"%.0f".format(gradient.angleDegrees)}°") {
        MorphicSlider(
            value = gradient.angleDegrees,
            onValueChange = { value -> onUpdate { it.withGradient(gradient.copy(angleDegrees = value)) } },
            valueRange = 0f..360f,
            onValueChangeFinished = onCommit,
        )
    }
    LabeledControl("From") {
        ColorField(argb = gradient.startArgb) { argb ->
            onUpdate { it.withGradient(gradient.copy(startArgb = argb)) }
        }
    }
    LabeledControl("To") {
        ColorField(argb = gradient.endArgb) { argb ->
            onUpdate { it.withGradient(gradient.copy(endArgb = argb)) }
        }
    }
}

/** Three across: five entries land as 3 + 2, and the deferred shadow fills the second row rather than starting a third. */
private const val EffectColumns = 3

/** Between tiles on both axes. */
private val EffectGridSpacing = 8.dp

/** How wide a tile is allowed to get, whatever share of the panel its cell was handed. */
private val EffectTileMax = 96.dp

/** The glyph inside a tile's plate — a signpost, so it sits in the square rather than filling it. */
private const val EffectGlyphFraction = 0.42f
