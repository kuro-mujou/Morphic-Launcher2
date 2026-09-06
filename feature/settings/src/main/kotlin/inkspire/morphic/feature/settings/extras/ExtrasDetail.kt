package inkspire.morphic.feature.settings.extras

import androidx.compose.animation.AnimatedVisibility
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
import inkspire.morphic.core.model.AlphabetStripStyle
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import org.koin.androidx.compose.koinViewModel

/**
 * **Extras**: the launcher-wide additions that belong to no single surface.
 *
 * The A–Z index strip is the first, and the reason the section exists rather than the reason it is called this: the
 * strip attaches to *content ordered A–Z*, which the APPS derived layouts have now and HOME's vertical list will
 * have next, so it is not the APPS section's to own. What lands here later is whatever else answers to more than one
 * surface and sizes nothing — which is why this pane has no preview and no device to report, alone among the
 * sections.
 *
 * **The style chooser is absent while the strip is off, not disabled.** It is the standing rule, and this is the case
 * it is clearest on: a look for something that is not drawn is a control whose effect nobody can see. It animates in
 * rather than appearing, since the switch above it is what the finger is still on.
 */
@Composable
internal fun ExtrasDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ExtrasViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strip = state.alphabetStrip

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsSectionHeader("A–Z strip", spaceAbove = false)
        MorphicSwitchRow(
            label = "Show the strip",
            // Warns rather than describes, which is the bar this pane's one supporting line has to clear: where the
            // strip appears is not guessable from a switch that names no surface, and it is not something the user
            // can discover without leaving settings and trying all five layouts.
            supportingText = "On the app screen's list and grid, which are the layouts ordered A–Z.",
            checked = strip.enabled,
            onCheckedChange = viewModel::setAlphabetStripEnabled,
        )

        AnimatedVisibility(visible = strip.enabled) {
            Column {
                SettingsSectionHeader("Style")
                val styles = AlphabetStripStyle.entries
                MorphicSegmentedButtons(
                    options = styles.map { style ->
                        when (style) {
                            AlphabetStripStyle.STANDARD -> "Standard"
                            AlphabetStripStyle.CURVED -> "Curved"
                        }
                    },
                    selectedIndex = styles.indexOf(strip.style),
                    onSelect = { viewModel.setAlphabetStripStyle(styles[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
