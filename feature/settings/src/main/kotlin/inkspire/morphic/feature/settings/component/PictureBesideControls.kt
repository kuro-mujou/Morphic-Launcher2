package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A settings pane's **short-window arrangement**: its controls scrolling on the left, and the picture they adjust held
 * on the right, centered in the height.
 *
 * For a phone in landscape, where a picture above the controls leaves them a strip to scroll through — or pins over
 * them — while the width beside it goes unused. The same split `SurfaceDetail` takes for its icon group, with the
 * picture on the same side, so every pane that turns sideways turns the same way. Callers choose it with
 * `isShortWindow`, not `isLandscape`, for that pane's reason: a tablet in landscape is tall.
 *
 * **The picture sizes itself** — a mockup has a width it reads best at, and a cross has the width of its cards. Its
 * column scrolls only if the picture is taller than the pane, and is otherwise centered.
 */
@Composable
internal fun PictureBesideControls(
    picture: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // The bottom gap is the controls' alone: centered, the picture keeps clear of the edge by itself, and the
        // Screen manager's cross needs every dp of a landscape phone's height to fit without scrolling.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            content = controls,
        )
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                picture()
            }
        }
    }
}
