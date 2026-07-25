package inkspire.morphic.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import inkspire.morphic.core.icon.compose.LocalIconRenderManager
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.launcher.dev.DevRootScreen
import org.koin.android.ext.android.inject

/**
 * The launcher's single Activity. For now it hosts the dev harness ([DevRootScreen], which switches between the
 * drag playground and the pager test) under the icon-render manager provider; the real home / side surfaces
 * replace this from P4 onward.
 */
class MainActivity : ComponentActivity() {

    private val iconRenderManager: IconRenderManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalIconRenderManager provides iconRenderManager) {
                DevRootScreen()
            }
        }
    }
}
