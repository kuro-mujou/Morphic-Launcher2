package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.feature.settings.SettingsShellViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The **Icons** section: a hub, not an editor.
 *
 * L1's conclusion, and it is worth stating because the alternative is what it started with. Its icon settings were
 * the editor itself, hosted in the settings detail pane and built out of settings-list vocabulary; its own docs
 * conclude that this was the whole problem. So the pane became a place to choose *what* to edit, and the editing
 * moved to a full-screen destination — which is right for a second reason here, that a settings pane shares the
 * screen with the section list on a tablet, and a creative workspace cannot have half a screen.
 *
 * **Adaptive, as L1's dashboard was**: in portrait the two actions sit side by side above the presets; in landscape
 * they stack in a narrow column on the left with the presets filling the rest, so the short height is not spent on
 * two cards' worth of empty space.
 */
@Composable
internal fun IconsDetail(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    // The same navigation shape as `WallpaperDetail`, and for the same reason: the destination belongs to *this*
    // feature, so the module already knows it exists and there is nothing for `app` to be told. Contrast the
    // launcher shell, which takes `onOpenSettings` as an action precisely because settings is not its business.
    val editAll = { navigator.goTo(IconStudioRoute.Global()) }
    val editOne = { navigator.goTo(IconStudioRoute.App()) }

    if (currentDeviceConfiguration().isLandscape) {
        Row(modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardAction("Edit all icons", AllSubtitle, Icons.Outlined.Palette, Modifier.weight(1f), editAll)
                DashboardAction("Edit specific apps", OneSubtitle, Icons.Outlined.Apps, Modifier.weight(1f), editOne)
            }
            PresetsList(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardAction("Edit all icons", AllSubtitle, Icons.Outlined.Palette, Modifier.weight(1f), editAll)
                DashboardAction("Edit specific apps", OneSubtitle, Icons.Outlined.Apps, Modifier.weight(1f), editOne)
            }
            PresetsList(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Subtitles that say what each choice *does to the device*, not what screen it opens.
 *
 * The distinction matters here more than usual: the two lead to the same editor, and what separates them is only
 * what it will be editing — a recipe every app inherits, or one app's own. A user who picks wrong finds out after
 * changing every icon they own.
 */
private const val AllSubtitle = "One recipe every app inherits"
private const val OneSubtitle = "Give one app a look of its own"

/** One of the two primary actions: a squarish card, tappable whole. */
@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.content)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    }
}

/**
 * The saved-recipe library, and the one place a preset is *applied*.
 *
 * **Tapping one opens the studio loaded with it rather than writing it.** That is the whole design of applying:
 * a preset changes every icon on the device that inherits the default, which is not something to do from a list
 * row with no way to look first. So the row navigates, the studio shows the preset over a real app, the session
 * opens **dirty**, and Save is what commits — the same shape as an inheriting app opening dirty because saving is
 * what detaches it.
 *
 * Saving and deleting live in the studio, where a recipe exists to be saved. This is a library, not an editor.
 */
@Composable
private fun PresetsList(modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    val navigator = LocalNavigator.current
    val viewModel: SettingsShellViewModel = koinViewModel()
    val presets by viewModel.iconPresets.collectAsStateWithLifecycle()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presets", style = MaterialTheme.typography.titleSmall, color = colors.content)

        if (presets.isEmpty()) {
            // Says how to make one rather than that there are none — an empty library is the starting state, and
            // the only thing a reader needs from it is the next step.
            PresetCard(colors.surface) {
                Text(
                    text = "Save a look from the icon studio and it appears here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentMuted,
                )
            }
        }

        presets.forEach { preset ->
            PresetCard(colors.surfaceElevated, onClick = { navigator.goTo(IconStudioRoute.Global(preset.name)) }) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = colors.content)
                    Text(
                        text = "${preset.layerSet.layers.size} layers — open to apply",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.contentMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    background: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
