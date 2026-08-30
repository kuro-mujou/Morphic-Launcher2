package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The shape a wallpaper is composed for — the studio's *Vertical / Squared* toggle.
 *
 * **Composed-for, not rendered-at.** A generator is asked for a bitmap of a concrete pixel size; this is the *intent*
 * the recipe carries so it re-renders at the right proportions on any screen. A [VERTICAL] recipe composed for a
 * phone still reads correctly when re-rendered taller or shorter, where a [SQUARED] one is framed to look right
 * cropped square (a lock-screen thumbnail, a preview tile).
 *
 * Persisted inside the recipe, so the names are an on-disk contract.
 */
@Serializable
enum class WallpaperAspect {

    /** Framed for a tall screen — the default, and what "wallpaper" means unqualified. */
    @SerialName("vertical")
    VERTICAL,

    /** Framed to read well square — a preview, a thumbnail, a lock-screen crop. */
    @SerialName("squared")
    SQUARED,
}
