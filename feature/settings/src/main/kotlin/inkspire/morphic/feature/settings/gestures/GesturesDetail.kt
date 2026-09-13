package inkspire.morphic.feature.settings.gestures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.ShadeStyle
import inkspire.morphic.core.model.SwipeDirection
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsValueRow
import org.koin.androidx.compose.koinViewModel

/**
 * **Gestures**: what a swipe on HOME itself does, in each direction.
 *
 * **A swipe with an action takes one finger, and the screen on that edge moves to two.** The line under the rows says
 * so, because nothing else in settings would: assigning here changes how the Screen manager's edges are reached.
 *
 * Each row opens the action picker — the destination an item's gesture uses too — rather than a picker drawn here, so
 * there is one list of apps and shortcuts with one search box.
 *
 * **The panel style is absent until a swipe opens the system panel**, the standing rule: before that it changes nothing.
 *
 * @param onAssignSwipe opens the action picker for a direction. A destination `feature:home` declares, so `app` maps it.
 */
@Composable
internal fun GesturesDetail(
    onAssignSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<GesturesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on every return, since the gesture service is switched on in the system's settings and says nothing back.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSwipeService()
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsSectionHeader("Swipe on home", spaceAbove = false)
        MorphicGroupPanel {
            SwipeDirection.entries.forEach { direction ->
                SettingsValueRow(
                    label = direction.label,
                    value = state.actions[direction] ?: "None",
                    onClick = { onAssignSwipe(direction) },
                )
            }
        }
        Note("A swipe with an action uses one finger. The screen on that edge then opens with two.")

        AnimatedVisibility(visible = state.showsShadeStyle) {
            PanelStyleSettings(
                state = state,
                onStyle = viewModel::setShadeStyle,
                onOpenSwipeService = viewModel::openSwipeServiceSettings,
            )
        }
    }
}

/**
 * The panel style, a picture of it, and — under Separate — the gesture service that makes the notification side
 * reachable on skins whose own API will not open it.
 *
 * **Under Separate the picture waits for the service.** With the service off, the split it draws is exactly what some
 * firmware will not deliver, so the note saying so stands in its place until the service is on.
 *
 * **The service row is absent under Combined**, where a replayed swipe is never used and so switching it on would
 * change nothing.
 */
@Composable
private fun PanelStyleSettings(
    state: GesturesState,
    onStyle: (ShadeStyle) -> Unit,
    onOpenSwipeService: () -> Unit,
) {
    Column {
        SettingsSectionHeader("Notification panel")
        val styles = ShadeStyle.entries
        MorphicSegmentedButtons(
            options = styles.map { style ->
                when (style) {
                    ShadeStyle.COMBINED -> "Combined"
                    ShadeStyle.SEPARATE -> "Separate"
                }
            },
            selectedIndex = styles.indexOf(state.shadeStyle),
            onSelect = { onStyle(styles[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Said right under the choice, because the choice looks like a setting for the phone's panels and is not one:
        // it only tells the launcher which arrangement the phone already has.
        Note(
            "Match your phone's own notification panel setting. Changing it here doesn't change how your phone shows " +
                "notifications.",
        )

        AnimatedVisibility(visible = state.shadeStyle == ShadeStyle.COMBINED) {
            ShadeStylePreview(ShadeStyle.COMBINED)
        }
        AnimatedVisibility(visible = state.shadeStyle == ShadeStyle.SEPARATE) {
            Column {
                MorphicGroupPanel(modifier = Modifier.padding(top = 4.dp)) {
                    SettingsValueRow(
                        label = "Morphic gestures",
                        value = if (state.swipeServiceOn) "On" else "Off",
                        onClick = onOpenSwipeService,
                    )
                }
                if (state.swipeServiceOn) {
                    ShadeStylePreview(ShadeStyle.SEPARATE)
                } else {
                    Note(
                        "Depending on your phone's firmware, this gesture may not open notifications. Turn on Morphic " +
                            "gestures to fix it.",
                    )
                }
            }
        }
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
