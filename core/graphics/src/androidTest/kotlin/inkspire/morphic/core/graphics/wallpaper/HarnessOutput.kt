package inkspire.morphic.core.graphics.wallpaper

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore

/**
 * Where the render harnesses put a PNG for a human to look at — `Pictures/genharness` on the device, pulled with
 * `adb pull /sdcard/Pictures/genharness`.
 *
 * **Through the MediaStore rather than the app's own files dir.** An app's scoped external directory
 * (`Android/data/<pkg>/files`) is invisible to `adb shell` on modern Android, so a file dropped there cannot be
 * pulled; the shared media collection can.
 *
 * **Clear the folder before every run — this cannot.** On an emulator these files land with a *null*
 * `owner_package_name`, and MediaStore then silently refuses the instrumentation's own `delete` on them (bulk
 * selection *and* per-item URI alike — both return without removing the file). So a re-run cannot overwrite:
 * `insert` finds the old file still on disk and appends " (1)", "(2)", … and a pull of the plain name reads a
 * **stale** render. This has already masked a fixed generator as unchanged once, during W5 — the render was right and
 * the pulled file was old. Only `adb shell` has the filesystem access to clear them:
 *
 * ```
 * adb shell rm -rf /sdcard/Pictures/genharness
 * gradle :core:graphics:connectedDebugAndroidTest
 * adb pull /sdcard/Pictures/genharness
 * ```
 *
 * On Git Bash for Windows both `adb` calls need `MSYS_NO_PATHCONV=1`, or `/sdcard/...` is rewritten to a Windows
 * path and the `rm` silently clears nothing — which lands you back on the stale files above by a second route.
 *
 * A plain insert, with no attempt to overwrite: an in-app delete would only be a no-op dressed up as a safeguard.
 *
 * **The write is marked pending until it is finished, and without that a random frame of a run comes out
 * truncated.** MediaStore publishes a row the moment it is inserted, so a reader — `adb pull` goes through the same
 * provider — can be handed a file that is still being compressed, and what lands on the host is a PNG cut off
 * partway. It hit a different frame on each of two runs and left the rest of the sweep perfect, which is what makes
 * it worth a comment: an eleven-frame sweep with one short file reads as "that frame rendered wrong", and the frame
 * rendered fine.
 */
internal fun saveHarnessPng(resolver: ContentResolver, name: String, bitmap: Bitmap) {
    val pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val uri = resolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/genharness")
            if (pending) put(MediaStore.Images.Media.IS_PENDING, 1)
        },
    )!!
    resolver.openOutputStream(uri)!!.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
    if (pending) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
