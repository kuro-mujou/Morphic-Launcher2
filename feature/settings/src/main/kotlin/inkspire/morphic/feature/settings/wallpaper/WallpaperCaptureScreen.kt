package inkspire.morphic.feature.settings.wallpaper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Where the capture flow is: explaining, waiting for a screenshot, then importing the one that arrives.
 *
 * Three states rather than a spinner, because the middle one is the whole feature — the user has to *do* something,
 * and the screen has to get out of the way while they do it. L1's `CapturePhase` named the same three.
 */
private enum class CapturePhase {
    /** Explaining what is about to happen, before anything is hidden or asked for. */
    Guide,

    /** The launcher is out of the way and a screenshot is expected. Nothing is drawn at all — that is the point. */
    Waiting,

    /** One arrived and is being imported. */
    Importing,
}

/**
 * **Capturing the wallpaper**: clear the screen to the wallpaper alone, wait for the user to take a screenshot, and
 * keep the one that appears.
 *
 * The port of L1's `WallpaperCaptureScreen`, and the flow is L1's exactly because there is no other: **no API takes a
 * screenshot on an app's behalf**. So the launcher hides itself, asks, and watches `MediaStore` for what shows up —
 * with the watching moved into `data:wallpaper` (a `ContentObserver` and a media query are system reads, and a
 * composable is the wrong place to hold either), leaving this screen the three states and the window flags.
 *
 * **A capture is a picture of the wallpaper, not a wallpaper** — the stored image is marked
 * `WallpaperSource.CAPTURED` and the repository declines to apply it. It exists because a **live** wallpaper cannot be
 * read as a bitmap any other way, which is what the frosted backdrop and the dominant-colour signal will need (S5f).
 * That its only consumer is not built yet is deliberate and recorded in the plan.
 *
 * **The window already shows the wallpaper**, so this screen only has to get out of its way: the launcher's theme
 * carries `windowShowWallpaper` over a transparent background, which is what a launcher's window is. This screen
 * briefly did that itself with `FLAG_SHOW_WALLPAPER` at runtime, as the smallest change that made a capture show what
 * it claimed to; the theme replaced it, and hiding the system bars is all that is left.
 *
 * @param onDone leaves the screen — Cancel, a refused permission, and a finished import all mean the same thing.
 */
@Composable
fun WallpaperCaptureScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WallpaperViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val windowSize = LocalWindowInfo.current.containerSize

    var phase by remember { mutableStateOf(CapturePhase.Guide) }

    // The permission is what lets the *watch* see anything; without it the query returns nothing and the screen would
    // wait forever. Refusing is a cancel rather than an error, since there is nothing else to offer.
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        @Suppress("DEPRECATION")
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) phase = CapturePhase.Waiting else onDone()
    }

    // Out of the way: the system bars go, and nothing else is drawn (see the `Waiting` branch). Restored by
    // `onDispose`, so leaving this screen — however it is left — gives the launcher its chrome back.
    DisposableEffect(phase) {
        val bars = context.activity?.window?.let { WindowInsetsControllerCompat(it, view) }
        if (phase != CapturePhase.Guide) {
            bars?.apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // The watch runs only while waiting, and takes the **first** image to arrive. Collecting it as an effect keyed on
    // the phase is what unregisters the observer the moment one does — there is no second screenshot to wait for.
    LaunchedEffect(phase) {
        if (phase != CapturePhase.Waiting) return@LaunchedEffect
        val shot = viewModel.awaitCapture() ?: return@LaunchedEffect
        phase = CapturePhase.Importing
        viewModel.capture(shot, windowSize.width, windowSize.height, onSaved = onDone)
    }

    when (phase) {
        CapturePhase.Guide -> LauncherTheme {
            CaptureGuide(onStart = { permission.launch(mediaPermission) }, onCancel = onDone)
        }
        // **Nothing at all**, which is the one thing this state has to draw: whatever is painted here ends up in the
        // screenshot. No theme, no background, no scrim.
        CapturePhase.Waiting -> Unit
        CapturePhase.Importing -> Box(
            modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = ImportingScrimAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            if (state.busy) CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * What is about to happen, before any of it does.
 *
 * A dialog rather than a screen of its own, and it is not decoration: the flow asks the user to perform a **system**
 * gesture at a moment when the app will look broken (blank, no bars). Without being told first, that reads as a crash.
 * L1 wrote the same two sentences.
 */
@Composable
private fun CaptureGuide(onStart: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Capture your wallpaper") },
        text = {
            Text(
                "Tap Start and the screen will clear to show only your wallpaper.\n\n" +
                    "Then take a screenshot — it will be detected and used automatically.",
            )
        },
        confirmButton = { TextButton(onClick = onStart) { Text("Start") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * The [Activity] hosting this composition, or null.
 *
 * Unwrapping a `ContextWrapper` chain is the documented way to it, and it is needed because the window flags this
 * screen sets are the *activity's* — a composable has no window of its own.
 */
private val Context.activity: Activity?
    get() {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

/** How much the importing overlay dims what is behind it, which is the wallpaper by then. */
private const val ImportingScrimAlpha = 0.4f
