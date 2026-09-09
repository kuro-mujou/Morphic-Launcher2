package inkspire.morphic.launcher

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import inkspire.morphic.core.icon.compose.LocalIconRenderManager
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val iconRenderManager: IconRenderManager by inject()
    private val settingsRepository: SettingsRepository by inject()

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
        applyRotationMode()
        setContent {
            CompositionLocalProvider(LocalIconRenderManager provides iconRenderManager) {
                ProvideIconRecipes {
                    LauncherNavHost()
                }
            }
        }
    }

    /**
     * Follows the stored [RotationMode], asking the platform for it.
     *
     * **Here rather than in a composable**, because `requestedOrientation` is the Activity's and a launcher's window
     * outlives any one surface: settings must obey the lock as much as HOME does, and both are inside this Activity.
     *
     * Collected for the lifetime of the Activity rather than only while started. A rotation request made while the
     * launcher is backgrounded is exactly the one that has to be in place *before* it is next shown — deferring it to
     * `STARTED` would let the launcher come back in the orientation it was locked out of and turn afterwards.
     *
     * [RotationMode.AUTO] maps to `UNSPECIFIED` rather than `USER` or `SENSOR`: it means "ask for nothing", so the
     * device's own rotation setting — including the user having auto-rotate off system-wide — decides, which is not
     * a launcher preference's to override.
     */
    private fun applyRotationMode() {
        lifecycleScope.launch {
            settingsRepository.orientationSettings
                .map { it.rotation }
                .distinctUntilChanged()
                .collect { mode ->
                    requestedOrientation = when (mode) {
                        RotationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        RotationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        RotationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
        }
    }
}
