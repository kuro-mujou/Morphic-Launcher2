package inkspire.morphic.core.designsystem.component.toggle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The in-house on/off switch: a **thin rail with a knob riding over it**, which is Material 2's proportion rather
 * than Material 3's.
 *
 * **This is the first component that goes fully custom even though M3 has an equivalent, and the reason is an API
 * limit rather than a taste one.** The standing rule is to wrap the M3 component and restyle it — that is what
 * `MorphicButton` and `MorphicSlider` do, and it is how they get Expressive motion for free. M3's `Switch` cannot
 * be restyled this far: it exposes a `thumbContent` slot and a `colors`, and **nothing at all for the track**,
 * whose 52×32 pill comes from `SwitchTokens` and is not a parameter. A `Modifier.size` does not help either — the
 * component sizes itself internally. So the shape we want is unreachable through it, which is the same test the
 * 2D pad and the segmented control already pass, arrived at from the other direction.
 *
 * What M3's shape costs here specifically: its track fully encloses the thumb, so the control reads as a large
 * filled pill whose state is *which end the blob is at*. The M2 shape separates the two — a 14dp rail, a 20dp knob
 * standing proud of it — so the knob is the thing that moves and the rail is the thing that fills. On a monochrome
 * palette that separation is worth having, because it gives the state two independent signals (where the knob is,
 * and how bright the rail is) where the M3 pill leaves mostly one.
 *
 * **Expressive *motion* is kept, which is the half of M3 this codebase does keep.** The knob travels on
 * `motionScheme.defaultSpatialSpec` and the colors cross-fade on `defaultEffectsSpec` — spatial for the thing that
 * moves, effects for the thing that does not, which is the distinction those two specs exist to draw. A snapped
 * color under a springing knob is the tell that they were treated as one.
 *
 * **Colors come from the slider's own track/thumb roles** (`trackInactive`, `trackActive`, `thumb`), so a switch
 * and a slider sitting in the same panel are made of the same greys rather than of two independent readings of the
 * palette. The **on** track carries alpha, as M2's does, so the solid knob stays visible against it — at full
 * strength `trackActive` *is* `thumb` and the knob would vanish into the rail. Off, the knob is `contentMuted`
 * against a `trackInactive` rail, which is a light knob on a dark rail in the dark theme and a dark knob on a light
 * rail in the light one — the same reading either way, which a fixed pair of colors would not have given.
 *
 * The accent is the launcher's grayscale *emphasis*, not a hue — "on" reads by contrast, which is the palette's
 * standing rule. Red stays reserved for `error`, so a switch never uses it however destructive its setting is.
 *
 * **Tap only: there is no drag, and that is settled rather than deferred.** M3's switch can be swiped; this one is
 * not meant to be. The form it is used in is [MorphicSwitchRow], where the *row* is the target — nobody reaches for
 * 14dp of travel under a fingertip when the whole row toggles. The tap itself sits on the **knob**, so the ripple is
 * a circle on the thing that moves rather than a rectangle over the rail.
 *
 * Prefer [MorphicSwitchRow] wherever the switch has a label, which is nearly everywhere: a bare switch is a small
 * target beside text that is not part of it, and the row makes the whole thing one target and one announcement.
 *
 * @param onCheckedChange `null` makes the switch non-interactive *without* disabling it — which is what a parent
 *   that owns the toggle (see [MorphicSwitchRow]) needs, so the gesture is not handled twice and the knob does not
 *   gray out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalMorphicColors.current

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colors.contentDisabled.copy(alpha = OnTrackAlpha)
            !enabled -> colors.trackInactive
            checked -> colors.trackActive.copy(alpha = OnTrackAlpha)
            else -> colors.trackInactive
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "MorphicSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            checked -> colors.thumb
            else -> colors.contentMuted
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "MorphicSwitchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ThumbTravel else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "MorphicSwitchOffset",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = TrackWidth, height = ThumbDiameter),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The rail: full width, thinner than the box, so the knob stands proud of it top and bottom. That overhang
        // *is* the M2 look — an M3 track would be the full 20dp and swallow the knob.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(CircleShape)
                .background(trackColor),
        )
        // The knob. Offset in the layout lambda rather than as a `Modifier.offset(x = …)` value so the animated
        // read happens at layout rather than in composition — a spring emits every frame, and this way none of
        // those frames recompose anything.
        Box(
            Modifier
                .then(
                    if (onCheckedChange == null) {
                        Modifier
                    } else {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                    },
                )
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(ThumbDiameter)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/**
 * A labeled switch: the text on the left, the switch on the right, **the whole row one target**.
 *
 * This is the form every real screen wants, and hand-rolling it is how rows drift apart — the same argument the
 * launcher's shared cells are built on. Two things it gets right that a `Row { Text(...); MorphicSwitch(...) }`
 * does not:
 *
 * - **The label is part of the control.** `Modifier.toggleable` with [Role.Switch] puts the click and the
 *   accessibility announcement on the row, so the target is the row's full width rather than the switch's own, and
 *   a screen reader reads the label *and* the state as one thing instead of an unlabeled toggle next to some prose.
 *   The switch is then handed `onCheckedChange = null` — not `enabled = false`, which would gray it — so one press
 *   is handled once.
 * - **No `indication`**, which is why the row also owns an `interactionSource` rather than letting `toggleable`
 *   make one it would be the only reader of. A ripple washing a full-width row is a much larger gesture than the
 *   thing it reports, and the switch is already animating: the knob springing across *is* the feedback.
 * - **[supportingText] belongs to the label, not beside it.** A switch is two words at most, so anything that has
 *   to explain the setting goes underneath in muted type, where it stays part of the same target.
 */
@Composable
fun MorphicSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalMorphicColors.current

    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                interactionSource = remember { MutableInteractionSource() },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.content else colors.contentDisabled,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colors.contentMuted else colors.contentDisabled,
                )
            }
        }

        // Null, so the row's `toggleable` is the only handler — see the note above.
        MorphicSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Material 2's switch metrics, kept exactly: a 34×14 rail under a 20dp knob, so the knob travels the 14dp the rail
 * has left over and overhangs it by 3dp top and bottom.
 *
 * Taken rather than invented because the proportion is the whole point of this component — the numbers are what
 * make it read as M2 instead of as a smaller M3, and picking near-misses by eye is how it would end up neither.
 */
private val TrackWidth = 34.dp
private val TrackHeight = 14.dp
private val ThumbDiameter = 20.dp
private val ThumbTravel = TrackWidth - ThumbDiameter

/** Keeps the solid knob legible on the filled rail — at full strength `trackActive` and `thumb` are one color. */
private const val OnTrackAlpha = 0.5f
