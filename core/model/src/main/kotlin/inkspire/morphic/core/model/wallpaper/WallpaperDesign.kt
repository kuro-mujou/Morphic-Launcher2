package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which generator a wallpaper recipe is drawn by — the studio's *design*.
 *
 * **This enum grows one value per built generator, and no faster.** The full catalog the plan sets out is
 * twenty-two designs (see `docs/WALLPAPER_STUDIO_PLAN.md`), but a design id with no generator behind it is a value
 * `core:graphics` cannot render and a recipe cannot honor — a model in a vacuum. So the id is added *together with*
 * its generator, which is what keeps `Generators` a **total** `when` over this enum (the compiler then refuses to let
 * a generator be forgotten when a value is added). The catalog lives in the plan; this holds only what is real.
 *
 * Persisted inside the recipe, so the names are an on-disk contract. A recipe naming a design an older build does not
 * have is the one case a reader has to handle — see [WallpaperRecipe].
 */
@Serializable
enum class WallpaperDesign {

    /**
     * A gradient climbing the frame through the palette's stops — the simplest real design, and the one that proves
     * the whole pipeline (recipe → generator → bitmap) end to end without needing any of the gart engine.
     */
    @SerialName("linearGradient")
    LINEAR_GRADIENT,
}
