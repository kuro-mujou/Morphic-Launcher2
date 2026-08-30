package inkspire.morphic.feature.settings.wallpaperstudio

import android.graphics.Bitmap
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe

/**
 * What the wallpaper studio is showing: the recipe being edited, and its latest render.
 *
 * **The bitmap is derived state, not part of the recipe.** The recipe is the tiny, storable description (design +
 * seed + palette); [bitmap] is the picture that description resolves to at the current preview size, produced off the
 * main thread and swapped in when ready. Keeping them apart is what lets the preview crossfade from one render to the
 * next while the recipe changes instantly, and what will let the recipe be saved without dragging a multi-megabyte
 * image along.
 *
 * @property recipe the current design, seed and palette — the source of truth the render is a function of.
 * @property bitmap the recipe rendered at the preview's size, or null before the first render lands.
 * @property applying whether a set-as-wallpaper write is in flight — the apply button reads it to disable itself so a
 *   second tap cannot start a second write over the first.
 */
data class WallpaperStudioState(
    val recipe: WallpaperRecipe,
    val bitmap: Bitmap? = null,
    val applying: Boolean = false,
)
