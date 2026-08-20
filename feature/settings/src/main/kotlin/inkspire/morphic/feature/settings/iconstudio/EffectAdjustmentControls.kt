package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.compose.composeBlendMode
import inkspire.morphic.core.model.icon.IconFilter
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect

// The controls for the four `EffectKind.ADJUSTMENT` entries: opacity, blend, color and filter. An adjustment
// transforms pixels already there and its "off" is a value its own controls reach and name, which is why none of
// these carries a switch.

/** How much of the finished layer joins the stack. */
@Composable
internal fun OpacityControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    SliderControl(
        label = "Opacity",
        value = spec.opacity,
        valueRange = 0f..1f,
        default = 1f,
        onValueChange = { value -> onUpdate { it.copy(opacity = value) } },
        onValueChangeFinished = onCommit,
    )
}
/**
 * How the layer combines with what is beneath it.
 *
 * A press **commits**, which it did not before: a blend is a discrete edit with no gesture to punctuate, so
 * without it the change was live but absent from history and undo stepped straight past it. Same rule every
 * source tile follows.
 */
@Composable
internal fun BlendControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // No label of its own: the panel's header already says "Blend", and a section names its parts rather than
    // itself — the same duplicate the Source and Shape panels had.
    //
    // **Flowing rather than scrolling sideways**, which is `SourceControls`' rule and its reason: six modes is a
    // fixed, small set, and there is no reason to hide two of them past an edge. The filter row scrolls because it
    // holds seventeen.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FilterTileGap),
        verticalArrangement = Arrangement.spacedBy(FilterTileGap),
    ) {
        LayerBlend.entries.forEach { blend ->
            BlendTile(blend = blend, selected = spec.blend == blend) {
                onUpdate { it.copy(blend = blend) }
                onCommit()
            }
        }
    }
}
/**
 * One blend mode, shown as **what it does** rather than named and left to be imagined.
 *
 * **The rule the rest of this studio already follows, reaching the last chooser that broke it.** The shape grid gave
 * up its text chips because "the one control on this screen whose entire subject is what something looks like" should
 * draw it; the source tiles are a pack's own artwork; a filter swatch is a reference gradient under that filter's
 * matrix. A blend mode is the same kind of thing and worse as a word — *multiply* and *screen* name arithmetic, not a
 * look, and nothing about the names says which lightens.
 *
 * So a tile is [FilterReferenceStops] — the very same reference image the filter swatches use, which is what makes
 * the two comparable — with one fixed gray shape composited onto it through this mode. Every tile differs only by the
 * mode, so the tile *is* the answer to "what would this do".
 *
 * **Composited offscreen**, or the blend would reach past the swatch and combine with the panel's glass: a `Multiply`
 * with nothing beneath it in its own layer is the same trap `IconLayerStack` documents for the bottom layer of a
 * stack.
 *
 * **The mode comes from the renderer's own mapping** (`composeBlendMode`), so a swatch cannot show one thing while
 * the layer gets another.
 *
 * The name stays underneath: the picture says what it does, the word is what the user has to say to anyone else.
 * Same split as a source tile, which draws a pack and labels it too.
 */
@Composable
private fun BlendTile(blend: LayerBlend, selected: Boolean, onClick: () -> Unit) {
    SwatchTile(selected = selected, onClick = onClick) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(FilterSwatchHeight)
                .clip(EffectSwatchCorner)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(brush = Brush.linearGradient(FilterReferenceStops))
            val side = size.minDimension * BlendSwatchSide
            drawRoundRect(
                color = BlendSwatchSource,
                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * BlendSwatchCorner),
                blendMode = blend.composeBlendMode() ?: DrawScope.DefaultBlendMode,
            )
        }
        SwatchLabel(label = blend.name.lowercase(), selected = selected)
    }
}
/**
 * The shape every blend swatch composites onto the reference.
 *
 * A **mid gray**, deliberately: light enough that `Screen` visibly lifts the colors under it, dark enough that
 * `Multiply` visibly drops them, and neutral enough that `Darken` and `Lighten` differ from both by picking channels
 * rather than by being a different color. A white or black source would collapse half the table into identical
 * tiles.
 */
private val BlendSwatchSource = Color(0xFF9E9E9E)
/**
 * The shape's side, as a share of the swatch's shorter one — leaving the reference visible around it, which is what
 * makes the tile a comparison rather than a color chip.
 *
 * **A rounded square rather than a circle**, and it is the more honest picture: what a blend mode actually combines
 * here is one *layer* over another, and a layer in this studio is an icon-shaped thing. A disc read as an abstract
 * color test; this reads as the operation being demonstrated on the subject it will be used on.
 */
private const val BlendSwatchSide = 0.68f
/** About a quarter of the side — an icon's corner, which is what makes the shape read as a layer. */
private const val BlendSwatchCorner = 0.26f
/**
 * Hue, saturation, brightness and the tint — one `LayerEffect.Color`.
 *
 * **These write one effect, never four**, via `withEffect` — which is why an all-default one is
 * *removed* from the list rather than stored as a row of 1s, and why they share a single switch. Four separate
 * effects would mean their order in the list silently changed the result, which is the failure this shape does not
 * have: they compose into one matrix, in one pass, in a fixed sequence.
 *
 * The sliders sit above the tint because that sequence is the order they act in — recoloring happens first and a
 * [TintMode.SOLID] tint then overwrites the channels it produced.
 */
@Composable
internal fun ColorControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val color = effects.effectOrNull<LayerEffect.Color>() ?: LayerEffect.Color()

    SliderControl(
        label = "Saturation",
        value = color.saturation,
        valueRange = 0f..2f,
        default = 1f,
        onValueChange = { value -> onUpdate { it.withEffect(color.copy(saturation = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Brightness",
        value = color.brightness,
        valueRange = 0.2f..2f,
        default = 1f,
        onValueChange = { value -> onUpdate { it.withEffect(color.copy(brightness = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Hue",
        value = color.hueDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = 0f,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(color.copy(hueDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )

    LabeledControl("Tint") {
        // Clearable because a tint is the one recoloring that cannot be undone by returning a slider to its
        // middle — without a way off, picking one would be a one-way door.
        ClearableColorField(
            argb = color.tintArgb,
            onChange = { argb -> onUpdate { it.withEffect(color.copy(tintArgb = argb)) } },
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
                    onUpdate { it.withEffect(color.copy(tintMode = if (index == 1) TintMode.SOLID else TintMode.MULTIPLY)) }
                    onCommit()
                },
            )
        }
    }
}
/**
 * The built-in color looks: a category to narrow by, then the looks in it.
 *
 * **Two levels, because a flat list of seventeen named swatches is a wall.** The category row is presentation and
 * nothing more — a stored recipe holds an id and has never heard of a category — so regrouping the table later
 * costs nothing and breaks no saved icon.
 *
 * **A swatch shows the look, not the icon.** Every other chooser in this studio draws its own subject, and a
 * filter's subject is what it does to color — so each tile is a fixed reference gradient with that filter's matrix
 * over it. Previewing on the *icon* would have been the obvious alternative and is worse twice over: seventeen live
 * previews of the real stack is seventeen bakes, and an icon that happens to be black tells you nothing about a
 * warm grade. The reference strip is the same for every tile, so the tiles differ only by what the filter did.
 *
 * **"None" is the first tile rather than a clear button**, the shape the shape chooser settled on: unfiltered is a
 * choice among the same set — the one every layer starts on — not an escape from having chosen.
 */
@Composable
internal fun FilterControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val selected = effects.effectOrNull<LayerEffect.Filter>()?.filter
    // Opens on the selected filter's own category, so returning to the panel lands where the look came from
    // rather than at the top — `ca40030`'s rule, one control over.
    var category by rememberSaveable {
        mutableStateOf(selected?.let { IconFilters.entryOrNull(it)?.category } ?: IconFilters.Category.entries.first())
    }

    fun choose(filter: IconFilter?) {
        onUpdate { it.withEffect(filter?.let { id -> LayerEffect.Filter(id) }) }
        onCommit()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledControl("Style") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconFilters.Category.entries.forEach { entry ->
                    ChoiceChip(label = entry.label, selected = category == entry) { category = entry }
                }
            }
        }

        LabeledControl(category.label) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FilterTileGap),
            ) {
                FilterTile(
                    label = "None",
                    matrix = null,
                    selected = selected == null,
                    onClick = { choose(null) },
                )
                IconFilters.inCategory(category).forEach { entry ->
                    FilterTile(
                        label = entry.label,
                        matrix = entry.matrix,
                        selected = selected == entry.filter,
                        onClick = { choose(entry.filter) },
                    )
                }
            }
        }
    }
}
/**
 * One look: the reference gradient under this filter's matrix, named underneath.
 *
 * A null [matrix] is the "None" tile and draws the reference untouched, which is what makes it comparable — the
 * other tiles are that same strip, changed.
 */
@Composable
private fun FilterTile(label: String, matrix: FloatArray?, selected: Boolean, onClick: () -> Unit) {
    SwatchTile(selected = selected, onClick = onClick) {
        Canvas(Modifier.fillMaxWidth().height(FilterSwatchHeight).clip(EffectSwatchCorner)) {
            drawRect(
                brush = Brush.linearGradient(FilterReferenceStops),
                colorFilter = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.copyOf())) },
            )
        }
        SwatchLabel(label = label, selected = selected)
    }
}
/**
 * The shell both labeled swatch tiles sit in: a swatch, its name, one tap target, and the selection ring.
 *
 * **The ring is a sibling drawn over the tile, not a border on anything inside it — because a clip in the way eats
 * its corners, whatever radius that clip is.** Two were in the way, and the second is the one that is easy to miss:
 * the swatch is flush with the tile's top edge, so the *tile's* rounded clip runs across the swatch's two top
 * corners — which is exactly where the ring looked wrong and exactly which two corners.
 *
 * Both radii fail, for different reasons, which is why tuning them was never going to land:
 * - **Different radii cut.** A larger radius removes more of a corner, so a tile clipped at 10 against a swatch
 *   rounded at 8 has its boundary *inside* the swatch's — it takes a bite out of the ring and the swatch together,
 *   thin at the corner and full thickness along the straight sides.
 * - **Equal radii still cut, sub-pixel.** A rounded clip is a hardware outline clip and is not antialiased, so a
 *   boundary running along the ring's own antialiased outer edge drops whole pixels of it. The straight sides
 *   survive (an axis-aligned boundary falls on the pixel grid); the arcs come back thin and stepped.
 *
 * So the tile keeps its clip — shaping the press ripple is all it was ever for — and the ring is drawn by a node
 * that clip does not contain. `Modifier.border` is still what draws it rather than a hand-rolled stroke: it already
 * shrinks the corner radius by half the stroke so the ring's *outer* curvature lands on the shape's own radius, and
 * that is precisely the arithmetic that would be silently wrong if restated here.
 *
 * The ring adds no size, so [content]'s first item — the swatch, which the ring is sized to — is laid out exactly
 * as it was.
 */
@Composable
private fun SwatchTile(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.width(FilterTileWidth)) {
        Column(
            modifier = Modifier
                .clip(SwatchTileCorner)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EffectLabelGap),
            content = content,
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FilterSwatchHeight)
                    .border(SwatchRingWidth, StudioContentColor, EffectSwatchCorner),
            )
        }
    }
}
/** A swatch's name, dimmed until it is the chosen one. Shared so the two tiles cannot style it differently. */
@Composable
private fun SwatchLabel(label: String, selected: Boolean) {
    Text(
        text = label,
        color = StudioContentColor.copy(alpha = if (selected) 1f else 0.7f),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = EffectLabelPad),
    )
}
/**
 * What every swatch is a picture of.
 *
 * Chosen to span the axes a color matrix moves things along — a warm end, a neutral middle and a cool end, with
 * enough saturation to show a desaturating look and enough range to show a contrast one. A single flat color
 * would leave half the table looking identical.
 */
private val FilterReferenceStops = listOf(
    Color(0xFFFFB25E),
    Color(0xFFFF5F6D),
    Color(0xFF7A5CFF),
    Color(0xFF2ED8C3),
)
/**
 * The corner every swatch in this section is cut to — one value, because the clip and the selection ring drawn over
 * it are the *same* rounded rect and a difference between them would show as a sliver of unringed swatch at each
 * corner. Restated per tile it was three chances to drift.
 */
internal val EffectSwatchCorner = RoundedCornerShape(10.dp)
/** The tile around a swatch, which exists only to shape the press ripple. */
private val SwatchTileCorner = RoundedCornerShape(10.dp)
/** The selection ring's stroke, wherever one is drawn in this section. */
internal val SwatchRingWidth = 2.dp
private val FilterTileWidth = 72.dp
private val FilterSwatchHeight = 48.dp
internal val FilterTileGap = 8.dp
