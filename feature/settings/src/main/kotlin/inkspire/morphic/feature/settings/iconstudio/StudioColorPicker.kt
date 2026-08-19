package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.component.color.MorphicColorPicker
import inkspire.morphic.core.designsystem.component.field.MorphicTextField

/**
 * One request to pick a color: what it starts on, and where the result goes.
 *
 * @property argb the color the picker opens on. The field's current color, never a fresh default, so a picker opened
 *   to adjust an existing color starts where that color already is.
 * @property onPick called on every change, not on a Done — the studio's whole premise is a live preview, and a color
 *   the user cannot see on the icon while dragging is a color they are choosing blind.
 */
@Stable
class ColorPickRequest(
    val argb: Int,
    val onPick: (Int) -> Unit,
)

/**
 * Where a color field sends "open the full picker".
 *
 * **A host rather than state inside the field, because the picker cannot render where it is asked for.** It is asked
 * for from deep inside a tool section — three levels into a capped, scrolling panel — and it is a surface the size of
 * that whole panel. Rendered in place it did two things wrong at once: it filled the section, and its
 * saturation/value canvas took every drag, so the panel could no longer be scrolled *past* it. Both are properties of
 * where it was drawn, not of the picker, so the fix is to draw it somewhere else and leave the field with a request.
 *
 * Same shape as the launcher's `LocalMenuHost` and for the same reason: one host means "two open at once" is
 * unrepresentable, so opening a second field's picker closes the first by construction.
 *
 * Deliberately **not** in `IconStudioState`. Nothing here is part of the recipe — undo must not step through it, and a
 * ViewModel that knew which swatch was being edited would be holding a UI position.
 */
@Stable
class StudioColorPickerHost {

    /** The open request, or null when no picker is up. */
    var request: ColorPickRequest? by mutableStateOf(null)
        private set

    fun open(argb: Int, onPick: (Int) -> Unit) {
        request = ColorPickRequest(argb, onPick)
    }

    fun close() {
        request = null
    }
}

/** The studio's one color-picker host; see [StudioColorPickerHost]. */
val LocalStudioColorPicker = staticCompositionLocalOf<StudioColorPickerHost> {
    error("No StudioColorPickerHost provided — the picker is hosted by IconStudioScreen")
}

/**
 * The picker itself, drawn as a panel in the tool panel's own slot.
 *
 * **It takes that slot rather than floating over the canvas**, which is the placement decision worth stating: a color
 * is judged against the icon it is being applied to, so a picker centered on the screen would cover the one thing the
 * user is looking at. Bottom-anchored, the icon stays in view above it and the color moves under the finger.
 *
 * **It replaces the tool panel instead of stacking on it.** Two glass sheets would be taller than the canvas they
 * float over, and the field that opened this is one of the controls it would be covering anyway. One panel at a time
 * is also already the studio's rule — the rail opens exactly one.
 *
 * **The saturation/value canvas is width-capped rather than full-bleed.** It is square-ish by construction, so a
 * full-width one on a phone is most of the screen tall; capped, the panel lands near the tool panel's own height and
 * the two do not jump as one replaces the other.
 *
 * @param onDone dismisses the picker. There is no cancel: every change has already been applied live, and undo is the
 *   way back — the same bargain every other control in the studio makes.
 */
@Composable
fun StudioColorPickerPanel(
    request: ColorPickRequest,
    hazeState: HazeState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seeded from the request and owned from then on, so the readout and the swatch track the drag. Keyed on the
    // request, so opening a different field re-seeds rather than carrying the previous color's position over.
    var current by remember(request) { mutableStateOf(request.argb) }

    // **A plain `remember`, and it must stay one.** This was `rememberSaveable(request, saver = …)`, on the
    // design-system rule that a hoisted `TextFieldState` is what survives a configuration change — and here that
    // rule bought nothing and cost the panel its correctness.
    //
    // **Inputs cannot defeat a restore.** `rememberSaveable` is `remember(*inputs) { consumeRestored() ?: init() }`,
    // so a *new* request re-runs the block and the block hands back the **previous** session's text before `init` is
    // ever reached. Every picker session lives at the same composition position under one `PanelSlot.COLOR` holder
    // key, so what came back was whatever color the last picker had been left on — from a different field, on a
    // different layer, minutes earlier.
    //
    // **And it did not merely display it.** `snapshotFlow` emits on collection, so the stale text arrived at the
    // effect below, parsed, differed from `current`, and was pushed through `request.onPick` — overwriting the
    // color the user had just chosen on the swatch row with one they had not. Picking a swatch and then opening the
    // picker to adjust it reverted the swatch.
    //
    // **What saveable was for is unreachable anyway**, which is what makes this a plain deletion rather than a
    // trade. `StudioColorPickerHost.request` is a `remember`, so after process death there is no request and this
    // panel is not composed at all — the only way it ever comes back is a close-and-reopen, which is precisely the
    // case where restoring is wrong. There was never a live session for the saved text to be restored *into*.
    //
    // Keyed on the request, so opening any field seeds from that field's own color.
    val hexField = remember(request) { TextFieldState(request.argb.hex) }

    // **Typing wins while it parses; the picker wins otherwise.** Both write `current`, so the two can only disagree
    // while the text is unfinished — and then the guard leaves it alone, which is what stops a drag from deleting
    // half-typed input and what stops the field fighting a value it just produced.
    //
    // **`snapshotFlow` emits on collection, so this fires once on open with whatever the field holds** — which is
    // harmless *only* because the field is seeded from the same `request.argb` that `current` is. The two agree, the
    // `parsed != current` guard is false, and nothing is pushed back through `onPick`. A field seeded from anywhere
    // else turns that first emission into a write of a color the user never chose; see the note above it.
    LaunchedEffect(hexField, request) {
        snapshotFlow { hexField.text.toString() }.collect { text ->
            val parsed = parseHexColor(text)
            if (parsed != null && parsed != current) {
                current = parsed
                request.onPick(parsed)
            }
        }
    }
    LaunchedEffect(current) {
        if (parseHexColor(hexField.text.toString()) != current) {
            hexField.setTextAndPlaceCursorAtEnd(current.hex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .studioSurface(hazeState, shape = RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(current), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            )
            // **The hex is a field, not a readout.** It is the only exact way in — dragging a saturation panel cannot
            // hit a color a designer handed you as six digits — and it is the only exact way *out*, which is why it was
            // worth showing even before it could be typed into.
            MorphicTextField(
                state = hexField,
                placeholder = "#RRGGBB",
                isError = hexRejected(hexField.text.toString()),
                keyboardOptions = KeyboardOptions(
                    // Hex is written in upper case here, and the digits are ASCII either way — so the keyboard opens
                    // on the characters that can actually be typed rather than on a word-shaped one.
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            ChoiceChip(label = "Done", selected = false, onClick = onDone)
        }

        MorphicColorPicker(
            argb = current,
            onArgbChange = {
                current = it
                request.onPick(it)
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = PickerMaxWidth),
        )
    }
}

/** Keeps the panel near the tool panel's height — see [StudioColorPickerPanel]. */
private val PickerMaxWidth = 280.dp

/** `#RRGGBB`, upper case; the alpha is dropped because nothing in this editor lets a picked color carry one. */
private val Int.hex: String get() = "#%06X".format(this and 0xFFFFFF)

/** The digits of a typed color: the leading `#` is optional, and surrounding space is not the user's mistake. */
private fun String.hexDigits(): String = trim().removePrefix("#")

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * The color `text` names, or null if it does not name one yet.
 *
 * Six digits only — no three-digit shorthand, which would make `#FFF` and a half-typed `#FFF…` mean different things
 * at different moments of the same keystroke run. Always opaque, matching `MorphicColorPicker`: nothing in this editor
 * lets a picked color carry an alpha.
 */
private fun parseHexColor(text: String): Int? = text.hexDigits()
    .takeIf { it.length == 6 && it.all(Char::isHexDigit) }
    ?.let { 0xFF000000.toInt() or it.toInt(16) }

/**
 * Whether what is typed can never become a color — a character that is not a hex digit, or more than six of them.
 *
 * **Unfinished is not wrong**, which is the whole distinction this makes: `#67` fails to parse and must not be shown
 * as an error, or the field turns red on the way to every color the user types. What earns the error state is input
 * that no further keystroke can rescue.
 */
private fun hexRejected(text: String): Boolean = text.hexDigits().let { digits ->
    digits.length > 6 || !digits.all(Char::isHexDigit)
}
