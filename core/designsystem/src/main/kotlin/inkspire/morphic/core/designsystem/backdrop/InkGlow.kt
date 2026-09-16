package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The soft glow behind text drawn on a picture: a shadow with no offset, in the current palette's background, so it
 * always opposes the ink and spreads evenly around every glyph.
 *
 * **Constant strength, and it needs no more.** The ink was chosen to contrast the patch under it ([SpotTheme]), and the
 * glow is the *opposite* tone — so where the ink already reads, the glow is close to the patch's own tone and barely
 * shows, and where the spot straddles light and dark it is what separates the letters from the side that fights them.
 * It varies itself by what is behind it, which is the job the sized backing pill did by arithmetic.
 *
 * Read from [LocalMorphicColors], so inside a [SpotTheme] mid-flip it cross-fades with the ink rather than snapping.
 * Not for text on a fill of its own (a selected picker row): the glow would smear the background tone across it.
 */
@Composable
fun inkGlow(): Shadow = Shadow(
    color = LocalMorphicColors.current.background.copy(alpha = 0.9f),
    offset = Offset.Zero,
    blurRadius = with(LocalDensity.current) { 6.dp.toPx() },
)
