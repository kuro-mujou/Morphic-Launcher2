package inkspire.morphic.feature.settings.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * A row of the setup hub: something the launcher has not been told yet, and the way to tell it.
 *
 * **Not `SettingsNavRow`, and the difference is what each row is *for*.** That one is an index entry — it names a
 * place, and its KDoc argues at length that a second line makes a list of them read as one sentence with the nouns
 * shuffled. This is a call to action that appears because something is undone, so the second line is the part that
 * earns the tap: a row saying only "Set as default launcher" leaves the reader to guess what changes.
 *
 * **A row here is transient by construction.** It is drawn only while its step is unfinished, and the step's
 * doneness is *derived* rather than recorded — locked decision 3 of
 * [docs/ONBOARDING_PLAN.md](../../../../../../../../docs/ONBOARDING_PLAN.md), because a stored checklist is two
 * implementations of one fact kept honest by intention, and it eventually tells a user to do something they have
 * already done.
 *
 * Generic over its content rather than over a step type, because there is one step today and a sealed hierarchy
 * built for one case has nothing to shape it. `O5` is where the steps become a list.
 */
@Composable
internal fun SetupRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.content)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Spacer(Modifier.height(2.dp))
            Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.contentMuted,
        )
    }
}
