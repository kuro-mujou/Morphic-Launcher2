package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.designsystem.component.press.repeatingPress
import inkspire.morphic.core.model.icon.PreviewBackground

/** Every icon button in the studio is this wide, so a rail of them lines up without each caller choosing a number. */
private val ButtonSide = 40.dp

/** The glyph inside one. Short of the button's own side, so the press target is larger than what it shows. */
private val GlyphSide = 20.dp

/** The bottom container's height. A resting bar, not a panel — what it holds decides how tall it grows later. */
private val BottomBarHeight = 56.dp

/** How far the bottom container and the chrome above it stay clear of the screen's sides. */
internal val ChromeMargin = 12.dp

/**
 * How tall the top row of chrome is — back on one side, history and save on the other.
 *
 * Exported because the **workspace begins below it**: the icon's resting bound and the layer rail's resting head are
 * both placed against this, and they line up only because they are placed against the *same* number. It is
 * [ButtonSide] and must stay so; the pills are one button tall, and a second constant beside it would be that fact
 * written down twice.
 */
internal val StudioTopChromeHeight = ButtonSide

/** Between the chrome and the work it sits above — enough that the icon is not touching the buttons. */
internal val WorkspaceGap = 8.dp

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
    StudioButtonFace(
        icon = icon,
        contentDescription = contentDescription,
        enabled = enabled,
        selected = selected,
        modifier = modifier,
        interaction = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * A [StudioIconButton] that **keeps stepping while it is held down**.
 *
 * For a button whose job is to move a value by a little: tapping it once is a correction, and wanting twenty of them
 * is wanting to hold it, not to tap twenty times. That is the whole of what this adds — the look and the disabled
 * treatment are the plain button's.
 *
 * **A hold is one edit, not thirty**, which is why this takes two callbacks rather than one. [onStep] runs per fire
 * and must not commit; [onStepsFinished] runs once when the finger lifts. That is deliberately the same shape a slider
 * takes (`onValueChange` / `onValueChangeFinished`) and it is what keeps undo stepping *over* a hold rather than back
 * through every repeat inside it — the studio's rule for any control that can be dragged.
 *
 * **The repeat itself is `Modifier.repeatingPress`**, shared with the settings sliders since they wanted the same
 * behavior: the two thresholds, the fire-on-press and the callback pairing are all there, and what stays here is the
 * glass face they are attached to.
 *
 * **It fires on the press rather than on the release**, unlike every other button here, because a repeating control
 * that waits for the release would show nothing for the whole first tap. That also removes the double-fire a naive
 * "clickable plus a repeat timer" produces, where the release adds one more step after the value already looked right.
 *
 * The first repeat waits the platform's own long-press timeout, so an ordinary tap can never become two.
 */
@Composable
fun StudioStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Unit,
    onStepsFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    StudioButtonFace(
        icon = icon,
        contentDescription = contentDescription,
        enabled = enabled,
        selected = false,
        modifier = modifier,
        interaction = Modifier.repeatingPress(
            interactionSource = interactionSource,
            enabled = enabled,
            onStep = onStep,
            onStepsFinished = onStepsFinished,
        ),
    )
}

/** The shared look, so a stepper cannot drift a size or a dimmed alpha away from the plain button. */
@Composable
private fun StudioButtonFace(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier,
    interaction: Modifier,
) {
    Box(
        modifier = modifier
            .size(ButtonSide)
            .clip(CircleShape)
            .background(if (selected) StudioContentColor.copy(alpha = SelectedWashAlpha) else Color.Transparent)
            .then(interaction),
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

/** How fast a held stepper repeats once it starts. Fast enough to cross a range, slow enough to stop on a value. */

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
 * The two controls that act on **how the work is shown** rather than on what it is: the canvas backdrop, and putting
 * the preview back where it started.
 *
 * **One pill for the pair, on [StudioHistoryButtons]' exact argument.** Those two share a surface because a history
 * has a direction and two separate buttons invite reading them as unrelated; these two share one because neither
 * touches the recipe — they are the *view*, where the button beside them ("which app am I looking at?") chooses the
 * subject. Grouping is what says which of the three questions each answers, and it is the cheaper blur besides:
 * [studioSurface] is a Haze effect per node, so a pair sharing one samples the canvas once.
 *
 * **The reset takes the position pad's own center glyph, deliberately.** In `PositionPad` it means "put this back in
 * the middle" about a *layer*; here it means the same about the *view*. One verb, two subjects — and both are disabled
 * once there is nothing to undo, so the glyph carries the same promise in both places rather than being reused for its
 * looks.
 *
 * @param canResetView whether the preview has been panned or zoomed at all. Dimmed rather than absent, per this file's
 *   rule: its availability changes as the canvas is dragged, and a control that came and went under the finger using
 *   it would be worse than a gray one — and gray still says the view *can* be put back.
 */
@Composable
fun StudioViewButtons(
    background: PreviewBackground,
    canResetView: Boolean,
    hazeState: HazeState,
    onCycleBackground: () -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.studioSurface(hazeState, shape = CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The swatch is smaller than a glyph slot and stays so — it is a *picture* of a backdrop, and at 40dp it would
        // read as a tile rather than as a button's contents. Centered in a full-size cell instead, so the pair lines
        // up and both halves have the same press target.
        Box(
            modifier = Modifier.size(ButtonSide),
            contentAlignment = Alignment.Center
        ) {
            BackgroundCycleButton(
                background = background,
                onClick = onCycleBackground
            )
        }

        StudioIconButton(
            icon = Icons.Default.CenterFocusStrong,
            contentDescription = "Reset the preview's position and zoom",
            enabled = canResetView,
            onClick = onResetView,
        )
    }
}

/**
 * The studio's tool rail: one entry per [StudioTool], and pressing the selected one again puts it away.
 *
 * **Toggling rather than always-one-selected**, because the canvas is the work: a rail with no way back to "just the
 * icon" would mean a panel permanently covering a third of what is being edited. Null is a real state, and it is what
 * the studio opens in.
 *
 * **It shows the tools that apply to what is selected**, which is why it takes a list rather than reading
 * `StudioTool.entries` itself — see [StudioTool.appliesTo]. The bar wraps its contents, so a shorter one is a
 * narrower bar rather than a bar with holes in it.
 *
 * @param tools the entries to draw, in order.
 * @param selected the section whose panel is showing, or null for none.
 */
@Composable
fun StudioToolBar(
    hazeState: HazeState,
    tools: List<StudioTool>,
    selected: StudioTool?,
    onSelect: (StudioTool?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioBottomBar(hazeState = hazeState, modifier = modifier) {
        tools.forEach { tool ->
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
 * The bar the studio's tools live on: a sheet of the shared glass at the bottom of the screen, **as wide as what it
 * holds**.
 *
 * It filled the width until the stack moved to its own rail and the bar dropped to six entries — at which point the
 * glass ran edge to edge with a margin of nothing at each end, so the bar read as a surface the tools happened to sit
 * on rather than as the tools' own container. Wrapping is also what keeps that true as the set changes: a bar sized
 * to its contents cannot develop dead space, where a full-width one develops it every time an entry leaves.
 *
 * Separate from [StudioToolBar] so the container and its contents stay independent — the bar settles the material and
 * where the bottom edge of the workspace is, which is what every panel above it will inherit, and it is the piece a
 * future landscape arrangement re-points at a side rail.
 *
 * It does **not** apply `uiInsetsPadding` itself. The bar and whatever floats above it are one stack in the caller's
 * `Column`, and insetting each separately would inset the gap between them twice.
 *
 * @param content laid out in a row; a rail of [StudioIconButton]s is what this is shaped for.
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
            .height(height)
            .studioSurface(hazeState, shape = shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
