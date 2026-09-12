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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import org.koin.androidx.compose.koinViewModel

/**
 * **Permissions**: every permission the installed package declares, and what each is for.
 *
 * A pane of its own rather than a block on the About screen, which it was first. Down there it was the longest thing
 * on a hub whose job is to point somewhere — five rows of monospace constants under two lines of prose, pushing the
 * documents off the screen. What it is, is a list, and a list is a destination.
 *
 * **The list is Android's answer, not ours.** It is what `PackageManager` reports for this package, in the order it
 * reports it, so it includes the two permissions the platform and the AndroidX libraries attach without anyone here
 * writing them down. That is the property worth having: a reader comparing this against what the system's own app
 * info screen says will find them the same.
 *
 * Shares [AboutViewModel] with the About pane — one `getPackageInfo`, one snapshot, two views of it. A ViewModel of
 * its own would re-ask the same question of the same process for the same answer.
 */
@Composable
internal fun PermissionsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<AboutViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        MorphicGroupPanel {
            state.permissions.forEach { permission -> PermissionRow(permission) }
        }
    }
}

/**
 * One declared permission: its constant in full, what it is for, and whether it is granted.
 *
 * The constant is shown **in full and unprettified** — a shortened one cannot be checked against the manifest or
 * against Android's own app info screen, and checking is what the reader is here to do. It is also the only way the
 * AndroidX-injected signature permission reads honestly, since its prefix is this app's package rather than
 * `android.permission`.
 */
@Composable
private fun PermissionRow(permission: AppPermission) {
    val colors = LocalMorphicColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = permission.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            permission.granted?.let { granted ->
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.contentMuted,
                )
            }
        }
        permission.note?.let { note ->
            Spacer(Modifier.height(4.dp))
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
    }
}
