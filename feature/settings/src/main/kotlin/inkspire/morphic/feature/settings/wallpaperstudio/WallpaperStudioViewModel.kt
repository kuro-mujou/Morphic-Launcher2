package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.component.color.ColorPalettes
import inkspire.morphic.core.graphics.wallpaper.FilterPipeline
import inkspire.morphic.core.graphics.wallpaper.Generators
import inkspire.morphic.core.graphics.wallpaper.PaletteColorMode
import inkspire.morphic.core.model.wallpaper.DesignParams
import inkspire.morphic.core.model.wallpaper.Palette
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.core.model.wallpaper.WallpaperFilter
import inkspire.morphic.core.model.wallpaper.WallpaperRecipe
import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.WallpaperSource
import inkspire.morphic.data.wallpaper.WallpaperTarget
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
 * **A filter change re-renders from the generator, not from the shown bitmap.** The filter stack is not reversible
 * (a blur cannot be un-blurred), so there is no filtered bitmap to peel a pass off — the honest thing is to redraw the
 * generator and re-apply the whole (cheaper) stack. Generation dominates that cost anyway, so folding the filters into
 * the same [rerender] keeps one render path rather than a second incremental one.
 */
class WallpaperStudioViewModel(
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

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

    /** Recolor the current design with [colors] — a chosen palette, keeping the design and seed. */
    fun setPalette(colors: List<Int>) {
        if (colors == mutableState.value.recipe.palette.colors) return
        mutableState.update { it.copy(recipe = it.recipe.copy(palette = Palette(colors))) }
        rerender()
    }

    /**
     * Replace the design's Style knobs — the whole of [DesignParams] at once, since every control in the panel edits
     * one field of it.
     *
     * **Fired when a drag ends, not per frame, and the Style panel is written to commit rather than preview for that
     * reason.** A generator paints every pixel of a full-screen bitmap; there is no cheap intermediate to show while a
     * finger moves, and previewing would only queue renders for [rerender] to cancel. A draft-quality render during
     * the gesture is the refinement that would change this.
     *
     * A no-op when nothing moved, so a re-selected value costs no render.
     */
    fun setParams(params: DesignParams) {
        if (params == mutableState.value.recipe.params) return
        mutableState.update { it.copy(recipe = it.recipe.copy(params = params)) }
        rerender()
    }

    /**
     * Turn [filter] on at its default strength, or off if it is already on — the studio's filter chips.
     *
     * A chip is a switch, so the recipe only ever carries a filter at one strength here; the [WallpaperRecipe]'s
     * `Float` per filter leaves room for a strength slider later without a model change.
     */
    fun toggleFilter(filter: WallpaperFilter) {
        mutableState.update {
            val filters = it.recipe.filters.toMutableMap()
            if (filters.remove(filter) == null) filters[filter] = filter.defaultStrength
            it.copy(recipe = it.recipe.copy(filters = filters))
        }
        rerender()
    }

    /**
     * Sets the picture on screen as the system wallpaper, then calls [onApplied].
     *
     * **Applies the bitmap the user is looking at, not a re-render.** The preview already fills the screen, so its
     * bitmap is at the display's resolution and is exactly what was approved — what-you-see-is-what-you-get, and one
     * fewer render. It is marked [WallpaperSource.PICKED] because a generated wallpaper the user chose to set *is* a
     * chosen image as far as the rest of the system is concerned; a distinct "generated" source is a later refinement
     * for the my-designs shelf.
     *
     * **[onApplied] runs on the main dispatcher after the work**, so it is safe for the screen to navigate away in it —
     * the apply has already finished by the time it fires, and leaving earlier would only cancel an apply the user did
     * not wait for.
     */
    fun apply(onApplied: () -> Unit) {
        val bitmap = mutableState.value.bitmap ?: return
        if (mutableState.value.applying) return

        mutableState.update { it.copy(applying = true) }
        viewModelScope.launch {
            wallpaperRepository.setImage(bitmap, WallpaperSource.PICKED)
            wallpaperRepository.apply(WallpaperTarget.BOTH)
            mutableState.update { it.copy(applying = false) }
            onApplied()
        }
    }

    private fun rerender() {
        val width = viewportWidth
        val height = viewportHeight
        if (width == 0 || height == 0) return

        val recipe = mutableState.value.recipe
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            // The color mode is applied to the palette here, once, so the generator honors it without knowing it exists.
            val palette = PaletteColorMode.resolve(recipe.palette, recipe.params.colorMode)
            val base = Generators.forDesign(recipe.design)
                .render(width, height, palette, recipe.params, recipe.seed)
            ensureActive()
            val bitmap = FilterPipeline.apply(base, recipe.filters)
            ensureActive()
            mutableState.update { it.copy(bitmap = bitmap) }
        }
    }

    private companion object {

        /** What the studio opens on — one of the curated sets, warm-and-cool so any design has somewhere to go. */
        val DefaultPalette = Palette(ColorPalettes.all.first { it.name == "Dusk" }.colors)
    }
}
