package inkspire.morphic.data.wallpaper

import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The wallpaper image this launcher owns: a file it wrote, and the size it wrote it at.
 *
 * **A path rather than a `Uri`**, because the point of owning a copy is that the source may go away — a picked image
 * comes from a document provider whose grant does not survive a reboot, so an app that kept the `Uri` would lose the
 * user's wallpaper. L1 owned a copy for the same reason and this keeps that.
 *
 * @property path an absolute path under `filesDir/wallpaper`, written by this module.
 * @property width the stored bitmap's width in px, and [height] its height — recorded so a consumer can size a preview
 *   without decoding the file.
 * @property source where the image came from, which is what decides whether [WallpaperRepository.apply] means
 *   anything for it.
 */
@Serializable
data class WallpaperImage(
    val path: String,
    val width: Int,
    val height: Int,
    val source: WallpaperSource = WallpaperSource.PICKED,
)

/**
 * How the launcher came by its wallpaper image — and, because of that, whether setting it on the system is meaningful.
 *
 * L1's `WallpaperSource` narrowed to what exists: its `LIVE_ROTATE` belongs to the rotating pair (S5e) and arrives with
 * it. The distinction is not bookkeeping — L1's `applySingle` branches on exactly this, and skips `WallpaperManager`
 * entirely for a capture.
 */
enum class WallpaperSource {

    /** Chosen from the photo picker and framed on the crop screen. The only kind that can *become* the wallpaper. */
    PICKED,

    /**
     * One half of the **rotating pair** — the portrait or landscape image the launcher's own live wallpaper draws.
     *
     * Not applied through `WallpaperManager` either, and for a different reason from a capture: a live wallpaper is set
     * by the *system's* chooser, which the user has to confirm, so [WallpaperRepository.apply] has nothing to do with
     * it. What sets it is [RotatingWallpaperService] becoming the active wallpaper; what this source marks is which
     * file the service will draw.
     */
    ROTATING,

    /**
     * A screenshot the user took with the launcher's UI hidden — **a picture of the wallpaper, not a wallpaper.**
     *
     * Applying one would set a photograph of the current wallpaper as the wallpaper: a no-op at best, and at worst a
     * slow drift as each capture re-encodes the last. It exists as an *effect input*: it is the only way to sample a
     * live wallpaper, which cannot be read as a bitmap. So it is stored and previewed like any image, and
     * [WallpaperRepository.apply] declines it.
     */
    CAPTURED,
}

/** Where a wallpaper is set on the system: the home screen, the lock screen, or both. L1's `WallpaperTarget`. */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * **How bright the wallpaper behind the launcher's chrome is** — the launcher's dark/light input.
 *
 * Launcher chrome sits *on* the wallpaper with nothing between, so what it has to contrast is the picture, not the
 * system's dark-mode switch. That is the whole of the design system's "one theme, two is-dark inputs" rule: settings is
 * a surface of our own and feeds `isSystemInDarkTheme()`, while the shell feeds this.
 *
 * **L2's own idea, not a port.** L1 has no luminance analysis anywhere — it themes the launcher from the system's dark
 * mode like any app, which is why a bright wallpaper under white chrome is one of the things that reads badly there.
 *
 * Two values rather than three, with no `UNKNOWN`: the consumer is a theme, and a theme has to pick. What would be
 * "unknown" is [DARK], because that is what the shell hardcoded before this existed and it is the safer miss — light
 * chrome over an unexpectedly bright wallpaper is unreadable, where dark chrome over a dark one is merely dull.
 */
enum class WallpaperBrightness {

    /** A bright wallpaper — chrome should be dark-on-light. */
    LIGHT,

    /** A dark wallpaper, or nothing legible to go on — chrome should be light-on-dark. */
    DARK,
}

/**
 * The region of a source image to keep, as fractions of it — L1's `NormalizedCropRect`, kept name and all.
 *
 * **Fractions rather than pixels, because the crop is decided against a bitmap this module chose the size of.** The
 * screen shows a *sampled* decode (a 50-megapixel photo is not going on screen at full size), so a rectangle in that
 * bitmap's pixels would be meaningless against the source — and doubly so if the sampling ever changes. Fractions
 * survive both: they describe the picture rather than the decode.
 *
 * The whole image is `0f..1f` on both axes, which is also what [Full] means and what a caller with nothing to say
 * should pass.
 */
@Serializable
data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    companion object {
        /** Keep everything. The identity crop, for a caller that has not framed one. */
        val Full = NormalizedCropRect()
    }
}

/**
 * The launcher's **rotating wallpaper**: one image per orientation, drawn by [RotatingWallpaperService].
 *
 * A pair rather than a list, because that is what it is — a phone has two orientations, and the service picks between
 * them by comparing the surface's width and height. Either half may be absent while the user is still setting it up,
 * and the service falls back to whichever exists, so a half-configured pair still draws something rather than black.
 *
 * **This is where "the wallpaper" stops being a single image**, which is the reason this slice lands before the effects:
 * an effect has to answer "which image do I sample?", and the answer is per-orientation as soon as this exists.
 */
@Serializable
data class RotatingImages(
    val portrait: WallpaperImage? = null,
    val landscape: WallpaperImage? = null,
) {
    /** The image for [orientation], or null if that half has not been set. */
    operator fun get(orientation: Orientation): WallpaperImage? = when (orientation) {
        Orientation.PORTRAIT -> portrait
        Orientation.LANDSCAPE -> landscape
    }

    /** [image] as the [orientation] half, leaving the other alone — the merge `setRotatingImage` commits. */
    fun with(orientation: Orientation, image: WallpaperImage): RotatingImages = when (orientation) {
        Orientation.PORTRAIT -> copy(portrait = image)
        Orientation.LANDSCAPE -> copy(landscape = image)
    }

    /** True when neither half is set — the state in which applying the live wallpaper would draw nothing. */
    val isEmpty: Boolean get() = portrait == null && landscape == null
}

/**
 * What this module knows about the wallpaper — **the chosen image, and whether we are the one that set it**.
 *
 * **Much smaller than L1's `WallpaperState`, and deliberately so.** That one carried six fields (`appliedMode`,
 * `single`, `rotate`, `appliedSingle`, `singleDirty`, `appliedSystemWallpaperId`) because it juggled *two* image sets
 * (a single image and a per-orientation rotating pair) and kept a **snapshot copy** of whichever was applied so a
 * frosted backdrop could keep sampling the real system wallpaper. Two of those six are here now, and the snapshot is
 * one of them — arriving with the effects exactly as this note predicted, having been mistaken in between for something
 * [appliedSystemId] had replaced. It had not: see that property and [appliedHome], which answer different questions.
 *
 * @property image the wallpaper the user chose, or null if they never have.
 * @property rotating the per-orientation pair the launcher's own live wallpaper draws, empty until the user sets one.
 *   Beside [image] rather than instead of it, because they are independent: a user may keep a static image *and* a
 *   rotating pair, and switch by setting one or the other on the system. L1 kept both for the same reason.
 * @property appliedSystemId the `WallpaperManager` wallpaper id at the moment we last applied [image], or 0 if we never
 *   did. Kept for two jobs a boolean could not do: it is what makes the section's button read "Apply" or "Re-apply",
 *   and comparing it against the live id is how a wallpaper set **outside** this launcher will be detected (L1 stored
 *   the same `appliedSystemWallpaperId` and used it exactly that way).
 *
 * **What is deliberately *not* here: which wallpaper is currently active.** L1 stored that (`appliedMode`, latched by
 * `markRotateApplied`) and then needed `reconcileLiveWallpaper` on every resume to repair the cache when the user
 * changed wallpaper outside the app — a copy of something the system already knows, plus a job to keep the copy honest.
 * `WallpaperManager.wallpaperInfo` answers it directly, so [WallpaperRepository.isRotatingActive] asks instead of
 * remembering, and there is no latch, no reconciler, and nothing to go stale. That is smell 7 on this port's own list
 * ("derived state persisted as settings") declined rather than ported.
 */
@Serializable
data class WallpaperState(
    val image: WallpaperImage? = null,
    val rotating: RotatingImages = RotatingImages(),
    val imageApplied: Boolean = false,
    val appliedHome: WallpaperImage? = null,
    val appliedSystemId: Int = 0,
) {
    companion object {
        val Default = WallpaperState()
    }
}

/** The files this module owns under `filesDir/`[DIR]. Named here because the file *is* the persisted wallpaper. */
object WallpaperFiles {
    const val DIR = "wallpaper"

    /** The chosen image, cropped and scaled to the screen. L1's `owned.jpg`. */
    const val IMAGE = "single.jpg"

    /**
     * A copy of [IMAGE] as it was applied to the **home** wallpaper — what a frosted surface samples. L1's
     * `owned_applied.jpg`.
     *
     * A second file rather than a second reference, because [IMAGE] is a fixed name that every pick overwrites: the
     * moment a user chooses a new image the previous one is *gone*, and it is the previous one the home screen is still
     * showing until they apply. See `WallpaperState.appliedHome`.
     */
    const val APPLIED_HOME = "applied_home.jpg"

    /** The rotating pair, one per orientation — L1's `owned_portrait.jpg` / `owned_landscape.jpg`. */
    const val ROTATING_PORTRAIT = "rotating_portrait.jpg"
    const val ROTATING_LANDSCAPE = "rotating_landscape.jpg"
}

/**
 * **Reading and setting the launcher's wallpaper** — the module `feature:settings`' wallpaper section is built on.
 *
 * A *service* rather than a preferences store, which is why it is its own module rather than another slice of
 * `data:settings`: it decodes bitmaps, writes files, and talks to `WallpaperManager`. What it persists is a **pointer**
 * to a file it wrote plus the id of the wallpaper it set — bookkeeping, not preferences, which is the distinction the
 * settings port's S0 drew when it refused to bring L1's `WallpaperState` across into the settings blob. The *effect*
 * params (`BackdropEffect`) genuinely are preferences and stay there, arriving with S5f.
 *
 * **All three of L1's sources are here — picked, captured and the rotating pair** — and the half that *reads* them has
 * started: [brightness] is the first, and it is the one reading that needs no image processing at all. Still absent:
 * - **the blur and the dominant color** (`loadBackdropBlur`, `loadDominantColor`) — both are effect inputs, and both
 *   need L1's `Blur.kt` image processing, which the plan already says belongs beside the graphics code rather than in a
 *   repository. The **capture** exists for them, and lands first on purpose: an effect has to answer "which image do I
 *   sample?", and answering that once against every source beats re-answering it per source.
 */
interface WallpaperRepository {

    /** The chosen image and whether we set it — see [WallpaperState]. Emits [WallpaperState.Default] before a choice. */
    val wallpaper: Flow<WallpaperState>

    /**
     * How bright the **currently displayed** wallpaper is — see [WallpaperBrightness]. Re-emits when it changes.
     *
     * Note "currently displayed", not "the one we own": a launcher's chrome has to contrast whatever is actually behind
     * it, which may be a wallpaper another app set or a live wallpaper that is not ours. So this asks the *system*
     * first, and only falls back to reading our own file when the system says nothing **and** our file is provably what
     * is on screen (`appliedSystemId` still matching the live id — the second job that field's KDoc reserved it for).
     *
     * **It does not need `Blur.kt`'s `dominantColor`, which the port plan assumed it would**, for two reasons worth
     * writing down. The first is that `WallpaperManager.getWallpaperColors` already answers this: the OS computes it
     * over the real wallpaper, with no permission and no decode, and `HINT_SUPPORTS_DARK_TEXT` is literally the
     * question. The second is that `dominantColor` would be the *wrong statistic* even as a fallback — it is a
     * saturation-weighted average, deliberately biased so a vivid accent beats washed-out gray, which is what an
     * *accent* wants and the opposite of what "is this bright?" wants. Those are separate readings of one image, and
     * the blur half of `Blur.kt` still has no consumer until the frosted backdrop lands.
     */
    val brightness: Flow<WallpaperBrightness>

    /**
     * The wallpaper's **representative color** as ARGB, or null when it cannot be read — what
     * `BackdropTint.WALLPAPER` washes a frosted surface in.
     *
     * The third reading of "what is displayed", beside [brightness] and [backdrop], and it takes the same two-step:
     * `WallpaperColors.getPrimaryColor` first, because the OS computed it over the wallpaper *actually* on screen
     * (another app's, a live one) with no permission and no decode; `dominantColor` over our own file only below API
     * 27 or when a live wallpaper publishes nothing, and then only if that file is provably what is displayed.
     *
     * **Not `MaterialTheme.colorScheme.primary`, which is how L1 got this above API 31.** L1's launcher ran a normal
     * M3 dynamic scheme, so its primary *was* a wallpaper-derived hue. L2 feeds MaterialTheme a **monochrome** scheme
     * bridged from `MorphicColors`, so the same expression here returns gray — the dynamic-color route is closed by
     * a decision made long before this, and reading the wallpaper directly is what is left.
     */
    val accentColor: Flow<Int?>

    /**
     * A downsampled bitmap of [uri], for showing the user what they picked before anything is written.
     *
     * Sampled rather than decoded whole: a modern camera image is tens of megapixels, and a preview is a few hundred
     * dp. Null when the image cannot be read at all — a provider that revoked the grant, or a file that is not an image.
     */
    suspend fun decodePreview(uri: Uri): Bitmap?

    /**
     * Copies [uri] into this module's own storage as the wallpaper image: the [crop] region of it, scaled to
     * [outWidth] × [outHeight].
     *
     * **The rectangle is the caller's**, which is the change S5c made. This used to center-crop, as a stand-in for a
     * chooser that did not exist; now the crop screen passes the region the user framed, and there is nothing left
     * here that invents one. A caller with genuinely nothing to say passes [NormalizedCropRect.Full] — that is not the
     * old behavior under a new name, since keeping the whole image *stretches* it to the output rather than filling
     * it, which is why nothing does that today.
     *
     * Storing a cropped, screen-sized file at all (rather than the original) is what makes the stored file the thing
     * that is displayed — the same reason L1 scaled on the way in.
     *
     * **Does not touch the system wallpaper**; [apply] does. Choosing and applying are separate because the user may
     * pick an image, look at it, and change their mind — and because applying asks *where* (home, lock, both).
     *
     * @param outWidth the size to store at, which the crop screen passes as the **viewport it framed against**. That
     *   is what makes the result what the user saw: the rectangle and the output share one coordinate space.
     * @param source how the image was obtained. One write path for both, because they differ in exactly this: a
     *   capture is already the size and shape of the screen, so it passes [NormalizedCropRect.Full] — which is what
     *   that value was declared for.
     */
    suspend fun setImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        source: WallpaperSource,
    )

    /**
     * Sets the stored image as the system wallpaper on [target], and records the id the system gave it — **but only
     * when [target] included the home wallpaper.** `appliedSystemId` is evidence about the picture the launcher's chrome
     * sits on, which is the home one; a lock-only apply writes nothing there and leaves whatever was already claimed
     * alone, since it neither made nor broke that claim. Nothing records the lock wallpaper, because nothing asks.
     *
     * A no-op when nothing has been chosen, **and when the stored image is a [WallpaperSource.CAPTURED] one** — that
     * is a picture *of* the wallpaper, so setting it would either change nothing or re-encode the last capture. L1
     * branched on the same field in `applySingle` for the same reason. The section correspondingly offers no Apply for
     * a capture, but the rule lives here, where it cannot be worked around.
     *
     * Failure to set is logged rather than thrown: `WallpaperManager` can refuse for reasons the caller cannot fix or
     * predict (a device policy, a provider that vanished), and a settings screen has nothing useful to do with an
     * exception. L1 swallowed it the same way, with the same `runCatching`.
     */
    suspend fun apply(target: WallpaperTarget)

    /** The stored image as a bitmap, for a preview at real size. Null when nothing is stored, or the file is gone. */
    suspend fun loadImage(): Bitmap?

    /**
     * The wallpaper a frosted surface should sample, pre-blurred at [strength] and downscaled — or **null when there is
     * nothing we can honestly claim is behind the chrome**, which every consumer renders as its own flat color.
     *
     * **This is the "which image do I sample?" question the whole slice order was arranged around**, and it is answered
     * once here against all three sources rather than per effect:
     * 1. **Our rotating service is the live wallpaper** → the [orientation] half of the pair. No further test is needed:
     *    the service renders these exact files, so the two cannot disagree. L1 said the same of its `ROTATE` branch.
     * 2. **The stored image is a [WallpaperSource.CAPTURED] one** → it, unconditionally. A capture *is* a picture of
     *    whatever is displayed, which is the only reason it exists — gating it on having been applied would reject it
     *    always, since [apply] refuses a capture by design.
     * 3. **We kept a copy of what we applied to the home wallpaper, and it is provably still on the system**
     *    (`appliedHome` present, `appliedSystemId` matching the live wallpaper id) → that copy, never the current pick.
     *    The two differ whenever someone has chosen an image without applying it to home — including applying one to
     *    the *lock* screen, which cannot change what the launcher's chrome sits on.
     * 4. Otherwise null.
     *
     * **Step 3 needs both halves, and this once claimed it needed only one.** L1 kept a snapshot copy of the applied
     * image (`appliedSingle`) so that picking a new one without applying it could not desynchronize the backdrop from
     * the real wallpaper; the id comparison was taken to replace it. It does not. The id answers *"is the wallpaper on
     * the system still the one we set?"* — a question the snapshot could not answer, since a wallpaper changed outside
     * the launcher left L1's copy claiming to match — but it says nothing about whether we **still have that picture**,
     * and one fixed filename means every pick overwrites it. So both: the id, and `appliedHome`. It is the same gate
     * [brightness] uses, deliberately — "is our image what is on screen" should have one answer, not two.
     *
     * Blurred here rather than at the draw call because it is expensive and wants a background thread, and because the
     * result is reused for every frosted surface on screen. L1's `loadBackdropBlur`, same shape.
     *
     * **A flow, where L1's was a one-shot read.** All four answers above can change while the launcher is running, and
     * two of them change *without us doing anything*: a wallpaper set in the system's own settings makes step 3 stop
     * holding, and the live-wallpaper chooser makes step 1 start. L1 re-read on recomposition and could show a blur of
     * a wallpaper that was no longer there. This shares [brightness]' change signal, since "what is displayed" is one
     * question and both readings of it should notice the same events.
     *
     * Re-emits only when the *source* changes, so nothing re-blurs because an unrelated preference moved. A change of
     * [strength] is a new collection rather than an emission — the caller re-invokes with the new value.
     *
     * **"Source" is the file's identity, not its name**, and that distinction is load-bearing: the stored images live at
     * fixed paths and are overwritten in place, so a capture taken over a capture — or a half of the rotating pair
     * replaced while that pair is live — changes the picture without changing the path, and would otherwise leave every
     * frosted surface showing the previous wallpaper until the launcher restarted.
     *
     * **[orientation] is a flow, and that is the difference between a rotation costing nothing and costing a decode.**
     * It matters to step 1 alone: the rotating pair is two files, so turning the device genuinely changes which picture
     * is on screen. For a picked or captured image the answer is the same file whichever way the phone is held — so if
     * the orientation were a *value*, every rotation would be a new collection, the "did the source change?" comparison
     * below would restart with nothing to compare against, and the launcher would re-decode and re-blur a picture
     * identical to the one it already had. At a low strength that picture is nearly the whole screen, so the bill for
     * turning the phone was two full-resolution decodes. Passed as a flow it reaches the comparison instead, which
     * answers "no change" for two of the three sources and "different file" for the one where that is true.
     *
     * @param strength `0f..1f`, mapped to a blur radius and pass count internally so callers pass a preference and not
     *   a pixel count. Zero is a valid strength meaning "sample it sharp", which is not the same as no backdrop.
     * @param orientation which half of the rotating pair to sample, as it changes; ignored by the other two sources,
     *   whose stored image is already this screen's.
     */
    fun backdrop(strength: Float, orientation: Flow<Orientation>): Flow<Bitmap?>

    /**
     * The same wallpaper as [backdrop], re-blurred for **every** strength [strength] emits — the picture a *drag* is
     * read against.
     *
     * **One decode, many blurs, which is the whole difference from [backdrop].** That one is a subscription per
     * strength: a new value means a new collection, a fresh decode at a reduction chosen for that radius, and one blur.
     * Correct for a setting that changes when somebody lets go of a slider, and hopeless at frame rate — a JPEG decode
     * is 10–20ms, so a drag would queue a backlog and arrive after the finger lifted. Here the decode happens when the
     * *picture* changes and the blur happens when the *strength* does, which is the only shape that can keep up.
     *
     * **A `Flow<Float>` rather than a value, and that is the mechanism rather than a style.** As a subscription key a
     * strength would re-decode; as a flow it reaches the blur. It is also what makes the rate self-limiting: a
     * `StateFlow` of the dragged value conflates, so this produces as many frames as the machine can and skips the
     * values it was too slow for, rather than falling behind.
     *
     * **Fixed reduction, where [backdrop] ties it to the radius.** A preview blurred at a strength-dependent size would
     * have to re-decode at every strength, which is the thing being avoided — so it is one size, and the radius is
     * measured against it so the *softness* still matches what a surface will show. The bill is resolution at the sharp
     * end: below about an eighth of the slider [backdrop] keeps more of the picture than this does, so a preview drawn
     * from this is softer than the real thing there. That is why it is for the drag alone; the caller shows the settled
     * picture the moment the finger lifts, and this one is never what a decision is made against.
     *
     * Same source rules as [backdrop] — same file, same gate, null for the same reasons.
     */
    fun backdropPreview(strength: Flow<Float>, orientation: Flow<Orientation>): Flow<Bitmap?>

    /**
     * Stores the [crop] region of [uri] as the [orientation] half of the rotating pair, at [outWidth] × [outHeight].
     *
     * **Merges rather than replaces**: setting the landscape half leaves the portrait one alone, because the pair is
     * built one orientation at a time and losing the other half to each edit would make it impossible to finish. L1's
     * `setRotateImage` says the same in its own KDoc.
     *
     * **Touches nothing on the system.** Unlike [apply], there is nothing this could do: a live wallpaper is set through
     * the system's own chooser, which the user has to confirm. Writing the file is the whole of this module's part —
     * [RotatingWallpaperService] reads it the next time it draws, so an image changed while the wallpaper is live
     * appears on the next redraw without anything being re-applied.
     *
     * @param outWidth the size to store at, which is the *target orientation's* screen rather than the current one — a
     *   landscape half is framed and stored landscape-shaped even while the phone is held upright.
     */
    suspend fun setRotatingImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        orientation: Orientation,
    )

    /** The [orientation] half of the rotating pair as a bitmap, for its slot in the section. Null when unset. */
    suspend fun loadRotatingImage(orientation: Orientation): Bitmap?

    /**
     * Whether the launcher's own [RotatingWallpaperService] is the wallpaper the system is currently showing.
     *
     * **Asked, never remembered** — see [WallpaperState]. `WallpaperManager.wallpaperInfo` is non-null exactly when a
     * live wallpaper is set and names which, so this is a read rather than a cache: a user who changes wallpaper in the
     * system's own settings makes the next call return the truth, with nothing to reconcile. L1 latched the answer into
     * `appliedMode` and needed a resume-time reconciler to repair it.
     *
     * Suspending because it is a binder call, not because it is slow.
     */
    suspend fun isRotatingActive(): Boolean

    /**
     * The launcher's live-wallpaper service, for a caller that needs to name it in an intent.
     *
     * The component is this module's to know — it declares the service — and starting an activity is the *screen's*, so
     * the split is: this hands over the name, and `feature:settings` builds `ACTION_CHANGE_LIVE_WALLPAPER` around it.
     * L1 kept both in its feature module, which meant its data layer could not say what its own service was called.
     */
    fun rotatingServiceComponent(): ComponentName

    /**
     * Emits each image that appears in the device's gallery **after collection starts** — how a capture is noticed.
     *
     * There is no API for "take a screenshot", so L1's capture flow is the only one available: hide the launcher's UI,
     * ask the user to take one, and watch `MediaStore` for what arrives. This is that watch, moved out of the screen it
     * lived in — a `ContentObserver` and a query are system reads, and a composable is the wrong place to hold either.
     *
     * **It cannot tell a screenshot from any other new image**, and neither could L1's: what it reports is the newest
     * image, whatever produced it. That is why the screen asks for a screenshot *now* and takes the first emission — a
     * photo arriving from a sync at that exact moment is the known failure, and it is recoverable by capturing again.
     *
     * Requires read access to media images; without the permission the query returns nothing and this stays silent
     * rather than throwing, since the screen has already asked and can do nothing more about it.
     */
    fun newGalleryImages(): Flow<Uri>
}
