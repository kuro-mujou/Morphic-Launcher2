package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.component.color.ColorPalettes
import inkspire.morphic.core.graphics.wallpaper.Generators
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Drives the wallpaper studio: it holds the recipe, and it renders it.
 *
 * **The recipe changes on the main thread; the picture is caught up to it off one.** Every edit — a new design, a
 * shuffle — updates the recipe in [state] immediately and kicks a render on [Dispatchers.Default], because a
 * generator painting a full-screen bitmap is far too heavy for the frame it is asked on. The render that a newer edit
 * outdates is cancelled, so a fast series of shuffles does not queue a backlog of stale pictures.
 *
 * **Renders at the preview's own pixel size**, which the screen measures and hands back through [setViewport] — so
 * the picture is exactly the resolution it is shown at rather than a fixed guess scaled to fit. Nothing renders until
 * a size is known.
 *
 * No repository yet: this slice previews and mutates but does not *apply*. Setting a generated bitmap as the system
 * wallpaper is the next slice, and it needs a bitmap seam `WallpaperRepository` does not have.
 */
class WallpaperStudioViewModel : ViewModel() {

    private var viewportWidth = 0
    private var viewportHeight = 0
    private var renderJob: Job? = null

    private val mutableState = MutableStateFlow(
        WallpaperStudioState(
            recipe = WallpaperRecipe(
                design = WallpaperDesign.FLOW_FIELD,
                seed = Random.nextLong(),
                palette = DefaultPalette,
            ),
        ),
    )
    val state: StateFlow<WallpaperStudioState> = mutableState.asStateFlow()

    /** The preview size in pixels — the screen reports it once it is laid out, and again if it changes. */
    fun setViewport(width: Int, height: Int) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        rerender()
    }

    /** Switch to [design], keeping the seed and palette — the same variation of a different generator. */
    fun pickDesign(design: WallpaperDesign) {
        if (design == mutableState.value.recipe.design) return
        mutableState.update { it.copy(recipe = it.recipe.copy(design = design)) }
        rerender()
    }

    /** A new variation of the current design — a fresh seed, which is all a shuffle is. */
    fun shuffle() {
        mutableState.update { it.copy(recipe = it.recipe.copy(seed = Random.nextLong())) }
        rerender()
    }

    private fun rerender() {
        val width = viewportWidth
        val height = viewportHeight
        if (width == 0 || height == 0) return

        val recipe = mutableState.value.recipe
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val bitmap = Generators.forDesign(recipe.design)
                .render(width, height, recipe.palette, recipe.params, recipe.seed)
            ensureActive()
            mutableState.update { it.copy(bitmap = bitmap) }
        }
    }

    private companion object {

        /** What the studio opens on — one of the curated sets, warm-and-cool so any design has somewhere to go. */
        val DefaultPalette = Palette(ColorPalettes.all.first { it.name == "Dusk" }.colors)
    }
}
