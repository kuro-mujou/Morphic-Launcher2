package inkspire.morphic.core.designsystem.backdrop

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * The rounded-rect "liquid glass" lens, as AGSL.
 *
 * **Attribution, carried over from L1 and not to be dropped.** The refraction maths — the rounded-rect SDF and its
 * analytic gradient, the circular `circleMap` falloff confined to a rim band of `refractionHeight` with the centre
 * passing straight through, and the chromatic-aberration split — is adapted from Kyant's
 * [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0.
 *
 * `coord` is box-local px; `content` is the wallpaper bitmap in its own pixel space, so box-local → bitmap px maps via
 * `cropOrigin`/`cropScale` — the *same* alignment the blur path uses, which is what keeps the two effects sampling the
 * same place. The source it refracts is only **lightly** blurred, because a heavy blur has no structure left to bend:
 * that is why `BackdropEffect.LiquidGlass.blur` is its own parameter rather than sharing the blurs' default.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 cropOrigin;
uniform float2 cropScale;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float dispersion;
uniform float vibrancy;
uniform float sheen;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 gradSdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - r);
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, 0.0));
    } else {
        float gx = step(q.y, q.x);
        return sign(p) * float2(gx, 1.0 - gx);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 sampleAt(float2 boxCoord) {
    return content.eval(cropOrigin + boxCoord * cropScale);
}

half3 vibrant(half3 c) {
    half luma = dot(c, half3(0.2126, 0.7152, 0.0722));
    return mix(half3(luma), c, half(1.0 + vibrancy));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float sd = sdRoundRect(p, halfSize, radius);

    half4 col;
    if (-sd >= refractionHeight) {
        // Centre: pass straight through (just the blurred backdrop).
        col = sampleAt(coord);
    } else {
        sd = min(sd, 0.0);
        float t = 1.0 - (-sd / refractionHeight);     // 1 at the rim, 0 at the band's inner edge
        float d = circleMap(t) * refractionAmount;     // circular lens falloff
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 grad = gradSdRoundRect(p, halfSize, gradRadius);
        float2 refracted = coord + d * grad;

        if (dispersion <= 0.0) {
            col = sampleAt(refracted);
        } else {
            // Chromatic aberration: split R/G/B along the refraction direction, scaled toward the corners.
            float intensity = dispersion * ((p.x * p.y) / (halfSize.x * halfSize.y));
            float2 disp = d * grad * intensity;
            half4 mid = sampleAt(refracted);
            half cr = sampleAt(refracted + disp).r;
            half cb = sampleAt(refracted - disp).b;
            col = half4(cr, mid.g, cb, mid.a);
        }

        // Rim sheen from a top-left light, additive.
        float2 lightDir = float2(0.7071, 0.7071);
        float hl = pow(clamp(dot(grad, lightDir), 0.0, 1.0), 1.5) * t;
        col.rgb += half3(hl * sheen);
    }
    col.rgb = vibrant(col.rgb);
    return col;
}
"""

/**
 * Holds the compiled [RuntimeShader] and its brush across frames — one per frosted surface that uses the effect.
 *
 * **Stateful on purpose.** Compiling AGSL and building a `BitmapShader` are both expensive enough that doing either
 * per frame would undo the point; a drag re-invokes [brushFor] every frame with new uniforms, and only the uniforms
 * change. The bitmap is re-bound only on **identity** change (`!==`), not equality, because it is the same object for
 * the whole life of a backdrop and comparing megabytes of pixels to discover that would be worse than rebinding.
 *
 * API 33+ — `RuntimeShader` does not exist below it, which is why the node checks before constructing this and why
 * `BackdropEffect.LiquidGlass` degrades to a plain blurred crop on older devices. That fallback is L1's too.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlass {

    private val shader = RuntimeShader(LIQUID_GLASS_AGSL)
    private val brush = ShaderBrush(shader)
    private var boundImage: ImageBitmap? = null

    /**
     * Points the shader at this box and returns the (reused) brush to fill its shape with.
     *
     * @param src the crop of [image] behind this box, in bitmap px — the same rectangle the blur path draws, which is
     *   what makes switching effects not shift the picture.
     * @param refractionHeightPx how far in from the rim the lens band reaches; the centre inside it is undistorted.
     * @param refractionAmountPx how far the rim pulls its sample. Passed **negated**: pulling inward along the outward
     *   normal is what magnifies the background, i.e. what makes it read as a lens rather than a dent.
     */
    fun brushFor(
        image: ImageBitmap,
        size: Size,
        src: Rect,
        cornerRadiusPx: Float,
        refractionHeightPx: Float,
        refractionAmountPx: Float,
        dispersion: Float,
        vibrancy: Float,
        sheen: Float,
    ): ShaderBrush {
        if (boundImage !== image) {
            val bitmapShader = BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
            shader.setInputShader("content", bitmapShader)
            boundImage = image
        }
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("cropOrigin", src.left, src.top)
        shader.setFloatUniform("cropScale", (src.right - src.left) / w, (src.bottom - src.top) / h)
        shader.setFloatUniform("radius", cornerRadiusPx)
        shader.setFloatUniform("refractionHeight", refractionHeightPx.coerceAtLeast(1f))
        shader.setFloatUniform("refractionAmount", -refractionAmountPx)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("vibrancy", vibrancy)
        shader.setFloatUniform("sheen", sheen)
        return brush
    }
}

/**
 * Whether this device can render `BackdropEffect.LiquidGlass` at all.
 *
 * Public because the **settings section** is the real caller: an effect that silently degrades is worse than one the
 * chooser does not offer, so it asks this and says why instead. The renderer checks it too, since a stored preference
 * outlives the device it was chosen on — a backup restored onto an older phone is exactly that case.
 */
val liquidGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
