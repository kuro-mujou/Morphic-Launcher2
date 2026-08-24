package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.picker.AppPicker
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * The multi-select app picker, with its selection and its commit — **one sheet, wherever apps are chosen in bulk**.
 *
 * Extracted from the icon container's settings screen when the home list's *Add apps* row became the second thing
 * needing exactly this, on the extract-at-the-second-consumer rule. It stays in `feature:home` rather than moving to
 * `core:designsystem` because it is built on [LauncherBottomSheet], which lives here; it moves when a third consumer
 * elsewhere wants one. The picker *inside* it is already shared — `AppPicker` went into the design system on its
 * first consumer precisely because this one was named and waiting.
 *
 * **A sheet of its own rather than inline, so the scratch selection is scoped to the sheet being on screen**:
 * dismissing disposes it, which is what makes backing out leave nothing behind without anything having to reset it.
 *
 * @param apps what to offer, already filtered and ordered by the caller. A caller that is filling something offers
 *   what is not already in it — the picker does not know what "already there" means.
 * @param onAdd the chosen apps **in the order they were ticked**, which is the only order the user has expressed and
 *   the order both consumers store. Not called with an empty list; the button is disabled until something is picked.
 */
@Composable
internal fun AppSelectionSheet(
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onAdd: (List<ComponentKey>) -> Unit,
) {
    val colors = LocalMorphicColors.current
    // A list, not a set, so the apps land in the order they were ticked — see [onAdd].
    var picked by remember { mutableStateOf<List<ComponentKey>>(emptyList()) }

    LauncherBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = "Add apps",
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            // Disabled rather than hidden: the button is what tells a user the ticks are not yet committed, so it
            // has to be visible before anything is ticked. The launcher's "absent, not disabled" rule is about a
            // verb with no op behind it; this one has an op and a precondition, and hiding it would leave a
            // multi-select screen with no visible way to finish.
            TextButton(onClick = { onAdd(picked) }, enabled = picked.isNotEmpty()) {
                Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
            }
        }
        AppPicker(
            apps = apps,
            selected = picked.toSet(),
            onPick = { component ->
                picked = if (component in picked) picked - component else picked + component
            },
        )
    }
}
