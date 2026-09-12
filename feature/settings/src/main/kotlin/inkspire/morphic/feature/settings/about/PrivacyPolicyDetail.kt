package inkspire.morphic.feature.settings.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * **Privacy policy**: the launcher's, rendered from [PrivacyPolicy] rather than from a web page.
 *
 * **In the app rather than behind a link**, which is the only rendering consistent with what the document says: an
 * app that cannot open a network connection should not answer its own privacy question with "go online and read it".
 * It is also the only one that works on a device with no connection, in the launcher's own theme, at the reader's own
 * text size. The hosted copy at the repo root still exists, because Play Console asks for a URL — and it is generated
 * from this same document, not written twice.
 *
 * A renderer and nothing else: it has no ViewModel and no state, because a document that changes only when the source
 * is edited has nothing to hold.
 */
@Composable
internal fun PrivacyPolicyDetail(modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "Effective ${PrivacyPolicy.effective}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentMuted,
        )
        Spacer(Modifier.height(12.dp))

        // The lede on a panel, which is the same container the settings index puts a group of rows on. It is the
        // claim everything else qualifies, and it is the part a reader who reads nothing else will read.
        MorphicGroupPanel {
            Text(
                text = PrivacyPolicy.lede,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                modifier = Modifier.padding(16.dp),
            )
        }

        PrivacyPolicy.blocks.forEach { block -> PolicyBlockText(block) }
    }
}

/** One block of the document, in the type its kind calls for. */
@Composable
private fun PolicyBlockText(block: PolicyBlock) {
    val colors = LocalMorphicColors.current
    when (block) {
        is PolicyBlock.Heading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )

        is PolicyBlock.Subheading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleSmall,
            color = colors.content,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        is PolicyBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = 8.dp),
        )

        is PolicyBlock.Bullets -> Column(Modifier.padding(top = 8.dp)) {
            block.items.forEach { bullet -> PolicyBulletText(bullet) }
        }

        is PolicyBlock.Contact -> ContactText(block)
    }
}

/**
 * The contact line, with the address itself opening a mail app.
 *
 * `ACTION_SENDTO` with a `mailto:` URI rather than `ACTION_SEND`: the former resolves only to things that actually
 * send mail, where the latter offers every app that can share text and makes "write to us about privacy" a share
 * sheet. A device with no mail app resolves neither, which is why the launch is guarded — this pane must not be able
 * to crash on the one row in it that does anything.
 */
@Composable
private fun ContactText(block: PolicyBlock.Contact) {
    val colors = LocalMorphicColors.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text = block.text, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        Spacer(Modifier.width(6.dp))
        Text(
            text = block.email,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${block.email}")))
                }.onFailure { if (it !is ActivityNotFoundException) throw it }
            },
        )
    }
}

/**
 * A bullet, with its lead-in set in the heavier weight.
 *
 * The marker is a `Text` in its own column rather than a character prepended to the sentence, so a bullet that wraps
 * stays hanging-indented instead of running back under its own dot.
 */
@Composable
private fun PolicyBulletText(bullet: PolicyBullet) {
    val colors = LocalMorphicColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = "•", style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            text = bullet.annotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The bullet as one run of text, its term emphasized — see [PolicyBullet] for why the term is a field. */
@Composable
private fun PolicyBullet.annotated(): AnnotatedString = buildAnnotatedString {
    if (term != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = LocalMorphicColors.current.content)) {
            append(term)
        }
        append(' ')
    }
    append(text)
}
