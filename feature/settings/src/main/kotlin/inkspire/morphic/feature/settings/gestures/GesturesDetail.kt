package inkspire.morphic.feature.settings.gestures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsValueRow
import org.koin.androidx.compose.koinViewModel

/**
 * **Gestures**: what a swipe or a double tap on HOME itself does, and whether the service some actions need is on.
 *
 * **A swipe with an action takes one finger, and the screen on that edge moves to two.** The line under the rows says
 * so, because nothing else in settings would: assigning here changes how the Screen manager's edges are reached.
 *
 * Each row opens the action picker — the destination an item's gesture uses too — rather than a picker drawn here, so
 * there is one list of apps and shortcuts with one search box. How a panel action pulls is chosen there as well, as
 * part of the action, so nothing here qualifies what a row says it does.
 *
 * **The Morphic gestures card is absent until a gesture needs the service**, the standing rule, and always last.
 *
 * **Status, never a prompt.** An action that needs the service is saved whether or not it is on; the gesture asks for it
 * as it fires — `GestureServiceDialog`, in the shell.
 *
 * @param onAssignSwipe opens the action picker for a direction. A destination `feature:home` declares, so `app` maps it.
 * @param onAssignDoubleTap the same, for a double tap on HOME's empty space.
 */
@Composable
internal fun GesturesDetail(
    onAssignSwipe: (SwipeDirection) -> Unit,
    onAssignDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<GesturesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on every return, since the gesture service is switched on in the system's settings and says nothing back.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshService()
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
    ) {
        SettingsSectionHeader("Home gesture", spaceAbove = false)
        MorphicGroupPanel {
            SwipeDirection.entries.forEach { direction ->
                SettingsValueRow(
                    label = direction.label,
                    value = state.swipes[direction] ?: "None",
                    onClick = { onAssignSwipe(direction) },
                )
            }
            SettingsValueRow(
                label = "Double tap",
                value = state.doubleTap ?: "None",
                onClick = onAssignDoubleTap,
            )
        }
        Note("A swipe with an action uses one finger. The screen on that edge then opens with two.")

        AnimatedVisibility(visible = state.needsService) {
            GestureServiceCard(on = state.serviceOn, onOpen = viewModel::openServiceSettings)
        }
    }
}

/** The gesture service: whether it is on, a tap through to where it is switched, and what it is for. */
@Composable
private fun GestureServiceCard(on: Boolean, onOpen: () -> Unit) {
    Column {
        SettingsSectionHeader("Accessibility")
        MorphicGroupPanel {
            SettingsValueRow(
                label = "Morphic gestures",
                value = if (on) "On" else "Off",
                onClick = onOpen,
            )
        }
        Note(
            if (on) {
                "Opens the system panels and turns the screen off for your gestures."
            } else {
                "Gestures that open a system panel or lock the screen need this. Until it's on, they ask for it instead."
            },
        )
    }
}

/** A line explaining the control above it, in the pane's small muted type. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalMorphicColors.current.contentMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A swipe's name as a row reads it — named for the way the finger travels, like everything a user assigns. */
private val SwipeDirection.label: String
    get() = when (this) {
        SwipeDirection.UP -> "Swipe up"
        SwipeDirection.DOWN -> "Swipe down"
        SwipeDirection.LEFT -> "Swipe left"
        SwipeDirection.RIGHT -> "Swipe right"
    }
