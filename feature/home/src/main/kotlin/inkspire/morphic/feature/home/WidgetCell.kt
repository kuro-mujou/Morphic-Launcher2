package inkspire.morphic.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
 *
 * @param id the placed widget this is, when it is one — which is what lets its parts answer a tap (see
 *   [WidgetTouch]). Null under the finger and in the picker, where nothing is tapped.
 */
@Composable
internal fun WidgetCell(
    recipe: WidgetRecipe,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
    id: Long? = null,
) {
    val repository = koinInject<WidgetDataRepository>()
    val cadence = remember(recipe) { WidgetCadence.of(recipe) }
    // Seeded from the reading a widget already on screen holds, so a newly composed one — a lifted copy, a cell
    // recreated by a drop — does not draw empty until its own subscription emits. See `WidgetDataRepository.latest`.
    val data by remember(cadence) { repository.data(cadence) }
        .collectAsStateWithLifecycle(remember(cadence) { repository.latest(cadence) })
    val touch = LocalWidgetTouch.current
    val cell = if (id != null && touch != null) remember(touch, id) { touch.cell(id) } else null
    if (id != null && touch != null) DisposableEffect(touch, id) { onDispose { touch.release(id) } }
    Box(
        modifier
            .fillMaxSize()
            .then(WidgetCellInset)
            .then(if (cell == null) Modifier else Modifier.pressWatcher(cell))
            .then(itemGestures),
    ) {
        data?.let { WidgetRender(recipe, it, taps = cell?.targets) }
    }
}

/**
 * Records where each finger comes down on the widget and the widget's own coordinates, for [WidgetTouch] to resolve a
 * tap against. **Watches on the initial pass and consumes nothing**, so the gesture machine below it sees every event
 * exactly as it would without this.
 */
private fun Modifier.pressWatcher(cell: WidgetTouch.Cell): Modifier = this
    .onGloballyPositioned { cell.coordinates = it }
    .pointerInput(cell) {
        awaitEachGesture {
            cell.press = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
        }
    }

/**
 * The margin a widget keeps inside its cell, so two side by side, or one against the screen edge, do not touch.
 *
 * The 8dp is a placeholder until HOME's padding settings own it.
 */
internal val WidgetCellInset: Modifier = Modifier.padding(8.dp)
