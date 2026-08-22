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
                ProvideIconRecipes {
                    LauncherNavHost()
                }
            }
        }
    }
}
