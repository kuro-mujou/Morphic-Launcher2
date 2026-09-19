package inkspire.morphic.core.designsystem.pane

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

/**
 * **One pane at a time, pushed aside by the next** — a list that slides out to the left as a detail slides in from the
 * right, and back again. What the settings shell does on a phone and what the widget picker does inside its sheet.
 *
 * **A pane keeps its state while something is pushed over it.** `AnimatedContent` disposes the pane it slides out,
 * and with it every `rememberSaveable` inside — a list's scroll position above all, so backing out of a detail landed
 * at the top of a list the user had scrolled to the bottom of. Each pane is composed inside a
 * [rememberSaveableStateHolder] provider keyed by [key], so what it saved is handed back when it slides in again.
 *
 * **And a pane backed out of forgets it**, as a screen popped off a back stack does: returning to the list drops the
 * detail's saved state, so opening the same detail later starts at its top rather than wherever it was left.
 *
 * @param depth how deep a pane sits — the list 0, a detail 1, a detail's child 2. The slide direction is read from it:
 *   a deeper target pushes in from the right, a shallower one slides back from the left.
 * @param key a stable name for a pane's saved state. Must be saveable in a `Bundle`, which a `String` always is.
 * @param fade crossfades as well as slides, for panes drawn over something that shows through the gap.
 */
@Composable
fun <T> PushPanes(
    target: T,
    depth: (T) -> Int,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    fade: Boolean = false,
    label: String = "pushPanes",
    content: @Composable (T) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val shown = remember { ShownPane(target) }
    SideEffect {
        val previous = shown.value
        if (previous != target) {
            // Backing out: the pane being left is popped, not merely covered.
            if (depth(target) < depth(previous)) holder.removeState(key(previous))
            shown.value = target
        }
    }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = { push(forward = depth(targetState) > depth(initialState), fade = fade) },
        label = label,
    ) { pane ->
        holder.SaveableStateProvider(key(pane)) { content(pane) }
    }
}

/** The pane last shown, for telling a push from a pop. Plain: nothing composes against it. */
private class ShownPane<T>(var value: T)

/** Slides the incoming pane in from the side it lies on, and the outgoing one out the other way. */
private fun push(forward: Boolean, fade: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally { it * sign }
    val exit = slideOutHorizontally { -it * sign }
    return if (fade) (enter + fadeIn()) togetherWith (exit + fadeOut()) else enter togetherWith exit
}
