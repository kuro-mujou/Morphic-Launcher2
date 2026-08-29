package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.feature.settings.description
import inkspire.morphic.feature.settings.label

/**
 * **Which animation plays when HOME and a side surface cross.**
 *
 * A modal of radio rows, the shape [SideBindingPicker] already uses for the register — six mutually-exclusive
 * motions, exactly one in force at a time. The six do not fit a segmented control (the register itself found that a
 * six-option row wraps), and each wants a line saying what it looks like, which a chip cannot carry — so the picker
 * that names surfaces names transitions too, with the [SurfaceTransition.description] under each.
 *
 * **The whole register's one global choice, not a per-edge one** — so, unlike the edge picker, it has no subject to
 * name in its title and no "None": a crossing always animates somehow, and SLIDE is the plainest way.
 *
 * @param selected the transition in force.
 * @param onSelect commits and dismisses. One tap, no confirm: `feature:shell` reads the same flow, so the next swipe
 *   shows the change with nothing to apply.
 */
@Composable
internal fun SurfaceTransitionPicker(
    selected: SurfaceTransition,
    onSelect: (SurfaceTransition) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch animation") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SurfaceTransition.entries.forEach { transition ->
                    RegisterPickerRow(
                        label = transition.label,
                        subtitle = transition.description,
                        selected = transition == selected,
                        onClick = { onSelect(transition) },
                    )
                }
            }
        },
        // As in the edge picker: a "Cancel" would imply the taps were provisional; they are not. This only closes.
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.content) } },
        containerColor = colors.background,
        titleContentColor = colors.content,
        textContentColor = colors.content,
    )
}
