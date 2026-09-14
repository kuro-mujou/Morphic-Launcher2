package inkspire.morphic.core.designsystem.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

/**
 * Launches [intent], or does nothing if no activity will take it.
 *
 * For a request checked for a receiver before the control that launches it was offered: this covers the gap between
 * that check and the tap, which a package disabled in between is enough to open. A control must not be able to crash
 * the launcher over something it offered. Any other failure still throws.
 */
fun ActivityResultLauncher<Intent>.launchSafely(intent: Intent) {
    runCatching { launch(intent) }.onFailure { if (it !is ActivityNotFoundException) throw it }
}
