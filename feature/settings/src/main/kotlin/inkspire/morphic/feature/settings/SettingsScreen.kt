package inkspire.morphic.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.settings.apps.AppsDetail
import inkspire.morphic.feature.settings.dock.DockDetail
import inkspire.morphic.feature.settings.folder.FolderDetail
import inkspire.morphic.feature.settings.grid.GridSizeDetail
import inkspire.morphic.feature.settings.register.SurfaceRegisterDetail
import inkspire.morphic.feature.settings.wallpaper.WallpaperDetail

/** The list pane's width beside a detail, on a screen wide enough for both. */
private val ListPaneWidth = 360.dp

/**
 * The settings surface — **an index and a detail, side by side where there is room and one at a time where there
 * is not.**
 *
 * Ported from L1's `SettingsScreen`, whose structure is right: a section list that highlights its selection beside a
 * detail on a tablet, and slides between list and detail on a phone. What is *not* carried over is where the
 * selection lives — L1 kept its sections in the navigation module and had `feature:home` importing
 * `SettingsSection.WALLPAPER` as a result. Here the section is this screen's own state, and the only destination
 * `app` knows is "settings".
 *
 * **One theme boundary, at the top.** `darkTheme` follows [isSystemInDarkTheme] here, while the launcher shell feeds
 * its theme a *wallpaper-brightness* signal instead — settings is our own surface, launcher chrome has to contrast
 * whatever is behind it. Two "is-dark" inputs, one palette. The panes below inherit it rather than each theming
 * themselves, which is what a zone boundary means.
 *
 * @param onBack leaves settings entirely. In single-pane, system back first closes an open detail — L1's two-step,
 *   and the honest one: the detail is a place, so back should leave it before leaving the surface.
 * @param onOpenDevHarness opens `app`'s dev destination, offered as a floating button exactly as L1 offered its
 *   design gallery. Passed in because the harness is scaffolding `app` owns, which is what keeps this module from
 *   ever learning it exists.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDevHarness: (() -> Unit)? = null,
) {
    val twoPane = currentDeviceConfiguration().isTablet
    var selected by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        if (twoPane) {
            SettingsTwoPane(
                selected = selected ?: settingsGroups.first().sections.first(),
                onSelect = { selected = it },
                onBack = onBack,
                onOpenDevHarness = onOpenDevHarness,
                modifier = modifier,
            )
        } else {
            SettingsSinglePane(
                selected = selected,
                onSelect = { selected = it },
                onCloseDetail = { selected = null },
                onBack = onBack,
                onOpenDevHarness = onOpenDevHarness,
                modifier = modifier,
            )
        }
    }
}

/**
 * Phone layout: the list *or* one detail, sliding horizontally between them.
 *
 * The title crossfades with the selection and the back button means "close this detail" while one is open, which is
 * the pairing that makes the slide read as depth rather than as a swap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSinglePane(
    selected: SettingsSection?,
    onSelect: (SettingsSection) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
    onOpenDevHarness: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BackHandler { if (selected != null) onCloseDetail() else onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Crossfade(targetState = selected, label = "settings-title") { section ->
                        Text(section?.meta?.title ?: "Settings")
                    }
                },
                navigationIcon = {
                    BackButton { if (selected != null) onCloseDetail() else onBack() }
                },
            )
        },
        floatingActionButton = { DevHarnessButton(onOpenDevHarness) },
    ) { innerPadding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "settings-pane",
        ) { target ->
            if (target == null) {
                SettingsList(
                    selected = null,
                    onSelect = onSelect,
                    highlightSelected = false,
                    showChevron = true,
                    modifier = Modifier.fillMaxSize().background(colors.background),
                )
            } else {
                SettingsDetail(target)
            }
        }
    }
}

/** Tablet layout: the list beside the detail, with the detail cross-fading as the selection moves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTwoPane(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onOpenDevHarness: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                // No "close detail" step here: a pane is always showing, so back has only one meaning. That single
                // meaning is what L1's two-pane mode lost — it dropped the concept entirely and fell back to a
                // hardcoded default section.
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = { DevHarnessButton(onOpenDevHarness) },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            SettingsList(
                selected = selected,
                onSelect = onSelect,
                highlightSelected = true,
                showChevron = false,
                modifier = Modifier
                    .width(ListPaneWidth)
                    .fillMaxHeight()
                    .background(colors.background),
            )
            VerticalDivider()
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = { fadeIn(tween(DETAIL_FADE_MS)) togetherWith fadeOut(tween(DETAIL_FADE_MS)) },
                label = "settings-detail",
            ) { section ->
                SettingsDetail(section)
            }
        }
    }
}

/**
 * Section → pane. The one `when` over [SettingsSection], so a new section fails to compile until it is drawn.
 *
 * Each arm is a *detail*, not a screen: it owns its content and nothing else. The theme, the background, the app bar
 * and the back affordance belong to the shell, because in two-pane there is one of each for two panes.
 */
@Composable
private fun SettingsDetail(section: SettingsSection) {
    when (section) {
        SettingsSection.WALLPAPER -> WallpaperDetail()
        SettingsSection.SURFACE_REGISTER -> SurfaceRegisterDetail()
        SettingsSection.HOME_GRID -> GridSizeDetail()
        SettingsSection.DOCK -> DockDetail()
        SettingsSection.APPS -> AppsDetail()
        SettingsSection.FOLDER -> FolderDetail()
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/** Dev-only way into the harness, mirroring L1's design-gallery button. Absent when the host offers no harness. */
@Composable
private fun DevHarnessButton(onClick: (() -> Unit)?) {
    if (onClick == null) return
    FloatingActionButton(onClick = onClick) {
        Icon(imageVector = Icons.Filled.Build, contentDescription = "Dev harness")
    }
}

private const val DETAIL_FADE_MS = 180
