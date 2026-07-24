package inkspire.morphic.core.designsystem.component.field

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * Text input for our own controlled surfaces (Settings), on the monochrome [MorphicColors].
 *
 * It **wraps M3 [TextField]** — so it inherits real text editing (cursor, IME, selection) and the Expressive
 * field motion — but strips M3's colours and its baseline indicator: an opaque token container, no indicator
 * line, and a neutral focus ring drawn as a border. [isError] switches the ring + cursor to the red `error`
 * token.
 *
 * This is deliberately **not** the launcher-surface field: UI floating over the wallpaper needs
 * brightness-adaptive tinting (a separate system), whereas this assumes a known surface colour. Icons are
 * passed as composable slots, so this component pulls in no icon dependency.
 */
@Composable
fun MorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalMorphicColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val ringColor = when {
        isError -> colors.error
        focused -> colors.focusRing
        else -> Color.Transparent
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.border(1.5.dp, ringColor, shape),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        shape = shape,
        placeholder = if (placeholder.isEmpty()) null else { { Text(placeholder) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            disabledContainerColor = colors.surface,
            errorContainerColor = colors.surfaceElevated,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
            errorCursorColor = colors.error,
            focusedTextColor = colors.content,
            unfocusedTextColor = colors.content,
            disabledTextColor = colors.contentDisabled,
            errorTextColor = colors.content,
            focusedPlaceholderColor = colors.contentMuted,
            unfocusedPlaceholderColor = colors.contentMuted,
            disabledPlaceholderColor = colors.contentDisabled,
        ),
    )
}
