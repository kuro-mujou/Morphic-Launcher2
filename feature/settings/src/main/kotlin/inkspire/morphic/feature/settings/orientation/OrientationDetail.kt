package inkspire.morphic.feature.settings.orientation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.register.RegisterPickerRow
import org.koin.androidx.compose.koinViewModel

/**
 * **Orientation**: what the launcher does when the device turns.
 *
 * The one section describing the *window* rather than something drawn in it, which is why it has no preview and no
 * sizing — there is nothing here whose extent a posture could change. It reports its device all the same, because
 * the independence actions write layout and a copy between postures has to be laid out against a real grid.
 *
 * **"Follow device" is a choice in the row, not the absence of one.** It could have been a switch with two locked
 * states hidden behind it, but the three answers are peers: a user who wants landscape has not "turned something
 * on", they have picked one of three.
 */
@Composable
internal fun OrientationDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<OrientationViewModel>()
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Raised when independence is switched *off*, because that is the only direction with a question in it: turning
    // it on starts landscape from what it is already showing, while turning it off has to pick between two layouts
    // the user made.
    var merging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsSectionHeader("Rotation", spaceAbove = false)
        val modes = RotationMode.entries
        MorphicSegmentedButtons(
            options = modes.map { mode ->
                when (mode) {
                    RotationMode.AUTO -> "Follow device"
                    RotationMode.PORTRAIT -> "Portrait"
                    RotationMode.LANDSCAPE -> "Landscape"
                }
            },
            selectedIndex = modes.indexOf(state.settings.rotation),
            onSelect = { viewModel.setRotationMode(modes[it]) },
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsSectionHeader("Layout")
        MorphicSwitchRow(
            label = "Independent landscape layout",
            // States the cost rather than restating the label. Off is the interesting half — that arranging one
            // posture arranges both is the behaviour a user would otherwise have to discover by rotating.
            supportingText = "Off, landscape shows your portrait layout re-arranged to fit, and editing either " +
                "changes both. On, each keeps its own.",
            checked = state.settings.independentLayout,
            onCheckedChange = { independent ->
                if (independent) viewModel.enableIndependentLayout() else merging = true
            },
        )
    }

    if (merging) {
        IndependenceMergePicker(
            onPick = { merge ->
                viewModel.disableIndependentLayout(merge)
                merging = false
            },
            onDismiss = { merging = false },
        )
    }
}

/**
 * **Which of the two layouts survives** when landscape stops keeping one of its own.
 *
 * A modal of radio rows, the shape the register's pickers already use. Unlike those, dismissing is a genuine
 * *cancel*: independence stays on, because there is no answer here that can be assumed — every one of the three
 * discards a layout the user made, and picking for them is the one thing this dialog exists to avoid.
 */
@Composable
private fun IndependenceMergePicker(
    onPick: (IndependenceMerge) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which layout should both use?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RegisterPickerRow(
                    label = "Portrait",
                    subtitle = "Keep the portrait layout. Landscape is re-arranged from it.",
                    selected = false,
                    onClick = { onPick(IndependenceMerge.KEEP_PORTRAIT) },
                )
                RegisterPickerRow(
                    label = "Landscape",
                    subtitle = "Keep the landscape layout. Portrait is re-arranged from it.",
                    selected = false,
                    onClick = { onPick(IndependenceMerge.KEEP_LANDSCAPE) },
                )
                RegisterPickerRow(
                    label = "Neither",
                    subtitle = "Go back to the layout from before landscape became independent.",
                    selected = false,
                    onClick = { onPick(IndependenceMerge.RESTORE_SNAPSHOT) },
                )
            }
        },
        // A cancel, not a close: nothing has been written, and leaving without choosing leaves independence on.
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.content) } },
    )
}
