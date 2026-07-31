package inkspire.morphic.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Provisional spacing — placeholders until the settings port brings real row components with it. */
private val ScreenPadding = 20.dp
private val RowPadding = 16.dp

/**
 * The settings surface — **a prepared destination, not the finished screen.**
 *
 * It exists now so that navigation is real and reachable before the port that fills it: `SettingsRoute` resolves to
 * something, back works, and the L1 settings feature has somewhere to land one group at a time rather than arriving
 * as one 6.5k-line drop.
 *
 * **Its own theme boundary, and that is the point.** `darkTheme` comes from [isSystemInDarkTheme] here, while the
 * launcher shell feeds its theme a *wallpaper-brightness* signal instead — settings is our own surface, so it follows
 * the system, whereas launcher chrome has to contrast whatever wallpaper is behind it. Two "is-dark" inputs, one
 * palette; see the design-system notes. This is also the fix for L1 wrapping one `LauncherTheme` around its entire
 * `NavDisplay`, which left settings and the launcher unable to disagree.
 *
 * **What is deliberately absent.** No sections, no groups, no taxonomy. L1 has 11 `SettingsSection` values, a
 * list↔detail two-pane host, and four more full-screen destinations (icon studio, wallpaper capture/crop, design
 * gallery) — inventing that structure before any of it is ported would be deciding the shape from the outside. Two
 * things about L1's version are worth knowing *before* porting, since they are what to do differently:
 * - Its sections were **not** back-stack entries, so it ended up with two incompatible back mechanisms stitched
 *   together by hand (`if (selected != null) closeDetail() else navigator.goBack()`) and section state preserved
 *   through a hand-written `Saver` instead of the stack. Making each section a real destination — or letting an
 *   adaptive scaffold own its pane back stack — removes all of that.
 * - Its two largest section screens are 705 and 559 lines of one flat `Column`. Port by group, not by file.
 *
 * @param onBack leaves the surface. Wired to the navigator by the host, and to system back here so the two agree.
 * @param extraEntries additional rows to show, as label → action. The seam the dev harness arrives through: `app`
 *   owns that destination (it is not a product screen), so it passes the row rather than this module importing a key
 *   it has no business knowing. Empty in any build that has no such screens.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraEntries: List<Pair<String, () -> Unit>> = emptyList(),
) {
    BackHandler(onBack = onBack)

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(safeInsets)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.content,
            )
            Text(
                text = "Nothing here yet — the L1 settings port lands one group at a time.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                modifier = Modifier.padding(top = RowPadding / 2),
            )

            extraEntries.forEach { (label, onClick) ->
                // A plain `clickable`, unlike every launcher-surface item: the shared `launcherItemGestures`
                // contract exists so long-press timing and slop can't drift *between launcher surfaces*, and its
                // "touch target is its visible extent" rule is about grid cells. Settings is ordinary app chrome
                // with ordinary rows, and it should behave like the platform, not like a home-screen icon.
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(top = RowPadding)
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(vertical = RowPadding / 2),
                )
            }
        }
    }
}
