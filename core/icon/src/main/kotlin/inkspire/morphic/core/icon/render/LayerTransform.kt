package inkspire.morphic.core.icon.render

import android.graphics.Camera
import android.graphics.Matrix
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec

/**
 * Where something sits inside the icon's square box, in pixels — a normalized transform resolved against a concrete
 * size. Usually a layer's; [of]`(IconLayerSet)` is the finished icon's own, which is its angles and nothing else.
 *
 * **This exists to be the single interpretation of a layer's transform, because there are two renderers.** The
 * baked path composites to a bitmap for display; the editor renders the same layers live as Compose nodes so a
 * slider responds per frame. They use different graphics APIs, and if they read [IconLayerSpec.offsetX] even
 * slightly differently the result is the worst kind of bug: an icon that looks right while you are editing it and
 * wrong everywhere else, which the editor by definition cannot show you. So the arithmetic happens once, here, and
 * each path only has to apply the answer.
 *
 * The conventions this pins down, all of which are choices rather than facts:
 * - **Offsets are fractions of the box**, not pixels — so a set moved 10% left looks the same at every bake size.
 * - **Positive [translateYPx] is down**, matching both Android's canvas and Compose's.
 * - **Zoom and rotation are about the box's center**, not its origin, so an item scales in place.
 * - **[rotationDegrees] is clockwise.**
 * - **Tilt happens first**, before zoom, rotation and translation — so leaning a layer and then turning it in the
 *   plane does what it reads like, rather than the tilt being applied to an already-turned frame.
 */
data class LayerTransform(
    val zoom: Float,
    val rotationDegrees: Float,
    val translateXPx: Float,
    val translateYPx: Float,
    val tiltXDegrees: Float = 0f,
    val tiltYDegrees: Float = 0f,
) {

    /**
     * True when this transform would not move a single pixel, so a caller can skip the work of applying it.
     *
     * Worth having because it is the common case by a wide margin: every layer of an unedited icon is identity,
     * which is every icon on screen for a user who has never opened the studio.
     */
    val isIdentity: Boolean
        get() = zoom == 1f && rotationDegrees == 0f && translateXPx == 0f && translateYPx == 0f &&
            tiltXDegrees == 0f && tiltYDegrees == 0f

    /** True when the layer leans out of the plane, so the matrix carries perspective terms as well as affine ones. */
    val isTilted: Boolean get() = tiltXDegrees != 0f || tiltYDegrees != 0f

    /**
     * The same transform as an Android [Matrix].
     *
     * **Both renderers go through this now, and perspective is why.** The live path used to read the four affine
     * fields into a `graphicsLayer`, which was equivalent while the transform was affine — but `graphicsLayer`
     * expresses perspective as a `cameraDistance` whose unit is *not* [Camera]'s, and matching two camera models by
     * eye is precisely the kind of agreement this class exists to make unnecessary. Handing both paths the same
     * matrix makes the question disappear rather than answering it.
     *
     * Order mirrors the field order: tilt about the center, then scale about the center, then rotate about the
     * center, then translate. [Camera] produces its matrix about the **origin**, so the layer's center is moved
     * there and back around it — the same two-step [ShapeMask.matrixOf] uses for an ink fit.
     */
    fun toMatrix(sizePx: Int): Matrix {
        val center = sizePx / 2f
        return Matrix().apply {
            if (isTilted) {
                // `getMatrix` **overwrites**, so the tilt has to be established before anything is concatenated onto
                // it. Everything below is a `post`, which keeps that true as more is added.
                val camera = Camera()
                camera.setLocation(0f, 0f, -CameraDepth * sizePx / CameraUnitPx)
                camera.rotateX(tiltXDegrees)
                camera.rotateY(tiltYDegrees)
                camera.getMatrix(this)
                preTranslate(-center, -center)
                postTranslate(center, center)
            }
            postScale(zoom, zoom, center, center)
            postRotate(rotationDegrees, center, center)
            postTranslate(translateXPx, translateYPx)
        }
    }

    companion object {

        /**
         * The transform of something that is not moved at all.
         *
         * What anything placed *against* the composite is placed against — `LayerGradient.frameOf` asks for a
         * transform, and a null there would mean every consumer inventing this value for itself. Deliberately not
         * the same thing as [of]`(IconLayerSet)`, which is what the composite is *drawn* through: an effect anchored
         * to the finished icon sits in its flat box, since [Frame][LayerGradient.Frame] carries no perspective term.
         */
        val Identity: LayerTransform =
            LayerTransform(zoom = 1f, rotationDegrees = 0f, translateXPx = 0f, translateYPx = 0f)

        /**
         * The angles the finished icon is drawn through — the composite's whole transform, which is where it faces
         * and nothing else. See `IconLayerSet.rotation` for why zoom and offset are not the composite's to have.
         *
         * **No `sizePx`, unlike the layer overload, and that is a fact rather than an omission**: the only
         * size-dependent part of a transform is the offset, in fractions of the box, and the composite has none. The
         * matrix still needs a size, which is [toMatrix]'s parameter and the caller's to supply.
         */
        fun of(layerSet: IconLayerSet): LayerTransform = Identity.copy(
            rotationDegrees = layerSet.rotation,
            tiltXDegrees = layerSet.tiltX,
            tiltYDegrees = layerSet.tiltY,
        )

        /** Resolves [spec]'s normalized transform against a square box of [sizePx]. */
        fun of(spec: IconLayerSpec, sizePx: Int): LayerTransform = LayerTransform(
            zoom = spec.zoom,
            rotationDegrees = spec.rotation,
            translateXPx = spec.offsetX * sizePx,
            translateYPx = spec.offsetY * sizePx,
            tiltXDegrees = spec.tiltX,
            tiltYDegrees = spec.tiltY,
        )

        /**
         * How far the camera sits from the layer, as a multiple of the box — which is what decides how strongly a
         * tilt foreshortens.
         *
         * **A multiple of the box rather than a fixed distance**, for [translateXPx]'s reason one property over: a
         * recipe has to look the same baked at 96px for a list row and at 288px for a folder, and a constant pixel
         * depth would make the same tilt read as mild on one and violent on the other.
         *
         * Two and a half is chosen against what this is *for*. Android's own default for a View is about 1280dp,
         * which is twenty-odd times an icon's width and so nearly orthographic — right for a screen-sized view
         * leaning a few degrees, and invisible on a 48dp icon. Close enough to see, far enough that a 45° tilt does
         * not send a corner behind the camera.
         */
        private const val CameraDepth = 2.5f

        /** [Camera] measures its z in units of 72 pixels — its default location of -8 being -576px. */
        private const val CameraUnitPx = 72f
    }
}
