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
 * A–Z navigation is the first, and the reason the section exists rather than the reason it is called this: what it
 * attaches to is *the app collection*, not a surface — the APPS derived layouts index themselves with a strip, the
 * category pager filters itself from a picker, and HOME's vertical list is next. What lands here later is whatever
 * else answers to more than one surface and sizes nothing, which is why this pane has no preview and no device to
 * report, alone among the sections.
 *
 * **One switch for both affordances**, because they are one feature seen from two surfaces: a strip needs an
 * alphabetical list under it to scroll, and a surface arranged by hand can only be *filtered* by a letter. Splitting
 * them would be two switches for one question.
 *
 * **The style chooser is absent while it is off, not disabled.** It is the standing rule, and this is the case it is
 * clearest on: a look for something that is not drawn is a control whose effect nobody can see. It animates in rather
 * than appearing, since the switch above it is what the finger is still on.
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
        SettingsSectionHeader("A–Z", spaceAbove = false)
        MorphicSwitchRow(
            label = "Find apps by letter",
            // Warns rather than describes, which is the bar this pane's one supporting line has to clear: the switch
            // turns on *two* affordances that look nothing alike, and which one a layout gets is not guessable from
            // here or discoverable without trying all five of them.
            supportingText = "A strip beside the list and grid; a letter picker on the category pager.",
            checked = strip.enabled,
            onCheckedChange = viewModel::setAlphabetStripEnabled,
        )

        AnimatedVisibility(visible = strip.enabled) {
            Column {
                // The style is the *strip's* alone — the picker is a grid of letters and has no rail to bow — which
                // is why the heading names it rather than saying "Style" over a control that governs half the switch
                // above it.
                SettingsSectionHeader("Strip style")
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
