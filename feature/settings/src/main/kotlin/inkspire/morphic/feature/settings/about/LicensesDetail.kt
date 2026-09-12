package inkspire.morphic.feature.settings.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import org.koin.androidx.compose.koinViewModel

/**
 * **Open-source licenses**: every library this APK ships, and the licenses they are offered under.
 *
 * The list is generated at build time from the application's resolved dependency graph — nothing on this screen is
 * typed by hand, which is the only reason it is worth showing at all. A hand-kept list of libraries is wrong from the
 * first dependency added after it was written, and wrong *silently*: it looks exactly as complete as a true one.
 *
 * **Licenses first, then libraries**, which follows the shape of the data rather than the shape of the reference
 * screens. The manifest deduplicates a license across every artifact offered under it, and 144 libraries resolve to
 * two licenses here — so the full texts belong at the top, once, with each library naming which of them applies. The
 * alternative is the same eleven thousand characters of Apache text reachable from 140 separate rows.
 *
 * **A `LazyColumn`, unlike every other pane in this surface**, which are `Column`s that scroll. This one has a few
 * hundred rows rather than a dozen controls, and it is the only pane where laziness saves anything.
 */
@Composable
internal fun LicensesDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<LicensesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
    ) {
        item(key = "lede") { LicensesLede(state) }

        if (state.index.licenses.isNotEmpty()) {
            item(key = "licenses") {
                SettingsSectionHeader("Licenses in use")
                MorphicGroupPanel {
                    state.index.licenses.forEach { license -> LicenseRow(license) }
                }
            }
        }

        if (state.index.libraries.isNotEmpty()) {
            item(key = "libraries-header") { SettingsSectionHeader("Libraries") }
            // Not wrapped in one panel: a rounded container around several hundred rows is a rectangle whose corners
            // nobody ever sees, and it would clip the whole list rather than a group.
            items(state.index.libraries, key = { it.coordinates }) { library -> LibraryRow(library) }
        }
    }
}

/**
 * The claim the list makes, with the count it makes it about.
 *
 * The count is read off the parsed list rather than written down, for the same reason everything else on these two
 * panes is: a number in prose is a number that stops being true without anyone noticing.
 */
@Composable
private fun LicensesLede(state: LicensesState) {
    val colors = LocalMorphicColors.current
    val text = when {
        state.loading -> "Reading the list..."
        state.failed -> "The open-source list could not be read from this build. That is a fault in the app, not " +
            "an empty list: Morphic ships open-source libraries either way, and they are named in the source."
        else -> "Morphic Launcher is built on ${state.index.libraries.size} open-source libraries. This list is " +
            "generated from the app's own dependency graph each time it is built, so it names exactly what ships."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.contentMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * One license, expanding to its full text.
 *
 * Expansion rather than a destination: the settings surface nests exactly one level deep by design, and this pane is
 * already the second. A third would need a back stack the shell does not have — see `SettingsSection.parent`.
 *
 * The text scrolls **horizontally** as well, and that is not decoration: license texts are hard-wrapped at 80-odd
 * columns by their authors, and re-wrapping them at phone width turns a legal document into ragged soup. So the
 * original line breaks are kept and the line is allowed to overflow sideways.
 */
@Composable
private fun LicenseRow(license: OpenSourceLicense) {
    val colors = LocalMorphicColors.current
    var expanded by rememberSaveable(license.id) { mutableStateOf(false) }
    val text = license.text

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Only where there is something to reveal: a license whose text the build excluded expands to
                // nothing, and a control that opens an empty box is worse than no control.
                .then(if (text != null) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = license.name, style = MaterialTheme.typography.bodyLarge, color = colors.content)
                license.url?.let { url ->
                    Text(text = url, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            }
            if (text != null) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.contentMuted,
                )
            }
        }
        AnimatedVisibility(visible = expanded && text != null) {
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.contentMuted,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * One library: what it is called, which artifact it is, who publishes it and under what license.
 *
 * **Tapping opens the library's own page**, which is the point of listing it — a name a reader cannot go and check is
 * a name they have to take on trust, and taking things on trust is what this screen is trying to replace. Every
 * artifact in the current graph publishes a website in its POM; one that did not would simply not be clickable, which
 * is the standing rule about controls that do nothing.
 */
@Composable
private fun LibraryRow(library: OpenSourceLibrary) {
    val colors = LocalMorphicColors.current
    val context = LocalContext.current
    val website = library.website

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (website != null) {
                    Modifier.clickable {
                        // A device with no browser is rare and entirely possible, and a settings screen must not
                        // crash because one library row was tapped on it.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website))) }
                            .onFailure { if (it !is ActivityNotFoundException) throw it }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(text = library.name, style = MaterialTheme.typography.bodyLarge, color = colors.content)
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOfNotNull(library.coordinates, library.version).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.contentMuted,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            library.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentMuted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = library.licenses.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
            )
        }
    }
}
