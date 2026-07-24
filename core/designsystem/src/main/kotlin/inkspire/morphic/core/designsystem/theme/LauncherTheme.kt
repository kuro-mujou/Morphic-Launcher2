package inkspire.morphic.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * The app theme. It layers three things:
 *  - the M3 **Expressive motion engine** (`motionScheme = MotionScheme.expressive()`), so animations use
 *    expressive spring choreography — we keep M3's motion;
 *  - a **monochrome M3 [androidx.compose.material3.ColorScheme]** bridged from [MorphicColors] (see
 *    [toM3ColorScheme]), so stock Material components (Button, Slider, Switch, dialogs…) render greyscale
 *    while keeping that motion — this is what lets us build components *on* M3 and only restyle colour;
 *  - the same [MorphicColors] via [MorphicTheme] / [LocalMorphicColors], for our bespoke components (2D pad,
 *    segmented control, text-field ring) that read named roles directly.
 *
 * Both colour paths come from one palette + one `darkTheme` input — settings feeds the system setting, the
 * launcher will feed a wallpaper-brightness signal.
 */
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val morphicColors = if (darkTheme) MorphicColors.Dark else MorphicColors.Light
    MaterialTheme(
        colorScheme = morphicColors.toM3ColorScheme(darkTheme),
        motionScheme = MotionScheme.expressive(),
    ) {
        MorphicTheme(darkTheme = darkTheme, content = content)
    }
}
