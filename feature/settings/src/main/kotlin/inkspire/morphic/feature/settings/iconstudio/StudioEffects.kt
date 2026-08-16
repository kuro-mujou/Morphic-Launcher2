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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitch
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.compose.composeBlendMode
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.model.icon.Falloff
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconFilter
import inkspire.morphic.core.model.icon.IconPattern
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.ContentAnchor
import inkspire.morphic.core.model.icon.TintMode
import inkspire.morphic.core.model.icon.activeEffects
import inkspire.morphic.core.model.icon.effectOrNull
import inkspire.morphic.core.model.icon.withEffect
import inkspire.morphic.core.model.icon.withEnabled
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
/**
 * Which entry of the Effects grid is open, or null for the grid itself.
 *
 * **It lives above `EffectsControls` because the entry's header is pinned above the scroll**, and only the panel owns
 * that band. The header — back, the effect's name, its switch — used to be the first thing inside the scrolling body,
 * so on a section with more than a screenful of sliders the way back scrolled off the top and the switch went with
 * it. A control for leaving a place has to stay where the place is.
 *
 * That split is what makes this a holder rather than a hoisted value: the panel *renders* the header,
 * `EffectsControls` *renders* the body, and both read and write it.
 *
 * It carried a second field — whether this visit had changed anything — while leaving an entry untouched took the
 * effect back out again. Opening now applies for good, so nobody asks. See `EffectsControls`.
 */
@Stable
internal class EffectEntryState {

    /** The entry showing, or null for the grid. */
    var open: EffectSlice? by mutableStateOf(null)
        private set

    /** Opens [slice], or returns to the grid. */
    fun open(slice: EffectSlice?) {
        open = slice
    }

    companion object {

        /** Saves which entry was open, so a rotation does not drop the user back at the grid. */
        val Saver: Saver<EffectEntryState, String> = Saver(
            save = { it.open?.name ?: "" },
            restore = { name ->
                EffectEntryState().apply {
                    open(EffectSlice.entries.firstOrNull { slice -> slice.name == name })
                }
            },
        )
    }
}

/** @see EffectEntryState */
@Composable
internal fun rememberEffectEntryState(): EffectEntryState =
    rememberSaveable(saver = EffectEntryState.Saver) { EffectEntryState() }

/**
 * What an entry in the Effects grid *does* to the layer — which is what decides whether it carries a switch, and
 * whether opening it seeds anything.
 *
 * **The distinction is the user's, and it holds up:** an adjustment transforms pixels that are already there, an
 * addition puts new ones in. Everything else about how the two behave falls out of that one difference, so it is
 * named once here rather than being re-decided per entry.
 *
 * It is deliberately *not* the same question as `EffectSlice.ownsEffect`, which asks whether there is a stored
 * record. Color and Filter own records and are adjustments; opacity and blend own none and are adjustments too.
 */
internal enum class EffectKind {

    /**
     * Transforms what the layer already has: opacity, blend, color, filter. Rests at an identity its own controls
     * reach and name, so there is nothing to switch and nothing to seed.
     */
    ADJUSTMENT,

    /**
     * Puts something on the layer that was not there: a bloom, a glow, a pattern, a shadow. Its "off" is its
     * absence, which no slider can say, so it carries a switch — and it arrives with values you can see, because
     * an effect whose first impression is that nothing happened teaches the user nothing.
     */
    ADDITION,
}

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
 * The six effects still to come cost one value here each plus their controls — which is the whole point of the grid
 * over a column, since a column would have gained six more blocks of sliders instead. They are also the six that
 * need the bake-backed preview, so the next one added is the first to answer `drawsLive` false.
 */
internal enum class EffectSlice(val label: String, val icon: ImageVector, val kind: EffectKind) {

    /** How much of the layer joins the stack at all. */
    OPACITY("Opacity", Icons.Default.Opacity, EffectKind.ADJUSTMENT),

    /** How it combines with everything beneath it. */
    BLEND("Blend", Icons.Default.FilterBAndW, EffectKind.ADJUSTMENT),

    /** Hue, saturation, brightness and the tint — one `LayerEffect.Color`, one matrix. */
    COLOR("Color", Icons.Default.Tune, EffectKind.ADJUSTMENT),

    /**
     * One of the built-in color looks — see `IconFilters`.
     *
     * **Beside [COLOR] rather than at the end of the grid**, which is where it sat because it was built last. It is
     * the other way of asking the same question — "how should these pixels read?" — and the two are the ones a user
     * moves between while grading a layer, so a page apart was a page too far.
     */
    FILTER("Filter", Icons.Default.PhotoFilter, EffectKind.ADJUSTMENT),

    /**
     * Light or shade spilling across the artwork — the two-stop overlay, linear or radial.
     *
     * **This is the entry that used to read "Gradient"**, and the rename is the rule rather than a preference: every
     * other entry here names a look, so one naming a shader was the odd one out. Nothing was retired *into* it that
     * it could not already do — both stops stay arbitrary, so a duotone is still one edit.
     */
    BLOOM("Bloom", Icons.Default.Gradient, EffectKind.ADDITION),

    /** A sheen struck across the artwork, with a bowed edge between what is lit and what is not. */
    GLOSS("Gloss", Icons.Default.WbTwilight, EffectKind.ADDITION),

    /** A repeating texture laid over the artwork — see `IconPatterns`. */
    PATTERN("Pattern", Icons.Default.Grain, EffectKind.ADDITION),

    /** The layer's own silhouette repeated behind itself, so it reads as a slab. */
    EXTRUDE("Extrude", Icons.Default.Layers, EffectKind.ADDITION),

    /** The layer's color channels displaced and added back together — lens fringing. */
    CHROMATIC("Chromatic", Icons.Default.Tonality, EffectKind.ADDITION),

    /** A soft halo around the finished silhouette. Baked, never live — see `LayerEffect.Glow`. */
    GLOW("Glow", Icons.Default.BlurOn, EffectKind.ADDITION),

    /** The finished silhouette blurred, thrown and drawn behind. Baked, never live. */
    SHADOW("Shadow", Icons.Default.FlipToBack, EffectKind.ADDITION),

    /** Concentric waves pushing the layer's pixels about. Per-pixel, so baked, never live. */
    RIPPLE("Ripple", Icons.Default.Waves, EffectKind.ADDITION),

    /** Noise pushing the layer's pixels about, tearing it into pieces. Per-pixel, so baked, never live. */
    GRAIN("Grain", Icons.Default.Texture, EffectKind.ADDITION),

    /** The layer redrawn as a field of dots, one color per cell. Per-pixel, so baked, never live. */
    PIXELATE("Pixelate", Icons.Default.GridOn, EffectKind.ADDITION),

    /** Sharp in one region and softening away from it. A blur *and* a ramp, so baked, never live. */
    PROGRESSIVE_BLUR("Focus", Icons.Default.BlurLinear, EffectKind.ADDITION),
    ;

    /**
     * Whether this entry configures a `LayerEffect` rather than a spec field.
     *
     * **Orthogonal to [kind], and both are needed.** This one answers "is there a record?" — which is what decides
     * whether the composite offers the entry at all, since [OPACITY] and [BLEND] describe how something joins a
     * stack and the composite joins nothing. [kind] answers "what does it do to the layer?", which is what decides
     * the switch. [COLOR] and [FILTER] are the pair that separates them: they own records *and* are adjustments.
     */
    val ownsEffect: Boolean get() = this != OPACITY && this != BLEND

    /**
     * Whether this entry gets an on/off switch in its panel header — which is exactly the additions.
     *
     * **The line is "can this be off in a way its own controls cannot express?"** An addition's "off" is its
     * absence, and its controls only say *how much*: a bloom at zero strength is still a bloom you asked for, and
     * a user who dialled it down to compare wants the color and angle waiting when they dial it back. That is what
     * a switch is for, and it is why one belongs here.
     *
     * An adjustment's "off" **is** a value its controls reach and name. Color rests at hue 0, saturation 1,
     * brightness 1, no tint — and every one of those sliders already carries a reset disabled at exactly that
     * value, so the switch was a fifth control saying what four already said. Filter is stronger still: its list
     * *contains* "None", so a switch is a second way to pick the same entry — the same reason "no shape" is the
     * first tile in the shape grid rather than a toggle beside it. Opacity and blend are spec fields whose "off" is
     * likewise their default.
     *
     * What this costs is non-destructive A/B on an adjustment — flipping Color off and on to compare, without
     * losing the numbers. If that is wanted back it belongs to the whole icon rather than to one entry, as a
     * press-and-hold on the canvas.
     */
    val carriesSwitch: Boolean get() = kind == EffectKind.ADDITION

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
        CHROMATIC -> effects.filterIsInstance<LayerEffect.ChromaticSplit>().firstOrNull()
        GLOW -> effects.filterIsInstance<LayerEffect.Glow>().firstOrNull()
        SHADOW -> effects.filterIsInstance<LayerEffect.Shadow>().firstOrNull()
        RIPPLE -> effects.filterIsInstance<LayerEffect.Ripple>().firstOrNull()
        GRAIN -> effects.filterIsInstance<LayerEffect.Grain>().firstOrNull()
        PIXELATE -> effects.filterIsInstance<LayerEffect.Pixelate>().firstOrNull()
        PROGRESSIVE_BLUR -> effects.filterIsInstance<LayerEffect.ProgressiveBlur>().firstOrNull()
        FILTER -> effects.filterIsInstance<LayerEffect.Filter>().firstOrNull()
    }

    /**
     * Whether this entry is currently doing anything to [target] — which is what the grid marks, and it is a
     * requirement rather than a decoration.
     *
     * A single column showed every value at once, so "what have I changed?" was answered by looking. A grid hides
     * that behind five taps unless the tiles say it themselves, and a user who cannot see which effects are live
     * has to open all of them to find the one to undo. Marking the tiles gives the information back.
     *
     * **The two kinds are asked different questions, and that is what stops a tile changing under a finger.** An
     * addition reads its **switch** — the user said it is on, so it is marked, even while its strength sits at
     * zero. It used to read `activeEffects`, the renderers' own list, which folds the switch together with "would
     * this paint anything"; the result was a tile that unmarked itself as a slider passed through its floor, with
     * the switch beside it still on. Two controls contradicting each other, on the one gesture that reaches the
     * floor by accident.
     *
     * An adjustment has no switch to read, so identity is the only meaningful answer and there is nothing to
     * contradict: a Color back at its resting values genuinely is doing nothing, and saying so is the whole point
     * of the mark.
     */
    fun isActive(target: EffectTarget): Boolean = when (kind) {
        EffectKind.ADDITION -> storedEffect(target.effects)?.enabled == true

        EffectKind.ADJUSTMENT -> when (this) {
            OPACITY -> (target as? EffectTarget.Layer)?.spec?.opacity?.let { it != 1f } == true
            BLEND -> (target as? EffectTarget.Layer)?.spec?.blend?.let { it != LayerBlend.NORMAL } == true
            // The renderers' own list, so these mark themselves exactly when the icon is affected.
            else -> storedEffect(target.effects)?.let { it in target.effects.activeEffects } == true
        }
    }

    /**
     * A fresh record for this entry at its visible defaults, or null for an entry that owns none.
     *
     * **This is what "opening an effect shows you what it does" is made of.** Every addition's constructor defaults
     * are chosen to be plainly visible (see `LayerEffect.Pixelate.cellSize` for the two that were not), and until
     * this existed none of them were ever *applied* by opening the entry — the panel showed sliders at those values
     * against an icon they had not been written to, so tapping an effect produced no change and taught nothing.
     *
     * Adjustments are absent here on purpose: seeding one means writing its identity, which is a record that says
     * nothing and marks nothing.
     */
    fun seeded(): LayerEffect? = when (this) {
        OPACITY, BLEND, COLOR, FILTER -> null
        BLOOM -> BloomDefaults
        GLOSS -> GlossDefaults
        PATTERN -> PatternDefaults
        EXTRUDE -> ExtrudeDefaults
        CHROMATIC -> ChromaticDefaults
        GLOW -> GlowDefaults
        SHADOW -> ShadowDefaults
        RIPPLE -> RippleDefaults
        GRAIN -> GrainDefaults
        PIXELATE -> PixelateDefaults
        PROGRESSIVE_BLUR -> ProgressiveBlurDefaults
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
            EffectSlice.BLOOM -> BloomControls(target.effects, onEffects, onCommit)
            EffectSlice.GLOSS -> GlossControls(target.effects, onEffects, onCommit)
            EffectSlice.PATTERN -> PatternControls(target.effects, onEffects, onCommit)
            EffectSlice.EXTRUDE -> ExtrudeControls(target.effects, onEffects, onCommit)
            EffectSlice.CHROMATIC -> ChromaticControls(target.effects, onEffects, onCommit)
            EffectSlice.GLOW -> GlowControls(target.effects, onEffects, onCommit)
            EffectSlice.SHADOW -> ShadowControls(target.effects, onEffects, onCommit)
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
                    // forty-line `when` this replaced had an `else` arm that meant Bloom.
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
 * the two comparable — with one fixed grey shape composited onto it through this mode. Every tile differs only by the
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
                )
                // Outermost of the drawing, innermost of the chain: the border above is drawn over the finished
                // swatch rather than being blended into it.
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
        Text(
            text = blend.name.lowercase(),
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
 * The shape every blend swatch composites onto the reference.
 *
 * A **mid grey**, deliberately: light enough that `Screen` visibly lifts the colours under it, dark enough that
 * `Multiply` visibly drops them, and neutral enough that `Darken` and `Lighten` differ from both by picking channels
 * rather than by being a different colour. A white or black source would collapse half the table into identical
 * tiles.
 */
private val BlendSwatchSource = Color(0xFF9E9E9E)

/**
 * The shape's side, as a share of the swatch's shorter one — leaving the reference visible around it, which is what
 * makes the tile a comparison rather than a colour chip.
 *
 * **A rounded square rather than a circle**, and it is the more honest picture: what a blend mode actually combines
 * here is one *layer* over another, and a layer in this studio is an icon-shaped thing. A disc read as an abstract
 * colour test; this reads as the operation being demonstrated on the subject it will be used on.
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
 * How soft the blurred end gets, how much stays sharp, and where the sharp part is.
 *
 * **Labelled *Focus* rather than "Progressive blur"**, which is the reference's name for the mechanism rather than
 * for the look. What a user is doing here is choosing what stays in focus; the blur is how that is expressed. It is
 * also the only entry whose name would not fit a tile at four columns.
 *
 * **The falloff swaps the placement control**, [BloomControls]' rule and the second use of the same enum: a band
 * across the layer is placed by the direction it runs, a disc within it by where its centre sits, and neither value
 * can answer the other's question.
 *
 * **Softness of zero is a real answer**, not a degenerate one — a hard edge between sharp and blurred is a look, and
 * the renderer keeps the two stops a hair apart rather than refusing it.
 */
@Composable
private fun ProgressiveBlurControls(
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
private fun PixelateControls(
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
private fun GrainControls(
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
    // under the very finger dragging that slider, shifting everything beneath it mid-gesture. Greyed out it states
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
 * reaching for at maximum — the icon dispersed into a cloud of its own colours — was not on the control at all.
 */
private const val GrainReach = 0.45f

/**
 * How far the waves push, how many of them, and where they start.
 *
 * **No colour**, unlike every other effect in this panel — a ripple moves the layer's own pixels rather than adding
 * any, so there is nothing to tint. It is the first entry here whose whole subject is *where* the artwork is instead
 * of what colour it comes out.
 *
 * **Waves steps by one**, because it counts crests: a slider that could land on 8.37 of them would be offering a
 * precision the picture cannot show.
 */
@Composable
private fun RippleControls(
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
 * A tenth is already extreme — past it the crests overlap far enough that the artwork stops being recognisable and
 * the effect reads as damage rather than as water.
 */
private const val RippleReach = 0.1f

/**
 * The halo's colour, how strong it is, how far it fades and how far it is grown first.
 *
 * **Spread and radius are two different things and both are needed**, which is the one non-obvious control here. A
 * blur alone leaves the halo at about half strength right at the silhouette's edge and fading immediately, so a glow
 * built from radius alone reads as a smudge; spread grows the silhouette *before* the blur, giving the fade a solid
 * ring to start from. Radius is how soft, spread is how big.
 *
 * **No offset**, unlike [ShadowControls]: a glow is centred on the silhouette by definition, and a halo pushed to one
 * side is a coloured shadow — which is the other entry.
 */
@Composable
private fun GlowControls(
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
 * The shadow's colour, how strong it is, how soft, and where it is thrown.
 *
 * **A radius of zero is a legitimate answer here**, unlike a glow's: a hard-edged silhouette offset behind the layer
 * is a perfectly good shadow, and it is the one a long-shadow look is built from. The renderer reads that as "skip
 * the blur" rather than as nothing, since `BlurMaskFilter` refuses a radius of zero outright.
 *
 * The throw is the transform section's pad at a fraction of its travel, for [ChromaticControls]' reason — a shadow
 * thrown half the icon's width is not a shadow.
 */
@Composable
private fun ShadowControls(
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
 * How far a halo may reach, as a fraction of the box.
 *
 * A fifth is generous — the output is one square and anything past the edge is clipped, so a larger bound would only
 * offer travel that stops doing anything. Shared by the radius and the spread, which reach in the same units.
 */
private const val HaloReach = 0.2f

/** How far a shadow may be thrown. Past this it stops reading as cast by the icon and starts reading as a second one. */
private val ThrowRange = -0.15f..0.15f

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
private fun ChromaticControls(
    effects: List<LayerEffect>,
    onUpdate: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    onCommit: () -> Unit,
) {
    // Seeded at no offset when absent, which is also this effect's own "off" — so the pad opens centred and the
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
 * coloured edges and start reading as three overlapping icons, which is a different — and much less useful — effect.
 */
private val ChromaticRange = -0.05f..0.05f

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
 * The smallest **value** a slider that must not reach zero will take — which was `UnitStep` until that constant
 * became the stepper's increment and the two turned out to be unrelated questions sharing a number.
 *
 * Four sliders bound their floor to it: a bloom's radius, a pixelate's fill, a grain's size and an extrude's depth.
 * Each is a quantity whose zero *is* the effect's identity, so the floor is what keeps dragging to the bottom of the
 * track a very small effect rather than a silently absent one — the switch in the header being where "off" is said.
 * It stays at the value it has always had; only the stepper got finer.
 */
private const val UnitFloor = 0.05f

/**
 * **Each effect as it arrives** — held once, so the value the studio *seeds* and the value a slider's **reset**
 * returns to are the same object's fields rather than two numbers that happen to agree.
 *
 * They did not agree, and the symptom was a panel that lied. Every `Strength` reset was pinned to `0`, on the
 * reading that reset means "neutral" — so opening a fresh effect lit **every** reset button, telling the user they
 * had changed things they had not touched, and pressing one took the effect to invisible rather than back to what
 * they had just been shown. The row is supposed to double as the answer to *"have I changed this?"*, and against a
 * seeded default only one reading makes that true: reset goes to **the value the effect arrives at**.
 *
 * Which is also why these are read rather than restated. A default is tuned in `LayerEffect` — that is where the
 * effect says what it looks like — and a reset target copied by hand into a call site is one edit away from
 * disagreeing with it, silently, in the direction of the bug above.
 *
 * The adjustments need no entry: an unseeded effect arrives at its identity, so `LayerEffect.Color()`'s own neutral
 * *is* both answers, and the sliders that read `1f` and `0f` for hue, saturation and brightness were right all along.
 */
private val BloomDefaults = LayerEffect.Bloom()
private val GlossDefaults = LayerEffect.Gloss()

/**
 * The one addition with no all-default constructor: a pattern has to *be* one, and there is no neutral tile. Dots for
 * the reason `PatternControls` picks it as its own fallback — the most legible of the set at icon size.
 */
private val PatternDefaults = LayerEffect.Pattern(pattern = IconPatterns.Dots)
private val ExtrudeDefaults = LayerEffect.Extrude()
private val ChromaticDefaults = LayerEffect.ChromaticSplit()
private val GlowDefaults = LayerEffect.Glow()
private val ShadowDefaults = LayerEffect.Shadow()
private val RippleDefaults = LayerEffect.Ripple()
private val GrainDefaults = LayerEffect.Grain()
private val PixelateDefaults = LayerEffect.Pixelate()
private val ProgressiveBlurDefaults = LayerEffect.ProgressiveBlur()

/** What a tile's label adds under its plate — the gap plus one line of `labelSmall`, which is what sizes a page. */
private val EffectLabelHeight = 20.dp
