package inkspire.morphic.feature.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.activity.launchSafely
import org.koin.androidx.compose.koinViewModel

/**
 * Asks to be made the default home app when the launcher comes back without being it — once in a while, see
 * [DefaultLauncherPromptViewModel]; draws nothing otherwise.
 *
 * **A request, never a gate.** Declining leaves the launcher exactly as usable, and the settings index keeps its row.
 * An app cannot make itself the home app, so one tap in the system's own chooser is as close to taking the role back as
 * the platform allows.
 *
 * Placed by the shell above every surface, beside `GestureServicePrompt`.
 */
@Composable
internal fun DefaultLauncherPrompt() {
    val viewModel = koinViewModel<DefaultLauncherPromptViewModel>()
    val request by viewModel.request.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.askIfDue()
        onPauseOrDispose { }
    }
    // Started for a result, and the result ignored: the role chooser learns who is asking only from an activity started
    // for one — see `DefaultLauncherRole.requestIntent`.
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    request?.let { intent ->
        DefaultLauncherDialog(
            onSetDefault = {
                viewModel.dismiss()
                roleRequest.launchSafely(intent)
            },
            onDismiss = viewModel::dismiss,
        )
    }
}

/** The ask itself. An `AlertDialog`, as `GestureServiceDialog` is: it brings its own scrim over whichever surface is up. */
@Composable
private fun DefaultLauncherDialog(onSetDefault: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Morphic as default launcher") },
        text = { Text("Morphic isn't your default launcher, so pressing home opens a different one.") },
        confirmButton = { TextButton(onClick = onSetDefault) { Text("Set as default") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
