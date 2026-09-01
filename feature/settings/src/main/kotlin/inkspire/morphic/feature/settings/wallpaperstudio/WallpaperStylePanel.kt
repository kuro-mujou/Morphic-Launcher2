package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.graphics.wallpaper.AmountKnob
import inkspire.morphic.core.graphics.wallpaper.DesignStyle
import inkspire.morphic.core.graphics.wallpaper.Generators
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.WallpaperColorMode
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe
import inkspire.morphic.feature.settings.iconstudio.studioSliderRowStyle
import kotlin.math.roundToInt

/**
 * The studio's *Style* panel: a row of tabs naming the knobs the current design answers to, over the control for
 * whichever is selected.
 *
 * **The tabs are the design's own vocabulary, and the panel does not know them** — it asks the generator, through
 * [DesignStyle]. What "amount", "scale" and "variant" *mean* is different per design (bands, ribs, strokes; a spread,
 * a margin; a direction, a polygon's sides, a blend mode) and most designs answer to only some of them, so a fixed set
 * of controls would be labelled wrongly for most and offer knobs the generator ignores for many. A knob offered and
 * ignored is the silent failure this arrangement exists to prevent: the finger drags, the wallpaper re-renders, and
 * nothing moves.
 *
 * **A tab per knob rather than the knobs stacked**, mirroring the reference studio: the panel then costs one control's
 * height over a full-screen preview that is the whole point of the screen, rather than four rows of chrome across it.
 *
 * **One callback, not one per knob.** Every control here edits a single field of the same immutable [DesignParams], so
 * five lambdas would be that value taken apart and handed over in pieces — and the panel would grow another parameter
 * every time a design needs a knob the model does not have yet. It hands back the whole edited value instead.
 *
 * **It sits on its own scrim, which the chip rows below do not.** A slider is a thin track and a small number over an
 * arbitrary picture — including a white one — where a chip carries its own filled pill. The frosted backdrop the design
 * system defers is what would eventually give the whole bottom bar one ground; until then the scrim is where it is
 * actually needed.
 */
@Composable
internal fun WallpaperStylePanel(
    recipe: WallpaperRecipe,
    tab: StyleTab,
    onSelectTab: (StyleTab) -> Unit,
    onParams: (DesignParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The design's knobs, asked of the generator that reads them rather than tabulated here.
    val style = Generators.forDesign(recipe.design).style
    val params = recipe.params
    val tabs = style.tabs()
    // A design switch can take away the knob that was selected — Contour has a Look, the Mosaic it flips to has none.
    // Color is in every list, so there is always something to fall back to.
    val selected = if (tab in tabs) tab else tabs.first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            // Heavy enough for white labels over the *lightest* wallpaper a design can produce — the cream
            // contour paper is the case that sets this number, not the dark fields most designs open on.
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { entry ->
                ChooserChip(
                    label = style.labelOf(entry),
                    selected = entry == selected,
                    onClick = { onSelectTab(entry) },
                )
            }
        }

        when (selected) {
            StyleTab.AMOUNT -> AmountControl(style.amount, params.density) { onParams(params.copy(density = it)) }

            StyleTab.SCALE -> FractionControl(
                what = style.scale.orEmpty(),
                value = params.scale,
                default = DesignParams().scale,
                onCommit = { onParams(params.copy(scale = it)) },
            )

            StyleTab.IRREGULARITY -> FractionControl(
                what = style.irregularity.orEmpty(),
                value = params.irregularity,
                default = DesignParams().irregularity,
                onCommit = { onParams(params.copy(irregularity = it)) },
            )

            StyleTab.ROUNDNESS -> FractionControl(
                what = style.roundness.orEmpty(),
                value = params.roundness,
                default = DesignParams().roundness,
                onCommit = { onParams(params.copy(roundness = it)) },
            )

            StyleTab.DEPTH -> FractionControl(
                what = style.depth.orEmpty(),
                value = params.depth,
                default = DesignParams().depth,
                onCommit = { onParams(params.copy(depth = it)) },
            )

            StyleTab.VARIANT -> MorphicSegmentedButtons(
                options = style.variant?.options.orEmpty(),
                // Clamped the way a generator clamps it, so the pill sits on the look actually being drawn rather
                // than vanishing on a recipe whose stored index this design does not have.
                selectedIndex = params.variant.coerceIn(0, (style.variant?.options?.size ?: 1) - 1),
                onSelect = { onParams(params.copy(variant = it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            StyleTab.COLOR -> MorphicSegmentedButtons(
                options = WallpaperColorMode.entries.map { it.label },
                selectedIndex = params.colorMode.ordinal,
                onSelect = { onParams(params.copy(colorMode = WallpaperColorMode.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The *amount* knob: a count of the things this design draws, or a plain scale where it draws no countable things.
 *
 * **The slider offers the generator's own counts** — 4 to 22 bands, 300 to 1200 strokes — rather than a percentage,
 * because that is the number the design is actually tuned in, and every step of the track is then a different picture.
 * [AmountKnob.Count] does both directions of that mapping, so the count the row shows is the count the generator will
 * draw.
 */
@Composable
private fun AmountControl(knob: AmountKnob?, density: Float, onSetDensity: (Float) -> Unit) {
    when (knob) {
        is AmountKnob.Count -> MorphicSliderRow(
            value = knob.at(density),
            valueRange = knob.range,
            // Where reset goes: what an untouched recipe resolves to, read from the model that owns that default.
            default = knob.at(DesignParams().density),
            what = knob.label.lowercase(),
            valueLabel = { it.toString() },
            onCommit = { onSetDensity(knob.densityFor(it)) },
            style = studioSliderRowStyle(),
        )

        is AmountKnob.Fraction -> FractionControl(
            what = knob.label,
            value = density,
            default = DesignParams().density,
            onCommit = onSetDensity,
        )

        // Unreachable: the Amount tab is only offered for a design that declares the knob.
        null -> Unit
    }
}

/** A `0..1` knob read as a percentage — the organic-noise family, and the one amount that counts nothing. */
@Composable
private fun FractionControl(what: String, value: Float, default: Float, onCommit: (Float) -> Unit) {
    MorphicSliderRow(
        value = value,
        valueRange = 0f..1f,
        default = default,
        what = what.lowercase(),
        valueLabel = { "${(it * 100).roundToInt()}%" },
        onCommit = onCommit,
        style = studioSliderRowStyle(),
    )
}

/**
 * Which knob the Style panel is showing.
 *
 * Only [COLOR] is offered for every design; the others appear when the current generator declares them. The order is
 * the panel's, and it runs from what a design *is* toward how it is painted — [ROUNDNESS] sits with the shape knobs
 * before [VARIANT], and [DEPTH] past it, because a relief is lighting rather than shape.
 */
internal enum class StyleTab { AMOUNT, SCALE, IRREGULARITY, ROUNDNESS, VARIANT, DEPTH, COLOR }

/**
 * The tabs this design offers, in panel order — never empty, since [StyleTab.COLOR] applies to every design (the color
 * mode is honored by reducing the palette, not by the generator reading it).
 */
internal fun DesignStyle.tabs(): List<StyleTab> = buildList {
    if (amount != null) add(StyleTab.AMOUNT)
    if (scale != null) add(StyleTab.SCALE)
    if (irregularity != null) add(StyleTab.IRREGULARITY)
    if (roundness != null) add(StyleTab.ROUNDNESS)
    if (variant != null) add(StyleTab.VARIANT)
    if (depth != null) add(StyleTab.DEPTH)
    add(StyleTab.COLOR)
}

/** What this design calls [tab] — its own word for three of them, and the studio's for the color mode. */
private fun DesignStyle.labelOf(tab: StyleTab): String = when (tab) {
    StyleTab.AMOUNT -> amount?.label.orEmpty()
    StyleTab.SCALE -> scale.orEmpty()
    StyleTab.IRREGULARITY -> irregularity.orEmpty()
    StyleTab.ROUNDNESS -> roundness.orEmpty()
    StyleTab.VARIANT -> variant?.label.orEmpty()
    StyleTab.DEPTH -> depth.orEmpty()
    StyleTab.COLOR -> "Color"
}
