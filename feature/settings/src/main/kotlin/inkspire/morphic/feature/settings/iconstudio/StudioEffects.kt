package inkspire.morphic.feature.settings.iconstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitch
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.model.icon.BloomFalloff
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconFilter
import inkspire.morphic.core.model.icon.IconPattern
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.ShapeAnchor
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.model.icon.activeEffects
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the Effects section is pointed at: one layer, or the finished icon.
 *
 * **The two differ by exactly two entries, and it falls out of a rule this file already had.** An entry either owns
 * a `LayerEffect` — and so carries a switch — or configures an [IconLayerSpec] *field*. Opacity and blend are the
 * fields, they describe how something *joins a stack*, and the composite joins nothing: there is nothing beneath the
 * finished icon to be more or less opaque against. So `ownsEffect` answers both questions, and a new effect is
 * offered on both targets for free.
 *
 * A sum type rather than a nullable spec because the two carry different things and the compiler should say so —
 * `StudioTarget`'s reason, one layer up, where this is the same distinction expressed in what the panel needs.
 */
internal sealed interface EffectTarget {

    /** The effects this target carries, in pipeline order. */
    val effects: List<LayerEffect>

    /** The entries it offers — every one, for a layer. */
    val slices: List<EffectSlice> get() = EffectSlice.entries

    /** One layer of the stack: it has opacity and blend as well, being something that joins a stack. */
    data class Layer(val spec: IconLayerSpec) : EffectTarget {
        override val effects: List<LayerEffect> get() = spec.effects
    }

    /** The finished icon: effects only. */
    data class Composite(override val effects: List<LayerEffect>) : EffectTarget {
        override val slices: List<EffectSlice> get() = EffectSlice.entries.filter { it.ownsEffect }
    }
}

/**
 * One entry in the Effects grid: a job the user can go and do to this layer, with the glyph and word the grid
 * offers it under.
 *
 * **One entry per `LayerEffect`, plus the two spec fields — and that mapping is now load-bearing.** This briefly
 * split `LayerEffect.Color` into *Recolor* and *Tint*, on the reasoning that "balance the color" and "pick a tint"
 * are different things to want. The per-effect switch is what overturned it: `enabled` belongs to the effect, one
 * `Color` record holds both halves, so two entries sharing it could express "tint off, recolor on" — a state the
 * model cannot hold and the renderer would have to guess at. Splitting `Color` in the model instead is worse still:
 * its four numbers compose into a *single* matrix in a fixed sequence, and as separate entries their list order
 * would silently change the result, which is precisely the failure its own KDoc exists to prevent.
 *
 * So the rule is: **an entry that owns a `LayerEffect` gets a switch; one that configures a spec field does not.**
 * [OPACITY] and [BLEND] are fields — always in play, with their "off" being their default value — so there is
 * nothing to enable. Every effect the plan adds is 1:1 and takes a switch for free.
 *
 * The deferred **shadow** is the next entry, and it costs one value here plus its controls — which is the whole
 * point of the grid over a column, since a column would have gained another block of sliders instead.
 */
internal enum class EffectSlice(val label: String, val icon: ImageVector) {

    /** How much of the layer joins the stack at all. */
    OPACITY("Opacity", Icons.Default.Opacity),

    /** How it combines with everything beneath it. */
    BLEND("Blend", Icons.Default.FilterBAndW),

    /** Hue, saturation, brightness and the tint — one `LayerEffect.Color`, one matrix. */
    COLOR("Color", Icons.Default.Tune),

    /**
     * Light or shade spilling across the artwork — the two-stop overlay, linear or radial.
     *
     * **This is the entry that used to read "Gradient"**, and the rename is the rule rather than a preference: every
     * other entry here names a look, so one naming a shader was the odd one out. Nothing was retired *into* it that
     * it could not already do — both stops stay arbitrary, so a duotone is still one edit.
     */
    BLOOM("Bloom", Icons.Default.Gradient),

    /** A sheen struck across the artwork, with a bowed edge between what is lit and what is not. */
    GLOSS("Gloss", Icons.Default.WbTwilight),

    /** A repeating texture laid over the artwork — see `IconPatterns`. */
    PATTERN("Pattern", Icons.Default.Grain),

    /** The layer's own silhouette repeated behind itself, so it reads as a slab. */
    EXTRUDE("Extrude", Icons.Default.Layers),

    /** One of the built-in colour looks — see `IconFilters`. */
    FILTER("Filter", Icons.Default.PhotoFilter),
    ;

    /**
     * Whether this entry configures a `LayerEffect` rather than a spec field — which is exactly the entries that
     * get a switch, since `enabled` is the effect's. [OPACITY] and [BLEND] are always in play and their "off" is
     * their default value.
     */
    val ownsEffect: Boolean get() = this != OPACITY && this != BLEND

    /**
     * The stored effect this entry owns, or null when it configures spec fields instead — or when the effect has
     * simply never been configured, which is what an absent entry in `effects` means.
     *
     * Reads `spec.effects` rather than `spec.color`/`spec.gradient` deliberately: those two drop an identity
     * effect, and this needs to know whether there is a *record* to switch off, not whether it currently paints.
     */
    fun storedEffect(effects: List<LayerEffect>): LayerEffect? = when (this) {
        OPACITY, BLEND -> null
        COLOR -> effects.filterIsInstance<LayerEffect.Color>().firstOrNull()
        BLOOM -> effects.filterIsInstance<LayerEffect.Bloom>().firstOrNull()
        GLOSS -> effects.filterIsInstance<LayerEffect.Gloss>().firstOrNull()
        PATTERN -> effects.filterIsInstance<LayerEffect.Pattern>().firstOrNull()
        EXTRUDE -> effects.filterIsInstance<LayerEffect.Extrude>().firstOrNull()
        FILTER -> effects.filterIsInstance<LayerEffect.Filter>().firstOrNull()
    }

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
    fun isActive(target: EffectTarget): Boolean = when (this) {
        OPACITY -> (target as? EffectTarget.Layer)?.spec?.opacity?.let { it != 1f } == true
        BLEND -> (target as? EffectTarget.Layer)?.spec?.blend?.let { it != LayerBlend.NORMAL } == true
        // `activeEffects` is the renderers' own list, so a tile marks itself exactly when the icon is affected —
        // which means an effect switched off reads as inactive, and correctly so: it is not doing anything.
        COLOR -> target.effects.activeEffects.any { it is LayerEffect.Color }
        BLOOM -> target.effects.activeEffects.any { it is LayerEffect.Bloom }
        GLOSS -> target.effects.activeEffects.any { it is LayerEffect.Gloss }
        PATTERN -> target.effects.activeEffects.any { it is LayerEffect.Pattern }
        EXTRUDE -> target.effects.activeEffects.any { it is LayerEffect.Extrude }
        FILTER -> target.effects.activeEffects.any { it is LayerEffect.Filter }
    }
}

/**
 * How the layer reads: opacity and blend, recoloring, tint, bloom and the built-in looks — as a **grid of entries
 * you open**, rather than every control at once.
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
    target: EffectTarget,
    onEffects: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onLayer: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // Saveable, so a rotation does not drop the user back at the grid. It is *not* hoisted to the ViewModel: which
    // control is open is not part of the recipe, does not belong in undo, and nothing outside this panel asks.
    var open by rememberSaveable { mutableStateOf<EffectSlice?>(null) }

    // **Closed when the target stops offering it**, which is not hypothetical: Opacity belongs to a layer, so moving
    // the selection to the whole icon with that panel open would leave sliders on screen writing to nothing.
    val slice = open?.takeIf { it in target.slices }

    // Back leaves the entry before it leaves the studio. Enabled only when there is somewhere to go back *to*, so
    // the studio's own handler still answers from the grid — nested handlers resolve innermost-enabled-first, which
    // is what makes this two lines rather than a shared piece of state.
    BackHandler(enabled = slice != null) { open = null }

    if (slice == null) {
        EffectGrid(target = target, onOpen = { open = it })
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EffectHeader(
            slice = slice,
            target = target,
            onBack = { open = null },
            onEffects = onEffects,
            onCommit = onCommit,
        )

        // Exhaustive, so an entry cannot be added to the grid without controls behind it — the same reason the
        // tool panel's own `when` lists every section rather than falling through an `else`.
        when (slice) {
            // The two spec fields, reachable only on a layer: `EffectTarget.Composite` does not list them, so the
            // cast is the compiler being told what `slices` already guarantees.
            EffectSlice.OPACITY -> (target as? EffectTarget.Layer)?.let { OpacityControls(it.spec, onLayer, onCommit) }
            EffectSlice.BLEND -> (target as? EffectTarget.Layer)?.let { BlendControls(it.spec, onLayer, onCommit) }
            EffectSlice.COLOR -> ColorControls(target.effects, onEffects, onCommit)
            EffectSlice.BLOOM -> BloomControls(target.effects, onEffects, onCommit)
            EffectSlice.GLOSS -> GlossControls(target.effects, onEffects, onCommit)
            EffectSlice.PATTERN -> PatternControls(target.effects, onEffects, onCommit)
            EffectSlice.EXTRUDE -> ExtrudeControls(target.effects, onEffects, onCommit)
            EffectSlice.FILTER -> FilterControls(target.effects, onEffects, onCommit)
        }
    }
}

/**
 * The entries, [EffectColumns] across and paged.
 *
 * **Paged for the shape chooser's reason, and here the list really is about to get long.** Four entries fit one row
 * today; the plan adds eleven. Paging horizontally is what keeps this section a fixed height however many arrive —
 * the alternative is a vertical scroller inside the panel's own vertical scroller, which makes every drag
 * ambiguous. So adding an effect adds a *page* eventually, never height.
 *
 * **The height is derived, and from the fullest page rather than from the page capacity.** A page holds up to
 * [EffectRows] rows, but today's single page uses one — sizing to the capacity would reserve an empty row under
 * four tiles. Same derive-versus-store rule the shape pager follows, one question further on.
 */
@Composable
private fun EffectGrid(target: EffectTarget, onOpen: (EffectSlice) -> Unit) {
    val slices = target.slices
    val pages = remember(slices) { slices.chunked(EffectColumns * EffectRows) }
    val pagerState = rememberPagerState { pages.size }
    val rows = remember(pages) { pages.maxOf { ceil(it.size / EffectColumns.toFloat()).toInt() } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            // A tile is a square plate plus its label, so a row is taller than it is wide per column. The plate is
            // capped, so past that width the extra goes to the gaps between tiles rather than to the tiles.
            val cell = ((maxWidth - EffectGridSpacing * (EffectColumns - 1)) / EffectColumns)
                .coerceAtMost(EffectTileMax)
            val pageHeight = (cell + EffectLabelHeight) * rows + EffectGridSpacing * (rows - 1)

            HorizontalPager(
                state = pagerState,
                pageSpacing = 8.dp,
                modifier = Modifier.height(pageHeight),
            ) { page ->
                EffectPage(slices = pages[page], target = target, onOpen = onOpen)
            }
        }

        // Absent at one page, where a single dot would say nothing about a pager that cannot be paged.
        if (pages.size > 1) PagerDots(current = pagerState.currentPage, count = pages.size)
    }
}

/**
 * One page of entries.
 *
 * **Plain rows rather than a lazy grid**, for the shape page's reason: a page holds a compile-time-bounded number
 * of tiles, so laziness saves nothing and costs a scroller nested inside the panel's own. The short last row is
 * padded with empty weights, or its tiles would come out wider than the rest.
 */
@Composable
private fun EffectPage(slices: List<EffectSlice>, target: EffectTarget, onOpen: (EffectSlice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EffectGridSpacing)) {
        slices.chunked(EffectColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(EffectGridSpacing)) {
                row.forEach { slice ->
                    // **The cell takes the share; the tile takes a bounded slice of it.** A square tile in a
                    // column that grows with the panel is a square that grows with the panel, and this panel is as
                    // wide as the screen — so on a tablet the tiles would be huge and the grid would scroll for a
                    // handful of entries. Capped, the tiles keep their size and the row spreads them out.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        EffectTile(
                            slice = slice,
                            active = slice.isActive(target),
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
 * Which entry is open, the way back to the grid, and — for an entry that owns an effect — its switch.
 *
 * A breadcrumb rather than a replacement for the panel's own header: that still says "Effects", so the two read as
 * where you are and what you are in.
 *
 * **The switch is disabled until the effect exists**, which is the honest reading of three states in one control.
 * An effect absent from the list has never been configured, so there is nothing to silence and nothing to restore;
 * moving a slider is what brings it into being, and from then on the switch turns it off *keeping* what was tuned.
 * Absent rather than disabled was the alternative and is worse here: a control that appears the moment you touch a
 * slider makes the panel jump under the finger that touched it.
 */
@Composable
private fun EffectHeader(
    slice: EffectSlice,
    target: EffectTarget,
    onBack: () -> Unit,
    onEffects: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back to effects",
            onClick = onBack,
        )
        Text(
            text = slice.label,
            color = StudioContentColor,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )

        // Only where there is a `LayerEffect` to carry the flag — see [EffectSlice].
        if (slice.ownsEffect) {
            val stored = slice.storedEffect(target.effects)
            MorphicSwitch(
                checked = stored?.enabled == true,
                enabled = stored != null,
                onCheckedChange = { on ->
                    // Flipping a switch is discrete, so it records at once and undo steps over it.
                    onEffects { current ->
                        when (slice) {
                            EffectSlice.COLOR -> current.effectOrNull<LayerEffect.Color>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }

                            EffectSlice.FILTER -> current.effectOrNull<LayerEffect.Filter>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }

                            EffectSlice.GLOSS -> current.effectOrNull<LayerEffect.Gloss>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }

                            EffectSlice.PATTERN -> current.effectOrNull<LayerEffect.Pattern>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }

                            EffectSlice.EXTRUDE -> current.effectOrNull<LayerEffect.Extrude>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }

                            else -> current.effectOrNull<LayerEffect.Bloom>()
                                ?.let { current.withEffect(it.copy(enabled = on)) }
                        } ?: current
                    }
                    onCommit()
                },
            )
        }
    }
}

/** How much of the finished layer joins the stack. */
@Composable
private fun OpacityControls(
    spec: IconLayerSpec,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    SliderControl(
        label = "Opacity",
        value = spec.opacity,
        valueRange = 0f..1f,
        step = UnitStep,
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
 * Hue, saturation, brightness and the tint — one `LayerEffect.Color`.
 *
 * **These write one effect, never four**, via `withEffect` — which is why an all-default one is
 * *removed* from the list rather than stored as a row of 1s, and why they share a single switch. Four separate
 * effects would mean their order in the list silently changed the result, which is the failure this shape does not
 * have: they compose into one matrix, in one pass, in a fixed sequence.
 *
 * The sliders sit above the tint because that sequence is the order they act in — recolouring happens first and a
 * [TintMode.SOLID] tint then overwrites the channels it produced.
 */
@Composable
private fun ColorControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val color = effects.effectOrNull<LayerEffect.Color>() ?: LayerEffect.Color()

    SliderControl(
        label = "Saturation",
        value = color.saturation,
        valueRange = 0f..2f,
        step = UnitStep,
        default = 1f,
        onValueChange = { value -> onUpdate { it.withEffect(color.copy(saturation = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Brightness",
        value = color.brightness,
        valueRange = 0.2f..2f,
        step = UnitStep,
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
        // Clearable because a tint is the one recolouring that cannot be undone by returning a slider to its
        // middle — without a way off, picking one would be a one-way door.
        ClearableColorField(
            argb = color.tintArgb,
            onChange = { argb -> onUpdate { it.withEffect(color.copy(tintArgb = argb)) } },
        )
    }

    // **Only once a tint exists**, which is the difference between a mode and a dead control: with no tint set there
    // is nothing for either option to do, and the pair would be two buttons that change nothing.
    //
    // *Shaded* keeps the layer's own light and dark and pushes it toward the colour; *Solid* keeps only the shape and
    // fills it flat. Solid is what makes app-shipped themed icons agree with each other — they arrive black, white or
    // coloured depending on who built them, and only their alpha is meant to be meaningful — and it is the one mode a
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
 * The built-in colour looks: a category to narrow by, then the looks in it.
 *
 * **Two levels, because a flat list of seventeen named swatches is a wall.** The category row is presentation and
 * nothing more — a stored recipe holds an id and has never heard of a category — so regrouping the table later
 * costs nothing and breaks no saved icon.
 *
 * **A swatch shows the look, not the icon.** Every other chooser in this studio draws its own subject, and a
 * filter's subject is what it does to colour — so each tile is a fixed reference gradient with that filter's matrix
 * over it. Previewing on the *icon* would have been the obvious alternative and is worse twice over: seventeen live
 * previews of the real stack is seventeen bakes, and an icon that happens to be black tells you nothing about a
 * warm grade. The reference strip is the same for every tile, so the tiles differ only by what the filter did.
 *
 * **"None" is the first tile rather than a clear button**, the shape the shape chooser settled on: unfiltered is a
 * choice among the same set — the one every layer starts on — not an escape from having chosen.
 */
@Composable
private fun FilterControls(
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
    Column(
        modifier = Modifier
            .width(FilterTileWidth)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(FilterSwatchHeight)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, StudioContentColor, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            drawRect(
                brush = Brush.linearGradient(FilterReferenceStops),
                colorFilter = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.copyOf())) },
            )
        }
        Text(
            text = label,
            color = StudioContentColor.copy(alpha = if (selected) 1f else 0.7f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        )
    }
}

/**
 * What every swatch is a picture of.
 *
 * Chosen to span the axes a colour matrix moves things along — a warm end, a neutral middle and a cool end, with
 * enough saturation to show a desaturating look and enough range to show a contrast one. A single flat colour
 * would leave half the table looking identical.
 */
private val FilterReferenceStops = listOf(
    Color(0xFFFFB25E),
    Color(0xFFFF5F6D),
    Color(0xFF7A5CFF),
    Color(0xFF2ED8C3),
)

private val FilterTileWidth = 72.dp
private val FilterSwatchHeight = 48.dp
private val FilterTileGap = 8.dp

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
private fun BloomControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at zero strength when absent, so the sliders show a coherent bloom before it is turned on rather than
    // jumping to arbitrary values the moment strength leaves zero.
    val bloom = effects.effectOrNull<LayerEffect.Bloom>() ?: LayerEffect.Bloom(strength = 0f)

    LabeledControl("Falloff") {
        MorphicSegmentedButtons(
            options = listOf("Linear", "Radial"),
            selectedIndex = if (bloom.falloff == BloomFalloff.RADIAL) 1 else 0,
            onSelect = { index ->
                val falloff = if (index == 1) BloomFalloff.RADIAL else BloomFalloff.LINEAR
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
            onUpdate { it.withEffect(bloom.copy(argb = argb)) }
        }
    }

    SliderControl(
        label = "Strength",
        value = bloom.strength,
        valueRange = 0f..1f,
        step = UnitStep,
        // Nothing, not `Bloom()`'s own default of 1: reset means "as if untouched", and an unconfigured bloom is
        // the one this panel seeds at zero so it stays invisible until asked for.
        default = 0f,
        onValueChange = { value -> onUpdate { it.withEffect(bloom.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )

    when (bloom.falloff) {
        BloomFalloff.LINEAR -> SliderControl(
            label = "Angle",
            value = bloom.angleDegrees,
            valueRange = 0f..360f,
            step = AngleStep,
            default = 0f,
            format = { "%.0f°".format(it) },
            onValueChange = { value -> onUpdate { it.withEffect(bloom.copy(angleDegrees = value)) } },
            onValueChangeFinished = onCommit,
        )

        // Floored just above zero rather than at it: a disc that reaches nowhere is identity, so a slider that
        // could land there would silently delete the effect the user is in the middle of tuning.
        BloomFalloff.RADIAL -> SliderControl(
            label = "Radius",
            value = bloom.radius,
            valueRange = UnitStep..1.5f,
            step = UnitStep,
            default = 1f,
            onValueChange = { value -> onUpdate { it.withEffect(bloom.copy(radius = value)) } },
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
        checked = bloom.anchor == ShapeAnchor.CONTENT,
        onCheckedChange = { on ->
            onUpdate { it.withEffect(bloom.copy(anchor = if (on) ShapeAnchor.CONTENT else ShapeAnchor.BOX)) }
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
    LabeledControl("Position") {
        when (bloom.falloff) {
            BloomFalloff.RADIAL -> PositionPad(
                x = bloom.offsetX,
                y = bloom.offsetY,
                onValueChange = { x, y -> onUpdate { it.withEffect(bloom.copy(offsetX = x, offsetY = y)) } },
                onCommit = onCommit,
            )

            BloomFalloff.LINEAR -> {
                // The same convention `LayerGradient.endpoints` runs on — 0° straight down, so the direction vector
                // is (sin, cos). Reading it back as a projection is what keeps the slider and the picture agreeing
                // after the angle has been turned.
                val radians = bloom.angleDegrees * PI.toFloat() / 180f
                val dx = sin(radians)
                val dy = cos(radians)

                SteppedSlider(
                    value = bloom.offsetX * dx + bloom.offsetY * dy,
                    valueRange = PositionRange,
                    step = UnitStep,
                    what = "position",
                    onValueChange = { along ->
                        onUpdate { it.withEffect(bloom.copy(offsetX = along * dx, offsetY = along * dy)) }
                    },
                    onValueChangeFinished = onCommit,
                )
            }
        }
    }
}

/**
 * The slab's colour, how deep it runs, which way, and how strongly.
 *
 * **Depth is bounded well short of the box**, and that bound is about cost rather than taste: the extrusion is drawn
 * as many copies of the layer, `LayerExtrude` caps how many, and past the cap the copies spread far enough apart for
 * the edge to stair visibly. A quarter of the box is deep enough to read as a slab and still inside the cap at every
 * bake size.
 *
 * **Black by default**, unlike every other overlay here, because an extrusion is a *shadowed* side rather than a
 * light — it is the one place in this panel where the resting colour is the dark one.
 */
@Composable
private fun ExtrudeControls(
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
        step = UnitStep,
        default = 0f,
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Depth",
        value = extrude.depth,
        valueRange = UnitStep..0.25f,
        step = UnitStep,
        default = 0.15f,
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(depth = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Angle",
        value = extrude.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = 0f,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(extrude.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
}

/**
 * Which texture, in what colour, how large, which way round, and how strongly.
 *
 * **The tiles are chosen from a row and the rest is sliders**, which is the filter panel's arrangement rather than
 * the shape chooser's paged grid: six entries fit a scroll, and unlike shapes they are not the *whole* control —
 * scale and angle change the look at least as much as which tile it is.
 *
 * **"None" is the first tile rather than a clear button**, the shape and filter choosers' shared answer: having no
 * texture is a choice among the same set, and the one every layer starts on.
 *
 * **A swatch draws the tile itself, tiled** — the same thing the icon will get, at the same stencil-into-colour
 * treatment, so what is being chosen is visible rather than named. That costs one small bitmap per tile, which is
 * what makes it affordable where a filter's seventeen live icon previews were not.
 *
 * No *randomize* button, unlike the reference this was drawn from. What it randomizes there cannot be read off a
 * capture — an angle, an offset, a per-tile scatter — and a button that writes a random number into a slider the
 * user can already drag is a novelty rather than a control.
 */
@Composable
private fun PatternControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    val current = effects.effectOrNull<LayerEffect.Pattern>()
    // Seeded from whatever is stored so switching tiles keeps the scale, angle and colour already tuned — the tile
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
            step = UnitStep,
            default = 1f,
            onValueChange = { value -> onUpdate { it.withEffect(pattern.copy(strength = value)) } },
            onValueChangeFinished = onCommit,
        )
        SliderControl(
            label = "Scale",
            value = pattern.scale,
            // Floored well above zero: the tile is floored in pixels anyway, so a smaller number would stop
            // changing anything while the slider went on moving.
            valueRange = 0.05f..1f,
            step = UnitStep,
            default = 0.25f,
            onValueChange = { value -> onUpdate { it.withEffect(pattern.copy(scale = value)) } },
            onValueChangeFinished = onCommit,
        )
        SliderControl(
            label = "Angle",
            value = pattern.angleDegrees,
            valueRange = 0f..360f,
            step = AngleStep,
            default = 0f,
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .then(
                if (selected) Modifier.border(2.dp, StudioContentColor, RoundedCornerShape(8.dp)) else Modifier,
            )
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

/**
 * The sheen's colour, how hard it is struck, where from, and how its edge bows.
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
private fun GlossControls(
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
        step = UnitStep,
        // Nothing, not `Gloss()`'s own default: reset means "as if untouched", and an unconfigured sheen is the one
        // this panel seeds at zero so it stays invisible until asked for.
        default = 0f,
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(strength = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Angle",
        value = gloss.angleDegrees,
        valueRange = 0f..360f,
        step = AngleStep,
        default = 0f,
        format = { "%.0f°".format(it) },
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(angleDegrees = value)) } },
        onValueChangeFinished = onCommit,
    )
    SliderControl(
        label = "Curve",
        value = gloss.curve,
        valueRange = -1f..1f,
        step = UnitStep,
        default = 0f,
        onValueChange = { value -> onUpdate { it.withEffect(gloss.copy(curve = value)) } },
        onValueChangeFinished = onCommit,
    )

    MorphicSwitchRow(
        label = "Fit to artwork",
        supportingText = gloss.anchor.glossHint,
        checked = gloss.anchor == ShapeAnchor.CONTENT,
        onCheckedChange = { on ->
            onUpdate { it.withEffect(gloss.copy(anchor = if (on) ShapeAnchor.CONTENT else ShapeAnchor.BOX)) }
            onCommit()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** @see bloomHint */
private val ShapeAnchor.glossHint: String
    get() = when (this) {
        ShapeAnchor.BOX -> "The sheen stays put; moving the layer slides the artwork under it."
        ShapeAnchor.CONTENT -> "The sheen sits on the artwork and moves, zooms and turns with it."
    }

/** One line saying what the chosen anchor does — the shape section's rule, that a static one would look broken. */
private val ShapeAnchor.bloomHint: String
    get() = when (this) {
        ShapeAnchor.BOX -> "The light stays put; moving the layer slides the artwork under it."
        ShapeAnchor.CONTENT -> "The light sits on the artwork and moves, zooms and turns with it."
    }

/**
 * Four across, two rows to a page — eight entries before a second page is needed, against four today.
 *
 * Three columns was the first cut and made the tiles too big: a phone hands each one most of 110dp, which is a
 * button the size of an app icon for a section that is a menu. Four brings them to roughly 76dp, under the cap
 * below, so the cap now only binds on a tablet.
 */
private const val EffectColumns = 4
private const val EffectRows = 2

/** Between tiles on both axes. */
private val EffectGridSpacing = 8.dp

/** How wide a tile is allowed to get, whatever share of the panel its cell was handed. */
private val EffectTileMax = 96.dp

/** The glyph inside a tile's plate — a signpost, so it sits in the square rather than filling it. */
private const val EffectGlyphFraction = 0.42f

/**
 * The grid the stepper buttons snap to.
 *
 * One step for every 0..1-ish value here — opacity, saturation, brightness, gradient strength — because they are all
 * read the same way and a user who learns one button's feel has learned them all. It puts 0.00, 0.50 and 1.00 on the
 * grid, which are the values people ask for by name, and it matches the zoom slider one section over.
 */
private const val UnitStep = 0.05f

/** Five degrees for both angles, so 45, 90 and 180 are reachable by stepping — the rotation slider's own step. */
private const val AngleStep = 5f
/** What a tile's label adds under its plate — the gap plus one line of `labelSmall`, which is what sizes a page. */
private val EffectLabelHeight = 20.dp
