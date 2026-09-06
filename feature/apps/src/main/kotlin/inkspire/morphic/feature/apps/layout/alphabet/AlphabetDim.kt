package inkspire.morphic.feature.apps.layout.alphabet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades this item back while an [AlphabetStrip] scrub is pointing somewhere else.
 *
 * **The other half of the strip's answer, and the reason it does not need to filter.** Scrolling alone puts the
 * letter's run at the top of a list that looks exactly like it did a moment ago; dimming everything outside the run
 * is what makes the arrival legible. L1 dimmed to the same 0.3 and called it `alphabetFocus`.
 *
 * Animated so a scrub across several letters reads as a wash moving down the list rather than as rows blinking, and
 * applied through a `graphicsLayer` rather than `Modifier.alpha` for the same reason every other per-frame value on
 * this surface is: it is a draw property, and a draw property should not invalidate a layout.
 */
@Composable
internal fun Modifier.alphabetDim(dimmed: Boolean): Modifier {
    val alpha by animateFloatAsState(if (dimmed) 0.3f else 1f, label = "alphabetDim")
    return graphicsLayer { this.alpha = alpha }
}
