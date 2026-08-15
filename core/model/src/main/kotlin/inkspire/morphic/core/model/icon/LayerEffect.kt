package inkspire.morphic.core.model.icon

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A visual effect applied to a single layer while compositing — something that changes what the layer *is*.
 *
 * Deliberately an open sealed hierarchy held in a **list**, rather than one fixed nullable field per effect. That
 * is a direct lesson from L1, which hard-coded a column per effect and paid for it in repeated schema churn: here
 * a new effect is a new variant with `@SerialName`, and a stored recipe written by a build that has it still loads
 * on one that does not, because `ignoreUnknownKeys` drops what it cannot read.
 *
 * **Opacity and blend mode are deliberately *not* here.** They live on [IconLayerSpec] as fields, because they
 * describe how a layer joins the stack rather than what it looks like — see [LayerBlend].
 *
 * A **shadow** is the next variant, and the one that is not additive — see the plan's deferral note.
 */
/**
 * How a [LayerEffect.Color.tintArgb] is laid onto the layer it tints.
 *
 * **Two modes because a multiply cannot lift a color, and one real case needs lifting.** App-shipped themed-icon
 * layers are not consistent in the wild — some ship a black glyph, some white, some a colored one, some artwork that
 * is not a silhouette at all — and the platform's own contract is that *only their alpha is meaningful*, the consumer
 * tinting them to whatever it wants. A multiply cannot do that: black times any tint is still black, so a black glyph
 * can never be made white. [SOLID] is what makes the app's shipped color stop mattering.
 *
 * Persisted inside the layer set as part of [LayerEffect.Color], so the names are an on-disk contract.
 */
@Serializable
enum class TintMode {

    /**
     * Multiplies each channel by the tint's, pushing the layer toward that color **while keeping its own shading**.
     * The only mode there used to be, and still the right one for tinting artwork that has internal detail.
     */
    @SerialName("multiply")
    MULTIPLY,

    /**
     * Replaces the color outright and **keeps only the alpha**, so the layer becomes a flat silhouette of itself.
     * The platform's own treatment of a themed-icon layer, and the way to make apps that ship different colors agree.
     *
     * Everything that recolors is spent by this: [LayerEffect.Color.hueDegrees], `saturation` and `brightness` all
     * act on channels this then overwrites. That falls out of the matrix arithmetic rather than being special-cased,
     * and it is correct — a flat color has no shading left for them to act on.
     */
    @SerialName("solid")
    SOLID,
}

/**
 * The geometry a ramp follows: a line across the frame, or a circle out from a point.
 *
 * **Shared by [LayerEffect.Bloom] and [LayerEffect.ProgressiveBlur]**, which is why it is not named for either. It
 * was `BloomFalloff` while the bloom was the only thing that ramped; the blur is the second consumer and asks the
 * identical question, so it took the honest name rather than gaining a duplicate enum with the same two values.
 * Renaming the *type* costs nothing on disk — the `@SerialName`s below are the contract, and each effect's own field
 * is still called `falloff`.
 *
 * **Each form has exactly one geometric parameter, and it is not the same one**, which is why this is an enum rather
 * than a flag beside two always-visible sliders: a [LINEAR] ramp is decided by the direction it runs and spans its
 * frame whatever that direction is, where a [RADIAL] one is decided by where its centre sits. Neither value can
 * answer the other's question, so the studio shows one control or the other and this is what it asks.
 *
 * Persisted inside the layer set, so the names are an on-disk contract.
 */
@Serializable
enum class Falloff {

    /** A ramp running across the whole frame along an angle. */
    @SerialName("linear")
    LINEAR,

    /** A ramp running outward from a point — a glow from the middle, a vignette, or a blur that spreads. */
    @SerialName("radial")
    RADIAL,
}

/**
 * One complete set of a [LayerEffect.Bloom]'s settings — a bloom holds **two**, one per [Falloff], and shows the one
 * its falloff selects.
 *
 * **Two whole profiles rather than a shared set plus the bits that differ**, and the difference is what a user is
 * doing when they flip that switch: comparing two arrangements, not editing one through two lenses. With anything
 * shared, turning a disc down to a whisper and flipping to linear hands back a ramp nobody dimmed — and the sharing
 * was worst on position, where the panel resolved a ramp's distance into the disc's own point and destroyed it.
 *
 * **Each profile carries every field, including the ones its own falloff ignores**, and that is deliberate rather
 * than sloppy. It makes "each control keeps its own value per falloff" literally true with one type instead of two
 * plus a shared third, and the unused fields cost nothing: they are never written, so `encodeDefaults = false`
 * never stores them. It also leaves the door open — giving the two falloffs *different* defaults for a field they
 * both use is a one-line change at [LayerEffect.Bloom]'s constructor rather than a reshape.
 *
 * The defaults here are the arrangement a bloom **arrives** in, chosen to be plainly visible; see
 * `EffectSlice.seeded`. They are also what each slider's reset returns to, which is one fact read from one place
 * rather than two numbers kept in step by hand.
 *
 * @property argb the light's color. The far end of the ramp is this color with its alpha gone, so it fades out of
 *   the picture rather than into a second one.
 * @property strength how strongly it is laid on; 0 is invisible. A separate knob from [argb]'s own alpha because
 *   the color picker has no alpha channel by design, and because this is the one a user reaches for.
 * @property anchor what the light is placed against — the icon's box, or this layer's artwork carried by its
 *   transform. [ContentAnchor.BOX] leaves it where it is put while the content slides underneath;
 *   [ContentAnchor.CONTENT] sits it on the ink and moves, zooms and turns with it. The same question a shape mask
 *   asks, answered by the same enum through the same derivation, which is what stops the two drifting apart.
 * @property angleDegrees the direction a ramp runs, clockwise from "straight down"; 0 is top-to-bottom.
 *   [Falloff.LINEAR]'s.
 * @property along how far the ramp is pushed along its own [angleDegrees], as a fraction of the frame.
 *   [Falloff.LINEAR]'s. **A scalar rather than a point**, because a linear gradient is constant along its own
 *   perpendicular and so genuinely cannot see a sideways move — storing one would be storing a value nothing can
 *   ever observe, which is exactly what the old shared offsets did.
 * @property radius how far a disc reaches, as a fraction of the way to the frame's corners; 1 covers it entirely.
 *   [Falloff.RADIAL]'s — a ramp always spans its frame.
 * @property offsetX where a disc sits, as a fraction of the frame from its centre. Positive is toward the frame's
 *   own right, which is the artwork's right under [ContentAnchor.CONTENT] — so a light placed on a corner of the
 *   artwork stays on that corner when the layer turns. [Falloff.RADIAL]'s.
 * @property offsetY the same, downward. [Falloff.RADIAL]'s.
 */
/**
 * One complete set of a [LayerEffect.ProgressiveBlur]'s settings — the blur holds **two**, one per [Falloff].
 *
 * [BloomProfile]'s shape and its whole argument, applied to the other effect that has two forms. The sharing was the
 * same and so was its cost: the blur's radius, sharp area and softness were one set between the two, so tuning a
 * radial focus and flipping to linear to compare handed back a ramp already carrying the disc's numbers — which is
 * not a comparison, it is the same edit seen twice.
 *
 * Each profile carries every field including the ones its falloff ignores, for [BloomProfile]'s stated reason.
 *
 * **There is no `along` here, unlike a bloom's ramp**, and that is a real difference rather than an omission: a
 * progressive blur's linear form is placed entirely by [sharpArea] and [softness] — where along its angle the sharp
 * band sits and how far it takes to soften — so a distance would be a second control for the position the band
 * already has. The disc needs [centerX] / [centerY] because a band has a *width* while a disc has a *place*.
 *
 * @property radius how far the blurred copy is softened, as a fraction of the box. 0 is no blur, which is how the
 *   effect is identity.
 * @property sharpArea how much of the frame stays fully sharp before the ramp starts, 0..1.
 * @property softness how much of the frame the ramp takes to reach full blur.
 * @property angleDegrees which way the band runs, clockwise from "straight down". [Falloff.LINEAR]'s.
 * @property centerX where the sharp disc sits, as a fraction of the frame from its centre. [Falloff.RADIAL]'s.
 * @property centerY the same, downward. [Falloff.RADIAL]'s.
 */
@Serializable
data class BlurProfile(
    /**
     * **A visible default**, for `LayerEffect.Pixelate.cellSize`'s reason exactly: this rested at zero, which is the
     * value `isIdentity` reads, so the effect drew nothing until its first slider moved. Half the panel's own reach,
     * which on a 96dp bake is a soft edge you cannot mistake for a sharp one.
     */
    val radius: Float = 0.05f,
    val sharpArea: Float = 0.2f,
    val softness: Float = 0.4f,
    val angleDegrees: Float = 0f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
)

@Serializable
data class BloomProfile(
    val argb: Int = 0xFFFFFFFF.toInt(),
    val strength: Float = 0.6f,
    val anchor: ContentAnchor = ContentAnchor.CONTENT,
    val angleDegrees: Float = 45f,
    val along: Float = -0.4f,
    val radius: Float = 1.5f,
    val offsetX: Float = -0.5f,
    val offsetY: Float = -0.5f,
)

/**
 * Which way a [LayerEffect.Grain]'s noise pushes the pixels it displaces.
 *
 * **Two forms rather than a slider between them**, which is [Falloff]'s shape and its reason: an angle means
 * nothing to noise that pushes every way at once, so a continuous "directionality" would leave the angle control
 * inert at one end and the panel changing height as the slider crossed zero. A discrete choice makes the panel
 * change once, deliberately, when the user asks for the form that has a direction.
 *
 * Persisted inside the layer set, so the names are an on-disk contract.
 */
@Serializable
enum class GrainDrift {

    /** Every pixel pushed its own way — the dissolve, and what "grain" means unqualified. */
    @SerialName("free")
    FREE,

    /** Every pixel pushed along one axis, so the artwork tears into bands rather than into blobs. */
    @SerialName("directed")
    DIRECTED,
}

@Serializable
sealed interface LayerEffect {

    /**
     * Whether this effect draws at all. **Off is not the same as absent**, which is the whole reason it exists: an
     * effect with five parameters and a color is something a user tunes and then wants to *compare against*, and
     * removing it from the list to switch it off would throw that tuning away. Absent from the list means never
     * configured; present and `false` means set up and silenced.
     *
     * Persisted, and defaulted true — with `encodeDefaults = false` on both icon stores, an effect nobody switched
     * off costs nothing on disk and every recipe written before this existed reads back unchanged.
     */
    val enabled: Boolean

    /**
     * True when this would not change a single pixel, so both renderers can skip it and the editor can tell a
     * configured effect from a default one.
     *
     * On the interface rather than per variant because [IconLayerSpec.activeEffects] filters the whole list at
     * once — before this it was two identically-named properties that no shared code could reach.
     */
    val isIdentity: Boolean

    /**
     * Whether the **live** render path can draw this effect, or whether the studio has to preview the layer from
     * its bake instead.
     *
     * **Not persisted** — it is a property of what we can implement, not of the icon — which is why it is a body
     * `get()` on each variant rather than a constructor parameter. The bake has no such flag because it has no
     * such limit: it owns a software bitmap, so a blur is a `BlurMaskFilter` and a displacement is arithmetic over
     * an `IntArray`, at every API level.
     *
     * Declared here so that adding an effect *forces* the question to be answered once, in the model, instead of
     * each renderer guessing. See `docs/ICON_EFFECTS_PLAN.md` §2.
     */
    val drawsLive: Boolean

    /**
     * Recoloring, as one color matrix: hue rotation, then saturation, then brightness, then an optional [tintArgb].
     *
     * One variant rather than four because they compose into a single matrix and are applied in one pass — splitting
     * them would mean four list entries whose *order* silently changed the result, which is a way to be wrong that
     * this shape simply does not have.
     *
     * **Monochrome is this, not a variant of its own**: `saturation = 0` with a [tintArgb] is a tinted grayscale,
     * which is what L1's monochrome fallback computed. Note that is a different thing from
     * [LayerSource.AppDefaultMonochrome], which swaps in artwork the *app* ships; this recolors whatever is there.
     *
     * @property tintArgb the color [tintMode] applies; null leaves the channels alone. The tint's own alpha is
     *   ignored — [IconLayerSpec] already has opacity, and two ways to set one thing is one too many.
     * @property tintMode how [tintArgb] is applied. Defaults to [TintMode.MULTIPLY], which is what a tint has
     *   always meant here, so every stored recipe reads back unchanged.
     * @property saturation 0 is grayscale, 1 unchanged, above 1 oversaturated.
     * @property brightness a plain multiplier on the color channels; 1 is unchanged.
     * @property hueDegrees rotation around the color wheel, 0 unchanged.
     */
    @Serializable
    @SerialName("color")
    data class Color(
        val tintArgb: Int? = null,
        val saturation: Float = 1f,
        val brightness: Float = 1f,
        val hueDegrees: Float = 0f,
        val tintMode: TintMode = TintMode.MULTIPLY,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        override val isIdentity: Boolean
            get() = tintArgb == null && saturation == 1f && brightness == 1f && hueDegrees == 0f

        /** A color matrix, which both paths already share through `LayerFilter`. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * Light spilling across the layer: [argb] fading out to nothing, painted **over it and clipped to it** —
     * source-atop, so it colors the artwork rather than covering the icon with a rectangle.
     *
     * **This is what used to be called `Gradient`, and it is one color now rather than two.** The rename is because
     * every other entry in this list names a *look* — a blend, a filter, a color — where "gradient" named the
     * mechanism. Fading to transparent rather than to a second chosen color is the bigger change and it is what
     * makes the effect usable: with two opaque stops, source-atop *replaces* every pixel it covers, so a
     * white-to-black bloom at full strength obliterated the artwork it was supposed to light. What is given up is the
     * two-arbitrary-stop duotone the general control also allowed; a tint plus a bloom reaches most of it.
     *
     * **The `@SerialName` stays `"gradient"` deliberately, even though the shape broke.** An unknown discriminator
     * is not skipped the way an unknown *key* is — it throws, and `IconLayerSetCodec` drops the **whole recipe** on
     * a throw, where an unreadable field costs one color. So the settings layer's rule that a key name is the seam
     * for a semantic break does not transfer here: the blast radius is a whole icon rather than one slice. Stored
     * recipes lose their two stops (the old keys are dropped, [argb] defaults to white) and keep everything else.
     *
     * ### The two falloffs are two whole blooms, and [falloff] picks which one is showing
     *
     * **A ramp and a disc are not one set of settings with a switch on it.** They had one set between them, so every
     * control the two shared carried across: turning a disc's strength down and flipping to linear gave a dim ramp
     * nobody had asked for. Worse for position — a ramp is placed by a distance along its angle and a disc by a
     * point, and the panel resolved the ramp's distance into the disc's own point, so flipping to radial and back
     * destroyed whatever position had been set there.
     *
     * So a bloom holds **two complete [BloomProfile]s** and [falloff] selects one. Each is arrived at, tuned and
     * left alone independently; flipping between them is looking at two arrangements rather than editing one through
     * two lenses.
     *
     * **Every reader goes through [active], which is what made this cost the renderers nothing.** Both draw paths
     * read `bloom.argb`, `bloom.strength`, `bloom.angleDegrees` and `bloom.radius`, and they still do — the
     * forwarding accessors below resolve the profile once, where two paths each choosing one could choose
     * differently. [placementX] is the same argument for the position, and doing that projection here rather than in
     * the panel is what made the split possible at all: turning "how far along the angle" into a point is arithmetic
     * about the model, and the UI storing its *result* was how the two falloffs came to overwrite each other.
     *
     * @property falloff whether the light runs across the frame or out from a point in it, and therefore which of
     *   [linear] and [radial] is in play. See [Falloff].
     * @property linear the ramp's own settings — its [BloomProfile.angleDegrees] and [BloomProfile.along] are the
     *   ones that mean anything to it.
     * @property radial the disc's — [BloomProfile.radius] and [BloomProfile.offsetX] / [BloomProfile.offsetY].
     */
    @Serializable
    @SerialName("gradient")
    data class Bloom(
        val falloff: Falloff = Falloff.LINEAR,
        val linear: BloomProfile = BloomProfile(),
        val radial: BloomProfile = BloomProfile(),
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** The profile [falloff] selects — every reader below goes through it, and so does every write. */
        val active: BloomProfile get() = if (falloff == Falloff.LINEAR) linear else radial

        /**
         * The active profile's fields, forwarded.
         *
         * **This is what let the split cost the renderers nothing.** Both of them read `bloom.argb`,
         * `bloom.strength`, `bloom.angleDegrees` and `bloom.radius`, and they still do — the resolution happens
         * here, once, rather than in two paths that would each have to pick the right profile and could pick
         * differently. That is the same reason `placementX` exists rather than a branch in `LayerGradient`.
         */
        val argb: Int get() = active.argb

        /** @see argb */
        val strength: Float get() = active.strength

        /** @see argb */
        val anchor: ContentAnchor get() = active.anchor

        /** @see argb */
        val angleDegrees: Float get() = active.angleDegrees

        /** @see argb */
        val radius: Float get() = active.radius

        /**
         * Where the light sits in the frame, whichever falloff is placing it — a fraction of the frame from its
         * centre, which is what `LayerGradient.frameOf` moves by.
         *
         * A disc reads its own point. A ramp resolves [BloomProfile.along] onto [angleDegrees], the same
         * `(sin, cos)` convention `LayerGradient.endpoints` runs on, so pushing it "forward" moves it the way the
         * light travels.
         */
        val placementX: Float
            get() = if (falloff == Falloff.LINEAR) active.along * sin(angleRadians) else active.offsetX

        /** @see placementX */
        val placementY: Float
            get() = if (falloff == Falloff.LINEAR) active.along * cos(angleRadians) else active.offsetY

        private val angleRadians: Float get() = angleDegrees * PI.toFloat() / 180f

        /**
         * This bloom with [transform] applied to whichever profile is active — how every control in the panel
         * writes, so none of them has to know which falloff it is editing.
         */
        fun withActive(transform: (BloomProfile) -> BloomProfile): Bloom =
            if (falloff == Falloff.LINEAR) copy(linear = transform(linear)) else copy(radial = transform(radial))

        /**
         * Painting nothing, either because it was turned down to nothing or because a radial one reaches nowhere.
         *
         * That second clause is **not** cosmetic: `RadialGradient` rejects a radius of zero or less outright, so
         * without it the one value a slider can always be dragged to would crash the bake. The renderers still
         * clamp, since a recipe is not obliged to be sensible — but an effect that would draw nothing should say so
         * here, where [IconLayerSpec.activeEffects] filters it out before either path is reached.
         */
        override val isIdentity: Boolean
            get() = strength <= 0f || (falloff == Falloff.RADIAL && radius <= 0f)

        /** A shader drawn source-atop, which both paths can do at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * A sheen struck across the layer: [argb] on one side of a bowed edge, fading out across it. Source-atop, like
     * [Bloom], so it lights the artwork rather than covering the icon with a shape.
     *
     * **The difference from [Bloom] is the *edge*, and it is what makes this its own effect rather than a preset.**
     * A bloom is a ramp or a disc — light with no boundary. A gloss has one: a region that is lit, a region that is
     * not, and an arc between them. That is what a highlight on a glossy surface looks like, and neither of a bloom's
     * two falloffs can produce it.
     *
     * @property angleDegrees where the light comes from, clockwise from straight down — so 0 lights the top, the same
     *   convention [Bloom.angleDegrees] runs on.
     * @property curve how the boundary bows, −1..1. **One control doing two things deliberately**: its magnitude is
     *   how tightly curved the edge is (0 is very nearly straight), its sign is which way it bows — the lit region
     *   bulging out, or the arc cutting into it. The light stays on the side [angleDegrees] names either way, so the
     *   sign can never be mistaken for a half turn.
     * @property strength how strongly the sheen is laid on; 0 is invisible, and is how it is switched off.
     * @property anchor what the sheen is placed against — the icon's box, or this layer's artwork carried by its
     *   transform. The same enum a shape mask and a bloom take, through the same derivation.
     */
    @Serializable
    @SerialName("gloss")
    data class Gloss(
        val argb: Int = 0xFFFFFFFF.toInt(),
        val angleDegrees: Float = 225f,
        val curve: Float = -1f,
        val strength: Float = 0.2f,
        val anchor: ContentAnchor = ContentAnchor.CONTENT,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Turned down to nothing is the only way a sheen paints nothing — a flat edge is still an edge. */
        override val isIdentity: Boolean get() = strength <= 0f

        /** A shader drawn source-atop, which both paths can do at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * A repeating texture laid over the layer: [pattern]'s marks, tiled, in [argb]. Source-atop like the other two
     * overlays, so it decorates the artwork rather than covering the icon with wallpaper.
     *
     * **The drawable is a stencil and [argb] is what it is drawn in** — see [IconPattern]. That is what makes one
     * asset serve every colour, and it is also what [invert] can act on: swapping the marks for the ground is a
     * property of a two-tone stencil and would mean nothing over a full-colour tile.
     *
     * ### One set of settings for every texture, unlike a bloom's two per falloff
     *
     * The obvious next question, having split [Bloom] and [ProgressiveBlur] into a profile per form, is whether
     * [pattern] deserves the same. It does not, and the difference is what the parameters *mean*.
     *
     * A falloff changes the question: a ramp has an angle and a disc has a radius, and neither can answer the
     * other's. A texture changes nothing — [scale] is "how big is one tile" and [angleDegrees] is "which way does it
     * run" whether the marks are dots or a grid. With the questions identical, keeping the answers is what makes
     * switching texture an **audition**: the user is comparing dots against a grid *at their own scale*, and
     * per-texture state would replace that with a jump to unrelated settings every time they tapped a tile.
     *
     * Two more reasons the same way. The falloffs are **two, fixed** — a profile each is a bounded cost — where
     * textures are a growing asset library, so profiles would grow with it and adding an asset would add stored
     * state. And [IconShape] is the same shape of thing exactly, chosen from a library by id, and carries no
     * per-shape state either.
     *
     * **What would be reasonable, if a texture ever needs it, is a per-texture *default*** — the scale a texture
     * arrives at, keyed on its id. That is additive, costs no storage, and does not break the audition, because it
     * only decides where a texture the user has never tuned starts.
     *
     * @property scale one tile's side as a fraction of the icon's box, so a quarter puts four tiles across it. A
     *   fraction rather than a pixel count for [IconLayerSpec.offsetX]'s reason: the same recipe has to look the
     *   same baked at 96px for a list row and at 288px for a folder.
     * @property angleDegrees which way the tiling runs, turned about the box's centre. Every built-in tile is
     *   authored square-on for this: a slanted asset would be a second way to say the same thing, and one that
     *   could only reach the angles it happened to be drawn at.
     * @property invert draws the ground and leaves the marks empty — the negative of the tile.
     * @property strength how strongly the texture is laid on, and how it is switched off.
     */
    @Serializable
    @SerialName("pattern")
    data class Pattern(
        val pattern: IconPattern,
        val argb: Int = 0xFFFFFFFF.toInt(),
        /**
         * **The finest scale that is still real**, and the panel's slider starts here for the same reason its range
         * does: below about a twentieth of the box the pixel floor in `LayerPattern.tileSizePx` takes over, so the
         * number would go on falling while the picture stopped changing. A texture arriving fine reads as *texture*;
         * arriving coarse reads as a pattern of shapes laid over the icon, which is the thing a user goes looking
         * for rather than the thing they want handed to them.
         *
         * One consequence worth knowing: at this scale the floor binds on the **small** previews. A 48dp rail tile
         * and the draft bake both clamp to a 4px tile, so they show the texture coarser than the finished icon does.
         * The full-size preview and the bake agree, which is what matters, but the tiles are approximate here in a
         * way they are not for any other effect.
         */
        val scale: Float = 0.05f,
        val angleDegrees: Float = 0f,
        /**
         * **Laid on lightly**, which is the other half of arriving fine: at full strength a tile this small covers
         * the artwork in opaque marks rather than texturing it, and what a user is reaching for is the *surface* of
         * the icon changing, not a second picture over it. Turning it up is the obvious move once the effect is
         * visible; arriving there is not.
         */
        val strength: Float = 0.3f,
        val invert: Boolean = false,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Nothing chosen, or turned down to nothing. An id this build does not know is caught by the renderer. */
        override val isIdentity: Boolean get() = strength <= 0f || pattern.id.isBlank()

        /** A tiled shader drawn source-atop, which both paths can do at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * The layer's own silhouette repeated **behind** itself along a direction, in [argb] — the layer read as a solid
     * slab seen slightly off-square.
     *
     * **The only effect so far that draws what is already there rather than over it**, which is why it is the one
     * whose live cost scales with a slider: the extrusion is the union of many copies of the silhouette, and there is
     * no primitive for that short of drawing them. Both renderers cap the count for that reason — see `LayerExtrude`.
     *
     * @property angleDegrees which way the slab extends, clockwise from straight down — 0 puts the depth below the
     *   layer, which is what "extruded" reads as. The same convention every other angle here runs on.
     * @property depth how far it extends, as a fraction of the icon's box. A fraction rather than pixels so one
     *   recipe reads the same baked at 96px for a list row and at 288px for a folder.
     * @property strength how strongly it is laid on, and how it is switched off.
     */
    @Serializable
    @SerialName("extrude")
    data class Extrude(
        val argb: Int = 0xFF000000.toInt(),
        val angleDegrees: Float = 45f,
        val depth: Float = 0.1f,
        /**
         * The opacity of the finished slab — linear across its whole range, and the same at every depth and every
         * bake size. See `IconRenderer.extruded` for why that took a change and what it cost.
         *
         * **It arrives opaque, and that is a re-pick rather than a preference.** This was tuned by eye at 0.10 while
         * `strength` was still each *copy's* alpha, where the copies compounded to `1 - (1 - strength)^count` — at
         * the studio's preview size that is the full 48 copies, so 0.10 was already 99% and looked exactly like 1.0.
         * The value that preserves what was approved is therefore the top of the range, not the number that was
         * typed. Worth re-picking now that the slider means something across all of it.
         */
        val strength: Float = 1f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Turned down to nothing, or reaching nowhere — a slab with no depth is the layer itself. */
        override val isIdentity: Boolean get() = strength <= 0f || depth <= 0f

        /** Offset copies of the layer, which both paths can draw at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * The layer split into its red, green and blue channels and put back together offset — the coloured fringing a
     * cheap lens leaves, and the one effect here that *replaces* the layer rather than drawing over it.
     *
     * **No strength, and that is the honest shape rather than an omission.** Every other effect needs a separate
     * knob because its parameters describe a look that exists at any intensity; this one is *made of* an offset, so
     * an offset of nothing already means "not split". A strength slider beside it would be a second way to reach the
     * same state, and the two would disagree about which one switched it off.
     *
     * @property offsetX how far the red channel moves, as a fraction of the icon's box — blue moves the same
     *   distance the other way, and green stays put. A fraction for [IconLayerSpec.offsetX]'s reason: the fringe has
     *   to be the same width of the icon at every bake size.
     * @property offsetY the same, downward.
     */
    @Serializable
    @SerialName("chromatic")
    data class ChromaticSplit(
        val offsetX: Float = 0.02f,
        val offsetY: Float = 0f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Nothing moved is nothing split — which is what makes the offsets their own on/off. */
        override val isIdentity: Boolean get() = offsetX == 0f && offsetY == 0f

        /** Three colour matrices added together, which both paths can do at any API. */
        override val drawsLive: Boolean get() = true
    }

    /**
     * A soft halo of [argb] around the layer's finished silhouette, drawn behind it.
     *
     * **The first effect that cannot be drawn live**, together with [Shadow] — see [drawsLive]. It derives from the
     * silhouette *after* the transform and the mask, which the bake holds as a bitmap and can blur at any API, and
     * which the live path only has as nodes: Compose's only blur is `RenderEffect`, API 31+ against a `minSdk` of 26.
     * So the studio previews an icon carrying one **from the bake** rather than the effect being gated to Android 12.
     *
     * **A glow has no direction, which is the whole of what separates it from [Shadow].** It is centred on the
     * silhouette by definition; a halo pushed to one side is a coloured shadow, and that is the other effect. Making
     * them one record with both an offset and a spread would also mean a layer could carry only one of them, since at
     * most one effect of a type is meaningful — and a glowing icon casting a shadow is an ordinary thing to want.
     *
     * @property radius how far the halo fades out, as a fraction of the icon's box.
     * @property spread how far the silhouette is grown *before* it is blurred, again a fraction of the box. Without
     *   it a blur alone leaves the halo at about half strength right at the edge and fading immediately; spread is
     *   what gives a glow a solid ring to fade *from*.
     * @property strength how strongly it is laid on, and how it is switched off.
     */
    @Serializable
    @SerialName("glow")
    data class Glow(
        val argb: Int = 0xFFFFFFFF.toInt(),
        val radius: Float = 0.08f,
        /**
         * **A hair of dilation, which is what stops the halo reading as a smudge.** A blur alone leaves the glow at
         * half strength right at the silhouette's edge, so the brightest part of it is where the artwork already
         * covers it; growing the silhouette first gives the fade a solid ring to start from. A hundredth of the box
         * is under a pixel at a list-row bake and a couple at a folder's — enough to seat the glow, far short of the
         * outline a larger spread turns it into.
         */
        val spread: Float = 0.01f,
        val strength: Float = 1f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Turned down to nothing, or reaching nowhere — neither a blur nor a spread leaves anything to see. */
        override val isIdentity: Boolean get() = strength <= 0f || (radius <= 0f && spread <= 0f)

        /** Compose's only blur is `RenderEffect`, API 31+ against a `minSdk` of 26. The bake has no such limit. */
        override val drawsLive: Boolean get() = false
    }

    /**
     * The layer's finished silhouette blurred, offset and drawn behind it in [argb] — a cast shadow.
     *
     * [Glow]'s twin, and the same mechanism: what separates them is that a shadow is thrown *somewhere*, so it has an
     * offset and no spread where a glow has a spread and no offset. See [Glow] for why they are two effects and why
     * neither draws live.
     *
     * @property radius how soft the shadow is, as a fraction of the icon's box. Zero is a hard silhouette.
     * @property offsetX how far it is thrown, as a fraction of the box; positive is right.
     * @property offsetY the same, downward — which is where a shadow falls by default, so this is the one non-zero
     *   default among the four.
     * @property strength how strongly it is laid on, and how it is switched off.
     */
    @Serializable
    @SerialName("shadow")
    data class Shadow(
        val argb: Int = 0xFF000000.toInt(),
        val radius: Float = 0.05f,
        /**
         * **Thrown down *and* to the side, where this used to fall straight down.** Equal on both axes puts the
         * light over the icon's leading shoulder, which is where every other surface in this launcher implies it is
         * — a shadow directly beneath reads as the icon floating rather than as anything lighting it. The pair is
         * what makes it a direction; either alone is a drop.
         */
        val offsetX: Float = 0.04f,
        val offsetY: Float = 0.04f,
        val strength: Float = 1f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /**
         * Turned down to nothing is the only way a shadow disappears — unlike [Glow], a hard silhouette directly
         * behind the layer is still hidden by it, but any offset at all makes it visible again.
         */
        override val isIdentity: Boolean get() = strength <= 0f

        /** @see Glow.drawsLive */
        override val drawsLive: Boolean get() = false
    }

    /**
     * Concentric waves pushing the layer's pixels toward and away from a centre — the layer seen through water.
     *
     * **The first *per-pixel* effect**, and the third that cannot draw live: it reads each output pixel from
     * somewhere else in the layer, which is arithmetic over an `IntArray` in the bake and AGSL (API 33+) in Compose,
     * against a `minSdk` of 26. See [Glow.drawsLive].
     *
     * @property amplitude how far a pixel is pushed at the crest of a wave, as a fraction of the icon's box. It is
     *   also the switch: a wave that displaces nothing is no wave at all.
     * @property waves how many crests fall across the box. More makes finer ripples rather than bigger ones.
     * @property centerX where the waves start, as a fraction of the box from its middle. Positive is right.
     * @property centerY the same, downward.
     */
    @Serializable
    @SerialName("ripple")
    data class Ripple(
        val amplitude: Float = 0.06f,
        val waves: Float = 10f,
        /**
         * **The top-right corner**, which `LayerRipple.centerPx` reaches at exactly ±0.5 — the ends of the pad's own
         * range on both axes.
         *
         * A ripple centred in the box is symmetrical, and symmetry reads as a lens over the artwork rather than as
         * something disturbing it. From a corner the crests sweep across, which is the picture the effect is for.
         *
         * One consequence of the corner worth knowing: [waves] counts crests across a **box's width** of distance,
         * and the far corner is a diagonal away — about 1.4 of those — so ten waves shows nearer fourteen rings
         * across the icon. Not a miscount; the wavelength is a fixed fraction of the box by design, so that one
         * recipe ripples the same at every bake size.
         */
        val centerX: Float = 0.5f,
        val centerY: Float = -0.5f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /**
         * Displacing nothing, or having nothing to displace along.
         *
         * The second clause is not decoration: a wave count of zero divides the box into no crests, which is a
         * division by zero one step down in `LayerRipple`.
         */
        override val isIdentity: Boolean get() = amplitude <= 0f || waves <= 0f

        /** Per-pixel, so AGSL and API 33+ live — where the bake reads an `IntArray` at every API. */
        override val drawsLive: Boolean get() = false
    }

    /**
     * The layer's pixels pushed about by noise — the artwork torn into blobs rather than smoothly distorted.
     *
     * [Ripple]'s sibling: both read every output pixel from somewhere else, so both are per-pixel and neither draws
     * live. What separates them is where the displacement comes from — a wave there, a noise field here.
     *
     * **The noise is smooth, not per-pixel random**, and that is the whole difference between grain and static. A
     * fresh number per pixel scatters the artwork into confetti; a field interpolated between lattice points a
     * [grainSize] apart moves neighbouring pixels *together*, which is what tears it into recognisable pieces.
     *
     * @property amplitude how far a pixel is pushed at the field's extreme, as a fraction of the icon's box. Also
     *   the switch: noise that displaces nothing changes nothing.
     * @property grainSize how far apart the field's lattice points sit, again a fraction of the box — so it is the
     *   size of the *pieces*, where [amplitude] is how far they move.
     * @property drift whether the pieces scatter or all slide one way. See [GrainDrift].
     * @property angleDegrees which way they slide, clockwise from straight down. [GrainDrift.DIRECTED] only — noise
     *   that pushes every way at once has no direction to name.
     */
    @Serializable
    @SerialName("grain")
    data class Grain(
        val amplitude: Float = 0.02f,
        val grainSize: Float = 0.08f,
        val drift: GrainDrift = GrainDrift.FREE,
        val angleDegrees: Float = 0f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /**
         * Displacing nothing, or having no field to displace along.
         *
         * The second clause guards a division: a grain size of zero is a lattice with no spacing, which
         * `LayerGrain` would otherwise divide by.
         */
        override val isIdentity: Boolean get() = amplitude <= 0f || grainSize <= 0f

        /** Per-pixel, so AGSL and API 33+ live — where the bake reads an `IntArray` at every API. */
        override val drawsLive: Boolean get() = false
    }

    /**
     * The layer redrawn as a field of dots, one colour sampled per cell — an LED panel rather than a blur.
     *
     * **Not a resampling, unlike [Ripple] and [Grain], and that is why it shares nothing with them.** Those read
     * every output pixel from somewhere else in the layer; this reads one colour per *cell* and then draws a shape.
     * A pixelate built as a coordinate quantisation would give solid touching blocks and could express neither
     * [fill] nor [roundness] — the gaps between dots are the look, and gaps are something drawn rather than sampled.
     *
     * @property cellSize how big one dot's cell is, as a fraction of the icon's box. It is also the switch: cells
     *   with no size are the layer itself, which is why this and not a separate strength.
     * @property fill how much of its cell a dot covers, 0..1. Below 1 the gaps open up, which is what separates a
     *   panel of lights from a mosaic.
     * @property roundness the dot's corner, 0 square through 1 circle. A fraction of the dot rather than a length,
     *   so it stays a circle at every [fill] and every bake size.
     */
    @Serializable
    @SerialName("pixelate")
    data class Pixelate(
        /**
         * **A visible default, like every other effect here.** It rested at zero, which is this effect's own
         * identity — so adding a pixelate did nothing at all until a slider was moved, and the studio offered a
         * control whose first impression was that it was broken. An eighth of the box puts roughly eight cells
         * across the icon: coarse enough to read instantly as pixels, fine enough that the artwork is still the
         * artwork.
         */
        val cellSize: Float = 0.12f,
        val fill: Float = 1f,
        val roundness: Float = 0f,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** Cells with no size, or dots that cover none of them — either way the layer comes back untouched. */
        override val isIdentity: Boolean get() = cellSize <= 0f || fill <= 0f

        /** Per-pixel sampling, so AGSL and API 33+ live — where the bake reads an `IntArray` at every API. */
        override val drawsLive: Boolean get() = false
    }

    /**
     * The layer blurred, but only where a ramp says so — sharp in one region and softening away from it.
     *
     * **The last of the thirteen, and the only one needing two mechanisms.** Every other effect is a blur *or* a
     * ramp; this is a blur masked *by* a ramp, which is why the plan put it last. The pieces both already exist —
     * the ramp is a gradient [LayerGradient] can place, the blur is the bake's alone — so what is new is the
     * compositing that joins them.
     *
     * @property radius how soft the blurred end gets, as a fraction of the icon's box. Also the switch: no blur is
     *   the layer itself, however the ramp is shaped.
     * @property falloff whether the sharp region is a band across the layer or a disc within it. See [Falloff] —
     *   it decides which of [angleDegrees] and the two centre fractions mean anything.
     * @property sharpArea how much stays completely sharp, as a fraction of the ramp's own extent.
     * @property softness how far past that the blur takes to reach full strength. Zero is a hard edge between sharp
     *   and blurred, which is a real look rather than a degenerate one.
     * @property angleDegrees which way the ramp runs, clockwise from straight down — so 0 keeps the top sharp and
     *   blurs downward. [Falloff.LINEAR] only.
     * @property centerX where the sharp disc sits, as a fraction of the box from its middle. [Falloff.RADIAL] only.
     * @property centerY the same, downward.
     */
    @Serializable
    @SerialName("progressiveBlur")
    data class ProgressiveBlur(
        val falloff: Falloff = Falloff.RADIAL,
        val linear: BlurProfile = BlurProfile(),
        val radial: BlurProfile = BlurProfile(),
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** The profile [falloff] selects — every reader below goes through it, and so does every write. */
        val active: BlurProfile get() = if (falloff == Falloff.LINEAR) linear else radial

        /**
         * The active profile's fields, forwarded — [Bloom.argb]'s arrangement and its reason.
         *
         * `IconRenderer` reads `blur.radius`, `blur.angleDegrees` and `blur.centerX`, and `LayerProgressiveBlur`
         * reads `blur.sharpArea` and `blur.softness` off the whole effect. None of them changed: the profile is
         * resolved here, once, rather than at each of those sites where they could resolve differently.
         */
        val radius: Float get() = active.radius

        /** @see radius */
        val sharpArea: Float get() = active.sharpArea

        /** @see radius */
        val softness: Float get() = active.softness

        /** @see radius */
        val angleDegrees: Float get() = active.angleDegrees

        /** @see radius */
        val centerX: Float get() = active.centerX

        /** @see radius */
        val centerY: Float get() = active.centerY

        /** This blur with [transform] applied to whichever profile is active. @see Bloom.withActive */
        fun withActive(transform: (BlurProfile) -> BlurProfile): ProgressiveBlur =
            if (falloff == Falloff.LINEAR) copy(linear = transform(linear)) else copy(radial = transform(radial))

        /** No blur is no effect, whatever the ramp is doing — which is what makes the radius the switch. */
        override val isIdentity: Boolean get() = radius <= 0f

        /** A real image blur, so `RenderEffect` and API 31+ live — where the bake owns its own bitmap. */
        override val drawsLive: Boolean get() = false
    }

    /**
     * One of the built-in colour looks, by id — see [IconFilter] for why the table lives in `core:icon` and why
     * this is a fixed vocabulary rather than curated content.
     *
     * **A whole matrix rather than the four numbers [Color] exposes**, which is the point of having both: the
     * sliders are for adjusting *this* icon, a filter is a look somebody authored that no combination of hue,
     * saturation, brightness and tint can reach — the channel mixing a sepia needs, or the lifted blacks of a matte
     * grade. They compose, and their order in the list decides which acts on which.
     *
     * @property filter the id. An unknown one resolves to no matrix and the effect draws nothing, so a recipe from
     *   a later build degrades rather than failing.
     */
    @Serializable
    @SerialName("filter")
    data class Filter(
        val filter: IconFilter,
        override val enabled: Boolean = true,
    ) : LayerEffect {

        /** No id, nothing to look up. An id this build does not know is caught by the renderer, not here. */
        override val isIdentity: Boolean get() = filter.id.isBlank()

        /** A colour matrix, which both paths already share through `LayerFilter`'s own machinery. */
        override val drawsLive: Boolean get() = true
    }
}

/**
 * The effects that actually draw, **in the order they are applied** — which is the list's own order.
 *
 * **On the list rather than on [IconLayerSpec], because a layer is no longer the only thing that has effects.**
 * [IconLayerSet] carries its own, applied to the finished composite, and "which of these draw?" has to have one
 * answer for both — a set whose disabled effects were filtered by a different rule from a layer's would be a
 * difference nobody would think to look for.
 *
 * Two questions, deliberately answered together: [LayerEffect.enabled] is the user's switch,
 * [LayerEffect.isIdentity] is the effect saying it would paint nothing. No renderer should have to ask either.
 */
val List<LayerEffect>.activeEffects: List<LayerEffect>
    get() = filter { it.enabled && !it.isIdentity }

/**
 * Whether the **live** render path can draw every one of these, or whether the studio must preview from the bake.
 *
 * False if any single active effect says so — an effect that cannot be drawn cannot simply be skipped, since a
 * preview missing one effect is a preview that lies.
 */
val List<LayerEffect>.drawLive: Boolean
    get() = activeEffects.all { it.drawsLive }

/**
 * This effect with its switch set — the one thing every variant can be asked that none of them declares.
 *
 * **An exhaustive `when` here rather than a member on each of the thirteen**, because what it replaced was worse
 * than either: the studio's switch carried a `when` over `EffectSlice` whose **`else` arm meant Bloom**, so a new
 * effect added without a matching arm would have silently toggled the bloom's switch instead of its own — and the
 * two entries that no longer carry a switch at all still had arms there. Over a sealed interface the compiler
 * refuses to let a new variant be forgotten, which is the guarantee that was missing.
 *
 * In the model rather than in the panel because `enabled` is the model's field, and a second consumer that wanted
 * to toggle one would otherwise write the same thirteen cases again.
 */
fun LayerEffect.withEnabled(enabled: Boolean): LayerEffect = when (this) {
    is LayerEffect.Color -> copy(enabled = enabled)
    is LayerEffect.Filter -> copy(enabled = enabled)
    is LayerEffect.Bloom -> copy(enabled = enabled)
    is LayerEffect.Gloss -> copy(enabled = enabled)
    is LayerEffect.Pattern -> copy(enabled = enabled)
    is LayerEffect.Extrude -> copy(enabled = enabled)
    is LayerEffect.ChromaticSplit -> copy(enabled = enabled)
    is LayerEffect.Glow -> copy(enabled = enabled)
    is LayerEffect.Shadow -> copy(enabled = enabled)
    is LayerEffect.Ripple -> copy(enabled = enabled)
    is LayerEffect.Grain -> copy(enabled = enabled)
    is LayerEffect.Pixelate -> copy(enabled = enabled)
    is LayerEffect.ProgressiveBlur -> copy(enabled = enabled)
}

/**
 * The stored effect of type [T], or null when there is no record of one.
 *
 * **This is the *editor's* view, where [activeEffects] is the *renderers'*.** It deliberately ignores both of the
 * questions that list answers — a panel has to show the sliders of an effect you switched off, which is what
 * switching off rather than deleting is for, and it has to go on showing them when you drag one to nothing.
 * Anything deciding what to *draw* must read [activeEffects] instead.
 *
 * **It used to drop an identity effect as well, and that was the bug behind a real one.** Paired with [withEffect]
 * doing the same, dragging a bloom's strength to zero deleted the whole record — its color, angle, radius, falloff
 * and anchor with it — so the panel's switch greyed out mid-gesture and dragging back up produced a *fresh* effect
 * at defaults rather than the one being edited. Identity is a statement about what an effect would paint, and the
 * editor is not asking that question.
 */
inline fun <reified T : LayerEffect> List<LayerEffect>.effectOrNull(): T? =
    filterIsInstance<T>().firstOrNull()

/**
 * These effects with the one of type [T] replaced, or **removed** when [effect] is null.
 *
 * At most one of each type is meaningful, so this replaces rather than appends.
 *
 * **An effect that would paint nothing is kept**, which reverses this function's own earlier rule. Dropping it kept
 * an untouched recipe empty on disk — a real goal, but bought at the wrong moment: applied on *every edit*, it made
 * "drag a slider to its floor" mean "discard every other value on this effect". Storage stays small the honest way
 * instead, by nothing writing a record until the user asks for one; and `encodeDefaults = false` means the record
 * a user does own costs only the fields they moved.
 *
 * **Position is preserved when a record already exists**, which is not tidiness: the list *is* the pipeline order,
 * so appending an edited effect to the end would silently re-order it past everything after it — a tint that used
 * to recolor a bloom would stop doing so, on an edit that was about neither. Only a genuinely new effect is
 * appended, and it goes last because that is where it was added.
 */
inline fun <reified T : LayerEffect> List<LayerEffect>.withEffect(effect: T?): List<LayerEffect> {
    if (effect == null) return filterNot { it is T }
    return if (any { it is T }) map { if (it is T) effect else it } else this + effect
}
