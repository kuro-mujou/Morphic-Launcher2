package inkspire.morphic.feature.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.PunchThroughLayer
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.surface.SurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The first-run screen: the launcher as it will be, the looks it can take, and one button to keep the one on screen.
 *
 * **A look is chosen by looking at it.** The first look is applied as the screen is shown, and tapping another row
 * applies that one — the preview is the launcher itself, so it re-renders from the store and plays the new look's
 * crossing. Nothing is final until "Use this look".
 *
 * **No "Not now", and no skip.** With a look always applied and selected, a second button that kept the recommended
 * one would do exactly what the first does until a row is tapped — a control that changes nothing. And skipping would
 * reach a home with no route to the app list, the one outcome the gate exists to rule out.
 *
 * **Side by side when the window is wider than tall**, so the rows are never pushed under a preview that has taken the
 * height. Themed from the system, as settings is, because it paints its own background around the picture.
 *
 * @param onShown reads the looks and applies the first, as the screen is first composed.
 */
@Composable
internal fun OnboardingScreen(
    state: OnboardingState,
    preview: @Composable (SurfacePagerState) -> Unit,
    onShown: () -> Unit,
    onSelect: (String) -> Unit,
    onUse: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }
    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        PunchThroughLayer(background = LocalMorphicColors.current.background) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .uiInsetsPadding()
                    .padding(24.dp),
            ) {
                val lookName = state.looks.firstOrNull { it.id == state.selected }?.name
                if (maxWidth > maxHeight) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LauncherMiniature(
                            preview = preview,
                            edge = state.previewEdge,
                            lookName = lookName,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        LookChoices(
                            state = state,
                            onSelect = onSelect,
                            onUse = onUse,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LauncherMiniature(
                            preview = preview,
                            edge = state.previewEdge,
                            lookName = lookName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        LookChoices(state = state, onSelect = onSelect, onUse = onUse, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** The rows, and the button under them — absent until a look has been applied, since until then there is none to keep. */
@Composable
private fun LookChoices(
    state: OnboardingState,
    onSelect: (String) -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LookList(
            looks = state.looks,
            selected = state.selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.selected != null) {
            MorphicButton(onClick = onUse) {
                Text("Use this look")
            }
        }
    }
}
