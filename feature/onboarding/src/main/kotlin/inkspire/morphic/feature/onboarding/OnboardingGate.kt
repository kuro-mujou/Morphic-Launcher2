package inkspire.morphic.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.surface.SurfacePagerState
import org.koin.androidx.compose.koinViewModel

/**
 * Shows the first-run screen in place of [content] until setup is finished — and nothing at all until that is known.
 *
 * **A gate around navigation, not a destination inside it.** As the start destination the first-run screen would sit
 * beneath HOME on the back stack, where the home button's pop-to-start would land on it, and finishing would mean
 * rebuilding the stack under the user. Outside navigation, finishing swaps what is composed, and clearing the flag
 * brings the screen back.
 *
 * **Composing nothing while unresolved is not a blank screen**: the window is transparent over the wallpaper, which is
 * what the launcher is about to show anyway.
 *
 * @param preview the launcher drawn as a picture, driven by the pager state it is handed. A slot rather than a
 *   dependency, so this module never names the shell that `app` puts here.
 */
@Composable
fun OnboardingGate(
    preview: @Composable (SurfacePagerState) -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.gate) {
        OnboardingGateState.UNRESOLVED -> Unit
        OnboardingGateState.OPEN -> OnboardingScreen(
            state = state,
            preview = preview,
            onShown = viewModel::onScreenShown,
            onSelect = viewModel::select,
            onUse = viewModel::use,
        )

        OnboardingGateState.CLOSED -> content()
    }
}
