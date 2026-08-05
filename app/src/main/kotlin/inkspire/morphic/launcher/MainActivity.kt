package inkspire.morphic.launcher

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import inkspire.morphic.core.icon.compose.LocalIconRenderManager
import inkspire.morphic.core.icon.render.IconRenderManager
import org.koin.android.ext.android.inject

/**
 * The launcher's single Activity.
 *
 * It does exactly two things: provide what it owns from DI (the icon render manager, which outlives any one screen
 * because its bitmap cache does), and host the navigation graph. Everything else — the back stack, the theme, the
 * surfaces — belongs to [LauncherNavHost] and below.
 *
 * That split is deliberate and is the fix for L1, whose equivalent grew to 204 lines of `setContent`: nav wiring,
 * wallpaper-colour loading, icon-cache invalidation, six composition locals and the `Navigator` implementation, all in
 * one lambda. An Activity is the worst place to keep any of it, because none of it can be reached from a test or a
 * preview.
 *
 * Still a normal app: the `HOME` intent category is the last thing added (P9), so this opens on the launcher surface
 * as an ordinary launched app rather than replacing the user's home screen.
 */
class MainActivity : ComponentActivity() {

    private val iconRenderManager: IconRenderManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.value.toInt(),
                Color.Transparent.value.toInt()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.value.toInt(),
                Color.Transparent.value.toInt()
            )
        )
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalIconRenderManager provides iconRenderManager) {
                LauncherNavHost()
            }
        }
    }
}
