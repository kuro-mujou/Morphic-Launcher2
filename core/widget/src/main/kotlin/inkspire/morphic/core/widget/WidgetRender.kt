package inkspire.morphic.core.widget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.widgetscript.ScriptData

/**
 * A widget, drawn live: [recipe]'s layers laid out in the space [modifier] gives it, with every formula evaluated
 * against [data].
 *
 * **This is the only renderer, and [data] is the only thing that differs between its callers.** The studio preview
 * passes sample data and a placed widget passes the device's; there is no second "preview" path. An icon's two
 * renderers could disagree and the editor could not show it — here that failure is made impossible rather than
 * guarded against, so a second path must not be added. If one is ever needed, it shares a derivation with this one.
 *
 * Fills what it is given and clips to it: a widget is exactly the size of its cell.
 */
@Composable
fun WidgetRender(recipe: WidgetRecipe, data: ScriptData, modifier: Modifier = Modifier) {
    WidgetOverlap(recipe.layers, data, modifier.fillMaxSize().clipToBounds())
}
