package inkspire.morphic.core.designsystem.insets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Every side, for [uiInsetsPadding]'s default — the whole of [uiInsets] rather than a slice of it. */
private val AllSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical

/**
 * **System bars ∪ display cutout** — the area a surface must not draw its content into, and the one this launcher
 * honors everywhere.
 *
 * Deliberately **not** `WindowInsets.safeDrawing`, which also reserves the keyboard: nothing on a launcher surface
 * takes text, so padding for an IME that will never appear would leave a permanent gap. Equally deliberately not
 * `systemBars` alone — a punch-hole or notch is not a system bar, and a grid measured without it puts a column of
 * icons under the camera in landscape.
 *
 * Ported from L1's `uiInsets`, which L2 had not carried: the same expression was instead written out longhand in
 * eleven files (home, all five APPS layouts, the folder overlay, four settings sections), which is how a launcher ends
 * up with one surface honoring the cutout and its settings screen not.
 */
val uiInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * Pads by [uiInsets] on [sides], honoring whatever an ancestor has already consumed.
 *
 * `@Composable` rather than L1's `composed { }` — that factory is deprecated, defeats modifier reuse, and was only
 * ever needed because [uiInsets] has to be read in composition.
 *
 * @param sides which edges to reserve. Rarely all of them: a pane beside another pane must not inset the edge its
 *   neighbor already covers, and a screen with a top app bar has had its top edge covered by that bar.
 */
@Composable
fun Modifier.uiInsetsPadding(sides: WindowInsetsSides = AllSides): Modifier =
    windowInsetsPadding(uiInsets.only(sides))
