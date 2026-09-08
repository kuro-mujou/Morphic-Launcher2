package inkspire.morphic.feature.settings.wallpaperstudio

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.common.render.draftThenSettle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the wallpaper studio: it holds the recipe, and it renders it.
 *
 * **The recipe changes on the main thread; the picture is caught up to it off one.** Every edit updates the recipe in
 * [state] immediately and feeds [requests], because a generator painting a full-screen bitmap is far too heavy for the
 * frame it is asked on.
 *
 * **It renders twice: a downscaled draft at once, then the full size once the finger stops** — [draftThenSettle],
 * shared with the icon studio, which carries the reasoning for the loop's every clause. It is what lets a Style knob
 * preview *per frame of a drag*: a full-screen render is never shorter than the gap between two pointer events, so
 * conflating by cancellation alone leaves nothing on screen until the finger lifts.
 *
 * **The draft is the same picture, not a cheaper one.** Every generator frames itself in fractions of the size it is
 * given, so a proportional downscale is the same composition at fewer pixels — see [DraftShortSidePx] for the floor
 * that keeps that true, and [FilterPipeline] for the one filter that had to stop being measured in pixels for it to
 * hold.
 *
 * **What is given up, and it is worth stating: a superseded full-size pass is abandoned in intent only.** The
 * [Generators] do not check for cancellation, so one keeps running on its dispatcher after the loop has let it go. The
 * settle is what makes that survivable — a full pass only starts once the recipe has been still — and the drafts it
 * competes with are an order of magnitude cheaper. Making the generators cooperate is the fix if it is ever felt.
 *
 * **A filter change re-renders from the generator, not from the shown bitmap.** The filter stack is not reversible
 * (a blur cannot be un-blurred), so there is no filtered bitmap to peel a pass off — the honest thing is to redraw the
 * generator and re-apply the whole (cheaper) stack. Generation dominates that cost anyway, so folding the filters into
 * the same request keeps one render path rather than a second incremental one.
 */
class WallpaperStudioViewModel(
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    private var viewportWidth = 0
    private var viewportHeight = 0

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

    /**
     * What the render loop is fed — null until the screen has reported a size to paint into.
     *
     * **What the loop is fed, rather than what it is keyed on.** A `StateFlow` conflates by equality, so an edit that
     * lands on a recipe already drawn produces no emission at all, and a drag that outruns the renderer collapses into
     * one value rather than a backlog.
     */
    private val requests = MutableStateFlow<RenderRequest?>(null)

    init {
        viewModelScope.launch {
            // Whether the picture on screen came from this request's draft — read by the settled pass, which must not
            // dissolve a second time over the fade the draft already played. Local to the one coroutine running the
            // loop, so the two lambdas share it without a field.
            var drafted = false
            requests.filterNotNull().draftThenSettle(
                settle = SettleMs.milliseconds,
                draft = { request ->
                    val scale = DraftShortSidePx.toFloat() / min(request.width, request.height)
                    // Nothing to draft on a viewport already smaller than the draft floor: it would be the same
                    // picture painted twice.
                    drafted = scale < 1f
                    if (drafted) show(paint(request, scale), draft = true, dissolve = request.dissolve)
                    drafted
                },
                settled = { request ->
                    show(paint(request, 1f), draft = false, dissolve = request.dissolve && !drafted)
                },
            )
        }
    }

    /** The preview size in pixels — the screen reports it once it is laid out, and again if it changes. */
    fun setViewport(width: Int, height: Int) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        rerender(dissolve = true)
    }

    /** Switch to [design], keeping the seed and palette — the same variation of a different generator. */
    fun pickDesign(design: WallpaperDesign) {
        if (design == mutableState.value.recipe.design) return
        mutableState.update { it.copy(recipe = it.recipe.copy(design = design)) }
        rerender(dissolve = true)
    }

    /** A new variation of the current design — a fresh seed, which is all a shuffle is. */
    fun shuffle() {
        mutableState.update { it.copy(recipe = it.recipe.copy(seed = Random.nextLong())) }
        rerender(dissolve = true)
    }

    /** Recolor the current design with [colors] — a chosen palette, keeping the design and seed. */
    fun setPalette(colors: List<Int>) {
        if (colors == mutableState.value.recipe.palette.colors) return
        mutableState.update { it.copy(recipe = it.recipe.copy(palette = Palette(colors))) }
        rerender(dissolve = true)
    }

    /**
     * Replace the design's Style knobs — the whole of [DesignParams] at once, since every control in the panel edits
     * one field of it.
     *
     * **Fired per frame of a drag, which is what the draft pass exists for**, and the picture swaps rather than
     * dissolving: a knob moving is one picture changing, where a shuffle is a different picture arriving. The panel's
     * commit calls this too and lands on the value already drawn, which the guard below turns into nothing.
     */
    fun setParams(params: DesignParams) {
        if (params == mutableState.value.recipe.params) return
        mutableState.update { it.copy(recipe = it.recipe.copy(params = params)) }
        rerender(dissolve = false)
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
        rerender(dissolve = true)
    }

    /**
     * Sets the picture on screen as the system wallpaper, then calls [onApplied].
     *
     * **Applies the bitmap the user is looking at, not a re-render** — the settled one. It fills the screen, so it is
     * at the display's resolution and is exactly what was approved: what-you-see-is-what-you-get, and one fewer
     * render. **A draft is refused rather than upscaled**, which is why the button greys while one is showing: a draft
     * is a fraction of the screen's pixels, and setting it would read as the studio having produced a soft wallpaper.
     * It is marked [WallpaperSource.PICKED] because a generated wallpaper the user chose to set *is* a chosen image as
     * far as the rest of the system is concerned; a distinct "generated" source is a later refinement for the
     * my-designs shelf.
     *
     * **[onApplied] runs on the main dispatcher after the work**, so it is safe for the screen to navigate away in it —
     * the apply has already finished by the time it fires, and leaving earlier would only cancel an apply the user did
     * not wait for.
     */
    fun apply(onApplied: () -> Unit) {
        val shot = mutableState.value.shot?.takeUnless { it.draft } ?: return
        if (mutableState.value.applying) return

        mutableState.update { it.copy(applying = true) }
        viewModelScope.launch {
            wallpaperRepository.setImage(shot.bitmap, WallpaperSource.PICKED)
            wallpaperRepository.apply(WallpaperTarget.BOTH)
            mutableState.update { it.copy(applying = false) }
            onApplied()
        }
    }

    /** Feeds the current recipe to the render loop at the current viewport. Nothing renders until a size is known. */
    private fun rerender(dissolve: Boolean) {
        if (viewportWidth == 0 || viewportHeight == 0) return
        requests.value = RenderRequest(
            recipe = mutableState.value.recipe,
            width = viewportWidth,
            height = viewportHeight,
            dissolve = dissolve,
        )
    }

    /**
     * Paints [request] at [scale] of the viewport — the one render path, run twice per request at two sizes.
     *
     * **`withContext` is what makes the settled pass abandonable at all**: an assignment is not a suspension point, so
     * a render that finished after being cancelled would otherwise publish its picture anyway. Returning through here
     * throws first.
     */
    private suspend fun paint(request: RenderRequest, scale: Float): Bitmap = withContext(Dispatchers.Default) {
        val width = (request.width * scale).roundToInt().coerceAtLeast(1)
        val height = (request.height * scale).roundToInt().coerceAtLeast(1)
        val recipe = request.recipe
        // The color mode is applied to the palette here, once, so the generator honors it without knowing it exists.
        val palette = PaletteColorMode.resolve(recipe.palette, recipe.params.colorMode)
        val base = Generators.forDesign(recipe.design).render(width, height, palette, recipe.params, recipe.seed)
        FilterPipeline.apply(base, recipe.filters)
    }

    private fun show(bitmap: Bitmap, draft: Boolean, dissolve: Boolean) {
        mutableState.update { it.copy(shot = WallpaperShot(bitmap, draft = draft, dissolve = dissolve)) }
    }

    /**
     * One picture to paint: a recipe, the size to paint it at, and how it should arrive.
     *
     * **[dissolve] is part of the request's identity, and that is harmless because it never moves alone** — every
     * setter that changes it changes the recipe or the viewport in the same breath. Carrying it here rather than
     * deciding at the screen is what keeps the rule (a discrete edit fades, a continuous one swaps) stated once, by
     * the code that knows which kind of edit it was.
     */
    private data class RenderRequest(
        val recipe: WallpaperRecipe,
        val width: Int,
        val height: Int,
        val dissolve: Boolean,
    )

    private companion object {

        /** What the studio opens on — one of the curated sets, warm-and-cool so any design has somewhere to go. */
        val DefaultPalette = Palette(ColorPalettes.all.first { it.name == "Dusk" }.colors)

        /**
         * The short side a draft is painted at, the viewport's aspect kept — the one number the cheap pass is tuned by.
         *
         * **Sized to the finest lattice any generator lays down, because a draft can be too small to be true.**
         * `ContourGenerator` samples its terrain on a lattice 360 cells across the short side and floors that cell at
         * one pixel, so below this a draft stops being the same picture at fewer pixels and becomes a *coarser field*:
         * its contours would move rather than soften, and the preview would be lying about the composition rather than
         * about its resolution. The icon studio's `DraftPx` is the same argument at a different scale.
         *
         * Costs roughly a ninth of a full-screen pass on a 1080×2400 phone, which is what a per-frame preview needs.
         */
        const val DraftShortSidePx = 360

        /**
         * How long a recipe must go unchanged before the full-size pass is worth starting.
         *
         * **Longer than the icon studio's 140ms, because a full-screen pass costs an order of magnitude more and
         * cannot in fact be abandoned** — the generators do not check for cancellation, so one started under a finger
         * that is still moving competes with every draft after it for the rest of its run. Still short enough that
         * lifting a finger reads as the picture sharpening rather than as waiting.
         */
        const val SettleMs = 220L
    }
}
