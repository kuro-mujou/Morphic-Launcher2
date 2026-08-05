package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val ScreenPadding = 20.dp

/**
 * How wide the preview column is in landscape. L1's `LANDSCAPE_PREVIEW_WIDTH`, and a fixed dp for its reason: the
 * preview draws a cell at its **true** size, so the column has to be wide enough to hold one, not a share of the pane.
 */
private val LandscapePreviewWidth = 220.dp

/** The pinned heading over the icon group. */
private const val IconSectionTitle = "Icon & text"

/**
 * The shape every surface section takes: **a layout group, then a pinned icon group with a live preview in its header.**
 *
 * Ported from L1's `IconDetailPortrait` / `IconDetailLandscape`, which its five surface details all went through. Three
 * things about the arrangement are load-bearing:
 *
 * - **The grid editor comes first, then the sliders that constrain it.** The editor is a picture of the surface, so it
 *   is what a user is looking for on arrival; the margin, height and row-height sliders below it are adjustments *to*
 *   that picture and each one previews live into it. L1 ordered all five details this way (editor, then extent, then
 *   padding), and the callers here place their own controls in that order inside [layout].
 * - **The icon heading and the preview pin together.** The controls below set a fraction and two dp bounds, which say
 *   nothing on their own — the preview is how they are read. Scrolling the controls out from under a preview that had
 *   scrolled away would make them unreadable again, so the two travel as one sticky block. That is the whole reason
 *   this is a `LazyColumn` rather than the `Column` + `verticalScroll` these sections used before.
 * - **Landscape is a different arrangement, not a narrower one.** The layout group scrolls away, the heading pins, and
 *   the icon group fills the viewport as a final full-height item: controls scrolling on the left, preview fixed on the
 *   right. A phone in landscape has room for a cell beside its sliders and no room for one above them.
 *
 * The offscreen layer, the pane background, the insets and the disabled overscroll are all `PunchThroughPane`'s in
 * `SettingsScreen` — L1 repeated them in both of its scaffolds, because it had no shared pane. Overscroll being off is
 * what makes a lazy list safe here at all: a stretch re-composites the scrolling content and the icon preview's punch
 * stops reaching the window for as long as it lasts.
 *
 * @param title the section's own heading, above the layout group.
 * @param subtitle one line under it saying what the section governs.
 * @param onReroll shuffles which sample app the preview draws — L1's dice, in L1's place beside the icon heading.
 * @param layout the section's own controls: its grid editor first, then everything that adjusts it.
 * @param preview the live icon preview, given the modifier that sizes it for the current arrangement.
 * @param icons the icon and text controls, which every section fills with the same shared group.
 */
@Composable
internal fun SurfaceDetail(
    title: String,
    subtitle: String,
    onReroll: () -> Unit,
    layout: @Composable ColumnScope.() -> Unit,
    preview: @Composable (Modifier) -> Unit,
    icons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentDeviceConfiguration().isLandscape) {
        LandscapeDetail(title, subtitle, onReroll, layout, preview, icons, modifier)
    } else {
        PortraitDetail(title, subtitle, onReroll, layout, preview, icons, modifier)
    }
}

@Composable
private fun PortraitDetail(
    title: String,
    subtitle: String,
    onReroll: () -> Unit,
    layout: @Composable ColumnScope.() -> Unit,
    preview: @Composable (Modifier) -> Unit,
    icons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "layout") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(top = ScreenPadding),
            ) {
                DetailHeading(title, subtitle)
                layout()
            }
        }
        stickyHeader(key = "icon-header") { _ ->
            Column(modifier = Modifier.fillMaxWidth().background(colors.background)) {
                IconSectionHeader(onReroll, Modifier.padding(horizontal = ScreenPadding))
                preview(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding))
            }
        }
        item(key = "icon-controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = ScreenPadding),
            ) {
                icons()
            }
        }
    }
}

@Composable
private fun LandscapeDetail(
    title: String,
    subtitle: String,
    onReroll: () -> Unit,
    layout: @Composable ColumnScope.() -> Unit,
    preview: @Composable (Modifier) -> Unit,
    icons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val density = LocalDensity.current
    // The icon body is a full-viewport item placed *under* the pinned heading, so it has to know how tall that is.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "layout") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(top = ScreenPadding, bottom = ScreenPadding),
            ) {
                DetailHeading(title, subtitle)
                layout()
            }
        }
        stickyHeader(key = "icon-header") { _ ->
            IconSectionHeader(
                onReroll = onReroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = ScreenPadding)
                    .onSizeChanged { headerHeightPx = it.height },
            )
        }
        item(key = "icon-body") {
            Row(
                modifier = Modifier
                    .fillParentMaxHeight()
                    .fillMaxWidth()
                    .padding(top = headerHeight, bottom = ScreenPadding),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding),
                ) {
                    icons()
                }
                preview(Modifier.width(LandscapePreviewWidth).padding(end = ScreenPadding))
            }
        }
    }
}

/** The section's own heading and one line of what it governs. */
@Composable
private fun DetailHeading(title: String, subtitle: String) {
    val colors = LocalMorphicColors.current
    Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.content)
    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
}

/**
 * The pinned heading over the icon group, with L1's dice beside it.
 *
 * The dice is here rather than on the preview because it belongs to the *section* — one app's icon is not
 * representative (a wide logo and a round one sit differently in the same cell), so what it reshuffles is which sample
 * the whole group is being judged against.
 */
@Composable
private fun IconSectionHeader(onReroll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SettingsSectionHeader(IconSectionTitle, modifier = Modifier.weight(1f))
        IconButton(onClick = onReroll) {
            Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = "Show a different app",
                tint = colors.contentMuted,
            )
        }
    }
}
