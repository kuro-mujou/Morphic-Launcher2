package inkspire.morphic.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The first-run screen. For now it offers one look, so a fresh install always ends with an edge bound to the app list.
 *
 * **Every way forward applies a look.** There is no skip beside the button: skipping would reach a home with no route
 * to the app list, the one outcome the gate exists to rule out.
 *
 * Themed from the system, as settings is, because it paints a solid background rather than sitting on the wallpaper.
 */
@Composable
internal fun OnboardingScreen(onApplyClassic: () -> Unit, modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .uiInsetsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Classic layout",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.content,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Home screen pages with a dock. Swipe up from home for all your apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            MorphicButton(onClick = onApplyClassic) {
                Text("Use this layout")
            }
        }
    }
}
