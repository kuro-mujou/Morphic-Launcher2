package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.component.slider.Morphic2DPad
import inkspire.morphic.core.designsystem.component.slider.MorphicSlider
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors

/**
 * Dev-only gallery for the in-house design system: every `Morphic*` component plus the [MorphicColors]
 * palette, under a light/dark toggle so we can eyeball both schemes. Not shipped UI — it stands in for the
 * home screen until the real surfaces land, and each new component is added here as it is built.
 */
@Composable
fun DevGalleryScreen(modifier: Modifier = Modifier) {
    var dark by remember { mutableStateOf(true) }

    LauncherTheme(darkTheme = dark) {
        val colors = LocalMorphicColors.current
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Design System", color = colors.content, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "tap to switch to ${if (dark) "Light" else "Dark"}",
                color = colors.contentMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable { dark = !dark }
                    .padding(vertical = 8.dp),
            )

            SectionHeader("Buttons", colors)
            ButtonsDemo()

            SectionHeader("Sliders", colors)
            SlidersDemo()

            SectionHeader("2D pad", colors)
            PadDemo()

            SectionHeader("Text field", colors)
            TextFieldDemo()

            SectionHeader("Palette", colors)
            colorRoles(colors).forEach { (name, color) ->
                Swatch(name, color, colors)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ButtonsDemo() {
    val colors = LocalMorphicColors.current
    var segment by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Built on M3 — monochrome + Expressive shape-morph on press",
            color = colors.contentMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorphicButton(onClick = {}, style = MorphicButtonStyle.Filled) { Text("Filled") }
            MorphicButton(onClick = {}, style = MorphicButtonStyle.Tonal) { Text("Tonal") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorphicButton(onClick = {}, style = MorphicButtonStyle.Outlined) { Text("Outlined") }
            MorphicButton(onClick = {}, style = MorphicButtonStyle.Text) { Text("Text") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorphicButton(onClick = {}, style = MorphicButtonStyle.Elevated) { Text("Elevated") }
            MorphicButton(onClick = {}, enabled = false) { Text("Disabled") }
        }
        MorphicSegmentedButtons(
            options = listOf("All", "Individual"),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SlidersDemo() {
    val colors = LocalMorphicColors.current
    var basic by remember { mutableStateOf(0.4f) }
    var ranged by remember { mutableStateOf(0f) }
    var stepped by remember { mutableStateOf(0.5f) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Built on M3 — monochrome, native grab animation · basic / range / stepped",
            color = colors.contentMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        MorphicSlider(value = basic, onValueChange = { basic = it })
        MorphicSlider(value = ranged, onValueChange = { ranged = it }, valueRange = -1f..1f)
        MorphicSlider(value = stepped, onValueChange = { stepped = it }, steps = 4)
    }
}

@Composable
private fun PadDemo() {
    val colors = LocalMorphicColors.current
    var x by remember { mutableStateOf(0f) }
    var y by remember { mutableStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Morphic 2D pad — drag the knob (X →, Y ↑)",
            color = colors.contentMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Morphic2DPad(
            x = x,
            y = y,
            onValueChange = { nx, ny -> x = nx; y = ny },
            modifier = Modifier.size(180.dp),
        )
        Text(
            "x = ${"%.2f".format(x)}   y = ${"%.2f".format(y)}",
            color = colors.content,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TextFieldDemo() {
    val colors = LocalMorphicColors.current
    var name by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf("bad name") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Morphic text field (settings) — normal + error",
            color = colors.contentMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        MorphicTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Folder name",
            modifier = Modifier.fillMaxWidth(),
        )
        MorphicTextField(
            value = invalid,
            onValueChange = { invalid = it },
            placeholder = "Name",
            isError = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionHeader(title: String, colors: MorphicColors) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = colors.content, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Swatch(name: String, color: Color, colors: MorphicColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, colors.outline, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, color = colors.content, style = MaterialTheme.typography.bodyMedium)
            Text(hex(color), color = colors.contentMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Role name → colour, in a stable display order, for the palette listing. */
private fun colorRoles(c: MorphicColors): List<Pair<String, Color>> = listOf(
    "background" to c.background,
    "surface" to c.surface,
    "surfaceElevated" to c.surfaceElevated,
    "scrim" to c.scrim,
    "content" to c.content,
    "contentMuted" to c.contentMuted,
    "contentDisabled" to c.contentDisabled,
    "accent" to c.accent,
    "onAccent" to c.onAccent,
    "accentMuted" to c.accentMuted,
    "outline" to c.outline,
    "divider" to c.divider,
    "trackInactive" to c.trackInactive,
    "trackActive" to c.trackActive,
    "thumb" to c.thumb,
    "focusRing" to c.focusRing,
    "error" to c.error,
    "onError" to c.onError,
)

private fun hex(color: Color): String = "#%08X".format(color.toArgb())
