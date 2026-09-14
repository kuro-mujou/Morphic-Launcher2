package inkspire.morphic.feature.settings.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.activity.launchSafely
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.data.settings.SetupStep
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.meta

/**
 * **The setup hub**: one panel above the settings index listing what the launcher has not been told yet, each row the
 * way to tell it. It is drawn only while it has a row, so an answered question stops taking the list's most valuable
 * space.
 *
 * **Each row goes where its step is done**, rather than doing it here: the wallpaper and icon rows open their sections,
 * the default-launcher row the system's chooser, and the widget row HOME — widgets are added from HOME's menu, and
 * settings has no picker of its own to open.
 *
 * **Every row but the default launcher can be put away** with its dismiss button; that one stays until the launcher is
 * the home app, since until then the home button does not open it.
 *
 * HOME's menu offers "Finish setup" while this panel has a row, and brings the user here — the one place the hub lives.
 */
@Composable
internal fun SetupHub(
    state: SetupHubState,
    homeLayout: HomeLayout,
    onOpenSection: (SettingsSection) -> Unit,
    onDismiss: (SetupStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    // **Started for a result, and the result is never read** — the role chooser identifies its asker only through an
    // activity started for one; launched plainly it finishes before drawing. See `DefaultLauncherRole.requestIntent`.
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    MorphicGroupPanel(modifier = modifier) {
        state.steps.forEach { step ->
            val dismiss = if (step.dismissible) ({ onDismiss(step) }) else null
            when (step) {
                SetupStep.DEFAULT_LAUNCHER -> state.defaultLauncherRequest?.let { request ->
                    SetupRow(
                        icon = Icons.Outlined.Home,
                        title = "Set as default launcher",
                        supporting = "Press the home button and this launcher opens.",
                        onClick = { roleRequest.launchSafely(request) },
                        onDismiss = dismiss,
                    )
                }

                SetupStep.WALLPAPER -> SetupRow(
                    icon = SettingsSection.WALLPAPER.meta(homeLayout).icon,
                    title = "Choose a wallpaper",
                    supporting = "Pick a picture, or make one in the studio.",
                    onClick = { onOpenSection(SettingsSection.WALLPAPER) },
                    onDismiss = dismiss,
                )

                SetupStep.ICON_STYLE -> SetupRow(
                    icon = SettingsSection.ICONS.meta(homeLayout).icon,
                    title = "Choose an icon style",
                    supporting = "Give every app icon one look.",
                    onClick = { onOpenSection(SettingsSection.ICONS) },
                    onDismiss = dismiss,
                )

                SetupStep.FIRST_WIDGET -> SetupRow(
                    icon = Icons.Outlined.Widgets,
                    title = "Add a widget",
                    supporting = "Long-press the home screen, then choose Widgets.",
                    onClick = { navigator.goHome() },
                    onDismiss = dismiss,
                )
            }
        }
    }
}
