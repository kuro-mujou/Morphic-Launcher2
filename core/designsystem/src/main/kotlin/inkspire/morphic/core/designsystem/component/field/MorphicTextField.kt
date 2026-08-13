package inkspire.morphic.core.designsystem.component.field

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * Text input for our controlled surfaces (Settings), on the monochrome [MorphicColors].
 *
 * Built on the **state-based** [BasicTextField] (pass a `rememberTextFieldState()`) with a custom
 * [TextFieldDecorator] — not M3's `TextField` — so text + selection survive configuration changes, and we own
 * the focus visuals: no floating label or baseline indicator fighting our styling. Focus is tracked into our
 * own [onFocusChanged] state; the container shows the focus ring (the red `error` ring when [isError]); the
 * placeholder sits behind the field until there's text; the cursor uses the accent (or error) color; and
 * focus is dropped when the keyboard is dismissed. Icon slots are composables, so this pulls in no icon dep.
 *
 * **[modifier] goes on the outermost node, so layout modifiers work** — `weight`, `width`, `padding`, the ordinary
 * contract every composable in this module keeps. It used to be applied to the styled container *inside* the
 * decorator, which reads harmlessly (that container is what a caller wants to size) and is not: a `weight` there is
 * attached to a node the parent `Row` never sees, so the parent measures the field at whatever width it asks for and
 * the modifier does **nothing** — silently, with the siblings pushed out of the row as the only symptom. Sizing still
 * reaches the container, because [BasicTextField] propagates its minimum constraints into the decoration.
 *
 * This is the settings field; the launcher-surface field (frosted, wallpaper-adaptive) is deferred.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorphicTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    shape: Shape = RoundedCornerShape(12.dp),
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val colors = LocalMorphicColors.current
    var focused by remember { mutableStateOf(false) }

    // Drop focus when the keyboard is dismissed (back / swipe / system hide) so the field isn't left focused.
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible && focused) focusManager.clearFocus()
    }

    val ring = when {
        isError -> colors.error
        focused -> colors.focusRing
        else -> Color.Transparent
    }
    val contentColor = if (enabled) colors.content else colors.contentDisabled
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor)

    BasicTextField(
        state = state,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        textStyle = textStyle,
        lineLimits = lineLimits,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        cursorBrush = SolidColor(if (isError) colors.error else colors.accent),
        decorator = TextFieldDecorator { innerTextField ->
            Row(
                // Styling only — the caller's modifier is on the field node above, and this container inherits its
                // width from there. See the KDoc for why it is not applied here.
                modifier = Modifier
                    .clip(shape)
                    .background(colors.surfaceElevated)
                    .border(1.5.dp, ring, shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = colors.contentMuted)
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(10.dp))
                    trailingIcon()
                }
            }
        },
    )
}
