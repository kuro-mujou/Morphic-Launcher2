package inkspire.morphic.feature.settings.orientation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import org.koin.androidx.compose.koinViewModel

/**
 * **Orientation**: what the launcher does when the device turns.
 *
 * The one section describing the *window* rather than something drawn in it, which is why it has no preview, no
 * sizing, and — alone among the settings panes — no device report: nothing here is drawn differently per posture,
 * and nothing here writes a layout that would need a grid to be laid out against.
 *
 * **"Follow device" is a choice in the row, not the absence of one.** It could have been a switch with two locked
 * states hidden behind it, but the three answers are peers: a user who wants landscape has not "turned something
 * on", they have picked one of three.
 *
 * **Neither control raises a dialog, and the layout switch is the one worth noticing.** It used to ask which of two
 * layouts survived, because both modes wrote the same rows and one had to lose. Each mode owns its own rows now, so
 * the switch is a switch.
 */
@Composable
internal fun OrientationDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<OrientationViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            onCheckedChange = viewModel::setIndependentLayout,
        )
    }
}
