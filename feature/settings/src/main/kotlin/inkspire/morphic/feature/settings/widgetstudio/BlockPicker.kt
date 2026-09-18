package inkspire.morphic.feature.settings.widgetstudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.ShrinkToFit
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.WidgetRender
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.data.widgets.WidgetBlock

/**
 * The blocks that can be added, each shown **on this widget** — its background, at its size, with the block where it
 * would land — so what is picked is what appears, rather than a block seen in isolation that then looks different on
 * a dark card or clipped by a short one.
 *
 * @param size the widget as HOME draws it, which each preview is laid out at before being shrunk into its cell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockPicker(
    blocks: List<WidgetBlock>,
    recipe: WidgetRecipe,
    data: ScriptData?,
    size: DpSize,
    onPick: (WidgetBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    // The widget with its blocks taken away: its background, which every preview sits on.
    val background = remember(recipe) { recipe.copy(layers = recipe.layers.filter { it.name == null }) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Text(
            text = "Add a block",
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(blocks, key = { it.id }) { block ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(block) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShrinkToFit(
                        size = size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(size.width / size.height),
                    ) {
                        if (data != null) WidgetRender(background.copy(layers = background.layers + block.layer), data)
                    }
                    Text(
                        text = block.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
