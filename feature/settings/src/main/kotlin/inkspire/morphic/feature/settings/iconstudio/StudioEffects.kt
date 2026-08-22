package inkspire.morphic.feature.settings.iconstudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitch
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.withEnabled
import kotlin.math.ceil

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
 * **One section rather than a tab per group**, which is what the grid restates: splitting
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
    entry: EffectEntryState,
    onEffects: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onLayer: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // **The grid's page, held here rather than inside `EffectGrid`.** That composable is *disposed* the moment an
    // entry opens — the arm below returns early — so a `rememberPagerState` inside it took the page with it, and
    // coming back from an effect on page two landed on page one. Held one level up, where the panel stays composed
    // for as long as the section is open, it is simply still there.
    val gridPager = rememberPagerState { pageCountOf(target) }

    // **Closed when the target stops offering it**, which is not hypothetical: Opacity belongs to a layer, so moving
    // the selection to the whole icon with that panel open would leave sliders on screen writing to nothing.
    val slice = entry.open?.takeIf { it in target.slices }

    // **Opening an addition applies it, and it stays.** Seeding is what makes an effect legible at all — the
    // defaults were always visible, but nothing wrote them, so tapping Glow showed sliders against an unchanged
    // icon. Now the halo is there before the finger leaves the tile.
    //
    // **It used to be taken back out again if the entry was left untouched**, so that browsing cost nothing. That
    // was the right call while an effect arrived invisible — opening one told you nothing, so it was fair to treat
    // leaving as never having asked. It is the wrong call now that every addition arrives at values chosen to be
    // seen: opening *is* applying, the user watched it happen, and undoing it behind their back reads as the studio
    // refusing what they just did. Undo is the way back, and it is one step because this commits.
    //
    // **Committing is what that costs, and it is not optional.** An uncommitted seed leaves `editing` diverged from
    // the last history entry, so undo would step to the one *before* it and take a prior edit along with the seed.
    // See `IconStudioViewModel.recordHistory`.
    //
    // A `LaunchedEffect` rather than the tile's own click handler because an entry can be arrived at more ways than
    // by pressing its tile: a rotation restores one, and the target changing can move which entries exist.
    //
    // **Keyed on the entry alone, and on nothing about the effects.** Keying on the list too would re-run whenever
    // it changed, and the change that matters is *undo* — stepping back over a seed would immediately re-seed it,
    // so the effect could never be undone at all. The cost is that a record removed while its entry is open is not
    // put back, which is the honest half of that: it was removed on purpose.
    LaunchedEffect(slice) {
        val opened = slice ?: return@LaunchedEffect
        if (opened.storedEffect(target.effects) != null) return@LaunchedEffect
        opened.seeded()?.let { fresh ->
            onEffects { it + fresh }
            onCommit()
        }
    }

    // Back leaves the entry before it leaves the studio. Enabled only when there is somewhere to go back *to*, so
    // the studio's own handler still answers from the grid — nested handlers resolve innermost-enabled-first, which
    // is what makes this two lines rather than a shared piece of state.
    BackHandler(enabled = slice != null) { entry.open(null) }

    if (slice == null) {
        EffectGrid(target = target, pagerState = gridPager, onOpen = { entry.open(it) })
        return
    }

    // **No header here — it is the panel's, pinned above the scroll.** See [EffectEntryState]. This is the body
    // alone, exactly as every other section is.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Exhaustive, so an entry cannot be added to the grid without controls behind it — the same reason the
        // tool panel's own `when` lists every section rather than falling through an `else`.
        when (slice) {
            // The two spec fields, reachable only on a layer: `EffectTarget.Composite` does not list them, so the
            // cast is the compiler being told what `slices` already guarantees.
            EffectSlice.OPACITY ->
                (target as? EffectTarget.Layer)?.let { OpacityControls(it.spec, onLayer, onCommit) }

            EffectSlice.BLEND ->
                (target as? EffectTarget.Layer)?.let { BlendControls(it.spec, onLayer, onCommit) }

            EffectSlice.COLOR -> ColorControls(target.effects, onEffects, onCommit)
            EffectSlice.FILTER -> FilterControls(target.effects, onEffects, onCommit)
            EffectSlice.DUOTONE -> DuotoneControls(target.effects, onEffects, onCommit)
            EffectSlice.BLOOM -> BloomControls(target.effects, onEffects, onCommit)
            EffectSlice.GLOSS -> GlossControls(target.effects, onEffects, onCommit)
            EffectSlice.VIGNETTE -> VignetteControls(target.effects, onEffects, onCommit)
            EffectSlice.BEVEL -> BevelControls(target.effects, onEffects, onCommit)
            EffectSlice.PATTERN -> PatternControls(target.effects, onEffects, onCommit)
            EffectSlice.EXTRUDE -> ExtrudeControls(target.effects, onEffects, onCommit)
            EffectSlice.CHROMATIC -> ChromaticControls(target.effects, onEffects, onCommit)
            EffectSlice.OUTLINE -> OutlineControls(target.effects, onEffects, onCommit)
            EffectSlice.GLOW -> GlowControls(target.effects, onEffects, onCommit)
            EffectSlice.SHADOW -> ShadowControls(target.effects, onEffects, onCommit)
            EffectSlice.INNER_SHADOW -> InnerShadowControls(target.effects, onEffects, onCommit)
            EffectSlice.INNER_GLOW -> InnerGlowControls(target.effects, onEffects, onCommit)
            EffectSlice.RIPPLE -> RippleControls(target.effects, onEffects, onCommit)
            EffectSlice.GRAIN -> GrainControls(target.effects, onEffects, onCommit)
            EffectSlice.PIXELATE -> PixelateControls(target.effects, onEffects, onCommit)
            EffectSlice.PROGRESSIVE_BLUR -> ProgressiveBlurControls(target.effects, onEffects, onCommit)
        }
    }
}

/**
 * The entries, [EffectColumns] across and paged.
 *
 * **Paged for the shape chooser's reason, and the list has since grown into it.** A layer now offers eight entries
 * — exactly one full page — and the plan adds six more, so the next effect is what opens page two. Paging
 * horizontally is what keeps this section a fixed height however many arrive; the alternative is a vertical
 * scroller inside the panel's own vertical scroller, which makes every drag ambiguous. So adding an effect adds a
 * *page* eventually, never height.
 *
 * **The height is derived, and from the fullest page rather than from the page capacity.** They coincide for a
 * layer now that the entries exactly fill a page — but the **composite** offers six, so its page is a row shorter,
 * and sizing to the capacity would leave it a row of nothing. Same derive-versus-store rule the shape pager
 * follows, one question further on.
 */
@Composable
private fun EffectGrid(target: EffectTarget, pagerState: PagerState, onOpen: (EffectSlice) -> Unit) {
    val slices = target.slices
    val pages = remember(slices) { slices.chunked(EffectColumns * EffectRows) }
    val rows = remember(pages) { pages.maxOf { ceil(it.size / EffectColumns.toFloat()).toInt() } }

    val labelBand = effectLabelBand()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            // A tile is a square plate plus its label, so a row is taller than it is wide per column. The plate is
            // capped, so past that width the extra goes to the gaps between tiles rather than to the tiles.
            val cell = ((maxWidth - EffectGridSpacing * (EffectColumns - 1)) / EffectColumns)
                .coerceAtMost(EffectTileMax)
            val pageHeight = (cell + labelBand) * rows + EffectGridSpacing * (rows - 1)

            HorizontalPager(
                state = pagerState,
                pageSpacing = 8.dp,
                // **Top, against the pager's own default of centered.** The height above is the *fullest* page's,
                // so a page with fewer rows than that is genuinely shorter — and centered, its one row floated in
                // the middle of the band while every other page's started at the top, which reads as the grid
                // having moved rather than as a page being short. A grid's first row belongs where the last page
                // left one.
                verticalAlignment = Alignment.Top,
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
 * How many pages [target]'s entries fill.
 *
 * Its own function because two places need the same answer and they are a composable apart: the pager is created
 * where it survives being left ([EffectsControls]) and the pages are laid out where they are drawn ([EffectGrid]).
 * A count derived twice is a count that can disagree, and the symptom would be a pager that refuses its last page.
 */
private fun pageCountOf(target: EffectTarget): Int =
    ceil(target.slices.size / (EffectColumns * EffectRows).toFloat()).toInt().coerceAtLeast(1)

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
        verticalArrangement = Arrangement.spacedBy(EffectLabelGap),
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
                .padding(bottom = EffectLabelPad),
        )
    }
}

/**
 * What a tile's label adds under its plate: the gap above it, one line of `labelSmall`, and the padding beneath.
 *
 * **Read off the type scale rather than stated as a number**, which is what stopped the words being cropped. It was
 * a flat 20dp against the ≈22dp `labelSmall` really occupies at font scale 1 — and more at every accessibility scale
 * above it — and the shortfall does not show as a tight gap at the foot of the grid: this is the number the pager is
 * given as its *height*, and a pager clips, so the bottom row's label lost its descenders and then the word. Derived
 * from the same three quantities [EffectTile] draws with, so a page cannot disagree with the tiles inside it — the
 * shape and the reason of `cellLabelHeight`, one screen over.
 *
 * Rounded up in whole pixels, because text lays out in them: half a pixel short is still a clipped letter.
 */
@Composable
private fun effectLabelBand(): Dp {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    val line = if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * DefaultLineHeightRatio
    return with(density) { ceil(line.toPx()).toDp() } + EffectLabelGap + EffectLabelPad
}

/**
 * Which entry is open, the way back to the grid, and — for an entry that owns an effect — its switch.
 *
 * **Rendered by `StudioToolPanel` in its pinned header band, not here in the body**, which is what stops it scrolling
 * away: a control for leaving a place has to stay where the place is, and Progressive blur's six sliders were already
 * enough to carry the way back off the top. That is also why it *replaces* the panel's own title rather than sitting
 * under it — the band holds one thing, and while you are inside an entry the entry is what you are in. The section's
 * name is still one tap away on the bar.
 *
 * It stays declared here, beside the entries it names, so the Effects section keeps its own vocabulary; the host
 * knows only that there is an entry open, which is [EffectEntryState].
 *
 * **The switch is disabled until the effect exists**, which is the honest reading of three states in one control.
 * An effect absent from the list has never been configured, so there is nothing to silence and nothing to restore;
 * moving a slider is what brings it into being, and from then on the switch turns it off *keeping* what was tuned.
 * Absent rather than disabled was the alternative and is worse here: a control that appears the moment you touch a
 * slider makes the panel jump under the finger that touched it.
 */
@Composable
internal fun EffectHeader(
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

        // **Only the additions**, whose "off" is their absence — see [EffectSlice.carriesSwitch]. An adjustment's
        // off is a value its own controls reach and name, so a switch there was a fifth control repeating four.
        if (slice.carriesSwitch) {
            val stored = slice.storedEffect(target.effects)
            MorphicSwitch(
                checked = stored?.enabled == true,
                // Never off in practice, since opening an addition seeds it — kept as the honest guard for the one
                // frame between the entry composing and the seed landing.
                enabled = stored != null,
                onCheckedChange = { on ->
                    // Flipping a switch is discrete, so it records at once and undo steps over it.
                    //
                    // The record is re-found inside the transform rather than closed over, so this writes to what
                    // the list holds *now*; and `withEnabled` is exhaustive over the sealed type, where the
                    // a forty-line `when` over the slice needs an `else` arm, and an `else` meaning Bloom is how
                    // a new effect toggles the wrong switch.
                    onEffects { current ->
                        val record = slice.storedEffect(current) ?: return@onEffects current
                        current.map { if (it === record) it.withEnabled(on) else it }
                    }
                    onCommit()
                },
            )
        }
    }
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
 * Between a labeled tile's picture and its name, and under the name — the same two on an effect entry and on a
 * swatch. [effectLabelBand] reads them back, because the effect grid's page height is built from them.
 */
internal val EffectLabelGap = 4.dp
internal val EffectLabelPad = 2.dp

/** What a text style's line height is worth when it declares none, matching `IconLabelCell`'s own fallback. */
private const val DefaultLineHeightRatio = 1.2f
