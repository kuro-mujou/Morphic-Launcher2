package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Every icon button in the studio is this wide, so a rail of them lines up without each caller choosing a number. */
private val ButtonSide = 40.dp

/** The glyph inside one. Short of the button's own side, so the press target is larger than what it shows. */
private val GlyphSide = 20.dp

/** The bottom container's height. A resting bar, not a panel — what it holds decides how tall it grows later. */
private val BottomBarHeight = 56.dp

/** How far the bottom container and the chrome above it stay clear of the screen's sides. */
internal val ChromeMargin = 12.dp

/**
 * How far a selected entry's wash carries. The same value the layer rows use, so "this is the one being edited" looks
 * the same wherever it is said.
 */
private const val SelectedWashAlpha = 0.16f

/**
 * One icon button on the shared studio material.
 *
 * **Disabled means dimmed, not absent**, unlike the settings sections' rule that a control changing nothing should not
 * be drawn. Undo and redo are the case that rule does not cover: their availability changes with every edit, so
 * removing them would make the rail's contents jump around under the finger that is using it — and a dimmed arrow
 * still says the history exists and which way it runs.
 *
 * @param selected draws the wash behind the glyph. Only meaningful for a button that puts the studio into a state —
 *   a bar entry — rather than one that acts and is done.
 */
@Composable
fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(ButtonSide)
            .clip(CircleShape)
            .background(if (selected) StudioContentColor.copy(alpha = SelectedWashAlpha) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = StudioContentColor.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(GlyphSide),
        )
    }
}

/**
 * One icon button on a pill of its own — the shape every standalone action in the studio takes.
 *
 * Extracted at its third consumer (back, save, and the subject button), which is this codebase's usual point: two
 * copies of `Box(studioSurface(…)) { StudioIconButton(…) }` is a coincidence, three is a component that would otherwise
 * drift a corner or a padding at a time.
 */
@Composable
fun StudioPillButton(
    icon: ImageVector,
    contentDescription: String,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier = modifier.studioSurface(hazeState, shape = CircleShape)) {
        StudioIconButton(
            icon = icon,
            contentDescription = contentDescription,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

/**
 * Undo and redo, as one pill of glass rather than two tiles.
 *
 * **One surface for the pair because they are one control** — a history has a direction, and two separate buttons
 * invite reading them as two unrelated actions. It is also the cheaper blur: [studioSurface] is a Haze effect per
 * node, so a pair sharing one costs one sample of the canvas instead of two.
 */
@Composable
fun StudioHistoryButtons(
    hazeState: HazeState,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.studioSurface(hazeState, shape = CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo)
        StudioIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, enabled = canRedo)
    }
}

/**
 * The studio's tool rail: one entry per [StudioTool], and pressing the selected one again puts it away.
 *
 * **Toggling rather than always-one-selected**, because the canvas is the work: a rail with no way back to "just the
 * icon" would mean a panel permanently covering a third of what is being edited. Null is a real state, and it is what
 * the studio opens in.
 *
 * @param selected the section whose panel is showing, or null for none.
 */
@Composable
fun StudioToolBar(
    hazeState: HazeState,
    selected: StudioTool?,
    onSelect: (StudioTool?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioBottomBar(hazeState = hazeState, modifier = modifier) {
        StudioTool.entries.forEach { tool ->
            StudioIconButton(
                icon = tool.icon,
                contentDescription = tool.label,
                selected = tool == selected,
                onClick = { onSelect(tool.takeIf { it != selected }) },
            )
        }
    }
}

/**
 * The bar the studio's tools live on: a full-width sheet of the shared glass at the bottom of the screen.
 *
 * Separate from [StudioToolBar] so the container and its contents stay independent — the bar settles the material and
 * where the bottom edge of the workspace is, which is what every panel above it will inherit, and it is the piece a
 * future landscape arrangement re-points at a side rail.
 *
 * It does **not** apply `uiInsetsPadding` itself. The bar and whatever floats above it are one stack in the caller's
 * `Column`, and insetting each separately would inset the gap between them twice.
 *
 * @param content laid out in a centered row; a rail of [StudioIconButton]s is what this is shaped for.
 */
@Composable
fun StudioBottomBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    height: Dp = BottomBarHeight,
    content: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .studioSurface(hazeState, shape = shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
