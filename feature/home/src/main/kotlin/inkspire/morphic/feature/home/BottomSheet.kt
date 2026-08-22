package inkspire.morphic.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.wallpaperBackdrop
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.surface.LockSurfaceGesture
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** How much of the screen a sheet takes. L1's fraction, from the widget picker this was extracted from. */
private const val SheetHeightFraction = 0.7f

/** Rounded at the top only, since the bottom is the screen edge. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * A **modal bottom sheet over a launcher surface** — a scrim, a frosted panel, and the modality that makes it one.
 *
 * Extracted from `WidgetPickerSheet` when the icon container's app picker became the second thing needing exactly
 * this chrome, on the extract-at-the-second-consumer rule. It stays in `feature:home` rather than moving to
 * `core:designsystem` because both consumers are here; it moves when a third surface elsewhere wants one.
 *
 * **The modality is one claim, and it buys two behaviors.** `SurfaceGestureLock` is the launcher's answer to "does
 * something on screen own the finger?", so holding it means `SurfacePager` will not slide another surface in from
 * under an open sheet, and `surfaceMenuGestures` stands down so a long-press on the scrim cannot open the menu of
 * the surface buried behind. The declarative form, because the reason is a piece of state — this composable being
 * on screen — rather than an event.
 *
 * @param heightFraction how much of the screen the sheet takes, or **null to size it to its content** — which is
 *   what a sheet with a fixed handful of rows wants. The `Box` bounds it either way, so wrapping cannot grow past
 *   the screen.
 * @param onDismiss a tap on the scrim, and the default for [onBack].
 * @param onBack overridden by a sheet with somewhere to go back *to*: the widget picker's detail pane closes before
 *   the sheet does, so the two panes read as depth rather than as a swap.
 */
@Composable
internal fun LauncherBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onDismiss,
    heightFraction: Float? = SheetHeightFraction,
    content: @Composable ColumnScope.() -> Unit,
) {
    LockSurfaceGesture(locked = true)
    BackHandler(onBack = onBack)

    val colors = LocalMorphicColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // A tap anywhere off the sheet closes it. `detectTapGestures` rather than `clickable` so it stays
            // silent — no ripple and no semantics click on a full-screen scrim, exactly as the context menu's
            // tap-catcher does.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // **Null wraps to the content**, for a sheet whose rows are countable. A fraction is right for the
                // pickers, which are lists of unknown length and want a predictable window — but imposed on a short
                // sheet it is a fixed box that silently clips whatever does not fit, and does so only at the font
                // scales and densities nobody develops at.
                .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                .clip(SheetShape)
                .wallpaperBackdrop(shape = SheetShape, scrimColor = colors.surfaceElevated)
                // Swallows taps on the sheet itself, so they do not reach the scrim behind it and dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .uiInsetsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}
