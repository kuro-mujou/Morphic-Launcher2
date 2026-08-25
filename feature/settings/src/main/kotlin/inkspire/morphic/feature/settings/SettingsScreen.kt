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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.backdrop.PunchThroughLayer
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.apps.AppsDetail
import inkspire.morphic.feature.settings.dock.DockDetail
import inkspire.morphic.feature.settings.effects.EffectsDetail
import inkspire.morphic.feature.settings.folder.FolderDetail
import inkspire.morphic.feature.settings.grid.GridSizeDetail
import inkspire.morphic.feature.settings.home.HomeDetail
import inkspire.morphic.feature.settings.iconstudio.IconsDetail
import inkspire.morphic.feature.settings.register.SurfaceRegisterDetail
import inkspire.morphic.feature.settings.wallpaper.WallpaperDetail
import org.koin.androidx.compose.koinViewModel

/**
 * How deep into the surface a pane sits: the list is 0, a section reached from it is 1, a section reached through a
 * hub is 2.
 *
 * **What the slide direction is read from**, and it has to be depth rather than nullness. The spec used to say "a
 * non-null target means forward", which was true while every section was a list row — with a hub in the middle,
 * *hub -> child* and *hub -> list* both have a non-null target, so backing out of a zone pane would animate as if it
 * were going deeper. Nothing breaks; it just reads as the wrong direction, which is the kind of fault that survives.
 */
private val SettingsSection?.paneDepth: Int
    get() = this?.let { it.depth + 1 } ?: 0

/**
 * The settings surface — **an index and a detail, side by side where there is room and one at a time where there
 * is not.**
 *
 * A section list that highlights its selection beside a detail on a tablet, and slides between list and detail on a
 * phone. The selected section is this screen's own state rather than a route argument, so the only destination `app`
 * knows is "settings".
 *
 * **One theme boundary, at the top.** `darkTheme` follows [isSystemInDarkTheme] here, while the launcher shell feeds
 * its theme a *wallpaper-brightness* signal instead — settings is our own surface, launcher chrome has to contrast
 * whatever is behind it. Two "is-dark" inputs, one palette. The panes below inherit it rather than each theming
 * themselves, which is what a zone boundary means.
 *
 * @param onBack leaves settings entirely. In single-pane, system back first closes an open detail: the detail is a
 *   place, so back should leave it before leaving the surface.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSection: SettingsSection? = null,
    initialLayout: AppsLayout? = null,
) {
    val twoPane = currentDeviceConfiguration().isTablet
    // **Seeded from the route, and only from the *first* arrival** — `rememberSaveable`'s initializer runs once, so
    // a deep link chooses where settings opens and everything after that is the user's navigation within it. That is
    // the right shape for an argument that describes an arrival rather than a state to be kept in step.
    var selected by rememberSaveable { mutableStateOf(initialSection) }
    // **Which layout the APPS pane should open on**, when it was reached from the register's gear rather than from the
    // list. Two saveable enums rather than one compound selection: `SettingsSection` is the list's vocabulary and stays
    // that, and a payload only one section can carry does not belong inside it. Null means "opened normally", which is
    // every other route in.
    var appsLayout by rememberSaveable { mutableStateOf(initialLayout) }
    val openSection: (SettingsSection, AppsLayout?) -> Unit = { section, layout ->
        appsLayout = layout
        selected = section
    }
    // The one thing the *shell* has to know, and no section owns: HOME's pairing names two of the rows in the list
    // and the title over their panes. Read here rather than in `SettingsList`, so the list and the app bar cannot
    // disagree about what a section is called.
    val homeLayout by koinViewModel<SettingsShellViewModel>().homeLayout.collectAsStateWithLifecycle()

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        if (twoPane) {
            val shown = selected ?: settingsGroups.first().sections.first()
            SettingsTwoPane(
                homeLayout = homeLayout,
                selected = shown,
                // **Two-pane has an "up" now, where it had only "leave".** The detail is always showing beside the
                // list, so there was nothing to close — but a *child* pane replaces the hub that opened it, and
                // leaving settings from there would skip the screen the user came through. Null on every section
                // that is a list row, which is the old behavior exactly.
                onCloseChild = shown.parent?.let { parent -> { selected = parent } },
                onSelect = { selected = it; appsLayout = null },
                appsLayout = appsLayout,
                onOpenSection = openSection,
                onBack = onBack,
                modifier = modifier,
            )
        } else {
            SettingsSinglePane(
                homeLayout = homeLayout,
                selected = selected,
                onSelect = { selected = it; appsLayout = null },
                onCloseDetail = { selected = selected?.parent },
                appsLayout = appsLayout,
                onOpenSection = openSection,
                onBack = onBack,
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
    homeLayout: HomeLayout,
    selected: SettingsSection?,
    onSelect: (SettingsSection) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
    appsLayout: AppsLayout?,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BackHandler { if (selected != null) onCloseDetail() else onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Crossfade(targetState = selected, label = "settings-title") { section ->
                        Text(section?.meta(homeLayout)?.title ?: "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = {
                    BackButton { if (selected != null) onCloseDetail() else onBack() }
                },
                actions = { SettingsSectionActions(selected) },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            transitionSpec = {
                if (targetState.paneDepth > initialState.paneDepth) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "settings-pane",
        ) { target ->
            if (target == null) {
                SettingsList(
                    homeLayout = homeLayout,
                    selected = null,
                    onSelect = onSelect,
                    highlightSelected = false,
                    showChevron = true,
                    insetSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                )
            } else {
                SettingsDetail(target, WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom, appsLayout, onOpenSection)
            }
        }
    }
}

/** Tablet layout: the list beside the detail, with the detail cross-fading as the selection moves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTwoPane(
    homeLayout: HomeLayout,
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onCloseChild: (() -> Unit)?,
    onBack: () -> Unit,
    appsLayout: AppsLayout?,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val up = onCloseChild ?: onBack
    BackHandler(onBack = up)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = { BackButton(up) },
                actions = { SettingsSectionActions(selected) },
            )
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            SettingsList(
                homeLayout = homeLayout,
                selected = selected,
                onSelect = onSelect,
                highlightSelected = true,
                showChevron = false,
                insetSides = WindowInsetsSides.Start + WindowInsetsSides.Bottom,
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(colors.background),
            )
            VerticalDivider()
            AnimatedContent(
                targetState = selected,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                transitionSpec = { fadeIn(tween(DETAIL_FADE_MS)) togetherWith fadeOut(tween(DETAIL_FADE_MS)) },
                label = "settings-detail",
            ) { section ->
                SettingsDetail(section, WindowInsetsSides.End + WindowInsetsSides.Bottom, appsLayout, onOpenSection)
            }
        }
    }
}

/**
 * Section → pane. The one `when` over [SettingsSection], so a new section fails to compile until it is drawn.
 *
 * Each arm is a *detail*, not a screen: it owns its content and nothing else. The theme, the background, the app bar
 * and the back affordance belong to the shell, because in two-pane there is one of each for two panes.
 *
 * @param insetSides the edges this pane must keep its content clear of, which only the shell knows — the same detail
 *   owes both sides on a phone and only the end on a tablet, where a list pane covers the other one.
 */
@Composable
private fun SettingsDetail(
    section: SettingsSection,
    insetSides: WindowInsetsSides,
    appsLayout: AppsLayout?,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
) = PunchThroughPane(insetSides) {
    when (section) {
        SettingsSection.WALLPAPER -> WallpaperDetail()
        SettingsSection.EFFECTS -> EffectsDetail()
        SettingsSection.ICONS -> IconsDetail()
        SettingsSection.SURFACE_REGISTER -> SurfaceRegisterDetail(onOpenSection = onOpenSection)
        SettingsSection.HOME -> HomeDetail(onOpenSection = onOpenSection)
        SettingsSection.HOME_GRID -> GridSizeDetail()
        SettingsSection.DOCK -> DockDetail()
        SettingsSection.APPS -> AppsDetail(initialLayout = appsLayout)
        SettingsSection.FOLDER -> FolderDetail()
    }
}

/**
 * A settings pane, drawn as a [PunchThroughLayer] — which is what lets the icon preview punch a hole through it and
 * show the wallpaper behind the window.
 *
 * The recipe itself moved to `core:designsystem` when the second consumer arrived (an icon container's settings
 * preview); all four of its clauses, and why each fails silently, are on [PunchThroughLayer]. Every section here is
 * already a plain scrolling column, so the punch is all there ever was to share — no per-detail sticky-header
 * scaffold wrapped around it.
 *
 * **Separately from the punch: the pane reaches the window edge and insets its own content**, which is why the
 * padding is on the *content* rather than on the layer. It used to be the other way round — the scaffold reserved
 * the system bars, so the pane stopped above the navigation bar and the strip it left showed the wallpaper through
 * the transparent window, which is the one place the punch was never meant to reach. Nothing but this pane knows
 * what color that strip should be, so nothing but this pane can paint it. Applying it as `contentPadding` would
 * additionally let content scroll under the bar; that is not available here, because a pane owns its own scroller
 * and most of them are a plain `Column`.
 *
 * @param insetSides which edges to keep content off — see [SettingsDetail].
 */
@Composable
private fun PunchThroughPane(insetSides: WindowInsetsSides, content: @Composable () -> Unit) {
    PunchThroughLayer(background = LocalMorphicColors.current.background) {
        Box(Modifier.fillMaxSize().uiInsetsPadding(insetSides)) { content() }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

private const val DETAIL_FADE_MS = 180
