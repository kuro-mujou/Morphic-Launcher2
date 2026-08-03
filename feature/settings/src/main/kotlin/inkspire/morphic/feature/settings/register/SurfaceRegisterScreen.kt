package inkspire.morphic.feature.settings.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.feature.settings.component.SettingsChip
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders until the settings port brings real row and chip components with it. */
private val ScreenPadding = 20.dp
private val GroupGap = 24.dp
private val ChipGap = 8.dp

/**
 * The **surface register**: for each edge of HOME, which surface it opens and in which layout.
 *
 * The first real settings section, and the one that makes the launcher a launcher — until an edge is bound here, no
 * edge of HOME is swipeable and the app list is unreachable by gesture. Choosing an option writes straight through
 * [SurfaceRegisterViewModel] to `data:settings`, and `feature:shell` is watching the same flow, so the change takes
 * effect on HOME immediately with nothing to apply or confirm.
 *
 * **Only the bindings are offered, deliberately.** The register also stores `homeLayout` and `transition`, and neither
 * has a consumer yet — `HomeScreen` is `PAGER_WITH_DOCK` by construction and `SurfacePager` implements only `SLIDE`. A
 * control for a setting that changes nothing is worse than a missing one: it teaches the user the app is broken. They
 * appear here when the things they configure exist.
 *
 * **Its own theme boundary**, feeding [isSystemInDarkTheme] — settings is our own surface and follows the system,
 * where the launcher shell follows wallpaper brightness. One palette, two "is-dark" inputs.
 *
 * **Chips rather than the segmented control**, which does exist (`MorphicSegmentedButtons`, so far used only by the dev
 * gallery). It lays its options out in one `Row` with `weight(1f)` each and cannot wrap, which suits two to four
 * options; an edge here offers six. Chips in a `FlowRow` wrap, so they are the right tool for this count — not a
 * stand-in for a missing component.
 *
 * @param onBack leaves the section. Wired to the navigator by the host, and to system back here so the two agree.
 */
@Composable
fun SurfaceRegisterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SurfaceRegisterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            Text("Surface register", style = MaterialTheme.typography.headlineSmall, color = colors.content)
            Text(
                text = "Which surface each edge of home opens. Swipe from an edge to reach it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
            )

            // Every edge gets a group, including unbound ones — an edge you cannot see is an edge you cannot bind,
            // which is the state the launcher ships in.
            HomeEdge.entries.forEach { edge ->
                val bound = state.register.sides[edge]
                Column(Modifier.padding(top = GroupGap)) {
                    Text(
                        text = edge.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.content,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ChipGap),
                        verticalArrangement = Arrangement.spacedBy(ChipGap),
                        modifier = Modifier.padding(top = ChipGap),
                    ) {
                        // "None" is a peer of the layouts rather than a separate toggle: an edge is bound to exactly
                        // one thing or nothing, so one row of mutually exclusive choices says it without a second
                        // control that could disagree with the first.
                        SettingsChip(
                            label = "None",
                            selected = bound == null,
                            onClick = { viewModel.bindApps(edge, layout = null) },
                        )
                        AppsLayout.entries.forEach { layout ->
                            SettingsChip(
                                label = layout.label,
                                selected = (bound as? SideBinding.Apps)?.layout == layout,
                                onClick = { viewModel.bindApps(edge, layout) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A human label for a layout.
 *
 * Local to this screen rather than on the enum: `core:model` stays free of display strings (and of localisation), which
 * is the same reason `Category` carries an id and the UI resolves the name. When a second screen needs these, they move
 * to one place in the settings feature — not into the model.
 */
private val AppsLayout.label: String
    get() = when (this) {
        AppsLayout.VERTICAL_LIST -> "List"
        AppsLayout.VERTICAL_GRID -> "Grid"
        AppsLayout.PAGER -> "Pager"
        AppsLayout.PAGER_WITH_CATEGORY -> "Pager + category"
        AppsLayout.CATEGORY_CARD -> "Cards"
    }
