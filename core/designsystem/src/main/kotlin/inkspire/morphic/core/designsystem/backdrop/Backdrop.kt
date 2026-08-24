package inkspire.morphic.core.designsystem.backdrop

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.BackdropTint
import kotlin.math.roundToInt

/**
 * One pre-blurred picture, and the mapping that finds a node's own piece of it.
 *
 * **The two are one type because separating them is the way this subsystem misaligns.** The mapping is derived from
 * *this* bitmap's dimensions ([screenToBitmapMapping]); apply it to a bitmap of another size and every frosted surface
 * draws its crop at the wrong scale — which on screen reads as the wallpaper sitting a little off behind the glass,
 * not as a mismatched pair of arguments. That was a convention while there was one picture. There is more than one
 * now, so it is a type.
 *
 * @property image the wallpaper, already blurred and reduced by `WallpaperRepository.backdrop`. How much smaller than
 *   the screen it is depends on the blur it was made for — a heavy blur has no detail left to lose, and a light one
 *   is close to full resolution because a sharp crop is exactly what it has to be.
 * @property screenToBitmap maps a rectangle in **screen** coordinates onto the matching sub-rectangle of [image] — see
 *   [screenToBitmapMapping] for why it is screen and not window coordinates.
 */
class BackdropImage(
    val image: ImageBitmap,
    val screenToBitmap: (Rect) -> Rect,
)

/**
 * Which of [BackdropState]'s pictures a frosted surface samples.
 *
 * **Two, because two kinds of surface answer to different strengths, and neither can borrow the other's picture.** A
 * *panel* renders the user's own choice — the effects section's blur slider is theirs. The full-screen *film* is fixed
 * (`BackdropEffect.fullScreenFilm`), because a surface arriving over HOME has to occlude it whatever decoration was
 * picked. One image cannot serve both: at a panel blur of zero it is the sharp wallpaper, and a sharp sheet occludes
 * nothing at all.
 */
enum class BackdropRole {

    /**
     * A bounded frosted surface with **edges of its own**, blurred at the user's own strength: a container tile on
     * the home grid, an icon's plate.
     *
     * **"Bounded" means the surface's edges are not the window's**, which is the same line `wallpaperBackdrop`'s
     * `refracts` draws and the reason these two things are one question rather than two. A context menu and a bottom
     * sheet look bounded and are not — one is anchored to an item and the other to the screen's bottom edge, both
     * hold a screenful of rows, and both take [FILM] instead. See [filmBackdrop].
     */
    PANEL,

    /** The full-screen sheet a surface arrives over. Blurred at the fixed strength `fullScreenFilm` names. */
    FILM,
}

/**
 * The pre-blurred wallpaper frosted surfaces sample, and how each finds its own piece of it.
 *
 * **A shared image plus a mapping, rather than a bitmap per surface** — which is the whole reason a frosted surface is
 * affordable. Every one of them draws a *crop*, positioned so its piece lines up with the wallpaper around it; two
 * frosted surfaces side by side therefore continue each other rather than each showing the same blur.
 *
 * **Two pictures rather than one, and that is [BackdropRole]'s doing rather than a cache.** They are the same
 * wallpaper at two blurs, so the cost is a second decode on a wallpaper change and not a second image per surface.
 *
 * @property panel the picture a bounded frosted surface samples.
 * @property film the picture the full-screen frost samples. Defaults to [panel] for a caller that draws no full-screen
 *   frost at all — a settings preview of one panel, which has only one strength to show and no film to get wrong.
 * @property tintColor the wallpaper's representative color, which every wash is blended toward — see
 *   [wallpaperTone]. `Color.Unspecified` when it could not be read, which makes the washes plain white and black.
 * @property luminance the wallpaper's **mean relative luminance**, which is what a surface drawn on this has to
 *   contrast. Here rather than beside the images because it describes the same picture and travels to the same
 *   places: a panel asking "should my text be light?" is asking about the thing this state holds.
 */
class BackdropState(
    val panel: BackdropImage,
    val film: BackdropImage = panel,
    val tintColor: Color = Color.Unspecified,
    val luminance: Float = 0f,
) {

    /** The picture for [role] — the one seam a caller has to get right, and the only one there is. */
    fun imageFor(role: BackdropRole): BackdropImage = when (role) {
        BackdropRole.PANEL -> panel
        BackdropRole.FILM -> film
    }
}

/**
 * [panelImage], [filmImage], [accentColor] and [windowSize] as the [BackdropState] frosted surfaces sample, or null
 * while an image or the window is missing.
 *
 * Here rather than at its first call site because there are two zones now — the launcher shell, and the settings
 * preview of one effect — and what they must not do differently is the pairing: **each picture is given the mapping
 * derived from its *own* dimensions**, which is what [BackdropImage] exists to make unavoidable. `downscaleFor`
 * reduces in proportion to the blur, so two pictures at two strengths are routinely *different sizes*, and a shared
 * mapping would draw one of them at the wrong scale. That does not fail; it renders the wallpaper very slightly
 * displaced behind the glass, which is the one artifact in this subsystem that reads as a mystery rather than as a bug.
 *
 * Both conversions want caching and neither belongs in a state holder: the `Bitmap` → `ImageBitmap` wrap, and the
 * screen→bitmap mapping, which is a closure that would otherwise be rebuilt on every recomposition and hand every
 * frosted surface a new lambda to invalidate against.
 *
 * Both images or neither: they read the same file through the same "is our wallpaper what is on screen?" gate, so a
 * half-answer is not a state the repository can produce, and treating it as one would mean inventing a picture for the
 * role that came back empty. A null [accentColor] is not a reason to return null — it only makes the washes plain
 * white and black — which is why it becomes `Color.Unspecified` rather than a second early return.
 */
@Composable
fun rememberBackdropState(
    panelImage: Bitmap?,
    accentColor: Int?,
    windowSize: IntSize,
    filmImage: Bitmap? = panelImage,
    luminance: Float = 0f,
): BackdropState? = remember(panelImage, filmImage, accentColor, windowSize, luminance) {
    if (panelImage == null || filmImage == null || windowSize.width == 0 || windowSize.height == 0) {
        null
    } else {
        BackdropState(
            panel = panelImage.asBackdropImage(windowSize),
            film = filmImage.asBackdropImage(windowSize),
            tintColor = accentColor?.let { Color(it) } ?: Color.Unspecified,
            luminance = luminance,
        )
    }
}

/** This bitmap wrapped for Compose, with the screen→bitmap mapping its own dimensions imply. */
private fun Bitmap.asBackdropImage(windowSize: IntSize): BackdropImage = BackdropImage(
    image = asImageBitmap(),
    screenToBitmap = screenToBitmapMapping(
        bitmapWidth = width,
        bitmapHeight = height,
        screenWidth = windowSize.width,
        screenHeight = windowSize.height,
    ),
)

/**
 * The backdrop every frosted surface samples, or null when there is nothing to sample.
 *
 * Null is the normal state, not an error: the launcher only has a wallpaper to sample once the user has given it one
 * (see `WallpaperRepository.loadBackdrop`), and every consumer falls back to its own flat color. Provided at the
 * **launcher shell**, which is the same zone boundary the theme is applied at and for the same reason — the settings
 * graph is a different surface with different rules. Providing it inside `HomeScreen` instead is what makes a
 * settings feature need a second provider of its own.
 */
val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

/**
 * The global effect frosted surfaces follow unless a call site overrides it.
 *
 * The unwashed variant as the default: an unprovided local means "nobody set this up", and a surface outside the
 * shell (a preview, say) should not reach for someone's stored decoration. It renders flat there anyway,
 * because [LocalBackdrop] is null too and *that* is what decides whether there is anything to sample — which is the
 * distinction `BackdropEffect` stopped carrying when a wash became a parameter rather than a variant.
 */
val LocalBackdropEffect = staticCompositionLocalOf<BackdropEffect> {
    BackdropEffect.Blur(tint = BackdropTint.NONE)
}

/**
 * Whether **something already frosted is directly below** the content composed here.
 *
 * **Frost does not stack**, and that is the whole rule. Below an already-blurred thing everything is already blurred,
 * so sampling the wallpaper again is not glass over what the user is looking at: it is a hole cut through to a
 * *sharper* picture than its surroundings, and at a panel blur of zero that hole is the raw photograph. The same is
 * true of an icon plate, which is a silhouette of that sharper picture floating on a blurred sheet.
 *
 * **Two kinds of thing set it, and the name is deliberately neither of them.** The full-screen film does
 * ([OnFilm] — `AppsScreen`, `AppCollectionOverlay`, the bottom sheets), and so does a frosted *panel*
 * ([OnPanel] — a container tile, whose icons would otherwise plate themselves against the tile they sit on). It was
 * called `LocalOverFilm` while the film was the only one, which is exactly how the container's plates went on
 * stacking blur unnoticed: the rule was general and the name was not.
 *
 * The menu sets it a third way, through `MenuRequest.overFrost`, and is the reason this cannot be read from the tree
 * alone: it is composed at the shell, as a sibling *above* every surface, so its own position says nothing about what
 * it is anchored to.
 *
 * False everywhere else, which is HOME's own grid and every settings preview: content sitting directly on the
 * wallpaper, where frosting is exactly the effect that was asked for.
 */
val LocalOverFrost = staticCompositionLocalOf { false }

/**
 * Draws the blurred wallpaper behind this node's content, clipped to [shape] — the frosted-surface modifier.
 *
 * Sampling is by *position*: the node reports where it is on screen, and the matching crop of [LocalBackdrop]'s image
 * is drawn into it. So a surface that moves (a pager swipe, a drag) slides over the wallpaper rather than carrying a
 * fixed patch of it, which is what makes the effect read as glass instead of as a texture.
 *
 * **Fills flat with [scrimColor] instead whenever sampling would be wrong**, which is two cases reaching one paint.
 * *Nothing to sample*: no backdrop provided, the state a device is in until the launcher is given a wallpaper — which
 * is why the parameter is not optional, since only the caller knows what color its surface has to stay readable
 * against. And *already blurred underneath*: [LocalOverFrost] says a full-screen frost sits directly below this node,
 * and a second sample over one is not glass on what the user is looking at but a **sharper hole** through it — at a
 * panel blur of zero, the raw photograph. So a frosted thing appearing over a frosted full-screen thing goes solid,
 * and it does so here rather than at each call site, because the call site that forgets is invisible.
 *
 * A caller that wants the surface *gone* over the film rather than solid — the icon plate, which is a silhouette with
 * nothing on a film to be a silhouette of — checks [LocalOverFrost] itself and does not draw at all. That is the one
 * escape, and it is a different answer to the same question rather than a bypass of this one.
 *
 * @param effect overrides the global [LocalBackdropEffect] for this one surface. Null follows the global choice, which
 *   is what almost everything should do.
 * @param role which of [BackdropState]'s pictures to sample. [BackdropRole.PANEL] for anything the user's own blur
 *   slider governs, which is everything bounded; the full-screen frost overrides it, and overrides [effect] in the
 *   same breath, because the two have to name the same strength — see [SurfaceBackdropLayer].
 * @param tone overrides the wash's base color, which is otherwise struck from the theme here ([wallpaperTone]).
 *   **The full-screen film has to pass one**, and the reason is that the tone is 70% `surfaceVariant`: two films
 *   painted under two themes are two different colors for one material, which is exactly what per-surface theming
 *   introduced. `Color.Unspecified` keeps the local reading, which is what a panel wants.
 * @param refracts whether this surface can be a **lens**. False for one whose edges are the screen's: liquid glass
 *   bends light in a band at its rim, and across a whole screen that band falls under the system bars — so a
 *   full-screen surface renders it as its blur plus its saturation boost instead, which is the part of the effect
 *   that survives at that size. Every other effect ignores this.
 */
fun Modifier.wallpaperBackdrop(
    shape: Shape = RectangleShape,
    effect: BackdropEffect? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.3f),
    refracts: Boolean = true,
    role: BackdropRole = BackdropRole.PANEL,
    tone: Color = Color.Unspecified,
): Modifier = composed {
    // A thin `composed` layer that only reads the (rarely-changing) locals and hands them to the node through the
    // element. That is what makes an effect or backdrop change re-fire `update()` → `invalidateDraw()`: a reused node
    // would not otherwise redraw when a *static* local changes. It costs nothing per frame — this recomposes only when
    // one of its reads changes, and the drawing below allocates nothing. It is also the only place the wallpaper tone
    // can be resolved, since that reads MaterialTheme and a draw node cannot.
    this then BackdropElement(
        shape = shape,
        effect = effect ?: LocalBackdropEffect.current,
        // **A film underneath means there is nothing this node may sample**, which is the same thing to the drawing
        // below as a device with no wallpaper — so it is expressed as the picture being withheld rather than as a
        // flag the node has to weigh. One place, above every call site, because the call site that forgets is the
        // invisible one.
        backdrop = LocalBackdrop.current?.takeIf { !LocalOverFrost.current },
        view = LocalView.current,
        scrimColor = scrimColor,
        // Unspecified means "work it out from the theme here", which is right for a panel: a panel is drawn on
        // HOME, under the one theme the wallpaper chose. A **film** cannot do that — see [tone].
        wallpaperTone = if (tone.isSpecified) tone else wallpaperTone(),
        refracts = refracts,
        role = role,
    )
}

/**
 * Draws the **full-screen film's own material** behind this node, clipped to [shape] — the launcher's one frost recipe.
 *
 * Three arguments to [wallpaperBackdrop] that have to agree, written in one place because getting them apart is
 * invisible: the stored effect's *variant* at fixed parameters ([BackdropEffect.fullScreenFilm]), the picture blurred
 * at exactly that strength ([BackdropRole.FILM]), and no rim. Sampling the film's picture while naming the panels'
 * effect draws a wash tuned for a lighter blur over a heavier one; naming the film's effect while sampling the panels'
 * picture is worse, since at a slider of zero that picture is the sharp wallpaper.
 *
 * **Three consumers, which is why this exists rather than being spelled out three times.** [SurfaceBackdropLayer] is
 * the screen-sized sheet a surface arrives over; the context menu and the launcher's bottom sheets are the same
 * material clipped to a rounded rect. They differ in [shape] and in nothing else — which is the point: a menu on HOME
 * is a piece of the same frost the APPS surface is read against, at the same strength, so the two cannot look like
 * different materials.
 *
 * **Which surfaces belong here is one test: whose edges are these?** A surface that borrows the *window's* — the
 * film, a full-width sheet on the bottom edge, a menu holding a screenful of rows — takes this. A surface with edges
 * genuinely its own, floating on the wallpaper, is a [BackdropRole.PANEL] and keeps the user's blur and the rim: a
 * container tile on the home grid is the case, and `containerPanel` is where it is drawn.
 *
 * **Everything about the film is resolved once, at the shell** — its wash and whether it is dark, together in
 * [LocalFilm] — which is what makes it one material rather than one recipe evaluated in several places. The
 * recipe alone was enough while there was a single theme; it stopped being enough the moment surfaces started theming
 * themselves against the film, because the wash is struck from the theme.
 *
 * **No rim, at any of these sizes.** A lens bends light in a band at its edge, and these edges are the screen's — the
 * band would fall under the system bars. What survives is the blur plus `BackdropEffect.saturation`, which is what
 * makes frost read as glass at any API.
 *
 * **A film over a film is solid**, which costs this nothing to guarantee: [wallpaperBackdrop] fills flat wherever
 * [LocalOverFrost] is set, so a sheet or a menu raised over the APPS surface or an open collection renders as
 * [scrimColor] rather than blurring an already-blurred picture.
 *
 * @param scrimColor what to paint when there is no wallpaper to sample — required for [wallpaperBackdrop]'s reason:
 *   only the caller knows what its own surface has to stay legible against.
 */
@Composable
fun Modifier.filmBackdrop(
    scrimColor: Color,
    shape: Shape = RectangleShape,
): Modifier = wallpaperBackdrop(
    shape = shape,
    effect = LocalBackdropEffect.current.fullScreenFilm,
    scrimColor = scrimColor,
    refracts = false,
    role = BackdropRole.FILM,
    // The shell's tone, not this call site's — see [LocalFilmTone]. Without it a film drawn inside a subtree that
    // re-themed itself (which every film-backed surface now does) would paint a different wash from the film beside
    // it, for the same effect.
    tone = filmTone(),
)

/**
 * The wallpaper's color, softened against the current surface tone — the base every wash is built from.
 *
 * **Two blends, and both matter.** The raw accent is a full-saturation color lifted out of a photograph; washing a
 * surface in it directly reads as a colored filter rather than as glass. Blending it [ACCENT_BLEND] of the way from
 * the mode's own `surfaceVariant` keeps it *mode-appropriate* — dark in dark, light in light — while still carrying
 * the hue. The surface it starts from is grayscale, so what comes out is a desaturated version of the wallpaper's
 * color rather than a second hue mixed in.
 *
 * Falls back to `surfaceVariant` when nothing could be read, which makes every wash below plain white or black.
 *
 * **[accent] is a parameter, defaulted to the backdrop's**, for one caller that has the color but not the backdrop: the
 * effects section draws the five tints as swatches, and `LocalBackdrop` is provided only around its *preview* (a control
 * has no business frosting itself). Reading the local there would have shown the Material You swatch as gray while the
 * preview beside it washed with the real hue — a chooser disagreeing with the thing it chooses.
 */
@Composable
fun wallpaperTone(accent: Color? = LocalBackdrop.current?.tintColor): Color {
    val surfaceTone = MaterialTheme.colorScheme.surfaceVariant
    return if (accent != null && accent.isSpecified) lerp(surfaceTone, accent, ACCENT_BLEND) else surfaceTone
}

/**
 * The flat wash a given [effect] puts over its blurred crop, for a layer that has to match a frosted surface without
 * reproducing the blur.
 */
@Composable
fun backdropTint(
    effect: BackdropEffect = LocalBackdropEffect.current,
    tone: Color = wallpaperTone(),
): Color = tintOf(effect, tone)

/**
 * The wash for [effect], over a backdrop whose wallpaper color is [tone].
 *
 * **All three washes carry the wallpaper's hue, and it was worth arguing about.** A plain white or black film over a
 * blurred photograph reads as dirty — the wash fights the colors under it instead of sitting in them — so white and
 * black are nudged [LIGHT_DARK_TINT_HUE] of the way toward the wallpaper tone, and the wallpaper wash uses that tone
 * outright. The design system's monochrome rule is about *chrome*, and this is the deliberate
 * exception: an effect the user picks, whose whole subject is the wallpaper.
 */
private fun tintOf(effect: BackdropEffect, tone: Color): Color = when (effect) {
    is BackdropEffect.Blur -> effect.wash(tone)
    // No wash, and for a different reason from a tint of `NONE`: glass tints nothing, it *refracts* and it lifts
    // saturation (`BackdropEffect.saturation`). A film of color over it would be the one thing that stops it
    // reading as glass.
    is BackdropEffect.LiquidGlass -> Color.Transparent
}

/**
 * The wash this blur paints — its tint's color at its amount, or **nothing at all** where the tint is
 * [BackdropTint.NONE].
 *
 * **That branch is a bug fix, not a shortcut.** `Color.Transparent` is transparent *black*, so
 * `.copy(alpha = tintAmount)` on it does not stay transparent: it resurrects the black at whatever amount the last tint
 * happened to be set to. "None" therefore painted a 30% black film — indistinguishable from Dark, and taking its
 * strength from a control the section deliberately hides while None is selected. It is the same trap `BitmapBlur`
 * documents one layer down, where a transparent pixel is almost always transparent black and averaging its channels
 * drags black into every edge.
 *
 * So the tint is asked, not the color: a wash of none has no alpha to apply, and none is applied. The amount is still
 * *kept* in the model meanwhile — same as `customTintArgb` — so choosing a color again returns to the wash the user had.
 */
internal fun BackdropEffect.Blur.wash(tone: Color): Color =
    if (tint == BackdropTint.NONE) Color.Transparent else tint.washColor(tone, customTintArgb).copy(alpha = tintAmount)

/**
 * **What color a [BackdropTint] actually is**, opaque — the alpha is [BackdropEffect.Blur.tintAmount]'s and applied by
 * the caller.
 *
 * **Here rather than in the model**, for the reason `DeviceConfiguration` is split in two: a tint names a *choice*,
 * where turning one into a color needs things the model cannot see — the mode's own `surfaceVariant` (through [tone])
 * and the wallpaper's accent. So `core:model` carries the enum and this carries what it looks like.
 *
 * **Public because the effects section draws these as swatches**, and a swatch that resolved its color separately from
 * the renderer is how a chooser comes to advertise a wash the surface does not paint. One function, two callers, no
 * chance of drift.
 *
 * All three of the neutral washes carry the wallpaper's hue at [LIGHT_DARK_TINT_HUE] — see [tintOf] for why that is a
 * deliberate exception to the monochrome rule rather than a leak of one.
 */
fun BackdropTint.washColor(tone: Color, customArgb: Int): Color = when (this) {
    // Not a color at all, which is the honest answer: `NONE` means the blur is the whole effect.
    BackdropTint.NONE -> Color.Transparent
    BackdropTint.LIGHT -> lerp(Color.White, tone, LIGHT_DARK_TINT_HUE)
    BackdropTint.DARK -> lerp(Color.Black, tone, LIGHT_DARK_TINT_HUE)
    BackdropTint.WALLPAPER -> tone
    BackdropTint.CUSTOM -> Color(customArgb)
}

/**
 * The color filter a sampled crop is drawn through, or null when the effect leaves color alone.
 *
 * Only liquid glass raises saturation, which is what gives a full-screen sheet of it a look of its own once the rim
 * has nowhere to be — see [BackdropEffect.saturation]. A `ColorMatrix` rather than a shader, so it works on every API
 * and is free on the ones that have neither.
 */
private fun saturationFilterOf(effect: BackdropEffect): ColorFilter? {
    val saturation = effect.saturation
    if (saturation == 1f) return null
    return ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
}

/**
 * Maps a screen-space rectangle onto the sub-rectangle of a bitmap that covers it, center-cropped.
 *
 * The bitmap is normally already this screen's shape (`setImage` scales it there), which makes this close to a plain
 * scale — but not always: a stored image outlives a fold, a rotation, or a different device restoring a backup, and
 * the center-crop is what keeps those from stretching. It is the same mapping the wallpaper itself is drawn with, so a
 * frosted crop lines up with the wallpaper around it.
 *
 * Returns the identity for a degenerate input rather than dividing by zero — one frame before measurement.
 */
fun screenToBitmapMapping(
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): (Rect) -> Rect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return { it }
    val scale = maxOf(screenWidth.toFloat() / bitmapWidth, screenHeight.toFloat() / bitmapHeight)
    val offsetX = (bitmapWidth * scale - screenWidth) / 2f
    val offsetY = (bitmapHeight * scale - screenHeight) / 2f
    return { r ->
        Rect(
            left = (r.left + offsetX) / scale,
            top = (r.top + offsetY) / scale,
            right = (r.right + offsetX) / scale,
            bottom = (r.bottom + offsetY) / scale,
        )
    }
}

private data class BackdropElement(
    val shape: Shape,
    val effect: BackdropEffect,
    val backdrop: BackdropState?,
    val view: View,
    val scrimColor: Color,
    val wallpaperTone: Color,
    val refracts: Boolean,
    val role: BackdropRole,
) : ModifierNodeElement<BackdropNode>() {

    override fun create() = BackdropNode(shape, effect, backdrop, view, scrimColor, wallpaperTone, refracts, role)

    override fun update(node: BackdropNode) =
        node.update(shape, effect, backdrop, view, scrimColor, wallpaperTone, refracts, role)

    override fun InspectorInfo.inspectableProperties() {
        name = "wallpaperBackdrop"
        properties["shape"] = shape
        properties["effect"] = effect
        properties["scrimColor"] = scrimColor
        properties["role"] = role
    }
}

/**
 * A `Modifier.Node` rather than a `drawBehind`, because a frosted surface is a thing that **moves**.
 *
 * The outline and its clip path are cached against size and shape, so a position-only change — a pager swipe, a drag,
 * an animating card — redraws with a new crop rectangle and rebuilds nothing. `drawBehind` would recreate the outline
 * and the `Path` every frame of every one of those.
 */
private class BackdropNode(
    private var shape: Shape,
    private var effect: BackdropEffect,
    private var backdrop: BackdropState?,
    private var view: View,
    private var scrimColor: Color,
    private var wallpaperTone: Color,
    private var refracts: Boolean,
    private var role: BackdropRole,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {

    private var topLeft = Offset.Zero
    private val screenLoc = IntArray(2)

    private val outlinePath = Path()
    private var cachedOutline: Outline? = null
    private var cachedSize: Size = Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null

    /**
     * Built on first use and kept for the node's life — it holds a compiled shader and a bound bitmap, neither of
     * which should be rebuilt per frame. Null until the effect is actually liquid glass, so a device that never
     * selects it never compiles AGSL.
     */
    private var liquidGlass: LiquidGlass? = null

    fun update(
        shape: Shape,
        effect: BackdropEffect,
        backdrop: BackdropState?,
        view: View,
        scrimColor: Color,
        wallpaperTone: Color,
        refracts: Boolean,
        role: BackdropRole,
    ) {
        if (shape != this.shape) {
            this.shape = shape
            cachedOutline = null
        }
        this.effect = effect
        this.backdrop = backdrop
        this.view = view
        this.scrimColor = scrimColor
        this.wallpaperTone = wallpaperTone
        this.refracts = refracts
        this.role = role
        invalidateDraw()
    }

    /**
     * Resolves the node's absolute **screen** position: the host view's location on screen plus the node's position in
     * its window.
     *
     * `positionInWindow()` alone is window-relative, which is wrong inside a `Popup` — a separate window — and would
     * sample the wrong region of the wallpaper. `boundsInWindow()` is avoided for a different reason: it clips to the
     * visible area, so a partly off-screen surface would report a smaller box than `size` and the crop would stretch.
     */
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        view.getLocationOnScreen(screenLoc)
        val p = coordinates.positionInWindow()
        val next = Offset(screenLoc[0] + p.x, screenLoc[1] + p.y)
        if (next != topLeft) {
            topLeft = next
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        // The picture *and* the mapping in one read, which is what `BackdropImage` exists to make unavoidable: a crop
        // resolved against one bitmap and drawn from another is a misalignment nobody would look for in a lambda.
        val picture = backdrop?.imageFor(role)
        val outline = outlineFor(size, layoutDirection, this)
        // No picture *for this node* — which is either no backdrop on the device at all or a film already under it,
        // one question by the time it arrives here. An effect cannot say it: every variant blurs, and a tint of NONE
        // means no wash rather than no picture. `wallpaperBackdrop` above is where the two become one.
        if (picture == null || size.width <= 0f || size.height <= 0f) {
            drawOutline(outline, color = scrimColor)
            drawContent()
            return
        }
        val window = Rect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height)
        val src = picture.screenToBitmap(window)
        // A lens only where there is a rim to bend light at — see `refracts`. Without one, glass falls through to the
        // crop below, which draws it through its saturation boost; that is the half of the effect that survives at
        // full screen, and the whole of it below API 33.
        val glassEffect = (effect as? BackdropEffect.LiquidGlass)?.takeIf { refracts }
        if (glassEffect != null && liquidGlassSupported) {
            drawGlass(picture.image, src, glassEffect, outline)
        } else {
            // Every other effect — and liquid glass with no rim, or below API 33.
            drawBlurredCrop(picture.image, src)
        }
        drawContent()
    }

    /**
     * Fills the shape with the refracting lens instead of a flat crop.
     *
     * The corner radius comes from the **outline** rather than from the shape, because that is where it is already
     * resolved to px; a shape that is not a rounded rect reports zero, which the SDF reads as a plain rectangle — the
     * correct degradation rather than a special case.
     *
     * Both px conversions happen here and not in the model: the parameters are `0f..1f` preferences precisely so a
     * slider never has to speak in pixels, and the ceilings they map onto are a drawing decision. The depth is capped
     * at half the smaller side, since a band deeper than that would have the two rims overlap in the middle.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.drawGlass(
        img: ImageBitmap,
        src: Rect,
        effect: BackdropEffect.LiquidGlass,
        outline: Outline,
    ) {
        val cornerPx = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val refractionHeight = (effect.depth * GLASS_MAX_DEPTH_DP.dp.toPx())
            .coerceAtMost(minOf(size.width, size.height) * 0.5f)
        val refractionAmount = effect.refraction * GLASS_MAX_REFRACTION_DP.dp.toPx()
        val glass = liquidGlass ?: LiquidGlass().also { liquidGlass = it }
        drawPath(
            outlinePath,
            glass.brushFor(
                image = img,
                size = size,
                src = src,
                cornerRadiusPx = cornerPx,
                refractionHeightPx = refractionHeight,
                refractionAmountPx = refractionAmount,
                dispersion = effect.dispersion,
                vibrancy = effect.vibrancy,
                sheen = effect.sheen,
            ),
        )
    }

    /**
     * Draws the piece of [img] that sits behind this node, then the effect's wash over it.
     *
     * **The sample rectangle is clamped to the bitmap and the destination inset by the same amount**, so the visible
     * part keeps a 1∶1 scale instead of stretching when the surface runs past the wallpaper's edge — which happens
     * routinely, since a surface can be dragged beyond the screen and the mapping is happy to name a rectangle outside
     * the image.
     */
    private fun ContentDrawScope.drawBlurredCrop(img: ImageBitmap, src: Rect) {
        val scaleX = size.width / (src.right - src.left).coerceAtLeast(1f)
        val scaleY = size.height / (src.bottom - src.top).coerceAtLeast(1f)
        val cl = src.left.coerceIn(0f, img.width.toFloat())
        val ct = src.top.coerceIn(0f, img.height.toFloat())
        val cr = src.right.coerceIn(cl, img.width.toFloat())
        val cb = src.bottom.coerceIn(ct, img.height.toFloat())
        val dstW = ((cr - cl) * scaleX).roundToInt()
        val dstH = ((cb - ct) * scaleY).roundToInt()
        clipPath(outlinePath) {
            if (dstW > 0 && dstH > 0) {
                drawImage(
                    image = img,
                    srcOffset = IntOffset(cl.roundToInt(), ct.roundToInt()),
                    srcSize = IntSize(
                        (cr - cl).roundToInt().coerceAtLeast(1),
                        (cb - ct).roundToInt().coerceAtLeast(1),
                    ),
                    dstOffset = IntOffset(
                        ((cl - src.left) * scaleX).roundToInt(),
                        ((ct - src.top) * scaleY).roundToInt(),
                    ),
                    dstSize = IntSize(dstW, dstH),
                    // Saturation, where the effect asks for it. On the *image* rather than as a second full-surface
                    // pass, so it costs nothing when it is the identity — which it is for every effect but glass.
                    colorFilter = saturationFilterOf(effect),
                )
            }
            drawRect(color = tintOf(effect, wallpaperTone))
        }
    }

    /**
     * The outline and clip path for [size], rebuilt only when size, shape or layout direction change.
     *
     * Both are in **local** coordinates, which is what lets them survive a position change untouched — the whole point
     * of this being a node.
     */
    private fun outlineFor(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cached = cachedOutline
        if (cached != null && cachedSize == size && cachedLayoutDirection == layoutDirection) return cached
        val outline = shape.createOutline(size, layoutDirection, density)
        cachedOutline = outline
        cachedSize = size
        cachedLayoutDirection = layoutDirection
        outlinePath.rewind()
        outlinePath.addOutline(outline)
        return outline
    }
}

/**
 * How far the wallpaper's accent is blended in from the mode's surface tone.
 *
 * Middle-low on purpose: enough that the hue is unmistakable, little enough that the result stays a *surface* rather
 * than a color filter, and mode-appropriate because it starts from `surfaceVariant`.
 */
private const val ACCENT_BLEND = 0.3f

/**
 * How far the light and dark washes are nudged from pure white/black toward the wallpaper tone.
 *
 * Kept low so "light blur" and "dark blur" stay clearly light and dark while losing the dirty look a neutral film has
 * over a colored photograph. Material You is the same idea with the dial at 1.
 */
private const val LIGHT_DARK_TINT_HUE = 0.35f

/** Ceilings the liquid-glass `0f..1f` parameters map onto at 1.0, in dp. L1's, unchanged. */
private const val GLASS_MAX_DEPTH_DP = 56f
private const val GLASS_MAX_REFRACTION_DP = 60f
