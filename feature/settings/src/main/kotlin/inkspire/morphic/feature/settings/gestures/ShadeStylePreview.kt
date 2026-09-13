package inkspire.morphic.feature.settings.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.ShadeStyle

/** The phone's outline. */
private val PhoneShape = RoundedCornerShape(20.dp)

/** A pulled-down panel hanging from the top of the screen. */
private val SheetShape = RoundedCornerShape(12.dp)

/** A notification card, the media card, and a vertical slider's track. */
private val CardShape = RoundedCornerShape(6.dp)

/**
 * A picture of the phone's pull-down panels as [style] arranges them, so the choice is matched against the phone in
 * hand rather than read about.
 *
 * **Separate is two phones, not one phone split down the middle.** On such a phone the two panels are two different
 * pulls that never share the screen, and a single outline holding both would draw the one thing that does not happen.
 *
 * **Each panel is drawn in the layout its kind of phone uses**, because that is what the user recognizes it by. A
 * combined shade heads its notifications with wide tiles and a brightness bar under them, the stock Android layout;
 * a separate control center leads with a media card and vertical sliders, then wide toggles, then round ones — the
 * layout the skins that split their panels share.
 *
 * **Drawn from boxes in the palette's own tokens rather than shipped as an image**: it follows light and dark with the
 * rest of settings, and it stays a diagram — shapes, not a screenshot of one skin, which would look wrong on every other.
 */
@Composable
internal fun ShadeStylePreview(style: ShadeStyle, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        when (style) {
            ShadeStyle.COMBINED -> Phone("Notifications and quick settings") {
                Sheet {
                    PillTiles(rows = 2)
                    HorizontalSlider()
                    Spacer(Modifier.height(4.dp))
                    repeat(3) { NotificationCard() }
                }
            }

            ShadeStyle.SEPARATE -> {
                Phone("Notifications") {
                    Sheet { repeat(3) { NotificationCard() } }
                }
                Phone("Quick settings") {
                    Sheet {
                        MediaAndSliders()
                        PillTiles(rows = 2)
                        RoundTiles(rows = 2)
                    }
                }
            }
        }
    }
}

/** One phone: its outline with a panel pulled down inside it as [content], and the [caption] naming that panel. */
@Composable
private fun Phone(caption: String, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalMorphicColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 132.dp, height = 200.dp)
                .clip(PhoneShape)
                .background(colors.background)
                .border(1.dp, colors.divider, PhoneShape)
                .padding(8.dp),
            content = content,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.contentMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A pulled-down panel: the sheet hanging from the top of the screen, holding [content]. */
@Composable
private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(LocalMorphicColors.current.surfaceElevated)
            .padding(6.dp),
        content = content,
    )
}

/** Brightness as one horizontal bar, part filled — as a combined shade draws it under its tiles. */
@Composable
private fun HorizontalSlider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(ink()),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = 0.6f)
                .fillMaxHeight()
                .background(LocalMorphicColors.current.accent),
        )
    }
}

/** A control center's first row: the media card, with brightness and volume as two vertical sliders beside it. */
@Composable
private fun MediaAndSliders() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(48.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CardShape)
                .background(ink()),
        )
        repeat(2) { VerticalSlider() }
    }
}

/** One vertical slider: a track filled from the bottom. */
@Composable
private fun VerticalSlider() {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight()
            .clip(CardShape)
            .background(ink()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.4f)
                .background(LocalMorphicColors.current.accent),
        )
    }
}

/** Wide toggles, two to a row, the first one lit — a row of pills reads as switches once one of them is on. */
@Composable
private fun PillTiles(rows: Int) {
    val colors = LocalMorphicColors.current
    val ink = ink()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { column ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(if (row == 0 && column == 0) colors.accent else ink),
                    )
                }
            }
        }
    }
}

/** Round toggles, four to a row, one of them lit. */
@Composable
private fun RoundTiles(rows: Int) {
    val colors = LocalMorphicColors.current
    val ink = ink()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { column ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (row == 0 && column == 2) colors.accent else ink),
                    )
                }
            }
        }
    }
}

/** One notification: an app's round icon beside two lines of text, drawn as bars. */
@Composable
private fun NotificationCard() {
    val ink = ink()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LocalMorphicColors.current.surface)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = 0.8f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(ink),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction = 0.5f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(ink),
            )
        }
    }
}

/**
 * The faint content color every placeholder shape is filled with — translucent over whatever it sits on, so the one
 * value reads in light and dark alike.
 */
@Composable
private fun ink(): Color = LocalMorphicColors.current.content.copy(alpha = 0.14f)
