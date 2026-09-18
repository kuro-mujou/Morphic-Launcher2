package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widget.WidgetRender
import inkspire.morphic.data.widgets.WidgetCadence
import inkspire.morphic.data.widgets.WidgetDataRepository
import org.koin.compose.koinInject

/**
 * A widget of the launcher's own, drawn live from [recipe] — on HOME, under the finger while it is dragged, and on
 * the picker's page before it is placed. All three are this, so what the picker shows is what lands.
 *
 * **It subscribes to exactly what the recipe reads**, and only while it is on screen: the data is collected with
 * the lifecycle, so HOME in the background wakes nothing. Nothing is drawn until the first reading arrives, which is
 * the frame after composition.
 *
 * The widget fills its cell less [WidgetCellInset], and [itemGestures] go on exactly that — the touch target is what
 * is drawn, so the inset stays free as it is around an icon. It also makes the bounds those gestures report the
 * widget's drawn size, which is what the Style studio is handed to preview at.
 */
@Composable
internal fun WidgetCell(recipe: WidgetRecipe, modifier: Modifier = Modifier, itemGestures: Modifier = Modifier) {
    val repository = koinInject<WidgetDataRepository>()
    val data by remember(recipe) { repository.data(WidgetCadence.of(recipe)) }.collectAsStateWithLifecycle(null)
    Box(
        modifier
            .fillMaxSize()
            .then(WidgetCellInset)
            .then(itemGestures),
    ) {
        data?.let { WidgetRender(recipe, it) }
    }
}

/**
 * The margin a widget keeps inside its cell, so two side by side, or one against the screen edge, do not touch.
 *
 * The 8dp is a placeholder until HOME's padding settings own it.
 */
internal val WidgetCellInset: Modifier = Modifier.padding(8.dp)
