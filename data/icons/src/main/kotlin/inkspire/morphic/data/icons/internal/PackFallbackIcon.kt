package inkspire.morphic.data.icons.internal

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.roundToInt

/**
 * What an `appfilter.xml` says to do with an app the pack does **not** theme.
 *
 * **This is part of the file's own spec, not a launcher's invention.** A pack author ships this art deliberately —
 * a plate to sit behind the app's own icon, a stencil to trim it with, an overlay to lay over it, and how far to
 * shrink it — so that an unthemed app still looks like it belongs to the set. Smart Launcher, Niagara and Nova all
 * apply it; we parsed `<item>` and dropped these four tags on the floor, which is why an app the pack missed came out
 * as a bare plate.
 *
 * @property backs one or more plates (`img1`, `img2`, …). Packs ship several so a screen of unthemed apps is not the
 *   same picture repeated; which one an app gets is chosen by hash of its package, so it stays put across restarts.
 * @property mask a stencil trimming the app's icon — see [composePackFallback] for the compositing mode and why it is
 *   the one thing here taken from convention rather than checked.
 * @property upon an overlay drawn over the finished icon (a gloss, a fold, a badge).
 * @property scale how much of the plate the app's own icon fills. LineX White says `0.5`, which is the small-icon-in-a
 *   -ring look in every launcher that supports this.
 */
internal class PackFallback(
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float,
) {

    /** Nothing to draw with — a pack that declared a `<scale>` and no art has no treatment to apply. */
    val isEmpty: Boolean get() = backs.isEmpty() && mask == null && upon == null

    /**
     * The same treatment with every name the pack does not actually ship dropped.
     *
     * **An authored list can name art that is not in the APK, and this one does** — LineX White declares twenty-four
     * plates and ships twelve. Unfiltered, [backFor] hashes across all of them, so about half the apps it covered
     * picked a name that resolved to nothing: no plate, and the app's icon left shrunk to half size on transparency,
     * which reads as the treatment having been skipped for some apps and not others.
     *
     * Filtering rather than skipping-and-retrying is what makes the hash still meaningful: every app lands on a plate
     * that exists, and the spread stays even. It is also exactly what `drawableNames` does for the browser, and for
     * the same reason — `getIdentifier` is a hash lookup, so checking two dozen names once per pack costs nothing.
     *
     * A pack left with nothing after this has no treatment at all, and [isEmpty] then says so: the app keeps its own
     * artwork untouched, which is the honest outcome rather than a half-size icon on an empty square.
     */
    fun retainingShipped(ships: (String) -> Boolean) = PackFallback(
        backs = backs.filter(ships),
        mask = mask?.takeIf(ships),
        upon = upon?.takeIf(ships),
        scale = scale,
    )

    /**
     * Which plate [packageName] gets. **Hashed rather than counted**, so an app keeps the same one however the list
     * around it changes, and `floorMod` rather than `abs` because `abs(Int.MIN_VALUE)` is still negative.
     */
    fun backFor(packageName: String): String? =
        backs.getOrNull(Math.floorMod(packageName.hashCode(), backs.size.coerceAtLeast(1)))
}

/**
 * The pack's treatment of an app it does not theme: [back] behind, the app's own [base] shrunk to [scale] of it,
 * trimmed by [mask], with [upon] over the top.
 *
 * **The mask is applied to the app's icon alone, never to the finished composite**, which is a decision rather than an
 * implementation detail. The plate is already the shape the pack author chose; running a stencil over it could only
 * cut into their own artwork. Trimming the thing being *fitted into* the plate is what the tag is for.
 *
 * **`DST_OUT` is the one value here taken from convention rather than checked** — the mask being opaque where the icon
 * is cut away, which is what ADW's own launcher did and what packs have been authored against since. LineX White ships
 * no `iconmask`, so there was nothing on this device to verify it with; a pack whose icons come out as holes rather
 * than shapes is this line inverted, and `DST_IN` is the whole fix.
 *
 * Sized from the art rather than from a constant: the plate is what defines the canvas, so it decides, and the app's
 * icon only matters when there is no plate. Bounded at both ends — a pack shipping 1024px plates would otherwise have
 * every unthemed icon allocate a megabyte, and a tiny one would be enlarged into a blur.
 */
internal fun composePackFallback(
    base: Drawable,
    back: Drawable?,
    mask: Drawable?,
    upon: Drawable?,
    scale: Float,
    resources: Resources,
): Drawable {
    val size = (back?.intrinsicWidth ?: base.intrinsicWidth).coerceIn(MinSizePx, MaxSizePx)
    val output = createBitmap(size, size)
    val canvas = Canvas(output)

    back?.let {
        it.setBounds(0, 0, size, size)
        it.draw(canvas)
    }

    // The app's icon on a layer of its own, so the mask below trims it without reaching the plate.
    val inner = (size * scale).roundToInt().coerceIn(1, size)
    val inset = (size - inner) / 2
    val iconLayer = createBitmap(size, size)
    val iconCanvas = Canvas(iconLayer)
    base.setBounds(inset, inset, inset + inner, inset + inner)
    base.draw(iconCanvas)

    mask?.let {
        iconCanvas.drawBitmap(it.toBitmap(size, size), 0f, 0f, MaskPaint)
    }
    canvas.drawBitmap(iconLayer, 0f, 0f, null)

    upon?.let {
        it.setBounds(0, 0, size, size)
        it.draw(canvas)
    }

    return output.toDrawable(resources)
}

/** Keeps the icon where the stencil is transparent — see [composePackFallback] on why this mode and not `DST_IN`. */
private val MaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
}

/** Small enough to be worth enlarging from, large enough that a big plate is not thrown away. */
private const val MinSizePx = 96
private const val MaxSizePx = 512
