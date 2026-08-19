package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * One layer in an [IconLayerSet]: its [role], where its content comes from ([source]), and how it is drawn.
 *
 * Transform values live in the icon's normalized square box: [offsetX]/[offsetY] are fractions of that box
 * (0 = centered), [zoom] is a scale (1 = the default fit, >1 zooms in), [rotation] is in degrees clockwise, and
 * [tiltX]/[tiltY] lean the layer out of the plane. [shape] masks a layer to a silhouette, so it defaults to `null`
 * (unshaped), and [shapeAnchor] says what that silhouette is cut against. [effects] is the extensible,
 * defaulted-empty effect bag (see [LayerEffect]).
 *
 * [opacity] and [blend] are **compositing** properties rather than effects: every layer has both, always, with a
 * meaningful default, which is what makes them fields. See [LayerBlend].
 *
 * @property visible When false the layer is skipped in the composite but kept in the set (an editor hide
 *   toggle). *(Assumption — a standard layer-editor affordance; flag if not wanted for v1.)*
 * @property normalize Whether this layer's artwork is resized so it covers about as much of its box as every other
 *   icon's. Apps author at wildly different sizes — some fill their whole canvas, some leave a wide margin — and
 *   nothing in the platform enforces otherwise, so left alone a home screen is visibly uneven.
 *
 *   **Off by default.** It rewrites how every app's icon looks, which is a change to make because someone asked for
 *   it rather than one to arrive with: an unconfigured launcher draws each app's artwork the size its author drew
 *   it. Turning it on is one tap in the studio's Source panel, and one edit on the global default reaches every app.
 *   It defaulted *on* until this was reversed, so stored recipes that never mentioned it — the field is not written
 *   when it matches the default — read back as off. That is the intended effect and needs no migration; a recipe
 *   that had it explicitly off still does.
 *
 *   **Foreground only.** `IconLayerResolver` measures whatever *this* layer resolved to and rescales that layer
 *   alone; a background is never resized, because a plate changes what sits behind the artwork rather than how large
 *   the artwork should be — so an app comes out the same size whether its background is drawn or switched off. An
 *   intermediate version applied one factor to every layer at once and this note used to describe it.
 * @property tiltX Rotation **about the horizontal axis**, in degrees — so the top of the layer leans away and the
 *   bottom toward the viewer. Named for the axis it turns around, matching `Camera.rotateX` and Compose's
 *   `rotationX`, which is the convention every platform API here uses; the label the studio shows is "Tilt X".
 *
 *   **It is a transform, not an effect, and that is why it lives here beside [rotation].** Leaning a layer out of
 *   the plane is the same *kind* of thing as turning it in the plane — it says where the layer sits — so splitting
 *   them across two mechanisms would mean one rotation being orderable against a color matrix and the other not.
 * @property tiltY Rotation about the **vertical** axis: the left edge leans away and the right toward the viewer.
 * @property shapeAnchor What [shape] is cut against — the box, or this layer's own artwork carried by its transform.
 *   Means nothing when [shape] is null, which is why the studio shows the control only once a shape is chosen. See
 *   [ContentAnchor]; it defaults to [ContentAnchor.BOX], so stored recipes read back rendering exactly as they did.
 */
@Serializable
data class IconLayerSpec(
    val role: LayerRole,
    val source: LayerSource,
    val visible: Boolean = true,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val shape: IconShape? = null,
    val shapeAnchor: ContentAnchor = ContentAnchor.BOX,
    val opacity: Float = 1f,
    val blend: LayerBlend = LayerBlend.NORMAL,
    val normalize: Boolean = false,
    val effects: List<LayerEffect> = emptyList(),
) {

    /**
     * The effects that actually draw, **in the order they are applied** — which is the order of [effects] itself.
     *
     * **This is the layer's pipeline, and the list order being real is new.** Both renderers used to read
     * [color] and [bloom] by name and apply them in a sequence they each hardcoded, so [effects] was a bag whose
     * order meant nothing. That is survivable at two effects and wrong at thirteen: a pattern over a glow is not a
     * glow over a pattern, so the order has to be the user's rather than the renderer's.
     *
     * **One consequence worth knowing.** The hardcoded sequence applied the overlay and *then* the color matrix,
     * because the matrix rode on the paint that joined the layer to the stack. A stored recipe whose list happens to
     * read `[Color, Bloom]` — which is what setting a tint before a bloom produced — now renders in that order
     * instead, so its tint no longer recolors its bloom. Nothing has shipped, and the alternative is a canonical
     * order that no reorder control could ever override.
     *
     * **The filtering itself lives on `List<LayerEffect>`**, not here, because a layer stopped being the only thing
     * that has effects the moment [IconLayerSet] gained its own — and "which of these draw?" must have exactly one
     * answer for both. See [activeEffects].
     */
    val activeEffects: List<LayerEffect>
        get() = effects.activeEffects

    /**
     * Whether the **live** render path can draw every effect on this layer, or whether the studio must preview it
     * from the bake. False if any single active effect says so — an effect that cannot be drawn cannot be skipped,
     * since a preview missing one effect is a preview that lies.
     */
    val drawsLive: Boolean
        get() = effects.drawLive
}

/**
 * This layer with its one effect of type [T] replaced, or removed when [effect] is null or would paint nothing.
 *
 * The list function of the same name, lifted to the layer that holds one — which is the ordinary case, and saves
 * every caller spelling out `copy(effects = effects.withEffect(…))`. Top-level rather than a member because a
 * `reified` type parameter cannot be one.
 */
inline fun <reified T : LayerEffect> IconLayerSpec.withEffect(effect: T?): IconLayerSpec =
    copy(effects = effects.withEffect(effect))
