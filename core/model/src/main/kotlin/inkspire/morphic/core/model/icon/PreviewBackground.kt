package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * What the icon studio's canvas is drawn *on*, cycled by a single control.
 *
 * A drawing app's transparency checkerboard, plus flat black and white, plus the two mixes — because the question an
 * icon designer actually has is "does this read on a dark background, on a light one, and where exactly are its
 * transparent parts?", and no single backdrop answers all three.
 *
 * **In `core:model` despite not being part of the recipe, and despite having one consumer.** It is here because it is
 * *stored* — a chosen backdrop survives the session, so `data:settings` has to name the type, and a data module must not
 * depend on a feature. That is the same move `BackdropEffect`, `DeviceConfiguration` and the icon layer model each made,
 * and the line it lands on is worth stating: this describes the studio's **workspace**, not the icon. Nothing about it
 * reaches a rendered icon, which is why it is not a field of [IconLayerSet] — an icon looks the same whatever it was
 * drawn over.
 *
 * **L1's sixth option, the launcher's own wallpaper, is deliberately absent.** The studio never shows it. That is
 * partly a design call and partly load-bearing: Haze blurs whatever node is really beneath a floating surface, and
 * the wallpaper reaches the settings previews through a `BlendMode.Src` punch to a *transparent* window — which
 * would leave the studio's panels with nothing to sample.
 */
@Serializable
enum class PreviewBackground {
    BLACK,
    WHITE,

    /** The checkerboard everywhere — transparency shown across the whole canvas. */
    CHECKERBOARD,

    /** Black outside the icon's bound, checkerboard within it: the icon's own alpha against a dark surround. */
    BLACK_WITH_CHECKER,

    /** White outside the icon's bound, checkerboard within it. */
    WHITE_WITH_CHECKER,
    ;

    /** The next background in the cycle, wrapping — the whole of the control's behavior. */
    fun next(): PreviewBackground = entries[(ordinal + 1) % entries.size]

    /** Whether the area *inside* the icon bound shows the transparency checkerboard. */
    val checkersInsideBound: Boolean
        get() = this == CHECKERBOARD || this == BLACK_WITH_CHECKER || this == WHITE_WITH_CHECKER

    /** Whether the area *outside* the icon bound does. */
    val checkersOutsideBound: Boolean get() = this == CHECKERBOARD

    /**
     * Whether the system bars should draw their icons **dark** over this backdrop.
     *
     * Read from the backdrop's *surround*, not its bound: the bound is a square in the middle of the canvas and never
     * reaches the bars, so both `_WITH_CHECKER` values take the color of the flat area around it.
     * [CHECKERBOARD] counts as light — its two grays both swallow a white glyph.
     *
     * Named for what the user sees, unlike the platform's `isAppearanceLightStatusBars`, which names the *background*
     * the icons are being asked to contrast. Getting that inversion wrong is invisible in code and obvious on screen.
     */
    val darkSystemBarIcons: Boolean
        get() = this == WHITE || this == WHITE_WITH_CHECKER || this == CHECKERBOARD

    companion object {

        /**
         * What the studio opens on before the user has ever chosen: the **checkerboard**.
         *
         * The one backdrop that answers the question a fresh recipe raises — *where is this icon actually transparent?*
         * — without also asserting a light or dark surround the user has not asked about. It is also the neutral
         * choice: the two flat colors and the two mixes each pick a side, and picking one as a default would make
         * every icon look right or wrong for a reason the studio invented.
         *
         * **One declaration, read by both the state and the settings slice**, so the value the screen shows before
         * storage has answered is the same one storage would have answered with.
         */
        val Default: PreviewBackground = CHECKERBOARD
    }
}
