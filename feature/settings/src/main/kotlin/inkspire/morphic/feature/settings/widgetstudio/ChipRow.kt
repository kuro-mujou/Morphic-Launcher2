package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * A row of named things to pick one of, scrolling sideways once they outgrow the width — which a segmented control
 * cannot, and a widget's parts do as soon as a few blocks are added.
 *
 * @param selected the index lit, or -1 for none.
 */
@Composable
internal fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = LocalMorphicColors.current
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            val lit = index == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) colors.onAccent else colors.content,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (lit) colors.accent else colors.surfaceElevated)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
