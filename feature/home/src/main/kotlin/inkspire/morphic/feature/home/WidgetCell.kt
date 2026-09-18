package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * The widget fills its cell, so [itemGestures] go on the whole of it — a widget is its footprint, not an icon in a
 * slot.
 */
@Composable
internal fun WidgetCell(recipe: WidgetRecipe, modifier: Modifier = Modifier, itemGestures: Modifier = Modifier) {
    val repository = koinInject<WidgetDataRepository>()
    val data by remember(recipe) { repository.data(WidgetCadence.of(recipe)) }.collectAsStateWithLifecycle(null)
    Box(
        modifier
            .fillMaxSize()
            .then(itemGestures),
    ) {
        data?.let { WidgetRender(recipe, it) }
    }
}
