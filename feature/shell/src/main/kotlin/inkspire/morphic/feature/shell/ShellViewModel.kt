package inkspire.morphic.feature.shell

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.apps.AppInfoOpener
import inkspire.morphic.data.apps.AppShortcut
import inkspire.morphic.data.apps.AppShortcuts
import inkspire.morphic.data.apps.AppUninstaller
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SurfaceRegister
import inkspire.morphic.data.wallpaper.WallpaperBrightness
import inkspire.morphic.data.wallpaper.WallpaperRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the launcher shell renders.
 *
 * @property register HOME's layout, its per-edge bindings, and the crossing transition. [SurfaceRegister.Default]
 *   until the store's first emission, which is why the shell never renders "no settings" — it renders the defaults,
 *   and the defaults bind no edge.
 * @property pagerWraps whether each pager's pages loop, resolved. The shell needs it because wrapping decides the
 *   *gesture*, not just the animation: a pager with no end has no edge to hand a one-finger swipe off at, so it makes
 *   that edge two-finger-only. This is the one state field read for both halves of a binding at once — HOME's pager
 *   for the open policy, the bound layout's for the close — which is why it arrives as a map rather than as the two
 *   booleans a single surface would want.
 * @property brightness how bright the wallpaper behind the chrome is, which is the launcher's dark/light input.
 *   [WallpaperBrightness.DARK] until the first read, which is what the shell hardcoded before this existed — so a
 *   first frame looks exactly as it used to and then corrects if the wallpaper is bright.
 * @property backdropEffect how frosted surfaces render over the wallpaper. Handed down as-is rather than resolved,
 *   because *what* it means is a drawing decision and belongs to the modifier that draws it.
 * @property backdropImages the wallpaper pre-blurred at the two strengths frosted surfaces render at — the user's own,
 *   and the full-screen frost's fixed one. Both null when the launcher has nothing it can honestly claim is on screen
 *   (see `WallpaperRepository.backdrop`), which is one condition for both, since they read the same file.
 *   `android.graphics.Bitmap` and not `ImageBitmap` on purpose: the conversion is a Compose concern, and a state holder
 *   that returns Compose graphics types is one step from doing composition work.
 * @property backdropAccent the wallpaper's representative color as ARGB, which every frosted wash is blended toward.
 *   Null when unreadable, which makes the washes plain white and black. Separate from [backdropImages] because it has a
 *   separate source — the system usually answers it without any image being read at all.
 */
data class ShellState(
    val register: SurfaceRegister = SurfaceRegister.Default,
    val pagerWraps: Map<GridSlot, Boolean> = emptyMap(),
    val brightness: WallpaperBrightness = WallpaperBrightness.DARK,
    val backdropEffect: BackdropEffect = BackdropEffect.Default,
    val backdropImages: BackdropImages = BackdropImages(),
    val backdropAccent: Int? = null,
)

/**
 * The wallpaper at the two blurs the launcher draws it at.
 *
 * **Two because a panel and the full-screen frost answer to different strengths** — the effects section's slider is the
 * panels', where the frost is fixed so that a surface arriving over HOME occludes it whatever was picked (see
 * `BackdropEffect.fullScreenFilm`). A single image cannot be both: at a panel blur of zero it is the sharp wallpaper,
 * and a sharp sheet occludes nothing.
 *
 * A pair rather than two fields on [ShellState] because they are produced together, by one flow, and go on to be
 * consumed together — `BackdropState` keeps the same pairing one layer up, where each picture also carries the mapping
 * derived from its own dimensions.
 */
data class BackdropImages(
    val panel: Bitmap? = null,
    val film: Bitmap? = null,
)

/**
 * Screen-level state holder for the launcher shell: the surface register, and how bright the wallpaper is.
 *
 * **Why a ViewModel for what looked like one flow read.** It would have been one line to `koinInject` a repository in
 * the composable and collect it there, which is how a feature ends up with no presentation layer at all. The rule is
 * a ViewModel per screen, and the second input has now arrived: this
 * is the `combine` that was predicted, rather than a second `collectAsStateWithLifecycle` plus assembly logic in the
 * composable.
 *
 * **Two repositories, and they are unrelated on purpose.** The register is a preference and brightness is a reading of
 * the world; joining them is this holder's whole job, and neither store has to know the other exists.
 *
 * No write path: the shell *obeys* both. Editing the register is the settings surface's job, and brightness is not
 * anybody's to set — it is what the wallpaper happens to be.
 */
class ShellViewModel(
    settingsRepository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val layoutRepository: LayoutRepository,
    private val appUninstaller: AppUninstaller,
    private val appInfoOpener: AppInfoOpener,
    private val appShortcuts: AppShortcuts,
) : ViewModel() {

    /**
     * **Takes [item] off HOME** — the top-action band's Remove target.
     *
     * It lives here rather than on `HomeViewModel` because the band spans every surface: the item may have been
     * lifted in the APPS drawer and never placed at all, and the shell is the only layer above both. That case is
     * also why this needs no "is it placed?" test — `RemoveFromGrid` on an app with no placement deletes no rows, so
     * a drag that came from the drawer and was thrown at Remove simply ends, which is exactly the cancel it should
     * be. A folder or container, which can only have come *from* home, is destroyed with its contents' membership,
     * as its own KDoc says.
     *
     * **One bound worth knowing:** an app dragged out of a home *folder* and dropped here goes back to that folder
     * rather than being deleted from it. It has no grid placement to remove, and the shell cannot see folder
     * membership — that is home's.
     */
    fun removeFromHome(item: GridItem) {
        viewModelScope.launch {
            layoutRepository.apply(ORIENTATION, listOf(LayoutChange.RemoveFromGrid(item)))
        }
    }

    /**
     * **Opens the system uninstaller** for [component] — the band's Uninstall target.
     *
     * Deliberately *not* paired with a [removeFromHome]: the platform asks the user to confirm and may well be
     * declined, and removing the icon first would leave an installed app the user can no longer see. The layout
     * prunes itself when the app-removed event arrives, which is the same path any uninstall from anywhere takes.
     */
    fun uninstall(component: ComponentKey) = appUninstaller.uninstall(component)

    /**
     * **Opens the system app-details screen** for [component] — the item menu's "App info".
     *
     * Here rather than on either surface's ViewModel for the menu's own reason: the same three verbs are offered
     * for an app wherever it is found, so binding them once at the shell is what stops home and the drawer
     * drifting apart. It joins [uninstall], which the top-action band already needed here for exactly that.
     */
    fun openAppInfo(component: ComponentKey) = appInfoOpener.openAppInfo(component)

    /**
     * **[component]'s own shortcuts** — the item menu's first stage.
     *
     * Suspending and *not* launched on [viewModelScope], deliberately: the read belongs to the menu that asked for
     * it, so it is called from the menu's own composition and canceled when the menu closes. Scoping it to the
     * screen would keep a read alive for a menu that is no longer on screen and deliver an answer nobody wants.
     * Empty when the launcher is not the active home app — see [AppShortcuts], which owns that rule.
     */
    suspend fun shortcuts(component: ComponentKey): List<AppShortcut> = appShortcuts.shortcuts(component)

    /** Starts one of the shortcuts [shortcuts] returned. Fire-and-forget, like [uninstall]. */
    fun startShortcut(shortcut: AppShortcut) = appShortcuts.start(shortcut)

    /**
     * Which way the device is held, reported by the shell.
     *
     * The backdrop needs it and cannot derive it: a **rotating** wallpaper is two different pictures, so "the
     * wallpaper" is not one image until you say which orientation. Reported rather than read from a `Configuration`
     * here for the reason every other surface reports its `DeviceConfiguration` — the composable is where the window
     * is, and a state holder that reaches for one has a `Context` in it.
     */
    private val orientation = MutableStateFlow(Orientation.PORTRAIT)

    val state: StateFlow<ShellState> =
        // Six sources against `combine`'s five, so the two that come from the same store and answer the same
        // question — what is bound to each edge, and how the pagers behind those bindings page — are grouped first.
        combine(
            combine(settingsRepository.surfaceRegister, settingsRepository.pagerWraps, ::Pair),
            wallpaperRepository.brightness,
            settingsRepository.backdropEffect,
            backdropImages(settingsRepository),
            wallpaperRepository.accentColor,
        ) { (register, wraps), brightness, effect, images, accent ->
            ShellState(register, wraps, brightness, effect, images, accent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellState())

    /** Reports the orientation the shell is being drawn in, so the rotating pair's right half is sampled. */
    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The blurred wallpaper at both of the strengths frosted surfaces render at, each re-collected only when **its own**
     * strength moves.
     *
     * **Two strengths taken from one read of the effect, which is what keeps them from drifting.** The panel strength is
     * the stored one — this is the line that makes the effects section's blur slider do something — and the film's is
     * what `fullScreenFilm` replaces it with, asked of the same value rather than restated as a constant here. So the
     * number the full-screen layer *renders* at and the number its picture is *blurred* at come from one expression, and
     * a change to that policy cannot leave the picture behind.
     *
     * **A subscription per picture, not one over the pair, and that is what stops a slider drag re-baking the film.**
     * Keyed on the pair, moving the panel's strength restarted both collections — so the film re-decoded and re-blurred
     * at a strength that had not moved and could not, being a constant. `distinctUntilChanged` per strength is the fix,
     * and it is the same correction the orientation needed one layer down: **de-duplicate where the value is owned**,
     * because a key made of several things cannot say which of them changed.
     *
     * That also states the cost honestly. A panel strength commit costs *one* decode, the panel's; the film's picture is
     * baked once for the life of the shell and again only if the displayed wallpaper changes. Only the first collection
     * pays for both.
     *
     * De-duplication is per strength rather than on the effect as a whole, so a tint or a lens parameter moving
     * re-blurs nothing: those are draw-time reads and the picture is unchanged by them. Switching between two variants
     * that share a strength (which the defaults do) is likewise free.
     *
     * `flatMapLatest`, so a strength commit cancels an in-flight blur rather than queueing one behind it; the flow it
     * switches to is the repository's, which re-emits on its own when the displayed wallpaper changes.
     *
     * **The orientation is handed over as a flow rather than joined into either key**, which is what keeps a rotation
     * from re-blurring anything: for every source but the rotating pair it names the same file, and only the repository
     * can tell — so it does the comparison, where a key here would restart the collection and re-decode. Measured on a
     * device: turning the phone cost two decodes of the wallpaper, one of them very nearly full-screen.
     *
     * The one cost left: at a panel strength of **exactly zero** the panel's picture is the whole screen, which is what
     * "no blur" means and the only strength that reaches full resolution (`blurBackdrop` halves anything it blurs at
     * all). The film's is an eighth of the screen and rounds to nothing beside it.
     */
    private fun backdropImages(settingsRepository: SettingsRepository): Flow<BackdropImages> = combine(
        blurredWallpaper(settingsRepository.backdropEffect.map { it.blurStrength }),
        blurredWallpaper(settingsRepository.backdropEffect.map { it.fullScreenFilm.blurStrength }),
        ::BackdropImages,
    )

    /**
     * The wallpaper blurred at [strength], re-read only when that strength actually moves.
     *
     * Split out so the two pictures cannot share a subscription — see [backdropImages]. Collecting the effect twice
     * costs a second decode of one small JSON slice per write of it, which is what buys each picture the right to be
     * left alone; `DataStore` serves both collectors from the value it already holds rather than reading the file twice.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun blurredWallpaper(strength: Flow<Float>): Flow<Bitmap?> = strength
        .distinctUntilChanged()
        .flatMapLatest { wallpaperRepository.backdrop(it, orientation) }

    private companion object {
        /** Keeps the store subscription alive across a configuration change instead of tearing it down and back up. */
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The orientation layout writes are scoped to. Portrait-only, matching `HomeViewModel`'s own constant — home
         * does not store per-posture placements yet, and a removal has to name the same tables the placement did.
         */
        val ORIENTATION = Orientation.PORTRAIT
    }
}
