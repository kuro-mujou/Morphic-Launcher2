package inkspire.morphic.feature.apps.layout.categorypager

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.model.Category

/**
 * Renames one category — the single verb a tab's menu offers.
 *
 * **The only category write the store can honor today**, which is why the menu that opens this has one row.
 * Creating and deleting are absent rather than disabled: a built-in category is re-created by the classifier on the
 * next launch, so a delete would silently come back, and a user-created one needs an id space that does not exist
 * yet (see `isBuiltInCategoryId`). Choosing a tab's icon is absent for a plainer reason — a category has no icon of
 * its own to store, the tab borrows its first app's.
 *
 * **An `AlertDialog`, matching the settings pickers**, rather than one of this launcher's own overlays: the frosted
 * launcher chrome is deferred, and a text field over the wallpaper needs it — where a dialog brings its own
 * scrim, its own IME insets and the monochrome M3 styling `LauncherTheme` already bridges. Its `TextFieldState` is
 * hoisted into it (not passed in) because this composable *is* the field's owner; the caller only says which
 * category is being renamed.
 *
 * @param onRename the trimmed name. Never called with a blank, and never with the name it already had — an
 *   unchanged rename would be a pointless write that re-emits the whole surface.
 */
@Composable
internal fun CategoryRenameDialog(
    category: Category,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTextFieldState()
    // Seeded from the category rather than at construction, so the dialog can be recomposed for a different tab
    // without carrying the previous one's text. The cursor lands at the end, which is where an edit of an existing
    // name starts.
    LaunchedEffect(category.id) { state.setTextAndPlaceCursorAtEnd(category.name) }

    val name by remember { derivedStateOf { state.text.toString().trim() } }
    val canRename = name.isNotEmpty() && name != category.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            MorphicTextField(
                state = state,
                placeholder = category.name,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = canRename,
                onClick = {
                    onRename(name)
                    onDismiss()
                },
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
