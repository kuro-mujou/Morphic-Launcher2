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
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.apps.AppsDetail
import inkspire.morphic.feature.settings.dock.DockDetail
import inkspire.morphic.feature.settings.folder.FolderDetail
import inkspire.morphic.feature.settings.grid.GridSizeDetail
import inkspire.morphic.feature.settings.register.SurfaceRegisterDetail
import inkspire.morphic.feature.settings.effects.EffectsDetail
import inkspire.morphic.feature.settings.iconstudio.IconsDetail
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
    initialSection: SettingsSection? = null,
    initialLayout: AppsLayout? = null,
    onOpenDevHarness: (() -> Unit)? = null,
) {
    val twoPane = currentDeviceConfiguration().isTablet
    // **Seeded from the route, and only from the *first* arrival** — `rememberSaveable`'s initialiser runs once, so
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
            SettingsTwoPane(
                homeLayout = homeLayout,
                selected = selected ?: settingsGroups.first().sections.first(),
                onSelect = { selected = it; appsLayout = null },
                appsLayout = appsLayout,
                onOpenSection = openSection,
                onBack = onBack,
                onOpenDevHarness = onOpenDevHarness,
                modifier = modifier,
            )
        } else {
            SettingsSinglePane(
                homeLayout = homeLayout,
                selected = selected,
                onSelect = { selected = it; appsLayout = null },
                onCloseDetail = { selected = null },
                appsLayout = appsLayout,
                onOpenSection = openSection,
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
    homeLayout: HomeLayout,
    selected: SettingsSection?,
    onSelect: (SettingsSection) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
    onOpenDevHarness: (() -> Unit)?,
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
                    homeLayout = homeLayout,
                    selected = null,
                    onSelect = onSelect,
                    highlightSelected = false,
                    showChevron = true,
                    insetSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    modifier = Modifier.fillMaxSize().background(colors.background),
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
    onBack: () -> Unit,
    onOpenDevHarness: (() -> Unit)?,
    appsLayout: AppsLayout?,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
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
                homeLayout = homeLayout,
                selected = selected,
                onSelect = onSelect,
                highlightSelected = true,
                showChevron = false,
                insetSides = WindowInsetsSides.Start + WindowInsetsSides.Bottom,
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
        SettingsSection.HOME_GRID -> GridSizeDetail()
        SettingsSection.DOCK -> DockDetail()
        SettingsSection.APPS -> AppsDetail(initialLayout = appsLayout)
        SettingsSection.FOLDER -> FolderDetail()
    }
}

/**
 * A settings pane, drawn into an **offscreen layer with its own background inside it** — which is what lets the icon
 * preview punch a hole through the pane and show the wallpaper behind the window.
 *
 * Three things have to be true at once for that punch to work, and this is two of them (the third is the
 * `BlendMode.Src` on the preview itself, and the fourth is the window, which `app`'s theme now provides):
 *
 * - **The pane is composited offscreen** (`withSaveLayer`). `BlendMode.Src` *replaces* the destination pixels rather
 *   than blending with them, so it needs a destination of its own to replace — and that destination has to end up
 *   composited onto a transparent window.
 * - **The background is painted inside that layer**, which is what the modifier order says: `background` sits after
 *   `drawWithContent`, so it is part of the content the layer captures. Painted outside, it would be underneath the
 *   punched hole and would be exactly what the hole revealed.
 * - **Overscroll is off** for everything inside. A stretch re-composites the scrolling content into its own layer
 *   mid-gesture, and the punch stops reaching the window for as long as it does — the hole fills with the pane's
 *   color and springs back. L1 hit this and disabled it the same way; it is the one part of the recipe that reads
 *   like superstition until you see it happen.
 *
 * The port of L1's `IconDetailPortrait` / `IconDetailLandscape` scaffolds, minus their reason for existing: those were
 * *two* screens' worth of sticky-header layout wrapped around the same trick, because L1 rebuilt the arrangement per
 * detail. Here every section is already a plain scrolling column, so the trick is all that was left to share.
 *
 * **Separately from the punch: the pane reaches the window edge and insets its own content.** It used to be the other
 * way round — the scaffold reserved the system bars, so the pane stopped above the navigation bar and the strip it
 * left showed the wallpaper through the transparent window, which is the one place the punch was never meant to reach.
 * Nothing but this pane knows what color that strip should be, so nothing but this pane can paint it. L1 did the same
 * with `contentPadding` on its `LazyColumn`, which additionally lets content scroll under the bar; that is not
 * available here, because a pane owns its own scroller and most of them are a plain `Column`.
 *
 * @param insetSides which edges to keep content off — see [SettingsDetail].
 */
@Composable
private fun PunchThroughPane(insetSides: WindowInsetsSides, content: @Composable () -> Unit) {
    val colors = LocalMorphicColors.current
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.withSaveLayer(bounds = size.toRect(), paint = Paint()) { drawContent() }
                    }
                }
                .background(colors.background)
                .uiInsetsPadding(insetSides),
        ) {
            content()
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/**
 * Dev-only way into the harness, mirroring L1's design-gallery button. Absent when the host offers no harness.
 *
 * It pads itself, because the scaffold's own inset reservation is zeroed — that reservation is what normally lifts a
 * FAB off the navigation bar, and the button is measured with this padding, so the scaffold places it clear.
 */
@Composable
private fun DevHarnessButton(onClick: (() -> Unit)?) {
    if (onClick == null) return
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) {
        Icon(imageVector = Icons.Filled.Build, contentDescription = "Dev harness")
    }
}

private const val DETAIL_FADE_MS = 180
