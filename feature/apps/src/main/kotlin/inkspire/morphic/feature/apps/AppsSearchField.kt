package inkspire.morphic.feature.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.field.MorphicTextField

/**
 * The APPS surface's search field, wherever `SearchPlacement` puts it.
 *
 * **`MorphicTextField`, which is the settings field, used here on purpose.** The launcher-surface field this wants —
 * frosted, wallpaper-adaptive — is deferred with the rest of the frosted chrome, and this surface is drawn on
 * `feature:shell`'s film, so a field with a solid container reads correctly against it rather than needing one. What
 * it costs is stated in the design system's own note: an unfocused field's emphasis *is* its focus ring, so the
 * container carries the affordance until the frosted field arrives.
 *
 * **The state is the caller's** ([TextFieldState] hoisted at the call site), which is the one exception the design
 * system makes to hiding state inside a component: what the field survives a configuration change *for* is that the
 * caller owns it. `AppsScreen` also reads it to report the query, and clears it when the surface leaves the screen.
 *
 * The IME's action key clears focus, which takes the keyboard down: results update on every keystroke, so there is
 * nothing for "Search" to submit, and a key that did nothing would be the disabled-control smell one layer down.
 */
@Composable
internal fun AppsSearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Box(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        MorphicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search apps",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { focusManager.clearFocus() },
        )
    }
}
