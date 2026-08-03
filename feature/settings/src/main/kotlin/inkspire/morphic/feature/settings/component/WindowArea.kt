package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import inkspire.morphic.core.designsystem.grid.GridArea

/**
 * The area a launcher surface actually gets on this device: the window, minus [insets], in dp.
 *
 * **The one place settings measures the screen.** Every bound this module offers — how tall the dock may be, how many
 * columns fit home — is a fraction of this, and a settings screen cannot measure the surface it is configuring, so it
 * measures the *window* the same way that surface does and subtracts the same insets. Sharing one function is what
 * stops two screens quietly disagreeing about how big the phone is.
 *
 * It is also the replacement for L1's `homeGridArea(window, insets, landscape, dockVisible, dockThickness)`, which
 * folded the dock subtraction into the measurement — so every caller had to supply dock facts even when it was sizing
 * something else, and home measured its own bounds a *second* way (`pagerBoundsInWindow`) that could disagree.
 * Subtracting the dock is one caller's arithmetic on the result, not part of measuring a window.
 */
@Composable
internal fun usableWindowArea(insets: WindowInsets): GridArea {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) {
        val insetH = insets.getLeft(density, layoutDirection) + insets.getRight(density, layoutDirection)
        val insetV = insets.getTop(density) + insets.getBottom(density)
        GridArea(
            widthDp = (size.width - insetH).coerceAtLeast(0).toDp().value,
            heightDp = (size.height - insetV).coerceAtLeast(0).toDp().value,
        )
    }
}
