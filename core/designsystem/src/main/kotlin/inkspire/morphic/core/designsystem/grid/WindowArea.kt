package inkspire.morphic.core.designsystem.grid

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The area a launcher surface actually gets on this device: the window, minus [insets], in dp.
 *
 * **The one place the launcher measures the screen**, and it lives beside [GridArea] because that is what it answers
 * in. Every bound derived from a screen — how tall the dock may be, how many columns fit home, what capacity an APPS
 * page has — is a fraction of this, and the three kinds of caller need it to agree: a *surface* laying itself out, a
 * *settings screen* bounding what the user may choose for that surface, and a *ViewModel* being told the capacity its
 * store must paginate against. A settings screen cannot measure the surface it is configuring, so it measures the same
 * window the same way instead; sharing one function is what stops two of them quietly disagreeing about how big the
 * phone is.
 *
 * It is also the replacement for L1's `homeGridArea(window, insets, landscape, dockVisible, dockThickness)`, which
 * folded the dock subtraction into the measurement — so every caller had to supply dock facts even when it was sizing
 * something else, and home measured its own bounds a *second* way (`pagerBoundsInWindow`) that could disagree.
 * Subtracting the dock is one caller's arithmetic on the result, not part of measuring a window.
 *
 * @param insets what the surface will not draw into — normally `systemBars ∪ displayCutout`, which is what every
 *   surface here applies. Passed rather than read, because *which* insets a surface honours is the surface's business.
 */
@Composable
fun usableWindowArea(insets: WindowInsets): GridArea {
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
