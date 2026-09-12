package inkspire.morphic.feature.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.component.SettingsNavRow
import org.koin.androidx.compose.koinViewModel

/**
 * **About**: what this build is, and the three panes that answer for it.
 *
 * A **hub**, like Home and Icons — it configures nothing at all, which makes it the only pane in this surface that is
 * pure report. The name and version are read from [android.content.pm.PackageManager] at runtime rather than written
 * down here, so the screen describes the APK the reader actually installed. A hand-written version string is the one
 * thing an about screen must never contain: it is wrong exactly when it matters, on the build somebody is asking
 * about.
 *
 * **One version row and three ways onward, in a single panel.** The package name, the SDK levels and the signing
 * fingerprint were here and are gone: they answered a question nobody had arrived with, and they pushed the three
 * rows that matter below the fold on a phone. What is left is the row a bug report starts with, and then the
 * documents.
 *
 * No app icon, deliberately. Drawing one means `Drawable.toBitmap`, which lives in `core-ktx` — a dependency this
 * module does not have and would be taking on for decoration. The name and version say who this is.
 *
 * @param homeLayout HOME's pairing, which this pane has no use for except to hand to [SettingsNavRow] — see
 *   [AboutPanel].
 * @param onOpenSection opens the permissions, privacy or licenses pane, exactly as the Home hub opens its two zones.
 */
@Composable
internal fun AboutDetail(
    homeLayout: HomeLayout,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<AboutViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.headlineSmall,
            color = LocalMorphicColors.current.content,
        )
        Spacer(Modifier.height(20.dp))
        AboutPanel(state, homeLayout, onOpenSection)
    }
}

/**
 * The version, then the three destinations.
 *
 * **A fact and three nav rows sharing one panel**, which is unusual for this surface and right here: they are one
 * short list rather than two groups, and a panel of its own around a single version row would be a container drawn to
 * separate it from nothing.
 *
 * All three rows go through [SettingsNavRow] with each section's own `meta`, so a row here cannot name a pane
 * differently from the way the app bar titles it — the same reason the Home hub uses it for its zones. None of the
 * three is renamed by HOME's pairing, so the [homeLayout] handed down is inert; it is still the shell's real one
 * rather than a constant picked to satisfy the signature, because a stand-in would read as a claim about HOME that
 * this pane is in no position to make.
 */
@Composable
private fun AboutPanel(
    state: AboutState,
    homeLayout: HomeLayout,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
) {
    MorphicGroupPanel {
        Version(state)
        listOf(SettingsSection.PERMISSIONS, SettingsSection.PRIVACY, SettingsSection.LICENSES).forEach { section ->
            SettingsNavRow(
                section = section,
                homeLayout = homeLayout,
                selected = false,
                showChevron = true,
                onClick = { onOpenSection(section, null) },
            )
        }
    }
}

/**
 * What is running: the version users read, the build number stores and bug reports use, and — only on a debug build —
 * the fact that it is one.
 *
 * The version code is worth its four characters because the two move independently: a hotfix ships as the same 0.1.0
 * with a new code, and it is the code that identifies a build to Play Console.
 *
 * **Built the same way as the rows beside it, rather than measured to match them.** Glyph, gap, title, then the value
 * out at the end where those rows put their chevron — so the panel is four rows sharing one left edge and one type.
 * It was a small grey label stacked over its value first, which read as a heading for the three rows under it; the
 * step after that indented the title by a reconstructed `12 + 24 + 16` to reach their column, which lined up but held
 * the nav row's three numbers in a second file, where a change to its icon or gap would have left this row *silently*
 * out of column. Leading with a real icon removes that: the numbers now sit in the same structural positions in both
 * files rather than being summed in one of them. They are still two sets of literals, which is the exemption bare dp
 * values take here by decision — but a wrong one is now visible as a misplaced glyph instead of a slight crookedness
 * nobody files.
 *
 * **The debug marker is absent rather than inverted**, per the standing rule. A release build is the ordinary case and
 * has nothing to say; a debug build must not be mistakable for one, and this is the screen that would be quoted back
 * if it were.
 */
@Composable
private fun Version(state: AboutState) {
    val colors = LocalMorphicColors.current
    val version = buildString {
        append(state.versionName)
        append(" (")
        append(state.versionCode)
        append(')')
        if (state.debuggable) append("  ·  Debug")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        // A clock with the arrow going round it — the glyph for a thing that advances in time, which is what a
        // version is. Not decorative here: it is what puts this row's title in the same column as the three below.
        Icon(imageVector = Icons.Outlined.Update, contentDescription = null, tint = colors.content)
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Version",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = version, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
    }
}
