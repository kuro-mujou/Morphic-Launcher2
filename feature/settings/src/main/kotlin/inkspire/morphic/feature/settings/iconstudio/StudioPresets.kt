package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.data.settings.IconPreset

/**
 * The studio's preset library: save what is being edited under a name, load one back, delete one.
 *
 * **A preset is a copy, not a link.** Loading one is an ordinary edit — recorded in history, undoable, and not
 * saved until Save — and deleting one touches nothing it was ever applied to. That is what makes the library
 * safe to keep tidy: there is no way for removing a preset to change an icon.
 *
 * Saving is likewise **independent of Save**. Naming a recipe puts it in the library and commits it nowhere, so a
 * user can build a look, keep it, and back out without applying it to anything.
 *
 * A section body: no surface and no title of its own — see [StudioSections][LayerStackRows] for why those belong to
 * the host. **The text field's state stays hoisted here rather than in the screen**, deliberately: a half-typed name is
 * scoped to the panel being open, and closing the panel discarding it is the behaviour a user expects.
 */
@Composable
internal fun PresetsControls(
    presets: List<IconPreset>,
    onSave: (String) -> Unit,
    onLoad: (IconPreset) -> Unit,
    onDelete: (String) -> Unit,
) {
    val nameState = rememberTextFieldState()
    val name by remember { derivedName(nameState) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphicTextField(
                state = nameState,
                placeholder = "Name this look",
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            // Disabled until there is a name, because an unnamed preset is one nothing could tell from another.
            Text(
                text = "save",
                color = StudioContentColor.copy(alpha = if (name.isEmpty()) 0.35f else 1f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(enabled = name.isNotEmpty()) {
                        onSave(name)
                        nameState.setTextAndPlaceCursorAtEnd("")
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (presets.isEmpty()) {
            Text(
                text = "Saved looks appear here, and in Settings → Icons.",
                color = StudioContentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLoad(preset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.name,
                        color = StudioContentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { onDelete(preset.name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete ${preset.name}",
                                tint = StudioContentColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The trimmed name currently typed. Its own derivation so the save control reads one value, not the raw buffer. */
private fun derivedName(state: androidx.compose.foundation.text.input.TextFieldState) =
    androidx.compose.runtime.derivedStateOf { state.text.toString().trim() }
