package inkspire.morphic.feature.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.gesture.describeGestureAction
import inkspire.morphic.core.model.GestureAction
import org.koin.androidx.compose.koinViewModel

/**
 * Asks for Morphic gestures at the moment a gesture needed it and found it off; draws nothing otherwise.
 *
 * **At the gesture, not at assignment.** The picker saves an action whether or not the service is on, because an
 * assignment made with it on can lose it later — some skins switch accessibility services off after an app update or a
 * force stop — and only the gesture can see that. Settings shows the service's state; this is the only prompt.
 *
 * Placed by the shell above every surface, since HOME's own swipes fire in the shell and an item's gestures in home.
 */
@Composable
internal fun GestureServicePrompt() {
    val viewModel = koinViewModel<GestureServicePromptViewModel>()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    blocked?.let { action ->
        GestureServiceDialog(action = action, onTurnOn = viewModel::turnOn, onDismiss = viewModel::dismiss)
    }
}

/**
 * The prompt itself. An `AlertDialog`, as `CategoryRenameDialog` is: it brings its own scrim over whichever surface the
 * gesture fired on.
 *
 * @param action what could not run, named so the user knows which gesture asked. Only a service action arrives here,
 *   so the empty catalog is never consulted for an app's name.
 */
@Composable
private fun GestureServiceDialog(action: GestureAction, onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn on Morphic gestures") },
        text = {
            Text(
                "${describeGestureAction(action, emptyMap())} needs Morphic gestures, the launcher's accessibility " +
                    "service. It performs gestures only and doesn't read anything on your screen.",
            )
        },
        confirmButton = { TextButton(onClick = onTurnOn) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
