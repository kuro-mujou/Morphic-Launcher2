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
import inkspire.morphic.core.model.RotationMode
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import org.koin.androidx.compose.koinViewModel

/**
 * **Orientation**: what the launcher does when the device turns.
 *
 * The one section describing the *window* rather than something drawn in it, which is why it has no preview and no
 * device to report — there is nothing here whose size a posture could change.
 *
 * **"Follow device" is a choice in the row, not the absence of one.** It could have been a switch with two locked
 * states hidden behind it, but the three answers are peers: a user who wants landscape has not "turned something on",
 * they have picked one of three. A segmented row says that where a switch plus a conditional chooser would not.
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
    }
}
