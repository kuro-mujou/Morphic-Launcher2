package inkspire.morphic.core.designsystem.picker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * The multi-select app picker as a **full-screen pane**: a title, the searchable grid, and a Cancel / Add footer.
 *
 * **It paints no background of its own**, which is the whole of how it is meant to be used: it is drawn over
 * something already frosted — a collection's own film — while whatever it replaced is hidden. That is L1's
 * arrangement for the folder picker and it is the right one, because the alternative puts a sheet over a card over a
 * frost and asks the user to read three layers of chrome to find a search field. Nothing here is transparent to the
 * *wallpaper*: the caller's frost is what it sits on.
 *
 * **The root swallows taps.** With no background, a press on the gap beside a cell would otherwise fall through to
 * whatever the frost is covering — a folder's dismiss scrim, on every host that has one — and close the collection
 * out from under the picker.
 *
 * **The commit is explicit**, unlike the single-select picker where a tap *is* the choice. Ticking several apps is
 * one deliberate act with a moment at the end to change your mind, so there is a footer rather than a per-row commit,
 * and back and Cancel both mean "never mind" rather than "add nothing".
 *
 * @param apps what to offer, already filtered and ordered by the caller — a caller filling something offers what is
 *   not already in it, since neither this nor [AppPicker] knows what "already there" means.
 * @param onAdd the chosen apps **in the order they were ticked**, which is the only order the user has expressed.
 *   Never called with an empty list; Add is disabled until something is picked.
 */
@Composable
fun AppMultiPicker(
    apps: List<AppInfo>,
    onCancel: () -> Unit,
    onAdd: (List<ComponentKey>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Add apps",
) {
    val colors = LocalMorphicColors.current
    // A list, not a set — see [onAdd].
    var picked by remember { mutableStateOf<List<ComponentKey>>(emptyList()) }

    // Composed after whatever opened this, so it answers back first: back leaves the picker and returns to the
    // collection, rather than closing the collection with the picker still up.
    BackHandler(onBack = onCancel)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .uiInsetsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.content)
            AppPicker(
                apps = apps,
                selected = picked.toSet(),
                onPick = { component ->
                    picked = if (component in picked) picked - component else picked + component
                },
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MorphicButton(
                    onClick = onCancel,
                    style = MorphicButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                // Disabled rather than absent, which is the exception this launcher's rule allows: the button is
                // what tells a user the ticks are not committed yet, so it has to be there before anything is
                // ticked. A picker whose only visible verb was Cancel would read as one with no way to finish.
                MorphicButton(
                    onClick = { onAdd(picked) },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
                }
            }
        }
    }
}
