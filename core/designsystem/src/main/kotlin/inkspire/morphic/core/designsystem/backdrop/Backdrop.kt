package inkspire.morphic.core.designsystem.backdrop

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
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
import inkspire.morphic.core.model.BackdropBlurTone
import inkspire.morphic.core.model.BackdropEffect
import kotlin.math.roundToInt

/**
 * The pre-blurred wallpaper a frosted surface samples, and how to find its own piece of it.
 *
 * **A shared image plus a mapping, rather than a bitmap per surface** — which is the whole reason a frosted surface is
 * affordable. Every one of them draws a *crop* of this one image, positioned so its piece lines up with the wallpaper
 * around it; two frosted surfaces side by side therefore continue each other rather than each showing the same blur.
 *
 * @property image the wallpaper, already blurred and downscaled by `WallpaperRepository.loadBackdrop`. Low resolution
 *   on purpose: it is upscaled at draw time, and a blur has no detail left to lose.
 * @property screenToBitmap maps a rectangle in **screen** coordinates onto the matching sub-rectangle of [image] — see
 *   [screenToBitmapMapping] for why it is screen and not window coordinates.
 * @property tintColor the wallpaper's representative colour, which every wash is blended toward — see
 *   [wallpaperTone]. `Color.Unspecified` when it could not be read, which makes the washes plain white and black.
 */
class BackdropState(
    val image: ImageBitmap,
    val screenToBitmap: (Rect) -> Rect,
    val tintColor: Color = Color.Unspecified,
)

/**
 * The backdrop every frosted surface samples, or null when there is nothing to sample.
 *
 * Null is the normal state, not an error: the launcher only has a wallpaper to sample once the user has given it one
 * (see `WallpaperRepository.loadBackdrop`), and every consumer falls back to its own flat colour. Provided at the
 * **launcher shell**, which is the same zone boundary the theme is applied at and for the same reason — the settings
 * graph is a different surface with different rules. L1 provided it inside its `HomeScreen`, which is why its settings
 * feature needed a second provider of its own to get the same effect.
 */
val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

/**
 * The global effect frosted surfaces follow unless a call site overrides it.
 *
 * [BackdropEffect.None] rather than [BackdropEffect.Default] as the composition-local default: an unprovided local
 * means "nobody set this up", and a surface outside the shell (the dev harness, a preview) should render flat rather
 * than reach for a backdrop that is not there.
 */
val LocalBackdropEffect = staticCompositionLocalOf<BackdropEffect> { BackdropEffect.None }

/**
 * Draws the blurred wallpaper behind this node's content, clipped to [shape] — the frosted-surface modifier.
 *
 * Sampling is by *position*: the node reports where it is on screen, and the matching crop of [LocalBackdrop]'s image
 * is drawn into it. So a surface that moves (a pager swipe, a drag) slides over the wallpaper rather than carrying a
 * fixed patch of it, which is what makes the effect read as glass instead of as a texture.
 *
 * **Falls back to [scrimColor] whenever it cannot sample** — no backdrop provided, or an effect of
 * [BackdropEffect.None]. That is why the parameter is not optional: a frosted surface has to be *opaque enough to read
 * against* on a device where the launcher has never been given a wallpaper, and the caller is the only one who knows
 * what that colour is.
 *
 * @param effect overrides the global [LocalBackdropEffect] for this one surface. Null follows the global choice, which
 *   is what almost everything should do.
 */
fun Modifier.wallpaperBackdrop(
    shape: Shape = RectangleShape,
    effect: BackdropEffect? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.3f),
): Modifier = composed {
    // A thin `composed` layer that only reads the (rarely-changing) locals and hands them to the node through the
    // element. That is what makes an effect or backdrop change re-fire `update()` → `invalidateDraw()`: a reused node
    // would not otherwise redraw when a *static* local changes. It costs nothing per frame — this recomposes only when
    // one of its reads changes, and the drawing below allocates nothing. It is also the only place the wallpaper tone
    // can be resolved, since that reads MaterialTheme and a draw node cannot.
    this then BackdropElement(
        shape = shape,
        effect = effect ?: LocalBackdropEffect.current,
        backdrop = LocalBackdrop.current,
        view = LocalView.current,
        scrimColor = scrimColor,
        wallpaperTone = wallpaperTone(),
    )
}

/**
 * The wallpaper's colour, softened against the current surface tone — the base every wash is built from.
 *
 * **Two blends, and both matter.** The raw accent is a full-saturation colour lifted out of a photograph; washing a
 * surface in it directly reads as a coloured filter rather than as glass. Blending it [ACCENT_BLEND] of the way from
 * the mode's own `surfaceVariant` keeps it *mode-appropriate* — dark in dark, light in light — while still carrying
 * the hue. That is L1's `materialYouTone`, and in L2 the surface it starts from is greyscale, so what comes out is a
 * desaturated version of the wallpaper's colour rather than a second hue mixed in.
 *
 * Falls back to `surfaceVariant` when nothing could be read, which makes every wash below plain white or black.
 */
@Composable
private fun wallpaperTone(): Color {
    val surfaceTone = MaterialTheme.colorScheme.surfaceVariant
    val accent = LocalBackdrop.current?.tintColor
    return if (accent != null && accent.isSpecified) lerp(surfaceTone, accent, ACCENT_BLEND) else surfaceTone
}

/**
 * The flat wash a given [effect] puts over its blurred crop, for a layer that has to match a frosted surface without
 * reproducing the blur.
 */
@Composable
fun backdropTint(effect: BackdropEffect = LocalBackdropEffect.current): Color = tintOf(effect, wallpaperTone())

/**
 * The wash for [effect], over a backdrop whose wallpaper colour is [tone].
 *
 * **All three washes carry the wallpaper's hue, which is L1's design and was worth arguing about.** A plain white or
 * black film over a blurred photograph reads as dirty — the wash fights the colours under it instead of sitting in
 * them — so L1 nudges white and black [LIGHT_DARK_TINT_HUE] of the way toward the wallpaper tone, and Material You
 * uses that tone outright. The design system's monochrome rule is about *chrome*, and this is the deliberate
 * exception: an effect the user picks, whose whole subject is the wallpaper.
 */
private fun tintOf(effect: BackdropEffect, tone: Color): Color = when (effect) {
    BackdropEffect.None -> Color.Transparent
    is BackdropEffect.Blur -> when (effect.tone) {
        BackdropBlurTone.LIGHT -> lerp(Color.White, tone, LIGHT_DARK_TINT_HUE).copy(alpha = effect.tint)
        BackdropBlurTone.DARK -> lerp(Color.Black, tone, LIGHT_DARK_TINT_HUE).copy(alpha = effect.tint)
    }
    is BackdropEffect.MaterialYou -> tone.copy(alpha = effect.tint)
    // No wash, which is L1's own pre-API-33 fallback rather than a placeholder of ours: without the shader this is a
    // plain blurred crop, and that is what liquid glass degrades to on every device below 33 in L1 too. The shader
    // itself is S5f-3; listed here rather than behind an `else` so writing it is a compile error at this line.
    is BackdropEffect.LiquidGlass -> Color.Transparent
}

/**
 * Maps a screen-space rectangle onto the sub-rectangle of a bitmap that covers it, centre-cropped.
 *
 * The bitmap is normally already this screen's shape (`setImage` scales it there), which makes this close to a plain
 * scale — but not always: a stored image outlives a fold, a rotation, or a different device restoring a backup, and
 * the centre-crop is what keeps those from stretching. It is the same mapping the wallpaper itself is drawn with, so a
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
) : ModifierNodeElement<BackdropNode>() {

    override fun create() = BackdropNode(shape, effect, backdrop, view, scrimColor, wallpaperTone)

    override fun update(node: BackdropNode) =
        node.update(shape, effect, backdrop, view, scrimColor, wallpaperTone)

    override fun InspectorInfo.inspectableProperties() {
        name = "wallpaperBackdrop"
        properties["shape"] = shape
        properties["effect"] = effect
        properties["scrimColor"] = scrimColor
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
        val bd = backdrop
        val outline = outlineFor(size, layoutDirection, this)
        if (effect == BackdropEffect.None || bd == null || size.width <= 0f || size.height <= 0f) {
            drawOutline(outline, color = scrimColor)
            drawContent()
            return
        }
        val window = Rect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height)
        val src = bd.screenToBitmap(window)
        val glassEffect = effect as? BackdropEffect.LiquidGlass
        if (glassEffect != null && liquidGlassSupported) {
            drawGlass(bd.image, src, glassEffect, outline)
        } else {
            // Every other effect — and liquid glass below API 33, where L1 falls back to exactly this too.
            drawBlurredCrop(bd.image, src)
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
 * How far the wallpaper's accent is blended in from the mode's surface tone — L1's `MATERIAL_YOU_ACCENT_BLEND`.
 *
 * Middle-low on purpose: enough that the hue is unmistakable, little enough that the result stays a *surface* rather
 * than a colour filter, and mode-appropriate because it starts from `surfaceVariant`.
 */
private const val ACCENT_BLEND = 0.3f

/**
 * How far the light and dark washes are nudged from pure white/black toward the wallpaper tone — L1's
 * `LIGHT_DARK_TINT_HUE`.
 *
 * Kept low so "light blur" and "dark blur" stay clearly light and dark while losing the dirty look a neutral film has
 * over a coloured photograph. Material You is the same idea with the dial at 1.
 */
private const val LIGHT_DARK_TINT_HUE = 0.35f

/** Ceilings the liquid-glass `0f..1f` parameters map onto at 1.0, in dp. L1's, unchanged. */
private const val GLASS_MAX_DEPTH_DP = 56f
private const val GLASS_MAX_REFRACTION_DP = 60f
