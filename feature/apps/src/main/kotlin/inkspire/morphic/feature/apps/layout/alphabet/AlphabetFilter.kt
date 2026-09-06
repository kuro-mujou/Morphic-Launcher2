package inkspire.morphic.feature.apps.layout.alphabet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * Picking a letter, and living with the one you picked — the A–Z **filter**, which is what an alphabet can offer a
 * surface that is not in alphabetical order.
 *
 * **A filter here, an index on the derived layouts, and the difference is the surface rather than a preference.** A
 * strip that scrolls needs an A–Z list under it to scroll; the category pager has categories, in an order the user
 * chose, so the only thing "go to M" can mean there is *show me M*. The two share their buckets ([LetterBucket]) and
 * nothing else.
 *
 * **It paints nothing, and replaces the arrangement rather than covering it.** A scrim over a live surface leaves the
 * page's header, its grid and its tab strip legible behind the letters, which is a page of text over a page of icons;
 * and it dims a surface that is *already* a sheet of blurred wallpaper, which is what this launcher puts a side
 * surface on. Drawing no background at all means the letters land on that film — the same one the grid's labels are
 * read against — and the caller not drawing the arrangement is what makes the film the thing behind them.
 */
@Composable
internal fun AlphabetPicker(
    labels: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            // Silent, like every other full-screen tap-catcher here: no ripple and no click semantics.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .uiInsetsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        LazyVerticalGrid(
            // Wider in landscape, where the same letters in five columns would be a column of five rows in the middle
            // of an empty screen. L1's two counts, which are the shape of the screen rather than a preference.
            columns = GridCells.Fixed(if (currentDeviceConfiguration().isLandscape) 8 else 5),
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.Center,
        ) {
            itemsIndexed(labels) { index, label ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.content,
                    )
                }
            }
        }
    }
}

/**
 * What a filtered surface says about itself: the letter it is showing, a way back to the picker, and a way out.
 *
 * **The way out is a control, not only the back button.** It is the same thing the search field had to learn: back
 * does close this, and nothing on screen says so, which leaves a user looking at a surface that is missing most of
 * their apps with no visible reason.
 *
 * **And a way back to the picker beside it**, which L1 had and is the reason scanning three letters is three taps
 * rather than six. Closing first would return to the pager and lose the place the filter was opened from.
 */
@Composable
internal fun AlphabetFilterBar(
    label: String,
    onPick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPick) {
            Text("A–Z", style = MaterialTheme.typography.labelLarge, color = colors.content)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close filter", tint = colors.content)
        }
    }
}
