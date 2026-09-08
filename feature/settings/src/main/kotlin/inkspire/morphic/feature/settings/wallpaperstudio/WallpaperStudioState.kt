package inkspire.morphic.feature.settings.wallpaperstudio

import android.graphics.Bitmap
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe

/**
 * What the wallpaper studio is showing: the recipe being edited, and its latest render.
 *
 * **The picture is derived state, not part of the recipe.** The recipe is the tiny, storable description (design +
 * seed + palette + knobs); [shot] is what that description resolves to at the current preview size, produced off the
 * main thread and swapped in when ready. Keeping them apart is what lets the preview transition from one render to the
 * next while the recipe changes instantly, and what will let the recipe be saved without dragging a multi-megabyte
 * image along.
 *
 * @property recipe the current design, seed, palette and knobs — the source of truth the render is a function of.
 * @property shot the recipe rendered at the preview's size, or null before the first render lands.
 * @property applying whether a set-as-wallpaper write is in flight — the apply button reads it to disable itself so a
 *   second tap cannot start a second write over the first.
 */
data class WallpaperStudioState(
    val recipe: WallpaperRecipe,
    val shot: WallpaperShot? = null,
    val applying: Boolean = false,
)

/**
 * One rendered picture, and the two things the screen cannot tell about it by looking.
 *
 * **[draft] is what stops a downscaled picture being set as the wallpaper.** A draft is a *preview* of the recipe at a
 * fraction of the screen's pixels; applying it would hand the system a picture at a fraction of the resolution it
 * asked for, and it would look like the studio had simply produced a soft wallpaper.
 *
 * **[dissolve] is how the picture should arrive**, which is a property of the *edit* rather than of the bitmap: a new
 * design or a shuffled seed is a different picture and fades in, while the next frame of a drag is the same picture
 * moving and must swap outright — a fade there would smear every intermediate render into the last one.
 */
data class WallpaperShot(
    val bitmap: Bitmap,
    val draft: Boolean,
    val dissolve: Boolean,
)
