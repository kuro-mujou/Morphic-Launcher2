package inkspire.morphic.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.menu.LauncherMenuHost
import org.koin.androidx.compose.koinViewModel

/**
 * Gives HOME's menu its "Finish setup" row while the setup hub has anything in it, and takes it away when it empties.
 *
 * **The row goes to settings**, where the hub is the first thing in the list — one hub in one place, reached from HOME,
 * rather than a second copy of it drawn inside a menu that holds rows and not panels. Draws nothing itself.
 *
 * @param onOpenSettings where the row goes: the settings index, which the hub sits at the top of.
 */
@Composable
internal fun OfferSetupOnMenu(menuHost: LauncherMenuHost, onOpenSettings: () -> Unit) {
    val viewModel = koinViewModel<SetupMenuViewModel>()
    val pending by viewModel.hasPending.collectAsStateWithLifecycle()
    // The home-role step changes in a system dialog that reports nothing back, so the answer is re-read on resume.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    SideEffect { menuHost.finishSetup = onOpenSettings.takeIf { pending } }
}
