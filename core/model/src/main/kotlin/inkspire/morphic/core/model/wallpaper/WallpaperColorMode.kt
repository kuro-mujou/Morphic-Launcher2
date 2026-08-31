package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How much of the palette a design actually paints with — the studio's *Color mode*, and the single biggest lever on
 * whether a wallpaper reads as *composed* or as a swatch card.
 *
 * **This is the restraint knob.** A generator handed a six-color palette and told to use all of it fills the frame with
 * six saturated colors — which is why so many first-cut generators looked loud. Smart Launcher's popular designs default
 * to **two** colors, or **one** hue in shades; "use everything" is the *opt-in*, not the default. Modeled here so the
 * default can be restrained and the loud version is a deliberate choice.
 *
 * **It is applied by reducing the palette before the generator ever sees it** (see `PaletteColorMode` in `core:graphics`),
 * so every generator honors it for free without a per-design branch — a mosaic drawing from a two-color palette is
 * bichromatic by construction. The values are ordered from most restrained to loudest.
 *
 * Persisted inside [DesignParams]; the names are an on-disk contract. New modes are added as they are implemented (the
 * `Stroke`/outline mode Smart Launcher offers waits for the thin-line renderer), so a reader stays total over what
 * exists — the same discipline [WallpaperDesign] keeps.
 */
@Serializable
enum class WallpaperColorMode {

    /** One hue, in shades from a light tint to a dark shade — the calmest, and where a design carries on form not color. */
    @SerialName("monochromatic")
    MONOCHROMATIC,

    /** Two colors — the palette's lightest and darkest — the tasteful default most premium wallpapers actually use. */
    @SerialName("bichromatic")
    BICHROMATIC,

    /** The whole palette, every stop — the boldest, and the one to reach for on purpose rather than by default. */
    @SerialName("colorful")
    COLORFUL,
}
