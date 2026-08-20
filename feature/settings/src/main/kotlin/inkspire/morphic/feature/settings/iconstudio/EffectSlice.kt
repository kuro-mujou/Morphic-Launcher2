package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import inkspire.morphic.core.icon.IconFilters
import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerBlend
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.activeEffects

// What one entry in the Effects grid *is*: which effect it edits, whether it can be switched off, what it opens
// at, and where the panel currently sits. Nothing here draws — the controls an entry opens live in the
// `Effect*Controls` files beside this one.

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
     * The layer's tones mapped onto a ramp between two chosen colors — see `LayerEffect.Duotone`.
     *
     * **Beside [COLOR] and [FILTER] because the three are one question asked three ways**: how should these pixels
     * read? Color grades what is there, a filter applies a look somebody authored, and this replaces the app's own
     * palette with two colors of the user's. They are what a user moves between while theming a layer.
     *
     * **An addition rather than an adjustment**, which looks arguable and is not. The test [carriesSwitch] states is
     * whether the entry's *resting* state is its off state: an adjustment rests at an identity its own sliders name,
     * where this arrives at the full ramp because that is what makes it legible. So zero strength is not where it
     * sits when untouched, and its "off" is its absence — which is a switch.
     */
    DUOTONE("Duotone", Icons.Default.Palette, EffectKind.ADDITION),

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

    /**
     * Color gathering in from the edges, the middle left clear — see `LayerEffect.Vignette`.
     *
     * **Third of the three light overlays and grouped with them**, which is what the grid is for: bloom, gloss and
     * vignette are the same source-atop ramp arranged three ways, and a user reaching for one is comparing it
     * against the other two.
     */
    VIGNETTE("Vignette", Icons.Default.Vignette, EffectKind.ADDITION),

    /**
     * The layer read as a raised surface and lit — see `LayerEffect.Bevel`.
     *
     * **Last of the light group, because it is the one whose subject is a *surface* rather than a wash.** Bloom,
     * gloss and vignette all lay light over the artwork; this derives light from the artwork's own shape, which is
     * a different question asked with the same vocabulary — an angle, and how strongly.
     */
    BEVEL("Bevel", Icons.Default.ViewInAr, EffectKind.ADDITION),

    /** A repeating texture laid over the artwork — see `IconPatterns`. */
    PATTERN("Pattern", Icons.Default.Grain, EffectKind.ADDITION),

    /** The layer's own silhouette repeated behind itself, so it reads as a slab. */
    EXTRUDE("Extrude", Icons.Default.Layers, EffectKind.ADDITION),

    /** The layer's color channels displaced and added back together — lens fringing. */
    CHROMATIC("Chromatic", Icons.Default.Tonality, EffectKind.ADDITION),

    /**
     * A hard band following the finished silhouette — see `LayerEffect.Outline`.
     *
     * **First of the five silhouette entries**, which the grid keeps together: an outline, a glow, a shadow, a
     * recess and a rim are one dilation arranged five ways, and a user reaching for one is choosing among them.
     * It leads because it is the hard-edged one the other four are softenings of.
     */
    OUTLINE("Outline", Icons.Default.BorderOuter, EffectKind.ADDITION),

    /** A soft halo around the finished silhouette. Baked, never live — see `LayerEffect.Glow`. */
    GLOW("Glow", Icons.Default.BlurOn, EffectKind.ADDITION),

    /** The finished silhouette blurred, thrown and drawn behind. Baked, never live. */
    SHADOW("Shadow", Icons.Default.FlipToBack, EffectKind.ADDITION),

    /**
     * The same shadow cast by the silhouette's complement and laid back inside it — see `LayerEffect.InnerShadow`.
     *
     * **Labeled "Inset" because the tile cannot hold "Inner shadow"**, which is the trade `PROGRESSIVE_BLUR` made
     * in coming out as "Focus": four columns is one short word, and an ellipsised label names nothing. The word is
     * the look rather than the mechanism, which is this grid's rule anyway — and it is what CSS calls the same
     * thing, so it is not a word invented here.
     *
     * Beside [SHADOW] rather than beside [GLOW], because those two are what it is one of: the same halo, outside and
     * in.
     */
    INNER_SHADOW("Inset", Icons.Default.FlipToFront, EffectKind.ADDITION),

    /**
     * The same halo again, centered on the inside edge and screened onto it — see `LayerEffect.InnerGlow`.
     *
     * **Labeled "Rim"**, on [INNER_SHADOW]'s own precedent: light gathered along an inside edge is a rim light, so
     * the word names the look rather than the mechanism, and it fits a tile at four columns where "Inner glow" does
     * not. Beside [INNER_SHADOW] because those two are the pair, exactly as [GLOW] and [SHADOW] are outside.
     */
    INNER_GLOW("Rim", Icons.Default.TripOrigin, EffectKind.ADDITION),

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
        DUOTONE -> effects.filterIsInstance<LayerEffect.Duotone>().firstOrNull()
        BLOOM -> effects.filterIsInstance<LayerEffect.Bloom>().firstOrNull()
        GLOSS -> effects.filterIsInstance<LayerEffect.Gloss>().firstOrNull()
        VIGNETTE -> effects.filterIsInstance<LayerEffect.Vignette>().firstOrNull()
        BEVEL -> effects.filterIsInstance<LayerEffect.Bevel>().firstOrNull()
        PATTERN -> effects.filterIsInstance<LayerEffect.Pattern>().firstOrNull()
        EXTRUDE -> effects.filterIsInstance<LayerEffect.Extrude>().firstOrNull()
        CHROMATIC -> effects.filterIsInstance<LayerEffect.ChromaticSplit>().firstOrNull()
        OUTLINE -> effects.filterIsInstance<LayerEffect.Outline>().firstOrNull()
        GLOW -> effects.filterIsInstance<LayerEffect.Glow>().firstOrNull()
        SHADOW -> effects.filterIsInstance<LayerEffect.Shadow>().firstOrNull()
        INNER_SHADOW -> effects.filterIsInstance<LayerEffect.InnerShadow>().firstOrNull()
        INNER_GLOW -> effects.filterIsInstance<LayerEffect.InnerGlow>().firstOrNull()
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
        DUOTONE -> DuotoneDefaults
        BLOOM -> BloomDefaults
        GLOSS -> GlossDefaults
        VIGNETTE -> VignetteDefaults
        BEVEL -> BevelDefaults
        PATTERN -> PatternDefaults
        EXTRUDE -> ExtrudeDefaults
        CHROMATIC -> ChromaticDefaults
        OUTLINE -> OutlineDefaults
        GLOW -> GlowDefaults
        SHADOW -> ShadowDefaults
        INNER_SHADOW -> InnerShadowDefaults
        INNER_GLOW -> InnerGlowDefaults
        RIPPLE -> RippleDefaults
        GRAIN -> GrainDefaults
        PIXELATE -> PixelateDefaults
        PROGRESSIVE_BLUR -> ProgressiveBlurDefaults
    }

}
