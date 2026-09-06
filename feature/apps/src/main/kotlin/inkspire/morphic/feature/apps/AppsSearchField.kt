package inkspire.morphic.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

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
 *
 * **Every placement can be closed; only one of them is *opened*.** Closing empties the query and drops the keyboard,
 * which is what leaves a surface with no half-finished search on it — a filtered arrangement, or a keyboard standing
 * over a page with nothing typed. The button that does it is always there on a mode, since a mode needs a way out,
 * and appears on a pinned field once there is a search to close: it is focused, or it has text in it. Back does the
 * same thing and is what an experienced user reaches for, which is exactly why it cannot be the only way — nothing on
 * screen says so.
 *
 * @param asMode true for the category pager's placement, the one that is a mode rather than a fixture. It buys the
 *   close button unconditionally, and focus on first composition — a field you pressed a button to get is one you
 *   meant to type in. A pinned field must not take focus, or opening APPS would raise the keyboard every time.
 */
@Composable
internal fun AppsSearchField(
    state: TextFieldState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    asMode: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = LocalMorphicColors.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // Through `derivedStateOf` so this row invalidates when the query becomes empty or stops being empty, rather than
    // on every keystroke — the text itself is `BasicTextField`'s to redraw, and nothing here reads it otherwise.
    val hasQuery by remember(state) { derivedStateOf { state.text.isNotEmpty() } }
    if (asMode) {
        // **Focus and the keyboard, as the field appears** — pressing a search button is the request, so nothing
        // else should have to be tapped. Safe unkeyed and unguarded: this composes only while the search is open, so
        // the effect runs once per opening, and a `LaunchedEffect` body runs after the node it targets is attached.
        //
        // `show()` as well as the focus, because focusing normally raises the keyboard and "normally" is not a
        // promise: the system suppresses the automatic raise in cases nothing here can see. Redundant in the common
        // path and a no-op when there is no input session yet, which is the cheap half of the trade.
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MorphicTextField(
            state = state,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                // Observed from out here rather than asked of the field: `MorphicTextField` tracks focus for its own
                // ring, and a second reading of the same focus target costs nothing and adds no parameter to a
                // component every settings screen shares.
                .onFocusChanged { focused = it.isFocused },
            placeholder = "Search apps",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { focusManager.clearFocus() },
        )
        if (asMode || focused || hasQuery) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close search", tint = colors.content)
            }
        }
    }
}
