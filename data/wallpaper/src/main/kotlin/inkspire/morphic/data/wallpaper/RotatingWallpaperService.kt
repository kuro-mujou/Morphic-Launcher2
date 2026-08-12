package inkspire.morphic.data.wallpaper

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import java.io.File

/**
 * **The launcher's own live wallpaper**: it draws the [RotatingImages] half that matches the screen's orientation, and
 * swaps as the device turns.
 *
 * The port of L1's `RotateWallpaperService`, and the reason a *rotating* wallpaper needs a service at all: Android has
 * no notion of "a static wallpaper per orientation". `WallpaperManager.setBitmap` takes one image, which the system then
 * crops or scales for whichever way the phone is held — so the only way to show a different picture in landscape is to
 * be the thing doing the drawing.
 *
 * **It lives in `data:wallpaper`, not in the settings feature.** L1 put it in `feature:settings`, which left its data
 * layer unable to name its own service (`applyRotateWallpaper` had to build the `ComponentName` in the UI). This module
 * owns the files it renders, so it owns the renderer; `feature:settings` only launches the system chooser at it.
 *
 * **No dependency injection, deliberately.** The wallpaper process is started by the system, lives outside the
 * launcher's own, and may outlive it — so it reads the two JPEGs straight from `filesDir` rather than standing up Koin
 * and a repository to be told the paths it already knows. L1 made the same call, in the same words. The consequence is
 * worth stating: this and [WallpaperFiles] are the contract, so a change to where the images are written is a change
 * here too.
 *
 * **It draws only when asked** — on surface creation, on becoming visible, and on a size change. A wallpaper that
 * animates costs battery for the whole time the home screen is showing, and a pair of still images has nothing to
 * animate.
 */
class RotatingWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = RotatingEngine()

    private inner class RotatingEngine : Engine() {

        /** The decoded image and the orientation it was decoded for, so a redraw at the same size decodes nothing. */
        private var bitmap: Bitmap? = null
        private var bitmapLandscape: Boolean? = null
        private var width = 0
        private var height = 0

        /** The image the system was last told about, so turning the device announces the new one exactly once. */
        private var notifiedBitmap: Bitmap? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            invalidateCache()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) return
            // The files may have changed while we were hidden — the settings section writes them without telling us,
            // which is the whole reason a redraw re-reads rather than trusting what it holds.
            invalidateCache()
            draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            invalidateCache()
        }

        private fun invalidateCache() {
            bitmap = null
            bitmapLandscape = null
        }

        /**
         * The image for the current surface, decoded at most once per orientation.
         *
         * **One bitmap in memory, not two.** L1 decoded *both* files on every reload and held them for the life of the
         * engine — two full-screen bitmaps in a process the system keeps alive behind the home screen. Only one can be
         * drawn at a time, so only one is kept; turning the device decodes the other and drops this.
         *
         * Falls back to the other orientation when the matching file is missing, which is what makes a half-configured
         * pair draw a picture rather than black — L1's `?: portrait ?: landscape`, kept.
         */
        private fun currentBitmap(landscape: Boolean): Bitmap? {
            if (bitmapLandscape == landscape && bitmap != null) return bitmap
            val dir = File(filesDir, WallpaperFiles.DIR)
            val preferred = if (landscape) WallpaperFiles.ROTATING_LANDSCAPE else WallpaperFiles.ROTATING_PORTRAIT
            val fallback = if (landscape) WallpaperFiles.ROTATING_PORTRAIT else WallpaperFiles.ROTATING_LANDSCAPE
            val decoded = decode(File(dir, preferred)) ?: decode(File(dir, fallback))
            bitmap = decoded
            bitmapLandscape = landscape
            return decoded
        }

        private fun decode(file: File): Bitmap? =
            if (!file.exists()) null else runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

        private fun draw() {
            if (width <= 0 || height <= 0) return
            val image = currentBitmap(landscape = width > height)
            val canvas = runCatching { surfaceHolder.lockCanvas() }.getOrNull() ?: return
            try {
                // Black first, so a missing pair is a black screen rather than whatever was in the buffer, and so an
                // image whose aspect does not match the surface is letterboxed against something deliberate.
                canvas.drawColor(Color.BLACK)
                if (image != null) canvas.drawBitmap(image, null, Rect(0, 0, width, height), null)
            } finally {
                runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
            }
            if (image !== notifiedBitmap) {
                notifiedBitmap = image
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
            }
        }

        /**
         * What colors this wallpaper is made of, for anything that has to sit legibly on top of it.
         *
         * **A live wallpaper is the only one the system cannot analyse for itself** — there is no bitmap to read, only
         * a surface being drawn to — so a service that does not answer this leaves every consumer of
         * `WallpaperManager.getWallpaperColors` with nothing: the status-bar icon contrast, any themed-icon palette,
         * and (the reason it is here) the launcher's own `WallpaperRepository.brightness`. Answering it means the
         * rotating pair takes the *same* path as every other wallpaper rather than needing a special case in the
         * repository that reads our files behind the system's back.
         *
         * Null until something has been drawn, which is honest rather than a gap: the system re-asks after
         * [notifyColorsChanged], and [draw] fires that the first time an image appears and again whenever the device
         * turns and the other half is decoded. L1's service published nothing and had no caller that missed it.
         */
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? {
            val image = bitmap ?: return null
            return runCatching { WallpaperColors.fromBitmap(image) }.getOrNull()
        }
    }
}
