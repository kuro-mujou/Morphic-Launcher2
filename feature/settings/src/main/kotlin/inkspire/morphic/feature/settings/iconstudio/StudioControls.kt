package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource

/*
 * The vocabulary every studio section is written in — a labeled block, a chip, and the words a layer is named by.
 *
 * **The sections themselves are one file each** (`StudioLayers`, `StudioSource`, `StudioTransform`, `StudioShape`,
 * `StudioEffects`, `StudioPresets`), which is what this file is left over from: they were one `StudioSections.kt`
 * and it reached 1200 lines, at which point "which section is this?" was a scroll rather than a filename. What
 * stays shared is only what more than one section says, and it lives here so a second copy of a chip cannot appear
 * — the same reason `IconPreviewPlate` and `AppPicker` were extracted rather than repeated.
 *
 * A section emits controls and nothing else: no surface, no title, no scroll. Those belong to the host, which is the
 * only thing that knows a section is one of several — so a new section cannot arrive with its own idea of what a panel
 * looks like, and the host can rearrange them (a side rail in landscape) without touching one.
 *
 * There is no "this layer / whole icon" scope toggle, and that is a simplification the model earned rather than a
 * decision taken here. L1's editor mixed per-layer tools (transform, color, shadow) with whole-icon ones (icon shape,
 * background, theming, size, skin, pack) in one flat row, and its UI plan left the split as an open question. In L2
 * every one of those whole-icon tools has already gone somewhere else: the tile shape became a *per-layer* shape (there
 * is no stack-level mask), the background is the background layer's source, theming is `AppDefaultMonochrome` on the
 * foreground, sizing is `data:settings` and a different screen entirely, the skin is deferred, and an icon pack **is**
 * a per-layer source. So every section but Presets and More acts on one layer, and the question does not arise.
 */

@Composable
internal fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = StudioContentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChip(label = label, selected = selected, modifier = Modifier.fillMaxWidth(), onClick = onClick)
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        color = StudioContentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** What a row calls the layer. The role, not the index — "layer 2" tells the user nothing. */
internal val LayerRole.label: String
    get() = when (this) {
        LayerRole.FOREGROUND -> "Foreground"
        LayerRole.BACKGROUND -> "Background"
        LayerRole.CUSTOM -> "Layer"
    }

/** The one-line summary of where a layer's pixels come from, shown beside its role. */
internal val LayerSource.label: String
    get() = when (this) {
        // What a freshly added layer says about itself, and it has to say *something*: the row is the only place an
        // empty layer is visible at all, since it draws nothing on the canvas.
        LayerSource.Empty -> "empty"
        LayerSource.AppDefault -> "app default"
        LayerSource.AppDefaultMonochrome -> "monochrome"
        is LayerSource.CustomImage -> "image"
        is LayerSource.SolidFill -> "solid color"
        is LayerSource.IconPack -> "icon pack"
    }
