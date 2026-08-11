package inkspire.morphic.feature.settings.iconstudio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.field.MorphicTextField

/**
 * Browse one icon pack's drawables and pick one for the app being edited.
 *
 * **Individual mode only** — a named drawable on the global default would be inherited by every app, so there is
 * nothing for it to mean there. See `PackBrowse`.
 *
 * The list comes from the pack's own `appfilter.xml`, which was going to need a separate "drawable lister" until
 * it turned out that file's *values* are drawable names: browsing is a projection of data the pack already loads.
 * What that leaves out is drawables the author shipped but mapped to no app, and the categories a `drawable.xml`
 * would carry — both additive, neither blocking.
 *
 * @param loadPreview one cell's thumbnail. Suspending and per-cell on purpose: a pack maps hundreds to thousands
 *   of drawables, so they are decoded **only as they scroll into view** and cached in the manager beneath, rather
 *   than a list of bitmaps being built up front for a grid that will show forty of them.
 */
@Composable
fun PackDrawablePicker(
    browse: PackBrowse,
    loadPreview: suspend (packPackage: String, drawableName: String) -> Bitmap?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchState = rememberTextFieldState()
    val query by remember { derivedStateOf { searchState.text.toString().trim().lowercase() } }
    // Plain `contains` here, unlike `AppPicker`'s collator: a drawable name is an author's identifier — ASCII,
    // lower case, underscore-separated — not a human-language label, so there are no accents to fold.
    val matches = remember(browse.names, query) {
        if (query.isEmpty()) browse.names else browse.names.filter { it.contains(query) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            // "0 icons" while the pack's mapping is still being parsed reads as a broken browser rather than as a
            // slow one — the same distinction `AppPicker` draws between nothing matching and nothing arrived yet.
            text = if (browse.names.isEmpty()) "Reading pack…" else "${browse.names.size} icons",
            color = StudioContentColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        MorphicTextField(
            state = searchState,
            placeholder = "Search icons",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 64.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(matches, key = { it }) { name ->
                PackDrawableCell(
                    packPackage = browse.packPackage,
                    drawableName = name,
                    loadPreview = loadPreview,
                    onClick = { onPick(name) },
                )
            }
        }
    }
}

/**
 * One drawable in the grid.
 *
 * `produceState` keyed on the drawable, so the decode **cancels when the cell scrolls away** — which is what keeps
 * a fast flick through a few thousand icons from queueing a few thousand decodes.
 */
@Composable
private fun PackDrawableCell(
    packPackage: String,
    drawableName: String,
    loadPreview: suspend (packPackage: String, drawableName: String) -> Bitmap?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, packPackage, drawableName) {
        value = loadPreview(packPackage, drawableName)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // No placeholder art while it loads: an empty square is quieter than a grid of flashing boxes, and the
        // decode is a resource lookup rather than a network call.
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = drawableName, modifier = Modifier.fillMaxSize())
        }
    }
}
